package com.flippingfriend.data;

import java.time.Instant;

/**
 * One entry from {@code /latest}.
 * <p>
 * The naming in the wiki feed trips people up, so to be explicit: {@code high} is the price of the
 * most recent <em>instant buy</em>, which is the price you can realistically <em>sell</em> into.
 * {@code low} is the most recent <em>instant sell</em>, which is the price you can realistically
 * <em>buy</em> at. So a flip is: buy near {@code low}, sell near {@code high}.
 */
public class LatestPrice
{
	private Integer high;
	private Long highTime;
	private Integer low;
	private Long lowTime;

	public LatestPrice()
	{
	}

	public LatestPrice(Integer high, Long highTime, Integer low, Long lowTime)
	{
		this.high = high;
		this.highTime = highTime;
		this.low = low;
		this.lowTime = lowTime;
	}

	/** Price the item last sold for to a buyer in a hurry; our realistic sell price. */
	public Integer getHigh()
	{
		return high;
	}

	/** Price the item was last dumped at by a seller in a hurry; our realistic buy price. */
	public Integer getLow()
	{
		return low;
	}

	public Long getHighTime()
	{
		return highTime;
	}

	public Long getLowTime()
	{
		return lowTime;
	}

	public boolean isComplete()
	{
		return high != null && low != null && high > 0 && low > 0;
	}

	/** Seconds since the last instant-buy, or {@link Long#MAX_VALUE} if there has never been one. */
	public long secondsSinceHigh(Instant now)
	{
		return highTime == null ? Long.MAX_VALUE : now.getEpochSecond() - highTime;
	}

	/** Seconds since the last instant-sell, or {@link Long#MAX_VALUE} if there has never been one. */
	public long secondsSinceLow(Instant now)
	{
		return lowTime == null ? Long.MAX_VALUE : now.getEpochSecond() - lowTime;
	}

	/**
	 * How stale the quieter side of the book is. A wide spread only means anything if both sides
	 * have traded recently; if one side is hours old the "margin" is imaginary.
	 */
	public long stalestSideSeconds(Instant now)
	{
		return Math.max(secondsSinceHigh(now), secondsSinceLow(now));
	}
}
