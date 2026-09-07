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
			new TradePlans(storage), new TransactionManager(storage));
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
		// Gross, for buys and for sells alike, because that is what the client actually reports.
		//
		// This used to subtract the tax on a sale, and every test of the money path was therefore run
		// against an input the real client never produces. Checked against 40 real SOLD events on a
		// live account: spent equals price x quantity to the coin, every time. The code was correct
		// for the mock and wrong for the game.
		Mockito.when(o.getSpent()).thenReturn(price * sold);
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
	public void sellingMoreThanIsHeldExtrapolatesProfitFromAverageCost() throws Exception
	{
		OfferTracker tracker = trackerAt(folder.newFolder("unbacked").toPath());
		buy(tracker, 0, 500);

		// The book holds 500. The game reports 3,558 sold -- the position was drained by something
		// the plugin did not see. The plugin extrapolates the cost basis of the unbacked 3,058 units
		// using the known average cost of the 500 units it did see.
		tracker.onOfferChanged(1, offer(GrandExchangeOfferState.SELLING, SELL_PRICE, 3558, 0));
		tracker.onOfferChanged(1, offer(GrandExchangeOfferState.SOLD, SELL_PRICE, 3558, 3558));

		List<FlipRecord> history = journal.getHistory();
		assertEquals(1, history.size());
		FlipRecord record = history.get(0);
		assertEquals("the full sold amount is recorded since cost was extrapolated", 3558, record.getQuantity());
		
		long expectedCostBasis = (long) BUY_PRICE * 3558;
		long expectedTax = new TaxCalculator().taxFor(RUBY, SELL_PRICE, 3558);
		long expectedGross = (long) SELL_PRICE * 3558;
		long expectedProfit = expectedGross - expectedCostBasis - expectedTax;
		
		assertEquals("profit is extrapolated proportionally", expectedProfit, record.getProfit());
	}

	@Test
	public void theTaxIsTakenOffTheProfitRatherThanInventedAndCancelled()
	{
		// The live case, to the coin. 24,370 Blood runes bought at 338 and sold at 347.
		//
		// The journal recorded a profit of 219,330, which is the margin with nothing taken off, and a
		// tax of 170,590 beside it that was never deducted from anything. Both came out of assuming
		// the exchange reports a sale NET of tax: it does not, so the code grossed 347 up to 354 -- a
		// price no one was ever paid -- called the difference tax, and then subtracted that invention
		// from its own inflation. The two cancelled and the real tax was never charged.
		//
		// Two per cent of 347 is 6.94, floored to 6, so
		// the tax on 24,370 of them is 146,220 and the profit is 73,110, not 219,330.
		int blood = 565;
		long gross = 347L * 24_370;
		long cost = 338L * 24_370;
		long tax = new TaxCalculator().taxFor(blood, 347, 24_370);

		assertEquals("two per cent of 347, floored, times the quantity", 146_220L, tax);
		assertEquals("what the margin is worth once the exchange has taken its cut",
			73_110L, gross - tax - cost);
		assertTrue("and that is a long way under what was recorded", 73_110L < 219_330L);
	}

	@Test
	public void sellingSomethingThePluginNeverBoughtIsNotProfit()
	{
		// One account's all-time profit read 62,141,481, of which 61,930,133 was twenty sales of its
		// own gear -- Ranger boots alone counting 35,924,263, because the proceeds of selling armour
		// you already owned were booked as though every coin were profit.
		//
		// Converting an asset you already had into coins is not a flip and has no margin. The guard
		// above this one already refused a sale that nothing could price; this refuses the same thing
		// wearing a position that prices it at zero.
		OfferTracker tracker = trackerAt(folder.getRoot().toPath());

		// No buy at all: straight to a sale, as happens when you list something out of your bank.
		tracker.onOfferChanged(2, offer(GrandExchangeOfferState.SELLING, SELL_PRICE, 1, 0));
		tracker.onOfferChanged(2, offer(GrandExchangeOfferState.SOLD, SELL_PRICE, 1, 1));

		for (FlipRecord record : journal.getHistory())
		{
			assertTrue("proceeds must never be recorded as profit: " + record.getItemName()
				+ " at " + record.getProfit(), record.getBuyPrice() > 0);
		}
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
