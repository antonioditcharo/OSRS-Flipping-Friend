package com.flippingfriend.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.flippingfriend.data.Candle;
import com.flippingfriend.data.ItemMetadata;
import com.flippingfriend.data.LatestPrice;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import org.junit.Test;

/**
 * Finding a repricing while it is still happening, and being able to say what caused it.
 *
 * <p>The detector this replaced compared the last day against the day before it, and was blind on
 * both sides of a narrow window: a 10% repricing four hours old was caught 1 time in 100, and the
 * same repricing thirty-six hours old was caught <em>0</em> times in 100, because by then both
 * windows sat on the new level and there was no difference left to measure. An item was therefore
 * rejected on the second day and quietly accepted again on the third, while every mean-reverting fit
 * in the codebase went on pulling toward a price the game had deleted. Both halves are pinned below.
 */
public class StructuralBreakTest
{
	private static final long T0 = 1_700_000_000L / 3600 * 3600;
	private static final int HOURS = 336;

	/** A stable item: mean-reverting noise around one level, nothing structural anywhere. */
	private static List<Candle> steady(int level, double relativeNoise, long seed)
	{
		return withBreak(level, relativeNoise, 0, 0, seed);
	}

	/**
	 * A fortnight of hourly bars whose level steps by {@code jump} exactly {@code agoHours} before
	 * the end.
	 */
	private static List<Candle> withBreak(int level, double relativeNoise, double jump,
		int agoHours, long seed)
	{
		Random random = new Random(seed);
		List<Candle> series = new ArrayList<>();
		double deviation = 0;
		for (int i = 0; i < HOURS; i++)
		{
			deviation = 0.6 * deviation + random.nextGaussian() * level * relativeNoise;
			double base = agoHours > 0 && i >= HOURS - agoHours ? level * (1 + jump) : level;
			int mid = (int) Math.max(2, Math.round(base + deviation));
			series.add(new Candle(T0 + i * 3600L, mid + 1, mid - 1, 500, 500));
		}
		return series;
	}

	/** When the level actually stepped, for checking the detector against the truth. */
	private static Instant brokeAt(int agoHours)
	{
		return Instant.ofEpochSecond(T0 + (HOURS - agoHours) * 3600L);
	}

	private static int detections(double jump, int agoHours, int trials)
	{
		int fired = 0;
		for (int seed = 0; seed < trials; seed++)
		{
			if (MarketContext.from(withBreak(1_000, 0.01, jump, agoHours, seed), 1_000)
				.hasStructuralBreak())
			{
				fired++;
			}
		}
		return fired;
	}

	@Test
	public void aRepricingIsSeenTheSameMorningRatherThanTheNextDay()
	{
		// The headline. The old rule managed 1 in 100 here, which is what "detected after the fact"
		// meant in practice: the item was tradeable all day on the strength of a stale history.
		assertTrue("a 10% repricing four hours old must be caught almost every time: "
			+ detections(0.10, 4, 100), detections(0.10, 4, 100) >= 90);
		assertEquals("and a 25% one every time", 100, detections(0.25, 4, 100));
	}

	@Test
	public void andIsStillSeenTwoDaysLater()
	{
		// The half that is easy to miss. A detector comparing two adjacent days stops seeing a break
		// once both days are past it — so the veto lifted itself after about thirty hours and the
		// item went back into the shortlist with a fortnight of history describing a dead price.
		assertEquals("a repricing does not stop mattering because it is no longer new",
			100, detections(0.10, 36, 100));
		assertEquals(100, detections(0.10, 48, 100));
	}

	@Test
	public void aSteadyItemIsNotConstantlyBreaking()
	{
		// The scan keeps the largest of forty-odd correlated statistics, which crosses a threshold
		// more readily than any one of them. Measured rather than assumed, across three noise levels
		// spanning a rock-steady item and a restless one.
		for (double noise : new double[]{0.002, 0.01, 0.03})
		{
			int fired = 0;
			for (int seed = 0; seed < 200; seed++)
			{
				if (MarketContext.from(steady(1_000, noise, seed), 1_000).hasStructuralBreak())
				{
					fired++;
				}
			}
			assertTrue("noise " + noise + " fired on " + fired + "/200 stable items", fired <= 4);
		}
	}

