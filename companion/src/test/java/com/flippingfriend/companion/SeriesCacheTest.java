package com.flippingfriend.companion;

import com.flippingfriend.data.Candle;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The contract that lets the shortlist be hundreds of items instead of forty-five: reading history
 * never touches the network.
 * <p>
 * It is worth pinning because the old behaviour was perfectly reasonable in isolation — fetch on a
 * cache miss — and the cost of it was invisible from the call site. Every extra item on the
 * shortlist added a throttled request to the planning cycle, so the number of items the plugin could
 * consider was silently governed by how long a cycle was allowed to run. If a future change puts a
 * fetch back inside {@code series}, nothing will look broken; the plugin will just quietly get
 * narrower again.
 */
public class SeriesCacheTest
{
    @Test public void oneMalformedCachedHistoryCannotClearTheOtherWarmItems()throws Exception {
        Path file=folder.newFolder("mixed-numeric").toPath().resolve("series-cache.json.gz");
        var expected=history();var before=new SeriesCache(file);before.remember(1,"5m",expected,Instant.now());before.remember(2,"5m",expected,Instant.now());before.save();
        com.google.gson.JsonObject root;
        try(var in=new java.util.zip.GZIPInputStream(Files.newInputStream(file))) {
            root=com.google.gson.JsonParser.parseString(new String(in.readAllBytes(),StandardCharsets.UTF_8)).getAsJsonObject();
        }
        for(var element:root.getAsJsonArray("entries"))if(element.getAsJsonObject().get("key").getAsString().equals("1@5m"))
            element.getAsJsonObject().getAsJsonArray("data").get(0).getAsJsonObject().addProperty("avgLowPrice",4_294_967_396L);
        try(var out=new java.util.zip.GZIPOutputStream(Files.newOutputStream(file))){out.write(root.toString().getBytes(StandardCharsets.UTF_8));}
        byte[] source=Files.readAllBytes(file);var after=new SeriesCache(file);
        assertTrue(after.series(1,"5m").isEmpty());assertSameHistory(expected,after.series(2,"5m"));
        assertTrue(Arrays.equals(source,Files.readAllBytes(file)));assertEquals(1,after.warmCount("5m"));
    }
    @Test public void maintenanceBudgetVisitsUnfetchedThenOldestItemsInsteadOfRepeatingItsFirstPage()throws Exception {
        var client=org.mockito.Mockito.mock(okhttp3.OkHttpClient.class);var call=org.mockito.Mockito.mock(okhttp3.Call.class);
        var requested=new ArrayList<String>();
        var request=new okhttp3.Request.Builder().url("https://example.invalid/history").build();
        org.mockito.Mockito.when(client.newCall(org.mockito.ArgumentMatchers.any())).thenAnswer(c->{
            okhttp3.Request next=c.getArgument(0);requested.add(next.url().queryParameter("id"));return call;
        });
        org.mockito.Mockito.when(call.execute()).thenAnswer(c->new okhttp3.Response.Builder().request(request)
            .protocol(okhttp3.Protocol.HTTP_1_1).code(200).message("OK")
            .body(okhttp3.ResponseBody.create("{\"data\":[]}",okhttp3.MediaType.get("application/json"))).build());
        var cache=new SeriesCache(null,client);cache.remember(1,"5m",history(),Instant.now().minusSeconds(180));
        cache.remember(2,"5m",history(),Instant.now().minusSeconds(900));
        assertEquals(1,cache.warmMaintenance(List.of(1,2,3,3),1));
        assertEquals(1,cache.warmMaintenance(List.of(1,2,3),1));
        assertEquals(List.of("3","2"),requested);
    }
    @Test public void heldStockRefreshesBeforeEntryCacheExpiryWithoutFetchingOnReadsOrRetryingFailures()throws Exception {
        var client=org.mockito.Mockito.mock(okhttp3.OkHttpClient.class);
        var call=org.mockito.Mockito.mock(okhttp3.Call.class);
        var request=new okhttp3.Request.Builder().url("https://example.invalid/history").build();
        org.mockito.Mockito.when(client.newCall(org.mockito.ArgumentMatchers.any())).thenReturn(call);
        org.mockito.Mockito.when(call.execute()).thenAnswer(invocation->new okhttp3.Response.Builder().request(request)
            .protocol(okhttp3.Protocol.HTTP_1_1).code(200).message("OK")
            .body(okhttp3.ResponseBody.create("{\"data\":[]}",okhttp3.MediaType.get("application/json"))).build());
        var cache=new SeriesCache(null,client);
        cache.remember(11230,"5m",history(),Instant.now().minusSeconds(180));
        assertEquals(0,cache.warm(List.of(11230),"5m",1));
        assertFalse(cache.series(11230,"5m").isEmpty());
        org.mockito.Mockito.verifyNoInteractions(client);
        assertEquals(1,cache.warmMaintenance(List.of(11230),1));
        assertEquals(0,cache.warmMaintenance(List.of(11230),1));
        org.mockito.Mockito.verify(client,org.mockito.Mockito.times(1)).newCall(org.mockito.ArgumentMatchers.any());
        org.mockito.Mockito.when(call.execute()).thenThrow(new java.io.IOException("offline"));
        cache.remember(11230,"5m",history(),Instant.now().minusSeconds(180));
        assertEquals(1,cache.warmMaintenance(List.of(11230),1));
        assertEquals(0,cache.warmMaintenance(List.of(11230),1));
    }
	@Rule
	public final TemporaryFolder folder = new TemporaryFolder();

