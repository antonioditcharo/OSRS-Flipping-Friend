package com.flippingfriend.model;

import com.flippingfriend.RiskProfile;
import com.flippingfriend.data.Candle;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Position sizing is where a good idea becomes a bad trade. These cases check that every ceiling
 * actually binds, because any one of them failing silently is how a plugin ends up recommending an
 * order the game will not fill or the player cannot afford.
 */
public class PositionSizerTest
{
	private PositionSizer sizer;

	@Before
	public void setUp()
	{
		sizer = new PositionSizer();
	}

	private static final double LEG_HOURS = 1.0;
	private static final int PRICE = 100;

	/**
	 * A probe backed by the real fill model over a synthetic hour of trading.
	 * <p>
	 * These used to pass a flow figure and a horizon, and the sizer inverted a formula from those
	 * two numbers. That is what let it drift: the formula it inverted was the fill model's smooth
	 * branch while the shipping model uses the lumpy one, and nothing here could see the difference
	 * because the test reimplemented the same wrong formula to check the answer. Asking the model is
	 * the only version of this test that can fail when the model changes.
	 */
	private static java.util.function.IntToDoubleFunction flowOf(double unitsPerHour, double legHours)
	{
		int buckets = 12;
		int perBucket = (int) Math.max(1, Math.round(unitsPerHour / buckets));
		java.util.List<Candle> series = new java.util.ArrayList<>();
		for (int i = 0; i < buckets; i++)
		{
			series.add(new Candle(1_700_000_000L + i * 300L, PRICE + 2, PRICE, perBucket, perBucket));
		}
		FillCurve curve = FillCurve.from(series);
		FillModel model = new FillModel();
		return q -> model.estimateBuy(curve, PRICE, q, legHours).getProbability();
	}

	@Test
	public void neverExceedsTheBuyLimit()
	{
		int quantity = sizer.size(RiskProfile.HIGH, 1_000_000_000L, 100, 50, flowOf(100_000, LEG_HOURS), 0.10, 0.95);
		assertTrue("buy limit must bind", quantity <= 50);
	}

	@Test
	public void neverExceedsAvailableCoins()
	{
		// 10,000 coins at 1,000 each is ten items, before the profile's per-flip cap is applied.
		int quantity = sizer.size(RiskProfile.HIGH, 10_000, 1000, 10_000, flowOf(100_000, LEG_HOURS), 0.10, 0.95);
		assertTrue("cannot buy more than the coins allow", quantity <= 10);
	}

	@Test
	public void liquidityBindsOnThinItems()
	{
		// Coins and buy limit are effectively unlimited here, so only the market can constrain it.
		// Twelve traded per hour must not produce an order in the thousands.
		int thin = sizer.size(RiskProfile.HIGH, 1_000_000_000L, 100, 100_000, flowOf(12, LEG_HOURS), 0.10, 0.95);
		int busy = sizer.size(RiskProfile.HIGH, 1_000_000_000L, 100, 100_000, flowOf(12_000, LEG_HOURS), 0.10, 0.95);

		assertTrue("a thin market must produce a small order", thin < 30);
		assertTrue("and a busy one a much larger order", busy > thin * 100);
	}

	@Test
	public void returnsNothingWhenTheFlipCannotBeAfforded()
	{
		assertEquals(0, sizer.size(RiskProfile.MODERATE, 500, 1000, 100, flowOf(1000, LEG_HOURS), 0.05, 0.9));
	}

	@Test
	public void returnsNothingWhenTheBuyLimitIsSpent()
	{
		assertEquals(0, sizer.size(RiskProfile.MODERATE, 1_000_000, 100, 0, flowOf(1000, LEG_HOURS), 0.05, 0.9));
	}

	@Test
	public void riskierProfilesCommitMoreCapital()
	{
		long coins = 100_000_000L;
		int low = sizer.size(RiskProfile.LOW, coins, 1000, 1_000_000, flowOf(1_000_000, LEG_HOURS), 0.05, 0.9);
		int moderate = sizer.size(RiskProfile.MODERATE, coins, 1000, 1_000_000, flowOf(1_000_000, LEG_HOURS), 0.05, 0.9);
		int high = sizer.size(RiskProfile.HIGH, coins, 1000, 1_000_000, flowOf(1_000_000, LEG_HOURS), 0.05, 0.9);

		assertTrue("moderate should stake at least as much as low", moderate >= low);
		assertTrue("high should stake at least as much as moderate", high >= moderate);
	}

