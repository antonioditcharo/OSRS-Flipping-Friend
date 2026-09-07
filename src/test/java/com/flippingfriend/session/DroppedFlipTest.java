package com.flippingfriend.session;

import com.flippingfriend.data.PluginStorage;
import com.flippingfriend.data.TestStorage;
import com.flippingfriend.model.TaxCalculator;
import java.nio.file.Path;
import java.util.List;
import net.runelite.api.GrandExchangeOffer;
import net.runelite.api.GrandExchangeOfferState;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.Mockito;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * A completed trade must reach the journal even when the position behind it has gone.
 * <p>
 * The failure this fixes: 90 Rune nails were bought for 70,200 gp, the position was destroyed by a
 * reconcile pass, and when the sale settled the book could account for none of it. The journal write
 * bailed on {@code backed <= 0} and returned, so a real trade of real money left no record anywhere —
 * not in the journal, not in the panel, not in anything the model learns from. It was also completely
 * silent.
 * <p>
 * Two independent defences are tested here. The cost is written to a ledger the moment a buy fills,
 * so it outlives the position; and a fill that was never observed is settled up before the slot is
 * handed back, rather than being thrown away with it.
 */
public class DroppedFlipTest
{
	private static final int RUNE_NAILS = 4824;
	private static final int BUY_PRICE = 780;
	private static final int SELL_PRICE = 800;

	@Rule
	public final TemporaryFolder folder = new TemporaryFolder();

	private TradeJournal journal;
	private PositionBook positions;

	private OfferTracker trackerAt(Path root)
	{
		PluginStorage storage = TestStorage.rootedAt(root, "p");
		positions = new PositionBook(storage);
		journal = new TradeJournal(storage, null, new AccountMonitor(null, null, null));
		return new OfferTracker(storage, positions, new BuyLimitTracker(storage), journal,
			new TaxCalculator(), new TradePlans(storage), new TransactionManager(storage));
	}

	private static GrandExchangeOffer offer(GrandExchangeOfferState state, boolean buying, int price,
		int total, int sold)
	{
		GrandExchangeOffer o = Mockito.mock(GrandExchangeOffer.class);
		Mockito.when(o.getState()).thenReturn(state);
		Mockito.when(o.getItemId()).thenReturn(RUNE_NAILS);
		Mockito.when(o.getPrice()).thenReturn(price);
		Mockito.when(o.getTotalQuantity()).thenReturn(total);
		Mockito.when(o.getQuantitySold()).thenReturn(sold);
		Mockito.when(o.getSpent()).thenReturn(price * sold);
		return o;
	}

	private static GrandExchangeOffer buying(GrandExchangeOfferState state, int total, int sold)
	{
		return offer(state, true, BUY_PRICE, total, sold);
	}

	private static GrandExchangeOffer selling(GrandExchangeOfferState state, int total, int sold)
	{
		return offer(state, false, SELL_PRICE, total, sold);
	}

	/** The slot after a collect. Everything about it reads as nothing. */
	private static GrandExchangeOffer empty()
	{
		GrandExchangeOffer o = Mockito.mock(GrandExchangeOffer.class);
		Mockito.when(o.getState()).thenReturn(GrandExchangeOfferState.EMPTY);
		return o;
	}

	@Test
	public void aSaleWithNoPositionIsStillJournalled() throws Exception
	{
		OfferTracker tracker = trackerAt(folder.newFolder("unbacked").toPath());

		// Bought 90, exactly as it happened.
		tracker.onOfferChanged(4, buying(GrandExchangeOfferState.BUYING, 2109, 90));
		assertNotNull("the buy was recorded", positions.get(RUNE_NAILS));

		// The position is destroyed before the sale settles -- what an older reconcile did on its own.
		positions.close(RUNE_NAILS);

		tracker.onOfferChanged(1, selling(GrandExchangeOfferState.SELLING, 90, 0));
		tracker.onOfferChanged(1, selling(GrandExchangeOfferState.SOLD, 90, 90));

		List<FlipRecord> flips = journal.getHistory();
		assertEquals("the trade must reach the journal", 1, flips.size());

		FlipRecord flip = flips.get(0);
		assertEquals(90, flip.getQuantity());
		assertEquals("priced from what was actually paid", BUY_PRICE, flip.getBuyPrice());
		assertTrue("and marked as reconstructed rather than measured", flip.isCostReconstructed());
	}

	@Test
	public void aNormalSaleIsNotMarkedReconstructed() throws Exception
	{
		OfferTracker tracker = trackerAt(folder.newFolder("backed").toPath());

		tracker.onOfferChanged(4, buying(GrandExchangeOfferState.BUYING, 90, 90));
		tracker.onOfferChanged(1, selling(GrandExchangeOfferState.SELLING, 90, 0));
		tracker.onOfferChanged(1, selling(GrandExchangeOfferState.SOLD, 90, 90));

		List<FlipRecord> flips = journal.getHistory();
		assertEquals(1, flips.size());
		assertFalse("a flip measured end to end must not claim to be reconstructed",
			flips.get(0).isCostReconstructed());
	}