	private SeriesCache cache() throws Exception
	{
		return new SeriesCache();
	}

	private static List<Candle> history()
	{
		List<Candle> candles = new ArrayList<>();
		long start = Instant.now().getEpochSecond() - 3600;
		for (int i = 0; i < 12; i++)
		{
			// A null on one side is normal -- an item with no sale in the bucket reports nothing --
			// and it is the case a sloppy serialiser turns into a zero, which reads as a free item.
			candles.add(new Candle(start + i * 300L, i == 3 ? null : 1000 + i, 990 + i, 40, 35));
		}
		return candles;
	}

	private static void assertSameHistory(List<Candle> expected, List<Candle> actual)
	{
		assertEquals("the number of bars must survive", expected.size(), actual.size());
		for (int i = 0; i < expected.size(); i++)
		{
			Candle want = expected.get(i);
			Candle got = actual.get(i);
			assertEquals("bar " + i + " timestamp", want.getTimestamp(), got.getTimestamp());
			assertEquals("bar " + i + " high", want.getAvgHighPrice(), got.getAvgHighPrice());
			assertEquals("bar " + i + " low", want.getAvgLowPrice(), got.getAvgLowPrice());
			assertEquals("bar " + i + " high volume", want.getHighPriceVolume(), got.getHighPriceVolume());
			assertEquals("bar " + i + " low volume", want.getLowPriceVolume(), got.getLowPriceVolume());
		}
	}

	/**
	 * The point of persisting at all: a restart inside the cache lifetime plans immediately instead
	 * of spending its first minutes re-fetching what it already had. Held only in memory, every
	 * restart threw away up to three hours of collected history, and while it was being refetched
	 * every candidate was vetoed for want of it -- which is what "checking the market" for minutes
	 * on end actually was.
	 */
	@Test
	public void historySurvivesARestart() throws Exception
	{
		Path file = folder.newFolder("warm").toPath().resolve("series-cache.json.gz");
		List<Candle> history = history();

		SeriesCache before = new SeriesCache(file);
		before.remember(4151, "5m", history, Instant.now());
		before.save();

		assertTrue("the cache should have been written", Files.isRegularFile(file));

		SeriesCache after = new SeriesCache(file);
		assertSameHistory(history, after.series(4151, "5m"));
		assertEquals("and it must count as warm, so nothing re-fetches it", 1, after.warmCount("5m"));
	}

	/**
	 * The limit on the above. A cache is allowed to make a restart faster, never to make it decide on
	 * older prices than it would have accepted while running.
	 */
	@Test
	public void historyPastItsLifetimeIsNotRestored() throws Exception
	{
		Path file = folder.newFolder("stale").toPath().resolve("series-cache.json.gz");

		SeriesCache before = new SeriesCache(file);
		// The five-minute series goes stale in twenty minutes. This was fetched four hours ago.
		before.remember(4151, "5m", history(), Instant.now().minusSeconds(4 * 3600));
		before.remember(4152, "5m", history(), Instant.now());
		before.save();

		SeriesCache after = new SeriesCache(file);
		assertTrue("history older than its own lifetime must not come back",
			after.series(4151, "5m").isEmpty());
		assertFalse("while history still inside it must", after.series(4152, "5m").isEmpty());
	}

