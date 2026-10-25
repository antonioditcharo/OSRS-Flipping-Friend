package com.flippingfriend.companion;

import com.flippingfriend.data.Candle;
import com.flippingfriend.core.AccountSnapshot;
import com.flippingfriend.core.CompanionAction;
import com.flippingfriend.core.CompanionHealth;
import com.flippingfriend.core.OfferEvent;
import com.flippingfriend.core.OfferLifecycleTransition;
import com.flippingfriend.core.PolicyDecision;
import com.flippingfriend.core.PortfolioAllocation;
import com.flippingfriend.core.PortfolioPlan;
import com.flippingfriend.core.PortfolioPlanOutcome;
import com.google.gson.Gson;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Application boundary: durable events in, fresh portfolio/health out.
 * <p>
 * Planning happens on its own thread rather than inside the request that triggered it. A planning
 * cycle fetches per-item history for the whole shortlist and deliberately trickles those requests to
 * stay polite to a free community API, so it takes tens of seconds. Doing that inline would hold the
 * plugin's HTTP call open for the duration and — because reads and writes share this object's
 * state — would block the panel's own poll behind it, making the client look frozen every time an
 * offer changed. Events are therefore recorded synchronously, which is the part that must not be
 * lost, and the plan is recomputed behind them.
 * <p>
 * Requests to re-plan are <em>coalesced</em>. Collecting a completed offer can fire several events in
 * a second, and each one invalidates the last plan; queueing a full cycle per event would spend
 * minutes computing plans that are already obsolete. One run in flight plus at most one queued
 * behind it gives the same end state for a fraction of the work.
 */
final class CompanionService implements AutoCloseable
{
	private final Gson gson;
    private PilotGateway pilot;
    void usePilot(PilotGateway gateway){pilot=gateway;planner.usePilot(gateway,()->{pilotRevision.incrementAndGet();requestPlan();});}
    private final java.util.concurrent.atomic.AtomicLong pilotRevision=new java.util.concurrent.atomic.AtomicLong();
    private volatile long lastPlannedPilotRevision=-1;
    Map<String,Object> pilotStatus(){return pilot==null?Map.of("mode","LOCAL_ONLY"):pilot.status(accountId);}
	private final SqliteStore store;
	private final String accountId;
	private final boolean ownsMarket;
	private volatile java.util.function.Supplier<java.util.List<Integer>> warmShortlist;
    private volatile java.util.function.Supplier<java.util.List<Integer>> warmMaintenance=this::maintenanceIds;
    private volatile java.util.List<Integer> maintainedPositions=java.util.List.of();
	private final MarketIngestionService market;
	private final SeriesCache series;
	private final PortfolioPlanner planner;
    private final ReplacementEntryPlanner replacementPlanner;
	private final ActiveOfferTracker activeOffers;
	private final CompanionActionSelector actionSelector = new CompanionActionSelector();
	private final CompanionEntryPolicy entryPolicy = new CompanionEntryPolicy();
	/** Buy-limit windows are per account and reset four hours after the first purchase in them. */
	private final BuyLimitLedger buyLimits = new BuyLimitLedger();


	/** Single thread, so plans are computed one at a time and in order. */
	private final ExecutorService planThread = new java.util.concurrent.ThreadPoolExecutor(0,1,60,
        java.util.concurrent.TimeUnit.SECONDS,new java.util.concurrent.LinkedBlockingQueue<>(),runnable ->
	{
		Thread thread = new Thread(runnable, "flipping-friend-planner");
		thread.setDaemon(true);
		return thread;
	});
	/** True while a re-plan is already queued, which makes further requests redundant. */
	private final AtomicBoolean planQueued = new AtomicBoolean();
	private volatile java.util.concurrent.CompletableFuture<Void> planCompletion = java.util.concurrent.CompletableFuture.completedFuture(null);
	private volatile long lastPlannedRevision = -1, lastPlannedMarketAt = -1, lastPlannedSeriesRevision = -1;

	/**
	 * Fetches per-item history in the background so planning never waits on the network.
	 * <p>
	 * Separating the two is what allows the shortlist to be hundreds of items rather than forty-five.
	 * Fetched inline, every extra item lengthened the planning cycle, so the number the plugin could
	 * consider was decided by how long a cycle was allowed to take rather than by how many were worth
	 * looking at. Here the cost is spread across the cache lifetime instead, at a rate a free
	 * community API can absorb without noticing.
	 */
	private final ExecutorService warmThread = new java.util.concurrent.ThreadPoolExecutor(0,1,60,
        java.util.concurrent.TimeUnit.SECONDS,new java.util.concurrent.LinkedBlockingQueue<>(),runnable ->
	{
		Thread thread = new Thread(runnable, "flipping-friend-warmer");
		thread.setDaemon(true);
		return thread;
	});

	private volatile AccountSnapshot account;
    // Accounting mode outlasts a process. Restoring it must not restore stale cash/slot authority.
    private boolean automaticTradeScope;
	/** When the last plan was built, and against how many free slots, for the spacing rule below. */
	private volatile long lastPlanAt;
    private final java.util.concurrent.atomic.AtomicLong planningInputRevision=new java.util.concurrent.atomic.AtomicLong();
	private volatile int lastPlanFreeSlots = -1;
	/** What the last plan was built under, so a change of intent is not held behind the spacing. */
	private volatile boolean lastPlanSellOnly;
	private volatile PortfolioPlan plan;
	private static final Logger log = LoggerFactory.getLogger(CompanionService.class);

	/** How old market data may get before the companion stops calling itself healthy. */
	private static final long STALE_MARKET_SECONDS = 120;
	/**
	 * Renew unchanged plans before their expiry; this is not a delay on changed inputs.
	 */
	private static final long PLAN_RENEWAL_MARGIN_SECONDS = 30;

	private volatile String healthReason = "Waiting for market data.";

	/**
	 * Faults that are still outstanding, keyed by the subsystem that raised them.
	 * <p>
	 * These used to be written into healthReason, which the market cycle overwrites with "Ready."
	 * every sixty seconds. A permanent condition -- a full disk, a corrupt database, plan persistence
	 * failing on every single pass -- was therefore visible for at most one cycle before the service
	 * went back to describing itself as healthy. A fault clears only when the thing that raised it
	 * succeeds, so a condition that is still true still says so.
	 */
	private final Map<String, String> faults = new ConcurrentHashMap<>();

