package com.flippingfriend.model;

import com.flippingfriend.data.Candle;
import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The fill model is what separates a real margin from a decorative one, so these cases check the
 * two properties the scorer relies on: an offer priced where the market never goes should be
 * reported as hopeless, and a bigger order on the same item should take proportionally longer.
 */
public class FillModelTest
{
	private static final int BUCKET_SECONDS = 300;

	private FillModel model;
	private List<Candle> busyItem;

	@Before
	public void setUp()
	{
		model = new FillModel();

		// A liquid item oscillating between 990 and 1010, trading 100 a side every five minutes.
		busyItem = new ArrayList<>();
		for (int i = 0; i < 120; i++)
		{
			int offset = (i % 2 == 0) ? 0 : 4;
			busyItem.add(candle(i, 1010 - offset, 990 + offset, 100, 100));
		}
	}

	@Test
	public void anOfferTheMarketNeverReachesDoesNotFill()
	{
		// Nobody is selling at 500 when the market floor is 990.
		FillEstimate estimate = model.estimateBuy(busyItem, 500, 100, 1.0);
		assertFalse(estimate.isPlausible());
		assertEquals(0.0, estimate.getProbability(), 1e-9);
	}

	@Test
	public void anOfferAtTheMarketFillsReadily()
	{
		FillEstimate estimate = model.estimateBuy(busyItem, 1000, 100, 1.0);

		assertTrue(estimate.isPlausible());
		assertTrue("a small order on a busy item should be very likely to fill",
			estimate.getProbability() > 0.8);
		assertTrue(estimate.getUnitsPerHour() > 0);
	}

	@Test
	public void throughputIsTheVolumeThatReachedOurPriceOverTheWholeWindow()
	{
		// Pins the arithmetic, because getting it wrong is invisible: every relative property still
		// holds when throughput is uniformly wrong by a constant factor, so only an absolute check
		// catches it.
		//
		// Half the buckets trade at a low of 990 and half at 994, 100 units a side, against highs of
		// 1010 and 1006 -- so the curve's dispersion is (1010 - 994) / 2 = 8.
		//
		// A bucket's recorded price is the volume-weighted mean of what traded there, not a price
		// every trade in it achieved, so a bucket contributes the share of itself that plausibly
		// crossed at our price rather than all or nothing. Buying at 990: the buckets centred on 990
		// give half of themselves, because half of what they averaged traded above 990; the ones
		// centred on 994 give a quarter. That is 60*50 + 60*25 = 4,500 units, where the old
		// all-or-nothing rule claimed all 6,000 of the first group and none of the second.
		FillEstimate estimate = model.estimateBuy(busyItem, 990, 100, 1.0);

		// 120 buckets span 119 intervals: the window is first timestamp to last, not bucket count.
		double hoursInWindow = 119 * BUCKET_SECONDS / 3600.0;
		double expectedUnitsPerHour = 4500 / hoursInWindow * 0.35;

		assertEquals("throughput must not discount reachability twice", expectedUnitsPerHour,
			estimate.getUnitsPerHour(), 1e-6);
	}

