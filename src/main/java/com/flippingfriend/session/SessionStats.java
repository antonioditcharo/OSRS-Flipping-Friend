package com.flippingfriend.session;

/**
 * Headline numbers for the panel. Profit is always net of Grand Exchange tax, because a gross
 * figure would flatter every trade by exactly the amount the plugin exists to account for.
 */
public class SessionStats
{
	private final int flips;
	private final int wins;
	private final long profit;
	private final long taxPaid;
	private final double totalMinutesHeld;
	private final long elapsedSeconds;
	private final java.util.Map<com.flippingfriend.model.MarketSector, Long> sectorProfits;
	private final long cost;

	public SessionStats(int flips, int wins, long profit, long taxPaid, double totalMinutesHeld,
		long elapsedSeconds, java.util.Map<com.flippingfriend.model.MarketSector, Long> sectorProfits, long cost)
	{
		this.flips = flips;
		this.wins = wins;
		this.profit = profit;
		this.taxPaid = taxPaid;
		this.totalMinutesHeld = totalMinutesHeld;
		this.elapsedSeconds = elapsedSeconds;
		this.sectorProfits = sectorProfits;
		this.cost = cost;
	}

	public static SessionStats empty()
	{
		return new SessionStats(0, 0, 0, 0, 0, 0, java.util.Collections.emptyMap(), 0);
	}

	public int getFlips()
	{
		return flips;
	}

	public int getWins()
	{
		return wins;
	}

	public long getProfit()
	{
		return profit;
	}

	public long getTaxPaid()
	{
		return taxPaid;
	}

	public long getElapsedSeconds()
	{
		return elapsedSeconds;
	}

	public double getWinRate()
	{
		return flips == 0 ? 0 : (double) wins / flips;
	}

	public double getAverageHoldMinutes()
	{
		return flips == 0 ? 0 : totalMinutesHeld / flips;
	}

	/** Profit per hour measured against wall-clock time since the session started, not time in trade. */
	public long getProfitPerHour()
	{
		if (elapsedSeconds < 60)
		{
			return 0;
		}
		return (long) (profit * 3600.0 / elapsedSeconds);
	}

	public java.util.Map<com.flippingfriend.model.MarketSector, Long> getSectorProfits()
	{
		return sectorProfits;
	}

	public long getCost()
	{
		return cost;
	}

	public double getRoi()
	{
		return cost == 0 ? 0 : (double) profit / cost;
	}

	public SessionStats withOngoing(long ongoingProfit, long ongoingTax, long ongoingCost, java.util.Map<com.flippingfriend.model.MarketSector, Long> ongoingSectorProfits)
	{
		java.util.Map<com.flippingfriend.model.MarketSector, Long> mergedSectorProfits = new java.util.HashMap<>(this.sectorProfits);
		for (java.util.Map.Entry<com.flippingfriend.model.MarketSector, Long> entry : ongoingSectorProfits.entrySet())
		{
			mergedSectorProfits.put(entry.getKey(), mergedSectorProfits.getOrDefault(entry.getKey(), 0L) + entry.getValue());
		}

		return new SessionStats(
			this.flips,
			this.wins,
			this.profit + ongoingProfit,
			this.taxPaid + ongoingTax,
			this.totalMinutesHeld,
			this.elapsedSeconds,
			mergedSectorProfits,
			this.cost + ongoingCost
		);
	}
}