	private void fault(String subsystem, String detail, Throwable cause)
	{
		faults.put(subsystem, detail);
		log.warn("{} failed: {}", subsystem, detail, cause);
	}

	private void resolved(String subsystem)
	{
		if (faults.remove(subsystem) != null)
		{
			log.info("{} recovered", subsystem);
		}
	}
	private volatile boolean planning;
	private volatile boolean warming;
	private final AtomicBoolean warmRequested = new AtomicBoolean();

	/** Per-cycle request budget for background history, per resolution. */
	private static final int WARM_BUDGET = 60;

	/**
	 * How often to retrain the outcome models on whatever the daemon has gathered since.
	 * <p>
	 * Slow on purpose. The daemon produces on the order of a thousand observations a day, so
	 * retraining hourly would mostly re-fit the same data and churn the model for no gain. Six-hourly
	 * keeps the model current with a day's learning while leaving it stable enough that the plans a
	 * player sees do not shift underneath them for no visible reason.
	 */
	private static final long RETRAIN_INTERVAL_SECONDS = 6 * 3600;

	private volatile long lastTrainedAt;

	/** Held in memory only, which is what the tests want. */
	CompanionService(Gson gson, SqliteStore store)
	{
		this(gson, store, null);
	}

	/**
	 * @param cacheDir where collected price history is kept between runs, or null to keep it only in
	 *                 memory. A run that starts with a warm cache can plan on its first cycle instead
	 *                 of spending several vetoing every candidate for want of history.
	 */
	CompanionService(Gson gson, SqliteStore store, java.nio.file.Path cacheDir)
	{
		this(gson, store, new MarketIngestionService(gson), cacheDir == null ? new SeriesCache()
			: new SeriesCache(cacheDir.resolve("series-cache.json.gz")), null, true);
	}

	private CompanionService(Gson gson, SqliteStore store, MarketIngestionService market,
		SeriesCache series, String accountId, boolean ownsMarket)
	{
		this.gson = gson;
		this.store = store;
		this.accountId = accountId;
		this.ownsMarket = ownsMarket;
		this.activeOffers = new ActiveOfferTracker();
		this.market = market;
		this.series = series;
		this.planner = new PortfolioPlanner(series);
        this.replacementPlanner = new ReplacementEntryPlanner(series);
		this.warmShortlist = planner::shortlistIds;
	}

	CompanionService forAccountStore(SqliteStore store, String accountId)
	{
		var scoped=new CompanionService(gson, store, market, series,
            com.flippingfriend.core.AccountIdentity.require(accountId), false);
        scoped.usePilot(pilot);return scoped;
	}

	void useWarmShortlist(java.util.function.Supplier<java.util.List<Integer>> supplier) { warmShortlist = supplier; }
    void useWarmMaintenance(java.util.function.Supplier<java.util.List<Integer>> supplier) {warmMaintenance=supplier;}
    java.util.List<Integer> maintenanceIds() {
        var ids=new java.util.LinkedHashSet<Integer>(maintainedPositions);
        for(var offer:activeOffers.getActiveOffers())if(offer.getItemId()>0)ids.add(offer.getItemId());
        return new java.util.ArrayList<>(ids);
    }
	java.util.List<Integer> shortlistIds() { return planner.shortlistIds(); }

	void refreshAccountPlan()
	{
		refreshCalibration();
		AccountSnapshot snapshot = account;
		long age = snapshot == null ? Long.MAX_VALUE : Instant.now().getEpochSecond() - snapshot.getObservedAt();
		if (age < 0 || age > 150) { plan = null; return; }
		requestPlan();
	}

	/**
	 * Rebuilds in-memory state that describes a reality outlasting this process.
	 * <p>
	 * Only the buy-limit ledger is replayed. Execution statistics are deliberately not, because they
	 * are already durable in {@code execution_stat}: re-recording settled offers would count every
	 * historical offer a second time on each restart, and a sample inflated by restarts is worse than
	 * no sample at all.
	 */
	void rehydrate() throws Exception
	{
        automaticTradeScope=accountId!=null && store.recordedAutomaticTradeScope(accountId);
		java.util.List<BuyLimitProjection> durableBuyLimits = store.buyLimitProjections();
		buyLimits.restore(durableBuyLimits);
		boolean restoreBuyLimitsFromEvents = durableBuyLimits.isEmpty();
		for (OfferEvent event : store.activeOfferEvents())
			activeOffers.apply(accountId == null ? event : event.withAccountId(accountId));
		boolean restoreOffersFromEvents = store.offerProjections().isEmpty();
		for (com.flippingfriend.core.OfferLifecycleProjection projection : store.offerProjections())
			if (projection.getState() == com.flippingfriend.core.OfferLifecycleState.ERROR_RECONCILIATION)
				fault("Offer projection slot " + projection.getSlot(), "durable offer requires reconciliation", null);
		refreshPositionFaults();
		if(accountId!=null)ledger();
		long since = Instant.now().minus(BuyLimitLedger.WINDOW).getEpochSecond();
		int replayed = 0;
		for (String payload : store.recentOfferEvents(since))
		{
			OfferEvent event = gson.fromJson(payload, OfferEvent.class);
			if (event != null)
			{
				if (restoreBuyLimitsFromEvents) buyLimits.apply(event);
				if (restoreOffersFromEvents) activeOffers.apply(event);
				replayed++;
			}
		}
		if (replayed > 0)
		{
			healthReason = "Resumed " + buyLimits.trackedItems() + " buy-limit windows from "
				+ replayed + " stored events.";
		}
	}

	void refreshMarket()
	{
		try
		{
			market.refresh();
			resolved("Market refresh");
			healthReason = "Ready.";
			refreshCalibration();
			requestPlan();
			requestWarm();
		}
		catch (Throwable ex)
		{
			// Throwable, not Exception. An Error escaping this method is not merely one failed cycle:
			// scheduleWithFixedDelay cancels the task permanently when it throws, so a single
			// NoClassDefFoundError stops market ingestion for the life of the process, records no
			// fault, and leaves the health line still reading "Ready." That is exactly what happened.
			fault("Market refresh", String.valueOf(ex), ex);
		}
	}

