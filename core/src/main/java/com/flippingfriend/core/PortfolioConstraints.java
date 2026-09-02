package com.flippingfriend.core;

/** Per-plan budget, exposure and safety constraints. */
public final class PortfolioConstraints
{
	private final int freeSlots;
	private final long freeCoins;
	private final long sessionLossBudget;
	private final long perItemCapitalCap;
	private final long perGroupCapitalCap;

	/** Capital already committed per item and per correlated group, before this plan adds any. */
	private final java.util.Map<Integer, Long> committedByItem;
	private final java.util.Map<String, Long> committedByGroup;
	/** The player's floor on what a flip is worth doing. */
	private final long minProfitPerFlip;

	public java.util.Map<Integer, Long> getCommittedByItem()
	{
		return committedByItem == null ? java.util.Collections.emptyMap() : committedByItem;
	}

	public java.util.Map<String, Long> getCommittedByGroup()
	{
		return committedByGroup == null ? java.util.Collections.emptyMap() : committedByGroup;
	}

	public long getMinProfitPerFlip() { return minProfitPerFlip; }

	public PortfolioConstraints withCommitted(java.util.Map<Integer, Long> byItem,
		java.util.Map<String, Long> byGroup, long minProfit)
	{
		return new PortfolioConstraints(freeSlots, freeCoins, sessionLossBudget, perItemCapitalCap,
			perGroupCapitalCap, byItem, byGroup, minProfit);
	}

	public PortfolioConstraints(int freeSlots, long freeCoins, long sessionLossBudget,
		long perItemCapitalCap, long perGroupCapitalCap, java.util.Map<Integer, Long> committedByItem,
		java.util.Map<String, Long> committedByGroup, long minProfitPerFlip)
	{
		this.freeSlots = Math.max(0, freeSlots);
		this.freeCoins = Math.max(0, freeCoins);
		this.sessionLossBudget = Math.max(0, sessionLossBudget);
		this.perItemCapitalCap = Math.max(0, perItemCapitalCap);
		this.perGroupCapitalCap = Math.max(0, perGroupCapitalCap);
		this.committedByItem = committedByItem;
		this.committedByGroup = committedByGroup;
		this.minProfitPerFlip = Math.max(0, minProfitPerFlip);
	}

	public PortfolioConstraints(int freeSlots, long freeCoins, long sessionLossBudget,
		long perItemCapitalCap, long perGroupCapitalCap)
	{
		this(freeSlots, freeCoins, sessionLossBudget, perItemCapitalCap, perGroupCapitalCap,
			null, null, 0);
	}

	/**
	 * The same limits with a different number of slots to fill.
	 * <p>
	 * Used to rank a full board for measurement without disturbing the constraints that decide what is
	 * actually offered — every other limit, and everything already committed, stays exactly as it is.
	 */
	public PortfolioConstraints withSlots(int slots)
	{
		return new PortfolioConstraints(slots, freeCoins, sessionLossBudget, perItemCapitalCap,
			perGroupCapitalCap, committedByItem, committedByGroup, minProfitPerFlip);
	}

	/**
	 * The same limits with a different purse.
	 * <p>
	 * Used with {@link #withSlots} to rank the measurement board against the whole bankroll rather
	 * than the part of it that happens to be uncommitted. Ranking the board on free coins made it
	 * shrink as the player put money to work, so the chart that claims to measure the planner was
	 * partly measuring how busy the account was -- the exact fault the board was introduced to fix,
	 * left half-done because only the slot count was swapped.
	 */
	public PortfolioConstraints withCoins(long coins)
	{
		return new PortfolioConstraints(freeSlots, coins, sessionLossBudget, perItemCapitalCap,
			perGroupCapitalCap, committedByItem, committedByGroup, minProfitPerFlip);
	}

	public int getFreeSlots() { return freeSlots; }
	public long getFreeCoins() { return freeCoins; }
	public long getSessionLossBudget() { return sessionLossBudget; }
	public long getPerItemCapitalCap() { return perItemCapitalCap; }
	public long getPerGroupCapitalCap() { return perGroupCapitalCap; }
}
