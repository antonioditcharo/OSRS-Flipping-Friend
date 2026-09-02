package com.flippingfriend.session;

import com.flippingfriend.data.TestStorage;
import com.flippingfriend.model.TaxCalculator;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import net.runelite.api.GrandExchangeOffer;
import net.runelite.api.GrandExchangeOfferState;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.Mockito;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The engine needs to know what is already sitting in a slot. Without it, buy limits — which only
 * decrement when an offer actually <em>fills</em> — leave a freshly bought item looking completely
 * untouched, and the panel repeats the instruction the player has just carried out.
 */
public class OfferTrackerReportingTest
{
	private static final int MAGIC_LOGS = 1513;

	@Rule
	public final TemporaryFolder folder = new TemporaryFolder();

	private OfferTracker trackerAt(Path root)
	{
		return new OfferTracker(TestStorage.rootedAt(root, "p"), new PositionBook(TestStorage.rootedAt(root, "p")),
			new BuyLimitTracker(TestStorage.rootedAt(root, "p")),
			new TradeJournal(TestStorage.rootedAt(root, "p"), null, new AccountMonitor(null, null, null)), new TaxCalculator(), new TradePlans(TestStorage.rootedAt(root, "p")));
	}

	private static GrandExchangeOffer offer(GrandExchangeOfferState state, int itemId, int price,
		int total, int sold)
	{
		GrandExchangeOffer o = Mockito.mock(GrandExchangeOffer.class);
		Mockito.when(o.getState()).thenReturn(state);
		Mockito.when(o.getItemId()).thenReturn(itemId);
		Mockito.when(o.getPrice()).thenReturn(price);
		Mockito.when(o.getTotalQuantity()).thenReturn(total);
		Mockito.when(o.getQuantitySold()).thenReturn(sold);
		Mockito.when(o.getSpent()).thenReturn(price * sold);
		return o;
	}

	@Test
	public void reportsAnItemWithAnOpenBuyOffer() throws Exception
	{
		OfferTracker tracker = trackerAt(folder.newFolder("a").toPath());
		tracker.onOfferChanged(0, offer(GrandExchangeOfferState.BUYING, MAGIC_LOGS, 1000, 100, 0));

		Set<Integer> open = tracker.itemsWithOpenOffers();
		assertTrue("a buy in progress must be visible to the engine", open.contains(MAGIC_LOGS));
	}

	@Test
	public void reportsQuantityListedForSale() throws Exception
	{
		OfferTracker tracker = trackerAt(folder.newFolder("b").toPath());
		tracker.onOfferChanged(0, offer(GrandExchangeOfferState.SELLING, MAGIC_LOGS, 1200, 10, 4));

		Map<Integer, Integer> listed = tracker.listedForSale();
		assertEquals("six of the ten are still unsold", Integer.valueOf(6), listed.get(MAGIC_LOGS));
	}

	@Test
	public void aCollectedSlotIsNoLongerOpen() throws Exception
	{
		OfferTracker tracker = trackerAt(folder.newFolder("c").toPath());
		tracker.onOfferChanged(0, offer(GrandExchangeOfferState.BUYING, MAGIC_LOGS, 1000, 100, 0));
		assertTrue(tracker.itemsWithOpenOffers().contains(MAGIC_LOGS));

		// Collected: the slot empties and the item is free to be suggested again.
		tracker.onOfferChanged(0, offer(GrandExchangeOfferState.EMPTY, MAGIC_LOGS, 1000, 100, 100));

		assertFalse(tracker.itemsWithOpenOffers().contains(MAGIC_LOGS));
		assertTrue(tracker.listedForSale().isEmpty());
	}

	@Test
	public void aFinishedButUncollectedOfferIsNotOpen() throws Exception
	{
		OfferTracker tracker = trackerAt(folder.newFolder("d").toPath());
		tracker.onOfferChanged(0, offer(GrandExchangeOfferState.BOUGHT, MAGIC_LOGS, 1000, 100, 100));

		// It still occupies the slot, but it is no longer buying — the engine's next instruction
		// should be to collect it, not to treat it as work in progress.
		assertFalse(tracker.itemsWithOpenOffers().contains(MAGIC_LOGS));
	}
}
