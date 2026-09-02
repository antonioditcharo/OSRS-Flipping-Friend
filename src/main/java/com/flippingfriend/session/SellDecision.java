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
	/**
	 * Whether this holding is close enough to leaving that a slot should be kept for it.
	 * <p>
	 * Decided here because this is the only place that knows both the price the position could leave
	 * at and the target it is being held for. The slot reservation used to hold a slot for anything
	 * held at all, which meant a position waiting hours for a price it might never reach kept a
	 * Grand Exchange slot idle the whole time.
	 */
	private boolean exitNear;

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

	/** A copy that says the exit is close. Set after the fact, since only the caller knows. */
	public SellDecision withExitNear(boolean near)
	{
		SellDecision copy = new SellDecision(action, price, reason, expectedMinutes, expectedProfit);
		copy.exitNear = near;
		return copy;
	}

	/**
	 * True when leaving is close enough to be worth reserving a slot for: the price is within a
	 * pricing step of the target, or the hold limit is about to force the sale anyway. Always true
	 * once the decision is actually to sell.
	 */
	public boolean isExitNear()
	{
		return exitNear || action != Action.HOLD;
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
