package com.flippingfriend.companion;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Which offers this system told the player to cancel, and when.
 *
 * <p>Exists to answer one question that two separate learners both need and neither can see:
 * <b>how much of our censoring is our own doing?</b>
 *
 * <p>{@link FillHazard} and {@link CaptureRates} both learn from settled offers, and both carry the
 * same recorded limitation — a player cancels the offers that are not filling, so the cancellations
 * are not a random sample and the estimates lean optimistic about long waits. Stated that way it is
 * a caveat nobody can act on. What turns it into a number is knowing which cancels followed our own
 * "this slot is worth more elsewhere" alert: those are informative censoring of the most direct kind,
 * because the model's own opinion caused the observation to stop.
 *
 * <p>What this records is a <em>circumstance</em>, not a motive. "We advised cancelling this offer
 * eleven minutes before it was cancelled" is observable and true. "The player cancelled it because we
 * said so" is a guess, and the game gives no reason with the event. The distinction matters because
 * the number is going into a bias estimate, and a bias estimate built on a guess is worse than none.
 *
 * <p>Deliberately not a correction. Knowing the share tells you whether the bias is worth correcting
 * for at all — a few per cent needs nothing, and a third would mean the hazard's late slices are
 * mostly describing our own impatience. Building the correction before knowing which of those is
 * true would be fitting a machine to an unmeasured problem.
 */
final class CancelAdvice
{
	/**
	 * How long an alert stays relevant to a cancel that follows it.
	 * <p>
	 * Half an hour. Long enough to cover a player who reads the panel, finishes what they were doing
	 * and then acts; short enough that an alert from two sessions ago is not credited with a
	 * cancellation it had nothing to do with.
	 */
	private static final long RELEVANT_FOR_SECONDS = 30 * 60;

	/** Kept small: this is a handful of open offers, not a history. */
	private static final int MAX_TRACKED = 256;

	private final Map<Long, Long> advisedAt = new ConcurrentHashMap<>();

	private static long key(int itemId, int slot)
	{
		return ((long) itemId << 8) | (slot & 0xFF);
	}

	/** Records that the plan asked for this offer to be reconsidered. */
	void advised(int itemId, int slot, long atSeconds)
	{
		if (itemId <= 0 || atSeconds <= 0)
		{
			return;
		}
		if (advisedAt.size() >= MAX_TRACKED)
		{
			prune(atSeconds);
		}
		advisedAt.put(key(itemId, slot), atSeconds);
	}

	/**
	 * Whether our own advice preceded this cancellation, within the window above.
	 * <p>
	 * Consuming the record as it answers: an alert explains the cancel that follows it and not the
	 * next one on the same slot, which would be a different offer.
	 */
	boolean wasAdvised(int itemId, int slot, long atSeconds)
	{
		Long when = advisedAt.remove(key(itemId, slot));
		return when != null && atSeconds >= when && atSeconds - when <= RELEVANT_FOR_SECONDS;
	}

	/** Drops advice too old to explain anything. */
	void prune(long nowSeconds)
	{
		advisedAt.entrySet().removeIf(entry -> nowSeconds - entry.getValue() > RELEVANT_FOR_SECONDS);
	}

	int tracked()
	{
		return advisedAt.size();
	}
}
