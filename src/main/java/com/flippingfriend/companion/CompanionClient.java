package com.flippingfriend.companion;

import com.flippingfriend.FlippingFriendConfig;
import com.flippingfriend.core.AccountSnapshot;
import com.flippingfriend.core.CompanionAction;
import com.flippingfriend.core.CompanionActionType;
import com.flippingfriend.core.CompanionHealth;
import com.flippingfriend.core.OfferEvent;
import com.flippingfriend.core.OfferLifecycleAction;
import com.flippingfriend.core.PolicyAbstentionReason;
import com.flippingfriend.core.PolicyDecision;
import com.flippingfriend.core.PolicyDecisionType;
import com.flippingfriend.core.PositionSnapshot;
import com.flippingfriend.core.PortfolioAllocation;
import com.flippingfriend.core.PortfolioPlan;
import com.flippingfriend.data.PluginStorage;
import com.flippingfriend.data.Candle;
import com.flippingfriend.model.Explainer;
import com.flippingfriend.model.Suggestion;
import com.flippingfriend.model.SuggestionType;
import com.flippingfriend.session.AccountState;
import com.flippingfriend.session.Position;
import com.flippingfriend.session.TrackedOffer;
import com.google.gson.Gson;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Properties;
import java.util.UUID;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Thin, fail-closed client for the local companion. It never controls the game; it merely copies
 * observed state out and receives manual instructions back.
 */
@Singleton
public class CompanionClient
{
	private static final String BASE = "http://127.0.0.1:37777/v1/";
	private static final int TIMEOUT_MILLIS = 750;
	/** Ledger-backed reads may scan retained evidence. This is a transport budget, not advice TTL. */
	private static final int ACCOUNT_READ_TIMEOUT_MILLIS = 5_000;
	/** The market feed is megabytes rather than a plan, so it gets its own budget. */
	private static final int FEED_TIMEOUT_MILLIS = 5_000;
        private static final long INITIAL_RETRY_DELAY_MILLIS = 2_000;
        private static final long MAX_RETRY_DELAY_MILLIS = 60_000;
	private final PluginStorage storage;
	private final String base;
	private final Gson gson;
	private final SuggestionLedger ledger;
        private final OfferEventOutbox outbox;
        private final LongSupplier retryClock;
        private final AtomicBoolean replayRunning = new AtomicBoolean();
        private final Object replayScheduling=new Object();
        private long replayRescheduleGeneration=-1;
        private final AtomicLong replayGeneration = new AtomicLong();
        private final AtomicLong retryRequestGeneration = new AtomicLong(-1);
        private volatile boolean replayEnabled;
        private volatile long nextRetryAtMillis = Long.MAX_VALUE;
        private volatile long retryDelayMillis = INITIAL_RETRY_DELAY_MILLIS;

	/** The item currently being recommended, kept so a re-plan does not move it. */
	private volatile int incumbentItemId;

	/**
	 * Forgets the trade being held, so a new session or a changed setting starts clean.
	 * <p>
	 * Without this the lock outlives the thing it was locking: switching risk profile kept showing the
	 * previous profile's pick for as long as it survived in the plan, and a logout carried it into the
	 * next login.
	 */
	public void clearIncumbent()
	{
		incumbentItemId = 0;
		lastBuy = null;
	}

    public void invalidateEntryAdvice() { lastPlan=null; lastHealth=null; clearIncumbent(); }

	/** The single point where a plan becomes the one in force, so tests can reach the same state. */
	void acceptPlan(PortfolioPlan plan)
	{
		accountContext();
		lastPlan = plan;
	}
	synchronized boolean acceptFetchedPlan(PortfolioPlan plan, String requestedAccount, long requestedReview)
	{
		if (!java.util.Objects.equals(requestedAccount, accountContext())
			|| requestedReview != stockReviewGeneration.get()) return false;
        var risk=plan==null?null:plan.getCanonicalRisk();
        if(risk!=null && (!risk.isValid() || !java.util.Objects.equals(requestedAccount,risk.getAccountId()))) {
            lastPlan=null;return false;
        }
		acceptPlan(plan);
		return true;
	}

	/**
	 * Whether the companion is in a position to answer at all, as opposed to answering "not now".
	 * <p>
	 * These are different facts and the caller has to tell them apart. A plan that says every slot is
	 * occupied is an answer; falling back to the built-in engine on the strength of it means showing
	 * the player a trade chosen by different rules, seconds after showing them one chosen by these.
	 * A plan that is absent or expired means the companion has genuinely gone quiet, and only then is
	 * the built-in engine the right thing to ask.
	 */
	public boolean hasFreshPlan()
	{
		accountContext();
		PortfolioPlan plan = lastPlan;
		return plan != null && plan.getExpiresAt() > Instant.now().getEpochSecond();
	}
	/**
	 * The last buy actually put in front of the player, kept so a momentary gap does not replace it.
	 * <p>
	 * On three slots a single offer changing state moves the free-slot count between 0 and 1, and the
	 * plan flips between READY and "every slot is occupied" -- six were produced in four seconds
	 * during one transition. Every one of those blips used to reach the player as a different answer.
	 */
	private volatile Suggestion lastBuy;
    private Suggestion lastPresented;
    private String lastPresentedSuccessorId;
	private volatile String lastError = "Companion has not been contacted.";
	private volatile PortfolioPlan lastPlan;
	private volatile CompanionHealth lastHealth;
	private volatile com.flippingfriend.core.AccountLedgerSnapshot lastLedger;
    /** Display-only durable records. Never an input to trading, ownership accounting or entry authority. */
    private volatile com.flippingfriend.core.AccountLedgerSnapshot lastHistoryLedger;
    private long ledgerGeneration;
    private final java.util.concurrent.ExecutorService performanceWorker=java.util.concurrent.Executors.newSingleThreadExecutor(r->{
        Thread t=new Thread(r,"flipping-friend-performance");t.setDaemon(true);return t;
    });
    private final AtomicBoolean performanceQueued=new AtomicBoolean();
    private volatile com.flippingfriend.core.PerformanceSnapshot performance;
    private volatile com.flippingfriend.core.TradeReviewSnapshot tradeReview;
    private final AtomicBoolean tradeReviewQueued=new AtomicBoolean();
    private volatile String tradeReviewError="";
    private volatile String performanceScope="SESSION",performanceError="";
    private volatile long lastPerformanceRequest;
    private long performanceGeneration;
    public synchronized void setPerformanceScope(String scope) {
        if(!Set.of("SESSION","DAY","WEEK","ALL").contains(scope))throw new IllegalArgumentException("Unknown performance scope");
        if(!scope.equals(performanceScope)){performanceScope=scope;performance=null;performanceGeneration++;lastPerformanceRequest=0;}
        requestPerformanceRefresh();
    }
    public String performanceScope(){return performanceScope;}
    public String performanceError(){return performanceError;}
    public String tradeReviewError(){return tradeReviewError;}
    public boolean tradeReviewUpdating(){return tradeReviewQueued.get();}
    public synchronized com.flippingfriend.core.TradeReviewSnapshot tradeReview() {
        var result=tradeReview;String account=accountContext();long now=Instant.now().getEpochSecond();
        return replayEnabled && result!=null && account!=null && account.equals(result.accountId)
            && result.observedAt<=now && now-result.observedAt<=150?result:null;
    }
    public synchronized void reviewTrades(String expectedAccount,long cursor,java.util.List<String> sources,boolean archive) {
        String account=accountContext();var current=tradeReview();
        if(account==null || !account.equals(expectedAccount) || current==null || current.positionCursor!=cursor
            || !tradeReviewQueued.compareAndSet(false,true))return;
        long generation=performanceGeneration;
        var body=new com.flippingfriend.core.TradeReviewSnapshot.Request(account,UUID.randomUUID().toString(),cursor,archive,sources);
        if(!body.isValid()){tradeReviewQueued.set(false);return;}
        performanceWorker.execute(()->{
            try {
                var result=gson.fromJson(request("trade-review","POST",gson.toJson(body),ACCOUNT_READ_TIMEOUT_MILLIS,account),com.flippingfriend.core.TradeReviewSnapshot.class);
                synchronized(this) {
                    if(generation!=performanceGeneration || !account.equals(accountContext()))return;
                    if(result==null || !result.isValid() || !account.equals(result.accountId) || result.observedAt>Instant.now().getEpochSecond())
                        throw new IllegalStateException("Invalid account review response");
                    tradeReview=result;tradeReviewError="";performance=null;lastPerformanceRequest=0;
                }
                requestPerformanceRefresh();
            }catch(Exception unavailable){synchronized(this){if(generation==performanceGeneration && account.equals(accountContext()))tradeReviewError="Review update failed; records remain unchanged. Refresh and retry.";}}
            finally{tradeReviewQueued.set(false);}
        });
    }
    public synchronized com.flippingfriend.core.PerformanceSnapshot performance() {
        String account=accountContext();var current=performance;
        long age=current==null?Long.MAX_VALUE:Instant.now().getEpochSecond()-current.observedAt;
        return replayEnabled && current!=null && age>=0 && age<=150 && java.util.Objects.equals(account,current.accountId)
            && performanceScope.equals(current.scope)?current:null;
    }
    /** Records never run on the decision worker, and never authorize a trade. */
    public synchronized void requestPerformanceRefresh() {
        String account=accountContext();long now=System.currentTimeMillis();
        if(!replayEnabled || account==null || now-lastPerformanceRequest<2000 || !performanceQueued.compareAndSet(false,true))return;
        lastPerformanceRequest=now;long generation=performanceGeneration;String scope=performanceScope;
        performanceWorker.execute(()->{
            try {
                var result=gson.fromJson(request("performance/current?scope="+scope,"GET",null,ACCOUNT_READ_TIMEOUT_MILLIS,account),
                    com.flippingfriend.core.PerformanceSnapshot.class);
                synchronized(this) {
                    if(generation!=performanceGeneration || !java.util.Objects.equals(account,accountContext()) || !scope.equals(performanceScope))return;
                    if(result==null || !result.isValid() || !account.equals(result.accountId) || !scope.equals(result.scope)
                        || result.observedAt>Instant.now().getEpochSecond())throw new IllegalStateException("Invalid account performance response");
                    performance=result;performanceError="";
                }
                try {
                    var review=gson.fromJson(request("trade-review/current","GET",null,ACCOUNT_READ_TIMEOUT_MILLIS,account),com.flippingfriend.core.TradeReviewSnapshot.class);
                    synchronized(this) {
                        if(generation==performanceGeneration && account.equals(accountContext())) {
                            if(review==null || !review.isValid() || !account.equals(review.accountId) || review.observedAt>Instant.now().getEpochSecond())
                                throw new IllegalStateException("Invalid account review response");
                            tradeReview=review;tradeReviewError="";
                        }
                    }
                }catch(Exception unavailable){synchronized(this){if(generation==performanceGeneration && account.equals(accountContext())){tradeReview=null;tradeReviewError="Review archive is unavailable; all evidence remains visible.";}}}
            }catch(Exception failure){synchronized(this){if(generation==performanceGeneration && java.util.Objects.equals(account,accountContext()))performanceError="Performance is updating";}}
            finally{performanceQueued.set(false);}
        });
    }
    public synchronized void resetPerformanceSession() {
        String account=accountContext();if(account==null || !replayEnabled)return;
        long generation=performanceGeneration;
        String requestId=UUID.randomUUID().toString();
        performanceWorker.execute(()->{
            try {
                request("performance/session","POST",gson.toJson(java.util.Map.of("requestId",requestId)),ACCOUNT_READ_TIMEOUT_MILLIS,account);
                synchronized(this) {
                    if(generation!=performanceGeneration || !java.util.Objects.equals(account,accountContext()))return;
                    performance=null;performanceGeneration++;lastPerformanceRequest=0;performanceError="";
                }
                requestPerformanceRefresh();
            }catch(Exception unavailable){synchronized(this){if(generation==performanceGeneration)performanceError="Session reset unavailable; retry after account checks finish";}}
        });
    }
	@Inject private com.flippingfriend.session.StockDeclarations stockDeclarations;
	private final AtomicLong stockReviewGeneration = new AtomicLong();
	public long stockReviewVersion() { return stockReviewGeneration.get(); }

