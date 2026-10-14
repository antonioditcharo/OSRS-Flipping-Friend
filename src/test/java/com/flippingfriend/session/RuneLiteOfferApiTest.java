package com.flippingfriend.session;

import net.runelite.api.GrandExchangeOffer;
import net.runelite.api.GrandExchangeOfferState;
import com.flippingfriend.data.PluginStorage;
import com.flippingfriend.data.TestStorage;
import com.flippingfriend.model.TaxCalculator;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.Mockito;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** The observed 1.13.1 client and its hub plugins link against long GE monetary getters. */
public class RuneLiteOfferApiTest
{
	@Rule public final TemporaryFolder folder = new TemporaryFolder();

	private OfferTracker tracker(PluginStorage storage, PositionBook positions)
	{
		return new OfferTracker(storage, positions, new BuyLimitTracker(storage),
			new TradeJournal(storage, null, new AccountMonitor(null, null, null)),
			new TaxCalculator(), new TradePlans(storage));
	}

	private GrandExchangeOffer offer(long price, long spent, int filled)
	{
		GrandExchangeOffer result = Mockito.mock(GrandExchangeOffer.class);
		Mockito.when(result.getState()).thenReturn(GrandExchangeOfferState.BUYING);
		Mockito.when(result.getItemId()).thenReturn(1513);
		Mockito.when(result.getPrice()).thenReturn(price);
		Mockito.when(result.getSpent()).thenReturn(spent);
		Mockito.when(result.getTotalQuantity()).thenReturn(4);
		Mockito.when(result.getQuantitySold()).thenReturn(filled);
		return result;
	}
	@Test
	public void buildMatchesTheLiveLongMonetaryOfferApi() throws Exception
	{
		assertEquals("the old int signature cannot link to the current client",
			long.class, GrandExchangeOffer.class.getMethod("getPrice").getReturnType());
		assertEquals(long.class, GrandExchangeOffer.class.getMethod("getSpent").getReturnType());
	}

	@Test public void partialBuyPreservesSpentAboveTheOldIntegerLimit() throws Exception
	{
		PluginStorage storage = TestStorage.rootedAt(folder.newFolder().toPath(), "p");
		PositionBook positions = new PositionBook(storage);
		OfferTracker tracker = tracker(storage, positions);
		tracker.load();
		tracker.onOfferChanged(0, offer(1_000_000_000L, 3_000_000_000L, 3));
		assertEquals(3_000_000_000L, tracker.getOffer(0).getSpent());
		assertEquals(3_000_000_000L, tracker.getOffer(0).getRecordedSpent());
		assertEquals(3_000_000_000L, positions.get(1513).getTotalCost());
		tracker.onOfferChanged(0, offer(1_000_000_000L, 3_500_000_000L, 4));
		assertEquals(3_500_000_000L, positions.get(1513).getTotalCost());
		assertEquals(4, positions.get(1513).getQuantity());
	}

	@Test public void unsupportedUnitPricePreservesEvidenceAndPriorAccountingAcrossRestart() throws Exception
	{
		PluginStorage storage = TestStorage.rootedAt(folder.newFolder().toPath(), "p");
		PositionBook positions = new PositionBook(storage);
		OfferTracker tracker = tracker(storage, positions);
		tracker.load();
		tracker.onOfferChanged(0, offer(1000L, 1000L, 1));
		tracker.save();
		tracker.onOfferChanged(0, offer(3_000_000_000L, 3_000_000_000L, 1));
		assertEquals(1000L, tracker.getOffer(0).getPrice());
		assertEquals(1000L, positions.get(1513).getTotalCost());
		Path evidence = storage.accountDir().resolve("unsupported-offer-price.json");
		assertTrue(Files.isRegularFile(evidence));
		assertEquals(3_000_000_000L, new JsonParser().parse(Files.readString(evidence))
			.getAsJsonObject().get("price").getAsLong());
		OfferTracker restarted = tracker(storage, positions);
		restarted.load();
		restarted.onOfferChanged(0, offer(1000L, 2000L, 2));
		assertEquals(1000L, positions.get(1513).getTotalCost());
		GrandExchangeOffer empty = Mockito.mock(GrandExchangeOffer.class);
		Mockito.when(empty.getState()).thenReturn(GrandExchangeOfferState.EMPTY);
		restarted.onOfferChanged(0, empty);
		assertEquals(1000L, restarted.getOffer(0).getPrice());
		assertEquals(1, positions.get(1513).getQuantity());
	}
}
