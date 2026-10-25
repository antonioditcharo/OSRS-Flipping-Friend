package com.flippingfriend.model;

import com.flippingfriend.session.FlipRecord;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.inject.Singleton;

/**
 * Learns how wrong the model's own predictions have been, and corrects for it.
 * <p>
 * This is the part a shared, server-side model cannot do well. How fast an offer fills depends on
 * when you play, which worlds you use, and how quickly you actually get back to the Grand Exchange
 * — all of which are properties of <em>you</em>, not of the item. A model averaged over thousands
 * of users has to predict the average player; this one only ever has to predict one.
 * <p>
 * Estimates are shrunk through two levels: an item's own history, backed by the account's overall
 * bias, backed by a neutral prior of 1.0. So a single lucky flip barely moves anything, an item you
 * trade constantly gets a genuinely item-specific correction, and an item you have never traded
 * still benefits from what the account has learned in general. With no history at all every
 * multiplier is exactly 1.0, which makes the calibrated model strictly no worse than the raw one.
 */
@Singleton
public class Calibrator
{
	/** Prior strength, in observations. Roughly "trust the prior as much as three real flips". */
	private static final double ITEM_PRIOR_WEIGHT = 3.0;
	private static final double GLOBAL_PRIOR_WEIGHT = 5.0;
	/**
	 * Simulated evidence is deliberately given less pull than real evidence. There is far more of
	 * it, and without a heavier discount it would drown out the handful of observations that
	 * actually involved a queue, a rival buyer and a real coin pouch.
	 */
	private static final double PAPER_PRIOR_WEIGHT = 25.0;
	/** No amount of history justifies letting a multiplier run away. */
	private static final double MIN_MULTIPLIER = 0.25;
	private static final double MAX_MULTIPLIER = 4.0;
	/** Predictions below this are too small to divide by meaningfully. */
	private static final double MIN_PREDICTION = 1e-6;

	private volatile Map<Integer, Observation> byItem = new HashMap<>();
	private volatile Observation global = new Observation();
	private volatile Map<Integer, Observation> paperByItem = new HashMap<>();
	private volatile Observation paperGlobal = new Observation();

	/** Rebuilds from the journal. Cheap enough to run on login and after every completed flip. */
	public void rebuild(List<FlipRecord> history)
	{
		Map<Integer, Observation> items = new HashMap<>();
		Observation overall = new Observation();

		for (FlipRecord record : history)
		{
			double predictedMinutes = record.getPredictedMinutes();
			double actualMinutes = record.actualMinutes();
			double predictedProfit = record.getPredictedProfit();

			// A flip we never made a prediction for teaches us nothing about prediction error.
			if (predictedMinutes <= MIN_PREDICTION || predictedProfit <= MIN_PREDICTION)
			{
				continue;
			}

			// Raw quantities rather than a per-flip ratio, so these pool. Above 1 on the way out
			// means it filled faster than predicted, which is why the times go in this way round.
			double elapsed = Math.max(actualMinutes, MIN_PREDICTION);
			items.computeIfAbsent(record.getItemId(), id -> new Observation())
				.addFlip(predictedMinutes, elapsed, predictedProfit, record.getProfit());
			overall.addFlip(predictedMinutes, elapsed, predictedProfit, record.getProfit());
		}

		this.byItem = items;
		this.global = overall;
	}

	/**
	 * Folds in evidence from simulated trades.
	 * <p>
	 * Paper trades are kept in their own channel, and only the <em>price</em> half of them is used.
	 * A simulation knows whether the market reached a price, which is real information; it cannot
	 * know whether our offer would have been at the front of the queue when it got there, so its
	 * fill times are systematically optimistic. Letting them into the speed estimate would teach the
	 * model to promise fills it cannot deliver, which is worse than not learning at all.
	 * <p>
	 * They are also weighted below real trades, because a trade that was actually placed is stronger
	 * evidence than one that was only imagined.
	 */
	public void observePaperEvidence(Map<Integer, PaperOutcome> outcomes)
	{
		Map<Integer, Observation> paper = new HashMap<>();
		Observation overall = new Observation();

		for (Map.Entry<Integer, PaperOutcome> entry : outcomes.entrySet())
		{
			PaperOutcome outcome = entry.getValue();
			if (outcome.count <= 0)
			{
				continue;
			}

			// A completion rate stands in for the profit ratio, which is not the type confusion it
			// looks like: a flip that never completed realises no profit at all, so the share that
			// completed is what the realised-over-predicted ratio tends to for a simulation that
			// cannot observe magnitude. It is a lower bound rather than an estimate, which is the
			// right shape for a prior.
			double completionRate = clamp(outcome.completed / (double) outcome.count);
			Observation observation = new Observation();
			// Speed is deliberately left neutral: simulations cannot measure it honestly.
			observation.addRatio(1.0, completionRate, outcome.count);
			paper.put(entry.getKey(), observation);
			overall.addRatio(1.0, completionRate, outcome.count);
		}

		this.paperByItem = paper;
		this.paperGlobal = overall;
	}

	public void reset()
	{
		byItem = new HashMap<>();
		global = new Observation();
		paperByItem = new HashMap<>();
		paperGlobal = new Observation();
	}

