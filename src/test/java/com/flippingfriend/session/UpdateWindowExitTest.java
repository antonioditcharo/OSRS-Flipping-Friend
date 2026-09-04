package com.flippingfriend.session;

import com.flippingfriend.CheckInterval;
import com.flippingfriend.RiskProfile;
import com.flippingfriend.TradingHorizon;
import com.flippingfriend.data.Candle;
import com.flippingfriend.data.LatestPrice;
import com.flippingfriend.model.FeatureEngine;
import com.flippingfriend.model.FillModel;
import com.flippingfriend.model.GameUpdateCalendar;
import com.flippingfriend.model.ItemFeatures;
import com.flippingfriend.model.TaxCalculator;
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
 * Out before the game changes, not after.
 *
 * <p>Every other input this engine has is derived from price history, and a game update is the one
 * event that cannot be: it has not happened yet, so no fit contains it, and the forecast that prices
 * the exit is describing a market that is about to stop existing. Holding a position through one is
 * taking a position on patch notes nobody here has read.
 *
 * <p>The rule is not a curfew. It is whether the sale would still be sitting in the queue when the
 * update lands — an exit that completes in ten minutes with four hours to go is fine, and the same
 * exit with five minutes to go is a coin flip on content.
 */
public class UpdateWindowExitTest
{
	private static final int BUCKET_SECONDS = 300;
	private static final int ITEM = 561;

	private final TaxCalculator tax = new TaxCalculator();
	private final FeatureEngine featureEngine = new FeatureEngine();
	private final TradingHorizon horizon =
		TradingHorizon.of(RiskProfile.MODERATE, CheckInterval.FIFTEEN_MINUTES);

	/** The next window after an arbitrary fixed moment, so nothing here depends on the wall clock. */
	private static final Instant WINDOW =
		GameUpdateCalendar.weekly().nextWindowStart(Instant.ofEpochSecond(1_700_000_000L));

	private static List<Candle> around(int level, int count)
	{
		Random random = new Random(11);
		List<Candle> series = new ArrayList<>();
		double drift = 0;
		for (int i = 0; i < count; i++)
		{
			drift = 0.6 * drift + random.nextGaussian() * level * 0.004;
			int high = (int) Math.max(2, Math.round(level + drift));
			series.add(new Candle(1_700_000_000L + i * BUCKET_SECONDS, high, high - 1, 5_000, 5_000));
		}
		return series;
	}

	private SellDecision decideAt(Instant now, SellTimingEngine engine, int marketSell)
	{
		List<Candle> series = around(1_100, 250);
		ItemFeatures features = featureEngine.compute(ITEM, series, BUCKET_SECONDS);
		Position position = new Position(ITEM, "Nature rune", 10_000, 10_000L * 1_000,
			now.getEpochSecond() - 30 * 60, true);
		position.setTargetSellPrice(1_200);
		LatestPrice latest = new LatestPrice(marketSell, 0L, marketSell - 2, 0L);
		return engine.evaluate(position, latest, features, series, horizon, now, 0, false, false,
			false);
	}

	private SellTimingEngine engine()
	{
		return new SellTimingEngine(new FillModel(), tax);
	}

	@Test
	public void aPositionThatCouldNotSellBeforeTheUpdateSellsNow()
	{
		// Two minutes before the window opens. Whatever the price is doing, this offer would still be
		// resting in the queue when the game changes, so the decision is to be out of it.
		SellDecision decision = decideAt(WINDOW.minus(Duration.ofMinutes(2)), engine(), 1_050);

		assertTrue("the sale must fire on the event: " + decision.getReason(), decision.isSell());
		assertEquals("A game update lands before this could sell. Exiting first.",
			decision.getReason());
		assertTrue("and it must hold its slot to do it", decision.isExitNear());
	}

	@Test
	public void aPositionWithTheWholeWeekAheadOfItIsLeftAlone()
	{
		// The same position, the same price, six days from an update. Nothing about the update should
		// touch it — a rule that fired on the day of the week rather than on the time remaining would
		// be a curfew, and would sell perfectly good positions every Wednesday morning.
		SellDecision decision =
			decideAt(WINDOW.minus(Duration.ofDays(6)), engine(), 1_050);

		assertFalse("nothing to do this far out: " + decision.getReason(), decision.isSell());
	}

	@Test
	public void theTargetWalksDownFasterWithAnUpdateComing()
	{
		// The mechanism, rather than the exit. reachableTarget asks what the market can still touch in
		// the time remaining, and an update ends that time — so nothing had to be added to the
		// walk-down for updates. Shortening the horizon was enough.
		SellTimingEngine engine = engine();
		List<Candle> series = around(1_100, 250);
		Position position = new Position(ITEM, "Nature rune", 10_000, 10_000L * 1_000,
			WINDOW.getEpochSecond() - 30 * 60, true);
		position.setTargetSellPrice(1_200);

		int clear = engine.reachableTarget(position, series, horizon, 30,
			WINDOW.minus(Duration.ofDays(3)));
		int imminent = engine.reachableTarget(position, series, horizon, 30,
			WINDOW.minus(Duration.ofMinutes(20)));

		assertTrue("both must be revised down from the entry target", clear < 1_200);
		assertTrue("but twenty minutes of market left buys less than six hours does: "
			+ imminent + " vs " + clear, imminent < clear);
	}

	@Test
	public void aCuratedUpdateIsObeyedLikeTheWeeklyOne()
	{
		// A league start or a release moved off its usual day. The engine takes the calendar it is
		// given, so a date somebody supplies changes the exit exactly as the weekly rhythm does.
		Instant quiet = WINDOW.minus(Duration.ofDays(3));
		SellTimingEngine standard = engine();
		SellTimingEngine informed = new SellTimingEngine(new FillModel(), tax,
			GameUpdateCalendar.weekly().withKnownUpdates(
				Collections.singletonList(quiet.plus(Duration.ofMinutes(2)))));

		assertFalse("the standing calendar knows of nothing here",
			decideAt(quiet, standard, 1_050).isSell());
		assertTrue("the informed one does: " + decideAt(quiet, informed, 1_050).getReason(),
			decideAt(quiet, informed, 1_050).isSell());
	}

	@Test
	public void theUpdateExitDoesNotOverrideTheProfitFloor()
	{
		// Ordering matters here. The hold-limit exit and the stop both sit above this in the method,
		// and so does the check that there is any point selling at all — an update is a reason to
		// stop waiting, not a reason to dump a position into a market that is not paying.
		SellTimingEngine engine = engine();
		List<Candle> series = around(1_100, 250);
		ItemFeatures features = featureEngine.compute(ITEM, series, BUCKET_SECONDS);
		Instant now = WINDOW.minus(Duration.ofMinutes(2));
		// Bought at 1,400 into a market at 1,050: every exit here is a loss.
		Position position = new Position(ITEM, "Nature rune", 10_000, 10_000L * 1_400,
			now.getEpochSecond() - 30 * 60, true);
		position.setTargetSellPrice(1_500);
		LatestPrice latest = new LatestPrice(1_050, 0L, 1_048, 0L);

		SellDecision decision = engine.evaluate(position, latest, features, series, horizon, now,
			0, false, false, false);

		assertTrue("whatever it decides, it must not be silent", decision.getReason() != null);
		assertFalse("an imminent update must not make a losing sale look mandatory: "
			+ decision.getReason(),
			decision.isSell() && decision.getReason().contains("game update")
				&& decision.getExpectedProfit() < 0);
	}
}