	/**
	 * Notices that ingestion has stopped, which the ingestion loop itself cannot do.
	 * <p>
	 * A task that dies takes its own error reporting with it. This runs on a separate timer and asks
	 * the only question that matters from outside: is the market data still arriving? If it is not,
	 * it says so in a way a human will see and tries to start it again.
	 */
	void checkIngestion()
	{
		try
		{
			long marketAt = market.state().observedAt;
			long age = Instant.now().getEpochSecond() - marketAt;
			if (marketAt > 0 && age <= STALE_MARKET_SECONDS)
			{
				resolved("Market data");
				return;
			}
			fault("Market data", (marketAt <= 0 ? "no market data has ever arrived"
				: age + "s since the last successful refresh") + "; retrying", null);
			refreshMarket();
		}
		catch (Throwable ex)
		{
			log.warn("ingestion watchdog failed", ex);
		}
	}

	/**
	 * Tops up cached history for the shortlist, a bounded number of items per market cycle.
	 * <p>
	 * The budget is what keeps this polite. Roughly three hundred items each need refreshing once per
	 * cache lifetime, which spread over that lifetime is a request every few seconds — nothing a
	 * public API would notice, and slow enough that a cold start fills in over several minutes rather
	 * than arriving as a burst.
	 */
	private void requestWarm()
	{
		if (!ownsMarket) return;
		if (warming || !warmRequested.compareAndSet(false, true))
		{
			return;
		}
		warmThread.execute(() ->
		{
			warmRequested.set(false);
			warming = true;
			try
			{
                int maintenanceSpent=series.warmMaintenance(warmMaintenance.get(),Math.min(32,WARM_BUDGET));
				if(maintenanceSpent>0)requestPlan();
				java.util.List<Integer> wanted = warmShortlist.get();
				if (!wanted.isEmpty())
				{
					int spent = maintenanceSpent+series.warm(wanted, CandidateFactory.LIVE_SHORT_STEP, WARM_BUDGET-maintenanceSpent);
					spent += series.warm(wanted, CandidateFactory.LIVE_LONG_STEP, WARM_BUDGET);
					if (spent > 0)
					{
						// A fresh item cannot be planned until its history lands, so a plan made
						// before the top-up is already out of date by the time it is served.
						requestPlan();
					}
				}
			}
			catch (Exception ex)
			{
				fault("History refresh", String.valueOf(ex), ex);
			}
			finally
			{
				warming = false;
			}
		});
	}

	/**
	 * No challenger has passed promotion gates. Diagnostic source scans belong to the explicit
	 * account readout, not account initialization or market refresh under the account registry lock.
	 */
	private void refreshCalibration()
	{
		planner.setCalibration(FillCalibration.NEUTRAL);
	}

    com.flippingfriend.core.ExecutionLearningReport learningEvidence() {
        long now=Instant.now().getEpochSecond();
        if(accountId==null)return com.flippingfriend.core.ExecutionLearningReport.unavailable(null,now,
            "An account-scoped source ledger is required; unassigned history is not learning evidence.");
        try { return store.executionLearningReport(accountId,now); }
        catch(Exception unavailable) { return com.flippingfriend.core.ExecutionLearningReport.unavailable(accountId,now,
            "Execution evidence could not be read; the public-price baseline remains active."); }
    }

	private void refreshPositionFaults() throws Exception
	{
        var positions=store.positionProjections();
		for (com.flippingfriend.core.PositionProjection position : positions)
		{
			String subsystem = "Position projection item " + position.getItemId();
			if (position.getConsistencyState() == com.flippingfriend.core.PositionConsistencyState.RECONCILE)
				fault(subsystem, position.getConsistencyReason(), null);
			else resolved(subsystem);
		}
	}

	synchronized void account(AccountSnapshot snapshot) throws Exception
	{
		if (snapshot == null || (accountId != null && !accountId.equals(snapshot.getAccountId())))
			throw new IllegalArgumentException("snapshot account identity mismatch");
		PositionStateView view = PositionStateView.from(snapshot);
		SnapshotReconciliationResult reconciliation =
			store.recordAndReconcileAccount(snapshot, view, gson.toJson(snapshot));
		if (account != null && !gson.toJsonTree(account.getStockDeclarations()).equals(gson.toJsonTree(snapshot.getStockDeclarations())))
		{
			plan = PortfolioPlan.unavailable(snapshot.getCorrelationId(), PortfolioPlanOutcome.COMPANION_STATE_UNAVAILABLE,
				"Holding review changed; recomputing the buy plan.", Instant.now().getEpochSecond());
			lastPlanAt = 0;
		}
        boolean changed=!samePlanningInputs(account,snapshot);
		account = snapshot;
        automaticTradeScope=snapshot.isAutomaticTradeScope();
        // A complete new receipt must retry a transient proof failure even if cash/slots are
        // unchanged. Otherwise plan spacing can retain the refusal from its in-flight offers.
        boolean retryUnavailable = plan != null
            && plan.getOutcome() == PortfolioPlanOutcome.COMPANION_STATE_UNAVAILABLE;
        if(changed || retryUnavailable){planningInputRevision.incrementAndGet();plan=null;lastPlanAt=0;}
		// The plugin's ledger is durable; this one is rebuilt from events that can go missing. Take
		// the higher of the two so a dropped fill is corrected on the next cycle rather than never.
		buyLimits.restore(store.buyLimitProjections());
		if (reconciliation.reconciliationRequired() > 0)
		{
			fault("Snapshot reconciliation", reconciliation.reconciliationRequired()
				+ " position(s) require reconciliation", null);
		}
		else resolved("Snapshot reconciliation");
		// Positive snapshot evidence can repair an initial/restarted unknown-cost position.
		refreshPositionFaults();
		if(snapshot.isAutomaticTradeScope() && CurrentOfferReadSet.valid(store.planningProjections(accountId),snapshot,Instant.now().getEpochSecond()))
			store.performanceSession(snapshot.getAccountId(),Instant.now().getEpochSecond(),false,java.util.UUID.randomUUID().toString());
		requestPlan();
	}
	com.flippingfriend.core.AccountLedgerSnapshot ledger() throws Exception
	{
        var ledger=store.ledgerSnapshot(accountId);
        if(ledger!=null && ledger.isValid() && java.util.Objects.equals(accountId,ledger.getAccountId()))
            maintainedPositions=ledger.getTradingPositions().stream().filter(p->p.getQuantity()>0)
                .map(p->p.getItemId()).collect(java.util.stream.Collectors.toList());
		return ledger;
	}
    com.flippingfriend.core.PerformanceSnapshot performance(String scope) throws Exception {
        SqliteStore.PerformanceInput input;SqliteStore.PlanningProjections positions=null;
        AccountSnapshot snapshot=null;MarketIngestionService.MarketState prices=null;
        synchronized(this) {
            long now=Instant.now().getEpochSecond();
            input=store.performanceInput(accountId,scope,now);
            if(account!=null && currentPlanningProof(now)) {
                positions=store.planningProjections(accountId);snapshot=account;prices=market.state();
            }
        }
        // The report keeps its captured prefix while new executions/ACKs remain free to commit.
        var result=store.performance(input);
        if(positions!=null)StockPerformance.apply(result,positions,snapshot,prices,input.now);
        if(!result.isValid())throw new IllegalStateException("Invalid performance valuation");
        return result;
    }

