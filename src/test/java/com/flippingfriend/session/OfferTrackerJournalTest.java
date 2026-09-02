package com.flippingfriend.session;

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
import static org.junit.Assert.assertTrue;

/**
 * What ends up in the trade journal, which is the only record of what the player actually made.
 * <p>
 * Every case here is drawn from a real journal that had gone wrong. Selling 7,000 rubies in chunks
 * was logged as three separate trades; one sale of 4,337 mithril bars was logged six times; and the
 * last sale of each item claimed a profit two orders of magnitude too large. Lifetime profit read
 * 5,869,202 against a true 435,491.
 */
public class OfferTrackerJournalTest
{
	private static final int RUBY = 1603;
	private static final int BUY_PRICE = 821;
	private static final int SELL_PRICE = 845;

	@Rule
	public final TemporaryFolder folder = new TemporaryFolder();

	private TradeJournal journal;
	private PositionBook positions;
	private BuyLimitTracker buyLimits;

	private OfferTracker trackerAt(Path root)
	{
		com.flippingfriend.data.PluginStorage storage = TestStorage.rootedAt(root, "p");
		positions = new PositionBook(storage);
		buyLimits = new BuyLimitTracker(storage);
		journal = new TradeJournal(storage, null, new AccountMonitor(null, null, null));
		return new OfferTracker(storage, positions, buyLimits, journal, new TaxCalculator(),
			new TradePlans(storage));
	}

	private static GrandExchangeOffer offer(GrandExchangeOfferState state, int price, int total,
		int sold)
	{
		GrandExchangeOffer o = Mockito.mock(GrandExchangeOffer.class);
		Mockito.when(o.getState()).thenReturn(state);
		Mockito.when(o.getItemId()).thenReturn(RUBY);
		Mockito.when(o.getPrice()).thenReturn(price);
		Mockito.when(o.getTotalQuantity()).thenReturn(total);
		Mockito.when(o.getQuantitySold()).thenReturn(sold);
		int spent = price * sold;
		if (state == GrandExchangeOfferState.SELLING || state == GrandExchangeOfferState.SOLD || state == GrandExchangeOfferState.CANCELLED_SELL)
		{
			TaxCalculator taxCalc = new TaxCalculator();
			spent -= taxCalc.taxFor(RUBY, price, sold);
		}
		Mockito.when(o.getSpent()).thenReturn(spent);
		return o;
	}

	/** Buys {@code quantity} units in one offer and collects it, giving the book a real cost basis. */
	private void buy(OfferTracker tracker, int slot, int quantity)
	{
		tracker.onOfferChanged(slot, offer(GrandExchangeOfferState.BUYING, BUY_PRICE, quantity, 0));
		tracker.onOfferChanged(slot, offer(GrandExchangeOfferState.BOUGHT, BUY_PRICE, quantity, quantity));
		tracker.onOfferChanged(slot, offer(GrandExchangeOfferState.EMPTY, BUY_PRICE, quantity, quantity));
	}

	@Test
	public void oneOfferSoldInChunksIsOneTrade() throws Exception
	{
		OfferTracker tracker = trackerAt(folder.newFolder("chunks").toPath());
		buy(tracker, 0, 1500);

		// The market fills the sell offer in three pieces, which is entirely normal and used to
		// produce three journal entries -- "makes it look like I'm making more trades than I am".
		tracker.onOfferChanged(1, offer(GrandExchangeOfferState.SELLING, SELL_PRICE, 1500, 0));
		tracker.onOfferChanged(1, offer(GrandExchangeOfferState.SELLING, SELL_PRICE, 1500, 500));
		tracker.onOfferChanged(1, offer(GrandExchangeOfferState.SELLING, SELL_PRICE, 1500, 1000));
		assertTrue("nothing is written until the offer finishes", journal.getHistory().isEmpty());

		tracker.onOfferChanged(1, offer(GrandExchangeOfferState.SOLD, SELL_PRICE, 1500, 1500));

		List<FlipRecord> history = journal.getHistory();
		assertEquals("one offer is one trade, however many pieces it filled in", 1, history.size());
		assertEquals(1500, history.get(0).getQuantity());
	}

	@Test
	public void aReplayedOfferIsNotBookedTwice() throws Exception
	{
		Path root = folder.newFolder("replay").toPath();
		OfferTracker tracker = trackerAt(root);
		// Deliberately buy more than is sold. With the position already emptied the cost-basis guard
		// catches a re-book on its own, which would let this pass without any replay protection at
		// all -- it did, when the protection was removed to check.
		buy(tracker, 0, 3000);

		tracker.onOfferChanged(1, offer(GrandExchangeOfferState.SELLING, SELL_PRICE, 1500, 750));
		tracker.onOfferChanged(1, offer(GrandExchangeOfferState.SOLD, SELL_PRICE, 1500, 1500));
		tracker.onOfferChanged(1, offer(GrandExchangeOfferState.EMPTY, SELL_PRICE, 1500, 1500));
		assertEquals(1, journal.getHistory().size());

		// The game replays every slot on login. Before, the collect had thrown away the evidence that
		// these fills were counted, so the whole offer arrived looking new and was booked again.
		tracker.onOfferChanged(1, offer(GrandExchangeOfferState.SOLD, SELL_PRICE, 1500, 1500));
		tracker.onOfferChanged(1, offer(GrandExchangeOfferState.EMPTY, SELL_PRICE, 1500, 1500));

		assertEquals("a replayed offer is the same trade, not a second one", 1,
			journal.getHistory().size());
		assertEquals("and the 1,500 still held must not have been sold by the replay",
			1500, positions.get(RUBY).getQuantity());
	}