	private com.flippingfriend.session.StockDeclarations stockDeclarations()
	{
		if (stockDeclarations == null) stockDeclarations = new com.flippingfriend.session.StockDeclarations(storage);
		return stockDeclarations;
	}
	public java.util.Map<Integer, com.flippingfriend.core.StockDeclaration> stockReviews()
	{ return stockDeclarations().read(); }

	public synchronized boolean reviewStock(String expectedAccount, com.flippingfriend.core.PositionProjection position,
		com.flippingfriend.core.StockDeclaration.Use use)
	{
		com.flippingfriend.core.AccountLedgerSnapshot current = lastLedger();
		if (current == null || !java.util.Objects.equals(expectedAccount, current.getAccountId()) || position == null) return false;
		try
		{
			com.flippingfriend.core.PositionProjection latest = current.getPositions().stream()
				.filter(p -> p.getItemId() == position.getItemId()).findFirst().orElse(null);
			if (latest == null) return false;
			com.flippingfriend.core.StockDeclaration declaration = use == null ? null
				: new com.flippingfriend.core.StockDeclaration(position, use);
			if (declaration != null && !declaration.matches(latest)) return false;
			stockDeclarations().record(expectedAccount, position.getItemId(), declaration);
			stockReviewGeneration.incrementAndGet();
			clearIncumbent(); lastPlan = null;
			return true;
		}
		catch (Exception failed) { lastError = "Stock review was not saved: " + failed.getMessage(); return false; }
	}
	private String observedAccountId;
	/** Latest durably published canonical offer event by slot, retained after acknowledgement. */
	private final java.util.concurrent.ConcurrentMap<Integer, OfferEvent> canonicalOffers =
		new java.util.concurrent.ConcurrentHashMap<>();

	private synchronized String accountContext()
	{
		String current = storage.accountId();
		java.nio.file.Path accountDirectory = current == null ? null : storage.accountDir(current);
		boolean unsupportedPrice = accountDirectory != null && java.nio.file.Files.exists(accountDirectory
			.resolve(com.flippingfriend.session.OfferTracker.UNSUPPORTED_PRICE_FILE));
		if (unsupportedPrice) current = null;
		// Repeat invalidation while blocked: an earlier in-flight response must not restore advice.
		if (unsupportedPrice || !java.util.Objects.equals(current, observedAccountId))
		{
			clearIncumbent(); lastPlan = null; lastHealth = null;
			lastLedger = null;
            lastHistoryLedger = null;
            ledgerGeneration++;
            performance=null;performanceGeneration++;lastPerformanceRequest=0;performanceError="";
            tradeReview=null;tradeReviewError="";
			canonicalOffers.clear(); ledger.clear();
            lastPresented = null;
			observedAccountId = current;
		}
		return current;
	}

        public CompanionClient(PluginStorage storage, Gson gson, SuggestionLedger ledger)
        {
                this(storage, gson, ledger, new OfferEventOutbox(storage));
        }

        @Inject
        public CompanionClient(PluginStorage storage, Gson gson, SuggestionLedger ledger,
                OfferEventOutbox outbox)
        {
                this(storage, gson, ledger, outbox, System::currentTimeMillis);
        }

        CompanionClient(PluginStorage storage, Gson gson, SuggestionLedger ledger,
                OfferEventOutbox outbox, LongSupplier retryClock)
        {
                this(storage,gson,ledger,outbox,retryClock,BASE);
        }

        CompanionClient(PluginStorage storage, Gson gson, SuggestionLedger ledger,
                OfferEventOutbox outbox, LongSupplier retryClock, String base)
        {
                this.storage = storage;
                this.base = base;
                this.observedAccountId = storage.accountId();
                this.gson = gson;
                this.ledger = ledger;
                this.outbox = outbox;
                this.retryClock = retryClock;
        }

	/**
	 * @param markedDrawdown coins the session is currently down, closed trades and open positions
	 *                       together. This is what arms the drawdown circuit breaker, so passing a
	 *                       placeholder here would leave the breaker permanently disarmed while
	 *                       still appearing, in every test and in the panel, to be working.
	 */
	public void publishAccount(AccountState state, FlippingFriendConfig config, long markedDrawdown,
		java.util.Map<Integer, Long> committedByItem, java.util.Map<Integer, Integer> buyLimitUsed,
		java.util.Set<String> blocked, java.util.Set<Integer> skipped, java.util.Set<Integer> onOffer,
		boolean sellOnly, java.util.Collection<Position> positions)
	{
		publishAccount(state, config, markedDrawdown, committedByItem, buyLimitUsed, blocked,
			skipped, onOffer, sellOnly, positions, java.util.Collections.emptyList());
	}

	public void publishAccount(AccountState state, FlippingFriendConfig config, long markedDrawdown,
		java.util.Map<Integer, Long> committedByItem, java.util.Map<Integer, Integer> buyLimitUsed,
		java.util.Set<String> blocked, java.util.Set<Integer> skipped, java.util.Set<Integer> onOffer,
		boolean sellOnly, java.util.Collection<Position> positions, java.util.Collection<TrackedOffer> offers)
	{
		publishAccount(state, config, markedDrawdown, committedByItem, buyLimitUsed, blocked, skipped,
			onOffer, sellOnly, positions, offers, accountContext());
	}

	public void publishAccount(AccountState state, FlippingFriendConfig config, long markedDrawdown,
		java.util.Map<Integer, Long> committedByItem, java.util.Map<Integer, Integer> buyLimitUsed,
		java.util.Set<String> blocked, java.util.Set<Integer> skipped, java.util.Set<Integer> onOffer,
		boolean sellOnly, java.util.Collection<Position> positions, java.util.Collection<TrackedOffer> offers,
		String expectedAccountId)
    {
        publishAccount(state,config,markedDrawdown,committedByItem,buyLimitUsed,blocked,skipped,onOffer,
            sellOnly,positions,offers,expectedAccountId,null);
    }
    public void publishAccount(AccountState state, FlippingFriendConfig config, long markedDrawdown,
        java.util.Map<Integer,Long> committedByItem,java.util.Map<Integer,Integer> buyLimitUsed,
        java.util.Set<String> blocked,java.util.Set<Integer> skipped,java.util.Set<Integer> onOffer,
        boolean sellOnly,java.util.Collection<Position> positions,java.util.Collection<TrackedOffer> offers,
        String expectedAccountId,com.flippingfriend.session.PlanningAccountObservation observation)
	{
		if (!state.isLoggedIn()) return;
		String accountId = accountContext();
		if (accountId == null || !accountId.equals(expectedAccountId)) { lastError = "Waiting for current account identity"; return; }
        java.util.Map<Integer,String> receipt=new java.util.HashMap<>();
        if(observation!=null) {
            long age=Instant.now().getEpochSecond()-observation.getObservedAt();
            if(!expectedAccountId.equals(observation.getAccountId()) || state!=observation.getState() || age<0 || age>150)return;
            java.util.List<TrackedOffer> observed=observation.getSlots();
            onOffer=new java.util.HashSet<>();
            var factories=new java.util.ArrayList<OfferEventOutbox.EventFactory>();
            for(int slot=0;slot<8;slot++) {
                if(observed.get(slot)!=null)onOffer.add(observed.get(slot).getItemId());
                factories.add(observed.get(slot)==null
                    ? clearedFactory(slot,expectedAccountId,true,observation.getObservedAt())
                    : offerFactory(observed.get(slot),expectedAccountId,true,observation.getObservedAt()));
            }
            try {
                for(var event:enqueueObservedOffers(factories)) {
                    receipt.put(event.getSlot(),event.getEventId());rememberCanonicalOffer(event);
                }
                lastLedger=null;
            }catch(Exception failure) {
                lastError="Could not persist offer observations: "+failure.getMessage();return;
            }
        }else {
            // Legacy callers cannot supply current-read proof or release bounded-history planning.
            for (TrackedOffer offer : new java.util.ArrayList<>(offers))
                if (!canonicalOffers.containsKey(offer.getSlot())) publishOffer(offer, expectedAccountId);
        }
		replayPendingOffers();
		try
		{
			if (!outbox.pending().isEmpty()) { lastError = "Waiting for durable offer acknowledgements"; return; }
		}
		catch (Exception error) { lastError = error.getMessage(); return; }
		long now = observation==null ? Instant.now().getEpochSecond() : observation.getObservedAt();
                java.util.List<PositionSnapshot> positionSnapshots = new java.util.ArrayList<>();
		for (Position position : positions == null
				? java.util.Collections.<Position>emptyList()
				: new java.util.ArrayList<>(positions))
                {
                        positionSnapshots.add(new PositionSnapshot(position.getItemId(), position.getItemName(),
                                position.getQuantity(), position.getTotalCost(), position.isCostKnown(),
                                position.getOpenedAt(), position.getTargetSellPrice(), position.getStopPrice(),
                                position.getPredictedSellMinutes()));
                }
		// What is already held goes with it. The companion's exposure ceilings were seeded empty on
		// every plan, so they limited a plan against itself and never against the book -- which is how
		// the same item could be recommended again the moment its buy was collected. The profit floor
		// travels too: it existed only in the built-in engine, whose buy result is discarded, so the
		// setting had no effect on any trade the player was actually shown.
		AccountSnapshot snapshot = new AccountSnapshot(UUID.randomUUID().toString(), now,
			state.spendableCoins(config.includeBankValue(), config.bankrollCap()), state.getCommittedCoins(),
			state.getFreeSlots(), state.getTotalSlots(), state.canBuyMembersItems(), state.isBankSeen(),
			markedDrawdown, config.riskProfile() == null ? null : config.riskProfile().name(),
			committedByItem, config.minProfitPerFlip(), buyLimitUsed,
			!config.useCalibration(),
			config.checkInterval() == null ? 0 : config.checkInterval().getMinutes(),
			blocked, skipped, onOffer, sellOnly,
			// How long a flip should take. The companion is what actually chooses the trade, and
			// until this travelled it planned every leg at the risk appetite's own horizon however
			// short or long a flip the player had asked for.
			config.targetHoldMinutes(), positionSnapshots).withAccountId(accountId)
			.withAutomaticTradeScope();
        snapshot=snapshot.withObservedHoldings(currentPhysicalHoldings(state));
        snapshot=snapshot.withObservedInventory(state.getInventoryHoldings());
		if(observation!=null) snapshot=snapshot.withOfferReadSet(receipt);
		post("events/account-state", snapshot);
	}
    /** Remove cached bank quantities without masking contradictory/overflowed container totals. */
    static java.util.Map<Integer,Integer> currentPhysicalHoldings(AccountState state) {
        java.util.Map<Integer,Integer> observed=new java.util.HashMap<>(state.getHoldings());
        state.getBankHoldings().forEach((item,quantity)->{
            Integer total=observed.get(item);
            if(quantity==null || quantity<0 || total==null && quantity>0 || total!=null && total<quantity)
                throw new IllegalArgumentException("Contradictory physical container quantities");
            if(total!=null)observed.put(item,Math.subtractExact(total,quantity));
        });
        observed.entrySet().removeIf(e->e.getValue()!=null && e.getValue()==0);
        return observed;
    }

