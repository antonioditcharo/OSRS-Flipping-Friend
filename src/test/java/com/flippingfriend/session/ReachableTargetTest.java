package com.flippingfriend.session;

import com.flippingfriend.CheckInterval;
import com.flippingfriend.RiskProfile;
import com.flippingfriend.TradingHorizon;
import com.flippingfriend.data.Candle;
import com.flippingfriend.data.LatestPrice;
import com.flippingfriend.model.FeatureEngine;
import com.flippingfriend.model.FillModel;
import com.flippingfriend.model.ItemFeatures;
import com.flippingfriend.model.PriceForecast;
import com.flippingfriend.model.TaxCalculator;
import com.flippingfriend.model.GameUpdateCalendar;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The sell target is revised as the horizon runs out, instead of being held until the clock forces
 * an exit at whatever the market happens to be doing.
 * <p>
 * A target set at entry is a claim about a price that was reachable <em>then</em>. Five hours later
 * it is a claim about a price reachable in the hour that remains, and it was never checked against
 * that. The failure mode is not exotic: the position sits at its original ask, the slot stays
 * occupied, nothing happens, and the hold limit eventually dumps it at market — the worst of both,
 * because the patience bought nothing and the exit was forced anyway.
 * <p>
 * What separates this from walking the ask down on a timer is where the number comes from.
 * {@link PriceForecast#reachableWithin} is measured on the item's own history, so the give-up rate is
 * the item's: a volatile item keeps a high target longer because it can still get there, and a placid
 * one gives up sooner because it cannot. {@link #aVolatileItemKeepsItsAmbitionLongerThanAPlacidOne()}
 * is the test that would fail if this were secretly a schedule.
 */
public class ReachableTargetTest
{
	private static final int BUCKET_SECONDS = 300;
	private static final int ITEM = 561;

	/**
	 * Three days before a game update, and therefore four days after the last one.
	 *
	 * <p>Not {@code Instant.now()}, which is what this used before the calendar existed. The engine
	 * now shortens a position's horizon to end at the next update, so a test running on a Tuesday
	 * afternoon would exercise a different code path from the same test on a Thursday — and would
	 * pass six days a week. Derived from the calendar rather than hardcoded as a date, so it stays
	 * three days clear however the window is defined.
	 */
	private static final Instant NOW =
		GameUpdateCalendar.weekly().nextWindowStart(Instant.ofEpochSecond(1_700_000_000L))
			.minus(Duration.ofDays(3));

	private final TaxCalculator tax = new TaxCalculator();
	private final SellTimingEngine engine = new SellTimingEngine(new FillModel(), tax);
	private final FeatureEngine featureEngine = new FeatureEngine();
	private final TradingHorizon horizon =
		TradingHorizon.of(RiskProfile.MODERATE, CheckInterval.FIFTEEN_MINUTES);

	/** A flat market around {@code level}, with {@code wobble} as the per-step relative shock. */
	private static List<Candle> around(int level, double wobble, int count)
	{
		Random random = new Random(11);
		List<Candle> series = new ArrayList<>();
		double drift = 0;
		for (int i = 0; i < count; i++)
		{
			drift = 0.6 * drift + random.nextGaussian() * level * wobble;
			int high = (int) Math.max(2, Math.round(level + drift));
			series.add(new Candle(1_700_000_000L + i * BUCKET_SECONDS, high, high - 1, 5_000, 5_000));
		}
		return series;
	}

	private Position position(int cost, int target, long minutesHeld)
	{
		Position position = new Position(ITEM, "Nature rune", 10_000, 10_000L * cost,
			NOW.getEpochSecond() - minutesHeld * 60, true);
		position.setTargetSellPrice(target);
		return position;
	}

	private int targetAfter(int cost, int entryTarget, List<Candle> series, long minutesHeld)
	{
		return engine.reachableTarget(position(cost, entryTarget, minutesHeld), series, horizon,
			minutesHeld, NOW);
	}

	private int holdLimit()
	{
		return horizon.getProfile().getMaxHoldMinutes();
	}

	@Test
	public void anUnreachableTargetIsWalkedDownAsTheHorizonRunsOut()
	{
		// Bought at 1,000, hoped for 1,200, and the market settled at 1,100 and stayed there.
		List<Candle> series = around(1_100, 0.004, 250);

		int early = targetAfter(1_000, 1_200, series, 5);
		int late = targetAfter(1_000, 1_200, series, holdLimit() - 30);

		assertTrue("a target the item's own history never reaches must be revised: " + early,
			early < 1_200);
		assertTrue("and it must genuinely travel as the time goes: " + late + " vs " + early,
			late < early);
	}

	@Test
	public void theWalkDownIsMonotoneInTimeElapsed()
	{
		// The decay is a property of the forecast rather than a schedule - but it still has to be a
		// decay, at every step and not merely between the ends. This is the assertion that failed
		// against the endpoint quantile, which drifted back up by a coin as its band jittered.
		List<Candle> series = around(1_100, 0.004, 250);
		int limit = holdLimit();

		int previous = Integer.MAX_VALUE;
		for (long held = 0; held < limit; held += Math.max(1, limit / 24))
		{
			int target = targetAfter(1_000, 1_200, series, held);
			assertTrue("the target must never rise as time runs out: at " + held + "m it was "
				+ target + ", after " + previous, target <= previous);
			previous = target;
		}
	}

	@Test
	public void aVolatileItemKeepsItsAmbitionLongerThanAPlacidOne()
	{
		// The whole argument for a model over a timer. Same price, same elapsed time, same entry
		// target: the only difference is how far the item actually travels, and that is what decides
		// how long a high ask stays defensible.
		long held = holdLimit() / 2;

		int volatileTarget = targetAfter(1_000, 1_200, around(1_100, 0.010, 250), held);
		int placidTarget = targetAfter(1_000, 1_200, around(1_100, 0.0005, 250), held);

		assertTrue("an item that still moves can still reach higher: " + volatileTarget + " vs "
			+ placidTarget, volatileTarget > placidTarget);
		assertTrue("and a placid one is pinned near its own level: " + placidTarget,
			placidTarget < 1_105);
	}

	@Test
	public void theTargetIsNeverWalkedBelowBreakEven()
	{
		// The market has fallen well under the cost basis. Chasing it down realises the loss, and the
		// stop and the hold limit are the things allowed to do that - deliberately, and named.
		List<Candle> series = around(800, 0.004, 250);
		int breakEven = tax.breakEvenSellPrice(ITEM, 1_000);

		int target = targetAfter(1_000, 1_200, series, holdLimit() - 5);

		assertEquals("a losing sale is never the revised target", breakEven, target);
		assertTrue("and break-even is above the buy price, because the sale is taxed",
			breakEven > 1_000);
	}

	@Test
	public void theTargetIsNeverWalkedUpwards()
	{
		// The market has run past the entry target. Raising the ask would override the sizing decision
		// that justified the trade, and would hold a slot open chasing a price the plan never wanted.
		List<Candle> series = around(1_400, 0.004, 250);

		assertEquals(1_200, targetAfter(1_000, 1_200, series, 20));
	}

	@Test
	public void anUnusableForecastLeavesTheOriginalTargetAlone()
	{
		// Too little history to fit, so there is no revision to make: a quantile from four candles is
		// a number about four candles.
		assertEquals(1_200, targetAfter(1_000, 1_200, around(1_100, 0.004, 4), 20));
		assertEquals(1_200, targetAfter(1_000, 1_200, Collections.emptyList(), 20));
		assertEquals(1_200, targetAfter(1_000, 1_200, null, 20));
	}

	@Test
	public void aForecastTooWideToMeanAnythingIsRefused()
	{
		// A band spanning a quarter of the price says nothing about where this will be in an hour, and
		// a number drawn from it would move the target on the strength of noise.
		List<Candle> series = around(1_100, 0.15, 250);
		PriceForecast forecast = PriceForecast.fit(series, BUCKET_SECONDS);

		assertTrue("the premise: this series really is past the usable band",
			forecast.relativeSpread(PriceForecast.Side.HIGH, 5) > 0.25);
		assertEquals("noise must not move the target", 1_200,
			targetAfter(1_000, 1_200, series, 20));
	}

	@Test
	public void aPositionWithNoKnownCostHasNoBreakEvenFloor()
	{
		// Inherited holdings have no cost basis. Treating an unknown cost as a floor of zero is right;
		// inventing one would put a floor under the target that no evidence supports.
		Position inherited = Position.preExisting(ITEM, "Nature rune", 10_000,
			NOW.getEpochSecond() - 30 * 60);
		inherited.setTargetSellPrice(1_200);

		int target = engine.reachableTarget(inherited, around(900, 0.004, 250), horizon, 30, NOW);

		assertTrue("without a cost basis the forecast is the only floor: " + target, target < 1_000);
	}

	// --- arrival: the revised target has to reach the decision, or none of the above matters ---

	@Test
	public void theSamePriceIsRefusedEarlyAndTakenLate()
	{
		// The mechanism, end to end and at one price. Held at 1,000 against an entry target of 1,200
		// the market never approached; 1,106 is on offer throughout. Early there are hours of chances
		// left to beat it, so it is refused. Late there are not, so it is taken - and on the entry
		// target alone this position would have sat there until the clock dumped it at market.
		List<Candle> series = around(1_100, 0.004, 250);
		ItemFeatures features = featureEngine.compute(ITEM, series, BUCKET_SECONDS);
		int marketSell = 1_106;
		LatestPrice latest = new LatestPrice(marketSell, 0L, marketSell - 2, 0L);

		SellDecision early = decide(position(1_000, 1_200, 30), latest, features, series, 30);
		long late = holdLimit() - 30;
		SellDecision atTheEnd = decide(position(1_000, 1_200, late), latest, features, series, late);

		assertTrue("the premise: the market never reaches the entry target", marketSell < 1_200);
		assertFalse("with the horizon in hand this is worth holding out on: " + early.getReason(),
			early.isSell());
		assertTrue("with it nearly gone the same price is the one to take: " + atTheEnd.getReason(),
			atTheEnd.isSell());
		assertEquals("Target price reached. Selling.", atTheEnd.getReason());
	}

	@Test
	public void aTargetStillWithinReachIsStillWaitedFor()
	{
		// The other half of the same claim. Revising down must not turn every holding into an
		// immediate sale - a price the market can still get to is still worth the slot.
		List<Candle> series = around(1_100, 0.004, 250);
		ItemFeatures features = featureEngine.compute(ITEM, series, BUCKET_SECONDS);
		int marketSell = 1_060;
		LatestPrice latest = new LatestPrice(marketSell, 0L, marketSell - 2, 0L);

		SellDecision decision = decide(position(1_000, 1_150, 10), latest, features, series, 10);

		assertFalse("this one is still on its way: " + decision.getReason(), decision.isSell());
	}

	private SellDecision decide(Position position, LatestPrice latest, ItemFeatures features,
		List<Candle> series, long minutesHeld)
	{
		Instant now = Instant.ofEpochSecond(position.getOpenedAt() + minutesHeld * 60);
		return engine.evaluate(position, latest, features, series, horizon, now, 0, false, false,
			false);
	}

	@Test
	public void theseTestsRunFarFromAnyUpdateWindow()
	{
		// The premise every other test here rests on. The engine now ends a position's horizon at the
		// next game update, so a fixture that drifted into an update window would quietly be
		// exercising a different mechanism and still passing.
		assertTrue("the fixture must leave the whole horizon free of updates",
			GameUpdateCalendar.weekly().hoursUntilNext(NOW) > 48);
	}
}