	synchronized void resetPerformanceSession(String request) throws Exception {
		long now=Instant.now().getEpochSecond();
		if(account==null || !currentPlanningProof(now))throw new IllegalStateException("Waiting for current account evidence");
		store.performanceSession(accountId,now,true,request);
	}
    synchronized com.flippingfriend.core.TradeReviewSnapshot tradeReview()throws Exception {
        return store.tradeReview(accountId,Instant.now().getEpochSecond());
    }
    synchronized long recoveryExternalRevision()throws Exception{return store.recoveryExternalRevision();}
    synchronized void commitRecovery(SaleCostAmendments.Prepared prepared)throws Exception {
        if(accountId==null || !accountId.equals(prepared.account))throw new IllegalArgumentException("foreign recovery");
        store.commitPreparedRecovery(prepared);planningInputRevision.incrementAndGet();requestPlan();
    }
    synchronized java.util.List<String> amendSaleCosts(SaleCostAmendments.Request request)throws Exception {
        if(request==null || accountId==null || !accountId.equals(request.accountId))
            throw new IllegalArgumentException("Amendment account must match the scoped service");
        var result=store.amendSaleCosts(accountId,request.saleSourceEventIds,Instant.now().getEpochSecond());
        planningInputRevision.incrementAndGet();requestPlan();return result;
    }
    synchronized com.flippingfriend.core.TradeReviewSnapshot reviewTrades(com.flippingfriend.core.TradeReviewSnapshot.Request request)throws Exception {
        return store.reviewTrades(accountId,request,Instant.now().getEpochSecond());
    }

	PositionStateView positions()
	{
		return PositionStateView.from(account);
	}

	synchronized SqliteStore.EventAcceptance offer(OfferEvent event) throws Exception
	{
		if (event == null || (accountId != null && !accountId.equals(event.getAccountId())))
			throw new IllegalArgumentException("offer account identity mismatch");
		SqliteStore.ProjectedEventAcceptance projected =
			store.recordAndProjectOffer(event, gson.toJson(event), automaticTradeScope);
		if (projected.acceptance == SqliteStore.EventAcceptance.DUPLICATE)
		{
			return projected.acceptance;
		}
		OfferLifecycleTransition transition = projected.transition;
        if(projected.planningStateChanged){planningInputRevision.incrementAndGet();plan=null;lastPlanAt=0;}
		if (!transition.isAccepted())
		{
			activeOffers.invalidateSlot(event.getSlot());
			fault("Offer projection slot " + event.getSlot(), transition.getReason(), null);
			return projected.acceptance;
		}
		resolved("Offer projection slot " + event.getSlot());
		if (projected.positionProjection != null
			&& projected.positionProjection.getConsistencyState() == com.flippingfriend.core.PositionConsistencyState.RECONCILE)
		{
			fault("Position projection item " + projected.positionProjection.getItemId(), projected.positionProjection.getConsistencyReason(), null);
		}
		else if (projected.positionProjection != null)
		{
			resolved("Position projection item " + projected.positionProjection.getItemId());
		}
		buyLimits.restore(store.buyLimitProjections());
		activeOffers.apply(event);
		// Execution evidence committed atomically with the canonical event above.

		// An offer changing is the single most important reason to re-plan: a slot has just opened
		// or closed, capital has moved, and a buy limit may have been consumed. Waiting for the next
		// market poll would leave the panel telling the player to do something they have just done.
		if (projected.planningStateChanged) requestPlan();
		return projected.acceptance;
	}

	/**
	 * What the plan claimed when it advised this trade.
	 * <p>
	 * Carried on the event rather than looked up, because by the time an offer settles the plan that
	 * produced it is long expired — and a calibrator fed the wrong prediction is worse than one fed
	 * nothing.
	 */
	static double predictedCompletion(OfferEvent event)
	{
		// The number the model actually produced, carried with the advice.
		//
		// This used to be derived from the predicted duration -- a linear ramp of minutes against the
		// horizon. That is not a probability of anything, and it cannot express the one thing a
		// calibrator exists to learn: that the model claimed 93% and it happened 74%. Whatever the
		// ramp said, it said it by construction, so the calibrator was being trained on its own
		// arithmetic rather than on the model's error.
		//
		// Zero means no claim was made -- an unprompted trade, or a leg the planner never scored. The
		// caller declines to calibrate in that case, which is the honest answer: the comment on this
		// method has always said a calibrator fed the wrong prediction is worse than one fed nothing.
		return Math.max(0, Math.min(1, event.getPredictedCompletion()));
	}

	PortfolioPlan plan()
	{
		// The account POST schedules work. Give that work a short chance to finish on this very
		// request, rather than returning WAIT and making the player sit through another client poll.
		// Never hold the service monitor while waiting: offer acknowledgements must keep flowing.
		try { planCompletion.get(400, java.util.concurrent.TimeUnit.MILLISECONDS); }
		catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
		catch (java.util.concurrent.ExecutionException | java.util.concurrent.TimeoutException pending) { }
		synchronized(this) { return currentPlan(); }
	}
	private PortfolioPlan currentPlan()
	{
		PortfolioPlan current = plan;
		long now = Instant.now().getEpochSecond();
        if(current!=null && "READY".equals(current.getStatus()) && !currentPlanningProof(now))
            return PortfolioPlan.unavailable(current.getCorrelationId(),PortfolioPlanOutcome.COMPANION_STATE_UNAVAILABLE,
                "Waiting for consistent current cash and Grand Exchange observations.",now);
		if (current == null || current.getExpiresAt() <= now)
		{
			return PortfolioPlan.unavailable("none", PortfolioPlanOutcome.COMPANION_STATE_UNAVAILABLE,
				account == null ? "Waiting for the game client to report your coins and free slots."
				: "Working out the best use of your slots.", now);
		}
		return current;
	}

