package com.flippingfriend.companion;

import com.flippingfriend.data.Candle;
import java.util.List;
import com.flippingfriend.core.AccountSnapshot;
import com.flippingfriend.core.CompanionHealth;
import com.flippingfriend.core.OfferEvent;
import com.flippingfriend.core.PortfolioPlan;
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
	private final SqliteStore store;
	private final MarketIngestionService market;
	private final SeriesCache series;
	private final PortfolioPlanner planner;
	private final ActiveOfferTracker activeOffers;
	/** Buy-limit windows are per account and reset four hours after the first purchase in them. */
	private final BuyLimitLedger buyLimits = new BuyLimitLedger();
	private final ExecutionRecorder executions;
	private final FillCalibration calibration = new FillCalibration();
	private final ShadowTrader shadow = new ShadowTrader(new com.flippingfriend.model.TaxCalculator());

	/**
	 * What the calibration snapshot is stored under. One name, so {@code loadModel} finds the most
	 * recent and {@code pruneModels} can retire the rest.
	 */
	private static final String CALIBRATION_MODEL = "fill-calibration";

	/** Stored separately from the calibration: different data, and one can fail to load without the other. */
	private static final String SHADOW_MODEL = "shadow-trades";

	private final LearnedFillModel learnedFill = new LearnedFillModel();
	/**
	 * How much of the flow at our price we really win, measured from our own settled offers.
	 * <p>
	 * The public feed cannot answer this — it publishes what traded, never who was queued for it —
	 * so an offer we placed ourselves is the only experiment that can, and every one of them has been
	 * running and going unrecorded.
	 */
	private final CaptureRates captureRates = new CaptureRates();

	/**
	 * How the odds of an offer filling change the longer it waits.
	 * <p>
	 * Fed from the same settled offers everything else here reads, including the cancelled ones that
	 * {@link ExecutionRecorder} correctly discards: a cancellation is not a fill time, but it is the
	 * observation "this had not filled yet", which is exactly what a hazard is made of.
	 */
	private final FillHazard fillHazard = new FillHazard();


	/**
	 * How often the learned state is written back. Frequent enough that a crash costs minutes of
	 * evidence rather than a session, rare enough that it is not a write per offer.
	 */
	private static final long CALIBRATION_SAVE_SECONDS = 300;

	/** Snapshots to keep, so a bad one is a rollback rather than a rebuild. */
	private static final int CALIBRATION_HISTORY = 5;

	private volatile long lastCalibrationSave;

	/** How often five-minute history older than the fine-retention window is compacted to hourly. */
	private static final long ROLLUP_INTERVAL_SECONDS = 6 * 3600;

	private volatile long lastRollUp;


	/** Single thread, so plans are computed one at a time and in order. */
	private final ExecutorService planThread = Executors.newSingleThreadExecutor(runnable ->
	{
		Thread thread = new Thread(runnable, "flipping-friend-planner");
		thread.setDaemon(true);
		return thread;
	});
	/** True while a re-plan is already queued, which makes further requests redundant. */
	private final AtomicBoolean planQueued = new AtomicBoolean();

	/**
	 * Fetches per-item history in the background so planning never waits on the network.
	 * <p>
	 * Separating the two is what allows the shortlist to be hundreds of items rather than forty-five.
	 * Fetched inline, every extra item lengthened the planning cycle, so the number the plugin could
	 * consider was decided by how long a cycle was allowed to take rather than by how many were worth
	 * looking at. Here the cost is spread across the cache lifetime instead, at a rate a free
	 * community API can absorb without noticing.
	 */
	private final ExecutorService warmThread = Executors.newSingleThreadExecutor(runnable ->
	{
		Thread thread = new Thread(runnable, "flipping-friend-warmer");
		thread.setDaemon(true);
		return thread;
	});

	private volatile AccountSnapshot account;
	/** When the last plan was built, and against how many free slots, for the spacing rule below. */
	private volatile long lastPlanAt;
	private volatile int lastPlanFreeSlots = -1;
	/** What the last plan was built under, so a change of intent is not held behind the spacing. */
	private volatile boolean lastPlanSellOnly;
	private volatile PortfolioPlan plan;
	private static final Logger log = LoggerFactory.getLogger(CompanionService.class);

	/** How old market data may get before the companion stops calling itself healthy. */
	private static final long STALE_MARKET_SECONDS = 120;
	/**
	 * The least time between two plan builds when nothing structural has changed. Short enough that
	 * the plan is never stale against its 150-second life, long enough that a slot transition does not
	 * rebuild it three times a second.
	 */
	private static final long MIN_PLAN_SPACING_SECONDS = 5;

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
		this.gson = gson;
		this.store = store;
		this.activeOffers = new ActiveOfferTracker();
		this.market = new MarketIngestionService(gson);
		// Start recording immediately. This is the one item on the plan where waiting has a permanent
		// cost: the API retains a bounded window, so a day not recorded is a day nobody can recover.
		try
		{
			this.market.setArchive(store.priceArchive());
		}
		catch (Exception unavailable)
		{
			log.warn("Price archive unavailable; history will not accumulate", unavailable);
		}
		this.series = cacheDir == null ? new SeriesCache()
			: new SeriesCache(cacheDir.resolve("series-cache.json.gz"));
		this.planner = new PortfolioPlanner(series);
		// Wired here, not merely offered: audit item 11 is a list of setters that exist, compile and
		// are never called, with fields nothing reads. A correction nothing consults is the same bug
		// in a new place.
		this.planner.setCalibration(calibration);
		this.planner.setShadowTrader(shadow);
		this.planner.setBuyLimitLedger(buyLimits);
		this.planner.setLearnedFillModel(learnedFill);
		this.planner.setCaptureRates(captureRates);
		this.planner.setFillHazard(fillHazard);
		restoreCalibration();
		restoreCaptureRates();
		restoreFillHazard();
		this.executions = new ExecutionRecorder(store);
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
		long since = Instant.now().minus(BuyLimitLedger.WINDOW).getEpochSecond();
		int replayed = 0;
		for (String payload : store.recentOfferEvents(since))
		{
			OfferEvent event = gson.fromJson(payload, OfferEvent.class);
			if (event != null)
			{
				buyLimits.apply(event);
				activeOffers.apply(event);
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
				java.util.List<Integer> wanted = planner.shortlistIds();
				if (!wanted.isEmpty())
				{
					int spent = series.warm(wanted, CandidateFactory.LIVE_SHORT_STEP, WARM_BUDGET);
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

	void account(AccountSnapshot snapshot) throws Exception
	{
		account = snapshot;
		// The plugin's ledger is durable; this one is rebuilt from events that can go missing. Take
		// the higher of the two so a dropped fill is corrected on the next cycle rather than never.
		buyLimits.reconcile(snapshot.getBuyLimitUsed());
		store.recordEvent(snapshot.getObservedAt(), snapshot.getCorrelationId(), "ACCOUNT_STATE",
			gson.toJson(snapshot));
		requestPlan();
	}

	void offer(OfferEvent event) throws Exception
	{
		store.recordEvent(event.getObservedAt(), event.getCorrelationId(), event.getEventType(),
			gson.toJson(event));
		buyLimits.apply(event);
		activeOffers.apply(event);
		// Settled offers are the only direct evidence of how our own orders behave in the queue,
		// as opposed to what the public price history says the market did.
		boolean settled = executions.record(event);
		if (settled)
		{
			// Both of these were computed and discarded until 2 September 2026, which is why the
			// comparison the system is built around had never been made once.
			calibration.observeSettled(event, predictedCompletion(event));
			observeCapture(event);
			observeHazard(event);

			long now = event.getObservedAt();
			if (calibration.durationsDue(now))
			{
				// Rebuilt on a timer rather than per offer: refresh() reads the whole execution_stat
				// table, and the multipliers do not move fast enough to justify that on every fill.
				calibration.refreshDurations(store.executionStats(), now);
			}
			if (now - lastCalibrationSave >= CALIBRATION_SAVE_SECONDS)
			{
				saveCalibration(now);
			}
			if (now - lastRollUp >= ROLLUP_INTERVAL_SECONDS)
			{
				compactArchive(now);
			}
			if (now - lastTrainedAt >= RETRAIN_INTERVAL_SECONDS)
			{
				// RETRAIN_INTERVAL_SECONDS and lastTrainedAt have both existed since the companion was
				// written and neither was ever read - audit item 82 lists the consequence, a monitor
				// reporting a model version that was always zero. This is the first thing to use them.
				lastTrainedAt = now;
				// Refit against everything the shadow channel has resolved, and let the gate decide
				// whether the result is allowed anywhere near a decision.
				learnedFill.retrain(shadow.trainingSet(true));
			}
		}

		// An offer changing is the single most important reason to re-plan: a slot has just opened
		// or closed, capital has moved, and a buy limit may have been consumed. Waiting for the next
		// market poll would leave the panel telling the player to do something they have just done.
		requestPlan();
	}

	/**
	 * Measures what this offer won against the flow that passed it, and records the answer.
	 * <p>
	 * The denominator comes from the archive rather than the live series cache, because the question
	 * is about the interval this offer was open — which is already in the past by the time it settles,
	 * and which the public API's rolling window will eventually stop covering. This is the first
	 * consumer of {@link PriceArchive} outside the replay harness, and the reason it was worth
	 * starting to record on day one.
	 * <p>
	 * A failure here must never cost a fill. Everything above this point in {@link #offer} has
	 * already been persisted, so a missing archive costs one measurement rather than an offer.
	 */
	private void observeCapture(OfferEvent event)
	{
		long open = event.getFirstSeenAt();
		long closed = event.getObservedAt();
		if (open <= 0 || closed - open < CaptureRates.MIN_OPEN_SECONDS)
		{
			return;
		}
		try
		{
			// One bucket of slack at each end, so the bars straddling the interval are fetched and can
			// be prorated rather than dropped for having started a moment too early.
			List<Candle> bars = store.priceArchive().series(event.getItemId(),
				PriceArchive.FIVE_MINUTE, open - 300, closed + 300);
			double flow = captureRates.observe(event, bars);
			if (flow > 0)
			{
				store.recordCapture(event.getItemId(), event.getFilledQuantity(), flow);
			}
		}
		catch (Exception unavailable)
		{
			log.debug("Could not measure capture for item {}", event.getItemId(), unavailable);
		}
	}

	/**
	 * Folds one settled offer into the fill hazard.
	 * <p>
	 * Both outcomes count, and that is the point. A completed offer says "it filled at this age"; a
	 * cancelled one says "it had not filled by this age", and a life table uses the second as
	 * readily as the first. About a third of settled offers are cancellations, and until now their
	 * durations went nowhere because the statistic being kept was a mean, which has no way to hold a
	 * censored observation.
	 */
	private void observeHazard(OfferEvent event)
	{
		long open = event.getFirstSeenAt();
		if (open <= 0)
		{
			// The plugin never saw this offer appear -- a login replay -- so its age is unknown
			// rather than zero, and a zero would put a fill in the first slice that did not happen
			// there.
			return;
		}
		double minutes = event.secondsOpen() / 60.0;
		boolean completed = event.isComplete();
		// Whether our own "this slot is worth more elsewhere" alert preceded the cancellation. Not a
		// motive -- the game supplies no reason -- but a circumstance, and the only observable one.
		boolean onOurAdvice = !completed && planner.cancelAdvice()
			.wasAdvised(event.getItemId(), event.getSlot(), event.getObservedAt());
		fillHazard.observe(event.getItemId(), event.isBuying(), minutes, completed, onOurAdvice);
		try
		{
			store.recordFillHazard(event.getItemId(), event.isBuying(),
				FillHazard.bucketOf(minutes), completed);
		}
		catch (Exception unavailable)
		{
			log.debug("Could not record the fill hazard for item {}", event.getItemId(), unavailable);
		}
	}

	/** Brings back everything previous sessions learned about capture. */
	private void restoreCaptureRates()
	{
		try
		{
			captureRates.restore(store.captureStats());
		}
		catch (Exception unavailable)
		{
			log.warn("Could not restore capture rates; they will rebuild from new fills", unavailable);
		}
	}

	/** Brings back the life table previous sessions built. */
	private void restoreFillHazard()
	{
		try
		{
			fillHazard.restore(store.fillHazards(), store.fillHazardObservations());
		}
		catch (Exception unavailable)
		{
			log.warn("Could not restore the fill hazard; it will rebuild from new offers", unavailable);
		}
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
		PortfolioPlan current = plan;
		long now = Instant.now().getEpochSecond();
		if (current == null || current.getExpiresAt() <= now)
		{
			return PortfolioPlan.unavailable("none", account == null
				? "Waiting for the game client to report your coins and free slots."
				: "Working out the best use of your slots.", now);
		}
		return current;
	}

	/**
	 * Reloads what previous sessions learned.
	 * <p>
	 * Without this the loop cannot get started on a machine that reboots: IsotonicCalibrator needs
	 * {@code MIN_OBSERVATIONS} settled offers per leg before it corrects anything, and a companion
	 * restarted daily would throw the count away before ever reaching it. The Thompson posteriors
	 * matter for the same reason — every item reverting to untried restarts the annealing each time.
	 */
	private void restoreCalibration()
	{
		try
		{
			String payload = store.loadModel(CALIBRATION_MODEL);
			if (payload == null)
			{
				return;
			}
			if (calibration.restore(gson.fromJson(payload, FillCalibration.Snapshot.class)))
			{
				healthReason = "Restored calibration from a previous session.";
			}
		}
		catch (Exception unreadable)
		{
			// A snapshot that cannot be read is discarded, not repaired. Starting cold is honest, and
			// the corrections are inert while cold, so the cost is a slow restart rather than a wrong
			// price. Not a fault: this is the expected path on a first run.
			log.warn("Could not restore calibration; starting cold", unreadable);
		}
		try
		{
			String payload = store.loadModel(SHADOW_MODEL);
			if (payload != null)
			{
				// Resolved outcomes are the accumulated veto evidence. VetoThresholds' published table
				// came from roughly 1,500 of them, a count nothing reaches if the history is thrown
				// away on every restart.
				shadow.restore(gson.fromJson(payload, ShadowTrader.Snapshot.class));
			}
		}
		catch (Exception unreadable)
		{
			log.warn("Could not restore shadow trades; starting with none", unreadable);
		}
	}

	/** Writes the learned state back, and retires all but the last {@value #CALIBRATION_HISTORY}. */
	private void saveCalibration(long now)
	{
		lastCalibrationSave = now;
		try
		{
			store.saveModel(CALIBRATION_MODEL, gson.toJson(calibration.snapshot()));
			store.saveModel(SHADOW_MODEL, gson.toJson(shadow.snapshot()));
			store.pruneModels(CALIBRATION_HISTORY);

			// Record the gate result beside the snapshot it describes. gate_result has existed since
			// the schema was written and nothing has ever written to it, so until now there was no way
			// to ask "was the calibration helping last Tuesday?" -- and that is exactly the question
			// worth asking after a change to the fill model.
			GateReport gates = calibration.gate();
			store.recordGates(now, gates.allPassed() ? "PROMOTED" : "HELD",
				gates.getGates(), Long.toString(store.latestModelVersion()));
		}
		catch (Exception unwritable)
		{
			// Losing a snapshot costs evidence, not correctness -- the in-memory state is unaffected
			// and the next attempt is five minutes away.
			log.warn("Could not save calibration", unwritable);
		}
	}

	/**
	 * Compacts five-minute history older than the fine-retention window down to hourly.
	 *
	 * <p>Roughly 4,600 items at twelve buckets an hour is about 1.3 million rows a day, which is not
	 * a size to keep at full resolution forever. The fill model needs five minutes only for recent
	 * history; the long-horizon features that justify keeping anything at all work at hourly, and it
	 * is about a twelfth of the rows.
	 */
	private void compactArchive(long now)
	{
		lastRollUp = now;
		if (PriceArchive.FINE_RETENTION_DAYS <= 0)
		{
			// Retention is unlimited by choice. Five-minute bars are the most informative thing this
			// system will ever own, and compacting them to save space that costs nothing would be
			// trading an asset for a saving.
			return;
		}
		try
		{
			store.priceArchive().rollUp(now - PriceArchive.FINE_RETENTION_DAYS * 86_400L);
		}
		catch (Exception unavailable)
		{
			log.warn("Could not compact the price archive", unavailable);
		}
	}

	/** How far back the archive can see, for the health line. */
	private String archiveSummary(long now)
	{
		try
		{
			PriceArchive archive = store.priceArchive();
			long earliest = archive.earliest();
			if (earliest <= 0)
			{
				return "Archive: empty.";
			}
			long days = Math.max(0, (now - earliest) / 86_400L);
			return String.format("Archive: %,d bars, %d days of history.", archive.rowCount(), days);
		}
		catch (Exception unavailable)
		{
			return "Archive: unavailable.";
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
		// Say what calibration is doing. IsotonicCalibrator and LearnedDurations both existed,
		// tested, for weeks without being called by anything -- and nothing in the product would
		// have looked different if they had never been written. A number on the health line is what
		// makes "the calibrator is learning" a claim that can be checked rather than assumed.
		detail += " " + calibration.summary();
		detail += " " + shadow.summary();
		// Said out loud so the archive cannot quietly fail to accumulate for months. The value of this
		// store is entirely in how far back it reaches, and that is not visible any other way.
		detail += " " + archiveSummary(now);
		detail += " " + learnedFill.summary();
		// Capture is the largest single lever on deployed capital and was a constant until it was
		// measured, so the health line has to say what the measurement found and how much of it there
		// is. "Learning" is not a claim anyone can check; "0.31 pooled against 0.50 assumed, from 74
		// offers" is.
		detail += " " + captureRates.summary(planner.assumedCaptureShare());
		// The shape of the curve, said out loud, because the whole question of whether this is worth
		// consulting is whether waiting gets worse -- and that is one number nobody can see any
		// other way.
		detail += " " + fillHazard.summary();
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
		return new CompanionHealth(status, detail, marketAt, modelVersion, now);
	}


	/** Asks for a re-plan without waiting for it, dropping the request if one is already queued. */
	private void requestPlan()
	{
		if (account == null)
		{
			return;
		}

		// Planning is triggered by the market cycle, by every account snapshot and by every offer
		// event, and an offer changing state produces several of those in a row. Six plans were built
		// in four seconds during one slot transition, each one replacing what the player was looking
		// at. The queue guard below stops two running at once; it does not stop them running
		// back-to-back for ever.
		//
		// So: leave a little space between builds, unless the thing that actually matters has moved.
		// A change in the free-slot count is exactly when a fresh plan is worth having immediately --
		// that is the moment a trade becomes possible -- and it is also the moment the old plan is
		// certainly wrong.
		//
		// Switching sell-only is the other such moment, and it was not covered. The plan carries a
		// list of things to buy; the instant the player says they are winding down, that list is
		// wrong in the way that matters most. Holding it behind the spacing -- and then behind the
		// plan's own ninety-second lifetime -- is what made the mode take so long to look like it had
		// done anything.
		AccountSnapshot snapshot = account;
		int freeSlots = snapshot == null ? -1 : snapshot.getFreeSlots();
		boolean sellOnly = snapshot != null && snapshot.isSellOnly();
		long now = Instant.now().getEpochSecond();
		if (freeSlots == lastPlanFreeSlots && sellOnly == lastPlanSellOnly
			&& now - lastPlanAt < MIN_PLAN_SPACING_SECONDS)
		{
			return;
		}

		if (!planQueued.compareAndSet(false, true))
		{
			return;
		}
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
			}
		});
	}

	private void refreshPlan()
	{
		AccountSnapshot snapshot = account;
		if (snapshot == null)
		{
			return;
		}
		PortfolioPlan computed = planner.plan(market.state(), snapshot,
			buyLimits.remaining(market.state().mapping), activeOffers.getActiveOffers());
		plan = computed;
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
		com.google.gson.JsonObject latest;
		com.google.gson.JsonObject fiveMinute;
		com.google.gson.JsonObject hourly;
	}

	@Override
	public void close()
	{
		planThread.shutdownNow();
		warmThread.shutdownNow();
		market.close();
		// Whatever the last warming pass collected, before the process goes. Saving is throttled
		// during a run, so without this a restart discards up to five minutes of fetching.
		series.save();
	}
}
