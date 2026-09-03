package com.flippingfriend.learning;

import java.util.ArrayList;
import java.util.List;

/**
 * Maps a predicted probability onto the rate that was actually observed at that prediction.
 *
 * <p>Recovered on 2 September 2026 from {@code flipping-friend-trainer.jar} and verified by bytecode
 * diff against the original. The javadoc is reconstructed; the code is exact.
 *
 * <p>A model can rank well and still be wrong about magnitude — the audit records buy legs predicted
 * at 98% completing 76% of the time. That gap does not merely mis-state profit: the optimiser's
 * objective is {@code expectedProfit / expectedSlotHours} and both terms are probability-weighted, so
 * a miscalibrated probability re-orders the ranking, and it does so worst on the trades the model is
 * most confident about. This class is the correction, and it is the piece the ONNX fill model is
 * missing entirely.
 *
 * <p>Calibration is isotonic: predictions are bucketed into {@value #BINS} bins, each bin's observed
 * success rate is computed, and the pool-adjacent-violators algorithm merges any adjacent pair that
 * runs backwards until the mapping is monotonic. Monotonicity is the whole point — it corrects the
 * model's numbers without ever reversing its ordering, so a calibrated model still prefers whatever
 * the raw model preferred.
 *
 * <p>Nothing is corrected until {@value #MIN_OBSERVATIONS} observations have accumulated; below that
 * {@link #calibrate} returns the input unchanged, so a cold calibrator is inert rather than
 * confidently wrong. Bins outside the observed range keep the identity mapping, so a prediction in a
 * region never seen is passed through rather than extrapolated.
 */
public final class IsotonicCalibrator
{
	private static final int BINS = 20;

	/** Below this, {@link #calibrate} is a pass-through. A calibrator fitted on noise is worse than none. */
	public static final int MIN_OBSERVATIONS = 200;

	private final double[] successes = new double[BINS];
	private final double[] counts = new double[BINS];

	/** Cached fit, invalidated on every observation. Volatile so {@link #calibrate} can read it unsynchronised. */
	private volatile double[] calibrated;

	private int observations;

	/** Records one resolved outcome: what was predicted, and whether it happened. */
	public synchronized void observe(double predicted, boolean occurred)
	{
		if (!Double.isFinite(predicted))
		{
			return;
		}
		int bin = binFor(predicted);
		counts[bin] += 1.0;
		if (occurred)
		{
			successes[bin] += 1.0;
		}
		observations++;
		calibrated = null;
	}

	/**
	 * Records an aggregate: {@code weight} outcomes at this prediction, of which {@code successRate}
	 * is the fraction that occurred. Lets a batch of resolved paper trades be folded in without
	 * replaying them one at a time.
	 */
	public synchronized void observe(double predicted, double successRate, double weight)
	{
		if (!Double.isFinite(predicted) || !Double.isFinite(successRate) || weight <= 0.0)
		{
			return;
		}
		int bin = binFor(predicted);
		counts[bin] += weight;
		successes[bin] += weight * Math.max(0.0, Math.min(1.0, successRate));
		observations += (int) Math.ceil(weight);
		calibrated = null;
	}

	/**
	 * Corrects one prediction. Interpolates linearly between bin centres so the mapping is continuous
	 * rather than a staircase — a prediction one gp either side of a bin edge should not jump.
	 */
	public double calibrate(double predicted)
	{
		if (!Double.isFinite(predicted))
		{
			return 0.0;
		}
		double[] mapping = fitted();
		if (mapping == null)
		{
			return clamp(predicted);
		}
		double position = clamp(predicted) * BINS - 0.5;
		int low = (int) Math.floor(position);
		double fraction = position - low;
		int lowBin = Math.max(0, Math.min(BINS - 1, low));
		int highBin = Math.max(0, Math.min(BINS - 1, low + 1));
		return clamp(mapping[lowBin] * (1.0 - fraction) + mapping[highBin] * fraction);
	}

	public synchronized int observations()
	{
		return observations;
	}

	/** True once enough has been seen to correct anything. */
	public boolean isActive()
	{
		return fitted() != null;
	}

	/**
	 * Count-weighted mean of (predicted − actual) across populated bins. Positive means the model is
	 * optimistic. Reported rather than applied — it is the headline number for whether calibration is
	 * earning its place.
	 */
	public synchronized double meanBias()
	{
		double weighted = 0.0;
		double total = 0.0;
		for (int bin = 0; bin < BINS; bin++)
		{
			if (counts[bin] <= 0.0)
			{
				continue;
			}
			double raw = (bin + 0.5) / BINS;
			double actual = successes[bin] / counts[bin];
			weighted += counts[bin] * (raw - actual);
			total += counts[bin];
		}
		return total <= 0.0 ? 0.0 : weighted / total;
	}

