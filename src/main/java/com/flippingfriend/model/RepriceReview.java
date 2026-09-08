package com.flippingfriend.model;

import java.util.HashMap;
import java.util.Map;

/**
 * Whether an open offer is still priced where the plugin would price it now, and whether saying so
 * is worth the interruption.
 *
 * <p>Until this existed the plugin only ever noticed an offer that had become too PASSIVE to fill —
 * a buy under the bid, a sell over the ask. An offer the market had moved in favour of was never
 * mentioned, so a sell listed at 100 while buyers moved to 110 simply filled at 100 and nobody said
 * anything. It also never compared an offer to its own latest recommendation; it re-derived from the
 * market each pass and asked a one-directional question of it.
 *
 * <h2>Why a threshold, and not just "is it different"</h2>
 *
 * <p>Prices move constantly. An offer is almost never at exactly the number the engine would pick
 * this second, and advice that fires on every coin of drift is advice nobody can follow — the player
 * would spend the session retyping offers and the plugin would look deranged. So the question is not
 * "has the price changed" but "is the change worth a cancel, a retype and a place".
 *
 * <p>Three gates, all of which must pass:
 *
 * <ul>
 *   <li><b>A real gain.</b> Moving must be worth more than staying, measured in the same units the
 *       rest of the engine reasons in.</li>
 *   <li><b>Worth the click.</b> The gain must clear a floor tied to the player's own minimum profit
 *       per flip. They have already told us what a trade has to be worth to be worth doing; a
 *       fraction of that is a fair bar for what an adjustment has to be worth.</li>
 *   <li><b>Worth it proportionally.</b> The gain must be a real share of what is already on the
 *       table, so a 400 gp improvement on a 40,000 gp position is left alone. This gate is skipped
 *       when the offer is worth nothing as it stands, which is what a stalled offer looks like —
 *       there, any gain is the whole gain.</li>
 * </ul>
 *
 * <h2>And a cooldown, because a threshold alone oscillates</h2>
 *
 * <p>Two prices either side of the bar will trade places for as long as the market jitters, and each
 * swap passes every test above on its own merits. So a given offer's target is moved at most once per
 * {@link #COOLDOWN_SECONDS}, which is one five-minute bar of the finest data the plugin has. Standing
 * advice is unaffected: repeating the same recommendation costs nothing and is not a move.
 */
public final class RepriceReview
{
	/**
	 * How long before the same offer's target may be moved again.
	 *
	 * <p>One five-minute bar, which is the resolution of the price history every decision here is
	 * drawn from. Reacting faster than the data can distinguish is reacting to noise.
	 */
	static final long COOLDOWN_SECONDS = 300;

	/** The gain must also be this share of what the offer is already worth, when it is worth anything. */
	static final double MATERIAL_SHARE = 0.05;

	/** What was last advised for a slot, so a target is not walked back and forth. */
	private final Map<Integer, Advised> advised = new HashMap<>();

	/**
	 * Keyed by slot and remembering the item, because slots are reused.
	 *
	 * <p>A cooldown is about one offer settling down, not about the eight boxes on the interface.
	 * Keyed on the slot alone, a finished trade would hand its cooldown to whatever went into that
	 * slot next and silence the first five minutes of a completely unrelated offer -- and nothing
	 * would ever have said so. Carrying the item makes a slot's history self-expiring: a different
	 * item is a different offer, and there is no separate clean-up call for anyone to forget.
	 */
	private static final class Advised
	{
		private final int itemId;
		private final int price;
		private final long at;

		private Advised(int itemId, int price, long at)
		{
			this.itemId = itemId;
			this.price = price;
			this.at = at;
		}
	}

	/**
	 * @param slot          the Grand Exchange slot the offer occupies
	 * @param currentPrice  what the offer is priced at now
	 * @param recommended   what the engine would price it at now, or 0 if it has no opinion
	 * @param gpPerHourNow      projected gp/hr if the offer is left alone
	 * @param gpPerHourMoved    projected gp/hr if it is moved to {@code recommended}
	 * @param nowSeconds    the current time
	 * @return true when the player should be told to move this offer
	 */
	public synchronized boolean worthMoving(int slot, int itemId, int currentPrice, int recommended,
		double gpPerHourNow, double gpPerHourMoved, long nowSeconds)
	{
		if (recommended <= 0 || recommended == currentPrice)
		{
			return false;
		}

		double gainGpHr = gpPerHourMoved - gpPerHourNow;
		if (gainGpHr <= 0)
		{
			return false;
		}

		// The floor is now based on a relative improvement rather than a flat GP amount.
		// A minimum 2% improvement in GP/hr is required to justify a move.
		if (gpPerHourNow > 0 && gainGpHr < gpPerHourNow * 0.02)
		{
			return false;
		}
		
		// Absolute minimum GP/hr gain to avoid spamming tiny moves on very low margin items.
		if (gainGpHr < 5000)
		{
			return false;
		}

		Advised last = advised.get(slot);
		if (last != null && last.itemId == itemId && last.price != recommended
			&& nowSeconds - last.at < COOLDOWN_SECONDS)
		{
			// A different target for an offer whose target was only just moved. Repeating the SAME
			// recommendation is not a move and is not held back -- advice the player has not acted on
			// yet must not vanish because it is a few minutes old.
			return false;
		}
		return true;
	}

	/** Records that this price was advised for this slot, starting its cooldown. */
	public synchronized void noteAdvised(int slot, int itemId, int price, long nowSeconds)
	{
		Advised last = advised.get(slot);
		if (last != null && last.itemId == itemId && last.price == price)
		{
			// Same advice as before, so the clock does not restart -- otherwise repeating a standing
			// recommendation would hold its own cooldown open for ever.
			return;
		}
		advised.put(slot, new Advised(itemId, price, nowSeconds));
	}
}
