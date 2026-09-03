package com.flippingfriend.learning;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

/**
 * Gradient-boosted decision trees over {@link FlipFeatures}, predicting whether a flip completes.
 *
 * <p>Recovered on 3 September 2026 from {@code flipping-friend-trainer.jar} and verified by bytecode
 * diff against the original. The javadoc is reconstructed; the code is exact.
 *
 * <p>This is the model the audit argues for and then observes was never built: at this data scale —
 * tens of thousands of resolved observations over sixteen engineered features — boosted trees will
 * very likely beat a sequence model, train in seconds rather than hours, and yield feature
 * importances that can be reasoned about. It turned out to have been built already, and deleted.
 *
 * <h2>It grades its own homework</h2>
 *
 * <p>A fifth of the rows are held out before the first tree is fitted, log-loss is measured on that
 * fifth after every round, and training stops when it has not improved for
 * {@code earlyStoppingRounds}. The ensemble is then <em>truncated back</em> to the tree count that
 * scored best, so the trees fitted after the model started overfitting are discarded rather than
 * shipped. That is the discipline the Python trainer lacks entirely — it has no test fold, no
 * baseline, and selects on the same data it fits.
 *
 * <p>None of which makes it fit to trade on by itself. Held-out log-loss says the model learned
 * something about its own sample; it does not say the model beats {@code FillModel}, which reads the
 * actual order book. That comparison is a separate gate, and this class deliberately does not
 * pretend to make it.
 */
public final class GradientBoostedTrees
{
	private final List<Tree> trees = new ArrayList<>();
	private double baseScore;
	private int featureCount;

	/**
	 * Fits the ensemble and returns the best held-out log-loss it reached.
	 *
	 * @return log-loss on the validation fifth, at the tree count the model was truncated to
	 */
	public double train(double[][] features, int[] labels, Settings settings)
	{
		if (features.length == 0 || features.length != labels.length)
		{
			throw new IllegalArgumentException("features and labels must match and be non-empty");
		}
		trees.clear();
		featureCount = features[0].length;
		Random random = new Random(settings.seed);
		int[] order = shuffledIndices(features.length, random);
		// Held out before the first tree is fitted, so nothing in the ensemble has seen it.
		int validationSize = Math.max(1, features.length / 5);
		int[] validation = Arrays.copyOfRange(order, 0, validationSize);
		int[] training = Arrays.copyOfRange(order, validationSize, order.length);

		double positives = 0.0;
		for (int index : training)
		{
			positives += labels[index];
		}
		// Start every prediction at the base rate in log-odds, so the trees only ever have to learn
		// the departure from it. A model with no trees is then the base rate rather than nonsense.
		double rate = Math.max(1.0E-6, Math.min(0.999999, positives / training.length));
		baseScore = Math.log(rate / (1.0 - rate));

		double[] scores = new double[features.length];
		Arrays.fill(scores, baseScore);
		double bestLoss = logLoss(features, labels, validation, scores);
		int bestTreeCount = 0;
		int roundsWithoutImprovement = 0;

		for (int round = 0; round < settings.maxTrees; round++)
		{
			double[] residuals = new double[features.length];
			for (int i = 0; i < features.length; i++)
			{
				residuals[i] = labels[i] - sigmoid(scores[i]);
			}
			int[] sample = subsample(training, settings.subsample, random);
			Tree tree = new Tree();
			tree.fit(features, residuals, sample, settings, 0);
			trees.add(tree);
			for (int i = 0; i < features.length; i++)
			{
				scores[i] += settings.learningRate * tree.predict(features[i]);
			}
			double loss = logLoss(features, labels, validation, scores);
			if (loss < bestLoss - 1.0E-6)
			{
				bestLoss = loss;
				bestTreeCount = trees.size();
				roundsWithoutImprovement = 0;
				continue;
			}
			if (++roundsWithoutImprovement >= settings.earlyStoppingRounds)
			{
				break;
			}
		}
		// Throw away the trees fitted after the held-out score stopped improving. Keeping them would
		// ship the overfitting the early stop detected.
		while (trees.size() > bestTreeCount)
		{
			trees.remove(trees.size() - 1);
		}
		return bestLoss;
	}

