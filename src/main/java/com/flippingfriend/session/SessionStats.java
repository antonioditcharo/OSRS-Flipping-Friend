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

	public SessionStats(int flips, int wins, long profit, long taxPaid, double totalMinutesHeld,
		long elapsedSeconds, java.util.Map<com.flippingfriend.model.MarketSector, Long> sectorProfits)
	{
		this.flips = flips;
		this.wins = wins;
		this.profit = profit;
		this.taxPaid = taxPaid;
		this.totalMinutesHeld = totalMinutesHeld;
		this.elapsedSeconds = elapsedSeconds;
		this.sectorProfits = sectorProfits;
	}

	public static SessionStats empty()
	{
		return new SessionStats(0, 0, 0, 0, 0, 0, java.util.Collections.emptyMap());
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
}
