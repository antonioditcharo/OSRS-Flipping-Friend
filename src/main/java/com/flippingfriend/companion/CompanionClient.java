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
	/** The market feed is megabytes rather than a plan, so it gets its own budget. */
	private static final int FEED_TIMEOUT_MILLIS = 5_000;
        private static final long INITIAL_RETRY_DELAY_MILLIS = 2_000;
        private static final long MAX_RETRY_DELAY_MILLIS = 60_000;
	private final PluginStorage storage;
	private final Gson gson;
	private final SuggestionLedger ledger;
        private final OfferEventOutbox outbox;
        private final LongSupplier retryClock;
        private final AtomicBoolean replayRunning = new AtomicBoolean();
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
	private volatile String lastError = "Companion has not been contacted.";
	private volatile PortfolioPlan lastPlan;
	private volatile CompanionHealth lastHealth;
	private volatile com.flippingfriend.core.AccountLedgerSnapshot lastLedger;
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
			canonicalOffers.clear(); ledger.clear();
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
                this.storage = storage;
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
            for(int slot=0;slot<8;slot++) {
                if(observed.get(slot)!=null)onOffer.add(observed.get(slot).getItemId());
                OfferEvent event=observed.get(slot)==null
                    ? publishOfferCleared(slot,expectedAccountId,true,observation.getObservedAt(),false)
                    : publishOffer(observed.get(slot),expectedAccountId,true,observation.getObservedAt(),false);
                if(event==null)return;
                receipt.put(slot,event.getEventId());
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
		if(observation!=null) snapshot=snapshot.withOfferReadSet(receipt);
		post("events/account-state", snapshot);
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

        private OfferEvent publishOffer(TrackedOffer offer, String expectedAccountId, boolean authenticated)
        {
                return publishOffer(offer,expectedAccountId,authenticated,Instant.now().getEpochSecond(),true);
        }
        private OfferEvent publishOffer(TrackedOffer offer, String expectedAccountId, boolean authenticated,long now,boolean replay)
        {
                if (offer == null) return null;
                if (expectedAccountId == null || !expectedAccountId.equals(accountContext()))
                { lastError = "Could not persist offer event: account identity changed or unavailable"; return null; }
                String state = offer.getState() == null ? "OBSERVED" : offer.getState();
                SuggestionLedger.Advice advice = ledger.attribute(offer.getItemId(), offer.isBuying(), now);
                try
                {
                        OfferEvent event = outbox.enqueue((eventId, sessionId, sequence) ->
                        {
                                long start = offer.getFirstSeen() > 0 ? offer.getFirstSeen() : now;
                                OfferEvent.Builder builder = OfferEvent.builder(UUID.randomUUID().toString(), now, state)
                                        .accountId(expectedAccountId)
                                        .authenticatedObservation(authenticated)
                                        .eventIdentity(eventId, sessionId,
                                                offer.getSlot() + "@" + start + ":" + offer.getItemId())
                                        .slot(offer.getSlot()).item(offer.getItemId(), offer.getItemName())
                                        .buying(offer.isBuying()).price(offer.getPrice())
                                        .quantities(offer.getTotalQuantity(), offer.getQuantityFilled())
                                        .spent(offer.getSpent()).sequence(sequence).firstSeenAt(offer.getFirstSeen());
                                if (advice != null)
                                {
                                        builder.recommendation(advice.getRecommendationId(), advice.getPrice(),
                                                advice.getQuantity(), advice.getQuoteAgeSeconds(),
                                                advice.getPredictedMinutes(), advice.getPredictedCompletion());
                                }
                                return builder.build();
                        });
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
                        OfferEvent event = outbox.enqueue((eventId, sessionId, sequence) ->
                                OfferEvent.builder(UUID.randomUUID().toString(), now, "EMPTY")
                                        .accountId(expectedAccountId)
                                        .authenticatedObservation(authenticated)
                                        .eventIdentity(eventId, sessionId, null).slot(slot)
                                        .sequence(sequence).build());
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
		try
		{
			com.flippingfriend.core.AccountLedgerSnapshot fetched = get("ledger/current",
				com.flippingfriend.core.AccountLedgerSnapshot.class);
			acceptLedger(fetched, requestedAccount);
		}
		catch (Exception unavailable) { lastLedger = null; }
	}

	synchronized boolean acceptLedger(com.flippingfriend.core.AccountLedgerSnapshot fetched, String requestedAccount)
	{
		if (!java.util.Objects.equals(requestedAccount, accountContext())) return false;
		if (fetched == null || !fetched.isValid()
			|| !java.util.Objects.equals(requestedAccount, fetched.getAccountId()))
		{ lastLedger = null; return false; }
		lastLedger = fetched;
		return true;
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
		accountContext();
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

			// Remember what we advised before showing it, so whatever the player does next can be
			// attributed to this plan rather than arriving as an anonymous offer.
			long quoteAge = Math.max(0, Instant.now().getEpochSecond() - plan.getCreatedAt());
			ledger.recorded(plan.getCorrelationId(), candidate.getItemId(), true,
				candidate.getBuyPrice(), candidate.getQuantity(), quoteAge,
				candidate.getBuyHours() * 60,
				candidate.getDisplayCompletionProbability());

			Suggestion suggestion = Suggestion.builder(SuggestionType.BUY)
				.item(candidate.getItemId(), candidate.getItemName())
				.price(candidate.getBuyPrice())
				.quantity(candidate.getQuantity())
				// What the trade makes if it works, which is what the same row means on every other
				// card in the panel.
				//
				// This used to be candidate.expectedProfit(), a probability-weighted figure with the
				// cost of a stranded position already netted out of it. Two problems. The built-in
				// engine puts the if-it-works profit under the identical label, so the meaning of
				// "Expected profit" changed depending on which engine had produced the card -- and
				// the holding card that appears minutes later shows the if-it-works figure too, so
				// a flip going exactly to plan looked like it had gained value on the way. And the
				// card already draws a confidence bar from the same probabilities, so weighting the
				// headline by them as well charged the player for the risk twice.
				//
				// The weighted figure is the right one for *ranking*, and it is still what ranks the
				// plan: PortfolioCandidate.expectedGpPerSlotHour uses it and is untouched.
				.expectedProfit(candidate.getNetProfit())
				.confidence(candidate.getDisplayCompletionProbability())
				.expectedMinutes(candidate.getSlotHours() * 60)
				// Both legs, which the panel has always been able to show and was never given: the
				// companion path never called this, so every companion-sourced suggestion rendered
				// "not known" for the buy and the sell while the portfolio list two inches below
				// printed both from the same candidate.
				.fillMinutes(candidate.getBuyHours() * 60, candidate.getSellHours() * 60)
				.targetSellPrice(candidate.getSellPrice())
				// rather than against the combined figure, which carries the buy leg's difficulty too.
				.headline("Buy " + explainer.formatNumber(candidate.getQuantity()) + " × " + candidate.getItemName())
				.build();

			lastBuy = suggestion;
			return suggestion;
		}
		catch (Exception ex)
		{
			lastError = ex.getMessage();
			return unavailable(lastError);
		}
	}

	/**
	 * Records that the panel advised this sale, so the outcome can be scored against the prediction.
	 * <p>
	 * Only buy advice was ever recorded, with {@code buying = true} hardcoded at the single call site.
	 * Everything downstream keys attribution on the leg, so every sell arrived at the companion with
	 * no recommendation attached: {@code wasRecommended()} was false, {@code observeOutcome} was never
	 * called for a sale, and the completion calibrator was trained entirely on buy legs and then
	 * applied to both. The sell leg is the one carrying the real uncertainty -- a buy at the bid nearly
	 * always fills -- so the half that was missing is the half worth learning from.
	 */
	public void recordSellAdvice(Suggestion suggestion)
	{
		if (suggestion == null || suggestion.getItemId() <= 0 || suggestion.getQuantity() <= 0)
		{
			return;
		}
		// The identity that matters is the trade, not the id: the ledger decides whether this is the
		// same advice by comparing price and quantity, which is what keeps the attribution window
		// from being restamped on every refresh.
		ledger.recorded("sell-" + suggestion.getItemId(), suggestion.getItemId(), false,
			suggestion.getPrice(), suggestion.getQuantity(), 0,
			suggestion.getSellFillMinutes() > 0
				? suggestion.getSellFillMinutes() : suggestion.getExpectedMinutes());
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

	PresentedEntryDecision nextEntryDecisionPresentation(Explainer explainer,
		Set<String> blocked, Set<Integer> skipped, Set<Integer> onOffer,
		EntryDecisionFetcher fetcher)
	{
		accountContext();
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
		PresentedEntryDecision presented = EntryDecisionPresenter.present(decision, plan, explainer);
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

	private void rememberCanonicalOffer(OfferEvent event)
	{
		if (event == null || event.getSlot() < 0) return;
		if (validOpenBuyForMaintenance(event)) canonicalOffers.put(event.getSlot(), event);
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
		try
		{
			return toSuggestion(get("action", CompanionAction.class));
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
                if (!replayRunning.compareAndSet(false, true)) return ReplayResult.BUSY;
                ReplayResult result = ReplayResult.FAILED;
                try
                {
                        if (!replayLifecycleIsCurrent(generation)) return ReplayResult.STALE;
                        for (OfferEvent event : outbox.pending())
                        {
                                if (!replayLifecycleIsCurrent(generation)) return ReplayResult.STALE;
                                String response;
                                try { response = sender.send(event); }
                                catch (Exception failure)
                                {
                                        if (!replayLifecycleIsCurrent(generation)) return ReplayResult.STALE;
                                        lastError = failure.getMessage();
                                        return ReplayResult.FAILED;
                                }
                                if (!replayLifecycleIsCurrent(generation)) return ReplayResult.STALE;
                                if (!consumeAcknowledgement(event, response))
                                {
                                        if (!replayLifecycleIsCurrent(generation)) return ReplayResult.STALE;
                                        lastError = "Companion returned an invalid offer acknowledgement.";
                                        return ReplayResult.FAILED;
                                }
                                if (!replayLifecycleIsCurrent(generation)) return ReplayResult.STALE;
                        }
                        result = ReplayResult.DRAINED;
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
                        }
                        replayRunning.set(false);
                }
        }

        private boolean replayLifecycleIsCurrent(long generation)
        {
                return replayEnabled && generation == replayGeneration.get();
        }

        private enum ReplayResult { DRAINED, FAILED, STALE, BUSY }

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

        public void resumeOfferReplay()
        {
                accountContext();
                replayGeneration.incrementAndGet();
                replayEnabled = true;
                retryDelayMillis = INITIAL_RETRY_DELAY_MILLIS;
                nextRetryAtMillis = 0;
        }

        public void pauseOfferReplay()
        {
                replayEnabled = false;
                replayGeneration.incrementAndGet();
                nextRetryAtMillis = Long.MAX_VALUE;
                retryDelayMillis = INITIAL_RETRY_DELAY_MILLIS;
                retryRequestGeneration.set(-1);
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
		if (event != null && event.getAccountId() != null
			&& !event.getAccountId().equals(storage.accountId())) return false;
                OfferEventAcknowledgementResponse ack;
                try { ack = gson.fromJson(response, OfferEventAcknowledgementResponse.class); }
                catch (RuntimeException malformed) { return false; }
                if (event == null || ack == null || !ack.isAccepted()
                        || !same(event.getEventId(), ack.getEventId())
                        || !same(event.getSessionId(), ack.getSessionId())
                        || event.getSequence() != ack.getSequence()) return false;
                return outbox.acknowledge(event.getEventId(), event.getSessionId(), event.getSequence());
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
		return request(path, method, body, TIMEOUT_MILLIS);
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
		HttpURLConnection connection = (HttpURLConnection) new URL(BASE + endpoint).openConnection();
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