	/** An empty result is a fetch that told us nothing. Keeping it would only hide a real gap. */
	@Test
	public void anEmptyResultIsNotPersisted() throws Exception
	{
		Path file = folder.newFolder("empty").toPath().resolve("series-cache.json.gz");

		SeriesCache before = new SeriesCache(file);
		before.remember(4151, "5m", Collections.emptyList(), Instant.now());
		before.save();

		assertTrue(new SeriesCache(file).series(4151, "5m").isEmpty());
	}

	/**
	 * A cache is rebuildable by definition, so anything wrong with the file must cost a cold start
	 * and nothing more. A companion that refuses to boot because a cache file was truncated by a bad
	 * shutdown would be a far worse failure than the one being fixed.
	 */
	@Test
	public void anUnreadableCacheStartsColdRatherThanFailing() throws Exception
	{
		Path file = folder.newFolder("broken").toPath().resolve("series-cache.json.gz");
		Files.write(file, "this is not a gzip stream".getBytes(StandardCharsets.UTF_8));

		SeriesCache cache = new SeriesCache(file);

		assertTrue("a damaged cache must read as empty, not throw", cache.series(4151, "5m").isEmpty());
	}

	/** A missing file is the ordinary first run, not an error. */
	@Test
	public void aMissingCacheStartsCold() throws Exception
	{
		Path file = folder.newFolder("first-run").toPath().resolve("series-cache.json.gz");

		assertTrue(new SeriesCache(file).series(4151, "5m").isEmpty());
	}

	@Test
	public void readingUncachedHistoryReturnsEmptyRatherThanFetching() throws Exception
	{
		SeriesCache cache = cache();

		// 4151 is a real item, so a fetch-on-miss implementation would return bars here. Returning
		// nothing is the whole point: planning takes what has been gathered and moves on.
		long before = System.nanoTime();
		assertTrue("an uncached read must not fetch", cache.series(4151, "5m").isEmpty());
		long elapsedMillis = (System.nanoTime() - before) / 1_000_000;

		assertTrue("and must return immediately, not after a network round trip: took "
			+ elapsedMillis + "ms", elapsedMillis < 200);
	}

	@Test
	public void readingIsInstantAcrossAWholeShortlist() throws Exception
	{
		SeriesCache cache = cache();

		// Three hundred reads is a realistic planning cycle. Fetching even one of them would blow
		// this budget many times over, which is exactly the failure being guarded against.
		long before = System.nanoTime();
		for (int itemId = 1; itemId <= 300; itemId++)
		{
			cache.series(itemId, "5m");
		}
		long elapsedMillis = (System.nanoTime() - before) / 1_000_000;

		assertTrue("a full shortlist must be readable in well under a second: took "
			+ elapsedMillis + "ms", elapsedMillis < 500);
	}

	@Test
	public void warmingNothingSpendsNothing() throws Exception
	{
		assertEquals(0, cache().warm(Collections.emptyList(), "5m", 50));
	}

	@Test
	public void warmingRespectsItsBudget() throws Exception
	{
		SeriesCache cache = cache();

		// Item ids that cannot resolve, so each attempt fails fast and is cached as empty. What is
		// being checked is the budget, not the fetch: the warmer must stop when told to, or a cold
		// start would fire hundreds of requests at once.
		int spent = cache.warm(Arrays.asList(-1, -2, -3, -4, -5), "5m", 2);

		assertEquals("the warmer must stop at its budget", 2, spent);
	}

	@Test
	public void aFailedFetchIsNotRetriedImmediately() throws Exception
	{
		SeriesCache cache = cache();

		assertEquals(1, cache.warm(Collections.singletonList(-1), "5m", 5));
		assertEquals("a failing item is remembered as empty rather than retried every pass",
			0, cache.warm(Collections.singletonList(-1), "5m", 5));
	}
}