	public double predict(double[] features)
	{
		double score = baseScore;
		for (Tree tree : trees)
		{
			score += learningRateOf(tree) * tree.predict(features);
		}
		return sigmoid(score);
	}

	public int treeCount()
	{
		return trees.size();
	}

	public boolean isTrained()
	{
		return !trees.isEmpty();
	}

	/** Restores the width after deserialisation, which {@link #featureImportance} needs and the trees do not carry. */
	public void afterLoad(int featureCount)
	{
		this.featureCount = featureCount;
	}

	/**
	 * Each feature's share of the total split gain, summing to one.
	 *
	 * <p>The reason to prefer trees here over a sequence model: this is answerable. A number saying
	 * order-versus-volume carries a third of the signal is something you can check against the fill
	 * model and argue with.
	 */
	public double[] featureImportance()
	{
		double[] importance = new double[featureCount];
		for (Tree tree : trees)
		{
			tree.accumulateImportance(importance);
		}
		double total = 0.0;
		for (double value : importance)
		{
			total += value;
		}
		if (total > 0.0)
		{
			for (int i = 0; i < importance.length; i++)
			{
				importance[i] /= total;
			}
		}
		return importance;
	}

	/** Mean log-loss over the given rows. Clamped away from 0 and 1 so a confident miss stays finite. */
	public static double logLoss(double[][] features, int[] labels, int[] rows, double[] scores)
	{
		double total = 0.0;
		for (int index : rows)
		{
			double probability = sigmoid(scores[index]);
			probability = Math.max(1.0E-9, Math.min(0.999999999, probability));
			total += labels[index] == 1 ? -Math.log(probability) : -Math.log(1.0 - probability);
		}
		return rows.length == 0 ? 0.0 : total / rows.length;
	}

	/** Log-loss of the trained ensemble on a set it has never seen. */
	public double evaluate(double[][] features, int[] labels)
	{
		double total = 0.0;
		for (int i = 0; i < features.length; i++)
		{
			double probability = Math.max(1.0E-9, Math.min(0.999999999, predict(features[i])));
			total += labels[i] == 1 ? -Math.log(probability) : -Math.log(1.0 - probability);
		}
		return features.length == 0 ? 0.0 : total / features.length;
	}

	/** Each tree remembers the rate it was fitted at, so a loaded model shrinks correctly. */
	private double learningRateOf(Tree tree)
	{
		return tree.learningRate;
	}

	private static int[] shuffledIndices(int size, Random random)
	{
		int[] order = new int[size];
		for (int i = 0; i < size; i++)
		{
			order[i] = i;
		}
		for (int i = size - 1; i > 0; i--)
		{
			int j = random.nextInt(i + 1);
			int swap = order[i];
			order[i] = order[j];
			order[j] = swap;
		}
		return order;
	}

	/** Row subsample per round. Falls back to the full set rather than returning an empty one. */
	private static int[] subsample(int[] rows, double fraction, Random random)
	{
		if (fraction >= 1.0)
		{
			return rows;
		}
		List<Integer> chosen = new ArrayList<>(rows.length);
		for (int row : rows)
		{
			if (random.nextDouble() < fraction)
			{
				chosen.add(row);
			}
		}
		if (chosen.isEmpty())
		{
			return rows;
		}
		int[] sample = new int[chosen.size()];
		for (int i = 0; i < sample.length; i++)
		{
			sample[i] = chosen.get(i);
		}
		return sample;
	}

	private static double sigmoid(double score)
	{
		if (score > 30.0)
		{
			return 0.999999999999;
		}
		if (score < -30.0)
		{
			return 1.0E-12;
		}
		return 1.0 / (1.0 + Math.exp(-score));
	}

