package com.flippingfriend.companion;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.flippingfriend.learning.FlipFeatures;
import com.flippingfriend.learning.GradientBoostedTrees;
import java.util.Random;
import org.junit.Test;

/**
 * Covers the recovered GBDT: that it learns, that it stops when it should, and that it says which
 * features carried the signal.
 *
 * <p>The reconstruction itself was verified by bytecode diff against the archived class, which is a
 * stronger check than any assertion here. These tests exist because the audit's central complaint is
 * models that were never scored against anything — so before this one is allowed near a decision it
 * has to demonstrate, in a test, that it beats predicting the base rate.
 */
public class GradientBoostedTreesTest
{
	private static final int ROWS = 1_200;

	/**
	 * A dataset where completion genuinely depends on two of the sixteen features, plus noise. Built
	 * on the real {@link FlipFeatures} width so the shapes are the ones production uses.
	 */
	private static double[][] featuresWithSignal(Random random)
	{
		double[][] features = new double[ROWS][FlipFeatures.SIZE];
		for (int i = 0; i < ROWS; i++)
		{
			features[i][0] = 1.0;
			for (int f = 1; f < FlipFeatures.SIZE; f++)
			{
				features[i][f] = random.nextDouble();
			}
		}
		return features;
	}

	/** Completes when the margin is wide and the order is small against the volume traded. */
	private static int[] labelsFor(double[][] features, Random random)
	{
		int margin = 1;
		int orderVsVolume = 12;
		int[] labels = new int[features.length];
		for (int i = 0; i < features.length; i++)
		{
			double strength = features[i][margin] - features[i][orderVsVolume];
			double probability = 1.0 / (1.0 + Math.exp(-4.0 * strength));
			labels[i] = random.nextDouble() < probability ? 1 : 0;
		}
		return labels;
	}

	@Test
	public void itBeatsPredictingTheBaseRate()
	{
		Random random = new Random(7);
		double[][] features = featuresWithSignal(random);
		int[] labels = labelsFor(features, random);

		GradientBoostedTrees model = new GradientBoostedTrees();
		model.train(features, labels, new GradientBoostedTrees.Settings());

		assertTrue("it must actually fit something", model.isTrained());

		// The baseline the audit insists every model must beat: predict the base rate, always.
		double positives = 0;
		for (int label : labels)
		{
			positives += label;
		}
		double rate = positives / labels.length;
		double baseline = 0;
		for (int label : labels)
		{
			baseline += label == 1 ? -Math.log(rate) : -Math.log(1 - rate);
		}
		baseline /= labels.length;

		double trained = model.evaluate(features, labels);
		assertTrue("log-loss " + trained + " must beat the base rate's " + baseline,
			trained < baseline);
	}

	/**
	 * The property that separates this from the Python trainer: it holds out a fifth, watches the
	 * held-out loss, and truncates the ensemble back to the tree count that scored best rather than
	 * shipping the trees fitted after it started overfitting.
	 */
	@Test
	public void itStopsEarlyAndKeepsOnlyTheTreesThatHelped()
	{
		Random random = new Random(11);
		double[][] features = featuresWithSignal(random);
		int[] labels = labelsFor(features, random);

		GradientBoostedTrees.Settings settings = new GradientBoostedTrees.Settings();
		settings.maxTrees = 400;
		GradientBoostedTrees model = new GradientBoostedTrees();
		model.train(features, labels, settings);

		assertTrue("it must not have used its whole budget on this much data, got "
			+ model.treeCount(), model.treeCount() < settings.maxTrees);
		assertTrue(model.treeCount() > 0);
	}

	/** Pure noise has nothing to learn, and a model that claims otherwise is the dangerous case. */
	@Test
	public void itLearnsAlmostNothingFromNoise()
	{
		Random random = new Random(13);
		double[][] features = featuresWithSignal(random);
		int[] labels = new int[ROWS];
		for (int i = 0; i < ROWS; i++)
		{
			labels[i] = random.nextInt(2);
		}

		GradientBoostedTrees model = new GradientBoostedTrees();
		model.train(features, labels, new GradientBoostedTrees.Settings());

		// log(2) is the loss of a coin flip. Anything far below it on unlearnable data would mean the
		// held-out split is not held out.
		double loss = model.evaluate(features, labels);
		assertTrue("noise must not produce confident predictions, got " + loss,
			loss > Math.log(2) - 0.15);
	}

	@Test
	public void importanceNamesTheFeaturesThatCarriedTheSignal()
	{
		Random random = new Random(17);
		double[][] features = featuresWithSignal(random);
		int[] labels = labelsFor(features, random);

		GradientBoostedTrees model = new GradientBoostedTrees();
		model.train(features, labels, new GradientBoostedTrees.Settings());
		double[] importance = model.featureImportance();

		assertEquals(FlipFeatures.SIZE, importance.length);
		double total = 0;
		for (double value : importance)
		{
			total += value;
		}
		assertEquals("importance is a share, so it sums to one", 1.0, total, 1e-9);

		int margin = 1;
		int orderVsVolume = 12;
		double signal = importance[margin] + importance[orderVsVolume];
		assertTrue("the two features the label depends on must dominate, got " + signal,
			signal > 0.5);
		assertEquals("and " + FlipFeatures.NAMES[margin] + " is one of them", "margin",
			FlipFeatures.NAMES[margin]);
		assertEquals("order vs volume", FlipFeatures.NAMES[orderVsVolume]);
	}

	@Test
	public void anUntrainedModelIsHonestAboutIt()
	{
		GradientBoostedTrees model = new GradientBoostedTrees();

		assertFalse(model.isTrained());
		assertEquals(0, model.treeCount());
	}

	@Test
	public void mismatchedInputIsRefusedRatherThanFitted()
	{
		GradientBoostedTrees model = new GradientBoostedTrees();
		try
		{
			model.train(new double[][]{ { 1.0, 2.0 } }, new int[]{ 1, 0 },
				new GradientBoostedTrees.Settings());
			org.junit.Assert.fail("features and labels of different lengths must be refused");
		}
		catch (IllegalArgumentException expected)
		{
			assertTrue(expected.getMessage().contains("must match"));
		}
	}

	/** Training must be reproducible, or a gate result cannot be attributed to a change. */
	@Test
	public void trainingIsDeterministicForAGivenSeed()
	{
		Random random = new Random(23);
		double[][] features = featuresWithSignal(random);
		int[] labels = labelsFor(features, random);

		GradientBoostedTrees first = new GradientBoostedTrees();
		GradientBoostedTrees second = new GradientBoostedTrees();
		double firstLoss = first.train(features, labels, new GradientBoostedTrees.Settings());
		double secondLoss = second.train(features, labels, new GradientBoostedTrees.Settings());

		assertEquals(firstLoss, secondLoss, 1e-12);
		assertEquals(first.treeCount(), second.treeCount());
		assertEquals(first.predict(features[0]), second.predict(features[0]), 1e-12);
	}
}
