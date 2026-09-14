package com.flippingfriend.session;

import com.flippingfriend.CheckInterval;
import com.flippingfriend.RiskProfile;
import com.flippingfriend.TradingHorizon;
import com.flippingfriend.data.Candle;
import com.flippingfriend.data.LatestPrice;
import com.flippingfriend.model.FeatureEngine;
import com.flippingfriend.model.FillModel;
import com.flippingfriend.model.ItemFeatures;
import com.flippingfriend.model.TaxCalculator;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * The rule that replaced every reason this engine used to keep a position unlisted.
 * <p>
 * <b>Anything held is listed on the pass that sees it.</b> Not when a target arrives, not once the
 * exit clears a profit floor, not after a patience budget is spent, and not "when somebody is
 * buying". Each of those was a real branch in this class, each of them answered HOLD, and each of
 * them was a position sitting in a bank doing nothing while the reason it was being kept quietly
 * stopped being true. Three test files that pinned those behaviours were deleted to make this one:
 * {@code ExitFloorTest}, {@code ExitNearTest} and {@code ExitDecayTest}. What survives from them is
 * here, because the parts about the <em>stop</em> and about the <em>price</em> are still right.
 * <p>
 * What is still a judgement is what to ask, and that is what the rest of this file checks: an
 * ordinary sale does not give the spread away, and one that cannot wait can cross it.
 */
public class NeverHoldTest
{
	private static final int BUCKET_SECONDS = 300;
	/** Grimy irit leaf: the position from the original complaint, held for a price that never came. */
	private static final int ITEM = 207;
	private static final int QUANTITY = 2_197;

	private final TaxCalculator tax = new TaxCalculator();
	private final SellTimingEngine engine = new SellTimingEngine(new FillModel(), tax);
	private final FeatureEngine featureEngine = new FeatureEngine();
	private final TradingHorizon horizon =
		TradingHorizon.of(RiskProfile.MODERATE, CheckInterval.FIFTEEN_MINUTES);

	private static List<Candle> around(int level, int count)
	{
		List<Candle> series = new ArrayList<>(count);
		long now = Instant.now().getEpochSecond();
		for (int i = 0; i < count; i++)
		{
			int drift = (int) Math.round(level * 0.02 * Math.sin(i * 0.37));
			series.add(new Candle(now - (long) (count - i) * BUCKET_SECONDS,
				level + drift + 2, level + drift - 2, 4_000, 4_000));
		}
		return series;
	}

	private static List<Candle> falling(int endLevel, int count)
	{
		List<Candle> series = new ArrayList<>(count);
		long now = Instant.now().getEpochSecond();
		for (int i = 0; i < count; i++)
		{
			int price = endLevel + (count - 1 - i) * 4;
			series.add(new Candle(now - (long) (count - i) * BUCKET_SECONDS,
				price + 2, price - 2, 4_000, 4_000));
		}
		return series;
	}

	private static Position bought(int quantity, int unitCost, int target, long minutesHeld)
	{
		Position position = new Position(ITEM, "Grimy irit leaf", quantity,
			(long) quantity * unitCost, Instant.now().getEpochSecond() - minutesHeld * 60, true);
		position.setTargetSellPrice(target);
		return position;
	}

	private SellDecision decide(Position position, List<Candle> series, LatestPrice price,
		long minProfit)
	{
		ItemFeatures features = featureEngine.compute(ITEM, series, BUCKET_SECONDS);
		return engine.evaluate(position, price, features, series, horizon, Instant.now(), minProfit,
			false, false, false);
	}

	private SellDecision decide(int cost, int target, int marketSell, long minutesHeld)
	{
		return decide(bought(QUANTITY, cost, target, minutesHeld), around(marketSell, 250),
			new LatestPrice(marketSell, Instant.now().getEpochSecond() - 30, marketSell - 40,
				Instant.now().getEpochSecond() - 30), 0);
	}

	// ------------------------------------------------------------------ nothing is held

	@Test
	public void aPriceNowhereNearTheTargetIsStillSold()
	{
		// The live case this started from: 2,197 Grimy irit leaf held at 1,375, waiting for 1,449,
		// with the market at 1,330. It waited. It is listed now.
		SellDecision decision = decide(1_375, 1_449, 1_330, 20);

		assertTrue("a target that has not arrived is not a reason to keep the item: "
			+ decision.getReason(), decision.isSell());
		assertTrue("and the slot is reserved for it", decision.isExitNear());
	}

	@Test
	public void anExitWorthAlmostNothingIsStillSold()
	{
		// 50,000 Fire runes bought at 5 and worth 5: an exit that earns nothing. This used to be held
		// back by a floor at half the minimum profit per flip, which meant the position was stuck
		// until the horizon limit hours later -- carrying its risk and its capital the whole time to
		// save a slot it was going to spend anyway.
		SellDecision decision = decide(bought(50_000, 5, 5, 30), around(5, 250),
			new LatestPrice(5, Instant.now().getEpochSecond(), 4, Instant.now().getEpochSecond()),
			400_000);

		assertTrue("a small exit is still an exit: " + decision.getReason(), decision.isSell());
	}