        public void publishOffer(TrackedOffer offer)
        {
                publishOffer(offer, accountContext());
        }

        /** A delayed observation must retain the account captured by the client thread. */
        public void publishOffer(TrackedOffer offer, String expectedAccountId)
        {
                publishOffer(offer, expectedAccountId, false);
        }

        /** Called only for copied, authenticated client observations, not account cache replay. */
        public void publishObservedOffer(TrackedOffer offer, String expectedAccountId)
        {
                publishOffer(offer, expectedAccountId, true);
        }
        public void publishObservedOffer(TrackedOffer offer,String expectedAccountId,long observedAt)
        { publishOffer(offer,expectedAccountId,true,observedAt,true); }

    /** Commit a copied client capture locally before dispatch. Never performs HTTP. */
    public void captureObservedOffer(TrackedOffer offer,String expectedAccountId,long observedAt) {
        if(offer!=null && publishOffer(offer.copyObservation(),expectedAccountId,true,observedAt,false)!=null)
            wakeCapturedOfferReplay();
    }
    public void captureObservedOfferCleared(int slot,String expectedAccountId,long observedAt) {
        if(publishOfferCleared(slot,expectedAccountId,true,observedAt,false)!=null)wakeCapturedOfferReplay();
    }
    /** Local forced sources only. Failure leaves new-episode continuation reconciliation suspended. */
    public java.util.List<OfferEvent> continuationSourceEpisodes() {
        String expected=accountContext();if(expected==null)return null;
        try{var first=outbox.observedSuccessorEpisodes();return expected.equals(accountContext())?first:null;}
        catch(Exception unavailable){return null;}
    }
    private void wakeCapturedOfferReplay() {
        long generation=replayGeneration.get();
        synchronized(replayScheduling) {
            if(!replayLifecycleIsCurrent(generation))return;
            // Wake a drained queue, retaining any active transport-failure backoff.
            if(nextRetryAtMillis==Long.MAX_VALUE)nextRetryAtMillis=retryClock.getAsLong();
            if(replayRunning.get())replayRescheduleGeneration=generation;
        }
    }

        private OfferEvent publishOffer(TrackedOffer offer, String expectedAccountId, boolean authenticated)
        {
                return publishOffer(offer,expectedAccountId,authenticated,Instant.now().getEpochSecond(),true);
        }
        private OfferEvent publishOffer(TrackedOffer offer, String expectedAccountId, boolean authenticated,long now,boolean replay)
        {
                if (offer == null) return null;
                if (expectedAccountId == null || !expectedAccountId.equals(accountContext()))
                { lastError = "Could not persist offer event: account identity changed or unavailable"; return null; }
                try
                {
                        OfferEvent event = enqueueObservedOffers(java.util.Collections.singletonList(offerFactory(offer,expectedAccountId,authenticated,now))).get(0);
                        lastLedger = null;
                        rememberCanonicalOffer(event);
                        if(replay) replayPendingOffers();
                        return event;
                }
                catch (Exception ex)
                {
                        lastError = "Could not persist offer event: " + ex.getMessage();
                        return null;
                }
        }

    private OfferEventOutbox.EventFactory offerFactory(TrackedOffer offer,String expectedAccountId,boolean authenticated,long now) {
        String state=offer.getState()==null?"OBSERVED":offer.getState();
        SuggestionLedger.Advice advice=ledger.attribute(offer.getItemId(),offer.isBuying(),now);
        var successor=ledger.attributeSuccessor(offer.getItemId(),offer.isBuying(),now);
        var buyPlan=ledger.attributeBuyPlan(offer.getItemId(),offer.isBuying(),now);
        return (eventId,sessionId,sequence)->{
            long start=offer.getFirstSeen()>0?offer.getFirstSeen():now;
            var builder=OfferEvent.builder(UUID.randomUUID().toString(),now,state).accountId(expectedAccountId)
                .authenticatedObservation(authenticated).eventIdentity(eventId,sessionId,
                    offer.getEpisodeIdentity()==null?offer.getSlot()+"@"+start+":"+offer.getItemId():offer.getEpisodeIdentity())
                .slot(offer.getSlot()).item(offer.getItemId(),offer.getItemName()).buying(offer.isBuying()).price(offer.getPrice())
                .quantities(offer.getTotalQuantity(),offer.getQuantityFilled()).spent(offer.getSpent()).sequence(sequence).firstSeenAt(offer.getFirstSeen());
            if(offer.isBuying())builder.acceptedBuyPlanEvidenceVersion("1");
            if(advice!=null) {
                builder.recommendation(advice.getRecommendationId(),advice.getPrice(),advice.getQuantity(),advice.getQuoteAgeSeconds(),
                    advice.getPredictedMinutes(),advice.getPredictedCompletion());
                builder.predictionEvidence(null,advice.getRecordedAt(),false);
                builder.executionForecast(advice.getForecast());builder.exitContext(advice.getExitContext());
                var order=offer.getFirstCaptureOrder();
                if(advice.getAcceptedOrder().precedes(order))
                    builder.acceptedAdviceProof(new com.flippingfriend.core.AcceptedAdviceProof(advice.getAcceptedOrder(),order));
            }
            if(successor!=null)builder.continuationSuccessorProof(successor.captured(offer.getFirstCaptureOrder()));
            if(buyPlan!=null) {
                var forecast=buyPlan.getForecast();
                if(forecast!=null && !forecast.matches(offer.getItemId(),true,buyPlan.getPrice(),buyPlan.getQuantity()))forecast=null;
                builder.acceptedBuyPlanProof(com.flippingfriend.core.AcceptedBuyPlanProof.accepted(expectedAccountId,
                    buyPlan.getAcceptedAdviceId(),offer.getItemId(),buyPlan.getPrice(),buyPlan.getQuantity(),buyPlan.getRecordedAt(),
                    buyPlan.getAcceptedOrder(),buyPlan.getExitContext(),forecast).captured(offer.getFirstCaptureOrder()));
            }
            return builder.build();
        };
    }

    private OfferEventOutbox.EventFactory clearedFactory(int slot,String expectedAccountId,boolean authenticated,long now) {
        return (eventId,sessionId,sequence)->OfferEvent.builder(UUID.randomUUID().toString(),now,"EMPTY")
            .accountId(expectedAccountId).authenticatedObservation(authenticated).eventIdentity(eventId,sessionId,null).slot(slot).sequence(sequence).build();
    }

        public void publishOfferCleared(int slot)
        {
                publishOfferCleared(slot, accountContext());
        }

        public void publishOfferCleared(int slot, String expectedAccountId)
        {
                publishOfferCleared(slot, expectedAccountId, false);
        }

        public void publishObservedOfferCleared(int slot, String expectedAccountId)
        {
                publishOfferCleared(slot, expectedAccountId, true);
        }
        public void publishObservedOfferCleared(int slot,String expectedAccountId,long observedAt)
        { publishOfferCleared(slot,expectedAccountId,true,observedAt,true); }

        private OfferEvent publishOfferCleared(int slot, String expectedAccountId, boolean authenticated)
        {
                return publishOfferCleared(slot,expectedAccountId,authenticated,Instant.now().getEpochSecond(),true);
        }
        private OfferEvent publishOfferCleared(int slot, String expectedAccountId, boolean authenticated,long now,boolean replay)
        {
                if (slot < 0) return null;
                if (expectedAccountId == null || !expectedAccountId.equals(accountContext()))
                { lastError = "Could not persist offer event: account identity changed or unavailable"; return null; }
                try
                {
                        OfferEvent event = enqueueObservedOffers(java.util.Collections.singletonList(clearedFactory(slot,expectedAccountId,authenticated,now))).get(0);
                        lastLedger = null;
                        canonicalOffers.remove(slot);
                        if(replay) replayPendingOffers();
                        return event;
                }
                catch (Exception ex)
                {
                        lastError = "Could not persist offer event: " + ex.getMessage();
                        return null;
                }
        }

	/**
	 * Fetches the current plan and remembers it, whatever else happens this cycle.
	 * <p>
	 * This used to be folded into {@link #nextBuySuggestion}, which the plugin only calls when the
	 * built-in engine has nothing to sell or collect. So on any cycle where the player had something
	 * else to do, the plan behind the portfolio list was never refreshed and the panel quietly kept
	 * rendering an older and older plan.
	 *
	 * @return the plan if the companion returned a usable one, otherwise null
	 */
	public PortfolioPlan refreshPlan()
	{
		long requestedReview = stockReviewGeneration.get();
		String requestedAccount = accountContext();
        if(offerEvidencePending()){lastPlan=null;lastLedger=null;return null;}
		refreshLedger(requestedAccount);
		try
		{
			CompanionHealth health = get("health", CompanionHealth.class);
			lastHealth = health;
			if (health == null || !health.canReadPlan(Instant.now().getEpochSecond()))
			{
				lastPlan = null;
				return null;
			}
			PortfolioPlan fetched = get("portfolio/current", PortfolioPlan.class);
			if (!java.util.Objects.equals(requestedAccount, accountContext()))
				throw new IllegalStateException("Account changed while fetching the plan");
			if (!acceptFetchedPlan(fetched, requestedAccount, requestedReview)) return null;
			return lastPlan;
		}
		catch (Exception ex)
		{
			lastError = ex.getMessage();
			return null;
		}
	}