	@Test
	public void aPriceReachedRarelyIsNotPenalisedForBeingRare()
	{
		// Two items shift the same total volume through our price over the same window; one does it
		// in a few busy bursts, the other in a steady trickle. Two separate effects are in play and
		// this pins both apart.
		//
		// The bug that once lived here multiplied by the reachable-bucket fraction on top of a
		// window-wide average, reporting the bursty item as four times slower purely for being
		// bursty. That must stay fixed.
		//
		// The deliberate effect that remains is uncertainty: thirty observations support a weaker
		// claim than a hundred and twenty, so the bursty estimate is discounted more. The difference
		// between them must be exactly that and nothing else — which is what asserting the precise
		// ratio checks.
		List<Candle> bursty = new ArrayList<>();
		List<Candle> steady = new ArrayList<>();
		for (int i = 0; i < 120; i++)
		{
			// Bursty: reaches 1000 every fourth bucket, 400 units when it does.
			boolean reaches = i % 4 == 0;
			bursty.add(candle(i, 1010, reaches ? 1000 : 1006, 100, reaches ? 400 : 0));
			// Steady: reaches 1000 every bucket, 100 units each time.
			steady.add(candle(i, 1010, 1000, 100, 100));
		}

		double burstyRate = model.estimateBuy(bursty, 1000, 100, 1.0).getUnitsPerHour();
		double steadyRate = model.estimateBuy(steady, 1000, 100, 1.0).getUnitsPerHour();

		assertEquals("equal volume through the same price is equal throughput", steadyRate,
			burstyRate, 1e-6);

		// With the uncertainty discount switched on, thirty observations support a weaker claim than
		// a hundred and twenty and the bursty estimate is trimmed — modestly, and by exactly that
		// factor, not by a fourfold penalty for being bursty.
		FillModel cautious = new FillModel(1.0);
		double burstyCautious = cautious.estimateBuy(bursty, 1000, 100, 1.0).getUnitsPerHour();
		double steadyCautious = cautious.estimateBuy(steady, 1000, 100, 1.0).getUnitsPerHour();
		double expectedRatio = (1 + 1 / Math.sqrt(120)) / (1 + 1 / Math.sqrt(30));

		assertEquals(steadyCautious * expectedRatio, burstyCautious, 1e-6);
		assertTrue(burstyCautious > steadyCautious * 0.8);
	}

	@Test
	public void payingMoreFillsFasterThanPayingLess()
	{
		FillEstimate generous = model.estimateBuy(busyItem, 1010, 500, 1.0);
		FillEstimate stingy = model.estimateBuy(busyItem, 991, 500, 1.0);

		assertTrue("a higher buy price should reach at least as much flow",
			generous.getUnitsPerHour() >= stingy.getUnitsPerHour());
		assertTrue(generous.getExpectedHours() <= stingy.getExpectedHours());
	}

	@Test
	public void askingLessSellsFasterThanAskingMore()
	{
		FillEstimate cheap = model.estimateSell(busyItem, 995, 500, 1.0);
		FillEstimate greedy = model.estimateSell(busyItem, 1010, 500, 1.0);

		assertTrue(cheap.getUnitsPerHour() >= greedy.getUnitsPerHour());
	}

	@Test
	public void aTinyOrderStillWaitsForSomebodyToTradeWithIt()
	{
		// Dividing quantity by a rate says a small enough order fills instantly. Queues do not work
		// that way: one item still waits for a counterparty to arrive at the price. Without this the
		// model reports near-zero durations for small orders, and since the optimizer ranks on profit
		// divided by duration, near-zero durations are exactly what it chases.
		FillEstimate one = model.estimateBuy(busyItem, 990, 1, 1.0);

		// 120 buckets spanning 119 intervals, reached in 60 of them, so the wait is two buckets.
		double hoursInWindow = 119 * BUCKET_SECONDS / 3600.0;
		double bucketHours = hoursInWindow / 120;
		double expectedWait = bucketHours / 0.5;

		assertTrue("the wait must dominate the time for a single unit",
			expectedWait > 1 / one.getUnitsPerHour() * 10);
		assertEquals("a single unit waits for a counterparty, then trades",
			expectedWait + 1 / one.getUnitsPerHour(), one.getExpectedHours(), 1e-6);
	}

	@Test
	public void theWaitIsAddedToThroughputRatherThanReplacingIt()
	{
		// The two costs are separate and must compose: time before the first fill, then time to work
		// through the quantity. A model that took the larger of the two would understate both ends.
		FillEstimate small = model.estimateBuy(busyItem, 990, 1, 1.0);
		FillEstimate large = model.estimateBuy(busyItem, 990, 1_000, 1.0);

		double extraUnits = 1_000 - 1;
		assertEquals(small.getExpectedHours() + extraUnits / large.getUnitsPerHour(),
			large.getExpectedHours(), 1e-6);
	}