	@Test
	public void aQuietBookIsStillSoldInto()
	{
		// "Nobody is buying this at the moment. Holding until they are." -- a quote with no live
		// price is missing information, not evidence that the item cannot be sold. The offer goes on
		// at the last price the item actually traded at.
		List<Candle> series = around(1_400, 250);
		SellDecision decision = decide(bought(QUANTITY, 1_375, 1_449, 20), series,
			new LatestPrice(null, null, null, null), 0);

		assertTrue("a silent quote is not a reason to hold: " + decision.getReason(),
			decision.isSell());
		assertTrue("and it is priced from what the item last traded at: " + decision.getPrice(),
			decision.getPrice() > 1_300 && decision.getPrice() < 1_500);
	}

	@Test
	public void aFallingMarketIsSoldAndSaysSo()
	{
		SellDecision decision = decide(bought(QUANTITY, 1_375, 1_449, 20), falling(1_330, 60),
			new LatestPrice(1_330, Instant.now().getEpochSecond(), 1_300,
				Instant.now().getEpochSecond()), 0);

		assertTrue(decision.isSell());
		assertTrue(decision.getReason().contains("falling"));
	}

	@Test
	public void thereIsNoMarketAtWhichAHoldingIsKept()
	{
		// Swept rather than sampled. The point of the rule is that it has no exceptions, so the
		// useful test is one that cannot be satisfied by adding another branch: every combination of
		// price, hold time and profit floor has to end in a sale.
		int cost = 1_375;
		for (int marketSell = 900; marketSell <= 2_000; marketSell += 50)
		{
			for (long minutesHeld : new long[]{0, 5, 60, 200, horizon.maxHoldMinutes() + 30})
			{
				for (long floor : new long[]{0, 50_000, 10_000_000})
				{
					SellDecision decision = decide(
						bought(QUANTITY, cost, 1_449, minutesHeld), around(marketSell, 250),
						new LatestPrice(marketSell, Instant.now().getEpochSecond(), marketSell - 40,
							Instant.now().getEpochSecond()), floor);

					assertTrue("held at " + marketSell + " after " + minutesHeld
							+ " minutes with a floor of " + floor + ": " + decision.getReason(),
						decision.isSell());
					assertTrue("and priced: " + decision.getPrice(), decision.getPrice() > 0);
				}
			}
		}
	}

	@Test
	public void anEmptyPositionIsNotASale()
	{
		// The one thing that is genuinely not a holding.
		assertTrue(!engine.evaluate(null, new LatestPrice(1_400, 0L, 1_360, 0L),
			ItemFeatures.unknown(ITEM), around(1_400, 50), horizon, Instant.now(), 0, false, false,
			false).isSell());
	}

	// ------------------------------------------------------------------ what it asks

	@Test
	public void aStopThatHasBeenHitIsACutAndCanCrossTheSpread()
	{
		// The stop survives the new rule, because it never was a reason to hold -- it is a reason to
		// stop asking for a good price. It keeps its own action so the card can warn that this one
		// realises a loss, and its pricing may reach the standing bid.
		Position position = bought(QUANTITY, 1_375, 1_449, 30);
		position.setStopPrice(1_420);
		SellDecision decision = decide(position, around(1_360, 250),
			new LatestPrice(1_400, Instant.now().getEpochSecond(), 1_320,
				Instant.now().getEpochSecond()), 0);

		assertEquals(SellDecision.Action.CUT, decision.getAction());
		assertTrue("a cut has to be able to price under the ordinary grid: " + decision.getPrice(),
			decision.getPrice() < 1_400 - Math.round(1_400 * 0.01));
	}

	@Test
	public void breakEvenIsNotALoss()
	{
		// A stop of round(5 x 0.92) is 5 on a five-gp item -- the price paid -- so every cheap item
		// used to be cut the instant the market touched break-even. The stop is placed below cost,
		// not at it.
		SellDecision decision = decide(bought(50_000, 5, 5, 30), around(5, 250),
			new LatestPrice(5, Instant.now().getEpochSecond(), 4, Instant.now().getEpochSecond()), 0);

		assertTrue("break-even is not a loss cut: " + decision.getReason(),
			decision.getAction() != SellDecision.Action.CUT);
	}

	@Test
	public void anOrdinarySaleDoesNotGiveTheSpreadAway()
	{
		// Selling immediately means listing immediately. It does not mean meeting the bid on a
		// position that is under no pressure at all: that would hand the spread to a buyer on every
		// flip the plugin ever recommends.
		int marketSell = 1_400;
		SellDecision decision = decide(bought(QUANTITY, 1_375, 1_449, 20), around(marketSell, 250),
			new LatestPrice(marketSell, Instant.now().getEpochSecond(), 1_320,
				Instant.now().getEpochSecond()), 0);

		assertTrue(decision.isSell());
		assertTrue("the ask stays near the top of the book: " + decision.getPrice(),
			decision.getPrice() >= marketSell - Math.round(marketSell * 0.011));
	}

	@Test
	public void everySaleReservesASlot()
	{
		// Everything held is a sale about to be placed, so everything held needs somewhere to go.
		assertTrue(decide(1_375, 1_449, 1_330, 0).isExitNear());
		assertTrue(decide(1_375, 1_449, 1_500, 0).isExitNear());
		assertTrue(decide(1_375, 1_449, 1_330, horizon.maxHoldMinutes() + 10).isExitNear());
	}
}