	private void refreshLedger(String requestedAccount)
	{
		if (requestedAccount == null) { lastLedger = null; return; }
        long generation;
        synchronized(this) { accountContext(); generation=ledgerGeneration; }
		try
		{
			com.flippingfriend.core.AccountLedgerSnapshot fetched = get("ledger/current",
				com.flippingfriend.core.AccountLedgerSnapshot.class);
			acceptLedger(fetched, requestedAccount,generation);
		}
        catch (Exception unavailable) { acceptLedger(null,requestedAccount,generation); }
	}

	synchronized boolean acceptLedger(com.flippingfriend.core.AccountLedgerSnapshot fetched, String requestedAccount)
	{
		return acceptLedger(fetched,requestedAccount,ledgerGeneration);
	}
    synchronized boolean acceptLedger(com.flippingfriend.core.AccountLedgerSnapshot fetched,String requestedAccount,long generation)
    {
        String account=accountContext();
        if(generation!=ledgerGeneration || !java.util.Objects.equals(requestedAccount,account))return false;
		if (fetched == null || !fetched.isValid()
			|| !java.util.Objects.equals(requestedAccount, fetched.getAccountId())
            || fetched.getObservedAt()>Instant.now().getEpochSecond())
		{ lastLedger = null; return false; }
		lastLedger = fetched;
        if(replayEnabled && (lastHistoryLedger==null || fetched.getObservedAt()>=lastHistoryLedger.getObservedAt()))
            lastHistoryLedger=fetched;
		return true;
	}

    /** Retained account-scoped records for investigation, including during delivery/HTTP gaps. */
    public synchronized com.flippingfriend.core.AccountLedgerSnapshot historyLedger() {
        String account=accountContext();var history=lastHistoryLedger;
        return replayEnabled && account!=null && history!=null && account.equals(history.getAccountId())
            ? history:null;
    }

	public com.flippingfriend.core.AccountLedgerSnapshot lastLedger()
	{
		String currentAccount = accountContext();
		com.flippingfriend.core.AccountLedgerSnapshot current = lastLedger;

		long age = current == null ? Long.MAX_VALUE : Instant.now().getEpochSecond() - current.getObservedAt();
		return age < 0 || age > 150 || !java.util.Objects.equals(currentAccount, current.getAccountId()) ? null : current;
	}

    /** Trading instructions wait for all observed executions to reach the durable ledger. */
    public com.flippingfriend.core.AccountLedgerSnapshot tradingLedger() {
        try { if (!outbox.pending().isEmpty()) return null; }
        catch (Exception unavailable) { return null; }
        return lastLedger();
    }

    private java.util.List<OfferEvent> enqueueObservedOffers(java.util.List<OfferEventOutbox.EventFactory> factories)throws Exception {
        try { return outbox.enqueueObservedOffers(factories); }
        catch(Exception unavailable) {
            // Keep account proof and cached advice withdrawn while the outbox retains this tail.
            lastLedger=null;invalidateEntryAdvice();
            long generation=replayGeneration.get(),now=retryClock.getAsLong();
            synchronized(replayScheduling) {
                if(replayLifecycleIsCurrent(generation)) {
                    nextRetryAtMillis=Math.min(nextRetryAtMillis,now+retryDelayMillis);
                    if(replayRunning.get())replayRescheduleGeneration=generation;
                }
            }
            throw unavailable;
        }
    }

	/**
	 * The trade to recommend, held steady between plans.
	 * <p>
	 * A new plan arrives every fifteen to thirty seconds and its ranking is not stable between runs,
	 * so simply taking the top row meant the recommendation changed under the player several times a
	 * minute. Once an item has been put in front of someone it stays there while it remains a trade
	 * the plan still wants and the player has not rejected — the price and size are refreshed from
	 * each new plan, but the item does not move.
	 *
	 * @param blocked  item names the player never wants suggested, lower-cased
	 * @param skipped  item ids the player has skipped this session
	 * @param onOffer  item ids with a live Grand Exchange offer, which must not be suggested again
	 */
	public Suggestion nextBuySuggestion(Explainer explainer, Set<String> blocked, Set<Integer> skipped,
		Set<Integer> onOffer)
	{
		String proposedAccount = accountContext();
		try
		{
			PortfolioPlan plan = lastPlan;
			if (plan == null || !"READY".equals(plan.getStatus()) || plan.getAllocations().isEmpty()
				|| plan.getExpiresAt() <= Instant.now().getEpochSecond())
			{
				// Nothing usable in the current plan. Before announcing that, check whether the trade
				// already on screen still stands: this method is only reached when the client says a
				// slot is free, so a plan claiming every slot is occupied was built from an older
				// snapshot than the one that got us here. Replacing a good suggestion with a message
				// -- and then with whatever the built-in engine picks next cycle -- is what reads as
				// the system changing its mind three times in five seconds.
				Suggestion held = held(blocked, skipped, onOffer);
				if (held != null)
				{
					return held;
				}
				return unavailable(plan == null ? lastError : plan.getReason());
			}

			PortfolioAllocation allocation = select(plan, blocked, skipped, onOffer);
			if (allocation == null)
			{
				return unavailable("Every trade in the current plan is one you have skipped or blocked.");
			}
			com.flippingfriend.core.PortfolioCandidate candidate = allocation.getCandidate();

			Suggestion suggestion = EntryDecisionPresenter.buySuggestion(candidate,plan,explainer,proposedAccount);

			lastBuy = suggestion;
			return suggestion;
		}
		catch (Exception ex)
		{
			lastError = ex.getMessage();
			return unavailable(lastError);
		}
	}

    /** Called only after the client-thread guide accepts advice. This does not prove player intent. */
    public synchronized void recordPresentedAdvice(Suggestion suggestion, String expectedAccount)
    {
        recordPresentedAdvice(suggestion,expectedAccount,0);
    }
    public synchronized void recordPresentedAdvice(Suggestion suggestion, String expectedAccount,int stopPrice)
    {
        String account = accountContext();
        if(account==null || !account.equals(expectedAccount))return;
        if (suggestion == null
            || (suggestion.getAdviceAccountId() != null && !account.equals(suggestion.getAdviceAccountId()))
            || (suggestion.getAdviceExpiresAt() > 0 && suggestion.getAdviceExpiresAt() <= Instant.now().getEpochSecond())
            || (suggestion.getType() != SuggestionType.BUY && suggestion.getType() != SuggestionType.SELL)
            || suggestion.getItemId() <= 0 || suggestion.getPrice() <= 0 || suggestion.getQuantity() <= 0) {
            withdrawPresentedSuccessor();return;
        }
        if(suggestion==lastPresented && java.util.Objects.equals(lastPresentedSuccessorId,
                suggestion.getContinuationSuccessorProof()==null?null:suggestion.getContinuationSuccessorProof().getAcceptedAdviceId()))return;
        boolean buying = suggestion.getType() == SuggestionType.BUY;
        double minutes = buying ? suggestion.getBuyFillMinutes() : suggestion.getSellFillMinutes();
        Double probability = suggestion.getLegCompletionProbability();
        com.flippingfriend.core.ExecutionForecast forecast = suggestion.getExecutionForecast();
        boolean scalarValid = probability != null && Double.isFinite(probability) && probability >= 0 && probability <= 1
            && Double.isFinite(minutes) && minutes > 0;
        if (forecast != null && (!scalarValid || !forecast.matches(suggestion.getItemId(),buying,suggestion.getPrice(),suggestion.getQuantity())
            || Math.abs(forecast.getExpectedTotalSeconds()-minutes*60) > 1e-9*Math.max(1,minutes*60)
            || Math.abs(forecast.getCompletionProbability()-probability) > 1e-12)) {
            scalarValid = false; forecast = null;
        }
        String recommendation = suggestion.getRecommendationId();
        if (recommendation == null || recommendation.isEmpty()) recommendation = (buying ? "buy-" : "sell-") + suggestion.getItemId();
        com.flippingfriend.core.AcquisitionExitContext exit=null;
        if(buying && suggestion.getTargetSellPrice()>0 && stopPrice>=0 && stopPrice<suggestion.getTargetSellPrice()
            && Double.isFinite(suggestion.getSellFillMinutes()) && suggestion.getSellFillMinutes()>0)
            exit=new com.flippingfriend.core.AcquisitionExitContext(suggestion.getTargetSellPrice(),stopPrice,suggestion.getSellFillMinutes());
        ledger.recorded(recommendation,suggestion.getItemId(),buying,suggestion.getPrice(),suggestion.getQuantity(),
            suggestion.getQuoteAgeSeconds(),scalarValid ? minutes : 0,scalarValid ? probability : 0,forecast,exit,suggestion.getContinuationSuccessorProof());
        lastPresented = suggestion;
        lastPresentedSuccessorId=suggestion.getContinuationSuccessorProof()==null?null:suggestion.getContinuationSuccessorProof().getAcceptedAdviceId();
    }
    /** A hidden, refused or unsupported display cannot remain prospective successor evidence. */
    public synchronized void withdrawPresentedSuccessor() {
        ledger.withdrawAcceptedDisplay();lastPresented=null;lastPresentedSuccessorId=null;
    }
    /** Compatibility entry point for an already accepted sale; producers must not call this. */
    public void recordSellAdvice(Suggestion suggestion) {
        if (suggestion != null && suggestion.getType() == SuggestionType.SELL) recordPresentedAdvice(suggestion,accountContext());
    }

	/**
	 * Prefers the trade already on screen, then the plan's own order.
	 * <p>
	 * Returning null means every row is blocked or skipped, which is a real answer and not a failure.
	 */
	PortfolioAllocation select(PortfolioPlan plan, Set<String> blocked, Set<Integer> skipped)
	{
		return select(plan, blocked, skipped, java.util.Collections.emptySet());
	}

	PortfolioAllocation select(PortfolioPlan plan, Set<String> blocked, Set<Integer> skipped,
		Set<Integer> onOffer)
	{
		PortfolioAllocation chosen = choose(plan, blocked, skipped, onOffer);
		if (chosen != null)
		{
			incumbentItemId = chosen.getCandidate().getItemId();
		}
		return chosen;
	}

