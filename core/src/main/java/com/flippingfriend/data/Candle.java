package com.flippingfriend.data;

/**
 * One time-bucket from {@code /5m}, {@code /1h} or {@code /timeseries}: the volume-weighted average
 * instant-buy and instant-sell price over the bucket, plus how many items changed hands on each side.
 * <p>
 * Any of the price fields can be null when nothing traded on that side during the bucket, which is
 * itself a useful liquidity signal rather than a defect.
 */
public class Candle
{
	private long timestamp;
	private Integer avgHighPrice;
	private Integer avgLowPrice;
	private int highPriceVolume;
	private int lowPriceVolume;

	public Candle()
	{
	}

	public Candle(long timestamp, Integer avgHighPrice, Integer avgLowPrice, int highPriceVolume, int lowPriceVolume)
	{
		this.timestamp = timestamp;
		this.avgHighPrice = avgHighPrice;
		this.avgLowPrice = avgLowPrice;
		this.highPriceVolume = highPriceVolume;
		this.lowPriceVolume = lowPriceVolume;
	}

	public long getTimestamp()
	{
		return timestamp;
	}

	public void setTimestamp(long timestamp)
	{
		this.timestamp = timestamp;
	}

	public Integer getAvgHighPrice()
	{
		return avgHighPrice;
	}

	public Integer getAvgLowPrice()
	{
		return avgLowPrice;
	}

	public int getHighPriceVolume()
	{
		return highPriceVolume;
	}

	public int getLowPriceVolume()
	{
		return lowPriceVolume;
	}

	public int getTotalVolume()
	{
		return highPriceVolume + lowPriceVolume;
	}

	public boolean hasBothSides()
	{
		return avgHighPrice != null && avgLowPrice != null && avgHighPrice > 0 && avgLowPrice > 0;
	}

	/**
	 * Best single price for the bucket. Prefers the midpoint when both sides traded, otherwise
	 * falls back to whichever side did, and returns 0 when the bucket is empty.
	 */
	public double midPrice()
	{
		if (avgHighPrice != null && avgLowPrice != null)
		{
			return (avgHighPrice + avgLowPrice) / 2.0;
		}
		if (avgHighPrice != null)
		{
			return avgHighPrice;
		}
		if (avgLowPrice != null)
		{
			return avgLowPrice;
		}
		return 0;
	}
}
