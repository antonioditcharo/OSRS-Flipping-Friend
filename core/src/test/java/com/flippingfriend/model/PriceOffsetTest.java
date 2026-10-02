package com.flippingfriend.model;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class PriceOffsetTest
{
	@Test
	public void zeroOffsetPreservesThePrice()
	{
		assertEquals(1_000, PriceOffset.apply(1_000, 0.0));
	}

	@Test
	public void ordinaryOffsetsUseEstablishedRounding()
	{
		assertEquals(1_012, PriceOffset.apply(1_000, 0.012));
		assertEquals(988, PriceOffset.apply(1_000, -0.012));
	}

	@Test
	public void nonzeroTinyOffsetsStillMoveOneCoin()
	{
		assertEquals(6, PriceOffset.apply(5, 0.0015));
		assertEquals(4, PriceOffset.apply(5, -0.0015));
	}

	@Test
	public void priceNeverFallsBelowOne()
	{
		assertEquals(1, PriceOffset.apply(1, -0.035));
		assertEquals(1, PriceOffset.apply(1, -2.0));
	}

	@Test
	public void supportedAppetiteOffsetsRemainDeterministic()
	{
		assertEquals(10_040, PriceOffset.apply(10_000, RiskAppetite.CAUTIOUS.getBuyOffsets()[2]));
		assertEquals(10_120, PriceOffset.apply(10_000, RiskAppetite.BALANCED.getBuyOffsets()[3]));
		assertEquals(10_350, PriceOffset.apply(10_000, RiskAppetite.AGGRESSIVE.getBuyOffsets()[4]));
		assertEquals(10_350, PriceOffset.apply(10_000, RiskAppetite.AGGRESSIVE.getBuyOffsets()[4]));
	}
}