	/** Double-checked: the common path is a volatile read, and only a refit takes the lock. */
	private double[] fitted()
	{
		double[] current = calibrated;
		if (current != null)
		{
			return current;
		}
		synchronized (this)
		{
			if (calibrated != null)
			{
				return calibrated;
			}
			if (observations < MIN_OBSERVATIONS)
			{
				return null;
			}
			calibrated = fit();
			return calibrated;
		}
	}

	/**
	 * Pool adjacent violators. Walks the populated bins in order, and whenever the running block's
	 * rate exceeds the one before it, merges the two into their count-weighted mean — repeating until
	 * the sequence is non-decreasing.
	 */
	private double[] fit()
	{
		List<double[]> blocks = new ArrayList<>();
		int highestPopulated = -1;
		for (int bin = 0; bin < BINS; bin++)
		{
			if (counts[bin] <= 0.0)
			{
				continue;
			}
			highestPopulated = bin;
			blocks.add(new double[]{ bin, counts[bin], successes[bin] / counts[bin] });
			while (blocks.size() > 1)
			{
				double[] last = blocks.get(blocks.size() - 1);
				double[] previous = blocks.get(blocks.size() - 2);
				if (previous[2] <= last[2])
				{
					break;
				}
				double weight = previous[1] + last[1];
				double mean = (previous[1] * previous[2] + last[1] * last[2]) / weight;
				blocks.remove(blocks.size() - 1);
				previous[1] = weight;
				previous[2] = mean;
			}
		}

		double[] mapping = new double[BINS];
		if (blocks.isEmpty())
		{
			for (int bin = 0; bin < BINS; bin++)
			{
				mapping[bin] = identityFor(bin);
			}
			return mapping;
		}

		// Outside the range actually observed, pass the prediction through rather than extrapolating
		// a correction from evidence that does not cover it.
		int firstBin = (int) blocks.get(0)[0];
		int lastBin = highestPopulated;
		int blockIndex = 0;
		for (int bin = 0; bin < BINS; bin++)
		{
			if (bin < firstBin || bin > lastBin)
			{
				mapping[bin] = identityFor(bin);
				continue;
			}
			while (blockIndex + 1 < blocks.size() && blocks.get(blockIndex + 1)[0] <= bin)
			{
				blockIndex++;
			}
			mapping[bin] = clamp(blocks.get(blockIndex)[2]);
		}
		return mapping;
	}

	/**
	 * Flattens the accumulated evidence for persistence: {@code [observations, successes...,
	 * counts...]}, {@value #BINS} of each.
	 *
	 * <p>Added after recovery, so this class no longer matches the archived bytecode byte for byte.
	 * That is deliberate. The diff was how the recovery was <em>verified</em> (commit 1b4b448), not a
	 * promise never to change the class — and without persistence a calibrator that needs
	 * {@value #MIN_OBSERVATIONS} observations per leg may never reach them on a companion that
	 * restarts daily.
	 */
	public synchronized double[] snapshot()
	{
		double[] state = new double[1 + BINS * 2];
		state[0] = observations;
		System.arraycopy(successes, 0, state, 1, BINS);
		System.arraycopy(counts, 0, state, 1 + BINS, BINS);
		return state;
	}

	/**
	 * Restores a snapshot, ignoring anything of the wrong shape. A snapshot written by a build with a
	 * different bin count is discarded rather than stretched: a mangled calibration curve is worse
	 * than a cold one, because a cold one is honest about knowing nothing.
	 */
	public synchronized boolean restore(double[] state)
	{
		if (state == null || state.length != 1 + BINS * 2)
		{
			return false;
		}
		observations = (int) state[0];
		System.arraycopy(state, 1, successes, 0, BINS);
		System.arraycopy(state, 1 + BINS, counts, 0, BINS);
		calibrated = null;
		return true;
	}

	private static double identityFor(int bin)
	{
		return clamp((bin + 0.5) / BINS);
	}

	private static int binFor(double predicted)
	{
		int bin = (int) (clamp(predicted) * BINS);
		return Math.max(0, Math.min(BINS - 1, bin));
	}

	private static double clamp(double value)
	{
		return Math.max(0.0, Math.min(1.0, value));
	}
}
