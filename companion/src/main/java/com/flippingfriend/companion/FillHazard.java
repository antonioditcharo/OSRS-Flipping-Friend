package com.flippingfriend.companion;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * How the chance of an offer filling changes the longer it sits there.
 *
 * <p>{@link com.flippingfriend.model.FillModel} answers "what are the odds this fills within four
 * hours" from public price history, and answers it well. It cannot answer the question a resting
 * offer actually poses, which is: <b>it has been twenty minutes and nothing has happened — what now?</b>
 *
 * <p>Under the Poisson arrivals the analytical model assumes, that question has a trivial answer:
 * nothing. A Poisson process is memoryless, so twenty minutes of silence tells you exactly as much
 * about the next twenty as no information at all would. That is a convenient assumption and it is
 * not how a queue behaves. An offer that has not filled has been telling you something the whole
 * time — that it is behind other people's orders, or priced where the market is not coming, or on an
 * item that has gone quiet — and none of that is in the price history, because it is about
 * <em>our</em> order rather than about the market.
 *
 * <p>So the hazard is measured instead of assumed: for each slice of elapsed time, out of the offers
 * that were still open when they reached it, what fraction filled during it. If that fraction is
 * flat, the memoryless assumption holds and this class earns nothing. If it declines, waiting is
 * worth less than the analytical model believes and the repricing decision has been wrong in a
 * direction nobody could see.
 *
 * <h2>Censoring, and why the existing statistic could not do this</h2>
 *
 * <p>{@link ExecutionRecorder} deliberately discards the duration of a cancelled offer, and its
 * reasoning is right for what it is computing: "an offer cancelled after ten minutes did not take
 * ten minutes to fill; it never filled, and averaging it in as though it had would drag the mean
 * towards whatever the player's patience happens to be."
 *
 * <p>But a cancelled offer is not nothing. It is the observation "this one had not filled after ten
 * minutes", which is exactly what a hazard needs and exactly what a mean cannot use. Every cancelled
 * offer contributes to the denominator of every bucket it survived and to the numerator of none.
 * That is right-censoring, and handling it is the whole reason this is a life table rather than an
 * average — <b>a third of the sample was being thrown away because the statistic being computed had
 * no way to hold it.</b>
 *
 * <h2>What it still cannot see</h2>
 *
 * <p>Cancellations are not a random sample: a player cancels the offers that are not filling, so the
 * censoring is informative and the measured hazard is, if anything, optimistic about long waits. The
 * same limitation {@link CaptureRates} carries, for the same reason, and it needs the same fix —
 * recording why an offer was cancelled.
 */
final class FillHazard
{
	/**
	 * Elapsed-time slices, in minutes, with the last one open-ended.
	 * <p>
	 * Geometric rather than even. The interesting behaviour is all in the first half hour — an offer
	 * either gets taken quickly or settles into a wait — and even five-minute buckets would spend
	 * most of their resolution on the flat part while blurring the part that decides a repricing.
	 */
	static final int[] BUCKET_MINUTES = {0, 2, 5, 10, 20, 40, 80, 160};

	/**
	 * Nominal width of the last, open-ended slice, in minutes.
	 * <p>
	 * It has to have one, because a hazard is a rate and a rate needs a denominator. Doubling the
	 * one before it continues the pattern and keeps the tail from dominating: an offer still open at
	 * three hours is being priced on a slice, not on eternity.
	 */
	private static final double LAST_BUCKET_WIDTH = 160;

	/**
	 * Offers that must have reached a bucket before its own rate outweighs the pooled one.
	 * <p>
	 * The same shrinkage {@link LearnedDurations} uses and for the same reason, applied per bucket:
	 * the late buckets are always the thinnest, and they are also the ones a hold decision leans on
	 * hardest.
	 */
	private static final int PRIOR_STRENGTH = 20;

	/** Total observations below which nothing is claimed at all. */
	private static final int MIN_OBSERVATIONS = 100;

	/** A hazard of exactly one would make survival zero and every later bucket meaningless. */
	private static final double MAX_HAZARD = 0.95;