	private PortfolioAllocation choose(PortfolioPlan plan, Set<String> blocked, Set<Integer> skipped,
		Set<Integer> onOffer)
	{
		PortfolioAllocation first = null;
		for (PortfolioAllocation allocation : plan.getAllocations())
		{
			if (!acceptable(allocation, blocked, skipped, onOffer))
			{
				continue;
			}
			if (allocation.getCandidate().getItemId() == incumbentItemId)
			{
				return allocation;
			}
			if (first == null)
			{
				first = allocation;
			}
		}
		return first;
	}

	private static boolean acceptable(PortfolioAllocation allocation, Set<String> blocked,
		Set<Integer> skipped, Set<Integer> onOffer)
	{
		com.flippingfriend.core.PortfolioCandidate candidate = allocation.getCandidate();
		if (candidate == null || candidate.getQuantity() <= 0)
		{
			return false;
		}
		// An item with a live offer must not be suggested again. The built-in engine has always
		// excluded these, in its own words, because otherwise "the engine would cheerfully tell you to
		// buy the thing you just bought, over and over, until it filled" -- and the companion path,
		// which is the one that now produces the buy, had no notion of them at all. Holding the
		// incumbent made that worse rather than better: before, the re-ranking would at least rotate
		// away; now it is pinned there until the offer fills.
		if (onOffer != null && onOffer.contains(candidate.getItemId()))
		{
			return false;
		}
		if (skipped != null && skipped.contains(candidate.getItemId()))
		{
			return false;
		}
		String name = candidate.getItemName();
		return blocked == null || name == null
			|| !blocked.contains(name.toLowerCase(java.util.Locale.ROOT));
	}

	public PresentedEntryDecision nextEntryDecisionPresentation(Explainer explainer,
		Set<String> blocked, Set<Integer> skipped, Set<Integer> onOffer)
	{
		return nextEntryDecisionPresentation(explainer, blocked, skipped, onOffer,
			this::fetchEntryDecision);
	}


	/**
	 * Production entry authority. A missing, stale, malformed, unavailable, or mismatched companion
	 * decision freezes new buying instead of activating the plugin's independent ranker.
	 */
	public Suggestion nextEntrySuggestion(Explainer explainer, Set<String> blocked,
		Set<Integer> skipped, Set<Integer> onOffer)
	{
		PresentedEntryDecision presented = nextEntryDecisionPresentation(
			explainer, blocked, skipped, onOffer);
		if (presented != null)
		{
			return presented.getSuggestion();
		}
		return Suggestion.waiting("Companion entry decision unavailable",
			"No new buy will be suggested until the companion returns a current, valid entry decision.");
	}

    /** A collected remainder is only a size preference; re-plan it with fresh account authority. */
    public Suggestion replacementSuggestion(int item,int cap,Explainer explainer,Set<String> blocked,
            Set<Integer> skipped,Set<Integer> onOffer) {
        String requested=accountContext();long generation=ledgerGeneration;
        Suggestion waiting=Suggestion.waiting("Checking your replacement",
            "Waiting for a fresh eligible order within the cancelled remainder.");
        if(item<=0 || cap<=0 || requested==null || skipped.contains(item) || onOffer.contains(item) || offerEvidencePending())return waiting;
        long sentAt=Instant.now().getEpochSecond();
        try {
            var reply=gson.fromJson(request("policy/replacement?item="+item+"&quantityCap="+cap,
                "GET",null,ACCOUNT_READ_TIMEOUT_MILLIS,requested),com.flippingfriend.core.ReplacementEntry.class);
            if(generation!=ledgerGeneration || !java.util.Objects.equals(requested,accountContext()) || offerEvidencePending())return waiting;
            Suggestion result=presentReplacement(reply,item,cap,requested,sentAt,Instant.now().getEpochSecond(),explainer);
            if(result==null || result.getType()==SuggestionType.BUY && blocked.contains(result.getItemName().toLowerCase(java.util.Locale.ROOT)))return waiting;
            return result;
        }catch(Exception unavailable){lastError=unavailable.getMessage();return waiting;}
    }
    static Suggestion presentReplacement(com.flippingfriend.core.ReplacementEntry reply,int item,int cap,String account,
            long sentAt,long receivedAt,Explainer explainer) {
        if(reply==null || !com.flippingfriend.core.ReplacementEntry.VERSION.equals(reply.getVersion())
            || !java.util.Objects.equals(account,reply.getAccountId()) || reply.getItemId()!=item || reply.getQuantityCap()!=cap
            || reply.getAccountObservedAt()<=0 || reply.getAccountObservedAt()>receivedAt || receivedAt-reply.getAccountObservedAt()>150
            || reply.getPlan()==null || reply.getPlan().getCreatedAt()>receivedAt || reply.getPlan().getExpiresAt()<=receivedAt
            || reply.getDecision()==null || !validEntryDecision(reply.getDecision())
            || !"companion-entry-1".equals(reply.getDecision().getPolicyVersion())
            || reply.getDecision().getDecidedAt()<sentAt || reply.getDecision().getDecidedAt()>receivedAt
            || receivedAt-reply.getDecision().getDecidedAt()>5
            || reply.getDecision().getInputObservedAt()>reply.getDecision().getDecidedAt()
            || receivedAt-reply.getDecision().getInputObservedAt()>150) return null;
        var presented=EntryDecisionPresenter.present(reply.getDecision(),reply.getPlan(),explainer,account);
        if(presented==null)return null;
        var result=presented.getSuggestion();
        if(result.getType()==SuggestionType.BUY && reply.getPlan().getAllocations().stream().noneMatch(a->a!=null
            && a.getCandidate()!=null && a.getCandidate().getItemId()==item && a.getCandidate().getQuantity()==result.getQuantity()
            && a.getCandidate().getBuyPrice()==result.getPrice() && a.getCandidate().getExpiresAt()>receivedAt))return null;
        return result.getType()!=SuggestionType.BUY || result.getItemId()==item && result.getQuantity()>0 && result.getQuantity()<=cap
            ?result:null;
    }

	PresentedEntryDecision nextEntryDecisionPresentation(Explainer explainer,
		Set<String> blocked, Set<Integer> skipped, Set<Integer> onOffer,
		EntryDecisionFetcher fetcher)
	{
		String requestedAccount = accountContext();
        if(offerEvidencePending()){lastPlan=null;return null;}
		PortfolioPlan plan = lastPlan;
		if (plan == null || plan.getExpiresAt() <= Instant.now().getEpochSecond()
			|| fetcher == null)
		{
			return null;
		}
		int rank = 1;
		if ("READY".equals(plan.getStatus()))
		{
			PortfolioAllocation allocation = select(plan, blocked, skipped, onOffer);
			if (allocation == null) return null;
			rank = allocation.getRank();
		}
		PolicyDecision decision = fetcher.fetch(plan.getCorrelationId(), rank);
		if (decision == null) return null;
		PresentedEntryDecision presented = EntryDecisionPresenter.present(decision, plan, explainer, requestedAccount);
		if (presented == null)
		{
			lastError = "Companion entry decision did not match the current plan.";
		}
		return presented;
	}

	@FunctionalInterface
	interface EntryDecisionFetcher
	{
		PolicyDecision fetch(String planId, int allocationRank);
	}
    private boolean offerEvidencePending() {
        try { if(outbox.pending().isEmpty())return false; }
        catch(Exception unavailable){ /* Missing delivery proof also withdraws entries. */ }
        lastError="Waiting for durable offer acknowledgements";return true;
    }

	public PolicyDecision fetchEntryDecision(String planId, int allocationRank)
	{
		if (planId == null || planId.trim().isEmpty())
		{
			lastError = "Entry decision plan identity is missing.";
			return null;
		}
		if (allocationRank <= 0)
		{
			lastError = "Entry decision allocation rank is invalid.";
			return null;
		}
		try
		{
			String encoded = URLEncoder.encode(planId, StandardCharsets.UTF_8.name());
			return consumeEntryDecision(request("policy/entry?planId=" + encoded
				+ "&rank=" + allocationRank, "GET", null));
		}
		catch (Exception ex)
		{
			lastError = ex.getMessage();
			return null;
		}
	}