	/** Hyperparameters, with the values the recovered model was trained at. */
	public static final class Settings
	{
		public double learningRate = 0.05;
		public int maxDepth = 3;
		public int maxTrees = 400;
		public int minSamplesPerLeaf = 20;
		public double subsample = 0.8;
		public int earlyStoppingRounds = 20;
		public long seed = 20260824L;
	}

	/** One regression tree over the residuals. */
	static final class Tree
	{
		private int feature = -1;
		private double threshold;
		private double leafValue;
		private double gain;
		private Tree left;
		private Tree right;
		private double learningRate;

		Tree()
		{
		}

		void fit(double[][] features, double[] residuals, int[] rows, Settings settings, int depth)
		{
			learningRate = settings.learningRate;
			leafValue = mean(residuals, rows);
			if (depth >= settings.maxDepth || rows.length < settings.minSamplesPerLeaf * 2)
			{
				return;
			}
			double bestGain = 0.0;
			int bestFeature = -1;
			double bestThreshold = 0.0;
			for (int candidate = 0; candidate < features[0].length; candidate++)
			{
				double[] values = new double[rows.length];
				for (int i = 0; i < rows.length; i++)
				{
					values[i] = features[rows[i]][candidate];
				}
				double[] sorted = values.clone();
				Arrays.sort(sorted);
				// Seven quantile candidates rather than every distinct value: the split points that
				// matter are where the data actually is, and this is a fraction of the work.
				for (int q = 1; q < 8; q++)
				{
					double threshold = sorted[(int) ((long) sorted.length * (long) q / 8L)];
					double gain = splitGain(features, residuals, rows, candidate, threshold,
						settings.minSamplesPerLeaf);
					if (gain > bestGain)
					{
						bestGain = gain;
						bestFeature = candidate;
						bestThreshold = threshold;
					}
				}
			}
			if (bestFeature < 0)
			{
				return;
			}
			feature = bestFeature;
			threshold = bestThreshold;
			gain = bestGain;
			List<Integer> leftRows = new ArrayList<>();
			List<Integer> rightRows = new ArrayList<>();
			for (int row : rows)
			{
				if (features[row][feature] <= threshold)
				{
					leftRows.add(row);
					continue;
				}
				rightRows.add(row);
			}
			left = new Tree();
			left.fit(features, residuals, toArray(leftRows), settings, depth + 1);
			right = new Tree();
			right.fit(features, residuals, toArray(rightRows), settings, depth + 1);
		}

		double predict(double[] row)
		{
			if (feature < 0)
			{
				return leafValue;
			}
			return row[feature] <= threshold ? left.predict(row) : right.predict(row);
		}

		void accumulateImportance(double[] importance)
		{
			if (feature < 0)
			{
				return;
			}
			importance[feature] += gain;
			left.accumulateImportance(importance);
			right.accumulateImportance(importance);
		}

		/**
		 * Sum-of-squares reduction from the split, and zero when either side would be too small —
		 * which is what stops the tree carving off leaves that describe one lucky trade.
		 */
		private static double splitGain(double[][] features, double[] residuals, int[] rows,
			int feature, double threshold, int minPerLeaf)
		{
			double leftSum = 0.0;
			double rightSum = 0.0;
			int leftCount = 0;
			int rightCount = 0;
			for (int row : rows)
			{
				if (features[row][feature] <= threshold)
				{
					leftSum += residuals[row];
					leftCount++;
					continue;
				}
				rightSum += residuals[row];
				rightCount++;
			}
			if (leftCount < minPerLeaf || rightCount < minPerLeaf)
			{
				return 0.0;
			}
			return leftSum * leftSum / leftCount + rightSum * rightSum / rightCount;
		}

		private static double mean(double[] residuals, int[] rows)
		{
			if (rows.length == 0)
			{
				return 0.0;
			}
			double total = 0.0;
			for (int row : rows)
			{
				total += residuals[row];
			}
			return total / rows.length;
		}

		private static int[] toArray(List<Integer> values)
		{
			int[] array = new int[values.size()];
			for (int i = 0; i < array.length; i++)
			{
				array[i] = values.get(i);
			}
			return array;
		}
	}
}
