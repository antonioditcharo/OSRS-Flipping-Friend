package com.flippingfriend.companion;

import com.flippingfriend.core.OfferEvent;
import com.flippingfriend.learning.IsotonicCalibrator;
import java.util.Map;

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

	private final IsotonicCalibrator buyCompletion = new IsotonicCalibrator();
	private final IsotonicCalibrator sellCompletion = new IsotonicCalibrator();
	private final LearnedDurations durations = new LearnedDurations();

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
		return true;
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
		return buyCompletion.isActive() || sellCompletion.isActive() || durations.itemsLearned() > 0;
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
			"Calibration: buy %d obs%s, sell %d obs%s; durations for %d items, pooled %.2fx.",
			buyCompletion.observations(), bias(buyCompletion),
			sellCompletion.observations(), bias(sellCompletion),
			durations.itemsLearned(), durations.overallRatio());
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