	/** Per item and side, then per bucket. */
	private final Map<Long, Counts[]> perItem = new HashMap<>();
	private final Counts[] pooled = newBuckets();
	private int observations;

	/** At-risk and filled counts for one slice of elapsed time. */
	static final class Counts
	{
		int atRisk;
		int filled;

		Counts()
		{
		}

		Counts(int atRisk, int filled)
		{
			this.atRisk = atRisk;
			this.filled = filled;
		}
	}

	private static Counts[] newBuckets()
	{
		Counts[] buckets = new Counts[BUCKET_MINUTES.length];
		for (int i = 0; i < buckets.length; i++)
		{
			buckets[i] = new Counts();
		}
		return buckets;
	}

	/** How long a slice lasts, in minutes. Unequal on purpose, which is why rates matter below. */
	static double widthOf(int bucket)
	{
		if (bucket < 0 || bucket >= BUCKET_MINUTES.length)
		{
			return 0;
		}
		return bucket == BUCKET_MINUTES.length - 1
			? LAST_BUCKET_WIDTH
			: BUCKET_MINUTES[bucket + 1] - BUCKET_MINUTES[bucket];
	}

	/** Which slice a duration falls in. */
	static int bucketOf(double minutes)
	{
		int bucket = 0;
		for (int i = 1; i < BUCKET_MINUTES.length; i++)
		{
			if (minutes >= BUCKET_MINUTES[i])
			{
				bucket = i;
			}
		}
		return bucket;
	}

	private static long key(int itemId, boolean buying)
	{
		return ((long) itemId << 1) | (buying ? 1L : 0L);
	}

	/**
	 * Folds one settled offer into the life table.
	 *
	 * <p>The offer was at risk in every bucket it survived into, and had its event — if it had one —
	 * in the last of them. A cancelled offer contributes denominators and no numerator, which is the
	 * whole of what censoring means here.
	 *
	 * @param completed true when the offer filled, false when it was cancelled or expired unfilled
	 */
	synchronized void observe(int itemId, boolean buying, double minutesOpen, boolean completed)
	{
		if (itemId <= 0 || minutesOpen < 0)
		{
			return;
		}
		int reached = bucketOf(minutesOpen);
		Counts[] item = perItem.computeIfAbsent(key(itemId, buying), id -> newBuckets());

		for (int bucket = 0; bucket <= reached; bucket++)
		{
			item[bucket].atRisk++;
			pooled[bucket].atRisk++;
		}
		if (completed)
		{
			item[reached].filled++;
			pooled[reached].filled++;
		}
		observations++;
	}

	/** Restores counts from the store, replacing anything held in memory. */
	synchronized void restore(Map<Long, Counts[]> stored, int totalObservations)
	{
		perItem.clear();
		for (int i = 0; i < pooled.length; i++)
		{
			pooled[i] = new Counts();
		}
		observations = 0;
		if (stored == null)
		{
			return;
		}
		for (Map.Entry<Long, Counts[]> entry : stored.entrySet())
		{
			Counts[] copy = newBuckets();
			for (int i = 0; i < copy.length && i < entry.getValue().length; i++)
			{
				Counts source = entry.getValue()[i];
				copy[i] = new Counts(source.atRisk, source.filled);
				pooled[i].atRisk += source.atRisk;
				pooled[i].filled += source.filled;
			}
			perItem.put(entry.getKey(), copy);
		}
		observations = totalObservations;
	}

	synchronized Map<Long, Counts[]> counts()
	{
		return Collections.unmodifiableMap(new HashMap<>(perItem));
	}

	synchronized int observationCount()
	{
		return observations;
	}

	/** True once there is enough evidence for anything here to be worth consulting. */
	synchronized boolean isUsable()
	{
		return observations >= MIN_OBSERVATIONS;
	}

