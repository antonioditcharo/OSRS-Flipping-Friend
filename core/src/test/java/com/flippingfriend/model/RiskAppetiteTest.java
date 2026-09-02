package com.flippingfriend.model;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Risk appetite decides what gets traded, never how much of the bankroll is available. These pin
 * both halves of that: the bold setting really is bolder in the ways that were measured to be worth
 * loosening, and no setting anywhere holds back the player's gold.
 */
public class RiskAppetiteTest
{
	@Test
	public void noSettingHoldsBackTheBankroll()
	{
		// The point of the redesign. A cautious setting used to cap deployment at a quarter of the
		// balance, which does not make a trade safer — it makes the same trade smaller. Whatever the
		// appetite, the exposure ceilings are diversification limits and never a fraction of the
		// bankroll withheld from use.
		for (RiskAppetite appetite : new RiskAppetite[]{
			RiskAppetite.CAUTIOUS, RiskAppetite.BALANCED, RiskAppetite.AGGRESSIVE})
		{
			assertTrue(appetite.getName() + " must let a single item take a real share",
				appetite.getItemExposureLimit() >= 0.35);
			assertTrue(appetite.getName() + " must not cap a correlated family below a third",
				appetite.getGroupExposureLimit() >= 0.5);
		}
	}

	@Test
	public void theBoldestSettingLiftsExposureCeilingsEntirely()
	{
		assertEquals("at the boldest setting only the buy limit and the market should bind",
			1.0, RiskAppetite.AGGRESSIVE.getItemExposureLimit(), 1e-9);
		assertEquals(1.0, RiskAppetite.AGGRESSIVE.getGroupExposureLimit(), 1e-9);
	}

	@Test
	public void boldnessClaimsMoreOfTheFlow()
	{
		// The lever that actually decides how much gold gets deployed. Buy limits allow tens of
		// millions per item; what caps an order is the share of trading we assume we can take.
		assertTrue(RiskAppetite.CAUTIOUS.getCaptureShare()
			< RiskAppetite.BALANCED.getCaptureShare());
		assertTrue(RiskAppetite.BALANCED.getCaptureShare()
			< RiskAppetite.AGGRESSIVE.getCaptureShare());
	}

	@Test
	public void boldnessPaysFurtherOverTheQuote()
	{
		// Sitting exactly at the quote reaches only what crosses there. Reaching deeper liquidity
		// means paying up, and how far is precisely a question of appetite.
		double cautious = furthest(RiskAppetite.CAUTIOUS.getBuyOffsets());
		double aggressive = furthest(RiskAppetite.AGGRESSIVE.getBuyOffsets());

		assertTrue("a bold setting should reach further past the quote: " + cautious + " vs "
			+ aggressive, aggressive > cautious * 4);
	}

	@Test
	public void boldnessWaitsLonger()
	{
		assertTrue(RiskAppetite.AGGRESSIVE.getHorizonHours()
			> RiskAppetite.CAUTIOUS.getHorizonHours());
	}

	@Test
	public void liquidityAndStalenessDoNotBendToAppetite()
	{
		// Measured against the learner's record, loosening the liquidity bar costs about a million gp
		// and loosening staleness about sixteen. An item nobody trades is not a braver trade, it is a
		// worse one, so this bar stays put at every level.
		double cautious = RiskAppetite.CAUTIOUS.getVetoes().getMinVolumeToLimitRatio();
		assertEquals(cautious, RiskAppetite.BALANCED.getVetoes().getMinVolumeToLimitRatio(), 1e-9);
		assertEquals(cautious, RiskAppetite.AGGRESSIVE.getVetoes().getMinVolumeToLimitRatio(), 1e-9);
	}

	@Test
	public void thePriceBarsLoosenWithAppetite()
	{
		// These are the ones the learner showed were turning away money: wide spreads, prices high in
		// their range, and jumpy items.
		assertTrue(RiskAppetite.CAUTIOUS.getVetoes().getMadRejectionK()
			< RiskAppetite.AGGRESSIVE.getVetoes().getMadRejectionK());
		assertTrue(RiskAppetite.CAUTIOUS.getVetoes().getMaxVolatility()
			< RiskAppetite.AGGRESSIVE.getVetoes().getMaxVolatility());
		assertTrue(RiskAppetite.CAUTIOUS.getVetoes().getMaxPricePercentile()
			< RiskAppetite.AGGRESSIVE.getVetoes().getMaxPricePercentile());
	}

	@Test
	public void everyLevelIsLooserOnPriceThanTheOldFixedSetting()
	{
		// The old single threshold rejected trades worth roughly nine million gp of measured upside.
		// Even the careful setting should be past it.
		assertTrue("the old bar of 3.5 was measured as too tight at every level",
			RiskAppetite.CAUTIOUS.getVetoes().getMadRejectionK() > 3.5);
	}

	@Test
	public void onlyTheBoldestBuysIntoAFallingPrice()
	{
		assertFalse(RiskAppetite.CAUTIOUS.getVetoes().isAllowTrendPlays());
		assertFalse(RiskAppetite.BALANCED.getVetoes().isAllowTrendPlays());
		assertTrue(RiskAppetite.AGGRESSIVE.getVetoes().isAllowTrendPlays());
	}

	@Test
	public void namesMapToSettingsAndAnythingUnknownIsBalanced()
	{
		assertEquals(RiskAppetite.CAUTIOUS, RiskAppetite.forName("LOW"));
		assertEquals(RiskAppetite.AGGRESSIVE, RiskAppetite.forName("HIGH"));
		assertEquals(RiskAppetite.BALANCED, RiskAppetite.forName("MODERATE"));
		assertEquals(RiskAppetite.BALANCED, RiskAppetite.forName(null));
		assertEquals(RiskAppetite.BALANCED, RiskAppetite.forName("nonsense"));
	}

	private static double furthest(double[] offsets)
	{
		double furthest = 0;
		for (double offset : offsets)
		{
			furthest = Math.max(furthest, Math.abs(offset));
		}
		return furthest;
	}
}
