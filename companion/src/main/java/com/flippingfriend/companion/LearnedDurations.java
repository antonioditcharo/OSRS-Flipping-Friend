package com.flippingfriend.companion;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Per-item correction factors for how long fills really take against how long the model said.
 *
 * <p>Recovered on 2 September 2026 from {@code flipping-friend-trainer.jar} and verified by bytecode
 * diff against the original. The javadoc is reconstructed; the code is exact.
 *
 * <p>This is the class the audit describes as needing "perhaps 120 lines" of new work. It already
 * existed, and it consumes precisely the table the audit says nothing reads: the {@code paired_*}
 * columns of {@code execution_stat}, which {@link ExecutionRecorder} has been filling all along.
 * Only offers that both completed and carried a prediction contribute, so the numerator and
 * denominator describe the same trades — the discipline {@link SqliteStore.ExecutionStat#durationRatio}
 * already enforces.
 *
 * <p>Two things stop a handful of unlucky flips from moving an item's multiplier far:
 *
 * <ul>
 *   <li>Each item is shrunk toward a prior by the weight {@code n / (n + }{@value #PRIOR_STRENGTH}{@code )},
 *       so an item needs {@value #PRIOR_STRENGTH} completions before its own evidence outweighs the
 *       prior at all.
 *   <li>That prior is computed <em>leave-one-out</em> — the pooled ratio with this item's own totals
 *       subtracted. Shrinking an item toward a mean that already contains it would let a
 *       heavily-traded item pull its own target and appear better calibrated than it is.
 * </ul>
 *
 * <p>The result is clamped to [{@value #MIN_MULTIPLIER}, {@value #MAX_MULTIPLIER}] and any degenerate
 * value collapses to 1.0, so a division by a stale zero yields "no correction" rather than a
 * multiplier that silently disables an item.
 */
final class LearnedDurations
{
	/** Completions needed before an item's own evidence carries half the weight. */
	private static final int PRIOR_STRENGTH = 8;

	private static final double MIN_MULTIPLIER = 0.5;
	private static final double MAX_MULTIPLIER = 3.0;

	private volatile Map<Integer, Double> multipliers = Collections.emptyMap();
	private volatile int itemsLearned;
	private volatile double overallRatio = 1.0;

	LearnedDurations()
	{
	}

	/** Recomputes every multiplier from the current execution statistics. */
	void refresh(Map<Integer, SqliteStore.ExecutionStat> stats)
	{
		if (stats == null)
		{
			return;
		}
		if (stats.isEmpty())
		{
			multipliers = Collections.emptyMap();
			itemsLearned = 0;
			overallRatio = 1.0;
			return;
		}

		double totalRealised = 0.0;
		double totalPredicted = 0.0;
		int totalCompleted = 0;
		for (SqliteStore.ExecutionStat stat : stats.values())
		{
			if (stat.pairedCompleted > 0 && stat.predictedMinutes > 0.0)
			{
				totalRealised += stat.pairedFillMinutes;
				totalPredicted += stat.predictedMinutes;
				totalCompleted += stat.pairedCompleted;
			}
		}
		double pooled = shrinkTowardsNeutral(totalRealised, totalPredicted, totalCompleted);

		Map<Integer, Double> learned = new HashMap<>();
		for (Map.Entry<Integer, SqliteStore.ExecutionStat> entry : stats.entrySet())
		{
			SqliteStore.ExecutionStat stat = entry.getValue();
			if (stat.pairedCompleted <= 0 || stat.predictedMinutes <= 0.0)
			{
				continue;
			}
			// Leave-one-out: this item's own totals are removed before it is shrunk toward the rest.
			double priorRealised = totalRealised - stat.pairedFillMinutes;
			double priorPredicted = totalPredicted - stat.predictedMinutes;
			int priorCompleted = totalCompleted - stat.pairedCompleted;
			double prior = shrinkTowardsNeutral(priorRealised, priorPredicted, priorCompleted);

			double observed = stat.durationRatio();
			double weight = (double) stat.pairedCompleted / (stat.pairedCompleted + PRIOR_STRENGTH);
			learned.put(entry.getKey(), clamp(weight * observed + (1.0 - weight) * prior));
		}

		multipliers = learned;
		itemsLearned = learned.size();
		overallRatio = pooled;
	}

	/** This item's multiplier, or the pooled ratio when it has none of its own yet. */
	double multiplierFor(int itemId)
	{
		Double learned = multipliers.get(itemId);
		return learned != null ? learned : overallRatio;
	}

	int itemsLearned()
	{
		return itemsLearned;
	}

	double overallRatio()
	{
		return overallRatio;
	}

	/**
	 * Pools a realised/predicted ratio and pulls it toward 1.0 in proportion to how little evidence
	 * stands behind it. With no evidence the answer is exactly 1.0 — no correction — rather than a
	 * ratio computed from nothing.
	 */
	private static double shrinkTowardsNeutral(double realised, double predicted, int completed)
	{
		if (predicted <= 0.0 || completed <= 0)
		{
			return 1.0;
		}
		double weight = (double) completed / (completed + PRIOR_STRENGTH);
		return clamp(weight * (realised / predicted) + (1.0 - weight));
	}

	/** Degenerate values become 1.0, so a bad statistic means "no correction", never "skip this item". */
	private static double clamp(double value)
	{
		if (!Double.isFinite(value) || value <= 0.0)
		{
			return 1.0;
		}
		return Math.max(MIN_MULTIPLIER, Math.min(MAX_MULTIPLIER, value));
	}
}