	/**
	 * The chance an offer fills during one slice, given it was still open when it reached it.
	 *
	 * <p>Shrunk toward the pooled hazard, leave-one-out, so an item that dominates the sample is not
	 * shrunk toward itself.
	 */
	synchronized double hazard(int itemId, boolean buying, int bucket)
	{
		if (bucket < 0 || bucket >= pooled.length)
		{
			return 0;
		}
		Counts overall = pooled[bucket];
		Counts[] item = perItem.get(key(itemId, buying));
		Counts own = item == null ? new Counts() : item[bucket];

		int priorAtRisk = overall.atRisk - own.atRisk;
		int priorFilled = overall.filled - own.filled;

		if (own.atRisk <= 0)
		{
			return priorAtRisk > 0
				? Math.min(MAX_HAZARD, (double) priorFilled / priorAtRisk)
				: 0;
		}
		double observed = (double) own.filled / own.atRisk;
		if (priorAtRisk <= 0)
		{
			// Nothing to shrink toward. Leave-one-out has no observations left -- one item in the
			// sample, or the only item that ever reached this slice -- and a prior computed from an
			// empty set is not a weak prior, it is an arbitrary number.
			//
			// Treating it as zero, which is what falling through to the formula did, shrinks the
			// hazard toward "this never fills" on exactly the late slices where the sample is
			// thinnest and where a hold decision leans hardest. It showed up as the wait penalty
			// biting on a synthetic process with a perfectly constant rate: not because the hazard
			// declined, but because the last slice had been quietly pulled to the floor.
			return Math.min(MAX_HAZARD, observed);
		}
		double prior = (double) priorFilled / priorAtRisk;
		double weight = (double) own.atRisk / (own.atRisk + PRIOR_STRENGTH);
		return Math.min(MAX_HAZARD, weight * observed + (1 - weight) * prior);
	}

	/** The pooled hazard for a slice, across every item. The shape of the curve as a whole. */
	synchronized double pooledHazard(int bucket)
	{
		if (bucket < 0 || bucket >= pooled.length || pooled[bucket].atRisk <= 0)
		{
			return 0;
		}
		return Math.min(MAX_HAZARD, (double) pooled[bucket].filled / pooled[bucket].atRisk);
	}

	/** Offers observed to reach a slice, for judging how much the number behind it is worth. */
	synchronized int atRisk(int bucket)
	{
		return bucket < 0 || bucket >= pooled.length ? 0 : pooled[bucket].atRisk;
	}

	/**
	 * The hazard as a rate per minute rather than a chance per slice.
	 *
	 * <p>This distinction is the whole reason the class works, and getting it wrong made the
	 * correction fire on a process that was perfectly memoryless. The slices are geometric — two
	 * minutes wide at the start and eighty at the end — so a constant <em>chance per slice</em> is a
	 * steeply falling rate, and a constant rate shows up as a chance per slice that climbs. Comparing
	 * slices directly therefore reports duration dependence for a Poisson process and misses it in a
	 * queue, which is exactly backwards.
	 *
	 * <p>Converting is one line: a slice of width {@code w} whose conditional fill chance is {@code h}
	 * is produced by the constant rate {@code -ln(1 - h) / w}. Survival then integrates that rate over
	 * elapsed time, and a flat rate gives {@code exp(-rt)} — memoryless, as it should be.
	 */
	synchronized double hazardRate(int itemId, boolean buying, int bucket)
	{
		double width = widthOf(bucket);
		if (width <= 0)
		{
			return 0;
		}
		double perSlice = hazard(itemId, buying, bucket);
		return -Math.log(1.0 - Math.min(MAX_HAZARD, perSlice)) / width;
	}

	/**
	 * The chance an offer is still unfilled after this long.
	 *
	 * <p>Integrates the rate over the minutes actually elapsed, so half a slice counts half. Counting
	 * whole slices instead made an offer's survival depend on which side of a boundary it sat, and
	 * made a fixed horizon cover six slices from cold and one from an hour in.
	 */
	synchronized double survival(int itemId, boolean buying, double minutes)
	{
		if (minutes <= 0)
		{
			return 1.0;
		}
		double cumulative = 0;
		for (int bucket = 0; bucket < BUCKET_MINUTES.length; bucket++)
		{
			double start = BUCKET_MINUTES[bucket];
			if (minutes <= start)
			{
				break;
			}
			double elapsed = Math.min(minutes - start, widthOf(bucket));
			cumulative += hazardRate(itemId, buying, bucket) * elapsed;
		}
		return Math.max(0, Math.min(1, Math.exp(-cumulative)));
	}