	synchronized PolicyDecision entryDecision(String planId, int allocationRank, long decidedAt)
	{
        long revision=planningInputRevision.get();
		PortfolioPlan current = plan;
        if(current!=null && "READY".equals(current.getStatus()) && !currentPlanningProof(decidedAt))
            return entryPolicy.decide(new EntryPolicyContext(PortfolioPlan.unavailable(current.getCorrelationId(),
                PortfolioPlanOutcome.COMPANION_STATE_UNAVAILABLE,"Current account evidence changed.",decidedAt),null,decidedAt));
		if (current == null || current.getExpiresAt() <= decidedAt)
		{
			PortfolioPlan unavailable = PortfolioPlan.unavailable("none",
				PortfolioPlanOutcome.COMPANION_STATE_UNAVAILABLE,
				account == null ? "Waiting for the game client to report your coins and free slots."
					: "Working out the best use of your slots.", decidedAt);
			return entryPolicy.decide(new EntryPolicyContext(unavailable, null, decidedAt));
		}
		long observedAt = Math.max(0, Math.min(decidedAt, current.getCreatedAt()));
		String recommendationId = current.getCorrelationId() == null ? "none" : current.getCorrelationId();
		if (planId == null || planId.isBlank() || !planId.equals(current.getCorrelationId()))
			return CompanionEntryPolicy.abstain(recommendationId, decidedAt, observedAt,
				com.flippingfriend.core.PolicyAbstentionReason.ACCOUNT_STATE_INCONSISTENT, "PLAN_IDENTITY_MISMATCH");
		if (!"READY".equals(current.getStatus()))
			return entryPolicy.decide(new EntryPolicyContext(current, null, decidedAt));
		if (allocationRank <= 0)
			return CompanionEntryPolicy.abstain(recommendationId, decidedAt, observedAt,
				com.flippingfriend.core.PolicyAbstentionReason.ACCOUNT_STATE_INCONSISTENT, "INVALID_ALLOCATION_RANK");
		PortfolioAllocation selected = null;
		for (PortfolioAllocation allocation : current.getAllocations())
		{
			if (allocation != null && allocation.getRank() == allocationRank)
			{
				if (selected != null)
					return CompanionEntryPolicy.abstain(recommendationId, decidedAt, observedAt,
						com.flippingfriend.core.PolicyAbstentionReason.ACCOUNT_STATE_INCONSISTENT, "AMBIGUOUS_ALLOCATION_RANK");
				selected = allocation;
			}
		}
		if (selected == null)
			return CompanionEntryPolicy.abstain(recommendationId, decidedAt, observedAt,
				com.flippingfriend.core.PolicyAbstentionReason.NO_ELIGIBLE_CANDIDATE, "ALLOCATION_NOT_FOUND");
        try {
            AccountSnapshot snapshot=account;
            var read=store.planningProjections(snapshot==null?null:snapshot.getAccountId());
            var exposure=DurablePlanningExposure.forAccount(read,snapshot,decidedAt);
            var candidate=selected.getCandidate();
            if(exposure==null || candidate==null)throw new IllegalStateException("entry exposure unavailable");
            var risk=DurableRisk.evaluate(read,snapshot,exposure,market.state(),decidedAt);
            if(!risk.isAvailable())
                return CompanionEntryPolicy.abstain(recommendationId,decidedAt,observedAt,
                    com.flippingfriend.core.PolicyAbstentionReason.ACCOUNT_STATE_UNAVAILABLE,"DURABLE_RISK_UNAVAILABLE");
            if(risk.getRemainingLossBudget()<=0)
                return CompanionEntryPolicy.abstain(recommendationId,decidedAt,observedAt,
                    com.flippingfriend.core.PolicyAbstentionReason.DRAWDOWN_LIMIT_REACHED,"DRAWDOWN_LIMIT_REACHED");
            if(EntryStopPause.items(read,market.state(),decidedAt).contains(candidate.getItemId()))
                return CompanionEntryPolicy.abstain(recommendationId,decidedAt,observedAt,
                    com.flippingfriend.core.PolicyAbstentionReason.RISK_CONSTRAINT,"ENTRY_STOP_RECHECK");
            if(ExitSlotReservation.awaiting(read,snapshot,0)!=null)
                return CompanionEntryPolicy.abstain(recommendationId,decidedAt,observedAt,
                    com.flippingfriend.core.PolicyAbstentionReason.SLOT_RESERVED_FOR_EXIT,"EXIT_SLOT_RESERVED");
            java.util.Set<Integer> occupied=new java.util.HashSet<>();
            for(var p:read.offers)if(p.getState()!=com.flippingfriend.core.OfferLifecycleState.EMPTY)occupied.add(p.getItemId());
            var planning=snapshot.withPlanningRestrictions(exposure.committedByItem,EntryStopPause.restrictions(exposure,read,market.state(),decidedAt))
                .withPlanningOffers(occupied).withCanonicalRisk(risk);
            var metadata=market.state().mapping.get(candidate.getItemId());
            if(planning.isSellOnly() || PortfolioPlanner.lossBudgetFor(planning)<=0
                    || PortfolioPlanner.rejected(candidate,planning) || metadata==null
                    || (metadata.members && !planning.isMembers())
                    || remainingBuyLimits(planning,decidedAt).getOrDefault(candidate.getItemId(),0)<candidate.getQuantity())
                throw new IllegalStateException("item eligibility or buy limit changed");
            var constraints=PortfolioPlanner.constraintsFor(planning,com.flippingfriend.model.RiskAppetite.forName(planning.getRiskAppetite()),
                PortfolioPlanner.lossBudgetFor(planning),PortfolioPlanner.committedByGroupFor(planning,market.state().mapping));
            var feasible=new com.flippingfriend.core.PortfolioOptimizer().optimize(java.util.List.of(candidate),constraints,
                current.getCorrelationId(),decidedAt);
            if(feasible.getAllocations().isEmpty() || revision!=planningInputRevision.get())
                throw new IllegalStateException("current resource constraints changed");
        }catch(Exception unsafe){
            return CompanionEntryPolicy.abstain(recommendationId,decidedAt,observedAt,
                com.flippingfriend.core.PolicyAbstentionReason.ACCOUNT_STATE_INCONSISTENT,"ENTRY_CONSTRAINTS_CHANGED");
        }
		return entryPolicy.decide(new EntryPolicyContext(current, selected, decidedAt));
	}
    synchronized com.flippingfriend.core.ReplacementEntry replacementEntry(int item,int cap,long at) {
        if(item<=0 || cap<=0) throw new IllegalArgumentException("Positive item and cap required");
        try {
            long revision=planningInputRevision.get();
            if(!currentPlanningProof(at)) throw new IllegalStateException("Current account proof unavailable");
            var snapshot=account;
            var read=store.planningProjections(accountId);
            var exposure=DurablePlanningExposure.forAccount(read,snapshot,at);
            if(exposure==null) throw new IllegalStateException("Current exposure unavailable");
            var currentMarket=market.state();
            var risk=DurableRisk.evaluate(read,snapshot,exposure,currentMarket,at);
            if(!risk.isAvailable()) throw new IllegalStateException("Current risk unavailable");
            if(EntryStopPause.items(read,currentMarket,at).contains(item)) {
                var waiting=PortfolioPlan.unavailable("replacement-stop-recheck",PortfolioPlanOutcome.NO_ELIGIBLE_CANDIDATE,EntryStopPause.REASON,at).withCanonicalRisk(risk);
                return new com.flippingfriend.core.ReplacementEntry(accountId,item,cap,snapshot.getObservedAt(),waiting,
                    CompanionEntryPolicy.abstain(waiting.getCorrelationId(),at,at,com.flippingfriend.core.PolicyAbstentionReason.RISK_CONSTRAINT,"ENTRY_STOP_RECHECK"));
            }
            var waitingExit=ExitSlotReservation.awaiting(read,snapshot,item);
            if(waitingExit!=null) {
                var waiting=PortfolioPlan.unavailable("replacement-exit-slot",PortfolioPlanOutcome.NO_FREE_SLOT,
                    ExitSlotReservation.reason(waitingExit),at).withCanonicalRisk(risk);
                return new com.flippingfriend.core.ReplacementEntry(accountId,item,cap,snapshot.getObservedAt(),waiting,
                    entryPolicy.decide(new EntryPolicyContext(waiting,null,at)));
            }
            var occupied=new java.util.HashSet<Integer>();
            for(var offer:read.offers) if(offer.getState()!=com.flippingfriend.core.OfferLifecycleState.EMPTY) occupied.add(offer.getItemId());
            var planning=snapshot.withPlanningRestrictions(exposure.committedByItem,EntryStopPause.restrictions(exposure,read,currentMarket,at))
                .withPlanningOffers(occupied).withCanonicalRisk(risk);
            var result=replacementPlanner.plan(item,cap,currentMarket,planning,remainingBuyLimits(planning,at),read.currentEvents.values(),at);
            if(revision!=planningInputRevision.get() || !currentPlanningProof(Math.max(at,Instant.now().getEpochSecond())))
                throw new IllegalStateException("Current evidence changed");
            return result;
        }catch(Exception unavailable) {
            var plan=PortfolioPlan.unavailable("replacement-unavailable",PortfolioPlanOutcome.COMPANION_STATE_UNAVAILABLE,
                "Waiting for current account evidence before replacing the cancelled remainder.",at);
            return new com.flippingfriend.core.ReplacementEntry(accountId,item,cap,account==null?0:account.getObservedAt(),plan,
                entryPolicy.decide(new EntryPolicyContext(plan,null,at)));
        }
    }
	PolicyDecision buyMaintenanceDecision(long decidedAt)
	{
		return new BuyMaintenanceServiceAdapter().decide(activeOffers.getActiveOffers(),
			market.state(), buyLimits, decidedAt);
	}

