package com.flippingfriend.session;

import com.flippingfriend.data.Candle;
import com.flippingfriend.data.ItemMetadata;
import com.flippingfriend.data.LatestPrice;
import com.flippingfriend.data.MarketSnapshot;
import com.flippingfriend.model.TaxCalculator;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * The drawdown figure is what arms the circuit breaker, so the cases here are the ones where a
 * plausible-looking implementation would leave it disarmed: a session holding a collapsing position
 * but having sold nothing, and a session whose realised losses are masked by open positions that
 * have since recovered.
 */
public class SessionRiskTest
{
	private static final int ITEM = 4151;

	private TaxCalculator tax;
	private PositionBook positions;

	@Before
	public void setUp()
	{
		tax = new TaxCalculator();
		tax.resolveExemptions(Collections.singletonList(
			new ItemMetadata(ITEM, "Abyssal whip", true, 70, 0)));
		positions = new PositionBook(null);
	}

	private static SessionStats profit(long amount)
	{
		return new SessionStats(1, 1, amount, 0, 0, 3600, java.util.Collections.emptyMap());
	}

	private static MarketSnapshot marketAt(int bid)
	{
		Map<Integer, LatestPrice> latest = new HashMap<>();
		latest.put(ITEM, new LatestPrice(bid + 10_000, Instant.now().getEpochSecond(), bid,
			Instant.now().getEpochSecond()));
		Map<Integer, ItemMetadata> metadata = new HashMap<>();
		metadata.put(ITEM, new ItemMetadata(ITEM, "Abyssal whip", true, 70, 0));
		return new MarketSnapshot(metadata, latest, new HashMap<Integer, Candle>(),
			new HashMap<Integer, Candle>(), Instant.now());
	}

	@Test
	public void anItemWithNoInstantSellQuoteIsSkippedRatherThanCrashing()
	{
		// LatestPrice.getLow() is a nullable Integer: an item nobody has instant-sold has no low at
		// all. This is the first statement in the engine's refresh worker, so unboxing a null here
		// killed the entire cycle -- account publish, plan refresh, suggestion, walkthrough -- and
		// left the panel frozen on stale data with one line in the client log.
		Map<Integer, LatestPrice> latest = new HashMap<>();
		latest.put(ITEM, new LatestPrice(2_000_000, Instant.now().getEpochSecond(), null, null));
		Map<Integer, ItemMetadata> metadata = new HashMap<>();
		metadata.put(ITEM, new ItemMetadata(ITEM, "Abyssal whip", true, 70, 0));
		MarketSnapshot unquoted = new MarketSnapshot(metadata, latest,
			new HashMap<Integer, Candle>(), new HashMap<Integer, Candle>(), Instant.now());

		positions.recordBuy(ITEM, "Abyssal whip", 10, 10_000_000, Instant.now());

		// Unmarkable, not a loss: the realised figure stands on its own.
		assertEquals(0, SessionRisk.markedDrawdown(profit(1_000), positions, unquoted, tax));
	}

	@Test
	public void aSessionInProfitHasNoDrawdown()
	{
		assertEquals(0, SessionRisk.markedDrawdown(profit(5_000_000), positions, marketAt(1_000_000), tax));
	}

	@Test
	public void realisedLossesCountAgainstTheBudget()
	{
		assertEquals(3_000_000,
			SessionRisk.markedDrawdown(profit(-3_000_000), positions, marketAt(1_000_000), tax));
	}

	@Test
	public void anOpenPositionThatHasCollapsedIsADrawdownEvenWithNothingSold()
	{
		// The case a realised-only breaker misses entirely: nothing has been sold, so realised profit
		// is zero, while the position is worth half what it cost. A breaker that keeps buying here is
		// buying all the way down.
		positions.recordBuy(ITEM, "Abyssal whip", 10, 10_000_000, Instant.now());

		long drawdown = SessionRisk.markedDrawdown(profit(0), positions, marketAt(500_000), tax);

		assertTrue("a halved position must register as a loss, not as nothing", drawdown > 4_000_000);
	}

	@Test
	public void openGainsOffsetRealisedLosses()
	{
		// Equally, a session should not be frozen for a realised loss it has already made back.
		positions.recordBuy(ITEM, "Abyssal whip", 10, 10_000_000, Instant.now());

		long drawdown = SessionRisk.markedDrawdown(profit(-1_000_000), positions, marketAt(2_000_000), tax);

		assertEquals("marked to market the session is up overall", 0, drawdown);
	}

	@Test
	public void positionsWithNoKnownCostAreNotGuessedAt()
	{
		// Items already in the bank when the plugin first ran have no cost basis. Inventing one could
		// freeze trading over a "loss" that never happened.
		positions.adoptExisting(ITEM, "Abyssal whip", 10, Instant.now());

		assertEquals(0, SessionRisk.markedDrawdown(profit(0), positions, marketAt(1), tax));
	}

	@Test
	public void anUnusableMarketLeavesPositionsAtCost()
	{
		positions.recordBuy(ITEM, "Abyssal whip", 10, 10_000_000, Instant.now());

		assertEquals("without a believable quote there is no move to mark",
			0, SessionRisk.markedDrawdown(profit(0), positions, MarketSnapshot.empty(), tax));
	}

	@Test
	public void theMarkIsNetOfTaxBecauseSellingWouldBe()
	{
		// Bought at exactly the current bid: the position is not flat, it is down by the tax that
		// selling would cost. Ignoring that would overstate what the bank is worth.
		positions.recordBuy(ITEM, "Abyssal whip", 10, 10_000_000, Instant.now());

		long drawdown = SessionRisk.markedDrawdown(profit(0), positions, marketAt(1_000_000), tax);

		assertEquals(tax.taxFor(ITEM, 1_000_000, 10), drawdown);
	}
}
