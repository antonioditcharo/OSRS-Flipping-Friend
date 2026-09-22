package com.flippingfriend.companion;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * How wrong the fill model's own timings have been, per item, from this account's real offers.
 * <p>
 * The companion has been recording every settled offer since it was built — 1,386 of them across 280
 * items on the account this was written against — into {@code execution_stat}, alongside what it had
 * predicted at the time. Nothing ever read them back. {@code SqliteStore.executionStats()} existed
 * with no callers, {@link CandidateFactory} carried a javadoc for a learned-model field that had been
 * deleted out from under it, and the line marked "where everything that has been learned re-enters
 * the decision" sat above four assignments that came straight from the raw model. The loop was open
 * at the last inch.
 * <p>
 * It is worth closing because the recorded error is not small or random. On that account the model
 * predicted 234 minutes for an item that took 47, and 264 for one that took 73 — a factor of three
 * to five, consistently long, on the items traded most.
 *
 * <h2>What this deliberately does not do</h2>
 * <p>
 * <b>Only the waiting is corrected.</b> An offer's predicted duration is a wait for the first
 * counterparty plus the time to work through the size ordered, and those two do not transfer
 * equally. Every observation in the table was recorded while order sizing was broken — the plan of
 * the day was seven trades of <em>one unit each</em> — so the throughput half of those measurements
 * describes a market absorbing single items and says nothing about one absorbing two thousand. The
 * wait, on the other hand, is how long it takes anybody at all to turn up at your price, which does
 * not depend on how many you asked for. So the multiplier is applied to the wait and the throughput
 * term is left alone. The table has no quantity column, so this cannot be checked after the fact; it
 * is reasoning about which half of a measurement survives a change in the thing measured.
 * <p>
 * <b>Completion rates are not touched.</b> The table counts them, and the inference is tempting —
 * things that fill faster than predicted presumably also complete more often. But a cancelled offer
 * is an unknown outcome rather than a failed one, that distinction has already been got wrong three
 * times in this codebase, and one of those times it was training a live model. Duration is the part
 * where the evidence is unambiguous.
 */
final class FillCalibration
{
	/** No history: every multiplier is exactly 1.0, so the calibrated model equals the raw one. */
	static final FillCalibration NEUTRAL = new FillCalibration(Collections.emptyMap(), 1.0);

	/**
	 * Prior strength, in offers. An item with three observations is pulled halfway back to the
	 * account-wide figure; one with thirty is mostly its own.
	 */
	private static final double ITEM_PRIOR = 3.0;
	/** The account's overall bias is itself shrunk toward "the model was right". */
	private static final double GLOBAL_PRIOR = 10.0;
	/**
	 * Bounds, tighter than the plugin-side calibrator's, because of the size caveat above: this is
	 * evidence about one market condition being applied to another, and a correction that can only
	 * halve or double is a correction that cannot run away with the plan.
	 */
	private static final double MIN_MULTIPLIER = 0.5;
	private static final double MAX_MULTIPLIER = 2.0;
	/** Below this there is no prediction to divide by. */
	private static final double MIN_MINUTES = 1e-6;
	/**
	 * Offers needed before any correction is applied at all.
	 * <p>
	 * A handful of settled offers is a handful of anecdotes, and this multiplies every duration the
	 * planner ranks on. Thirty is small enough to start working within a session or two of real
	 * trading and large enough that one strange afternoon cannot move it far.
	 */
	private static final int MIN_OBSERVATIONS = 30;

	private final Map<Integer, Double> byItem;
	private final double global;
	private final double minimum;
	private final double maximum;

	private FillCalibration(Map<Integer, Double> byItem, double global)
	{
		this(byItem, global, MIN_MULTIPLIER, MAX_MULTIPLIER);
	}

	private FillCalibration(Map<Integer, Double> byItem, double global,
			double minimum, double maximum)
	{
		this.byItem = byItem;
		this.global = global;
		this.minimum = minimum;
		this.maximum = maximum;
	}

