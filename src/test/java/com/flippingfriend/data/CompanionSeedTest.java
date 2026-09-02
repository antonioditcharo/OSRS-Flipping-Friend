package com.flippingfriend.data;

import com.flippingfriend.companion.CompanionClient;
import com.google.gson.Gson;
import java.nio.file.Path;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.Mockito;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

/**
 * Starting from the companion's feed instead of the internet.
 * <p>
 * Signing on used to mean re-downloading every price in the game, on one thread, behind a
 * four-thousand-item mapping file, before the plugin would say anything — and then sixty per-item
 * history requests one at a time before it would say anything <em>useful</em>. All of it was already
 * sitting in the companion, which runs whether or not the game is open and refreshes every sixty
 * seconds.
 * <p>
 * The contract worth pinning is that the shortcut is a shortcut: it must fill the same maps the wiki
 * would, parse the wiki's own shapes, and never be required. A companion that is not running has to
 * leave the plugin working exactly as it did.
 */
public class CompanionSeedTest
{
	private static final int MAGIC_LOGS = 1513;

	@Rule
	public final TemporaryFolder folder = new TemporaryFolder();

	private final WikiPriceClient wiki = Mockito.mock(WikiPriceClient.class);
	private final CompanionClient companion = Mockito.mock(CompanionClient.class);

	private MarketDataService serviceAt(Path root)
	{
		return new MarketDataService(wiki, new Gson(), TestStorage.rootedAt(root, "p"));
	}

	private static CompanionClient.MarketFeed feed()
	{
		// Built through Gson because the fields are private and this is exactly how one arrives over
		// the wire -- so the test exercises the same construction path the plugin does.
		return new Gson().fromJson("{"
			+ "\"observedAt\":1788081274,"
			+ "\"latest\":{\"1513\":{\"high\":1200,\"highTime\":1,\"low\":1000,\"lowTime\":2}},"
			+ "\"fiveMinute\":{\"1513\":{\"avgHighPrice\":1190,\"avgLowPrice\":1010,"
			+ "\"highPriceVolume\":40,\"lowPriceVolume\":35}},"
			+ "\"hourly\":{\"1513\":{\"avgHighPrice\":1180,\"avgLowPrice\":1020,"
			+ "\"highPriceVolume\":400,\"lowPriceVolume\":350}}}",
			CompanionClient.MarketFeed.class);
	}

	@Test
	public void pricesArriveFromTheCompanionWithoutTouchingTheWiki() throws Exception
	{
		MarketDataService service = serviceAt(folder.newFolder("warm").toPath());
		Mockito.when(companion.fetchMarketFeed()).thenReturn(feed());
		service.setCompanion(companion);

		service.seedFromCompanion();

		LatestPrice price = service.getSnapshot().latest(MAGIC_LOGS);
		assertNotNull("the seed must fill the same map a poll would", price);
		assertEquals(Integer.valueOf(1200), price.getHigh());
		assertEquals(Integer.valueOf(1000), price.getLow());

		Candle five = service.getSnapshot().fiveMinute(MAGIC_LOGS);
		assertNotNull("and the averages too", five);
		assertEquals(Integer.valueOf(1190), five.getAvgHighPrice());

		Mockito.verifyNoInteractions(wiki);
	}

	@Test
	public void noCompanionMeansNoChangeAtAll()
	{
		// The whole thing is optional. Without a companion the plugin has to behave exactly as it did
		// before any of this existed.
		MarketDataService service = serviceAt(folder.getRoot().toPath());

		service.seedFromCompanion();

		assertNull(service.getSnapshot().latest(MAGIC_LOGS));
	}

	@Test
	public void aCompanionThatCannotAnswerIsNotAnError() throws Exception
	{
		MarketDataService service = serviceAt(folder.newFolder("down").toPath());
		Mockito.when(companion.fetchMarketFeed()).thenReturn(null);
		service.setCompanion(companion);

		service.seedFromCompanion();

		assertNull("a companion that is not running must cost nothing but speed",
			service.getSnapshot().latest(MAGIC_LOGS));
	}

	@Test
	public void oneMalformedRowDoesNotCostTheWholeFeed() throws Exception
	{
		MarketDataService service = serviceAt(folder.newFolder("partial").toPath());
		// A key that is not an item id. One bad row must not be the difference between a warm start
		// and a cold one.
		Mockito.when(companion.fetchMarketFeed()).thenReturn(new Gson().fromJson("{"
			+ "\"observedAt\":1,"
			+ "\"latest\":{\"1513\":{\"high\":1200,\"low\":1000},"
			+ "\"not-a-number\":{\"high\":5,\"low\":4}}}",
			CompanionClient.MarketFeed.class));
		service.setCompanion(companion);

		service.seedFromCompanion();

		assertNotNull("a bad key must be skipped, not abort the seed",
			service.getSnapshot().latest(MAGIC_LOGS));
	}
}
