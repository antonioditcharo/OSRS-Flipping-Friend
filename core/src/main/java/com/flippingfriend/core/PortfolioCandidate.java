package com.flippingfriend.core;

/**
 * One fully priced entry/exit tactic which can occupy exactly one GE slot.
 * <p>
 * <b>A flip has three outcomes, not two.</b> The obvious model — it works with probability p, and
 * otherwise you take a stop loss — is badly wrong for the Grand Exchange, and wrong in the direction
 * that makes every trade look unprofitable:
 * <ul>
 *   <li><b>The buy never fills.</b> By far the most common way a flip fails to complete. You cancel
 *       the offer and every coin comes back. The cost is the slot time, not the capital, and pricing
 *       it as a loss is simply an error.</li>
 *   <li><b>Buy fills, sell fills at the target.</b> The intended outcome, worth {@code netProfit}.</li>
 *   <li><b>Buy fills, sell does not.</b> The only outcome that actually loses money — and it loses
 *       roughly the tax plus whatever the price drifted while you held, because you unwind into the
 *       bid rather than eating a full stop.</li>
 * </ul>
 * Collapsing those into one probability and one loss overstates the downside by an order of
 * magnitude: a typical flip earns a 2% margin, so charging a 5% stop against every non-completion
 * demands better than a 70% round-trip fill rate before the trade is worth doing at all. Almost
 * nothing clears that, which is how a market full of profitable flips scores as empty.
 * <p>
 * {@code worstLoss} survives, but only in its proper role: a genuine worst case for the session loss
 * budget and drawdown breaker. It is a risk constraint, never an expectation.
 * <p>
 * <b>Slot time was the weak point, and it has been fixed.</b> Multi-fold walk-forward replay once
 * put {@link #expectedSlotHours()} at 2.25x optimistic while the same estimator, used by a strategy
 * that does not rank on it, came out at 1.22x — ranking on a noisy quantity selects for its errors,
 * and because slot time is the denominator of the objective the optimizer was drawn to precisely the
 * trades whose duration it most understated. It lost every held-out fold, returning 0.82x of what
 * ranking on raw profit returned.
 * <p>
 * The cause was a missing term rather than noise: the duration model divided quantity by a rate,
 * which says a small enough order fills instantly, when in reality it waits for a counterparty to
 * arrive at the price. Adding that wait brought calibration to 1.04x and the strategy to 1.25x of
 * the baseline. See {@code FillModel.waitWeight}.
 * <p>
 * {@link #expectedProfit()} remains optimistic — realised profit runs at roughly 0.5x to 0.67x of
 * predicted across runs. Attempts to close that gap by changing the completion model produced
 * effects smaller than the harness can resolve, so the residual optimism is documented rather than
 * tuned away. Real fills, which {@code LearnedDurations} already consumes for timing, are the
 * credible route to fixing it; simulated folds are not.
 */
public final class PortfolioCandidate
{
	private final int itemId;
	private final String itemName;
	private final String group;
	private final int targetBuyPrice;
	private final int equilibriumBuyPrice;
	private final int exitBuyPrice;
	private final int targetSellPrice;
	private final int equilibriumSellPrice;
	private final int exitSellPrice;
	private final int quantity;
	private final int buyLimitRemaining;
	private final long capitalRequired;
	private final long netProfit;
	private final long worstLoss;
	private final long unwindLoss;
	private final double buyFillProbability;
	/**
	 * The buy-side completion rate to <em>show</em>, as opposed to the one used for ranking.
	 * <p>
	 * The ranking figure is a Thompson draw — deliberately random, so an item the model is merely
	 * unsure about is occasionally tried rather than frozen out forever. Showing that number to a
	 * player is a different matter, and ThompsonSampler's own javadoc says so: "the same item would
	 * appear to change its mind between refreshes -- so the panel gets the mean and the planner gets
	 * the draw." The panel was being given the draw. Zero means nothing was supplied, in which case
	 * the ranking figure is the honest fallback.
	 */
	private double displayBuyFillProbability;
	private final double sellFillProbability;
	private final double buyHours;
	private final double sellHours;
	private final double horizonHours;
	private final long expiresAt;