	@Test
	public void aPriceTheMarketRarelyVisitsWaitsLonger()
	{
		// Same throughput at the price, different frequency of being reached. The one that trades in
		// occasional bursts makes you wait longer for the first fill, even though the units per hour
		// come out identical.
		List<Candle> bursty = new ArrayList<>();
		List<Candle> steady = new ArrayList<>();
		for (int i = 0; i < 120; i++)
		{
			boolean reaches = i % 4 == 0;
			bursty.add(candle(i, 1010, reaches ? 1000 : 1006, 100, reaches ? 400 : 0));
			steady.add(candle(i, 1010, 1000, 100, 100));
		}

		FillEstimate burstyFill = model.estimateBuy(bursty, 1000, 1, 1.0);
		FillEstimate steadyFill = model.estimateBuy(steady, 1000, 1, 1.0);

		assertEquals("identical throughput", steadyFill.getUnitsPerHour(),
			burstyFill.getUnitsPerHour(), 1e-6);
		assertTrue("but the bursty one keeps you waiting for a counterparty",
			burstyFill.getExpectedHours() > steadyFill.getExpectedHours() * 3);
	}

	@Test
	public void theWaitCanBeSwitchedOffForComparison()
	{
		FillModel noWait = new FillModel(0.0, 0.0);
		FillEstimate estimate = noWait.estimateBuy(busyItem, 990, 1, 1.0);

		assertEquals("with the wait disabled only the floor remains", 1.0 / 60.0,
			estimate.getExpectedHours(), 1e-9);
	}

	@Test
	public void anOrderSizedForTheTradingWindowFitsInsideTheHorizon()
	{
		// What sizing against the post-wait window buys you: an order that is expected to finish
		// inside the horizon rather than be cut short.
		//
		// The production sizer deliberately does not do this. Held-out replay showed the conservative
		// version costs 13.8%, because a partial fill still sells and ordering small every time gives
		// up more than the occasional unwind costs. The arithmetic below is kept because it is the
		// reason the choice is a real trade-off rather than an oversight.
		double horizon = 1.5;
		FillEstimate probe = model.estimateBuy(busyItem, 990, 1, horizon);

		int sized = (int) (probe.getUnitsPerHour() * probe.tradingHoursWithin(horizon));
		FillEstimate actual = model.estimateBuy(busyItem, 990, sized, horizon);

		assertTrue("an order sized for the window must be expected to finish within it: "
			+ actual.getExpectedHours() + "h against a " + horizon + "h horizon",
			actual.getExpectedHours() <= horizon + 1e-9);
	}

	@Test
	public void sizingAgainstTheWholeHorizonAcceptsAnOverrun()
	{
		// The other side of that trade-off, and what production actually does: order into the whole
		// horizon, accept that some orders are cut short, and keep the upside when the market is busy.
		double horizon = 1.5;
		FillEstimate probe = model.estimateBuy(busyItem, 990, 1, horizon);

		int naive = (int) (probe.getUnitsPerHour() * horizon);
		FillEstimate overrun = model.estimateBuy(busyItem, 990, naive, horizon);

		assertTrue("sizing into the whole horizon means accepting that it may be cut short",
			overrun.getExpectedHours() > horizon);
	}

	@Test
	public void largerOrdersTakeLonger()
	{
		FillEstimate small = model.estimateBuy(busyItem, 1000, 100, 1.0);
		FillEstimate large = model.estimateBuy(busyItem, 1000, 10_000, 1.0);

		assertTrue(large.getExpectedHours() > small.getExpectedHours());
		assertTrue("and are correspondingly less certain inside the same window",
			large.getProbability() < small.getProbability());
	}

	@Test
	public void aDeadItemIsNotPlausible()
	{
		List<Candle> dead = new ArrayList<>();
		for (int i = 0; i < 60; i++)
		{
			dead.add(candle(i, null, null, 0, 0));
		}

		assertFalse(model.estimateBuy(dead, 1000, 10, 1.0).isPlausible());
		assertFalse(model.estimateSell(dead, 1000, 10, 1.0).isPlausible());
	}

