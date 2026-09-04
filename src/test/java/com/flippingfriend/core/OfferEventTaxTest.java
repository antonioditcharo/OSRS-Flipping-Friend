package com.flippingfriend.core;

import static org.junit.Assert.assertEquals;

import com.google.gson.Gson;
import org.junit.Test;

/**
 * Pins the tax figure that makes realised profit a real number.
 *
 * <p>The dashboard computed profit as {@code revenue - costOfGoods} with no tax term anywhere, so
 * every completed flip was overstated by roughly 2% of its sale value. On a typical 2% margin that is
 * close to the entire profit — on the one number a person would use to judge whether any of this
 * works.
 *
 * <p>The tax is carried on the event rather than recomputed downstream. The alternative was a second
 * implementation of the Grand Exchange tax rules in JavaScript, and a second copy of a rule is how
 * the two quietly stop agreeing — which is the failure this repository already has a name for.
 *
 * <p>It crosses a serialisation boundary to reach the dashboard, so the round trip is what needs
 * pinning: a field that survives in memory and vanishes on the wire is indistinguishable from the
 * bug it was added to fix.
 */
public class OfferEventTaxTest
{
	private final Gson gson = new Gson();

	private static OfferEvent.Builder sale()
	{
		return OfferEvent.builder("c", 1_000L, "SOLD")
			.item(4151, "Abyssal whip")
			.buying(false)
			.price(1_000_000)
			.quantities(10, 10)
			.spent(10_000_000L);
	}

	@Test
	public void taxSurvivesTheRoundTripToTheDashboard()
	{
		OfferEvent event = sale().tax(200_000L).build();

		OfferEvent parsed = gson.fromJson(gson.toJson(event), OfferEvent.class);

		assertEquals("the dashboard reads this off the serialised event",
			200_000L, parsed.getTax());
		assertEquals(10_000_000L, parsed.getSpent());
	}

	/** An event built without one reports zero, not a negative or a null that would break arithmetic. */
	@Test
	public void anEventWithoutTaxReportsZero()
	{
		assertEquals(0L, sale().build().getTax());
		assertEquals("Gson skips the constructor, so the default must survive that too",
			0L, gson.fromJson("{\"eventType\":\"SOLD\"}", OfferEvent.class).getTax());
	}

	/** Negative tax is not a thing, and a negative here would inflate profit rather than reduce it. */
	@Test
	public void negativeTaxIsClampedToZero()
	{
		assertEquals(0L, sale().tax(-5_000L).build().getTax());
	}

	/**
	 * The arithmetic the dashboard performs, stated here so the intent is checkable: a 10m sale of
	 * goods that cost 9.8m is not 200k of profit if 200k of tax was paid on it.
	 */
	@Test
	public void netProfitIsRevenueMinusTaxMinusCost()
	{
		OfferEvent event = sale().tax(200_000L).build();

		long revenue = event.getSpent();
		long cost = 9_800_000L;
		long grossLooking = revenue - cost;
		long actual = revenue - event.getTax() - cost;

		assertEquals("what the dashboard used to report", 200_000L, grossLooking);
		assertEquals("what was actually made", 0L, actual);
	}
}