    /** Final sale authority is bound to the canonical source and exact displayed order. */
    public Suggestion authorizeSale(Suggestion proposed) {
        if(proposed==null || proposed.getType()!=SuggestionType.MODIFY_SELL
            && !(proposed.getType()==SuggestionType.SELL && proposed.isProfitKnown()))return proposed;
        String requestedAccount=accountContext();long now=Instant.now().getEpochSecond();
        int slot=proposed.getType()==SuggestionType.MODIFY_SELL?proposed.getSlot():-1;
        String source=currentSaleSource(proposed,slot);
        String reason="SALE_DELIVERY_PENDING";
        if(source!=null)try {
            String repriceSource=slot<0?proposed.getSaleRepriceSource():null;
            String key=proposed.getItemId()+":"+proposed.getPrice()+":"+proposed.getQuantity()+":"+source+":"+proposed.isLossCut()+":"+slot
                +":"+(repriceSource==null?"":repriceSource);
            String path="policy/sale?item="+proposed.getItemId()+"&price="+proposed.getPrice()+"&quantity="+proposed.getQuantity()
                +"&source="+URLEncoder.encode(source,StandardCharsets.UTF_8.name())+"&cut="+proposed.isLossCut()+"&slot="+slot;
            if(repriceSource!=null && !repriceSource.isBlank())path+="&repriceSource="+URLEncoder.encode(repriceSource,StandardCharsets.UTF_8.name());
            PolicyDecision decision=gson.fromJson(request(path,"GET",null,ACCOUNT_READ_TIMEOUT_MILLIS,requestedAccount),PolicyDecision.class);
            long receivedAt=Instant.now().getEpochSecond();
            if(validSaleDecision(decision,key,source,slot,now) && decision.getDecidedAt()<=receivedAt
                && receivedAt-decision.getDecidedAt()<=5 && receivedAt-decision.getInputObservedAt()<=150
                && java.util.Objects.equals(requestedAccount,accountContext()) && !offerEvidencePending()
                && source.equals(currentSaleSource(proposed,slot))) {
                    if(slot>=0)proposed.bindSaleRepriceSource(source);
                    lastError="";return proposed;
                }
            reason=decision==null?"SALE_AUTHORITY_UNAVAILABLE":decision.getAction()==OfferLifecycleAction.WAIT
                ?decision.getReasonCode():"SALE_SOURCE_CHANGED";
        }catch(Exception unavailable){reason="SALE_AUTHORITY_UNAVAILABLE";}
        lastError="Sale verification: "+reason;
        // A refused routine reduction leaves an already listed offer working. It is not an
        // account-wide entry veto: continue through the independently authorized entry path.
        // Unlisted stock and urgent loss cuts retain their recovery/priority WAIT.
        return slot>=0 && !proposed.isLossCut()?null:saleWaiting(reason);
    }
    private static Suggestion saleWaiting(String reason) {
        if("SALE_QUOTE_STALE".equals(reason))return Suggestion.waiting("Waiting for a fresh repricing quote",
            "The last trade is too old to justify cancelling or lowering this sell offer. Its current price stays in place.");
        if("SALE_ITEM_PAUSED".equals(reason) || "SALE_ACQUISITION_UNAVAILABLE".equals(reason))
            return Suggestion.waiting("Checking purchase evidence",
                "The remaining quantity or purchase cost is unresolved. See Missed flips; no sale or disposal is assumed.");
        if("SALE_STOCK_NOT_OBSERVED".equals(reason))return Suggestion.waiting("Locate the recorded stock",
            "The purchase is retained, but these units are not currently visible. Collect them or open their container before listing.");
        if("SALE_STOCK_NOT_IN_INVENTORY".equals(reason))return Suggestion.waiting("Move the stock to inventory",
            "Banked, equipped and uncollected stock stays on record. Only the captured inventory quantity can be listed now.");
        if("SALE_EXACT_COST_FLOOR".equals(reason))return Suggestion.waiting("Refreshing the non-loss sell price",
            "This price would not cover the exact recorded purchase cost after tax.");
        if("SALE_EXIT_CHANGED".equals(reason) || "SALE_PRICE_NOT_AUTHORIZED".equals(reason))
            return Suggestion.waiting("Refreshing the sell price", "The market price or exit policy changed. Updating the sell instruction.");
        return Suggestion.waiting("Refreshing sale evidence",
            "Waiting for current purchase and offer records to agree before showing a sell instruction.");
    }
    private String currentSaleSource(Suggestion proposed,int slot) {
        var current=tradingLedger();
        if(current==null || current.getTradingPositions()==null)return null;
        if(slot>=0) {
            OfferEvent offer=canonicalOffers.get(slot);
            return offer!=null && offer.isAuthenticatedObservation() && !offer.isBuying()
                && "SELLING".equals(offer.getEventType()) && offer.getItemId()==proposed.getItemId()
                && offer.getTotalQuantity()-offer.getFilledQuantity()==proposed.getQuantity()?offer.getEventId():null;
        }
        for(var p:current.getTradingPositions())if(p.getItemId()==proposed.getItemId() && p.getQuantity()>=proposed.getQuantity())return p.getSourceEventId();
        return null;
    }
    static boolean validSaleDecision(PolicyDecision decision,String identity,String source,int slot,long now) {
        return decision!=null && "1".equals(decision.getSchemaVersion()) && "companion-sale-8".equals(decision.getPolicyVersion())
            && identity.equals(decision.getCandidateId()) && source.equals(decision.getRecommendationId())
            && decision.getDecisionType()==(slot<0?PolicyDecisionType.EXIT:PolicyDecisionType.SELL_MAINTENANCE)
            && decision.getAction()==(slot<0?OfferLifecycleAction.PLACE_SELL:OfferLifecycleAction.CANCEL_SELL)
            && decision.getAbstentionReason()==PolicyAbstentionReason.NONE && "SALE_AUTHORIZED".equals(decision.getReasonCode())
            && decision.getDecidedAt()>=now && decision.getDecidedAt()<=now+5 && decision.getInputObservedAt()<=decision.getDecidedAt()
            && now-decision.getInputObservedAt()<=150;
    }

	PolicyDecision consumeEntryDecision(String response)
	{
		PolicyDecision decision;
		try
		{
			decision = response == null || response.trim().isEmpty()
				? null : gson.fromJson(response, PolicyDecision.class);
		}
		catch (RuntimeException malformed)
		{
			decision = null;
		}
		if (!validEntryDecision(decision))
		{
			lastError = "Companion returned an invalid entry decision.";
			return null;
		}
		lastError = "";
		return decision;
	}

	OfferEvent currentCanonicalOpenBuy()
	{
		accountContext();
		OfferEvent selected = null;
		for (OfferEvent event : canonicalOffers.values())
		{
			if (!validOpenBuyForMaintenance(event)) continue;
			if (selected == null || event.getSlot() < selected.getSlot()) selected = event;
		}
		return selected;
	}
    /** Frozen comparable original BUY duration, never refreshed advice for this item. */
    public double originalBuyExpectedMinutes(int slot) {
        String account=accountContext();
        var event=canonicalOffers.get(slot);
        return event!=null && java.util.Objects.equals(account,event.getAccountId())?originalBuyDuration(event):Double.NaN;
    }
    private static double originalBuyDuration(OfferEvent event) {
        return com.flippingfriend.core.BuyMaintenanceTiming.originalExpectedMinutes(event);
    }
    /** Freeze patience only after durable timing evidence is readable; pending ACK is not an outage. */
    public long freezeBuyMaintenanceDeadline(TrackedOffer expected,com.flippingfriend.session.OfferTracker tracker,
            double fallbackMinutes,long now) {
        if(expected==null || tracker==null || !expected.isBuying() || !"BUYING".equals(expected.getState()))return 0;
        String account=accountContext();if(account==null)return 0;
        var event=canonicalOffers.get(expected.getSlot());
        String identity=expected.getEpisodeIdentity()==null?expected.getSlot()+"@"+expected.getFirstSeen()+":"+expected.getItemId():expected.getEpisodeIdentity();
        if(!validObservedOpenBuy(event) || event.getObservedAt()<=0 || event.getObservedAt()>now
            || !account.equals(event.getAccountId()) || event.getItemId()!=expected.getItemId()
            || event.getPrice()!=expected.getPrice() || event.getTotalQuantity()!=expected.getTotalQuantity()
            || event.getFirstSeenAt()!=expected.getFirstSeen() || !identity.equals(event.getOfferIdentity())
            || event.getFilledQuantity()!=expected.getQuantityFilled() || event.getSpent()!=expected.getSpent())return 0;
        if(event.getAcceptedBuyPlanEvidenceVersion()!=null && !"1".equals(event.getAcceptedBuyPlanEvidenceVersion())
            || event.getAcceptedBuyPlanProof()!=null && !event.getAcceptedBuyPlanProof().matchesEpisode(event))return 0;
        try {
            outbox.pending(); // Unsaved append or malformed retained evidence must not become a permanent fallback.
            if(!account.equals(accountContext()) || !event.getSessionId().equals(outbox.sessionId()))return 0;
            return tracker.freezeBuyMaintenanceDeadline(expected.getSlot(),expected.getContinuationIdentity(),
                originalBuyDuration(event),fallbackMinutes,now,account);
        }catch(Exception unavailable){return 0;}
    }

    /** Diagnostic only: the original actual sale order forecast, never an acquisition estimate. */
    public double originalSellExpectedMinutes(TrackedOffer expected) {
        if(expected==null || expected.isBuying())return Double.NaN;
        String account=accountContext();var event=canonicalOffers.get(expected.getSlot());
        String identity=expected.getEpisodeIdentity()==null?expected.getSlot()+"@"+expected.getFirstSeen()+":"+expected.getItemId():expected.getEpisodeIdentity();
        if(event==null || !java.util.Objects.equals(account,event.getAccountId()) || !event.isAuthenticatedObservation()
            || event.isBuying() || !"SELLING".equals(event.getEventType()) || !event.isPredictionComparable()
            || event.getItemId()!=expected.getItemId() || event.getPrice()!=expected.getPrice()
            || event.getTotalQuantity()!=expected.getTotalQuantity()
            || event.getPredictionSourceEventId()==null || event.getFirstSeenAt()!=expected.getFirstSeen()
            || !identity.equals(event.getOfferIdentity()))return Double.NaN;
        var forecast=event.getExecutionForecast();
        return forecast!=null && forecast.matches(expected.getItemId(),false,expected.getPrice(),expected.getTotalQuantity())
            ?forecast.getExpectedTotalSeconds()/60:Double.NaN;
    }
    public double originalBuyExpectedMinutes(TrackedOffer expected) {
        if(expected==null || !expected.isBuying())return Double.NaN;
        String account=accountContext();var event=canonicalOffers.get(expected.getSlot());
        String identity=expected.getEpisodeIdentity()==null?expected.getSlot()+"@"+expected.getFirstSeen()+":"+expected.getItemId():expected.getEpisodeIdentity();
        if(event==null || !java.util.Objects.equals(account,event.getAccountId()) || event.getItemId()!=expected.getItemId() || event.getPrice()!=expected.getPrice()
            || event.getTotalQuantity()!=expected.getTotalQuantity() || event.getFirstSeenAt()!=expected.getFirstSeen()
            || !identity.equals(event.getOfferIdentity()))return Double.NaN;
        return originalBuyDuration(event);
    }

	private void rememberCanonicalOffer(OfferEvent event)
	{
		if (event == null || event.getSlot() < 0) return;
		boolean observedSale=event.isAuthenticatedObservation() && !event.isBuying()
			&& "SELLING".equals(event.getEventType()) && event.getItemId()>0 && event.getPrice()>0
			&& event.getTotalQuantity()>event.getFilledQuantity() && event.getFilledQuantity()>=0
			&& !blank(event.getEventId()) && !blank(event.getOfferIdentity());
		if (validObservedOpenBuy(event) || observedSale) canonicalOffers.put(event.getSlot(), event);
		else canonicalOffers.remove(event.getSlot());
	}

	public Suggestion selectBuyMaintenanceCancellation(Suggestion existing)
	{
		OfferEvent canonicalOpenBuy = currentCanonicalOpenBuy();
		return selectBuyMaintenanceCancellation(existing, canonicalOpenBuy,
			this::fetchBuyMaintenanceDecision);
	}
	Suggestion selectBuyMaintenanceCancellation(Suggestion existing, OfferEvent canonicalOpenBuy,
		BuyMaintenanceDecisionFetcher fetcher)
	{
		PresentedBuyMaintenanceDecision companion =
			nextBuyMaintenancePresentation(canonicalOpenBuy, fetcher);
		return BuyMaintenanceCancellationSelector.select(existing, canonicalOpenBuy, companion);
	}

	BuyMaintenanceParityResult compareBuyMaintenancePresentation(Suggestion existing,
		OfferEvent currentOpenBuy)
	{
		return compareBuyMaintenancePresentation(existing, currentOpenBuy,
			this::fetchBuyMaintenanceDecision);
	}

