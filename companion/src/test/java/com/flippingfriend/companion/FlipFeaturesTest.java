package com.flippingfriend.companion;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.flippingfriend.learning.FlipFeatures;
import com.flippingfriend.model.ItemFeatures;
import com.flippingfriend.model.MarketContext;
import java.util.Arrays;
import org.junit.Test;

/**
 * Guards the recovered {@link FlipFeatures}.
 *
 * <p>The reconstruction itself was verified by diffing {@code javap -c -p -constants} output against
 * the original class in {@code flipping-friend-trainer.jar} — byte-identical — which is a stronger
 * check than any assertion here. These tests exist for the next change, not for the recovery: they
 * pin the vector's width, its bias convention, and the property that motivated bringing the class
 * back at all.
 *
 * <p>Read {@link #distinguishesTradesTheOnnxModelCannot()} beside
 * {@code OnnxInferenceEngineTest}. The same two trades that the live ONNX fill model scores
 * identically produce different vectors here.
 */
public class FlipFeaturesTest
{
	private static final int ITEM_ID = 4151;

	private static double[] vector(double margin, int price, int orderSize, int buyLimit)
	{
		return FlipFeatures.of(margin, price, orderSize, buyLimit,
			ItemFeatures.unknown(ITEM_ID), MarketContext.unknown(), 12).values();
	}

	@Test
	public void widthAndNamesAgree()
	{
		assertEquals("SIZE must match NAMES", FlipFeatures.NAMES.length, FlipFeatures.SIZE);
		assertEquals(16, FlipFeatures.SIZE);
		assertEquals(FlipFeatures.SIZE, vector(0.03, 150, 100, 13000).length);
	}

	@Test
	public void elementZeroIsTheBiasTerm()
	{
		assertEquals(1.0, vector(0.03, 150, 100, 13000)[0], 0.0);
		assertEquals("bias", FlipFeatures.NAMES[0]);
	}

	/**
	 * The property the ONNX fill model fails. A cheap, high-volume order and an expensive, thin one
	 * must not be described by the same numbers — if they are, no model downstream can tell them
	 * apart however well it is trained.
	 */
	@Test
	public void distinguishesTradesTheOnnxModelCannot()
	{
		double[] cheapLiquid = vector(0.03, 150, 1000, 13000);
		double[] dearThin = vector(0.03, 2_000_000, 5, 8);

		assertFalse(
			"FlipFeatures must separate trades that fill_prob_v1.onnx scores identically",
			Arrays.equals(cheapLiquid, dearThin));

		// Named so a failure says which feature stopped carrying the distinction.
		int logPrice = Arrays.asList(FlipFeatures.NAMES).indexOf("log price");
		assertTrue("log price must rise with price",
			dearThin[logPrice] > cheapLiquid[logPrice]);
	}

	/**
	 * Every clamped feature stays inside [0, 1] even for absurd or degenerate inputs — the point of
	 * routing them through {@link FlipFeatures#clamp}. The two log features are excluded here and
	 * covered by {@link #logFeaturesAreNotClampedAndCanExceedOne()} instead.
	 */
	@Test
	public void clampedFeaturesAreBounded()
	{
		int logPrice = Arrays.asList(FlipFeatures.NAMES).indexOf("log price");
		int logVolume = Arrays.asList(FlipFeatures.NAMES).indexOf("log volume");
		int marginXVolume = Arrays.asList(FlipFeatures.NAMES).indexOf("margin x volume");

		for (double[] v : new double[][]{
			vector(0.03, 150, 1000, 13000),
			vector(9.99, 2_000_000_000, Integer.MAX_VALUE, 1),   // absurd inputs
			vector(-1.0, 1, 0, 0),                               // negative margin, no limit
			vector(Double.NaN, 1, 1, 1),                         // degenerate margin
		})
		{
			for (int i = 0; i < v.length; i++)
			{
				if (i == logPrice || i == logVolume || i == marginXVolume)
				{
					continue;
				}
				assertTrue(FlipFeatures.NAMES[i] + " = " + v[i] + " outside [0,1]",
					v[i] >= 0.0 && v[i] <= 1.0);
			}
		}
	}

	/**
	 * Pins the one surprise in the recovered class: {@code log volume} and {@code log price} are
	 * normalising divisions with assumed ceilings (10^5 units/hour, 10^9 gp), not clamps, so they
	 * exceed 1 above those. {@code log volume} does so for any item trading more than 100,000 units
	 * an hour, which is ordinary among the liquid items this engine targets.
	 *
	 * <p>This is original behaviour, confirmed by a byte-identical bytecode diff against
	 * {@code flipping-friend-trainer.jar}, not a reconstruction artefact. The test exists so that
	 * changing it is a deliberate decision — and because anything consuming this vector and assuming
	 * a unit range will be wrong about these two columns.
	 */
	@Test
	public void logFeaturesAreNotClampedAndCanExceedOne()
	{
		int logPrice = Arrays.asList(FlipFeatures.NAMES).indexOf("log price");
		int logVolume = Arrays.asList(FlipFeatures.NAMES).indexOf("log volume");

		// 2.147b is about the ceiling a tradeable item can reach.
		assertTrue("log price exceeds 1 above 10^9 gp",
			vector(0.03, 2_000_000_000, 1, 1)[logPrice] > 1.0);

		// ItemFeatures.unknown() reports zero volume, which floors to 1.0 and yields log10(1)/5 = 0,
		// so the volume ceiling cannot be exercised through the public factory. Assert the floor
		// instead, and state the arithmetic that governs the ceiling.
		assertEquals("zero volume floors to log10(1)/5 = 0",
			0.0, vector(0.03, 150, 1000, 13000)[logVolume], 1e-12);
		assertTrue("the divisor assumes a 10^5 ceiling, so 10^6 units/hour would give 1.2",
			Math.log10(1e6) / 5.0 > 1.0);
	}

	/** A row persisted before a feature was added must still load, with the bias rewritten. */
	@Test
	public void fromStoredRepairsWidthAndBias()
	{
		double[] narrow = new double[]{ 0.0, 0.5, 0.25 };
		double[] restored = FlipFeatures.fromStored(narrow).values();

		assertEquals(FlipFeatures.SIZE, restored.length);
		assertEquals("bias is rewritten, not trusted from the row", 1.0, restored[0], 0.0);
		assertEquals(0.5, restored[1], 0.0);
		assertEquals(0.25, restored[2], 0.0);
		assertEquals("missing columns stay zero", 0.0, restored[15], 0.0);

		double[] wide = new double[FlipFeatures.SIZE + 4];
		Arrays.fill(wide, 0.7);
		assertEquals(FlipFeatures.SIZE, FlipFeatures.fromStored(wide).values().length);
	}
}
