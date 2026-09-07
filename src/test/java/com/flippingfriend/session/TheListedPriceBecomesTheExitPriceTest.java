package com.flippingfriend.session;

import static org.junit.Assert.assertEquals;

import com.flippingfriend.data.TestStorage;
import com.flippingfriend.model.TaxCalculator;
import java.nio.file.Path;
import java.time.Instant;
import net.runelite.api.GrandExchangeOffer;
import net.runelite.api.GrandExchangeOfferState;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.Mockito;

/**
 * A live sell offer is the exit price, whatever the plan intended.
 *
 * <p>{@code targetSellPrice} was written in exactly one place — by {@link TradePlans} when the buy was
 * booked, and only when it was still unset — and never again. Reprice the offer, by hand or on this
 * plugin's own advice, and nothing wrote the new price back.
 *
 * <p>Everything downstream reads that field: the holdings card's "Selling at" row, the recommendation
 * card, the Grand Exchange overlay, the journal's predicted profit, and the sell engine's own exit
 * search. All of them went on reasoning about the price the plan chose when the trade was opened,
 * however far the offer had since moved from it. One field, written once, read by five things.
 */
public class TheListedPriceBecomesTheExitPriceTest
{
	private static final int RUBY = 1603;
	private static final int BUY_PRICE = 821;
	private static final int PLANNED_EXIT = 900;

	@Rule
	public final TemporaryFolder folder = new TemporaryFolder();

	private PositionBook positions;

	private OfferTracker trackerAt(Path root)
	{
		com.flippingfriend.data.PluginStorage storage = TestStorage.rootedAt(root, "p");
		positions = new PositionBook(storage);
		return new OfferTracker(storage, positions, new BuyLimitTracker(storage),
			new TradeJournal(storage, null, new AccountMonitor(null, null, null)),
			new TaxCalculator(), new TradePlans(storage), new TransactionManager(storage));
	}

	private static GrandExchangeOffer offer(GrandExchangeOfferState state, int price, int total,
		int done)
	{
		GrandExchangeOffer o = Mockito.mock(GrandExchangeOffer.class);
		Mockito.when(o.getState()).thenReturn(state);
		Mockito.when(o.getItemId()).thenReturn(RUBY);
		Mockito.when(o.getPrice()).thenReturn(price);
		Mockito.when(o.getTotalQuantity()).thenReturn(total);
		Mockito.when(o.getQuantitySold()).thenReturn(done);
		Mockito.when(o.getSpent()).thenReturn(price * done);
		return o;
	}

	@Test
	public void theStopFollowsTheCostWhenMoreIsBoughtIn()
	{
		// Found while auditing what else is written once and read everywhere. The stop is a percentage
		// below what the item cost when the trade was opened -- twelve per cent on High -- and
		// averaging more units in moved the average cost and left the stop behind.
		//
		// Averaging DOWN is the dangerous direction, because it walks the stop towards the cost. One
		// at 1,000 with a stop at 880, then one at 800: the average is 900 and the stop is still 880,
		// two per cent below cost instead of twelve. A dip the profile meant to sit through now cuts
		// the position -- which is the plugin looking "quick to accept a loss" for a reason that has
		// nothing to do with the sell engine.
		Position position = new Position(RUBY, "Ruby", 1, 1_000, Instant.now().getEpochSecond(), true);
		position.setStopPrice(880);

		position.addFill(1, 800);

		assertEquals("the average moved", 900, position.getAverageCost());
		assertEquals("and the stop moved with it, keeping its distance", 792,
			position.getStopPrice());
	}

	@Test
	public void averagingUpMovesTheStopUpToo()
	{
		Position position = new Position(RUBY, "Ruby", 1, 1_000, Instant.now().getEpochSecond(), true);
		position.setStopPrice(880);

		position.addFill(1, 1_200);

		assertEquals(1_100, position.getAverageCost());
		assertEquals(968, position.getStopPrice());
	}

	@Test
	public void aPositionWithNoStopIsLeftAlone()
	{
		// Nothing to rescale, and inventing one here would take the decision away from the plan.
		Position position = new Position(RUBY, "Ruby", 1, 1_000, Instant.now().getEpochSecond(), true);

		position.addFill(1, 800);

		assertEquals(0, position.getStopPrice());
	}

	@Test
	public void listingAtADifferentPriceMovesTheExitPrice()
	{
		OfferTracker tracker = trackerAt(folder.getRoot().toPath());
		Position position = positions.recordBuy(RUBY, "Ruby", 100, 100L * BUY_PRICE, Instant.now());
		position.setTargetSellPrice(PLANNED_EXIT);

		// Listed 30 gp under the plan, which is what repricing an offer looks like from here.
		tracker.onOfferChanged(1, offer(GrandExchangeOfferState.SELLING, 870, 100, 0));

		assertEquals("the offer on the market is the exit price now", 870,
			positions.get(RUBY).getTargetSellPrice());
	}

	@Test
	public void repricingAgainMovesItAgain()
	{
		// Not a one-off correction: the field has to keep following the offer, because the player and
		// the reprice advice can both move it repeatedly.
		OfferTracker tracker = trackerAt(folder.getRoot().toPath());
		Position position = positions.recordBuy(RUBY, "Ruby", 100, 100L * BUY_PRICE, Instant.now());
		position.setTargetSellPrice(PLANNED_EXIT);

		tracker.onOfferChanged(1, offer(GrandExchangeOfferState.SELLING, 880, 100, 0));
		tracker.onOfferChanged(1, offer(GrandExchangeOfferState.SELLING, 860, 100, 0));

		assertEquals(860, positions.get(RUBY).getTargetSellPrice());
	}

	@Test
	public void aBuyOfferLeavesThePlannedExitAlone()
	{
		// While the item is still being bought there is no listed price to learn from, and the plan's
		// intention is the best answer there is. A buy at 821 must not become an exit price of 821.
		OfferTracker tracker = trackerAt(folder.getRoot().toPath());
		Position position = positions.recordBuy(RUBY, "Ruby", 100, 100L * BUY_PRICE, Instant.now());
		position.setTargetSellPrice(PLANNED_EXIT);

		tracker.onOfferChanged(2, offer(GrandExchangeOfferState.BUYING, BUY_PRICE, 100, 0));

		assertEquals("the plan still owns the exit while we are buying", PLANNED_EXIT,
			positions.get(RUBY).getTargetSellPrice());
	}
}