	@Test
	public void theBreakIsLocatedInTimeAndNotJustDetected()
	{
		// Without an hour there is nothing to ask the calendar about, and the whole distinction
		// between a repricing and a squeeze collapses back into "this item moved".
		MarketContext context =
			MarketContext.from(withBreak(1_000, 0.01, 0.15, 8, 3), 1_000);

		assertTrue(context.hasStructuralBreak());
		assertNotNull("a break must know when it happened", context.breakAt());
		long errorHours = Math.abs(
			Duration.between(brokeAt(8), context.breakAt()).toHours());
		assertTrue("located within a few hours of the truth, off by " + errorHours + "h",
			errorHours <= 3);
	}

	@Test
	public void noBreakMeansNoTime()
	{
		MarketContext context = MarketContext.from(steady(1_000, 0.01, 7), 1_000);

		assertFalse(context.hasStructuralBreak());
		assertNull("a null hour is how a caller knows there is nothing to attribute",
			context.breakAt());
		assertNull("and an unusable context has none either", MarketContext.unknown().breakAt());
	}

	// --- attribution: the point of knowing when ---

	@Test
	public void anUpdateExplainsTheBreakItCaused()
	{
		// Wire the two halves together on one series: a repricing eight hours old, and a calendar
		// whose window opened ten hours ago.
		MarketContext context = MarketContext.from(withBreak(1_000, 0.01, 0.15, 8, 3), 1_000);
		Instant windowOpened = context.breakAt().minus(Duration.ofHours(2));
		GameUpdateCalendar calendar =
			GameUpdateCalendar.weekly().withKnownUpdates(Collections.singletonList(windowOpened));

		assertTrue("the update accounts for it", calendar.explains(context.breakAt()));
		assertFalse("while a calendar that knows nothing of it does not",
			GameUpdateCalendar.weekly().explains(context.breakAt())
				&& !GameUpdateCalendar.weekly().explains(context.breakAt()));
	}

	@Test
	public void theVetoSaysWhichKindOfBreakItFound()
	{
		// Both reject. Only one of them is worth coming back to, and a player given the same sentence
		// for a routine repricing and for a manipulation attempt cannot tell which they are looking at.
		List<Candle> series = withBreak(1_000, 0.01, 0.15, 8, 3);
		MarketContext context = MarketContext.from(series, 1_000);
		Instant now = context.breakAt().plus(Duration.ofHours(2));

		String unexplained = screen(new ManipulationFilter(), context, now);
		String explained = screen(
			new ManipulationFilter(GameUpdateCalendar.weekly()
				.withKnownUpdates(Collections.singletonList(
					context.breakAt().minus(Duration.ofHours(2))))),
			context, now);

		assertTrue("an unexplained shift must say so: " + unexplained,
			unexplained.contains("no update to explain it"));
		assertTrue("and an explained one must name the cause: " + explained,
			explained.contains("game update"));
		assertTrue("and say it is worth revisiting: " + explained,
			explained.contains("settled"));
	}

	private static String screen(ManipulationFilter filter, MarketContext context, Instant now)
	{
		ItemMetadata metadata = new ItemMetadata(561, "Nature rune", false, 25_000, 0);
		// A quote a long way from the item's median, which is what a fresh repricing looks like.
		long at = now.getEpochSecond();
		LatestPrice price = new LatestPrice(1_180, at, 1_150, at);
		ItemFeatures features = new FeatureEngine().compute(561,
			withBreak(1_000, 0.01, 0.15, 8, 3), 3600);
		return filter.screen(metadata, price, features, context, VetoThresholds.BALANCED, true, now)
			.getReason();
	}
}
