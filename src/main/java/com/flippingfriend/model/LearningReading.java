package com.flippingfriend.model;

/**
 * One metric's latest reading, as the companion's learning summary returns it.
 *
 * <p>Field names match the companion's {@code LearningSnapshot.Metric} exactly, because Gson maps
 * them by name across the loopback and a rename on either side would quietly produce zeroes rather
 * than an error.
 *
 * <p>The sample travels with the value and is not optional. A rate is not a fact, it is a fact plus
 * how much was behind it: capture fell from 0.85 to 0.074 on six observations and resized every order
 * in the plan, and the value alone could not have told anybody that.
 */
public final class LearningReading
{
	private String name;
	private double value;
	private long sample;

	public LearningReading()
	{
	}

	public LearningReading(String name, double value, long sample)
	{
		this.name = name;
		this.value = value;
		this.sample = sample;
	}

	public String getName() { return name; }
	public double getValue() { return value; }
	public long getSample() { return sample; }
}