	@Test
	public void sizeIsChosenSoTheOrderActuallyFillsInTime()
	{
		// The whole point of deriving the ceiling from the fill model: an order sized this way,
		// fed back through the model, must clear the probability the profile demands.
		FillModel model = new FillModel();

		for (RiskProfile profile : RiskProfile.values())
		{
			double unitsPerHour = 1200;
			double legHours = profile.getMaxHoldMinutes() / 120.0;
			int quantity = sizer.liquidityCeiling(profile, flowOf(unitsPerHour, legHours), 1_000_000);

			double achieved = flowOf(unitsPerHour, legHours).applyAsDouble(quantity);
			double requiredPerLeg = Math.sqrt(profile.getMinFillProbability());

			assertTrue(profile + " sized too large to fill in time",
				achieved >= requiredPerLeg - 0.02);
			assertTrue(profile + " should still allow a meaningful order", quantity > 0);
		}
	}

	@Test
	public void cautiousProfilesTradeSmallerForTheSameFlow()
	{
		// Same item, same horizon: a profile that demands more certainty must order less.
		int low = sizer.liquidityCeiling(RiskProfile.LOW, flowOf(1000, 1.0), 1_000_000);
		int high = sizer.liquidityCeiling(RiskProfile.HIGH, flowOf(1000, 1.0), 1_000_000);

		assertTrue("a stricter fill requirement must produce a smaller order", low < high);
	}

	@Test
	public void aLargeBankLetsTheBuyLimitBeTheCeiling()
	{
		// Capital binds on only about 25 of the 441 liquid items worth trading. When the bank could
		// fund the position many times over, staking a Kelly fraction of it just leaves buy limit
		// unused — and unused limit is throughput that cannot be recovered later.
		int limit = 1000;
		int price = 1000;                       // a full-limit position costs 1m
		long smallBank = 2_000_000L;            // capital is scarce: Kelly should still bite
		long largeBank = 100_000_000L;          // capital is abundant: the limit should govern

		int constrained = sizer.size(RiskProfile.MODERATE, smallBank, price, limit, flowOf(100_000, LEG_HOURS), 0.05, 0.9);
		int unconstrained = sizer.size(RiskProfile.MODERATE, largeBank, price, limit, flowOf(100_000, LEG_HOURS), 0.05, 0.9);

		assertTrue("a large bank should order more", unconstrained > constrained);
		assertEquals("and should go all the way to the buy limit", limit, unconstrained);
	}

	@Test
	public void aSmallBankIsStillProtectedByKelly()
	{
		// The guard must not disappear for the players who actually need it.
		int quantity = sizer.size(RiskProfile.MODERATE, 500_000, 1000, 1000, flowOf(100_000, LEG_HOURS),
			0.05, 0.9);

		assertTrue("a small bank must not be fully committed", quantity < 500);
	}

	@Test
	public void kellyStakesNothingOnANegativeEdge()
	{
		// A 20% chance of a 1% gain against a 5% loss is a losing bet, so the stake should be zero.
		assertEquals(0.0, sizer.kelly(RiskProfile.MODERATE, 0.01, 0.20), 1e-9);
	}

	@Test
	public void kellyStakesMoreAsTheEdgeImproves()
	{
		double weak = sizer.kelly(RiskProfile.HIGH, 0.05, 0.60);
		double strong = sizer.kelly(RiskProfile.HIGH, 0.05, 0.95);

		assertTrue(strong > weak);
	}

	@Test
	public void kellyNeverExceedsTheProfileCeiling()
	{
		for (RiskProfile profile : RiskProfile.values())
		{
			double stake = sizer.kelly(profile, 0.50, 1.0);
			assertTrue(profile + " exceeded its own per-flip cap",
				stake <= profile.getMaxCapitalFraction() + 1e-9);
		}
	}
}
