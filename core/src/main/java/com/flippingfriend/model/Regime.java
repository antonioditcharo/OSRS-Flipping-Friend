package com.flippingfriend.model;

/**
 * What the price has been doing lately. This is the difference between a margin that is a genuine
 * spread you can capture twice, and one that is really just a price falling past you.
 */
public enum Regime
{
	/** Drifting up. Buying is safer than usual, and sell targets can be held out further. */
	RISING("rising"),
	/** Drifting down. A wide spread here is usually a knife, not an opportunity. */
	FALLING("falling"),
	/** Price is breaching the upper Bollinger Band. Likely to mean-revert down. */
	OVERBOUGHT("overbought"),
	/** Price is breaching the lower Bollinger Band. Likely to mean-revert up. */
	OVERSOLD("oversold"),
	/** Oscillating around a stable level. The ideal state for flipping. */
	STABLE("stable");

	private final String label;

	Regime(String label)
	{
		this.label = label;
	}

	public String getLabel()
	{
		return label;
	}
}