	/**
	 * Why the order is the size it is.
	 * <p>
	 * Order size is the product of several factors — reachable volume on each side, the share of it
	 * claimed, the hour-of-day multiplier, the horizon, the buy limit — and when the result is
	 * surprising there is no way to tell which one did it. Carrying the working means the answer is
	 * visible instead of needing to be reverse-engineered from outside.
	 */

	/**
	 * @param netProfit  profit after Grand Exchange tax, computed by the caller. Tax is not derived
	 *                   here on purpose: getting it exactly right needs the tax-exempt item list,
	 *                   which is only known once the item mapping has loaded — bonds, tools,
	 *                   low-level food and teleport tablets are never taxed, and a static formula
	 *                   silently understates profit on every one of them.
	 * @param worstLoss  worst realistic loss, for the risk budget only.
	 * @param unwindLoss what it costs to exit into the bid when the sell leg does not fill.
	 * @param horizonHours how long a leg is given before the player gives up on it.
	 */
	public PortfolioCandidate(int itemId, String itemName, String group, int targetBuyPrice, int equilibriumBuyPrice, int exitBuyPrice, int targetSellPrice, int equilibriumSellPrice, int exitSellPrice,
		int quantity, int buyLimitRemaining, long netProfit, long worstLoss, long unwindLoss,
		double buyFillProbability, double sellFillProbability, double buyHours, double sellHours,
		double horizonHours, long expiresAt)
	{
		this.itemId = itemId;
		this.itemName = itemName;
		this.group = group == null ? "" : group;
		this.targetBuyPrice = targetBuyPrice;
		this.equilibriumBuyPrice = equilibriumBuyPrice;
		this.exitBuyPrice = exitBuyPrice;
		this.targetSellPrice = targetSellPrice;
		this.equilibriumSellPrice = equilibriumSellPrice;
		this.exitSellPrice = exitSellPrice;
		this.quantity = Math.max(0, quantity);
		this.buyLimitRemaining = Math.max(0, buyLimitRemaining);
		this.capitalRequired = (long) Math.max(0, targetBuyPrice) * this.quantity;
		this.netProfit = netProfit;
		this.worstLoss = Math.max(0, worstLoss);
		this.unwindLoss = Math.max(0, unwindLoss);
		this.buyFillProbability = clamp(buyFillProbability);
		this.sellFillProbability = clamp(sellFillProbability);
		this.buyHours = Math.max(0, finite(buyHours));
		this.sellHours = Math.max(0, finite(sellHours));
		this.horizonHours = Math.max(1.0 / 60.0, finite(horizonHours));
		this.expiresAt = expiresAt;
		this.displayBuyFillProbability = 0;
	}

	/**
	 * Copy carrying the probability shown to the player, as distinct from the one used for ranking.
	 * <p>
	 * This copy used to carry four more fields -- the buy and sell throughput, a season multiplier and
	 * a sentence naming which ceiling bound the order size. Nothing read any of them: not the panel,
	 * not the monitor, not the companion's own reader of stored plans. They were computed and copied
	 * once per candidate per size fraction, inside the price sweep inside the per-item loop, which
	 * makes this the hottest path in the system, and then serialised into every plan on the wire and
	 * into every row of portfolio_plan.
	 * <p>
	 * The javadoc argued that carrying the working means a surprising order size can explain itself.
	 * It was a fair argument for a feature that was never built, and the explanation it was for is the
	 * one the account holder asked to be taken off the sidebar.
	 */
	public PortfolioCandidate withDisplayProbability(double displayBuyProbability)
	{
		return new PortfolioCandidate(this, displayBuyProbability);
	}

	private PortfolioCandidate(PortfolioCandidate from, double displayBuyProbability)
	{
		this.displayBuyFillProbability = clamp(displayBuyProbability);
		this.itemId = from.itemId;
		this.itemName = from.itemName;
		this.group = from.group;
		this.targetBuyPrice = from.targetBuyPrice;
		this.equilibriumBuyPrice = from.equilibriumBuyPrice;
		this.exitBuyPrice = from.exitBuyPrice;
		this.targetSellPrice = from.targetSellPrice;
		this.equilibriumSellPrice = from.equilibriumSellPrice;
		this.exitSellPrice = from.exitSellPrice;
		this.quantity = from.quantity;
		this.buyLimitRemaining = from.buyLimitRemaining;
		this.capitalRequired = from.capitalRequired;
		this.netProfit = from.netProfit;
		this.worstLoss = from.worstLoss;
		this.unwindLoss = from.unwindLoss;
		this.buyFillProbability = from.buyFillProbability;
		this.sellFillProbability = from.sellFillProbability;
		this.buyHours = from.buyHours;
		this.sellHours = from.sellHours;
		this.horizonHours = from.horizonHours;
		this.expiresAt = from.expiresAt;
	}

