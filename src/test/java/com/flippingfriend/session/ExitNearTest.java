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
 * Whether a holding is close enough to leaving to be worth a Grand Exchange slot.
 * <p>
 * The reservation used to fire for anything held at all, so a position waiting hours for a price it
 * might never reach kept a slot idle the whole time — 2,197 Grimy irit leaf held one open while the
 * engine waited for 1,449 gp. A slot is only worth holding when the exit is actually in sight, which
 * happens two ways: the price arrives, or the clock runs out.
 */
public class ExitNearTest
{
	private static final int BUCKET_SECONDS = 300;
	private static final int ITEM = 207;

	private final SellTimingEngine engine = new SellTimingEngine(new FillModel(), new TaxCalculator());
	private final FeatureEngine featureEngine = new FeatureEngine();
	private final TradingHorizon horizon =
		TradingHorizon.of(RiskProfile.MODERATE, CheckInterval.FIFTEEN_MINUTES);

	private static List<Candle> around(int level, int count)
	{
		Random random = new Random(5);
		List<Candle> series = new ArrayList<>();
		double drift = 0;
		for (int i = 0; i < count; i++)
		{
			drift = 0.6 * drift + random.nextGaussian() * level * 0.004;
			int low = (int) Math.max(1, Math.round(level + drift));
			series.add(new Candle(1_700_000_000L + i * BUCKET_SECONDS, low + 2, low, 400, 400));
		}
		return series;
	}

	private SellDecision decide(int cost, int target, int marketSell, long minutesHeld)
	{
		Position position = new Position(ITEM, "Grimy irit leaf", 2_197, (long) 2_197 * cost,
			Instant.now().getEpochSecond() - minutesHeld * 60, true);
		position.setTargetSellPrice(target);
		List<Candle> series = around(marketSell, 250);
		ItemFeatures features = featureEngine.compute(ITEM, series, BUCKET_SECONDS);
		LatestPrice price = new LatestPrice(marketSell, 0L, marketSell - 2, 0L);
		return engine.evaluate(position, price, features, series, horizon, Instant.now(), 0, false, false, false);
	}

	@Test
	public void aPriceFarFromTheTargetDoesNotEarnASlot()
	{
		// The live case: held at 1,375, waiting for 1,449, market nowhere near it.
		SellDecision decision = decide(1_375, 1_449, 1_330, 20);

		assertTrue("this is a hold", !decision.isSell());
		assertTrue("and nowhere near leaving, so it must not hold a slot open: " + decision.getReason(),
			!decision.isExitNear());
	}

	@Test
	public void aPriceAtTheTargetDoesEarnOne()
	{
		// Within a pricing step of the target, so the sale is one move away and the slot is needed.
		SellDecision decision = decide(1_375, 1_449, 1_449, 20);

		assertTrue("a slot has to be there when the price arrives", decision.isExitNear());
	}

	@Test
	public void aPositionNearItsDeadlineEarnsOneWhateverThePriceIsDoing()
	{
		// The timed exit fires on the clock regardless of price, so the slot is genuinely needed soon.
		SellDecision decision = decide(1_375, 1_449, 1_330,
			(long) (horizon.maxHoldMinutes() * 0.9));

		assertTrue("the clock will force this sale shortly", decision.isExitNear());
	}

	@Test
	public void aDecisionToSellIsAlwaysNear()
	{
		// Whatever else is true, a holding being sold needs somewhere to go.
		SellDecision selling = new SellDecision(SellDecision.Action.SELL, 1_449, "go", 10, 5_000);

		assertTrue(selling.isExitNear());
	}
}
