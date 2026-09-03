package com.flippingfriend.learning;

import com.flippingfriend.model.ItemFeatures;
import com.flippingfriend.model.MarketContext;
import com.flippingfriend.model.Regime;

/**
 * The sixteen-element feature vector describing one candidate flip.
 *
 * <p>Recovered on 2 September 2026 from bytecode in
 * {@code flipping-friend-trainer.jar} (built 30 August), the last surviving copy after the source
 * was deleted. Reconstructed from {@code javap -c -p -constants} and verified by diffing the
 * recompiled bytecode against that original, so the arithmetic and every constant are exact. The
 * javadoc is the one thing that could not be recovered — the prose below describes what the code
 * does, not necessarily what the author originally wrote about it.
 *
 * <p>It is reinstated deliberately, even though the Java learner it was built for was replaced by
 * the ONNX models in {@code companion/src/main/resources/models/}. The reason is that the ONNX fill
 * model is fed only {@code { price, quantity, season, 0f }} and therefore cannot see liquidity at
 * all — which is why a 150gp order for 1,000 units and a 2,000,000gp order for 5 units currently
 * receive the same fill probability (see {@code OnnxInferenceEngineTest}). Two of the features here,
 * {@code order vs volume} and {@code log volume}, are exactly the missing information. This class is
 * the feature vector to retrain those models against; it is not a revival of the old learner.
 *
 * <p>Most values are squashed toward [0, 1] so that no single input dominates by scale, and that
 * squash is total: {@link #clamp} maps NaN and infinity to the low bound rather than propagating
 * them, so a missing or degenerate market statistic yields a conservative feature rather than
 * poisoning the whole vector.
 *
 * <p><strong>Two features are not clamped and can exceed 1.</strong> {@code log volume} divides by
 * 5 and {@code log price} by 9, which assumes ceilings of 10^5 units/hour and 10^9 gp. Volume above
 * 100,000/hour is ordinary for the liquid items this engine targets, so {@code log volume} exceeds 1
 * routinely, and {@code margin x volume} with it; {@code log price} exceeds 1 only above 10^9 gp,
 * which a handful of items reach. This is the original behaviour, preserved deliberately — the
 * bytecode diff that verified this reconstruction would not have held otherwise. It is recorded here
 * because it is easy to mistake for a bound, and it matters to anything downstream that assumes a
 * unit input range. {@code FlipFeaturesTest} pins it.
 *
 * <p>Element 0 is a constant 1.0 bias term, so a model consuming this vector gets its intercept for
 * free and {@link #fromStored} can repair a persisted row whose bias was lost.
 */
public class FlipFeatures
{
	/**
	 * Human-readable name of each element, in vector order. Kept beside the vector so a feature
	 * importance report names its columns instead of numbering them.
	 */
	public static final String[] NAMES = {
		"bias",
		"margin",
		"log volume",
		"volatility",
		"spread stability",
		"rising",
		"falling",
		"price percentile",
		"volatility ratio",
		"hour liquidity",
		"log price",
		"order vs limit",
		"order vs volume",
		"empty buckets",
		"margin x volume",
		"margin x stability",
	};

	public static final int SIZE = NAMES.length;

	private final double[] values;

	private FlipFeatures(double[] values)
	{
		this.values = values;
	}

	public double[] values()
	{
		return values;
	}

	/**
	 * Builds the vector for one candidate.
	 *
	 * @param margin     margin as a fraction of price, before tax
	 * @param price      unit price in gp
	 * @param orderSize  units this order would place
	 * @param buyLimit   the item's 4-hour buy limit, or 0 when unpublished
	 * @param features   per-item statistics
	 * @param context    market-wide context; when {@link MarketContext#isUsable()} is false each
	 *                   context-derived feature falls back to its neutral midpoint rather than zero,
	 *                   so "no context" is not silently the same signal as "context says zero"
	 * @param hour       hour of day, for the liquidity profile
	 */
	public static FlipFeatures of(double margin, int price, int orderSize, int buyLimit,
		ItemFeatures features, MarketContext context, int hour)
	{
		// Floored at 1 so it can be a divisor and a log argument without a separate guard.
		double volume = Math.max(1.0, features.getHourlyVolume());
		double stability = clamp01(features.getSpreadStability());

		// A 25% margin is treated as the top of the scale; anything beyond it is a data error far
		// more often than an opportunity.
		double marginFeature = clamp(margin, 0.0, 0.25) * 4.0;

		// 10^5 units/hour is the busiest the book realistically gets, so this lands in [0, 1].
		double logVolume = Math.log10(volume) / 5.0;
		double volatility = clamp(features.getVolatility(), 0.0, 0.2) * 5.0;

		double pricePercentile = context.isUsable() ? context.getPricePercentile() : 0.5;
		double volatilityRatio = context.isUsable()
			? clamp(context.volatilityRatio(features.getVolatility()), 0.0, 4.0) / 4.0
			: 0.25;
		double hourLiquidity = context.isUsable()
			? clamp(context.liquidityMultiplier(hour), 0.0, 2.0) / 2.0
			: 0.5;

		// 10^9 is above the most expensive tradeable item, so this stays inside [0, 1].
		double logPrice = Math.log10(Math.max(1, price)) / 9.0;

		// 508 of ~4,652 mapped items publish no buy limit. Zero means "unknown", and an unknown
		// limit must not read as "this order consumes all of it".
		double orderVsLimit = buyLimit <= 0 ? 0.0 : clamp01((double) orderSize / buyLimit);

		// The single most informative term for whether an order fills: size against the flow
		// actually trading. This is the feature the ONNX fill model has no equivalent of.
		double orderVsVolume = clamp01(orderSize / volume);

		double emptyBuckets = clamp01(features.getEmptyBucketFraction());

		double[] v = {
			1.0,
			marginFeature,
			logVolume,
			volatility,
			stability,
			features.getRegime() == Regime.RISING ? 1.0 : 0.0,
			features.getRegime() == Regime.FALLING ? 1.0 : 0.0,
			pricePercentile,
			volatilityRatio,
			hourLiquidity,
			logPrice,
			orderVsLimit,
			orderVsVolume,
			emptyBuckets,
			// Interactions, so a linear consumer can express "a wide margin is worth more on a
			// liquid item" and "a wide margin on an unstable spread is worth less" without a
			// hidden layer.
			marginFeature * logVolume,
			marginFeature * stability,
		};
		return new FlipFeatures(v);
	}

	/**
	 * Rebuilds a vector from a persisted row, tolerating a stored width that no longer matches
	 * {@link #SIZE}: extra columns are dropped, missing ones stay zero, and the bias is rewritten
	 * unconditionally. A row written before a feature was added therefore still loads.
	 */
	public static FlipFeatures fromStored(double[] stored)
	{
		double[] v = new double[SIZE];
		System.arraycopy(stored, 0, v, 0, Math.min(SIZE, stored.length));
		v[0] = 1.0;
		return new FlipFeatures(v);
	}

	private static double clamp01(double value)
	{
		return clamp(value, 0.0, 1.0);
	}

	/** NaN and infinity collapse to {@code low} — a degenerate statistic reads as the weakest signal. */
	private static double clamp(double value, double low, double high)
	{
		if (Double.isNaN(value) || Double.isInfinite(value))
		{
			return low;
		}
		return Math.max(low, Math.min(high, value));
	}
}
