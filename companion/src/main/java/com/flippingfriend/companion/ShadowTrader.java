package com.flippingfriend.companion;

import com.flippingfriend.data.Candle;
import com.flippingfriend.model.TaxCalculator;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Paper-trades the candidates the engine rejected, so a veto's cost is a number rather than an
 * opinion.
 *
 * <p>This is the counterfactual channel, and it is the one thing real trading cannot give you. A
 * session produces perhaps a dozen labelled outcomes, and only ever for trades that were taken — so
 * a filter that rejects profitable trades is wrong forever and invisibly, because the evidence that
 * would show it up is exactly the evidence never collected. Scoring every rejected candidate against
 * what the market subsequently did produces hundreds of observations an hour and, critically,
 * outcomes for the road not taken.
 *
 * <p>{@link com.flippingfriend.model.VetoThresholds} already documents a table of this shape —
 * "sell price outside normal range +7.15M, too strict; stale side of the book −16.31M, earning its
 * keep, by a mile" — and says in its own javadoc that a background learner produced it by
 * paper-trading rejected items. That learner is gone. This is the capability, not the class.
 *
 * <h2>What a paper trade can and cannot tell you</h2>
 *
 * <p>Resolution asks only whether the market <em>reached</em> a price, using the same convention as
 * {@link PortfolioSimulator}: a buy at {@code p} fills when some later bar's low (instant-sell) price
 * is at or below {@code p}, and a sell at {@code s} fills when a bar's high (instant-buy) price is at
 * or above {@code s}.
 *
 * <p>That is a statement about <b>price behaviour</b>, and nothing more. It cannot know whether your
 * order would have won its place in the queue against everyone else resting at that price — the feed
 * publishes what traded, never the book. So these outcomes must never feed
 * {@link LearnedDurations} or the queue-speed side of calibration, which is the two-channel rule the
 * existing {@code Calibrator} already enforces: paper trades inform price behaviour, only real fills
 * inform queue speed. Treating a paper fill as evidence about timing would quietly poison the one
 * measurement that has to come from reality.
 *
 * <h2>Bounded on purpose</h2>
 *
 * <p>Capped at {@value #MAX_OPEN} open positions and {@value #MAX_RESOLVED} resolved ones, oldest
 * evicted first. A previous incarnation of this project accumulated a 2.5 GB ingestion table that
 * nothing read and then deleted it; the lesson drawn at the time was to define the reader before the
 * writer. The reader here is {@link #report()}, and it is the only reason any of this is retained.
 */
final class ShadowTrader
{
	/** Positions awaiting resolution. A planning pass adds tens; they clear within the horizon. */
	private static final int MAX_OPEN = 20_000;

	/** Resolved outcomes kept for the report. Enough for the ~1,500-trade samples the table quotes. */
	private static final int MAX_RESOLVED = 20_000;

	/**
	 * What a stranded position costs to unwind, as a fraction of what was paid. Matches the
	 * assumption VetoThresholds states for the numbers it publishes, so a re-derived table is
	 * comparable with the one it replaces.
	 */
	private static final double UNWIND_COST = 0.02;

	/** Sentinel for a candidate the engine would have taken, so accepted and rejected share one path. */
	static final String ACCEPTED = "(accepted)";

	private final TaxCalculator tax;
	private final Deque<Position> open = new ArrayDeque<>();
	private final Deque<Resolved> resolved = new ArrayDeque<>();

	ShadowTrader(TaxCalculator tax)
	{
		this.tax = tax;
	}

	/**
	 * Opens a notional position.
	 *
	 * @param vetoReason why the engine rejected it, or {@link #ACCEPTED} if it did not
	 */
	synchronized void open(int itemId, String itemName, String vetoReason, int buyPrice, int sellPrice,
		int quantity, long atEpochSeconds, double horizonHours)
	{
		open(itemId, itemName, vetoReason, buyPrice, sellPrice, quantity, atEpochSeconds,
			horizonHours, null);
	}

	/**
	 * Opens a notional position that also carries the feature vector that produced it.
	 *
	 * <p>This is what turns the paper-trading channel into a training set. Every resolved position
	 * becomes a labelled row — the features as they stood at the decision, and what the market
	 * subsequently did — and because vetoed candidates are recorded alongside accepted ones, the
	 * labels are not censored by the policy that generated them. Real fills give a dozen rows a
	 * session and only for trades that were taken; this gives hundreds an hour including the ones
	 * that were refused, which is the difference between a model that can learn a veto is wrong and
	 * one that structurally cannot.
	 *
	 * @param features {@link com.flippingfriend.learning.FlipFeatures} values, or null when the item
	 *                 was turned away before there was enough history to build them
	 */
	synchronized void open(int itemId, String itemName, String vetoReason, int buyPrice, int sellPrice,
		int quantity, long atEpochSeconds, double horizonHours, double[] features)
	{
		if (itemId <= 0 || buyPrice <= 0 || sellPrice <= 0 || quantity <= 0 || horizonHours <= 0)
		{
			return;
		}
		while (open.size() >= MAX_OPEN)
		{
			open.pollFirst();
		}
		open.addLast(new Position(itemId, itemName, vetoReason == null ? ACCEPTED : vetoReason,
			buyPrice, sellPrice, quantity, atEpochSeconds, horizonHours, features));
	}

	/**
	 * Resolves every position whose horizon has elapsed, against the history it can now see.
	 *
	 * @param history where to read bars from; the same interface live planning uses
	 * @return how many were resolved
	 */
	synchronized int resolve(SeriesSource history, String timestep, long nowEpochSeconds)
	{
		if (history == null || open.isEmpty())
		{
			return 0;
		}
		int count = 0;
		for (int i = open.size(); i > 0; i--)
		{
			Position position = open.pollFirst();
			long deadline = position.openedAt + (long) (position.horizonHours * 3600);
			if (nowEpochSeconds < deadline)
			{
				// Not yet due. Put it back and keep the queue in age order.
				open.addLast(position);
				continue;
			}
			List<Candle> bars = history.series(position.itemId, timestep);
			record(position, outcomeOf(position, bars, deadline));
			count++;
		}
		return count;
	}

	/**
	 * Replays the bars between opening and the deadline. Buy first, then sell strictly after the bar
	 * that filled the buy — a position cannot be sold before it is held.
	 */
	private Outcome outcomeOf(Position position, List<Candle> bars, long deadline)
	{
		long boughtAt = -1;
		for (Candle bar : bars)
		{
			long at = bar.getTimestamp();
			if (at < position.openedAt || at > deadline)
			{
				continue;
			}
			if (boughtAt < 0)
			{
				Integer low = bar.getAvgLowPrice();
				if (low != null && low > 0 && low <= position.buyPrice)
				{
					boughtAt = at;
				}
				continue;
			}
			Integer high = bar.getAvgHighPrice();
			if (at > boughtAt && high != null && high >= position.sellPrice)
			{
				return Outcome.COMPLETED;
			}
		}
		return boughtAt < 0 ? Outcome.NEVER_BOUGHT : Outcome.STRANDED;
	}

	private void record(Position position, Outcome outcome)
	{
		long cost = (long) position.buyPrice * position.quantity;
		long profit;
		switch (outcome)
		{
			case COMPLETED:
				profit = tax.netProceeds(position.itemId, position.sellPrice, position.quantity) - cost;
				break;
			case STRANDED:
				// Held at the horizon. Charged the same unwind assumption VetoThresholds quotes.
				profit = -(long) (cost * UNWIND_COST);
				break;
			default:
				// Never bought: no capital was committed and no loss taken. The cost of a veto here is
				// zero, not negative — which is the point. A veto that only ever blocks trades that
				// would not have filled is free, and the table must not credit it with a saving.
				profit = 0;
				break;
		}
		while (resolved.size() >= MAX_RESOLVED)
		{
			resolved.pollFirst();
		}
		resolved.addLast(new Resolved(position.vetoReason, outcome, profit, cost,
			position.itemId, position.features));
	}

	/**
	 * What each veto has cost or earned, in gp, over the trades it rejected.
	 *
	 * <p>Sign convention follows the table in {@link com.flippingfriend.model.VetoThresholds}: a
	 * <b>positive</b> figure is forgone profit, meaning the veto is too strict, and a <b>negative</b>
	 * figure is loss avoided, meaning it is earning its keep.
	 *
	 * @param affordable ignore rejected trades that would have cost more than this, so a bankroll
	 *                   that could never have taken the trade does not get credited for skipping it
	 */
	synchronized List<VetoCost> report(long affordable)
	{
		Map<String, VetoCost> byReason = new LinkedHashMap<>();
		for (Resolved row : resolved)
		{
			if (affordable > 0 && row.cost > affordable)
			{
				continue;
			}
			byReason.computeIfAbsent(row.vetoReason, VetoCost::new).add(row);
		}
		List<VetoCost> costs = new ArrayList<>(byReason.values());
		// Most-wrong first: the veto forgoing the most profit is the one worth loosening next.
		costs.sort((left, right) -> Double.compare(right.netGp, left.netGp));
		return costs;
	}

	synchronized int openCount()
	{
		return open.size();
	}

	synchronized int resolvedCount()
	{
		return resolved.size();
	}

	/** One line for {@code /v1/health}, so the channel cannot run for weeks unnoticed. */
	synchronized String summary()
	{
		if (resolved.isEmpty())
		{
			return String.format("Shadow: %d open, none resolved yet.", open.size());
		}
		List<VetoCost> costs = report(0);
		VetoCost worst = costs.get(0);
		return String.format("Shadow: %d open, %d resolved; costliest veto \"%s\" at %+.2fM over %d.",
			open.size(), resolved.size(), worst.vetoReason, worst.netGp / 1e6, worst.trades);
	}

	/**
	 * Open and resolved positions, for the model registry.
	 *
	 * <p>The resolved rows are the point. They are the accumulated evidence behind
	 * {@link com.flippingfriend.model.VetoThresholds}, and the table it publishes came from roughly
	 * 1,500 of them — a count no companion reaches if the history is discarded every restart. The
	 * open positions are worth carrying too, since a position abandoned mid-horizon is a
	 * counterfactual that never resolves and therefore never counts.
	 */
	static final class Snapshot
	{
		int version = SNAPSHOT_VERSION;
		List<Position> open;
		List<Resolved> resolved;
	}

	static final int SNAPSHOT_VERSION = 1;

	synchronized Snapshot snapshot()
	{
		Snapshot state = new Snapshot();
		state.open = new ArrayList<>(open);
		state.resolved = new ArrayList<>(resolved);
		return state;
	}

	/**
	 * Restores a snapshot, dropping malformed rows rather than the whole payload — unlike the
	 * calibration curve, one bad row here skews a total slightly instead of corrupting a mapping, so
	 * salvaging the rest is worth more than refusing everything.
	 *
	 * @return how many resolved outcomes came back
	 */
	synchronized int restore(Snapshot state)
	{
		if (state == null || state.version != SNAPSHOT_VERSION)
		{
			return 0;
		}
		open.clear();
		resolved.clear();
		if (state.open != null)
		{
			for (Position position : state.open)
			{
				if (position != null && position.itemId > 0 && position.vetoReason != null)
				{
					open.addLast(position);
				}
			}
		}
		if (state.resolved != null)
		{
			for (Resolved row : state.resolved)
			{
				if (row != null && row.vetoReason != null && row.outcome != null)
				{
					resolved.addLast(row);
				}
			}
		}
		return resolved.size();
	}

	/**
	 * The labelled training set: every resolved position that carried a feature vector.
	 *
	 * <p>The label is whether the round trip completed. That is the quantity the fill model predicts
	 * and the optimiser weights its whole objective by, so it is the one worth learning — not price
	 * direction, which is what the existing LSTM predicts and what nothing downstream consumes.
	 *
	 * <p>Includes rejected candidates as well as accepted ones. A model trained only on trades the
	 * engine chose to take learns the engine's existing opinion back; the counterfactuals are what let
	 * it learn that an opinion was wrong.
	 *
	 * @param includeVetoed false to train only on trades the engine would have taken, for comparison
	 */
	synchronized TrainingSet trainingSet(boolean includeVetoed)
	{
		List<double[]> rows = new ArrayList<>();
		List<Integer> labels = new ArrayList<>();
		for (Resolved row : resolved)
		{
			if (row.features == null)
			{
				continue;
			}
			if (!includeVetoed && !ACCEPTED.equals(row.vetoReason))
			{
				continue;
			}
			rows.add(row.features);
			labels.add(row.outcome == Outcome.COMPLETED ? 1 : 0);
		}
		return new TrainingSet(rows, labels);
	}

	/** A feature matrix and its labels, in the shape {@code GradientBoostedTrees.train} expects. */
	static final class TrainingSet
	{
		private final double[][] features;
		private final int[] labels;

		private TrainingSet(List<double[]> rows, List<Integer> labelValues)
		{
			this.features = rows.toArray(new double[0][]);
			this.labels = new int[labelValues.size()];
			for (int i = 0; i < labelValues.size(); i++)
			{
				this.labels[i] = labelValues.get(i);
			}
		}

		double[][] features()
		{
			return features;
		}

		int[] labels()
		{
			return labels;
		}

		int size()
		{
			return labels.length;
		}

		/** Share of rows that completed. A set that is nearly all one class cannot train anything. */
		double positiveRate()
		{
			if (labels.length == 0)
			{
				return 0;
			}
			int positive = 0;
			for (int label : labels)
			{
				positive += label;
			}
			return (double) positive / labels.length;
		}
	}

	enum Outcome
	{
		COMPLETED,
		STRANDED,
		NEVER_BOUGHT
	}

	/** Net gp attributable to one veto reason. */
	static final class VetoCost
	{
		private final String vetoReason;
		private double netGp;
		private int trades;
		private int completed;
		private int stranded;
		private int neverBought;

		VetoCost(String vetoReason)
		{
			this.vetoReason = vetoReason;
		}

		private void add(Resolved row)
		{
			netGp += row.profit;
			trades++;
			switch (row.outcome)
			{
				case COMPLETED: completed++; break;
				case STRANDED: stranded++; break;
				default: neverBought++; break;
			}
		}

		String getVetoReason() { return vetoReason; }
		double getNetGp() { return netGp; }
		int getTrades() { return trades; }
		int getCompleted() { return completed; }
		int getStranded() { return stranded; }
		int getNeverBought() { return neverBought; }

		/** True when blocking these trades forgoes more than it saves. */
		boolean isTooStrict() { return netGp > 0; }
	}

	private static final class Position
	{
		private final int itemId;
		private final String itemName;
		private final String vetoReason;
		private final int buyPrice;
		private final int sellPrice;
		private final int quantity;
		private final long openedAt;
		private final double horizonHours;
		/** The feature vector as it stood at the decision, or null when none could be built. */
		private final double[] features;

		private Position(int itemId, String itemName, String vetoReason, int buyPrice, int sellPrice,
			int quantity, long openedAt, double horizonHours, double[] features)
		{
			this.features = features;
			this.itemId = itemId;
			this.itemName = itemName;
			this.vetoReason = vetoReason;
			this.buyPrice = buyPrice;
			this.sellPrice = sellPrice;
			this.quantity = quantity;
			this.openedAt = openedAt;
			this.horizonHours = horizonHours;
		}
	}

	private static final class Resolved
	{
		private final String vetoReason;
		private final Outcome outcome;
		private final long profit;
		private final long cost;
		private final int itemId;
		private final double[] features;

		private Resolved(String vetoReason, Outcome outcome, long profit, long cost, int itemId,
			double[] features)
		{
			this.vetoReason = vetoReason;
			this.outcome = outcome;
			this.profit = profit;
			this.cost = cost;
			this.itemId = itemId;
			this.features = features;
		}
	}
}
