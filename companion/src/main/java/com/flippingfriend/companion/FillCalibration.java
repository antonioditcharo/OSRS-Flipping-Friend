package com.flippingfriend.companion;

import com.flippingfriend.core.OfferEvent;
import com.flippingfriend.learning.IsotonicCalibrator;
import com.flippingfriend.learning.ThompsonSampler;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Turns settled offers into corrections the engine can apply.
 *
 * <p>This is the join the system was built around and never made. {@code ExecutionRecorder} has
 * always written what happened, and the planner has always claimed what would happen; until
 * 2 September 2026 the two were computed side by side in {@code CompanionService.offer} and both
 * discarded. Everything here consumes evidence that was already being produced.
 *
 * <h2>Two channels, deliberately separate</h2>
 *
 * <p><b>Completion</b> is calibrated per leg, not per flip. A buy resting below the market and a sell
 * resting above it fail for unrelated reasons, and pooling them produces a curve that describes
 * neither — which is also why {@code getCompletionProbability()}, the product of the two, is the wrong
 * thing to score a single leg against. Audit item 29 asks for reliability diagrams per leg for the
 * same reason.
 *
 * <p><b>Duration</b> is a per-item multiplier from {@link LearnedDurations}, fed from the
 * {@code paired_*} columns, which count only offers that both completed and carried a prediction.
 *
 * <h2>What is deliberately not observed</h2>
 *
 * <p>An offer with no claim attached — an unprompted trade, or a leg the planner never scored —
 * contributes nothing. Feeding it in as a zero would teach the calibrator that the model predicts
 * zero and is usually wrong, which is worse than not learning at all. {@code predictedCompletion}'s
 * own javadoc has said so since before there was anything to feed.
 *
 * <p>Both corrections stay inert until they have enough evidence:
 * {@link IsotonicCalibrator#MIN_OBSERVATIONS} for completion, and a per-item shrinkage weight for
 * duration. A cold calibrator returns its input unchanged, so wiring this in cannot move a price
 * before it has grounds to.
 */
final class FillCalibration
{
	/**
	 * How often the per-item duration multipliers are recomputed. {@link LearnedDurations#refresh}
	 * reads the whole {@code execution_stat} table and rebuilds every multiplier, which is far too
	 * much to repeat on each settling offer during a busy session, and far more often than the
	 * numbers meaningfully move.
	 */
	private static final long DURATION_REFRESH_SECONDS = 120;

	/**
	 * How far above its own mean a draw must land before it counts as exploration. Purely for
	 * reporting — the ranking uses every draw. Without a threshold the rate would read as ~50%,
	 * since half of all draws land above the mean by some trivial amount.
	 */
	private static final double EXPLORATION_MARGIN = 0.02;

	private final IsotonicCalibrator buyCompletion = new IsotonicCalibrator();
	private final IsotonicCalibrator sellCompletion = new IsotonicCalibrator();
	private final LearnedDurations durations = new LearnedDurations();

	/**
	 * Exploration over the buy leg — the leg that decides whether a position is entered at all, and
	 * therefore the only one where a draw can win an under-rated item a slot it would never otherwise
	 * get. The sell leg is calibrated but not sampled: by the time it matters the capital is already
	 * committed, so perturbing it buys no information and only adds noise to the ranking.
	 */
	private final ThompsonSampler exploration = new ThompsonSampler();

	private final AtomicLong draws = new AtomicLong();
	private final AtomicLong exploratoryDraws = new AtomicLong();

	private volatile long lastDurationRefresh;

	/**
	 * Records one settled offer against the claim that was made for it.
	 *
	 * @param claimed the probability the plan gave this leg, or 0 when no claim was attached
	 * @return whether the observation was used, so a caller can report the fact
	 */
	boolean observeSettled(OfferEvent event, double claimed)
	{
		if (event == null || claimed <= 0)
		{
			return false;
		}
		calibratorFor(event.isBuying()).observe(claimed, event.isComplete());
		if (event.isBuying())
		{
			// Same evidence, different use: the calibrator learns how wrong the number was, the
			// sampler learns how uncertain this item still is.
			exploration.observe(event.getItemId(), event.isComplete());
		}
		return true;
	}

	/**
	 * A draw from this item's completion posterior, for <em>ranking</em>. Pass the already-calibrated
	 * probability: it becomes the prior, so the draw is centred on the corrected estimate rather than
	 * the raw one.
	 *
	 * <p>Rank on this and display {@link #calibrate}. The spread of the draw is the exploration, and
	 * it narrows on its own as evidence accumulates — the policy anneals without a schedule, and an
	 * item nobody has tried is exactly the one whose draw is widest.
	 */
	double explore(int itemId, double calibratedProbability)
	{
		double drawn = exploration.sample(itemId, calibratedProbability);
		draws.incrementAndGet();
		if (drawn > calibratedProbability + EXPLORATION_MARGIN)
		{
			exploratoryDraws.incrementAndGet();
		}
		return drawn;
	}

	/**
	 * Share of draws that landed more than {@value #EXPLORATION_MARGIN} above their own mean.
	 *
	 * <p>Read this as the width of the posterior, not as a suggestion-level exploration rate. Audit
	 * item 26 targets roughly one suggestion in ten being a close runner-up, and that is a different
	 * quantity: it would need the optimiser run twice per cycle, once on means and once on draws, and
	 * the two top picks compared. Item 18 already flags the optimiser as expensive and unbudgeted, so
	 * a third exact solve per cycle is the wrong trade until that is fixed.
	 *
	 * <p>What this number does give is the annealing curve. It starts high — an untried item drawing
	 * against a prior of strength {@code PRIOR_STRENGTH} is genuinely uncertain — and falls as fills
	 * accumulate. A rate that stays flat means evidence is not reaching the sampler.
	 */
	double explorationRate()
	{
		long total = draws.get();
		return total <= 0 ? 0.0 : (double) exploratoryDraws.get() / total;
	}

	/** True when the duration multipliers are due a rebuild. */
	boolean durationsDue(long nowEpochSeconds)
	{
		return nowEpochSeconds - lastDurationRefresh >= DURATION_REFRESH_SECONDS;
	}

	void refreshDurations(Map<Integer, SqliteStore.ExecutionStat> stats, long nowEpochSeconds)
	{
		durations.refresh(stats);
		lastDurationRefresh = nowEpochSeconds;
	}

	/**
	 * Corrects a predicted completion probability for the leg it belongs to. Returns the input
	 * unchanged until that leg's calibrator has {@link IsotonicCalibrator#MIN_OBSERVATIONS}.
	 */
	double calibrate(boolean buying, double predicted)
	{
		return calibratorFor(buying).calibrate(predicted);
	}

	/** How much longer this item's fills really take than predicted. 1.0 means no correction. */
	double durationMultiplier(int itemId)
	{
		return durations.multiplierFor(itemId);
	}

	boolean isActive()
	{
		return buyCompletion.isActive() || sellCompletion.isActive() || durations.itemsLearned() > 0
			|| exploration.itemsTracked() > 0;
	}

	/**
	 * One line for {@code /v1/health}. Reported rather than hidden because the failure this whole
	 * change addresses is a component that existed, did nothing, and said nothing about it — a
	 * calibrator nobody can see is indistinguishable from one that was never wired.
	 */
	String summary()
	{
		if (!isActive())
		{
			int seen = buyCompletion.observations() + sellCompletion.observations();
			return String.format("Calibration: learning, %d of %d settled offers needed.",
				seen, IsotonicCalibrator.MIN_OBSERVATIONS * 2);
		}
		return String.format(
			"Calibration: buy %d obs%s, sell %d obs%s; durations for %d items, pooled %.2fx; "
				+ "%.0f%% of draws above estimate across %d items.",
			buyCompletion.observations(), bias(buyCompletion),
			sellCompletion.observations(), bias(sellCompletion),
			durations.itemsLearned(), durations.overallRatio(),
			explorationRate() * 100.0, exploration.itemsTracked());
	}

	/** Positive bias means the model is optimistic — it claimed more than happened. */
	private static String bias(IsotonicCalibrator calibrator)
	{
		if (!calibrator.isActive())
		{
			return "";
		}
		return String.format(" (%+.1f%% optimistic)", calibrator.meanBias() * 100.0);
	}

	private IsotonicCalibrator calibratorFor(boolean buying)
	{
		return buying ? buyCompletion : sellCompletion;
	}
}
