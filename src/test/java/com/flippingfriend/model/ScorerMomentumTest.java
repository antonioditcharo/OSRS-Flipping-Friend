package com.flippingfriend.model;

import com.flippingfriend.CheckInterval;
import com.flippingfriend.RiskProfile;
import com.flippingfriend.TradingHorizon;
import com.flippingfriend.data.Candle;
import com.flippingfriend.data.ItemMetadata;
import com.flippingfriend.data.LatestPrice;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * What the price forecaster is allowed to do to a trade's pricing.
 * <p>
 * The header of {@link Scorer} promises that "the price is optimised, not assumed" — a grid of
 * prices around the spread is swept and the pair that maximises hourly profit wins. A forecaster
 * was then wired in that threw the sell half of that grid away whenever it answered, replacing it
 * with the single price {@code quotedSell * (1 + momentum)}. The trade between margin and speed
 * stopped being made at all, and a negative forecast pushed the ask under the buy price, at which
 * point the item produced no trade rather than a slower one.
 * <p>
 * The forecast predicts one five-minute bucket of smoothed return. It is evidence about direction
 * for a flip that may run for hours, so it tilts the grid and does not replace it.
 */
public class ScorerMomentumTest
{
	private static final int BUCKET_SECONDS = 300;
	private static final int ITEM = 2361;
	private static final int QUOTED_BUY = 1_000;
	private static final int QUOTED_SELL = 1_050;

	private final TaxCalculator tax = new TaxCalculator();
	private final Scorer scorer =
		new Scorer(tax, new FillModel(), new PositionSizer(), new Calibrator());
	private final FeatureEngine featureEngine = new FeatureEngine();
	private final TradingHorizon horizon =
		TradingHorizon.of(RiskProfile.MODERATE, CheckInterval.FIFTEEN_MINUTES);

	private static List<Candle> busy(int count)
	{
		List<Candle> series = new ArrayList<>();
		for (int i = 0; i < count; i++)
		{
			series.add(new Candle(1_700_000_000L + i * BUCKET_SECONDS, QUOTED_SELL, QUOTED_BUY,
				20_000, 20_000));
		}
		return series;
	}

	private Candidate scoreWithMomentum(double momentum)
	{
		List<Candle> series = busy(250);
		featureEngine.setPredictedMomentums(
			momentum == 0 ? Collections.emptyMap() : Collections.singletonMap(ITEM, momentum));
		ItemFeatures features = featureEngine.compute(ITEM, series, BUCKET_SECONDS);
		assertEquals("the fixture has to actually carry the forecast", momentum,
			features.getPredictedMomentum(), 1e-9);

		return scorer.score(new ItemMetadata(ITEM, "Adamant bar", false, 30_000, 1_000),
			new LatestPrice(QUOTED_SELL, 0L, QUOTED_BUY, 0L), features, series,
			MarketContext.unknown(), horizon, 500_000_000L, 30_000, false, 200,
			Instant.ofEpochSecond(1_700_000_000L));
	}

	@Test
	public void aTradeIsPricedWithNoForecasterAtAll()
	{
		// The baseline. If this stops producing a trade the rest of the file proves nothing.
		assertNotNull("a liquid item with a 5% spread has to be tradeable", scoreWithMomentum(0));
	}

	@Test
	public void aNegativeForecastStillProducesATrade()
	{
		// The failure that cost real recommendations. quotedSell * (1 - 0.05) is 997, under the
		// 1,000 buy price, so the sell leg was skipped for every offset -- and since that was the
		// only sell price on offer, the item produced nothing at all. Any forecast the model can
		// emit must leave a tradeable price behind.
		Candidate pessimistic = scoreWithMomentum(-0.05);

		assertNotNull("a gloomy forecast is a reason to price lower, not to fall silent",
			pessimistic);
		assertTrue("and the trade it prices still has to make money after tax",
			pessimistic.getNetProfit() > 0);
	}

	@Test
	public void theGridIsStillSweptWhenTheForecasterAnswers()
	{
		// With the grid gone there was exactly one sell price per buy price, so the ask could only
		// ever land on the anchor. The grid reaches below it, and on a busy item the faster fill is
		// usually what wins -- which is the whole reason the grid exists.
		Candidate forecast = scoreWithMomentum(0.004);

		assertNotNull(forecast);
		int anchor = Scorer.tilt(QUOTED_SELL, 0.004);
		assertTrue("the chosen ask has to come from a sweep, not from the anchor alone: "
			+ forecast.getSellPrice(), forecast.getSellPrice() != anchor
			|| forecast.getBuyPrice() != QUOTED_BUY);
	}

	@Test
	public void theForecastCannotMoveTheAskFartherThanHalfAPercent()
	{
		// A one-bucket prediction does not get to set the exit price of a multi-hour flip.
		assertEquals(QUOTED_SELL, Scorer.tilt(QUOTED_SELL, 0));
		assertEquals(Math.round(QUOTED_SELL * 1.005), Scorer.tilt(QUOTED_SELL, 0.40));
		assertEquals(Math.round(QUOTED_SELL * 0.995), Scorer.tilt(QUOTED_SELL, -0.40));
		assertEquals("a small forecast passes through untouched",
			Math.round(QUOTED_SELL * 1.002), Scorer.tilt(QUOTED_SELL, 0.002));
	}

	@Test
	public void aBrokenForecastIsIgnoredRatherThanBelieved()
	{
		assertEquals(QUOTED_SELL, Scorer.tilt(QUOTED_SELL, Double.NaN));
		assertEquals(QUOTED_SELL, Scorer.tilt(QUOTED_SELL, Double.NEGATIVE_INFINITY));
	}
}