	@Test
	public void sellingMoreThanIsHeldDoesNotInventProfit() throws Exception
	{
		OfferTracker tracker = trackerAt(folder.newFolder("unbacked").toPath());
		buy(tracker, 0, 500);

		// The book holds 500. The game reports 3,558 sold -- the position was drained by something
		// the plugin did not see. Charging the proceeds of 3,558 against the cost of 500 is exactly
		// how a 28,464 gp flip was recorded as 2,570,280.
		tracker.onOfferChanged(1, offer(GrandExchangeOfferState.SELLING, SELL_PRICE, 3558, 0));
		tracker.onOfferChanged(1, offer(GrandExchangeOfferState.SOLD, SELL_PRICE, 3558, 3558));

		List<FlipRecord> history = journal.getHistory();
		assertEquals(1, history.size());
		FlipRecord record = history.get(0);
		assertEquals("only the units with a known cost are priced", 500, record.getQuantity());
		assertTrue("profit must stay a margin, not become proceeds",
			record.getProfit() < (long) SELL_PRICE * 500);
	}

	@Test
	public void everyRecordedProfitIsTheMarginOnWhatWasSold() throws Exception
	{
		OfferTracker tracker = trackerAt(folder.newFolder("invariant").toPath());
		buy(tracker, 0, 1200);

		tracker.onOfferChanged(1, offer(GrandExchangeOfferState.SELLING, SELL_PRICE, 1200, 400));
		tracker.onOfferChanged(1, offer(GrandExchangeOfferState.SELLING, SELL_PRICE, 1200, 900));
		tracker.onOfferChanged(1, offer(GrandExchangeOfferState.SOLD, SELL_PRICE, 1200, 1200));

		// The invariant every good record in the real journal satisfied and every broken one violated.
		for (FlipRecord record : journal.getHistory())
		{
			long expected = (long) (record.getSellPrice() - record.getBuyPrice()) * record.getQuantity()
				- record.getTax();
			assertEquals("profit must equal margin times quantity, less tax",
				expected, record.getProfit());
		}
	}

	@Test
	public void aRestartDoesNotReBookAnOfferTheGameReplays() throws Exception
	{
		Path root = folder.newFolder("restart").toPath();
		OfferTracker tracker = trackerAt(root);
		buy(tracker, 0, 3000);
		tracker.onOfferChanged(1, offer(GrandExchangeOfferState.SOLD, SELL_PRICE, 1500, 1500));
		tracker.onOfferChanged(1, offer(GrandExchangeOfferState.EMPTY, SELL_PRICE, 1500, 1500));
		tracker.save();
		positions.save();
		assertEquals(1, journal.getHistory().size());

		// The restart is the case that matters: it is the one that causes the replay in the first
		// place, so remembering the offer only in memory protects against everything except the
		// situation the protection exists for.
		OfferTracker restarted = trackerAt(root);
		restarted.load();
		positions.load();
		journal.load();
		restarted.onOfferChanged(1, offer(GrandExchangeOfferState.SOLD, SELL_PRICE, 1500, 1500));

		assertEquals("the offer was already booked before the restart", 1,
			journal.getHistory().size());
	}

	@Test
	public void aFinishedOfferStillInItsSlotSurvivesARestart() throws Exception
	{
		Path root = folder.newFolder("uncollected").toPath();
		OfferTracker tracker = trackerAt(root);
		buy(tracker, 0, 1500);
		// Sold but not yet collected: it still occupies the slot and the player still has to collect
		// it, so it must come back as a live offer rather than as finished history.
		tracker.onOfferChanged(1, offer(GrandExchangeOfferState.SOLD, SELL_PRICE, 1500, 1500));
		tracker.save();

		OfferTracker restarted = trackerAt(root);
		restarted.load();

		assertEquals("an uncollected offer still occupies its slot", 1, restarted.getOffers().size());
		assertEquals(1500, restarted.getOffer(1).getQuantityFilled());
	}

	@Test
	public void aReplayedBuyDoesNotSpendTheBuyLimitTwice() throws Exception
	{
		OfferTracker tracker = trackerAt(folder.newFolder("limit").toPath());
		buy(tracker, 0, 1500);
		int afterFirst = buyLimits.purchasedInWindow(RUBY, java.time.Instant.now());

		// Same replay, on the buy side: it used to add the offer's whole running total a second time,
		// so the ledger under-reported what was spent and the next offer went over the limit.
		tracker.onOfferChanged(0, offer(GrandExchangeOfferState.BOUGHT, BUY_PRICE, 1500, 1500));
		tracker.onOfferChanged(0, offer(GrandExchangeOfferState.EMPTY, BUY_PRICE, 1500, 1500));

		assertEquals("a replayed buy must not be counted against the limit twice",
			afterFirst, buyLimits.purchasedInWindow(RUBY, java.time.Instant.now()));
	}
}
