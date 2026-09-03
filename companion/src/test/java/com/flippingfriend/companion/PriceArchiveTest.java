package com.flippingfriend.companion;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.flippingfriend.data.Candle;
import com.google.gson.JsonObject;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Covers the archive that keeps the history the wiki does not.
 *
 * <p>The reader is tested first and hardest, because a store nothing reads is how this project
 * previously accumulated a 2.5 GB table and then deleted it. {@link PriceArchive#asSeriesSource} is
 * the whole justification for writing anything down: it hands archived history to the replay
 * harness and the shadow trader through the interface they already speak.
 */
public class PriceArchiveTest
{
	private static final int ITEM = 4151;
	private static final long HOUR = 1_700_000_000L / 3600 * 3600;

	private Connection connection;
	private PriceArchive archive;

	@Before
	public void setUp() throws Exception
	{
		connection = DriverManager.getConnection("jdbc:sqlite::memory:");
		archive = new PriceArchive(connection);
	}

	@After
	public void tearDown() throws Exception
	{
		connection.close();
	}

	/** One poll's payload: the shape the wiki's /5m and /1h `data` object has. */
	private static JsonObject payload(int itemId, Integer high, Integer low, int highVol, int lowVol)
	{
		JsonObject bar = new JsonObject();
		if (high == null)
		{
			bar.add("avgHighPrice", com.google.gson.JsonNull.INSTANCE);
		}
		else
		{
			bar.addProperty("avgHighPrice", high);
		}
		if (low == null)
		{
			bar.add("avgLowPrice", com.google.gson.JsonNull.INSTANCE);
		}
		else
		{
			bar.addProperty("avgLowPrice", low);
		}
		bar.addProperty("highPriceVolume", highVol);
		bar.addProperty("lowPriceVolume", lowVol);

		JsonObject data = new JsonObject();
		data.add(String.valueOf(itemId), bar);
		return data;
	}

	@Test
	public void barsComeBackThroughTheInterfaceEverythingElseAlreadySpeaks() throws Exception
	{
		archive.record(payload(ITEM, 1_010, 1_000, 500, 400), PriceArchive.FIVE_MINUTE, HOUR);

		SeriesSource source = archive.asSeriesSource(HOUR - 3600, HOUR + 3600);
		List<Candle> bars = source.series(ITEM, PriceArchive.FIVE_MINUTE);

		assertEquals(1, bars.size());
		assertEquals(HOUR, bars.get(0).getTimestamp());
		assertEquals(Integer.valueOf(1_010), bars.get(0).getAvgHighPrice());
		assertEquals(Integer.valueOf(1_000), bars.get(0).getAvgLowPrice());
		assertEquals(500, bars.get(0).getHighPriceVolume());
	}

	/**
	 * Polling every sixty seconds for a bucket that changes every five minutes must cost nothing, and
	 * a replayed poll must not double-count.
	 */
	@Test
	public void writesAreIdempotent() throws Exception
	{
		JsonObject data = payload(ITEM, 1_010, 1_000, 500, 400);
		assertEquals("first poll writes", 1, archive.record(data, PriceArchive.FIVE_MINUTE, HOUR));
		assertEquals("the same bucket again writes nothing",
			0, archive.record(data, PriceArchive.FIVE_MINUTE, HOUR));
		assertEquals(1, archive.rowCount());
	}

	/** A side that did not trade is absent, not zero — the two mean different things. */
	@Test
	public void anUntradedSideStaysNullRatherThanBecomingZero() throws Exception
	{
		archive.record(payload(ITEM, null, 1_000, 0, 400), PriceArchive.FIVE_MINUTE, HOUR);

		Candle bar = archive.series(ITEM, PriceArchive.FIVE_MINUTE, 0, Long.MAX_VALUE).get(0);
		assertNull("no instant-buy happened, which is a liquidity signal not a price of zero",
			bar.getAvgHighPrice());
		assertEquals(Integer.valueOf(1_000), bar.getAvgLowPrice());
	}

	/**
	 * The rolled-up bar must mean the same thing as one fetched from {@code /1h}: volume-weighted,
	 * not a plain mean, which would overweight the quiet buckets.
	 */
	@Test
	public void rollUpIsVolumeWeighted() throws Exception
	{
		// Two buckets in one hour: a big one at 1,000 and a small one at 2,000.
		archive.record(payload(ITEM, 1_000, 1_000, 900, 900), PriceArchive.FIVE_MINUTE, HOUR);
		archive.record(payload(ITEM, 2_000, 2_000, 100, 100), PriceArchive.FIVE_MINUTE, HOUR + 300);

		assertEquals("both fine bars retired", 2, archive.rollUp(HOUR + 3600));

		List<Candle> hourly = archive.series(ITEM, PriceArchive.HOURLY, 0, Long.MAX_VALUE);
		assertEquals(1, hourly.size());
		assertEquals("a plain mean would say 1,500",
			Integer.valueOf(1_100), hourly.get(0).getAvgHighPrice());
		assertEquals("volumes sum", 1_000, hourly.get(0).getHighPriceVolume());
		assertEquals("and the fine rows are gone",
			0, archive.series(ITEM, PriceArchive.FIVE_MINUTE, 0, Long.MAX_VALUE).size());
	}

	@Test
	public void rollUpLeavesRecentHistoryAtFullResolution() throws Exception
	{
		archive.record(payload(ITEM, 1_000, 990, 100, 100), PriceArchive.FIVE_MINUTE, HOUR);
		archive.record(payload(ITEM, 1_020, 1_010, 100, 100), PriceArchive.FIVE_MINUTE, HOUR + 7200);

		assertEquals("only the old bucket is retired", 1, archive.rollUp(HOUR + 3600));
		assertEquals("the recent one keeps five-minute resolution",
			1, archive.series(ITEM, PriceArchive.FIVE_MINUTE, 0, Long.MAX_VALUE).size());
	}

	/** An hour where one side never traded must roll up to null, not to zero. */
	@Test
	public void anHourWithNoTradesOnOneSideRollsUpToNull() throws Exception
	{
		archive.record(payload(ITEM, null, 1_000, 0, 400), PriceArchive.FIVE_MINUTE, HOUR);
		archive.rollUp(HOUR + 3600);

		Candle hourly = archive.series(ITEM, PriceArchive.HOURLY, 0, Long.MAX_VALUE).get(0);
		assertNull(hourly.getAvgHighPrice());
		assertEquals(Integer.valueOf(1_000), hourly.getAvgLowPrice());
	}

	@Test
	public void aFailedReadIsAnEmptyHistoryRatherThanAThrow() throws Exception
	{
		SeriesSource source = archive.asSeriesSource(0, Long.MAX_VALUE);
		connection.close();

		assertTrue("a storage problem must not take down a planning pass",
			source.series(ITEM, PriceArchive.FIVE_MINUTE).isEmpty());
	}

	@Test
	public void reportsHowMuchHistoryHasAccumulated() throws Exception
	{
		assertEquals(0, archive.earliest());
		archive.record(payload(ITEM, 1_010, 1_000, 500, 400), PriceArchive.FIVE_MINUTE, HOUR + 3600);
		archive.record(payload(ITEM, 1_010, 1_000, 500, 400), PriceArchive.FIVE_MINUTE, HOUR);

		assertEquals("the oldest bar is what says how far back we can see", HOUR, archive.earliest());
		assertEquals(2, archive.rowCount());
	}

	@Test
	public void malformedEntriesAreSkippedNotFatal() throws Exception
	{
		JsonObject data = payload(ITEM, 1_010, 1_000, 500, 400);
		data.addProperty("not-an-item-id", 1);
		data.add("99999", new com.google.gson.JsonArray());

		assertEquals("only the real bar is written",
			1, archive.record(data, PriceArchive.FIVE_MINUTE, HOUR));
	}
}
