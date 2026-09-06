package com.flippingfriend.session;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.flippingfriend.CheckInterval;
import com.flippingfriend.RiskProfile;
import com.flippingfriend.TradingHorizon;
import com.flippingfriend.data.Candle;
import com.flippingfriend.data.LatestPrice;
import com.flippingfriend.model.FillModel;
import com.flippingfriend.model.ItemFeatures;
import com.flippingfriend.model.TaxCalculator;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

/**
 * Buying something is a commitment to selling it.
 *
 * <p>The exit search only ever considered prices within one per cent of the market's buy price, which
 * answers "how fast could this be liquidated at roughly today's price". That is the wrong question
 * about a position bought on purpose: a flip's profit is the gap between the bid it was bought at and
 * the ask it is sold at, and the ask sits further out than that grid can reach.
 *
 * <p>Three Karil's leathertops bought at 843,350 were opened to sell at 880,755. The market's buy
 * price was 854,390, so the grid's best offer was 856,098 — an 18,141 gp loss — and the engine
 * declined to spend a slot on it and held, having never once considered the price the trade was
 * entered for. It would have held until the hold limit forced that loss.
 *
 * <p>And once the stock is held, refusing to list it frees nothing. The slot question was answered at
 * purchase; declining afterwards only postpones getting the coins back.
 */
public class ABoughtPositionGetsListedTest
{
	private static final int KARILS = 4736;

	private final SellTimingEngine engine =
		new SellTimingEngine(new FillModel(), new TaxCalculator());

	/**
	 * A market that genuinely trades up to {@code ceiling}, so an ask out there is reachable.
	 *
	 * <p>The first version of this topped out below the target and the exit was refused as implausible
	 * -- which was the fixture being wrong rather than the code, but is worth keeping in mind: an ask
	 * nobody ever pays is not an exit, and the engine is right to say so.
	 */
	private static List<Candle> market(int price, int ceiling)
	{
		List<Candle> series = new ArrayList<>();
		long t0 = Instant.now().getEpochSecond() - 288 * 300L;
		for (int i = 0; i < 288; i++)
		{
			series.add(new Candle(t0 + i * 300L, ceiling, price - 20_000, 40, 40));
		}
		return series;
	}

	private SellDecision decide(int averageCost, int target, int marketHigh)
	{
		return decide(averageCost, target, marketHigh, Math.max(marketHigh, target) + 20_000);
	}

	private SellDecision decide(int averageCost, int target, int marketHigh, int ceiling)
	{
		Position position = new Position(KARILS, "Karil's leathertop", 3, (long) averageCost * 3,
			Instant.now().getEpochSecond() - 600, true);
		position.setTargetSellPrice(target);

		return engine.evaluate(position,
			new LatestPrice(marketHigh, Instant.now().getEpochSecond(),
				marketHigh - 12_000, Instant.now().getEpochSecond()),
			ItemFeatures.unknown(KARILS), market(marketHigh, ceiling),
			TradingHorizon.of(RiskProfile.MODERATE, CheckInterval.CONSTANT),
			Instant.now(), 5_000, false, false, false);
	}

	@Test
	public void thePriceItWasBoughtToSellAtIsConsidered()
	{
		// The live position, to the coin. Profitable at its target and a loss at the market.
		SellDecision decision = decide(843_350, 880_755, 854_390);

		assertTrue("a position in profit at its target must be listed, not held: "
			+ decision.getAction() + " -- " + decision.getReason(), decision.isSell());
		assertTrue("and listed at a price that actually makes money: " + decision.getPrice(),
			decision.getPrice() > 843_350);
	}

	@Test
	public void aModestProfitIsStillWorthTakingOnStockWeAlreadyOwn()
	{
		// Under half the 5,000 minimum profit per flip, which used to be held as "not worth a Grand
		// Exchange slot". The slot was spent when the item was bought; refusing to list frees nothing.
		// Tax is two per cent of the SALE price, about 17,200 gp on an item this size, so a modest
		// profit here still needs a wide-looking margin: 861,300 leaves 724 a piece, 2,172 across the
		// three, which is under half the 5,000 minimum and used to be held for that reason.
		SellDecision decision = decide(843_350, 861_300, 850_000);

		assertTrue("small is still positive: " + decision.getReason(), decision.isSell());
	}

	@Test
	public void anExitThatWouldLoseMoneyIsStillWaitedOut()
	{
		// The part that has to survive. With no target to reach for and the market below cost, there
		// is nothing to take -- the stop loss and the hold horizon decide when that changes.
		// Below cost but above the stop, so neither the cut nor the hold horizon has an opinion yet.
		SellDecision decision = decide(843_350, 0, 838_000);

		assertEquals("a loss is not an exit worth taking on its own", SellDecision.Action.HOLD,
			decision.getAction());
	}

	@Test
	public void aPositionAlreadyInProfitAtTheMarketDoesNotNeedItsTarget()
	{
		// The other holdings on the same account were in profit at the market price, and those were
		// never the problem. Nothing here may break them.
		SellDecision decision = decide(843_350, 880_755, 900_000);

		assertTrue(decision.isSell());
	}
}
