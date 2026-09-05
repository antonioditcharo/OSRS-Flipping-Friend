package com.flippingfriend.companion;

import com.flippingfriend.core.OfferEvent;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * One physical offer, counted once.
 *
 * <p>124 of 784 settled offers in the live database are byte-identical repeats carrying different
 * correlation ids: the same fill, reported twice. The correlation id is what the event log
 * deduplicates on, so two ids meant two events, and every estimator downstream counted both.
 *
 * <p>This is not new. A code comment in the plugin records one sale of 4,337 mithril bars being
 * counted six times over when the client replayed its slots on login, and the guard added then was
 * about ordering rather than identity — it made the replay land after the ledger was loaded, not
 * once.
 *
 * <p>What it costs is not obvious from the duplicate itself. Everything settled feeds capture rates,
 * the fill hazard life table, calibration and the buy-limit ledger, and all of them weight by
 * quantity, so a duplicated offer does not merely add a row — it doubles that offer's say in what the
 * model believes. The offers most likely to be duplicated are the ones with the most events behind
 * them, which are the large ones.
 *
 * <h2>What identifies an offer</h2>
 *
 * <p>The slot it occupied, what it was, what it cost, how much of it filled, and the two timestamps
 * bounding it. Two genuinely distinct offers cannot share all of those: they would have to have begun
 * and ended in the same second, in the same slot, on the same item, at the same price, having filled
 * to exactly the same depth. The correlation id is deliberately not part of it — it is the thing that
 * differs when the same offer is reported twice, so including it would defeat the purpose.
 *
 * <p>Bounded, because this is a hot path and the duplicates arrive close together. A few hundred
 * recent offers covers the window in which a repeat can plausibly turn up.
 */
final class SettledOnce
{
	/**
	 * How many recent offers to remember.
	 *
	 * <p>Generous next to the eight Grand Exchange slots a player has. The duplicates seen in practice
	 * arrive within seconds of each other, or in a burst when the client replays its slots on login,
	 * and eight slots cannot produce hundreds of settlements before a repeat would show up.
	 */
	private static final int REMEMBERED = 512;

	private final Set<String> seen = new LinkedHashSet<>();
	private final int capacity;
	private int duplicates;

	SettledOnce()
	{
		this(REMEMBERED);
	}

	SettledOnce(int capacity)
	{
		this.capacity = Math.max(1, capacity);
	}

	/**
	 * @return true the first time this offer is seen, false for a repeat of one already counted
	 */
	synchronized boolean firstSighting(OfferEvent event)
	{
		if (event == null)
		{
			return false;
		}

		String fingerprint = fingerprint(event);
		if (!seen.add(fingerprint))
		{
			duplicates++;
			return false;
		}

		while (seen.size() > capacity)
		{
			// Insertion-ordered, so this drops the oldest. A repeat that arrives after five hundred
			// other settlements is not a repeat of anything we are still reasoning about.
			java.util.Iterator<String> oldest = seen.iterator();
			oldest.next();
			oldest.remove();
		}
		return true;
	}

	/** Remembers an offer without judging it, for seeding from what is already on record. */
	synchronized void remember(OfferEvent event)
	{
		if (event != null)
		{
			firstSightingIgnoringCount(fingerprint(event));
		}
	}

	private void firstSightingIgnoringCount(String fingerprint)
	{
		seen.add(fingerprint);
		while (seen.size() > capacity)
		{
			java.util.Iterator<String> oldest = seen.iterator();
			oldest.next();
			oldest.remove();
		}
	}

	/** How many repeats have been turned away, for the health line. */
	synchronized int duplicatesRefused()
	{
		return duplicates;
	}

	private static String fingerprint(OfferEvent event)
	{
		return event.getEventType() + "/" + event.getItemId() + "/" + event.getSlot() + "/"
			+ event.getPrice() + "/" + event.getFilledQuantity() + "/" + event.getTotalQuantity()
			+ "/" + event.getFirstSeenAt() + "/" + event.getObservedAt();
	}
}
