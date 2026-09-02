package com.flippingfriend.companion;

import com.flippingfriend.core.OfferEvent;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks the current state of all active Grand Exchange offers for a player session.
 */
public class ActiveOfferTracker
{
	private final Map<Integer, OfferEvent> activeOffers = new ConcurrentHashMap<>();

	public void apply(OfferEvent event)
	{
		if (event == null || event.getEventType() == null)
		{
			return;
		}

		String state = event.getEventType();
		if ("EMPTY".equals(state) || "CANCELLED_BUY".equals(state) || "CANCELLED_SELL".equals(state)
			|| "BOUGHT".equals(state) || "SOLD".equals(state))
		{
			activeOffers.remove(event.getSlot());
		}
		else
		{
			activeOffers.put(event.getSlot(), event);
		}
	}

	public Collection<OfferEvent> getActiveOffers()
	{
		return Collections.unmodifiableCollection(activeOffers.values());
	}
}