	CompanionAction action()
	{
		return actionSelector.select(activeOffers.getActiveOffers());
	}
	synchronized PolicyDecision saleDecision(int item,int price,int quantity,String source,boolean cut,int slot,long at) {
        return saleDecision(item,price,quantity,source,cut,slot,at,null);
    }
    synchronized PolicyDecision saleDecision(int item,int price,int quantity,String source,boolean cut,int slot,long at,String repriceSource) {
		try {
			var read=store.saleProjections(accountId,at);
			return new CompanionSalePolicy().decide(read.planning,read.ledger,account,
				market.state(),series,item,price,quantity,source,cut,slot,at,repriceSource);
		}catch(Exception unavailable){return null;}
	}
    /** Prospective POST authorization; receipt metadata commits only after the exact final policy passes. */
    synchronized PolicyDecision saleMaintenanceDecision(String request,int item,int price,int quantity,String source,boolean cut,int slot,long at) {
        if(slot<0 || slot>7)throw new IllegalArgumentException("Working sale slot is required");
        java.util.UUID.fromString(request);
        try {
            var read=store.saleProjections(accountId,at);
            var decision=new CompanionSalePolicy().decide(read.planning,read.ledger,account,market.state(),series,
                item,price,quantity,source,cut,slot,at);
            if(decision.getAction()!=com.flippingfriend.core.OfferLifecycleAction.CANCEL_SELL)return decision;
            var offer=read.planning.currentEvents.get(slot);
            var horizon=com.flippingfriend.TradingHorizon.of(com.flippingfriend.RiskProfile.valueOf(account.getRiskAppetite()),
                AccountReplay.interval(account.getCheckIntervalMinutes()),account.getTargetHoldMinutes());
            var receipt=store.issueSalePolicyReceipt(request,account,offer,read.planning.saleProgressAt.getOrDefault(slot,offer.getFirstSeenAt()),
                price,quantity,cut,at,horizon.staleOfferMinutes()*60L);
            // A retry revalidates current policy, but cannot renew the immutable issuance clock.
            if(receipt.getDecidedAt()>at || at-receipt.getDecidedAt()>5)
                return PolicyDecision.abstain("1",CompanionSalePolicy.VERSION,decision.getDecisionId(),
                    com.flippingfriend.core.PolicyDecisionType.SELL_MAINTENANCE,
                    com.flippingfriend.core.PolicyAbstentionReason.ACCOUNT_STATE_INCONSISTENT,"SALE_POLICY_RECEIPT_EXPIRED",at,at,
                    decision.getCandidateId(),source);
            return decision.withSalePolicyReceipt(receipt);
        }catch(Exception unavailable) {
            String identity=CompanionSalePolicy.identity(item,price,quantity,source,cut,slot);
            return PolicyDecision.abstain("1",CompanionSalePolicy.VERSION,identity+":"+at,
                com.flippingfriend.core.PolicyDecisionType.SELL_MAINTENANCE,
                com.flippingfriend.core.PolicyAbstentionReason.ACCOUNT_STATE_INCONSISTENT,"SALE_POLICY_RECEIPT_UNAVAILABLE",at,at,identity,source);
        }
    }

