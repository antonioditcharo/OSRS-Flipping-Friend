package com.flippingfriend.model;

/** Applies a fractional offset to a Grand Exchange price using the established one-coin minimum step. */
public final class PriceOffset
{
	private PriceOffset()
	{
	}

	public static int apply(int price, double offset)
	{
		int shift = (int) Math.round(price * offset);
		if (shift == 0 && offset != 0)
		{
			shift = offset > 0 ? 1 : -1;
		}
		return Math.max(1, price + shift);
	}
}