	@Test
	public void aSaleWithNoCostAnywhereIsNotInvented() throws Exception
	{
		// Nothing was ever bought through the plugin, so there is no honest price for it. Booking the
		// proceeds as pure profit is how a 28k flip once came out as 2.57m.
		OfferTracker tracker = trackerAt(folder.newFolder("nocost").toPath());

		tracker.onOfferChanged(1, selling(GrandExchangeOfferState.SELLING, 90, 0));
		tracker.onOfferChanged(1, selling(GrandExchangeOfferState.SOLD, 90, 90));

		assertTrue("no cost means no invented profit", journal.getHistory().isEmpty());
	}

	/**
	 * An offer whose fill was seen but never booked is settled up before the slot is handed back.
	 * <p>
	 * The on-disk shape of a crash between observing a fill and recording it: the offer knows 90 have
	 * filled and that none of them were booked. The collect used to go straight to the journal from
	 * here, discarding the difference along with the offer.
	 */
	@Test
	public void anUnbookedFillIsSettledUpBeforeTheSlotIsFreed() throws Exception
	{
		Path root = folder.newFolder("late").toPath();
		PluginStorage storage = TestStorage.rootedAt(root, "p");
		java.nio.file.Path file = storage.accountDir().resolve("offers.json");
		java.nio.file.Files.write(file, ("[{\"slot\":4,\"itemId\":" + RUNE_NAILS
			+ ",\"itemName\":\"Rune nails\",\"buying\":true,\"price\":" + BUY_PRICE
			+ ",\"totalQuantity\":90,\"quantityFilled\":90,\"recordedQuantity\":0,"
			+ "\"recordedSpent\":0,\"spent\":70200,\"state\":\"BOUGHT\",\"firstSeen\":1,"
			+ "\"lastChanged\":2,\"journalled\":false,\"collected\":false}]")
			.getBytes(java.nio.charset.StandardCharsets.UTF_8));

		OfferTracker tracker = trackerAt(root);
		tracker.load();

		// The player collects. This is the last moment the fill can be booked.
		tracker.onOfferChanged(4, empty());

		assertNotNull("a fill seen but never booked must not leave with the slot",
			positions.get(RUNE_NAILS));
		assertEquals(90, positions.get(RUNE_NAILS).getQuantity());
		assertEquals("and it must carry what was actually spent", 70_200,
			positions.get(RUNE_NAILS).getTotalCost());
	}

	/**
	 * A re-buy the plugin watched from the start is recorded, even into the same slot.
	 * <p>
	 * The offer identity available here is item, side, price and total quantity, and a repeat flip of
	 * the same item at the same size produces all four identically. What separates a new offer from a
	 * replay is that a new one starts at zero and climbs, so watching it climb is what makes it
	 * recognisable. That is why the plugin now buffers offer events that arrive before it knows whose
	 * account it is looking at rather than discarding them: dropping the early updates is what left a
	 * genuinely new offer first seen already complete, which is the one case still indistinguishable
	 * from a replay.
	 */
	@Test
	public void aRebuyIntoTheSameSlotIsRecordedWhenItsFillIsWatched() throws Exception
	{
		OfferTracker tracker = trackerAt(folder.newFolder("rebuy").toPath());

		tracker.onOfferChanged(4, buying(GrandExchangeOfferState.BUYING, 90, 90));
		tracker.onOfferChanged(4, buying(GrandExchangeOfferState.BOUGHT, 90, 90));
		tracker.onOfferChanged(4, empty());   // collected
		positions.recordSell(RUNE_NAILS, 90);

		// The same item, same price, same size, same slot -- but seen from zero this time.
		tracker.onOfferChanged(4, buying(GrandExchangeOfferState.BUYING, 90, 0));
		tracker.onOfferChanged(4, buying(GrandExchangeOfferState.BUYING, 90, 45));
		tracker.onOfferChanged(4, buying(GrandExchangeOfferState.BOUGHT, 90, 90));

		assertNotNull("the second purchase is a purchase, not an echo of the first",
			positions.get(RUNE_NAILS));
		assertEquals(90, positions.get(RUNE_NAILS).getQuantity());
	}

	@Test
	public void aGenuineReplayIsStillNotDoubleCounted() throws Exception
	{
		// The other half, and the reason the suppression exists at all: RuneLite re-announces every
		// slot on login. One sale of 4,337 mithril bars was once recorded six times this way.
		OfferTracker tracker = trackerAt(folder.newFolder("replay").toPath());

		tracker.onOfferChanged(4, buying(GrandExchangeOfferState.BUYING, 90, 45));
		tracker.onOfferChanged(4, buying(GrandExchangeOfferState.BUYING, 90, 90));
		tracker.onOfferChanged(4, buying(GrandExchangeOfferState.BOUGHT, 90, 90));

		assertEquals(90, positions.get(RUNE_NAILS).getQuantity());

		// The same offer announced again, uncollected.
		tracker.onOfferChanged(4, buying(GrandExchangeOfferState.BOUGHT, 90, 90));

		assertEquals("still ninety, not a hundred and eighty",
			90, positions.get(RUNE_NAILS).getQuantity());
	}
}