	@Test
	public void rejectsNonsenseInputs()
	{
		assertFalse(model.estimateBuy((java.util.List<Candle>) null, 1000, 10, 1.0).isPlausible());
		assertFalse(model.estimateBuy((FillCurve) null, 1000, 10, 1.0).isPlausible());
		assertFalse(model.estimateBuy(busyItem, 0, 10, 1.0).isPlausible());
		assertFalse(model.estimateBuy(busyItem, 1000, 0, 1.0).isPlausible());
	}

	@Test
	public void lumpySupplyIsLessCertainThanSteadySupplyOfTheSameSize()
	{
		// The property the smooth-flow formula could not express. Both items put the same total volume
		// through our price over the same window, so their throughput is identical — but one does it
		// in a handful of bursts and the other in a steady trickle, and an order relying on three
		// lucky buckets is not as safe as one relying on thirty.
		List<Candle> bursty = new ArrayList<>();
		List<Candle> steady = new ArrayList<>();
		for (int i = 0; i < 120; i++)
		{
			boolean reaches = i % 8 == 0;
			bursty.add(candle(i, 1010, reaches ? 1000 : 1006, 100, reaches ? 800 : 0));
			steady.add(candle(i, 1010, 1000, 100, 100));
		}

		FillEstimate burstyFill = model.estimateBuy(bursty, 1000, 400, 1.0);
		FillEstimate steadyFill = model.estimateBuy(steady, 1000, 400, 1.0);

		assertEquals("identical average throughput", steadyFill.getUnitsPerHour(),
			burstyFill.getUnitsPerHour(), 1e-6);
		assertTrue("but the bursty one is a less certain fill",
			burstyFill.getProbability() < steadyFill.getProbability());
	}

	@Test
	public void abundantSteadySupplyIsStillTreatedAsNearCertain()
	{
		// The correction must not turn into blanket pessimism: when the flow is many times the order
		// and arrives constantly, the fill really is close to certain and the model should say so.
		FillEstimate estimate = model.estimateBuy(busyItem, 1000, 5, 1.0);

		assertTrue("plentiful steady supply should read as near certain: "
			+ estimate.getProbability(), estimate.getProbability() > 0.9);
	}

	@Test
	public void supplyExactlyEqualToTheOrderIsACoinFlip()
	{
		// When expected supply exactly matches the order, completion is genuinely uncertain. The old
		// smooth formula answered 63% here, which reads as comfortable rather than marginal.
		FillEstimate probe = model.estimateBuy(busyItem, 990, 1, 1.0);
		int exactlyEnough = (int) Math.round(probe.getUnitsPerHour() * probe.tradingHoursWithin(1.0));

		FillEstimate estimate = model.estimateBuy(busyItem, 990, exactlyEnough, 1.0);

		assertEquals("neither likely nor unlikely", 0.5, estimate.getProbability(), 0.08);
	}

	@Test
	public void theOlderSmoothFormulaCanStillBeRunForComparison()
	{
		FillModel smooth = new FillModel(0.0, 1.0, false);
		FillEstimate probe = smooth.estimateBuy(busyItem, 990, 1, 1.0);
		int exactlyEnough = (int) Math.round(probe.getUnitsPerHour() * probe.tradingHoursWithin(1.0));

		FillEstimate estimate = smooth.estimateBuy(busyItem, 990, exactlyEnough, 1.0);

		assertEquals("the smooth model calls an even match 63%", 1 - Math.exp(-1.0),
			estimate.getProbability(), 0.05);
	}

	@Test
	public void probabilityStaysWithinBounds()
	{
		for (int price = 980; price <= 1020; price += 5)
		{
			double probability = model.estimateBuy(busyItem, price, 50, 2.0).getProbability();
			assertTrue("probability out of range at " + price, probability >= 0 && probability <= 1);
		}
	}

	private static Candle candle(int index, Integer high, Integer low, int highVolume, int lowVolume)
	{
		return new Candle(1_700_000_000L + (long) index * BUCKET_SECONDS, high, low, highVolume, lowVolume);
	}
}
