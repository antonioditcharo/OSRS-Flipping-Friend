package com.flippingfriend.companion;

import com.flippingfriend.learning.GradientBoostedTrees;

/**
 * A trained completion model that must earn its influence before it has any.
 *
 * <p>This is the shape audit item 39 argues for and the reason it is safe to ship a model at all:
 * the analytical {@code FillModel} is a strong, well-understood prior that reads the actual order
 * book, and anything learned enters as a <em>correction</em> to it whose weight is its own measured
 * skill. A model that learns nothing gets weight zero and the system behaves exactly as it did
 * before — so the failure mode of training on noise is no change, not a confident wrong answer.
 *
 * <h2>Blended in log-odds, not in probability</h2>
 *
 * <pre>
 *   final = sigmoid( logit(analytical) + weight * (logit(model) - logit(analytical)) )
 * </pre>
 *
 * <p>Averaging probabilities directly would let a correction near the ends of the range do far more
 * damage than one in the middle: moving 0.98 to 0.90 is a small step in probability and a large one
 * in odds, which is where the money is. Log-odds space treats a given amount of evidence the same
 * way wherever it lands.
 *
 * <h2>The gate is a head-to-head, not a self-assessment</h2>
 *
 * <p>Training reports held-out log-loss, which says the model learned something about its own
 * sample. That is not the question. The question is whether it beats {@code FillModel} on rows
 * neither has seen, so every training row carries the analytical prediction made at the same moment,
 * and the weight comes from the Brier skill of one against the other. Below
 * {@value #MIN_SKILL} the model is ignored entirely: a correction that is barely better than the
 * thing it corrects is not worth the variance it adds.
 */
final class LearnedFillModel
{
	/** Rows needed before a model is trained at all. Below this the split is noise. */
	static final int MIN_ROWS = 500;

	/** Fraction of rows held back, taken from the end so the test set is the future of the train set. */
	private static final double TEST_FRACTION = 0.2;

	/** Brier skill against FillModel below which the model is ignored outright. */
	static final double MIN_SKILL = 0.02;

	/**
	 * Ceiling on influence. Even a model that scores well never fully replaces a prior that reads the
	 * book: it has seen this account's own trades, and the book is what everyone else is trading
	 * against.
	 */
	private static final double MAX_WEIGHT = 0.5;

	private GradientBoostedTrees model;
	private volatile double weight;
	private volatile String verdict = "never trained";
	private volatile int trainedRows;

	/**
	 * Trains on the older rows, scores on the newer ones, and sets the weight from the result.
	 *
	 * <p>Split by position rather than at random. The rows arrive in resolution order, so taking the
	 * tail as the test set means the model is always judged on data from after everything it learned
	 * from — a random split would let it be scored on the same market conditions it was fitted to.
	 *
	 * @return whether the model earned any influence at all
	 */
	synchronized boolean retrain(ShadowTrader.TrainingSet set)
	{
		int rows = set.size();
		if (rows < MIN_ROWS)
		{
			verdict = rows + " of " + MIN_ROWS + " rows";
			weight = 0;
			return false;
		}
		int testSize = Math.max(1, (int) (rows * TEST_FRACTION));
		int trainSize = rows - testSize;
		if (trainSize < MIN_ROWS / 2)
		{
			verdict = "not enough to train on";
			weight = 0;
			return false;
		}

		double[][] features = set.features();
		int[] labels = set.labels();
		double[] baseline = set.baseline();

		double[][] trainFeatures = new double[trainSize][];
		int[] trainLabels = new int[trainSize];
		System.arraycopy(features, 0, trainFeatures, 0, trainSize);
		System.arraycopy(labels, 0, trainLabels, 0, trainSize);

		GradientBoostedTrees candidate = new GradientBoostedTrees();
		candidate.train(trainFeatures, trainLabels, new GradientBoostedTrees.Settings());

		// Head to head on the held-out tail, against the analytical prediction made at the time.
		double modelError = 0;
		double baselineError = 0;
		int scored = 0;
		for (int i = trainSize; i < rows; i++)
		{
			if (baseline[i] < 0)
			{
				// No analytical prediction was recorded, so there is nothing to beat on this row.
				continue;
			}
			double actual = labels[i];
			double predicted = candidate.predict(features[i]);
			modelError += (predicted - actual) * (predicted - actual);
			baselineError += (baseline[i] - actual) * (baseline[i] - actual);
			scored++;
		}
		if (scored == 0 || baselineError <= 0)
		{
			verdict = "nothing comparable to score against";
			weight = 0;
			return false;
		}

		double skill = 1.0 - modelError / baselineError;
		trainedRows = trainSize;
		if (skill < MIN_SKILL)
		{
			// Kept out of the decision entirely rather than given a small weight. A correction barely
			// better than what it corrects is not worth the variance it adds.
			model = null;
			weight = 0;
			verdict = String.format("no better than FillModel (skill %+.1f%% over %d)",
				skill * 100, scored);
			return false;
		}
		model = candidate;
		weight = Math.min(MAX_WEIGHT, skill);
		verdict = String.format("beating FillModel by %+.1f%% over %d rows, weight %.2f",
			skill * 100, scored, weight);
		return true;
	}

	/**
	 * Corrects an analytical completion probability, or returns it untouched when nothing has been
	 * earned.
	 */
	double adjust(double analytical, double[] features)
	{
		GradientBoostedTrees current = model;
		double influence = weight;
		if (current == null || influence <= 0 || features == null || !current.isTrained())
		{
			return analytical;
		}
		double prior = logit(analytical);
		double learned = logit(current.predict(features));
		return sigmoid(prior + influence * (learned - prior));
	}

	boolean isInfluencing()
	{
		return model != null && weight > 0;
	}

	/** Rows the last training run had to work with, so a weight can be read against its evidence. */
	synchronized int trainingRows()
	{
		return trainedRows;
	}

	double weight()
	{
		return weight;
	}

	/** Feature importances of the model currently in use, or null when none is. */
	double[] importance()
	{
		GradientBoostedTrees current = model;
		return current == null ? null : current.featureImportance();
	}

	/** One line for {@code /v1/health}: what the model is doing, and why it is or is not trusted. */
	String summary()
	{
		return "Learned fill: " + verdict + (trainedRows > 0 ? " (" + trainedRows + " train rows)" : "");
	}

	private static double logit(double probability)
	{
		double clamped = Math.max(1.0E-6, Math.min(0.999999, probability));
		return Math.log(clamped / (1.0 - clamped));
	}

	private static double sigmoid(double score)
	{
		if (score > 30.0)
		{
			return 0.999999;
		}
		if (score < -30.0)
		{
			return 1.0E-6;
		}
		return 1.0 / (1.0 + Math.exp(-score));
	}
}
