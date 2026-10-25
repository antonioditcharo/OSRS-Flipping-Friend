package com.flippingfriend.session;

/**
 * What to do with an item we are currently holding.
 */
public class SellDecision
{
	public enum Action
	{
		/** Keep waiting; the target has not been reached and the position is still healthy. */
		HOLD,
		/** Sell now at the given price. */
		SELL,
		/** The trade has gone wrong. Get out at the given price and take the loss. */
		CUT
	}

	private final Action action;
	private final int price;
	private final String reason;
	private final double expectedMinutes;
	private final long expectedProfit;
	public SellDecision(Action action, int price, String reason, double expectedMinutes, long expectedProfit)
	{
		this.action = action;
		this.price = price;
		this.reason = reason;
		this.expectedMinutes = expectedMinutes;
		this.expectedProfit = expectedProfit;
	}

	public static SellDecision hold(String reason, double expectedMinutes)
	{
		return new SellDecision(Action.HOLD, 0, reason, expectedMinutes, 0);
	}

	/**
	 * True when this holding should have a Grand Exchange slot kept for it.
	 * <p>
	 * Which is any holding the engine can price, because every one of them is listed on the pass
	 * that sees it. This used to be a separate flag the caller set after the fact -- "the price is
	 * within a pricing step of the target, or the hold limit is about to force the sale" -- from the
	 * days when a position could be kept back for hours and it would have been wasteful to reserve a
	 * slot for something that might never leave. Nothing is kept back now, so the question answers
	 * itself, and a flag nobody sets is worse than no flag.
	 */
	public boolean isExitNear()
	{
		return action != Action.HOLD;
	}

	public Action getAction()
	{
		return action;
	}

	public int getPrice()
	{
		return price;
	}

	public String getReason()
	{
		return reason;
	}

	public double getExpectedMinutes()
	{
		return expectedMinutes;
	}

	public long getExpectedProfit()
	{
		return expectedProfit;
	}

	public boolean isSell()
	{
		return action == Action.SELL || action == Action.CUT;
	}
}