	/** Expected net GP, weighting each of the three outcomes by how likely it is. */
	public double expectedProfit()
	{
		double completed = buyFillProbability * sellFillProbability;
		double stranded = buyFillProbability * (1 - sellFillProbability);
		return completed * netProfit - stranded * unwindLoss;
	}

	/**
	 * Expected hours the slot stays occupied. A buy that never fills still ties the slot up for the
	 * whole horizon before it is cancelled, and a position that will not sell ties it up for the
	 * horizon a second time — so a high-margin tactic that rarely completes is correctly penalised
	 * for the slot time it wastes rather than only for the profit it misses.
	 */
	public double expectedSlotHours()
	{
		double completed = buyFillProbability * sellFillProbability;
		double stranded = buyFillProbability * (1 - sellFillProbability);
		double neverBought = 1 - buyFillProbability;
		double hours = neverBought * horizonHours
			+ completed * (buyHours + sellHours)
			+ stranded * (buyHours + horizonHours);
		return Math.max(1.0 / 60.0, hours);
	}

	/** The objective: expected net GP per hour of slot occupancy. */
	public double expectedGpPerSlotHour()
	{
		return expectedProfit() / expectedSlotHours();
	}

	/** Probability the whole round trip completes, for display. */
	public double getCompletionProbability()
	{
		return buyFillProbability * sellFillProbability;
	}

	/** Completion as a stable number fit to put in front of someone. See the field's own note. */
	public double getDisplayCompletionProbability()
	{
		return displayBuyFillProbability > 0
			? displayBuyFillProbability * sellFillProbability
			: getCompletionProbability();
	}

	/** Expected slot occupancy, kept under the old name for callers that only want a duration. */
	public double getSlotHours()
	{
		return expectedSlotHours();
	}

	public int getItemId() { return itemId; }
	// Gson sets these fields directly and skips the constructor, so the defaulting it does never
	// runs on the plugin side. PortfolioOptimizer calls isEmpty() on the group.
	public String getItemName() { return itemName == null ? "" : itemName; }
	public String getGroup() { return group == null ? "" : group; }
	public int getBuyPrice() { return targetBuyPrice; }
	public int getSellPrice() { return targetSellPrice; }
	public int getTargetBuyPrice() { return targetBuyPrice; }
	public int getEquilibriumBuyPrice() { return equilibriumBuyPrice; }
	public int getExitBuyPrice() { return exitBuyPrice; }
	public int getTargetSellPrice() { return targetSellPrice; }
	public int getEquilibriumSellPrice() { return equilibriumSellPrice; }
	public int getExitSellPrice() { return exitSellPrice; }
	public int getQuantity() { return quantity; }
	public int getBuyLimitRemaining() { return buyLimitRemaining; }
	public long getCapitalRequired() { return capitalRequired; }
	public long getNetProfit() { return netProfit; }
	public long getWorstLoss() { return worstLoss; }
	public long getUnwindLoss() { return unwindLoss; }
	public double getBuyFillProbability() { return buyFillProbability; }
	public double getSellFillProbability() { return sellFillProbability; }
	public double getBuyHours() { return buyHours; }
	public double getSellHours() { return sellHours; }
	public double getHorizonHours() { return horizonHours; }
	public long getExpiresAt() { return expiresAt; }

	/** Which factor actually decided the order size: the buy side, the sell side, coins, or the limit. */

	private static double clamp(double value)
	{
		return Math.max(0, Math.min(1, Double.isFinite(value) ? value : 0));
	}

	private static double finite(double value)
	{
		return Double.isFinite(value) ? value : 0;
	}
}
