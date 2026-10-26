package com.flippingfriend.data;

import java.time.Instant;
import java.util.Collections;
import java.util.Map;

/**
 * A consistent, immutable view of the market at one moment.
 * <p>
 * The engine reads a whole snapshot rather than the individual maps so that a poll landing halfway
 * through a scoring pass cannot produce a suggestion built from a 5-minute average of one item and
 * a spot price of another, five minutes apart.
 */
public class MarketSnapshot
{
	private static final MarketSnapshot EMPTY = new MarketSnapshot(
		Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap(),
		Collections.emptyMap(), Instant.EPOCH);

	private final Map<Integer, ItemMetadata> metadata;
	private final Map<Integer, LatestPrice> latest;
	private final Map<Integer, Candle> fiveMinute;
	private final Map<Integer, Candle> hourly;
	private final Instant capturedAt;
    private final long latestFeedObservedAt;

	public MarketSnapshot(Map<Integer, ItemMetadata> metadata, Map<Integer, LatestPrice> latest,
		Map<Integer, Candle> fiveMinute, Map<Integer, Candle> hourly, Instant capturedAt)
	{
        this(metadata,latest,fiveMinute,hourly,capturedAt,capturedAt==null?0:capturedAt.getEpochSecond());
    }
    public MarketSnapshot(Map<Integer,ItemMetadata> metadata,Map<Integer,LatestPrice> latest,
        Map<Integer,Candle> fiveMinute,Map<Integer,Candle> hourly,Instant capturedAt,long latestFeedObservedAt) {
		this.metadata = Collections.unmodifiableMap(new java.util.LinkedHashMap<>(metadata));
		this.latest = Collections.unmodifiableMap(new java.util.LinkedHashMap<>(latest));
		this.fiveMinute = Collections.unmodifiableMap(new java.util.LinkedHashMap<>(fiveMinute));
		this.hourly = Collections.unmodifiableMap(new java.util.LinkedHashMap<>(hourly));
		this.capturedAt = capturedAt;
        this.latestFeedObservedAt=latestFeedObservedAt;
	}
    /** Actual latest-feed receipt/publication clock; other feed refreshes cannot renew it. */
    public long getLatestFeedObservedAt(){return latestFeedObservedAt;}

	public static MarketSnapshot empty()
	{
		return EMPTY;
	}

	public Map<Integer, ItemMetadata> getMetadata()
	{
		return metadata;
	}

	public Map<Integer, LatestPrice> getLatest()
	{
		return latest;
	}

	public Map<Integer, Candle> getFiveMinute()
	{
		return fiveMinute;
	}

	public Map<Integer, Candle> getHourly()
	{
		return hourly;
	}

	public Instant getCapturedAt()
	{
		return capturedAt;
	}

	public ItemMetadata metadata(int itemId)
	{
		return metadata.get(itemId);
	}

	public LatestPrice latest(int itemId)
	{
		return latest.get(itemId);
	}

	public Candle fiveMinute(int itemId)
	{
		return fiveMinute.get(itemId);
	}

	public Candle hourly(int itemId)
	{
		return hourly.get(itemId);
	}

	/**
	 * True once there is enough loaded to score anything. Prices without metadata are useless
	 * because we would not know the buy limit.
	 */
	public boolean isUsable()
	{
		return !metadata.isEmpty() && !latest.isEmpty();
	}

	public String getItemName(int itemId)
	{
		ItemMetadata meta = metadata.get(itemId);
		return meta == null ? "Item " + itemId : meta.getName();
	}
}