	BuyMaintenanceParityResult compareBuyMaintenancePresentation(Suggestion existing,
		OfferEvent currentOpenBuy, BuyMaintenanceDecisionFetcher fetcher)
	{
		PresentedBuyMaintenanceDecision shadow =
			nextBuyMaintenancePresentation(currentOpenBuy, fetcher);
		return BuyMaintenanceParity.compare(existing, shadow);
	}

	PresentedBuyMaintenanceDecision nextBuyMaintenancePresentation(OfferEvent currentOpenBuy)
	{
		return nextBuyMaintenancePresentation(currentOpenBuy, this::fetchBuyMaintenanceDecision);
	}

	PresentedBuyMaintenanceDecision nextBuyMaintenancePresentation(OfferEvent currentOpenBuy,
		BuyMaintenanceDecisionFetcher fetcher)
	{
		if (!validOpenBuyForMaintenance(currentOpenBuy) || fetcher == null) return null;
		PolicyDecision decision = fetcher.fetch(currentOpenBuy.getOfferIdentity(),
			currentOpenBuy.getRecommendationId());
		if (decision == null) return null;
		PresentedBuyMaintenanceDecision presented =
			BuyMaintenanceDecisionPresenter.present(decision, currentOpenBuy);
		if (presented == null)
		{
			lastError = "Companion buy maintenance decision did not match the current offer.";
		}
		return presented;
	}

	@FunctionalInterface
	interface BuyMaintenanceDecisionFetcher
	{
		PolicyDecision fetch(String offerIdentity, String recommendationId);
	}

    /** Retain copied current BUY evidence for workflow clocks without creating attribution. */
    private static boolean validObservedOpenBuy(OfferEvent offer) {
        return offer!=null && offer.isAuthenticatedObservation() && offer.isBuying() && "BUYING".equals(offer.getEventType())
            && offer.getSlot()>=0 && offer.getSlot()<8 && offer.getItemId()>0 && offer.getPrice()>0
            && offer.getTotalQuantity()>offer.getFilledQuantity() && offer.getFilledQuantity()>=0 && offer.getSpent()>=0
            && offer.getFirstSeenAt()>0 && offer.getObservedAt()>=offer.getFirstSeenAt() && offer.getSequence()>0
            && !blank(offer.getAccountId()) && !blank(offer.getEventId()) && !blank(offer.getSessionId()) && !blank(offer.getOfferIdentity());
    }

	private static boolean validOpenBuyForMaintenance(OfferEvent offer)
	{
		return offer != null && offer.isBuying() && "BUYING".equals(offer.getEventType())
			&& offer.getTotalQuantity() > offer.getFilledQuantity()
			&& !blank(offer.getOfferIdentity()) && !blank(offer.getRecommendationId());
	}

	PolicyDecision fetchBuyMaintenanceDecision(String offerIdentity, String recommendationId)
	{
		if (blank(offerIdentity) || blank(recommendationId)) return null;
		try
		{
			PolicyDecision decision = get("policy/buy-maintenance", PolicyDecision.class);
			return validBuyMaintenanceDecision(decision, offerIdentity, recommendationId)
				? decision : null;
		}
		catch (Exception ex)
		{
			lastError = ex.getMessage();
			return null;
		}
	}

	static boolean validBuyMaintenanceDecision(PolicyDecision decision, String offerIdentity,
		String recommendationId)
	{
		if (decision == null || !"1".equals(decision.getSchemaVersion())
			|| blank(decision.getPolicyVersion()) || blank(decision.getDecisionId())
			|| decision.getDecisionType() != PolicyDecisionType.BUY_MAINTENANCE
			|| decision.getAction() == null || decision.getAbstentionReason() == null
			|| blank(decision.getReasonCode()) || blank(decision.getCandidateId())
			|| blank(decision.getRecommendationId())
			|| !decision.getCandidateId().equals(offerIdentity)
			|| !decision.getRecommendationId().equals(recommendationId)
			|| decision.getDecidedAt() < 0 || decision.getInputObservedAt() < 0
			|| decision.getInputObservedAt() > decision.getDecidedAt()) return false;
		if (decision.getAction() == OfferLifecycleAction.WAIT)
			return decision.getAbstentionReason() != PolicyAbstentionReason.NONE;
		return (decision.getAction() == OfferLifecycleAction.HOLD
			|| decision.getAction() == OfferLifecycleAction.CANCEL_BUY)
			&& decision.getAbstentionReason() == PolicyAbstentionReason.NONE;
	}

	static boolean validEntryDecision(PolicyDecision decision)
	{
		if (decision == null || !"1".equals(decision.getSchemaVersion())
			|| blank(decision.getPolicyVersion()) || blank(decision.getDecisionId())
			|| decision.getDecisionType() != PolicyDecisionType.ENTRY
			|| decision.getAction() == null || decision.getAbstentionReason() == null
			|| blank(decision.getReasonCode()) || blank(decision.getRecommendationId())
			|| decision.getDecidedAt() < 0 || decision.getInputObservedAt() < 0
			|| decision.getInputObservedAt() > decision.getDecidedAt())
		{
			return false;
		}
		if (decision.getAction() == OfferLifecycleAction.WAIT)
		{
			return decision.getAbstentionReason() != PolicyAbstentionReason.NONE;
		}
		return decision.getAction() == OfferLifecycleAction.PLACE_BUY
			&& decision.getAbstentionReason() == PolicyAbstentionReason.NONE
			&& !blank(decision.getCandidateId());
	}

	private static boolean blank(String value)
	{
		return value == null || value.trim().isEmpty();
	}

	public Suggestion nextAction()
	{
		String requestedAccount=accountContext();long generation=replayGeneration.get();
		if(requestedAccount==null || offerEvidencePending())return null;
		try
		{
			var action=get("action", CompanionAction.class);
            if(generation!=replayGeneration.get() || !java.util.Objects.equals(requestedAccount,accountContext())
                || offerEvidencePending())return null;
			return toSuggestion(action);
		}
		catch (Exception ex)
		{
			lastError = ex.getMessage();
			return null;
		}
	}

	static Suggestion toSuggestion(CompanionAction action)
	{
		if (action == null || action.getType() != CompanionActionType.COLLECT
				|| action.getItemId() <= 0 || action.getSlot() < 0)
		{
			return null;
		}
		return Suggestion.builder(SuggestionType.COLLECT)
				.item(action.getItemId(), action.getItemName())
				.slot(action.getSlot())
				.quantity(action.getQuantity())
				.headline(action.getHeadline())
				.detail(action.getDetail())
				.build();
	}

	public String lastError() { return lastError; }

	/** Last plan fetched by the engine thread; Swing reads this cached immutable object only. */
	public PortfolioPlan lastPlan() { accountContext(); return lastPlan; }

    /** Fresh companion arithmetic only; local journal profit is never a risk fallback. */
    public com.flippingfriend.core.AccountRiskSnapshot currentRisk() {
        String account=accountContext();
        PortfolioPlan plan=lastPlan;
        var risk=plan==null?null:plan.getCanonicalRisk();
        long now=Instant.now().getEpochSecond();
        try {
            if(offerEvidencePending() || risk==null || !risk.isAvailable() || plan.getExpiresAt()<=now
                || !java.util.Objects.equals(account,risk.getAccountId())
                || !java.util.Objects.equals(outbox.sessionId(),risk.getSessionId())
                || risk.getEvaluatedAt()>now || now-risk.getEvaluatedAt()>150
                || risk.getValidUntil()<now
                || risk.getMarketObservedAt()>now || now-risk.getMarketObservedAt()>120)return null;
            return risk;
        }catch(Exception unavailable){return null;}
    }
	public CompanionHealth lastHealth() { accountContext(); return lastHealth; }

	/**
	 * The buy already on screen, if it is still one the player could act on.
	 * <p>
	 * Held only while it remains genuinely available: not blocked, not skipped, and not already
	 * sitting in a slot. Anything else and the gap is real and should be reported.
	 */
	private Suggestion held(Set<String> blocked, Set<Integer> skipped, Set<Integer> onOffer)
	{
		Suggestion previous = lastBuy;
		if (previous == null || previous.getType() != SuggestionType.BUY)
		{
			return null;
		}
		int itemId = previous.getItemId();
		if ((skipped != null && skipped.contains(itemId))
			|| (onOffer != null && onOffer.contains(itemId)))
		{
			return null;
		}
		String name = previous.getItemName();
		if (blocked != null && name != null
			&& blocked.contains(name.toLowerCase(java.util.Locale.ROOT)))
		{
			return null;
		}
		return previous;
	}

	private Suggestion unavailable(String detail)
	{
		return Suggestion.waiting("Companion unavailable", "No new buy will be suggested until the local portfolio companion has fresh market data. " + detail);
	}

        private void replayPendingOffers()
        {
                long generation = replayGeneration.get();
                replayPendingOffers(event -> request("events/ge-offer", "POST", gson.toJson(event), TIMEOUT_MILLIS, event.getAccountId()), generation);
        }

        boolean replayPendingOffers(OfferEventSender sender)
        {
                return replayPendingOffers(sender, replayGeneration.get()) == ReplayResult.DRAINED;
        }