	CompanionHealth health()
	{
		long now = Instant.now().getEpochSecond();
		long marketAt = market.state().observedAt;
		boolean fresh = marketAt > 0 && now - marketAt <= STALE_MARKET_SECONDS;
		String status = fresh ? "READY" : "DEGRADED";
		String detail = healthReason;
		if (!fresh)
		{
			// The status and the sentence have to agree. This said DEGRADED while the reason read
			// "Ready." for two hours, and the reason is the half a person actually reads.
			detail = marketAt <= 0
				? "Waiting for the first market data."
				: "Market data is " + ((now - marketAt) / 60) + " minutes old; ingestion has stalled.";
		}
		if (!faults.isEmpty())
		{
			// An outstanding fault outranks the rolling status line, whatever that currently says.
			status = "DEGRADED";
			detail = String.join(" ", faults.entrySet().stream()
				.map(e -> e.getKey() + " failed: " + e.getValue())
				.toArray(String[]::new));
		}
		int warm = series.warmCount(CandidateFactory.LIVE_SHORT_STEP);
		int wanted = planner.shortlistIds().size();
		if (wanted <= 0)
		{
			// Before the first planning pass there is no shortlist to be warm about, and saying
			// nothing here reads as "warmed up" — which is how a completely cold companion came to
			// describe itself as ready.
			detail += " Choosing which items to study.";
		}
		else if (warm < wanted)
		{
			detail += String.format(" Studying items: %d of %d have enough history.", warm, wanted);
		}

		if (planning)
		{
			detail += " Planning in progress.";
		}
		// The real version, not the literal 1 that stood here. A placeholder in a health response is
		// worse than an absent field: it looks like an answer, and there is no way to tell from the
		// outside that the model has been retrained seventy times since.
		long modelVersion = 0;
		try
		{
			modelVersion = store.latestModelVersion();
		}
		catch (Exception unavailable)
		{
			// Reporting zero is honest; failing a health check over a version number is not.
		}
		boolean planningAvailable = false;
		if (fresh && account != null && faults.keySet().stream().allMatch(key ->
			key.startsWith("Position projection item ") || key.equals("Snapshot reconciliation") || key.equals("Planning exposure")))
		{
			try { planningAvailable = currentPlanningProof(now); }
			catch (Exception unavailable) { /* Keep the planning gate closed. */ }
		}
		return new CompanionHealth(status, detail, marketAt, modelVersion, now, planningAvailable);
	}


	/** Asks for a re-plan without waiting for it, dropping the request if one is already queued. */
    private boolean samePlanningInputs(AccountSnapshot first,AccountSnapshot second) {
        if(first==null || second==null)return first==second;
        var a=gson.toJsonTree(first).getAsJsonObject();var b=gson.toJsonTree(second).getAsJsonObject();
        for(String field:java.util.List.of("observedAt","correlationId","offerReadSet","canonicalRisk","markedSessionDrawdown")){a.remove(field);b.remove(field);}
        return a.equals(b);
    }
    private boolean currentPlanningProof(long now) {
        AccountSnapshot snapshot=account;
        if(snapshot==null)return false;
        if(!snapshot.isAutomaticTradeScope())return false;
        var currentMarket=market.state();
        if(currentMarket==null || currentMarket.observedAt<=0 || currentMarket.observedAt>now
                || now-currentMarket.observedAt>STALE_MARKET_SECONDS)return false;
        try {
            var read=store.planningProjections(snapshot.getAccountId());
            var exposure=DurablePlanningExposure.forAccount(read,snapshot,now);
            return exposure!=null && DurableRisk.evaluate(read,snapshot,exposure,currentMarket,now).isAvailable();
        }
        catch(Exception unavailable){return false;}
    }
    /** Neither a dropped client fill nor a dropped companion fill can increase the known limit. */
    Map<Integer,Integer> remainingBuyLimits(AccountSnapshot snapshot,long at) {
        Map<Integer,Integer> remaining=new java.util.HashMap<>();
        for(var item:market.state().mapping.values()) {
            int used=Math.max(0,snapshot.getBuyLimitUsed().getOrDefault(item.id,0));
            int clientRemaining=Math.max(0,item.buyLimit-used);
            remaining.put(item.id,Math.min(clientRemaining,
                buyLimits.remainingAt(item.id,item.buyLimit,java.time.Instant.ofEpochSecond(at))));
        }
        return remaining;
    }
	private void requestPlan()
	{
		if (account == null)
		{
			return;
		}

		// Reuse only unchanged economic/market/history inputs within their validity. New inputs
		// schedule immediately; queue coalescing prevents storms without a fixed latency floor.
		AccountSnapshot snapshot = account;
		int freeSlots = snapshot == null ? -1 : snapshot.getFreeSlots();
		boolean sellOnly = snapshot != null && snapshot.isSellOnly();
		long now = Instant.now().getEpochSecond();
		var currentMarket=market.state();
		var currentRisk=plan==null?null:plan.getCanonicalRisk();
		if (plan!=null && lastPlannedRevision==planningInputRevision.get()
            && lastPlannedPilotRevision==pilotRevision.get()
			&& currentMarket!=null && currentMarket.observedAt==lastPlannedMarketAt
			&& lastPlannedSeriesRevision==series.revision()
			&& freeSlots == lastPlanFreeSlots && sellOnly == lastPlanSellOnly
			&& plan.getExpiresAt()>now+Math.min(PLAN_RENEWAL_MARGIN_SECONDS,
                Math.max(1,(plan.getExpiresAt()-plan.getCreatedAt())/3))
			&& (currentRisk==null || currentRisk.getValidUntil()!=null && currentRisk.getValidUntil()>now))
		{
			return;
		}

		if (!planQueued.compareAndSet(false, true))
		{
			return;
		}
		var completion=new java.util.concurrent.CompletableFuture<Void>();
		planCompletion=completion;
		planThread.execute(() ->
		{
			planQueued.set(false);
			planning = true;
			try
			{
				refreshPlan();
				lastPlanAt = Instant.now().getEpochSecond();
				lastPlanFreeSlots = freeSlots;
				lastPlanSellOnly = sellOnly;
			}
			catch (Exception ex)
			{
				fault("Planning", String.valueOf(ex), ex);
			}
			finally
			{
				planning = false;
				completion.complete(null);
			}
		});
	}

