package com.flippingfriend.model;

import com.flippingfriend.data.Candle;
import com.flippingfriend.data.LatestPrice;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

/**
 * The spot price is one transaction, so it has to be sanity-checked against something weighted
 * before an offer is built from it. These cases cover both directions of that judgement: keep the
 * fresh number when it is credible, discard it when it is not.
 */
public class PriceAnchorTest
{
	private static Candle candle(Integer high, Integer low)
	{
		return new Candle(1_700_000_000L, high, low, 500, 500);
	}

	@Test
	public void keepsTheSpotPriceWhenItAgreesWithTheAverage()
	{
		LatestPrice latest = new LatestPrice(1010, 1L, 990, 2L);
		LatestPrice anchored = PriceAnchor.anchor(latest, candle(1005, 995));

		assertSame("an agreeing quote should pass through untouched", latest, anchored);
	}

	@Test
	public void discardsASpotPriceMilesFromTheAverage()
	{
		// Somebody bought one at 5,000 in a market trading around 1,000.
		LatestPrice latest = new LatestPrice(5000, 1L, 990, 2L);
		LatestPrice anchored = PriceAnchor.anchor(latest, candle(1005, 995));

		assertEquals("the outlier should be replaced by the average", Integer.valueOf(1005),
			anchored.getHigh());
		assertEquals("the credible side should be left alone", Integer.valueOf(990), anchored.getLow());
	}

	@Test
	public void handlesAnOutlierOnTheBuySide()
	{
		LatestPrice latest = new LatestPrice(1010, 1L, 10, 2L);
		LatestPrice anchored = PriceAnchor.anchor(latest, candle(1005, 995));

		assertEquals(Integer.valueOf(995), anchored.getLow());
		assertEquals(Integer.valueOf(1010), anchored.getHigh());
	}

	@Test
	public void keepsTheOriginalTimestamps()
	{
		// Staleness must still reflect when the market actually traded, not when we recomputed.
		LatestPrice latest = new LatestPrice(5000, 1234L, 990, 5678L);
		LatestPrice anchored = PriceAnchor.anchor(latest, candle(1005, 995));

		assertEquals(Long.valueOf(1234L), anchored.getHighTime());
		assertEquals(Long.valueOf(5678L), anchored.getLowTime());
	}

	@Test
	public void passesThroughWhenThereIsNothingToCompareAgainst()
	{
		LatestPrice latest = new LatestPrice(1010, 1L, 990, 2L);

		assertSame(latest, PriceAnchor.anchor(latest, null));
		assertSame(latest, PriceAnchor.anchor(latest, candle(null, null)));
		assertEquals(null, PriceAnchor.anchor(null, candle(1005, 995)));
	}

	@Test
	public void toleratesSmallMovesSoItDoesNotLagTheMarket()
	{
		// A 2% move is a real market move, not an outlier; overriding it would keep us behind.
		LatestPrice latest = new LatestPrice(1020, 1L, 1020, 2L);
		LatestPrice anchored = PriceAnchor.anchor(latest, candle(1000, 1000));

		assertSame(latest, anchored);
	}
}
