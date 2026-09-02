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
import java.util.Random;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

/**
 * Whether an exit is worth the slot it spends.
 * <p>
 * 50,000 Fire runes went out at exactly their buy price: not a loss, but hours of a Grand Exchange
 * slot to earn nothing, and two more flips did the same. The bar is half the whole-trade floor,
 * because that setting asks whether a trade is worth two slot-uses and the capital, and an exit is
 * the second leg of a trade whose capital is already committed.
 * <p>
 * The danger of any such floor is trapping a position it refuses to let go of, so what matters as
 * much as the refusal is what stays exempt.
 */
public class ExitFloorTest
{
	private static final int BUCKET_SECONDS = 300;
	private static final int FIRE_RUNE = 554;
	private static final long FLOOR = 5_000;

	private final SellTimingEngine engine = new SellTimingEngine(new FillModel(), new TaxCalculator());
	private final FeatureEngine featureEngine = new FeatureEngine();
	private final TradingHorizon horizon =
		TradingHorizon.of(RiskProfile.MODERATE, CheckInterval.FIFTEEN_MINUTES);

	private static List<Candle> flat(int price, int count)
	{
		Random random = new Random(3);
		List<Candle> series = new ArrayList<>();
		for (int i = 0; i < count; i++)
		{
			int low = Math.max(1, price + (int) Math.round(random.nextGaussian()));
			series.add(new Candle(1_700_000_000L + i * BUCKET_SECONDS, low + 1, low, 5000, 5000));
		}
		return series;
	}

	private SellDecision decide(Position position, int marketSell, long minutesHeld)
	{
		List<Candle> series = flat(marketSell, 250);
		ItemFeatures features = featureEngine.compute(FIRE_RUNE, series, BUCKET_SECONDS);
		LatestPrice price = new LatestPrice(marketSell, 0L, marketSell - 1, 0L);
		return engine.evaluate(position, price, features, series, horizon, Instant.now(), FLOOR, false, false, false);
	}

	private static Position bought(int quantity, int unitCost, int target, long minutesHeld)
	{
		Position position = new Position(FIRE_RUNE, "Fire rune", quantity,
			(long) quantity * unitCost, Instant.now().getEpochSecond() - minutesHeld * 60, true);
		position.setTargetSellPrice(target);
		return position;
	}

	@Test
	public void aCheapItemHasAWorkingStopRatherThanOneOnItsOwnPurchasePrice()
	{
		// The Fire rune case, and the real cause of it. A stop of round(5 x 0.92) is 5 -- the price
		// paid -- so the position was cut the instant the market touched break-even. Every item under
		// about ten gp had no working stop at all.
		SellDecision decision = decide(bought(50_000, 5, 5, 30), 5, 30);

		assertTrue("break-even is not a loss and must not trigger a loss cut: " + decision.getReason(),
			decision.getAction() != SellDecision.Action.CUT);
	}

	@Test
	public void aRoundTripWorthAlmostNothingIsNotSuggested()
	{
		// With the stop no longer firing, the floor is what stops this: nothing is earned by selling
		// 50,000 runes at what they cost, and a slot is spent for hours doing it.
		SellDecision decision = decide(bought(50_000, 5, 5, 30), 5, 30);

		assertTrue("selling for nothing is not a trade: " + decision.getReason(),
			!decision.isSell() || decision.getExpectedProfit() >= FLOOR / 2);
	}

	@Test
	public void aWorthwhileExitIsStillSuggested()
	{
		// The floor must not swallow real trades. 50,000 runes at a gp each clears it many times over.
		SellDecision decision = decide(bought(50_000, 5, 6, 30), 6, 30);

		assertTrue("a genuine profit still goes: " + decision.getReason(), decision.isSell());
	}

	@Test
	public void theCardSaysTheRealReasonRatherThanContradictingItself()
	{
		// The floor holds this back with the target already met, so the ordinary "waiting for a price"
		// message would read "Waiting for 5 gp. Currently 5 gp." -- the same self-contradiction fixed
		// earlier for a different cause. The reason has to name the profit, not the price.
		SellDecision decision = decide(bought(50_000, 5, 5, 30), 5, 30);

		assertTrue("it must not claim to be waiting for a price it already has: " + decision.getReason(),
			!decision.getReason().startsWith("Waiting for"));
		assertTrue("it has to say what is actually wrong: " + decision.getReason(),
			decision.getReason().contains("not worth a Grand Exchange slot"));
	}

	@Test
	public void aPositionPastItsHoldLimitLeavesRegardless()
	{
		// The exemption that stops the floor becoming a trap. Held beyond the plan, the slot and the
		// coins are worth more than the last of the profit -- floor or no floor.
		SellDecision decision = decide(bought(50_000, 5, 5, horizon.maxHoldMinutes() + 10), 5,
			horizon.maxHoldMinutes() + 10);

		assertTrue("a position out of time must always be able to leave: " + decision.getReason(),
			decision.isSell());
	}

	@Test
	public void aLossCutIsNeverBlocked()
	{
		// The most important exemption of all: the floor must never stand between a falling position
		// and the exit.
		Position position = bought(50_000, 10, 12, 30);
		position.setStopPrice(9);
		SellDecision decision = decide(position, 8, 30);

		assertTrue("a stop-loss outranks every filter: " + decision.getReason(), decision.isSell());
	}
}