	private void refreshPlan()
	{
        long revision=planningInputRevision.get();
        long optionalRevision=pilotRevision.get();
		AccountSnapshot snapshot = account;
		if (snapshot == null)
		{
			return;
		}
		AccountSnapshot sourceSnapshot = snapshot;
		var planningMarket=market.state();
		long seriesRevision=series.revision();
		Map<Integer,Long> bounds = new java.util.HashMap<>();
		com.flippingfriend.core.AccountRiskSnapshot risk = null;
		try
		{
			var read = store.planningProjections(snapshot.getAccountId());
			DurablePlanningExposure exposure =
				DurablePlanningExposure.forAccount(read, snapshot,Instant.now().getEpochSecond());
			if (exposure == null)
				throw new IllegalStateException(DurablePlanningExposure.refusalReason(read,snapshot,Instant.now().getEpochSecond()));
            risk=DurableRisk.evaluate(read,snapshot,exposure,planningMarket,Instant.now().getEpochSecond());
            if(!risk.isAvailable()) {
                synchronized(this) {
                    if(revision!=planningInputRevision.get() || account!=sourceSnapshot)return;
                    plan=PortfolioPlan.unavailable(snapshot.getCorrelationId(),PortfolioPlanOutcome.COMPANION_STATE_UNAVAILABLE,
                        risk.getReason(),risk.getEvaluatedAt()).withCanonicalRisk(risk);
                }
                return;
            }
            var waitingExit=ExitSlotReservation.awaiting(read,snapshot,0);
            if(waitingExit!=null) {
                synchronized(this) {
                    if(revision!=planningInputRevision.get() || account!=sourceSnapshot)return;
                    plan=PortfolioPlan.unavailable(snapshot.getCorrelationId(),PortfolioPlanOutcome.NO_FREE_SLOT,
                        ExitSlotReservation.reason(waitingExit),risk.getEvaluatedAt()).withCanonicalRisk(risk);
                }
                return;
            }
			snapshot = snapshot.withPlanningRestrictions(exposure.committedByItem,EntryStopPause.restrictions(exposure,read,planningMarket,Instant.now().getEpochSecond()));
			for (int item : exposure.unavailableEntryItems)
				bounds.put(item, exposure.committedByItem.getOrDefault(item, 0L));
            java.util.Set<Integer> occupied=new java.util.HashSet<>();
            for(var p:read.offers)if(p.getState()!=com.flippingfriend.core.OfferLifecycleState.EMPTY)occupied.add(p.getItemId());
            snapshot=snapshot.withPlanningOffers(occupied).withCanonicalRisk(risk);
			resolved("Planning exposure");
		}
		catch (Exception unavailable)
		{
			synchronized (this) {
			if (revision!=planningInputRevision.get() || account != sourceSnapshot) return;
			fault("Planning exposure", String.valueOf(unavailable.getMessage()), unavailable);
			plan = PortfolioPlan.unavailable(
				snapshot.getCorrelationId() == null ? "none" : snapshot.getCorrelationId(),
				PortfolioPlanOutcome.COMPANION_STATE_UNAVAILABLE,
				unavailable.getMessage()==null?"Recorded account evidence is unavailable.":unavailable.getMessage(),
				Instant.now().getEpochSecond());
			return;
			}
		}
		PortfolioPlan computed = planner.plan(planningMarket, snapshot,
			remainingBuyLimits(snapshot,Instant.now().getEpochSecond()), activeOffers.getActiveOffers())
			.withUncertainCapitalBounds(bounds).withCanonicalRisk(risk);
        synchronized (this) {
		if (revision!=planningInputRevision.get() || market.state()!=planningMarket || !samePlanningInputs(account,sourceSnapshot)
            || (sourceSnapshot.isAutomaticTradeScope() && !currentPlanningProof(Instant.now().getEpochSecond()))) return;
		plan = computed;
		lastPlannedRevision=revision;
		lastPlannedMarketAt=planningMarket.observedAt;
		lastPlannedSeriesRevision=seriesRevision;
        lastPlannedPilotRevision=optionalRevision;
        }
		try
		{
			store.savePlan(computed.getCreatedAt(), computed.getExpiresAt(),
				computed.getCorrelationId(), gson.toJson(computed));
			resolved("Plan persistence");
		}
		catch (Exception ex)
		{
			fault("Plan persistence", String.valueOf(ex.getMessage()), ex);
		}
	}

	/**
	 * The market feed as it stands, for the plugin to start from.
	 * <p>
	 * This process is already holding a copy of every wiki payload, refreshed every sixty seconds and
	 * running whether or not the game is. The plugin was re-downloading all of it on every launch and
	 * refusing to advise until it arrived -- which is what "getting the latest prices" was waiting
	 * for. Handing it over the loopback costs milliseconds and the data is no older than a minute, so
	 * nothing is traded on a stale price to buy the speed.
	 */
	MarketFeed marketFeed()
	{
		MarketIngestionService.MarketState state = market.state();
		MarketFeed feed = new MarketFeed();
		feed.observedAt = state.observedAt;
		feed.latest = state.latest;
		feed.fiveMinute = state.fiveMinute;
		feed.hourly = state.hourly;
		feed.fiveMinuteStartedAt=state.fiveMinuteStartedAt;
		feed.hourlyStartedAt=state.hourlyStartedAt;
		return feed;
	}

	/**
	 * One item's history, from the warm cache only. Never fetches.
	 * <p>
	 * The second half of the plugin's cold start: sixty per-item requests, made one at a time on a
	 * single thread, during which every candidate is vetoed for want of history. Every one of them is
	 * usually already here.
	 */
	java.util.List<Candle> series(int itemId, String timestep)
	{
		return series.series(itemId, timestep);
	}

	/** What the plugin needs to start warm: the three all-item payloads, exactly as the wiki sends them. */
	static final class MarketFeed
	{
		long observedAt;
		long fiveMinuteStartedAt,hourlyStartedAt;
		com.google.gson.JsonObject latest;
		com.google.gson.JsonObject fiveMinute;
		com.google.gson.JsonObject hourly;
	}

	@Override
	public void close()
	{
		planThread.shutdownNow();
		warmThread.shutdownNow();
		if (ownsMarket) market.close();
		// Whatever the last warming pass collected, before the process goes. Saving is throttled
		// during a run, so without this a restart discards up to five minutes of fetching.
		if (ownsMarket) series.save();
	}
}