	/**
	 * The question the analytical model cannot answer: it has been {@code waitedMinutes} and nothing
	 * has happened — what are the odds it fills in the next {@code horizonMinutes}?
	 *
	 * <p>Conditional on having survived, which is the entire point. If the hazard is flat this
	 * returns the same answer whatever the wait, and the memoryless assumption was right; if it
	 * declines, an offer that has already waited is worth less than a fresh one and the repricing
	 * decision changes.
	 */
	synchronized double completionWithin(int itemId, boolean buying, double waitedMinutes,
		double horizonMinutes)
	{
		if (horizonMinutes <= 0)
		{
			return 0;
		}
		double now = survival(itemId, buying, waitedMinutes);
		if (now <= 0)
		{
			return 1;
		}
		double later = survival(itemId, buying, waitedMinutes + horizonMinutes);
		return Math.max(0, Math.min(1, 1.0 - later / now));
	}

	/**
	 * Whether waiting genuinely gets worse, which is the finding that decides if any of this is worth
	 * consulting.
	 *
	 * <p>Compares the pooled hazard early against late. Above 1 means an offer that has already
	 * waited is less likely to fill than a fresh one — real duration dependence, and evidence the
	 * memoryless assumption costs money. At 1 the analytical model was right and this class should
	 * be left switched off.
	 *
	 * @return early hazard divided by late hazard, or 1 when there is not enough to say
	 */
	synchronized double durationDependence()
	{
		double early = weightedRate(0, 10);
		double late = weightedRate(10, Integer.MAX_VALUE);
		if (early <= 0 || late <= 0)
		{
			return 1.0;
		}
		return early / late;
	}

	/**
	 * The pooled hazard <em>rate</em> across a span of slices, weighted by how much evidence each
	 * carries.
	 * <p>
	 * Rates, not per-slice chances. Comparing chances across slices of different widths measures the
	 * widths.
	 */
	private double weightedRate(int fromMinutes, int toMinutes)
	{
		double total = 0;
		double weight = 0;
		for (int bucket = 0; bucket < pooled.length; bucket++)
		{
			if (pooled[bucket].atRisk <= 0 || BUCKET_MINUTES[bucket] < fromMinutes
				|| BUCKET_MINUTES[bucket] >= toMinutes)
			{
				continue;
			}
			double width = widthOf(bucket);
			double perSlice = Math.min(MAX_HAZARD, (double) pooled[bucket].filled / pooled[bucket].atRisk);
			total += (-Math.log(1.0 - perSlice) / width) * pooled[bucket].atRisk;
			weight += pooled[bucket].atRisk;
		}
		return weight <= 0 ? 0 : total / weight;
	}

	/** One line for the health report: the shape of the curve and how much stands behind it. */
	synchronized String summary()
	{
		if (observations == 0)
		{
			return "fill hazard: no settled offers measured yet";
		}
		StringBuilder curve = new StringBuilder();
		for (int bucket = 0; bucket < pooled.length; bucket++)
		{
			if (pooled[bucket].atRisk > 0)
			{
				if (curve.length() > 0)
				{
					curve.append(' ');
				}
				// Per hour, so slices of different widths can be read against each other. A curve
				// printed as a chance per slice climbs on a memoryless process, which reads as the
				// opposite of what is happening.
				curve.append(String.format("%dm:%.0f/h", BUCKET_MINUTES[bucket],
					hazardRate(0, true, bucket) * 60));
			}
		}
		return String.format("fill hazard: %d offers, %s (waiting costs %.2fx)%s",
			observations, curve, durationDependence(), isUsable() ? "" : " — not yet used");
	}
}
