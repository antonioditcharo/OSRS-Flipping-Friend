package com.flippingfriend.model;

/**
 * How likely an offer at a given price is to actually complete, and how long it should take.
 * <p>
 * This is the number most flipping tools quietly skip. A 15% margin on an item that trades four
 * times a day is worth far less than a 1% margin on one that trades four times a minute, and
 * without a fill estimate the two look identical in a margin table.
 */
public class FillEstimate
{
	private static final FillEstimate NEVER =
		new FillEstimate(0, Double.POSITIVE_INFINITY, 0, Double.POSITIVE_INFINITY);

	private final double probability;
	private final double expectedHours;
	private final double unitsPerHour;
	private final double waitHours;

	public FillEstimate(double probability, double expectedHours, double unitsPerHour, double waitHours)
	{
		this.probability = probability;
		this.expectedHours = expectedHours;
		this.unitsPerHour = unitsPerHour;
		this.waitHours = waitHours;
	}

	public static FillEstimate never()
	{
		return NEVER;
	}

	/** Chance the whole order completes inside the horizon it was estimated against. */
	public double getProbability()
	{
		return probability;
	}

	public double getExpectedHours()
	{
		return expectedHours;
	}

	public double getExpectedMinutes()
	{
		return Double.isInfinite(expectedHours) ? Double.POSITIVE_INFINITY : expectedHours * 60;
	}

	/**
	 * Time expected to pass before the first unit trades, separate from the rate afterwards.
	 * <p>
	 * Exposed because order size depends on it. Sizing against the whole horizon when part of it is
	 * spent waiting produces an order that cannot finish in the time it has, which is not a harmless
	 * approximation: it guarantees a partial fill and an unwind on the remainder.
	 */
	public double getWaitHours()
	{
		return waitHours;
	}

	/** How much of a horizon is actually left for trading once the wait is over. */
	public double tradingHoursWithin(double horizonHours)
	{
		return Math.max(0, horizonHours - waitHours);
	}

	/** Units of the item we expect to transact per hour at this price. */
	public double getUnitsPerHour()
	{
		return unitsPerHour;
	}

	public boolean isPlausible()
	{
		return probability > 0 && !Double.isInfinite(expectedHours);
	}
}