	/** Simulated results for one item, as gathered by the shadow trader. */
	public static final class PaperOutcome
	{
		private final int count;
		private final int completed;

		public PaperOutcome(int count, int completed)
		{
			this.count = count;
			this.completed = completed;
		}
	}

	/** How much faster than predicted this item has actually filled. 1.0 means bang on. */
	public double speedMultiplier(int itemId)
	{
		Observation item = byItem.get(itemId);
		double globalEstimate = shrink(global.speedRatio(), global.count, 1.0, GLOBAL_PRIOR_WEIGHT);
		if (item == null)
		{
			return globalEstimate;
		}
		return clamp(shrink(item.speedRatio(), item.count, globalEstimate, ITEM_PRIOR_WEIGHT));
	}

	/**
	 * How much of the predicted profit this item has actually delivered.
	 * <p>
	 * Real trades are shrunk towards the paper estimate rather than straight to the neutral prior,
	 * which is what makes simulated evidence useful: an item with no real flips still benefits from
	 * everything the shadow trader has observed about it.
	 */
	public double profitMultiplier(int itemId)
	{
		double globalEstimate = shrink(global.profitRatio(), global.count, paperPrior(itemId),
			GLOBAL_PRIOR_WEIGHT);
		Observation item = byItem.get(itemId);
		if (item == null)
		{
			return clamp(globalEstimate);
		}
		return clamp(shrink(item.profitRatio(), item.count, globalEstimate, ITEM_PRIOR_WEIGHT));
	}

	/** What the simulations suggest for this item, shrunk towards neutral and towards the global. */
	private double paperPrior(int itemId)
	{
		double globalPaper = shrink(paperGlobal.profitRatio(), paperGlobal.count, 1.0, PAPER_PRIOR_WEIGHT);
		Observation item = paperByItem.get(itemId);
		if (item == null)
		{
			return globalPaper;
		}
		return shrink(item.profitRatio(), item.count, globalPaper, PAPER_PRIOR_WEIGHT);
	}

	public int paperObservationCount(int itemId)
	{
		Observation item = paperByItem.get(itemId);
		return item == null ? 0 : item.count;
	}

	public int totalPaperObservations()
	{
		return paperGlobal.count;
	}

	/**
	 * Combined correction applied to an item's score. Both factors matter because profit per hour
	 * is profit divided by time, and the model can be wrong about either.
	 */
	public double scoreMultiplier(int itemId)
	{
		return clamp(speedMultiplier(itemId) * profitMultiplier(itemId));
	}

	public int observationCount(int itemId)
	{
		Observation item = byItem.get(itemId);
		return item == null ? 0 : item.count;
	}

	public int totalObservations()
	{
		return global.count;
	}

	/** True once there is enough history for the corrections to mean anything. */
	public boolean isTrained()
	{
		return global.count >= 5;
	}

	/**
	 * Pulls an observed ratio towards a prior in proportion to how little evidence stands behind it.
	 * The ratio arrives already pooled, so this is shrinkage of a statistic rather than an average of
	 * per-flip ratios wearing shrinkage as a disguise.
	 */
	private static double shrink(double ratio, int count, double prior, double priorWeight)
	{
		return (ratio * count + prior * priorWeight) / (count + priorWeight);
	}

	private static double clamp(double value)
	{
		if (Double.isNaN(value) || Double.isInfinite(value))
		{
			return 1.0;
		}
		return Math.max(MIN_MULTIPLIER, Math.min(MAX_MULTIPLIER, value));
	}

	private static final class Observation
	{
		/**
		 * Pooled numerators and denominators, not a running total of per-flip ratios.
		 * <p>
		 * A mean of ratios is not the ratio of the means, and the difference is not academic here.
		 * Two flips, one that filled twice as fast as predicted and one twice as slow, give ratios of
		 * 2.0 and 0.5 and average to 1.25 -- so the calibrator concludes fills run 25% ahead of
		 * prediction on a pair that took exactly as long as predicted. Jensen's inequality puts the
		 * bias in the flattering direction every time, and scoreMultiplier multiplies it into the
		 * ranking. The companion's LearnedDurations already pools correctly; this is the same job
		 * done two different ways in one codebase.
		 */
		private double predictedMinutes;
		private double actualMinutes;
		private double predictedProfit;
		private double realisedProfit;
		private int count;

		void addFlip(double predicted, double actual, double predictedGain, double realisedGain)
		{
			predictedMinutes += predicted;
			actualMinutes += actual;
			predictedProfit += predictedGain;
			realisedProfit += realisedGain;
			count++;
		}

		/** A ratio the caller can treat as a rate, already pooled over everything folded in. */
		void addRatio(double speed, double profit, int weight)
		{
			predictedMinutes += speed * weight;
			actualMinutes += weight;
			predictedProfit += weight;
			realisedProfit += profit * weight;
			count += weight;
		}

		double speedRatio()
		{
			return actualMinutes <= 0 ? 1.0 : clamp(predictedMinutes / actualMinutes);
		}

		double profitRatio()
		{
			return predictedProfit <= 0 ? 1.0 : clamp(realisedProfit / predictedProfit);
		}
	}
}
