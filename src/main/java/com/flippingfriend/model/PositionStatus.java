package com.flippingfriend.model;

import com.flippingfriend.session.SellDecision;

/**
 * What the plugin is doing about one holding, and why.
 * <p>
 * The sell engine already decides this for every position on every refresh — whether to sell now, what
 * price it is waiting for, how long it expects to take — and until now all of it was thrown away
 * except the single position that won. So a player holding nine thousand of something could watch it
 * sit there with no way to tell whether it was being managed, waiting deliberately, or forgotten.
 * <p>
 * Carrying the decision out to the panel costs nothing: it is already computed, on the engine thread,
 * where the model work belongs.
 */
public final class PositionStatus
{
	private final SellDecision decision;
	private final boolean unconfirmed;
	private final boolean blockedBySlots;
	/** Set when an offer is already carrying this holding out of the account. */
	private final boolean selling;
	private final int listedPrice;
	private final int listedFilled;
	private final int listedTotal;
	/** Units held back rather than given a slot of their own. */
	private final int withheld;
	/** Set when leaving needs our own buy cancelled first. */
	private boolean blockedByOwnBuy;
	/** Set while a buy for this item is still working, so the position is still growing. */
	private final boolean stillBuying;
	private final int boughtSoFar;
	private final int ordered;

	public PositionStatus(SellDecision decision, boolean unconfirmed, boolean blockedBySlots)
	{
		this(decision, unconfirmed, blockedBySlots, false, 0, 0, 0, 0, false, 0, 0);
	}

	/**
	 * A holding whose buy is still running, so selling it is deferred rather than declined.
	 * <p>
	 * The order is still growing, and nothing can be listed until it stops. Reserving a Grand Exchange
	 * slot for a fraction of it spends the scarcest thing the account has on an option it cannot use,
	 * so the wait is deliberate -- and saying so is the difference between a plugin that is waiting
	 * and one that looks like it has forgotten.
	 */
	public static PositionStatus stillBuying(int boughtSoFar, int ordered)
	{
		return new PositionStatus(null, false, false, false, 0, 0, 0, 0, true, boughtSoFar, ordered);
	}

	/**
	 * The exit is wanted, and our own buy is the thing standing in the way.
	 * <p>
	 * Distinct from an ordinary sell decision because the action is not "sell this" -- it is "stop
	 * buying this, then sell it". Showing the plain sell reason here left the card saying it was time
	 * to sell while the suggestion beside it said to cancel a purchase, which reads as two systems
	 * disagreeing rather than one explaining itself.
	 */
	public static PositionStatus exitBlockedByOwnBuy(SellDecision decision)
	{
		return new PositionStatus(decision, false, false, false, 0, 0, 0, 0, false, 0, 0)
			.asBlockedByOwnBuy();
	}

	private PositionStatus asBlockedByOwnBuy()
	{
		this.blockedByOwnBuy = true;
		return this;
	}

	private PositionStatus(SellDecision decision, boolean unconfirmed, boolean blockedBySlots,
		boolean selling, int listedPrice, int listedFilled, int listedTotal, int withheld,
		boolean stillBuying, int boughtSoFar, int ordered)
	{
		this.stillBuying = stillBuying;
		this.boughtSoFar = boughtSoFar;
		this.ordered = ordered;
		this.decision = decision;
		this.unconfirmed = unconfirmed;
		this.blockedBySlots = blockedBySlots;
		this.selling = selling;
		this.listedPrice = listedPrice;
		this.listedFilled = listedFilled;
		this.listedTotal = listedTotal;
		this.withheld = withheld;
	}

	/**
	 * A holding that is already on the market.
	 * <p>
	 * This is not a sale waiting to happen and must never be reported as one. An item sitting in a
	 * sell offer is in no container at all, so it looks exactly like a holding that cannot be seen --
	 * which is how a position already selling came to be labelled "not seen in your inventory or bank"
	 * and "ready to sell, but every slot is busy" at the same time.
	 */
	public static PositionStatus selling(int price, int filled, int total)
	{
		return new PositionStatus(null, false, false, true, price, filled, total, 0, false, 0, 0);
	}

	/**
	 * A holding that is partly on the market, with the rest deliberately held back.
	 * <p>
	 * A second sell offer for the same item costs a second Grand Exchange slot, and a slot is held for
	 * hours. That is worth doing for a real remainder and not for the trickle a part-filled buy
	 * produces: an order for twenty thousand grapes that fills a few hundred at a time would otherwise
	 * ask for a fresh offer on every refresh and take the whole account to sell one item in pieces.
	 */
	public static PositionStatus sellingWithRemainder(int price, int filled, int total, int withheld)
	{
		return new PositionStatus(null, false, false, true, price, filled, total, withheld, false, 0, 0);
	}

	/** How much of the holding is being kept back rather than given a second slot. */
	public int getWithheld()
	{
		return withheld;
	}

	public boolean isSelling()
	{
		return selling;
	}

	public int getListedPrice()
	{
		return listedPrice;
	}

	public int getListedFilled()
	{
		return listedFilled;
	}

	public int getListedTotal()
	{
		return listedTotal;
	}

	public SellDecision getDecision()
	{
		return decision;
	}

	/** True when the holding is on record but has not been seen in any container. */
	public boolean isUnconfirmed()
	{
		return unconfirmed;
	}

	/** True when the plugin wants to sell this and has no free slot to do it in. */
	public boolean isBlockedBySlots()
	{
		return blockedBySlots;
	}

	/** One line for the panel: what is happening with this holding, in the engine's own words. */
	/** True while a buy for this item is still working. */
	public boolean isStillBuying()
	{
		return stillBuying;
	}

	public String summary()
	{
		if (blockedByOwnBuy)
		{
			String why = decision == null || decision.getReason() == null ? "" : " " + decision.getReason();
			return "Time to leave this, but your own buy is still adding to it — cancel that first."
				+ why;
		}
		if (stillBuying)
		{
			String progress = ordered > 0
				? " — " + boughtSoFar + " of " + ordered + " so far" : "";
			return "Still buying" + progress + ". It will be offered for sale once the buy finishes.";
		}
		if (selling)
		{
			String at = listedPrice > 0 ? " at " + listedPrice + " gp" : "";
			String progress = listedFilled <= 0
				? "On the market" + at + " — none sold yet."
				: "On the market" + at + " — " + listedFilled + " of " + listedTotal + " sold.";
			if (withheld > 0)
			{
				progress += " Holding " + withheld + " more back rather than spend a second slot.";
			}
			return progress;
		}
		if (blockedBySlots)
		{
			return "Ready to sell, but every Grand Exchange slot is busy.";
		}
		String reason = decision == null || decision.getReason() == null ? "" : decision.getReason();
		if (unconfirmed)
		{
			return reason.isEmpty()
				? "Held on record, but not currently visible. If it is banked, open your bank to "
					+ "confirm it."
				: reason + " (not currently visible -- open your bank if it is in there)";
		}
		return reason;
	}
}
