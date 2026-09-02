package com.flippingfriend;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * How often the player checks the Grand Exchange has to actually change the trades they are given,
 * otherwise the setting is decoration. These cases pin down that it does, and in the right
 * direction.
 */
public class TradingHorizonTest
{
	/**
	 * The two settings were one, and that made a whole class of request impossible to express: slow
	 * flips while standing at the Grand Exchange. Holding time is now asked for directly, and the
	 * checking habit keeps only the jobs it was right about.
	 */
	@Test
	public void howLongAFlipTakesIsSeparateFromHowOftenYouLook()
	{
		// Standing at the exchange, but wanting patient trades.
		TradingHorizon patient = TradingHorizon.of(RiskProfile.MODERATE, CheckInterval.CONSTANT, 480);
		TradingHorizon impatient = TradingHorizon.of(RiskProfile.MODERATE, CheckInterval.CONSTANT, 15);

		assertTrue("asking for eight hours has to actually buy eight hours: "
			+ patient.maxHoldMinutes(), patient.maxHoldMinutes() >= 480);
		assertTrue("and asking for fifteen minutes must not", impatient.maxHoldMinutes() < 480);
		assertTrue("a longer hold sizes bigger orders",
			patient.legHorizonHours() > impatient.legHorizonHours());
		assertEquals("neither changes when an unfilled offer is worth revisiting",
			patient.staleOfferMinutes(), impatient.staleOfferMinutes());
	}

	@Test
	public void aRoundTripStillCannotFinishFasterThanYouComeBack()
	{
		// The floor that survived: you cannot place the second leg before you return, whatever holding
		// time was asked for.
		TradingHorizon horizon = TradingHorizon.of(RiskProfile.HIGH, CheckInterval.OCCASIONAL, 5);

		assertTrue("five minutes is not achievable when you look every three hours: "
			+ horizon.maxHoldMinutes(),
			horizon.maxHoldMinutes() >= CheckInterval.OCCASIONAL.getMinutes() * 2);
	}

	@Test
	public void notAskingLeavesTheRiskProfileInCharge()
	{
		assertEquals("zero means 'you decide', not 'no time at all'",
			TradingHorizon.of(RiskProfile.MODERATE, CheckInterval.FIFTEEN_MINUTES).maxHoldMinutes(),
			TradingHorizon.of(RiskProfile.MODERATE, CheckInterval.FIFTEEN_MINUTES, 0).maxHoldMinutes());
	}

	@Test
	public void checkingRarelyLengthensTheFillWindow()
	{
		// The habit sets a floor and the profile a ceiling, so the interval shows up wherever the
		// profile has not already allowed more patience than the player will exercise. Low is the
		// clearest case: it only tolerates 90 minute round trips, so a player who checks every few
		// hours genuinely changes what can be planned.
		TradingHorizon attentive = TradingHorizon.of(RiskProfile.LOW, CheckInterval.CONSTANT);
		TradingHorizon occasional = TradingHorizon.of(RiskProfile.LOW, CheckInterval.OCCASIONAL);

		assertTrue("someone checking every few hours should get longer-filling trades",
			occasional.legHorizonHours() > attentive.legHorizonHours());
	}

	@Test
	public void aPatientProfileIsNotShortenedByCheckingOften()
	{
		// Flipping faster does not beat the buy limit: a slot that turns over twelve times an hour
		// exhausts the same four-hour allowance as one that turns over once, then sits idle. So a
		// profile that already tolerates long fills should not be cut back just because the player
		// happens to be watching.
		TradingHorizon attentive = TradingHorizon.of(RiskProfile.HIGH, CheckInterval.CONSTANT);

		assertTrue("patience should survive an attentive player",
			attentive.legHorizonHours() >= RiskProfile.HIGH.getMaxHoldMinutes() / 120.0);
	}

	@Test
	public void aFlipCannotBeShorterThanTwoVisits()
	{
		// Low risk wants 45 minute round trips, but if you only look once an hour, two hours is the
		// floor: you cannot place the sell before you come back.
		TradingHorizon horizon = TradingHorizon.of(RiskProfile.LOW, CheckInterval.HOURLY);

		assertTrue(horizon.maxHoldMinutes() >= 120);
	}

	@Test
	public void anAttentivePlayerKeepsTheProfileTiming()
	{
		TradingHorizon horizon = TradingHorizon.of(RiskProfile.LOW, CheckInterval.CONSTANT);

		assertEquals("the profile should govern when the player is always present",
			RiskProfile.LOW.getMaxHoldMinutes(), horizon.maxHoldMinutes());
	}

	@Test
	public void staleOfferWarningsWaitAtLeastOneCheckingCycle()
	{
		// Nagging about an offer the player will not see for another half hour is noise.
		assertTrue(TradingHorizon.of(RiskProfile.MODERATE, CheckInterval.THIRTY_MINUTES)
			.staleOfferMinutes() >= 30);

		// But an attentive player still gets a prompt reasonably quickly.
		assertTrue(TradingHorizon.of(RiskProfile.MODERATE, CheckInterval.CONSTANT)
			.staleOfferMinutes() <= 10);
	}

	/** 1,200 units an hour of real flow, judged by the real fill model over the given leg horizon. */
	private static java.util.function.IntToDoubleFunction flow(double legHours)
	{
		java.util.List<com.flippingfriend.data.Candle> series = new java.util.ArrayList<>();
		for (int i = 0; i < 12; i++)
		{
			series.add(new com.flippingfriend.data.Candle(1_700_000_000L + i * 300L, 102, 100, 100, 100));
		}
		com.flippingfriend.model.FillCurve curve =
			com.flippingfriend.model.FillCurve.from(series);
		com.flippingfriend.model.FillModel model = new com.flippingfriend.model.FillModel();
		return q -> model.estimateBuy(curve, 100, q, legHours).getProbability();
	}

	@Test
	public void longerHorizonsAllowLargerOrders()
	{
		// The sizer works off the leg horizon, so this is what actually makes an infrequent checker
		// get chunkier trades rather than the same trade with a longer deadline. Coins and buy limit
		// are set high enough that only liquidity can bind.
		com.flippingfriend.model.PositionSizer sizer = new com.flippingfriend.model.PositionSizer();

		int attentive = sizer.size(RiskProfile.LOW, 10_000_000_000L, 100, 10_000_000,
			flow(TradingHorizon.of(RiskProfile.LOW, CheckInterval.CONSTANT).legHorizonHours()),
			0.05, 0.9);
		int occasional = sizer.size(RiskProfile.LOW, 10_000_000_000L, 100, 10_000_000,
			flow(TradingHorizon.of(RiskProfile.LOW, CheckInterval.OCCASIONAL).legHorizonHours()),
			0.05, 0.9);

		assertTrue("checking less often should permit a bigger order", occasional > attentive);
	}

	@Test
	public void survivesMissingSettings()
	{
		TradingHorizon horizon = TradingHorizon.of(null, null);
		assertEquals(RiskProfile.MODERATE, horizon.getProfile());
		assertTrue(horizon.maxHoldMinutes() > 0);
		assertTrue(horizon.legHorizonHours() > 0);
	}

	@Test
	public void describesItselfInPlainWords()
	{
		String text = TradingHorizon.of(RiskProfile.HIGH, CheckInterval.HOURLY).describe();
		assertTrue(text.toLowerCase().contains("high"));
		assertTrue(text.toLowerCase().contains("hour"));
	}

}
