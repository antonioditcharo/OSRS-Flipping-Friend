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
	 * How likely this exit was judged to fill, which is the claim the calibrator grades.
	 *
	 * <p>The engine has always computed this -- {@code bestExit} scores every candidate price on
	 * probability and duration and picks the best -- and then dropped it on the floor. Nothing
	 * downstream could say what the plugin had CLAIMED about a sale, so every settled sale reached
	 * the calibrator with a predicted completion of zero, and the calibrator's own rule is that a
	 * zero means no claim was made and the observation is discarded.
	 *
	 * <p>The result was a sell calibrator sitting at nought observations for the life of the plugin
	 * while the buy leg passed sixty. Not slow: unreachable. It could never have learned anything
	 * however long it ran, because it was never told what it was supposed to be grading.
	 */
	private double completionProbability;
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

	/** The completion this exit was judged at, 0 when no estimate stood behind it. */
	public double getCompletionProbability()
	{
		return completionProbability;
	}

	/** A copy carrying the probability, set after the fact since only the caller has it. */
	public SellDecision withCompletion(double probability)
	{
		SellDecision copy = new SellDecision(action, price, reason, expectedMinutes, expectedProfit);
		copy.exitNear = exitNear;
		copy.completionProbability = Double.isFinite(probability)
			? Math.max(0, Math.min(1, probability)) : 0;
		return copy;
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
