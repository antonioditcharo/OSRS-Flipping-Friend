package com.flippingfriend.companion;

import com.flippingfriend.core.OfferEvent;
import com.flippingfriend.data.Candle;
import com.flippingfriend.model.FillCurve;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * How much of the flow at our price we actually win, learned from our own fills.
 *
 * <p>{@code captureRate} is the single largest lever on deployed capital. Buy limits allow tens of
 * millions per item; what really caps an order is how much of the item's trading we are willing to
 * claim, and until now that was a constant chosen by risk setting — 0.35 cautious, 0.85 aggressive —
 * with nothing behind the numbers but judgement. Every order size in the system is proportional to
 * it, so if the true figure is half the assumed one, every order is twice as large as the market
 * will fill and every slot is held twice as long as planned.
 *
 * <p><b>Only our own orders can measure it.</b> The wiki feed publishes what traded, never the book:
 * it can say four thousand units crossed at or below our price in the last hour, and can never say
 * how many buyers were queued in front of us for them. The one experiment that answers the question
 * is an offer we placed ourselves, and this account has been running them all along and throwing the
 * answer away.
 *
 * <h2>The measurement</h2>
 *
 * <p>For a settled offer: over the interval it was open, some volume {@code F} traded at or beyond
 * its price, and we took {@code filled} of it. The realised capture is {@code filled / F}. The
 * denominator is computed by {@link FillCurve} rather than by a fresh sum, so "flow at or beyond our
 * price" means exactly what it means inside {@link com.flippingfriend.model.FillModel} — including
 * the dispersion allowance for bars that straddle the price, which a second implementation would
 * have quietly dropped.
 *
 * <p><b>A completed offer is not spoiled by having completed.</b> The first instinct is that
 * {@code filled} is capped by the order size rather than by the queue, so completions must
 * understate capture and only partial fills can measure it. That is wrong, and it matters, because
 * discarding completions would throw away most of the sample: the order size caps the numerator, but
 * completing also <em>ends the interval</em>, so it caps the denominator by the same act. Under the
 * Poisson arrivals the fill model already assumes, the time to the {@code Q}th arrival and a count of
 * {@code q} arrivals in a fixed time give the same rate estimate — {@code filled / F} either way.
 * What would spoil it is measuring flow over the whole time the offer existed rather than the time it
 * was hungry, and {@link OfferEvent#secondsOpen()} ends at the settling event.
 *
 * <p>Offers that filled nothing are kept. An hour spent watching flow pass and winning none of it is
 * the most informative observation there is, and dropping the zeroes would bias the estimate up by
 * exactly the amount it is trying to measure.
 *
 * <h2>What it cannot see</h2>
 *
 * <p>Cancellations are not a random sample of our orders — a player cancels the ones that are not
 * filling. That does not bias the estimator for the conditions it observed, but it does mean the
 * conditions observed lean slow. The effect is bounded by how often cancelling happens and is
 * recorded here rather than corrected, because correcting it would require knowing why each offer was
 * cancelled and the event does not carry it.
 *
 * <p>Polling resolution also inflates the denominator slightly: an offer is seen to settle at the
 * next poll after it really did, so a little flow that arrived after we were full is counted against
 * us. That biases capture down, by roughly the poll interval over the offer's duration, which is why
 * {@link #MIN_OPEN_SECONDS} drops the very short ones instead of letting them dominate.
 *
 * <h2>Shrinkage</h2>
 *
 * <p>The same two-level discipline as {@link LearnedDurations}, for the same reason. An item's own
 * rate is shrunk toward the pooled rate of every <em>other</em> item, and the pooled rate is shrunk
 * toward whatever the risk appetite asked for. With no evidence at all the answer is exactly the
 * appetite's own number, so this is inert until it has grounds — and the prior is expressed in units
 * of flow rather than in offers, because one offer against a torrent and one against a trickle are
 * not equal evidence.
 */
final class CaptureRates
{
	/**
	 * Flow that must accumulate before an item's own evidence outweighs the prior.
	 * <p>
	 * In units, not offers. A typical mid-volume flip sees a few hundred units cross at its price
	 * while it sits, so this is a handful of offers on a busy item and many more on a quiet one —
	 * which is the correct shape: a rate measured against thin flow is a thin measurement whatever
	 * the offer count says.
	 */
	private static final double PRIOR_FLOW = 500.0;

	/**
	 * Shortest offer worth learning from.
	 * <p>
	 * Below this the polling slop is a large fraction of the interval and the flow estimate leans on
	 * a single prorated bar. Five minutes is one bar of the archive's finest resolution.
	 */
	static final long MIN_OPEN_SECONDS = 300;

	/** Least flow worth a row. Under this the ratio is one lucky trade away from anything. */
	private static final double MIN_FLOW = 5.0;

	/** A capture rate is a share of a flow. Above one means the flow was measured short. */
	private static final double MAX_RATE = 1.0;

	/** Never claim we win nothing, or an item that had one bad hour becomes permanently untradeable. */
	private static final double MIN_RATE = 0.02;

	private final Map<Integer, Totals> perItem = new HashMap<>();
	private double totalFilled;
	private double totalFlow;
	private int observations;
	private int overflows;

	/** Per-item filled and available flow, in units. */
	static final class Totals
	{
		double filled;
		double flow;
		int observations;

		Totals()
		{
		}

		Totals(double filled, double flow, int observations)
		{
			this.filled = filled;
			this.flow = flow;
			this.observations = observations;
		}
	}

	/** Restores accumulated evidence from the store, replacing anything held in memory. */
	synchronized void restore(Map<Integer, Totals> stored)
	{
		perItem.clear();
		totalFilled = 0;
		totalFlow = 0;
		observations = 0;
		if (stored == null)
		{
			return;
		}
		for (Map.Entry<Integer, Totals> entry : stored.entrySet())
		{
			Totals totals = entry.getValue();
			if (totals == null || totals.flow <= 0)
			{
				continue;
			}
			perItem.put(entry.getKey(), new Totals(totals.filled, totals.flow, totals.observations));
			totalFilled += totals.filled;
			totalFlow += totals.flow;
			observations += totals.observations;
		}
	}

	/**
	 * Folds one settled offer into the evidence.
	 *
	 * @param bars the archive's five-minute bars overlapping the offer's open interval, oldest first
	 * @return the flow the offer was measured against, or 0 when it taught us nothing
	 */
	synchronized double observe(OfferEvent event, List<Candle> bars)
	{
		if (event == null || event.getItemId() <= 0 || event.getPrice() <= 0)
		{
			return 0;
		}
		long open = event.getFirstSeenAt();
		long closed = event.getObservedAt();
		double filled = Math.max(0, event.getFilledQuantity());
		int ordered = Math.max(0, event.getTotalQuantity());
		boolean tookEverythingAskedFor = ordered > 0 && filled >= ordered;

		// The short-offer rule exists because a brief offer's FLOW estimate leans on a prorated bar
		// and the polling slop is a large share of the interval. That is a reason to distrust the
		// denominator -- and an offer that filled completely does not need one, because it captured
		// everything it asked for however much was flowing. Excluding those was not conservative: a
		// fast complete fill is the strongest capture evidence there is, and dropping it while keeping
		// the offers that sat for an hour and filled nothing is a filter that admits only failures.
		if (open <= 0 || (closed - open < MIN_OPEN_SECONDS && !tookEverythingAskedFor))
		{
			return 0;
		}

		double flow = flowAtOrBeyond(bars, open, closed, event.isBuying(), event.getPrice());
		if (flow < MIN_FLOW && !tookEverythingAskedFor)
		{
			return 0;
		}

		// The denominator is the flow, EXCEPT when our own order size ended the measurement.
		//
		// An offer that filled completely is right-censored: we took everything we asked for, so all
		// it establishes is a LOWER bound on what we could have taken. Scoring it as filled/flow
		// reads "we only won a tenth of what passed" from an order that was never trying for more
		// than a tenth, and capture scales the next order. Small order, low measured capture, smaller
		// order. That is circular, and it ran to the end on this account.
		//
		// When the market limited us -- filled short of what we ordered -- the flow is exactly right
		// and nothing changes: that is a genuine share of what passed us.
		// When we took everything we asked for, the flow is not needed and may not even exist: a
		// sixty-second offer overlaps a single bar, and a curve cannot be built from one point, so
		// flowAtOrBeyond returns zero. That is precisely the case MIN_OPEN_SECONDS was refusing, and
		// precisely the case that needs no denominator -- we know what we took and we know we wanted
		// no more. Weighted by the size of the order, so filling ten thousand units counts for more
		// than filling ten.
		double takeable;
		if (tookEverythingAskedFor)
		{
			takeable = flow >= MIN_FLOW ? Math.min(flow, ordered) : ordered;
		}
		else
		{
			takeable = flow;
		}
		if (takeable <= 0)
		{
			return 0;
		}

		if (filled > takeable)
		{
			// Filling more than the archive saw crossing means the archive was short -- missing bars,
			// or a gap in the companion's uptime -- not that the fill did not happen. This used to be
			// discarded, which threw away the two best observations on this account for having done
			// too well. Counted at the ceiling instead, and still reported.
			overflows++;
			filled = takeable;
		}

		Totals totals = perItem.computeIfAbsent(event.getItemId(), id -> new Totals());
		totals.filled += filled;
		totals.flow += takeable;
		totals.observations++;
		totalFilled += filled;
		totalFlow += takeable;
		observations++;
		return takeable;
	}

	/**
	 * Volume that traded at or beyond {@code price} while the offer was open.
	 *
	 * <p>Bars at the edges are prorated by how much of them the offer actually overlapped, because a
	 * five-minute bucket counted whole at each end can be most of a short offer's denominator. The
	 * price side of the question is left entirely to {@link FillCurve}, so this agrees with the fill
	 * model by construction instead of by inspection.
	 */
	static double flowAtOrBeyond(List<Candle> bars, long open, long closed, boolean buying, int price)
	{
		if (bars == null || bars.isEmpty() || closed <= open)
		{
			return 0;
		}
		long bucket = bucketSeconds(bars);
		List<Candle> weighted = new ArrayList<>(bars.size());
		for (Candle bar : bars)
		{
			long start = bar.getTimestamp();
			long end = start + bucket;
			long overlap = Math.min(end, closed) - Math.max(start, open);
			if (overlap <= 0)
			{
				continue;
			}
			double share = Math.min(1.0, overlap / (double) bucket);
			weighted.add(new Candle(start, bar.getAvgHighPrice(), bar.getAvgLowPrice(),
				(int) Math.round(bar.getHighPriceVolume() * share),
				(int) Math.round(bar.getLowPriceVolume() * share)));
		}
		if (weighted.isEmpty())
		{
			return 0;
		}

		FillCurve curve = FillCurve.from(weighted);
		return buying ? curve.buyVolumeAtOrBelow(price) : curve.sellVolumeAtOrAbove(price);
	}

	/** The bars' own spacing, so this does not assume a resolution the archive might change. */
	private static long bucketSeconds(List<Candle> bars)
	{
		if (bars.size() < 2)
		{
			return 300;
		}
		long gap = bars.get(1).getTimestamp() - bars.get(0).getTimestamp();
		return gap > 0 ? gap : 300;
	}

	/**
	 * The capture rate to use for this item, given what the risk appetite asked for.
	 *
	 * @param fallback the appetite's configured share, which is also the prior everything shrinks
	 *                 toward — so with no evidence this returns it exactly
	 */
	synchronized double rateFor(int itemId, double fallback)
	{
		double prior = clamp(fallback);
		Totals totals = perItem.get(itemId);
		if (totals == null || totals.flow <= 0)
		{
			return pooled(totalFilled, totalFlow, prior);
		}
		// Leave-one-out: this item's own totals come out before it is shrunk toward the rest. An item
		// that dominates the sample would otherwise be shrunk toward itself, which is no shrinkage.
		double priorForItem =
			pooled(totalFilled - totals.filled, totalFlow - totals.flow, prior);
		return clamp((totals.filled + PRIOR_FLOW * priorForItem) / (totals.flow + PRIOR_FLOW));
	}

	/** The pooled rate across every item, for reporting and as the fallback for unseen items. */
	synchronized double pooledRate(double fallback)
	{
		return pooled(totalFilled, totalFlow, clamp(fallback));
	}

	synchronized int itemsLearned()
	{
		return perItem.size();
	}

	synchronized int observationCount()
	{
		return observations;
	}

	/** Offers whose fills exceeded the flow the archive could account for. A gap-in-coverage signal. */
	synchronized int overflowCount()
	{
		return overflows;
	}

	synchronized Map<Integer, Totals> totals()
	{
		Map<Integer, Totals> copy = new HashMap<>();
		for (Map.Entry<Integer, Totals> entry : perItem.entrySet())
		{
			Totals totals = entry.getValue();
			copy.put(entry.getKey(), new Totals(totals.filled, totals.flow, totals.observations));
		}
		return Collections.unmodifiableMap(copy);
	}

	/** One line for the health report: what was learned, from how much, and whether it is trusted. */
	synchronized String summary(double fallback)
	{
		if (observations == 0)
		{
			return String.format("capture: no fills measured yet, using %.2f from the risk appetite",
				fallback);
		}
		return String.format(
			"capture: %.3f pooled against %.2f assumed, from %d offers over %.0f units of flow "
				+ "across %d items%s",
			pooledRate(fallback), fallback, observations, totalFlow, perItem.size(),
			overflows > 0 ? " (" + overflows + " dropped for missing history)" : "");
	}

	private static double pooled(double filled, double flow, double prior)
	{
		double safeFilled = Math.max(0, filled);
		double safeFlow = Math.max(0, flow);
		return clamp((safeFilled + PRIOR_FLOW * prior) / (safeFlow + PRIOR_FLOW));
	}

	private static double clamp(double rate)
	{
		if (!Double.isFinite(rate) || rate <= 0)
		{
			return MIN_RATE;
		}
		return Math.max(MIN_RATE, Math.min(MAX_RATE, rate));
	}
}