	/**
	 * Constructs an explicitly bounded calibration for diagnostic generation only.
	 *
	 * <p>This does not alter the production bounds used by {@link #from(Map)}. It exists so
	 * package-local probes can pass a named scenario through the real candidate-generation path
	 * instead of duplicating that path or mutating production constants.
	 */
	static FillCalibration forDiagnostics(Map<Integer, Double> byItem, double global,
			double minimum, double maximum)
	{
		if (!Double.isFinite(global) || Double.isNaN(minimum) || Double.isNaN(maximum)
				|| minimum < 0 || maximum < minimum)
		{
			throw new IllegalArgumentException("invalid diagnostic calibration bounds");
		}

		Map<Integer, Double> copy = new HashMap<>();
		if (byItem != null)
		{
			for (Map.Entry<Integer, Double> entry : byItem.entrySet())
			{
				if (entry.getKey() == null || entry.getValue() == null
						|| !Double.isFinite(entry.getValue()))
				{
					throw new IllegalArgumentException(
							"diagnostic calibration entries must be finite");
				}
				copy.put(entry.getKey(), entry.getValue());
			}
		}

		return new FillCalibration(Collections.unmodifiableMap(copy), global, minimum, maximum);
	}

	/**
	 * Builds the corrections from what has actually been observed.
	 *
	 * @param stats per-item running totals, as {@code execution_stat} keeps them
	 */
	static FillCalibration from(Map<Integer, SqliteStore.ExecutionStat> stats)
	{
		if (stats == null || stats.isEmpty())
		{
			return NEUTRAL;
		}

		// The account-wide ratio first, because it is what a barely-seen item leans on.
		double actualTotal = 0;
		double predictedTotal = 0;
		int observedTotal = 0;
		for (SqliteStore.ExecutionStat stat : stats.values())
		{
			// Every settled offer, not only the ones that finished. See the note on the columns:
			// a completion is not a random offer, it is a quick one, and a duration model fed only
			// completions learns that the market is faster than it is. On the account this was built
			// against, completions alone gave a ratio of 0.10 -- the planner would have believed
			// every fill takes a tenth of the time it really does, and sized and ranked accordingly.
			actualTotal += stat.openMinutes;
			predictedTotal += stat.openPredictedMinutes;
			observedTotal += stat.openObserved;
		}
		if (observedTotal < MIN_OBSERVATIONS || predictedTotal <= MIN_MINUTES
			|| actualTotal <= MIN_MINUTES)
		{
			// Not enough uncensored evidence to correct anything with. Returning neutral leaves the
			// planner exactly where it was, which is the honest answer to having no usable data --
			// and is what happens on every existing install, because the columns this reads were
			// added after those rows were written and start at zero.
			return NEUTRAL;
		}
		double global = shrink(actualTotal / predictedTotal, observedTotal, GLOBAL_PRIOR, 1.0);

		Map<Integer, Double> byItem = new HashMap<>();
		for (Map.Entry<Integer, SqliteStore.ExecutionStat> entry : stats.entrySet())
		{
			SqliteStore.ExecutionStat stat = entry.getValue();
			if (stat.openObserved <= 0 || stat.openPredictedMinutes <= MIN_MINUTES
				|| stat.openMinutes <= MIN_MINUTES)
			{
				continue;
			}
			byItem.put(entry.getKey(), shrink(stat.openMinutes / stat.openPredictedMinutes,
				stat.openObserved, ITEM_PRIOR, global));
		}
		return new FillCalibration(byItem, global);
	}

	/**
	 * What to multiply this item's predicted <em>wait</em> by. Above one means the model has been
	 * optimistic about it, below one means pessimistic.
	 */
	double waitMultiplier(int itemId)
	{
		Double own = byItem.get(itemId);
		return bounded(own != null ? own : global);
	}

	/** How many items carry a correction of their own, for reporting. */
	int itemsLearned()
	{
		return byItem.size();
	}

	/** The account-wide bias, for reporting. */
	double overall()
	{
		return bounded(global);
	}

	private double bounded(double multiplier)
	{
		return multiplier < minimum ? minimum
				: multiplier > maximum ? maximum : multiplier;
	}

	/**
	 * Pulls an observed ratio toward a fallback in proportion to how little evidence stands behind
	 * it. One flip barely moves anything; a hundred are believed nearly outright.
	 */
	private static double shrink(double observed, int count, double prior, double fallback)
	{
		double weight = count / (count + prior);
		return clamp(weight * observed + (1 - weight) * fallback);
	}

	private static double clamp(double multiplier)
	{
		return multiplier < MIN_MULTIPLIER ? MIN_MULTIPLIER
			: multiplier > MAX_MULTIPLIER ? MAX_MULTIPLIER : multiplier;
	}
}
