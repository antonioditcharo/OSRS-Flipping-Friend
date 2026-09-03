package com.flippingfriend.companion;

import com.flippingfriend.core.OfferEvent;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Tracks how much of each item's four-hour buy allowance has been used.
 * <p>
 * The game does not expose this, and getting it wrong is expensive in a way that is easy to miss: an
 * offer over the limit simply stops filling part-way with no error, tying up a slot and leaving a
 * half position to be sold at whatever the market has moved to since.
 * <p>
 * The window is <em>anchored</em>, not sliding. Buying an item starts a four hour window; everything
 * bought inside it counts against the same allowance, and the whole allowance returns when that
 * window ends. Modelling it as a sliding window — the intuitive guess — would have the planner
 * suggesting purchases the game will refuse.
 */
final class BuyLimitLedger
{
	static final Duration WINDOW = Duration.ofHours(4);
	/** Reserved offer key holding whatever the plugin's ledger knows that this one has not seen. */
	private static final String RECONCILED = "reconciled";

	private final Map<Integer, Window> windows = new HashMap<>();

	/** Folds a fill into the ledger. Only completed purchases consume an allowance. */
	synchronized void apply(OfferEvent event)
	{
		if (event == null || !event.isBuying() || event.getFilledQuantity() <= 0)
		{
			return;
		}

		Instant when = Instant.ofEpochSecond(event.getObservedAt());
		windows.compute(event.getItemId(), (id, existing) ->
		{
			if (existing == null || existing.hasExpired(when))
			{
				Window window = new Window(when);
				window.record(offerKey(event), event.getFilledQuantity());
				return window;
			}
			// Events carry the running total for the offer, so each offer contributes its own
			// high-water mark rather than a sum of deltas that may be replayed on reconnect. The
			// high-water rule has to be per offer, though: keyed by item alone it could not tell two
			// separate 250-unit purchases from one 250-unit purchase announced twice, so it recorded
			// 250 where 500 of the allowance had gone. That is precisely the failure this class
			// exists to prevent -- the next offer goes over the limit, stops filling part-way with
			// no error, and leaves half a position to unwind.
			existing.record(offerKey(event), event.getFilledQuantity());
			return existing;
		});
	}

	/**
	 * Identifies one offer instance. The slot alone is not enough — it is reused the moment an offer
	 * is collected — so the moment the offer first appeared distinguishes successive tenants of it.
	 */
	private static String offerKey(OfferEvent event)
	{
		// firstSeenAt is 0 when the plugin never saw the offer appear -- a login replay, or the plugin
		// being switched on mid-offer. Keying on the raw value put every such offer under "slot:0",
		// where Math::max then merged two separate 250-unit purchases into one and recorded 250 where
		// 500 of the allowance had gone. That is the exact failure this class exists to prevent.
		// ExecutionRecorder.identity() already falls back this way; the two must agree.
		long start = event.getFirstSeenAt() > 0 ? event.getFirstSeenAt() : event.getObservedAt();
		return event.getSlot() + ":" + start;
	}

	/**
	 * Raises the ledger to match what the plugin knows it has bought.
	 * <p>
	 * Offer events reach this process over loopback HTTP with no queue, no retry and a 750 ms timeout,
	 * so a fill that lands while the companion is restarting is lost to it permanently -- and the next
	 * plan then orders over the limit, which stops filling part-way with no error. The plugin's own
	 * ledger is durable and survives that, so it is sent with every account snapshot and used as a
	 * floor here.
	 * <p>
	 * A floor, never a ceiling: this can only make the companion more cautious. Lowering the figure on
	 * the plugin's say-so would risk the opposite error, and over-ordering is the expensive one.
	 */
	synchronized void reconcile(Map<Integer, Integer> usedByItem)
	{
		if (usedByItem == null || usedByItem.isEmpty())
		{
			return;
		}
		Instant now = Instant.now();
		for (Map.Entry<Integer, Integer> reported : usedByItem.entrySet())
		{
			int used = reported.getValue() == null ? 0 : reported.getValue();
			if (used <= 0)
			{
				continue;
			}
			windows.compute(reported.getKey(), (id, existing) ->
			{
				Window window = existing == null || existing.hasExpired(now) ? new Window(now) : existing;
				window.floorAt(used);
				return window;
			});
		}
	}

	/** How many more of each item the game will allow right now. */
	synchronized Map<Integer, Integer> remaining(Map<Integer, MarketIngestionService.Item> mapping)
	{
		Instant now = Instant.now();
		Map<Integer, Integer> remaining = new HashMap<>();

		for (Map.Entry<Integer, MarketIngestionService.Item> entry : mapping.entrySet())
		{
			int limit = entry.getValue().buyLimit;
			Window window = windows.get(entry.getKey());
			int used = window == null || window.hasExpired(now) ? 0 : window.quantity();
			remaining.put(entry.getKey(), Math.max(0, limit - used));
		}
		return remaining;
	}

	/** When an item's allowance returns, for planning the slot's next occupant. */
	synchronized Instant resetsAt(int itemId)
	{
		return resetsAt(itemId, Instant.now());
	}

	/**
	 * When this item's window lifts, as judged from a supplied clock.
	 *
	 * <p>The no-argument form reads {@code Instant.now()} internally, which made it unusable anywhere
	 * the time is not the wall clock — replay, and any test of a four-hour window. For a class whose
	 * entire subject is a window, a hidden clock is the wrong default: the plugin's own
	 * {@code BuyLimitTracker.resetsAt(int, Instant)} has always taken one, and this was the odd one
	 * out. Kept as a delegating overload so existing callers are unaffected.
	 */
	synchronized Instant resetsAt(int itemId, Instant now)
	{
		Window window = windows.get(itemId);
		if (window == null || window.hasExpired(now))
		{
			return null;
		}
		return window.startedAt.plus(WINDOW);
	}

	synchronized int trackedItems()
	{
		return windows.size();
	}

	private static final class Window
	{
		/** Highest fill seen for each distinct offer in this window; the allowance used is their sum. */
		private final Map<String, Integer> byOffer = new HashMap<>();
		private final Instant startedAt;

		Window(Instant startedAt)
		{
			this.startedAt = startedAt;
		}

		void record(String offer, int filledQuantity)
		{
			byOffer.merge(offer, filledQuantity, Math::max);
		}

		/**
		 * Ensures the window accounts for at least this much, under a reserved key so it cannot be
		 * confused with a real offer and cannot be double-counted as more reports arrive.
		 */
		void floorAt(int used)
		{
			int fromOffers = 0;
			for (Map.Entry<String, Integer> entry : byOffer.entrySet())
			{
				if (!RECONCILED.equals(entry.getKey()))
				{
					fromOffers += entry.getValue();
				}
			}
			byOffer.put(RECONCILED, Math.max(0, used - fromOffers));
		}

		int quantity()
		{
			int total = 0;
			for (int filled : byOffer.values())
			{
				total += filled;
			}
			return total;
		}

		boolean hasExpired(Instant now)
		{
			return startedAt.plus(WINDOW).isBefore(now);
		}
	}
}
