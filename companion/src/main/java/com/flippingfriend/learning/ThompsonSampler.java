package com.flippingfriend.learning;

import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-item Beta posterior over whether a trade completes, sampled rather than averaged.
 *
 * <p>Recovered on 2 September 2026 from {@code flipping-friend-trainer.jar} and verified by bytecode
 * diff against the original. The javadoc is reconstructed; the code is exact.
 *
 * <p>It came back after being dropped with the learner ONNX replaced, because exploration is a
 * <em>policy</em> rather than a model and nothing replaced it. An engine that always acts on its
 * argmax only ever observes outcomes for trades it already rated highly, so the evidence it learns
 * from is censored by the policy that generated it — the classic bandit failure. Its confidence
 * rises while its coverage narrows, and an item it has quietly underrated is never attempted and
 * therefore never corrected. That is a system that calcifies rather than one that improves.
 *
 * <h2>The model's prediction is the prior, not a competitor</h2>
 *
 * <p>{@link #sample} and {@link #expected} both take the analytical probability and treat it as a
 * Beta prior of strength {@value #PRIOR_STRENGTH}. An item with no history returns a draw around
 * what the model said; an item with many observations is dominated by what actually happened. So the
 * sampler never discards the physics — it starts there and is moved by evidence, at a rate set by
 * how much evidence there is.
 *
 * <h2>Rank on a draw, show the mean</h2>
 *
 * <p>Ranking on {@link #sample} lets an under-rated item occasionally win a slot in proportion to
 * how uncertain its estimate is, which is exactly the trade the system needs to make to keep
 * learning. Displaying {@link #expected} keeps the number in front of the player honest and stable:
 * a suggestion whose stated confidence jumped around because it was a random draw would be
 * unreadable, and {@code PortfolioCandidate.withDisplayProbability} exists to carry that split.
 *
 * <p>Thread-safe. Posteriors live in a {@link ConcurrentHashMap} and each {@link Beta} synchronises
 * its own updates, so the planner thread can sample while the offer thread records outcomes.
 */
public final class ThompsonSampler
{
	/**
	 * Weight given to the model's prediction, in pseudo-observations. Six is deliberately small: it
	 * takes only a handful of real fills to start overriding a prior that is wrong about an item,
	 * while still preventing one unlucky flip from condemning it.
	 */
	private static final double PRIOR_STRENGTH = 6.0;

	/** Never return 0 or 1. A certainty is not something a finite sample can justify, and it would
	 * make the expected-value arithmetic downstream degenerate. */
	private static final double MIN_RATE = 0.01;
	private static final double MAX_RATE = 0.99;

	private final Map<Integer, Beta> posteriors = new ConcurrentHashMap<>();
	private final Random random;

	public ThompsonSampler()
	{
		this(new Random());
	}

	/** Seeded, for tests that need a run to be reproducible. */
	public ThompsonSampler(Random random)
	{
		this.random = random;
	}

	public void observe(int itemId, boolean completed)
	{
		posteriors.computeIfAbsent(itemId, id -> new Beta()).update(completed ? 1 : 0, completed ? 0 : 1);
	}

	/** Folds in a batch of resolved outcomes at once. */
	public void observe(int itemId, int completed, int failed)
	{
		if (completed < 0 || failed < 0 || completed + failed == 0)
		{
			return;
		}
		posteriors.computeIfAbsent(itemId, id -> new Beta()).update(completed, failed);
	}

	/**
	 * A draw from this item's posterior. Rank on this: the spread of the draw is the exploration,
	 * and it narrows on its own as evidence accumulates, so the policy anneals without a schedule.
	 */
	public double sample(int itemId, double modelPrediction)
	{
		Beta posterior = posteriors.get(itemId);
		double prior = clamp(modelPrediction);
		if (posterior == null)
		{
			// No history: draw around the model's own claim rather than returning it flat, so an
			// unseen item still gets a chance to be tried above its point estimate.
			return clamp(sampleBeta(prior * PRIOR_STRENGTH, (1.0 - prior) * PRIOR_STRENGTH));
		}
		return clamp(posterior.sample(prior));
	}

	/** The posterior mean. Show this: it is what the system actually believes. */
	public double expected(int itemId, double modelPrediction)
	{
		Beta posterior = posteriors.get(itemId);
		double prior = clamp(modelPrediction);
		return posterior == null ? prior : clamp(posterior.mean(prior));
	}

	public int observationsFor(int itemId)
	{
		Beta posterior = posteriors.get(itemId);
		return posterior == null ? 0 : posterior.total();
	}

	public int itemsTracked()
	{
		return posteriors.size();
	}

	/** Beta(a, b) as the ratio of two gamma draws — the standard construction. */
	private double sampleBeta(double alpha, double beta)
	{
		double x = sampleGamma(Math.max(1.0E-6, alpha));
		double y = sampleGamma(Math.max(1.0E-6, beta));
		return x + y <= 0.0 ? 0.5 : x / (x + y);
	}

	/**
	 * Marsaglia and Tsang's method. Shapes below 1 are handled by the standard boost-and-correct
	 * identity, since the squeeze below assumes shape at least 1.
	 */
	private double sampleGamma(double shape)
	{
		if (shape < 1.0)
		{
			return sampleGamma(shape + 1.0) * Math.pow(random.nextDouble(), 1.0 / shape);
		}
		double d = shape - 1.0 / 3.0;
		double c = 1.0 / Math.sqrt(9.0 * d);
		while (true)
		{
			double x = random.nextGaussian();
			double v = 1.0 + c * x;
			if (v <= 0.0)
			{
				continue;
			}
			v = v * v * v;
			double u = random.nextDouble();
			// The cheap squeeze accepts the overwhelming majority of draws without a logarithm.
			if (u < 1.0 - 0.0331 * x * x * x * x)
			{
				return d * v;
			}
			if (Math.log(u) < 0.5 * x * x + d * (1.0 - v + Math.log(v)))
			{
				return d * v;
			}
		}
	}

	private static double clamp(double value)
	{
		if (!Double.isFinite(value))
		{
			return MIN_RATE;
		}
		return Math.max(MIN_RATE, Math.min(MAX_RATE, value));
	}

	/** One item's posterior. Inner rather than static so it can reach the shared {@link Random}. */
	private final class Beta
	{
		private double successes;
		private double failures;

		private Beta()
		{
		}

		synchronized void update(int completed, int failed)
		{
			successes += completed;
			failures += failed;
		}

		synchronized double mean(double prior)
		{
			double alpha = successes + prior * PRIOR_STRENGTH;
			double beta = failures + (1.0 - prior) * PRIOR_STRENGTH;
			return alpha / (alpha + beta);
		}

		synchronized double sample(double prior)
		{
			return sampleBeta(successes + prior * PRIOR_STRENGTH,
				failures + (1.0 - prior) * PRIOR_STRENGTH);
		}

		synchronized int total()
		{
			return (int) (successes + failures);
		}
	}
}