        private ReplayResult replayPendingOffers(OfferEventSender sender, long generation)
        {
                synchronized(replayScheduling) {
                    if (!replayRunning.compareAndSet(false, true)) {
                        if(replayLifecycleIsCurrent(generation))replayRescheduleGeneration=generation;
                        return ReplayResult.BUSY;
                    }
                    if(replayRescheduleGeneration==generation)replayRescheduleGeneration=-1;
                }
                ReplayResult result = ReplayResult.FAILED;
                try
                {
                        if (!replayLifecycleIsCurrent(generation)) return ReplayResult.STALE;
                        outbox.retryUnsaved();
                        var acknowledged=new java.util.ArrayList<OfferEvent>();boolean failed=false;
                        for (OfferEvent event : outbox.pending())
                        {
                                if (!replayLifecycleIsCurrent(generation)) return ReplayResult.STALE;
                                String response;
                                try { response = sender.send(event); }
                                catch (Exception failure)
                                {
                                        if (!replayLifecycleIsCurrent(generation)) return ReplayResult.STALE;
                                        lastError = failure.getMessage();
                                        failed=true;break;
                                }
                                if (!replayLifecycleIsCurrent(generation)) return ReplayResult.STALE;
                                if (!validAcknowledgement(event, response))
                                {
                                        if (!replayLifecycleIsCurrent(generation)) return ReplayResult.STALE;
                                        lastError = "Companion returned an invalid offer acknowledgement.";
                                        failed=true;break;
                                }
                                acknowledged.add(event);
                                if (!replayLifecycleIsCurrent(generation)) return ReplayResult.STALE;
                        }
                        if (!replayLifecycleIsCurrent(generation)) return ReplayResult.STALE;
                        if(!acknowledged.isEmpty() && !outbox.acknowledgePrefix(acknowledged,acknowledged.get(0).getAccountId()))
                            throw new IllegalStateException("Offer acknowledgement prefix changed before persistence");
                        result=failed?ReplayResult.FAILED:outbox.pending().isEmpty()?ReplayResult.DRAINED:ReplayResult.MORE_PENDING;
                        return result;
                }
                catch (Exception failure)
                {
                        if (!replayLifecycleIsCurrent(generation)) return ReplayResult.STALE;
                        lastError = failure.getMessage();
                        return ReplayResult.FAILED;
                }
                finally
                {
                    synchronized(replayScheduling) {
                        if (replayLifecycleIsCurrent(generation))
                        {
                                long now = retryClock.getAsLong();
                                if (result == ReplayResult.DRAINED)
                                {
                                        retryDelayMillis = INITIAL_RETRY_DELAY_MILLIS;
                                        nextRetryAtMillis = Long.MAX_VALUE;
                                }
                                else if (result == ReplayResult.FAILED)
                                {
                                        nextRetryAtMillis = now + retryDelayMillis;
                                        retryDelayMillis = Math.min(MAX_RETRY_DELAY_MILLIS, retryDelayMillis * 2);
                                }
                                else if(result==ReplayResult.MORE_PENDING) {
                                    retryDelayMillis=INITIAL_RETRY_DELAY_MILLIS;nextRetryAtMillis=now;
                                }
                                if(replayRescheduleGeneration==generation) {
                                    if(result==ReplayResult.DRAINED || result==ReplayResult.MORE_PENDING)nextRetryAtMillis=now;
                                    replayRescheduleGeneration=-1;
                                }
                        }
                        replayRunning.set(false);
                    }
                }
        }

        private boolean replayLifecycleIsCurrent(long generation)
        {
                return replayEnabled && generation == replayGeneration.get();
        }

        private enum ReplayResult { DRAINED, FAILED, STALE, BUSY, MORE_PENDING }

        public boolean requestPendingOfferReplay(Executor executor)
        {
                long generation = replayGeneration.get();
                if (executor == null || !replayEnabled || retryClock.getAsLong() < nextRetryAtMillis
                        || !retryRequestGeneration.compareAndSet(-1, generation)) return false;
                try
                {
                        executor.execute(() ->
                        {
                                try
                                {
                                        if (replayEnabled && generation == replayGeneration.get())
                                        {
                                                replayPendingOffers(event -> request("events/ge-offer", "POST", gson.toJson(event), TIMEOUT_MILLIS, event.getAccountId()), generation);
                                        }
                                }
                                finally
                                {
                                        retryRequestGeneration.compareAndSet(generation, -1);
                                }
                        });
                        return true;
                }
                catch (RuntimeException rejected)
                {
                        retryRequestGeneration.compareAndSet(generation, -1);
                        return false;
                }
        }

        public synchronized void resumeOfferReplay()
        {
                accountContext();
                replayGeneration.incrementAndGet();
                ledgerGeneration++;
                performanceGeneration++;performance=null;lastPerformanceRequest=0;
                tradeReview=null;tradeReviewError="";
                replayEnabled = true;
                retryDelayMillis = INITIAL_RETRY_DELAY_MILLIS;
                nextRetryAtMillis = 0;
        }

        public synchronized void pauseOfferReplay()
        {
                replayEnabled = false;
                performanceGeneration++;performance=null;lastPerformanceRequest=0;
                tradeReview=null;tradeReviewError="";
                replayGeneration.incrementAndGet();
                ledgerGeneration++;
                nextRetryAtMillis = Long.MAX_VALUE;
                retryDelayMillis = INITIAL_RETRY_DELAY_MILLIS;
                retryRequestGeneration.set(-1);
                lastHistoryLedger=null;
                lastLedger=null;
        }

        long nextRetryAtMillis() { return nextRetryAtMillis; }
        long retryDelayMillis() { return retryDelayMillis; }

        @FunctionalInterface
        interface OfferEventSender
        {
                String send(OfferEvent event) throws Exception;
        }

        boolean consumeAcknowledgement(OfferEvent event, String response) throws Exception
        {
                if(!validAcknowledgement(event,response))return false;
                return outbox.acknowledge(event.getEventId(),event.getSessionId(),event.getSequence());
        }
        private boolean validAcknowledgement(OfferEvent event,String response) {
		if (event != null && event.getAccountId() != null
			&& !event.getAccountId().equals(storage.accountId())) return false;
                OfferEventAcknowledgementResponse ack;
                try { ack = gson.fromJson(response, OfferEventAcknowledgementResponse.class); }
                catch (RuntimeException malformed) { return false; }
                if (event == null || ack == null || !ack.isAccepted()
                        || !same(event.getEventId(), ack.getEventId())
                        || !same(event.getSessionId(), ack.getSessionId())
                        || event.getSequence() != ack.getSequence()) return false;
                return true;
        }

        private static boolean same(String left, String right) { return left != null && left.equals(right); }

	private void post(String path, Object body)
	{
		try { request(path, "POST", gson.toJson(body), TIMEOUT_MILLIS,
			body instanceof AccountSnapshot ? ((AccountSnapshot) body).getAccountId() : accountContext()); }
		catch (Exception ex) { lastError = ex.getMessage(); }
	}

	private <T> T get(String path, Class<T> type) throws Exception
	{
		return gson.fromJson(request(path, "GET", null), type);
	}

	/**
	 * The whole market feed as the companion has it, or null when it is not running.
	 * <p>
	 * This is what makes a cold start warm. The companion polls the wiki every sixty seconds whether
	 * or not the game is open, so on the loopback this is a few megabytes of already-fetched JSON and
	 * a few milliseconds -- against a fresh download from the internet, which the plugin used to do on
	 * every launch while refusing to advise. The data is a minute old at worst, so nothing is decided
	 * on a stale price to buy the speed.
	 * <p>
	 * A longer read timeout than the rest: the payload is genuinely large, and the ordinary
	 * three-quarters of a second is sized for a plan, not a feed.
	 */
	public MarketFeed fetchMarketFeed()
	{
		try
		{
			return gson.fromJson(request("market/snapshot", "GET", null, FEED_TIMEOUT_MILLIS),
				MarketFeed.class);
		}
		catch (Exception ex)
		{
			// Not an error. The companion may simply not be running, and everything still works from
			// the wiki -- more slowly, which is the behaviour this replaces rather than breaks.
			lastError = ex.getMessage();
			return null;
		}
	}

	/**
	 * One item's history from the companion's warm cache, or null when it has none.
	 * <p>
	 * The companion keeps a persistent per-item cache and warms it continuously. Asking it first
	 * turns the plugin's sixty sequential wiki round trips -- the "checking the market" wait -- into
	 * sixty loopback reads.
	 */
	public java.util.List<Candle> fetchSeries(int itemId, String timestep)
	{
		try
		{
			Candle[] series = gson.fromJson(
				request("market/series?id=" + itemId + "&timestep=" + timestep, "GET", null),
				Candle[].class);
			return series == null || series.length == 0 ? null : java.util.Arrays.asList(series);
		}
		catch (Exception ex)
		{
			return null;
		}
	}

	/** The three all-item payloads, in the wiki's own shape. */
	public static final class MarketFeed
	{
		private long observedAt;
		private com.google.gson.JsonObject latest;
		private com.google.gson.JsonObject fiveMinute;
		private com.google.gson.JsonObject hourly;

		public long getObservedAt() { return observedAt; }
		public com.google.gson.JsonObject getLatest() { return latest; }
		public com.google.gson.JsonObject getFiveMinute() { return fiveMinute; }
		public com.google.gson.JsonObject getHourly() { return hourly; }
	}

	private String request(String path, String method, String body) throws Exception
	{
		return request(path, method, body, "GET".equals(method)?ACCOUNT_READ_TIMEOUT_MILLIS:TIMEOUT_MILLIS);
	}

	private String request(String path, String method, String body, int readTimeoutMillis)
		throws Exception
	{
		return request(path, method, body, readTimeoutMillis, accountContext());
	}

	private String request(String path, String method, String body, int readTimeoutMillis, String accountId)
		throws Exception
	{
		boolean accountRequest = !path.startsWith("market/") && !path.equals("health");
		if (accountRequest)
		{
			com.flippingfriend.core.AccountIdentity.require(accountId);
			if (!accountId.equals(accountContext())) throw new IllegalStateException("Account changed before request");
		}
		String token = token();
		if (token == null) throw new IllegalStateException("Start the Flipping Friend companion first.");
		// An older companion has no account namespace and returns 404 before accepting any mutation.
		String endpoint = accountRequest ? "accounts/" + accountId + "/" + path : path;
		HttpURLConnection connection = (HttpURLConnection) new URL(base + endpoint).openConnection();
		connection.setRequestMethod(method);
		connection.setConnectTimeout(TIMEOUT_MILLIS);
		connection.setReadTimeout(readTimeoutMillis);
		connection.setRequestProperty("X-Flipping-Friend-Token", token);
		if (accountId != null) connection.setRequestProperty(com.flippingfriend.core.AccountIdentity.HEADER, accountId);
		connection.setRequestProperty("Content-Type", "application/json");
		if (body != null)
		{
			connection.setDoOutput(true);
			try (OutputStream output = connection.getOutputStream()) { output.write(body.getBytes(StandardCharsets.UTF_8)); }
		}
		int code = connection.getResponseCode();
		try (InputStream input = code < 200 || code >= 300 ? connection.getErrorStream() : connection.getInputStream())
		{
			if (code < 200 || code >= 300) throw new IllegalStateException("Companion returned " + code);
			String response = new String(input.readAllBytes(), StandardCharsets.UTF_8);
			if (accountRequest && (!accountId.equals(connection.getHeaderField(com.flippingfriend.core.AccountIdentity.HEADER))
				|| !com.flippingfriend.core.AccountIdentity.PROTOCOL.equals(
					connection.getHeaderField(com.flippingfriend.core.AccountIdentity.PROTOCOL_HEADER))))
				throw new IllegalStateException("Companion does not confirm account-scoped protocol");
			if (accountRequest && !accountId.equals(accountContext()))
				throw new IllegalStateException("Account changed during request");
			return response;
		}
		finally { connection.disconnect(); }
	}

	private String token() throws Exception
	{
		Path file = storage.root().resolve("companion").resolve("companion.properties");
		if (!Files.isRegularFile(file)) return null;
		Properties properties = new Properties();
		try (java.io.Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) { properties.load(reader); }
		return properties.getProperty("token");
	}
}
