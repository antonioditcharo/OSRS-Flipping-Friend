package com.flippingfriend.companion;

import com.flippingfriend.core.OfferEvent;
import com.flippingfriend.learning.IsotonicCalibrator;
import com.flippingfriend.learning.ThompsonSampler;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Turns settled offers into corrections the engine can apply.
 *
 * <p>This is the join the system was built around and never made. {@code ExecutionRecorder} has
 * always written what happened, and the planner has always claimed what would happen; until
 * 2 September 2026 the two were computed side by side in {@code CompanionService.offer} and both
 * discarded. Everything here consumes evidence that was already being produced.
 *
 * <h2>Two channels, deliberately separate</h2>
 *
 * <p><b>Completion</b> is calibrated per leg, not per flip. A buy resting below the market and a sell
 * resting above it fail for unrelated reasons, and pooling them produces a curve that describes
 * neither — which is also why {@code getCompletionProbability()}, the product of the two, is the wrong
 * thing to score a single leg against. Audit item 29 asks for reliability diagrams per leg for the
 * same reason.
 *
 * <p><b>Duration</b> is a per-item multiplier from {@link LearnedDurations}, fed from the
 * {@code paired_*} columns, which count only offers that both completed and carried a prediction.
 *
 * <h2>What is deliberately not observed</h2>
 *
 * <p>An offer with no claim attached — an unprompted trade, or a leg the planner never scored —
 * contributes nothing. Feeding it in as a zero would teach the calibrator that the model predicts
 * zero and is usually wrong, which is worse than not learning at all. {@code predictedCompletion}'s
 * own javadoc has said so since before there was anything to feed.
 *
 * <p>Both corrections stay inert until they have enough evidence:
 * {@link IsotonicCalibrator#MIN_OBSERVATIONS} for completion, and a per-item shrinkage weight for
 * duration. A cold calibrator returns its input unchanged, so wiring this in cannot move a price
 * before it has grounds to.
 */
final class FillCalibration
{
	/**
	 * How often the per-item duration multipliers are recomputed. {@link LearnedDurations#refresh}
	 * reads the whole {@code execution_stat} table and rebuilds every multiplier, which is far too
	 * much to repeat on each settling offer during a busy session, and far more often than the
	 * numbers meaningfully move.
	 */
	private static final long DURATION_REFRESH_SECONDS = 120;

	/**
	 * How far above its own mean a draw must land before it counts as exploration. Purely for
	 * reporting — the ranking uses every draw. Without a threshold the rate would read as ~50%,
	 * since half of all draws land above the mean by some trivial amount.
	 */
	private static final double EXPLORATION_MARGIN = 0.02;

	private final IsotonicCalibrator buyCompletion = new IsotonicCalibrator();
	private final IsotonicCalibrator sellCompletion = new IsotonicCalibrator();
	private final LearnedDurations durations = new LearnedDurations();

	/**
	 * Exploration over the buy leg — the leg that decides whether a position is entered at all, and
	 * therefore the only one where a draw can win an under-rated item a slot it would never otherwise
	 * get. The sell leg is calibrated but not sampled: by the time it matters the capital is already
	 * committed, so perturbing it buys no information and only adds noise to the ranking.
	 */
	private final ThompsonSampler exploration = new ThompsonSampler();

	/**
	 * One observation in {@value #HOLDOUT_EVERY} is withheld from the curve and used only to score it.
	 * A calibrator scored on the data it was fitted to always looks good — that is what fitting means
	 * — so the held-out channel is the only thing that can tell a correction from an overfit.
	 */
	private static final int HOLDOUT_EVERY = 5;

	/** Held-out observations needed before the gate will pass or fail rather than abstain. */
	private static final int MIN_HOLDOUT = 50;

	private final Brier buyHoldout = new Brier();
	private final Brier sellHoldout = new Brier();
	private int seen;

	private final AtomicLong draws = new AtomicLong();
	private final AtomicLong exploratoryDraws = new AtomicLong();

	private volatile long lastDurationRefresh;

	/**
	 * Records one settled offer against the claim that was made for it.
	 *
	 * @param claimed the probability the plan gave this leg, or 0 when no claim was attached
	 * @return whether the observation was used, so a caller can report the fact
	 */
	boolean observeSettled(OfferEvent event, double claimed)
	{
		if (event == null || claimed <= 0)
		{
			return false;
		}
		boolean buying = event.isBuying();
		boolean occurred = event.isComplete();
		if (++seen % HOLDOUT_EVERY == 0)
		{
			// Withheld. Scored against both the raw claim and what the curve would have said, so the
			// two are compared on identical evidence neither has seen.
			holdoutFor(buying).add(claimed, calibratorFor(buying).calibrate(claimed), occurred);
		}
		else
		{
			calibratorFor(buying).observe(claimed, occurred);
		}
		if (event.isBuying())
		{
			// Same evidence, different use: the calibrator learns how wrong the number was, the
			// sampler learns how uncertain this item still is.
			exploration.observe(event.getItemId(), event.isComplete());
		}
		return true;
	}

	/**
	 * A draw from this item's completion posterior, for <em>ranking</em>. Pass the already-calibrated
	 * probability: it becomes the prior, so the draw is centred on the corrected estimate rather than
	 * the raw one.
	 *
	 * <p>Rank on this and display {@link #calibrate}. The spread of the draw is the exploration, and
	 * it narrows on its own as evidence accumulates — the policy anneals without a schedule, and an
	 * item nobody has tried is exactly the one whose draw is widest.
	 */
	double explore(int itemId, double calibratedProbability)
	{
		double drawn = exploration.sample(itemId, calibratedProbability);
		draws.incrementAndGet();
		if (drawn > calibratedProbability + EXPLORATION_MARGIN)
		{
			exploratoryDraws.incrementAndGet();
		}
		return drawn;
	}

	/**
	 * Share of draws that landed more than {@value #EXPLORATION_MARGIN} above their own mean.
	 *
	 * <p>Read this as the width of the posterior, not as a suggestion-level exploration rate. Audit
	 * item 26 targets roughly one suggestion in ten being a close runner-up, and that is a different
	 * quantity: it would need the optimiser run twice per cycle, once on means and once on draws, and
	 * the two top picks compared. Item 18 already flags the optimiser as expensive and unbudgeted, so
	 * a third exact solve per cycle is the wrong trade until that is fixed.
	 *
	 * <p>What this number does give is the annealing curve. It starts high — an untried item drawing
	 * against a prior of strength {@code PRIOR_STRENGTH} is genuinely uncertain — and falls as fills
	 * accumulate. A rate that stays flat means evidence is not reaching the sampler.
	 */
	double explorationRate()
	{
		long total = draws.get();
		return total <= 0 ? 0.0 : (double) exploratoryDraws.get() / total;
	}

	/** True when the duration multipliers are due a rebuild. */
	boolean durationsDue(long nowEpochSeconds)
	{
		return nowEpochSeconds - lastDurationRefresh >= DURATION_REFRESH_SECONDS;
	}

	void refreshDurations(Map<Integer, SqliteStore.ExecutionStat> stats, long nowEpochSeconds)
	{
		durations.refresh(stats);
		lastDurationRefresh = nowEpochSeconds;
	}

	/**
	 * Corrects a predicted completion probability for the leg it belongs to. Returns the input
	 * unchanged until that leg's calibrator has {@link IsotonicCalibrator#MIN_OBSERVATIONS}.
	 */
	double calibrate(boolean buying, double predicted)
	{
		// Fitted is not the same as useful. Until the held-out evidence says the curve beats the raw
		// claim, the raw claim is what ships -- a correction that has not been shown to help is just
		// a second source of error wearing the word "calibrated".
		return earningItsPlace(buying) ? calibratorFor(buying).calibrate(predicted) : predicted;
	}

	/** Whether this leg's curve has demonstrably beaten the uncorrected claim out of sample. */
	boolean earningItsPlace(boolean buying)
	{
		Brier holdout = holdoutFor(buying);
		return calibratorFor(buying).isActive() && holdout.count() >= MIN_HOLDOUT && holdout.improves();
	}

	/**
	 * The promotion gates, evaluated on held-out data only.
	 *
	 * <p>Written down and scored mechanically because a gate checked by eye is a gate that gets
	 * waived on the day it matters — which is {@link GateReport}'s own argument for existing.
	 */
	synchronized GateReport gate()
	{
		GateReport report = new GateReport();
		addGates(report, true, "buy");
		addGates(report, false, "sell");
		return report;
	}

	private void addGates(GateReport report, boolean buying, String leg)
	{
		IsotonicCalibrator calibrator = calibratorFor(buying);
		Brier holdout = holdoutFor(buying);
		report.add(leg + " leg has enough evidence",
			calibrator.isActive(),
			calibrator.observations() + " of " + IsotonicCalibrator.MIN_OBSERVATIONS + " observations");
		report.add(leg + " leg has enough held-out evidence",
			holdout.count() >= MIN_HOLDOUT,
			holdout.count() + " of " + MIN_HOLDOUT + " held out");
		report.add(leg + " leg beats the uncorrected claim",
			holdout.count() >= MIN_HOLDOUT && holdout.improves(),
			holdout.describe());
	}

	/**
	 * The held-out evidence, for the record that watches this improve over time.
	 *
	 * <p>Both legs together. Buy and sell calibrators are never pooled for <em>correcting</em> — they
	 * are different processes and mixing them is the bug those two separate instances exist to
	 * prevent — but for "is the calibration earning its place at all", one figure across both is the
	 * question a reader is asking, and two lines that always move together tell them less than one.
	 */
	synchronized int heldOutCount()
	{
		return holdoutFor(true).count() + holdoutFor(false).count();
	}

	/**
	 * Brier skill against the uncorrected claim, weighted by how much was held out on each leg.
	 * <p>
	 * Above zero means the correction is helping. Zero is the honest answer before there is enough to
	 * say, and is what should be plotted rather than a gap — a component earning nothing is a fact,
	 * not missing data.
	 */
	synchronized double heldOutSkill()
	{
		Brier buy = holdoutFor(true);
		Brier sell = holdoutFor(false);
		int total = buy.count() + sell.count();
		if (total <= 0)
		{
			return 0;
		}
		return (buy.skill() * buy.count() + sell.skill() * sell.count()) / total;
	}

	/** Settled offers the calibrators have seen, across both legs. */
	synchronized int observationCount()
	{
		return calibratorFor(true).observations() + calibratorFor(false).observations();
	}

	/** How much longer this item's fills really take than predicted. 1.0 means no correction. */
	double durationMultiplier(int itemId)
	{
		return durations.multiplierFor(itemId);
	}

	boolean isActive()
	{
		return buyCompletion.isActive() || sellCompletion.isActive() || durations.itemsLearned() > 0
			|| exploration.itemsTracked() > 0;
	}

	/**
	 * One line for {@code /v1/health}. Reported rather than hidden because the failure this whole
	 * change addresses is a component that existed, did nothing, and said nothing about it — a
	 * calibrator nobody can see is indistinguishable from one that was never wired.
	 */
	String summary()
	{
		if (!isActive())
		{
			int seen = buyCompletion.observations() + sellCompletion.observations();
			return String.format("Calibration: learning, %d of %d settled offers needed.",
				seen, IsotonicCalibrator.MIN_OBSERVATIONS * 2);
		}
		return String.format(
			"Calibration: buy %d obs%s [%s], sell %d obs%s [%s]; durations for %d items, pooled %.2fx; "
				+ "%.0f%% of draws above estimate across %d items.",
			buyCompletion.observations(), bias(buyCompletion), applied(true),
			sellCompletion.observations(), bias(sellCompletion), applied(false),
			durations.itemsLearned(), durations.overallRatio(),
			explorationRate() * 100.0, exploration.itemsTracked());
	}

	/** How much longer or shorter fills actually take than the model predicts, pooled across items. */
	synchronized double pooledDurationRatio()
	{
		return durations.overallRatio();
	}

	/** How many items have enough settled offers to have a duration correction of their own. */
	synchronized int durationItemsLearned()
	{
		return durations.itemsLearned();
	}

	/**
	 * Whether the correction is actually being applied to that leg, and why not when it is not. The
	 * distinction between "fitted" and "in use" is the whole point of the gate, and a health line that
	 * hid it would be describing a system that does not exist.
	 */
	private String applied(boolean buying)
	{
		if (earningItsPlace(buying))
		{
			return String.format("applied, skill %+.1f%%", holdoutFor(buying).skill() * 100.0);
		}
		if (!calibratorFor(buying).isActive())
		{
			return "not applied: still fitting";
		}
		if (holdoutFor(buying).count() < MIN_HOLDOUT)
		{
			return "not applied: " + holdoutFor(buying).count() + "/" + MIN_HOLDOUT + " held out";
		}
		return "not applied: no better than raw";
	}

	/** Positive bias means the model is optimistic — it claimed more than happened. */
	private static String bias(IsotonicCalibrator calibrator)
	{
		if (!calibrator.isActive())
		{
			return "";
		}
		return String.format(" (%+.1f%% optimistic)", calibrator.meanBias() * 100.0);
	}

	/**
	 * Everything learned from settled offers, in a form the model registry can store.
	 *
	 * <p>{@link LearnedDurations} is deliberately absent: it is rebuilt from {@code execution_stat} on
	 * the next refresh, so persisting it would create a second copy of the same truth and a way for
	 * the two to disagree. Only state with no other home is snapshotted.
	 */
	static final class Snapshot
	{
		/** Guards against a build with different bin counts silently loading a mangled curve. */
		int version = SNAPSHOT_VERSION;
		double[] buyCompletion;
		double[] sellCompletion;
		Map<Integer, double[]> exploration;
		/** Held-out scores. Without these the gate re-earns its evidence from scratch on every restart. */
		double[] buyHoldout;
		double[] sellHoldout;
		int seen;
	}

	static final int SNAPSHOT_VERSION = 1;

	synchronized Snapshot snapshot()
	{
		Snapshot state = new Snapshot();
		state.buyCompletion = buyCompletion.snapshot();
		state.sellCompletion = sellCompletion.snapshot();
		state.exploration = exploration.snapshot();
		state.buyHoldout = buyHoldout.snapshot();
		state.sellHoldout = sellHoldout.snapshot();
		state.seen = seen;
		return state;
	}

	/**
	 * Restores a snapshot. A version mismatch or a malformed payload is discarded rather than
	 * partially applied — starting cold is honest, and the corrections are inert while cold, so the
	 * cost of refusing a bad snapshot is a slow restart rather than a wrong price.
	 *
	 * @return whether any actual evidence came back — not merely whether the payload parsed. An
	 *         empty snapshot is well-formed and restores nothing, and reporting that as a successful
	 *         restore would put "Restored calibration from a previous session" on the health line of
	 *         a companion that is starting cold.
	 */
	synchronized boolean restore(Snapshot state)
	{
		if (state == null || state.version != SNAPSHOT_VERSION)
		{
			return false;
		}
		buyCompletion.restore(state.buyCompletion);
		sellCompletion.restore(state.sellCompletion);
		exploration.restore(state.exploration);
		buyHoldout.restore(state.buyHoldout);
		sellHoldout.restore(state.sellHoldout);
		seen = Math.max(0, state.seen);
		return buyCompletion.observations() > 0
			|| sellCompletion.observations() > 0
			|| exploration.itemsTracked() > 0;
	}

	private IsotonicCalibrator calibratorFor(boolean buying)
	{
		return buying ? buyCompletion : sellCompletion;
	}

	private Brier holdoutFor(boolean buying)
	{
		return buying ? buyHoldout : sellHoldout;
	}

	/**
	 * Squared-error scoring of a probability against what happened, for the raw claim and the
	 * corrected one side by side.
	 *
	 * <p>Brier rather than accuracy because a fill probability is not a yes/no call — being right
	 * about direction while wrong about magnitude is precisely the failure calibration exists to fix,
	 * and accuracy cannot see it.
	 */
	static final class Brier
	{
		private double rawError;
		private double calibratedError;
		private int count;

		synchronized void add(double raw, double calibrated, boolean occurred)
		{
			double actual = occurred ? 1.0 : 0.0;
			rawError += (raw - actual) * (raw - actual);
			calibratedError += (calibrated - actual) * (calibrated - actual);
			count++;
		}

		synchronized int count()
		{
			return count;
		}

		/** Strictly better, so a tie leaves the simpler uncorrected number in place. */
		synchronized boolean improves()
		{
			return count > 0 && calibratedError < rawError;
		}

		/** Brier skill score against the raw claim: above zero means the correction is helping. */
		synchronized double skill()
		{
			return count <= 0 || rawError <= 0 ? 0.0 : 1.0 - calibratedError / rawError;
		}

		synchronized double[] snapshot()
		{
			return new double[]{ rawError, calibratedError, count };
		}

		synchronized void restore(double[] state)
		{
			if (state == null || state.length != 3
				|| !Double.isFinite(state[0]) || !Double.isFinite(state[1]) || state[2] < 0)
			{
				return;
			}
			rawError = state[0];
			calibratedError = state[1];
			count = (int) state[2];
		}

		synchronized String describe()
		{
            if (count <= 0)
            {
                return "no held-out evidence yet";
            }
			return String.format("Brier %.4f corrected vs %.4f raw, skill %+.1f%% over %d",
				calibratedError / count, rawError / count, skill() * 100.0, count);
		}
	}
}
