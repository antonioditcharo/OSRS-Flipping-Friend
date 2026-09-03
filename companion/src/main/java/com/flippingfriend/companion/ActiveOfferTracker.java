package com.flippingfriend.companion;

import com.flippingfriend.core.OfferEvent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks the current state of all active Grand Exchange offers for a player session.
 *
 * <p>"Active" means <em>holding a slot</em>, which is not the same as still trading. A finished offer
 * keeps its slot until the player collects from it: the game reports {@code BOUGHT} or {@code SOLD}
 * and the slot stays occupied, becoming {@code EMPTY} only once the items and coins are taken. A
 * cancelled offer behaves the same way — there is still something in the box.
 *
 * <p>This class used to forget an offer the moment it reached a terminal state, so the companion
 * believed a slot was free while the game still showed it held. {@code AccountMonitor} on the plugin
 * side has always counted occupancy as {@code state != EMPTY}, so the two disagreed, and the
 * companion's disagreement was the wrong one. Only {@code EMPTY} removes an offer now, which is the
 * same rule stated in one place instead of two.
 *
 * <p>The distinction is worth money rather than merely being tidy. An uncollected offer is a slot
 * earning nothing, and the value of collecting is whatever that slot would otherwise make — a number
 * the planner already computes as its hurdle. Forgetting the offer made that cost invisible.
 */
public class ActiveOfferTracker
{
	/**
	 * States where the offer is finished but the slot is still held, pending collection.
	 * <p>
	 * Cancelling is included deliberately: a cancelled buy leaves coins in the box and a cancelled
	 * sell leaves items, and neither frees the slot until taken.
	 */
	private static final Set<String> AWAITING_COLLECTION = Collections.unmodifiableSet(
		new java.util.HashSet<>(java.util.Arrays.asList(
			"BOUGHT", "SOLD", "CANCELLED_BUY", "CANCELLED_SELL")));

	private final Map<Integer, OfferEvent> activeOffers = new ConcurrentHashMap<>();

	public void apply(OfferEvent event)
	{
		if (event == null || event.getEventType() == null)
		{
			return;
		}

		// EMPTY is the only state that frees a slot. Everything else is still occupying one, whether
		// it is trading, finished and uncollected, or cancelled and uncollected.
		if ("EMPTY".equals(event.getEventType()))
		{
			activeOffers.remove(event.getSlot());
			return;
		}
		activeOffers.put(event.getSlot(), event);
	}

	public Collection<OfferEvent> getActiveOffers()
	{
		return Collections.unmodifiableCollection(activeOffers.values());
	}

	/** Offers holding a slot with nothing left to do but be collected. */
	public Collection<OfferEvent> awaitingCollection()
	{
		List<OfferEvent> waiting = new ArrayList<>();
		for (OfferEvent offer : activeOffers.values())
		{
			if (isAwaitingCollection(offer))
			{
				waiting.add(offer);
			}
		}
		return Collections.unmodifiableCollection(waiting);
	}

	/** True when this offer is finished and merely waiting to be emptied out. */
	public static boolean isAwaitingCollection(OfferEvent offer)
	{
		return offer != null && AWAITING_COLLECTION.contains(offer.getEventType());
	}
}
