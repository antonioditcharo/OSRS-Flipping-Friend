package com.flippingfriend.model;

import com.flippingfriend.data.Candle;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * An item whose price has moved is not an item nobody will trade with.
 * <p>
 * The fill curve is a histogram of absolute prices, and it was being built over every bar in the
 * cache — thirty hours of them. Over thirty hours prices move, and when they do, one of the two legs
 * falls outside almost all of the history on its side of the book: a price that has drifted down
 * leaves today's bid below nearly every instant-sell the window recorded, so the model reports that
 * almost no flow comes to it.
 * <p>
 * On a live board that put Dragonstone dragon bolts — thirteen thousand units an hour, both sides
 * busy — at <em>fifteen units an hour</em> on the buy leg, because it had drifted eight per cent.
 * Order size is the smaller of the two legs and the score multiplies the two fill probabilities, so
 * one drifted leg both sets the size and halves the value. The plan that came out of it was seven
 * trades of one unit each on a 128m account.
 * <p>
 * The window belongs to the question. What the plan wants to know is what will fill in the next few
 * hours, so the flow of the last few hours is the evidence that bears on it.
 */
public class DriftedPriceTest
{
	private static final int BUCKET_SECONDS = 300;
	/** Thirty hours of five-minute bars: what the cache actually holds for a live item. */
	private static final int BARS = 365;
	private static final long START = 1_700_000_000L;

	/**
	 * A busy item that drifts from {@code startLevel} to {@code endLevel} across the window, quoting
	 * a fixed spread around wherever it happens to be.
	 */
	private static List<Candle> drifting(int startLevel, int endLevel)
	{
		List<Candle> series = new ArrayList<>(BARS);
		for (int i = 0; i < BARS; i++)
		{
			int level = startLevel + (endLevel - startLevel) * i / (BARS - 1);
			series.add(new Candle(START + (long) i * BUCKET_SECONDS,
				level + 20, level - 20, 400, 400));
		}
		return series;
	}

	private static double buyRate(FillCurve curve, int price)
	{
		return new FillModel().estimateBuy(curve, price, 1, 4.0, 1.0).getUnitsPerHour();
	}

	@Test
	public void thirtyHoursOfHistoryScoresADriftedLegAsUntradeable()
	{
		// The live shape: the price has come down eight per cent over the window, and the bid we
		// would quote at today sits below nearly every instant-sell the window recorded.
		List<Candle> series = drifting(3_100, 2_850);
		int bidToday = 2_830;

		double overEverything = buyRate(FillCurve.from(series), bidToday);
		double overRecent = buyRate(FillCurve.overRecentHistory(series), bidToday);

		assertTrue("this is the fault being fixed: the whole window has to make the leg look nearly "
			+ "dead, or the fixture is not reproducing it (" + overEverything + " units/hour)",
			overEverything < 40);
		assertTrue("the same market, asked about the hours that bear on the question, is plainly "
			+ "tradeable: " + overRecent + " against " + overEverything,
			overRecent > overEverything * 4);
	}

	@Test
	public void theSellLegOfTheSameItemIsNotBrokenInExchange()
	{
		// Fixing one leg by breaking the other would be no fix at all -- order size is the smaller
		// of the two, so a windowing that merely moved the damage would change nothing downstream.
		List<Candle> series = drifting(3_100, 2_850);
		FillModel model = new FillModel();

		double sellRate = model.estimateSell(FillCurve.overRecentHistory(series), 2_870, 1, 4.0, 1.0)
			.getUnitsPerHour();
		double buyRate = buyRate(FillCurve.overRecentHistory(series), 2_830);

		assertTrue("both legs have to be live for the order to be sized on anything real: buy "
			+ buyRate + ", sell " + sellRate, Math.min(buyRate, sellRate) > 40);
	}

	@Test
	public void anItemThatHasNotMovedIsUnaffected()
	{
		// The window must not be a discount. On an item sitting still, the recent history is a
		// sample of the same distribution as the whole of it, so the answer barely changes -- what
		// little it does move is the evidence discount charging for the smaller sample, which is
		// correct and is the price of the fix.
		List<Candle> series = drifting(2_900, 2_900);

		double overEverything = buyRate(FillCurve.from(series), 2_880);
		double overRecent = buyRate(FillCurve.overRecentHistory(series), 2_880);

		assertEquals("a still market reads the same either way", overEverything, overRecent,
			overEverything * 0.2);
	}

	@Test
	public void aSeriesShorterThanTheWindowIsTheWholeSeries()
	{
		// Nothing to trim, so the two must agree exactly. Worth pinning: the windowing sits in front
		// of every fill estimate the plugin makes, and a fencepost here would quietly change the
		// answer for every short-history item on the board.
		List<Candle> series = new ArrayList<>();
		for (int i = 0; i < 30; i++)
		{
			series.add(new Candle(START + (long) i * BUCKET_SECONDS, 1_020, 1_000, 100, 100));
		}

		assertEquals(buyRate(FillCurve.from(series), 1_000),
			buyRate(FillCurve.overRecentHistory(series), 1_000), 1e-9);
	}

	@Test
	public void aSparseSeriesKeepsEnoughBarsToSayAnything()
	{
		// Hourly bars, where four hours is four of them. Falling back to a bar count rather than a
		// duration is what stops the window from turning a coarse series into no evidence at all.
		List<Candle> hourly = new ArrayList<>();
		for (int i = 0; i < 48; i++)
		{
			hourly.add(new Candle(START + (long) i * 3_600, 1_020, 1_000, 100, 100));
		}

		FillCurve curve = FillCurve.overRecentHistory(hourly);

		assertTrue("four hourly bars is not a measurement; the floor has to hold",
			curve.buyScoredBuckets() >= 24);
	}
}
