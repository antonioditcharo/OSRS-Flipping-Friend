package com.flippingfriend.companion;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What every learned quantity was, at a moment, with the evidence behind it.
 *
 * <p>The companion knows a great deal about how well it is doing and can say none of it about
 * <em>yesterday</em>. Every learned table is cumulative — {@code capture_stat} holds totals,
 * {@code fill_hazard} holds a life table, {@code execution_stat} holds sums — so each answers "what
 * is the capture rate" and none can answer "what was it on Tuesday, and what changed". A monitor
 * built on those is a gauge: it shows a reading, and a reading cannot show improvement.
 *
 * <p>That is the same lesson as the price archive, in a second place. History is not recoverable
 * after the fact; either it is being written down or it is gone. This writes it down.
 *
 * <h2>Every number carries its sample</h2>
 *
 * <p>The rule that makes this evidence rather than decoration. A capture rate moving from 0.50 to
 * 0.34 is one of two completely different events depending on whether the count behind it went from
 * 8 to 11 or from 40 to 900, and a chart that plots only the value cannot tell them apart — it will
 * show a confident line wobbling and invite somebody to explain the wobble. Storing {@code sample}
 * beside {@code value} is what lets the panel say "this moved because it learned something" rather
 * than "this moved".
 *
 * <p>It is also what lets it say the opposite. A metric whose sample has not grown has not learned
 * anything, whatever its value did, and that is worth showing plainly instead of leaving a reader to
 * infer it from a flat line they cannot distinguish from a stalled one.
 *
 * <h2>Names are an interface</h2>
 *
 * <p>A chart follows one metric across months. Renaming {@code capture.pooled} breaks the history
 * into two unrelated series and there is no way to notice, because both halves look fine. The names
 * below are therefore fixed strings rather than anything derived, and adding a metric is free while
 * renaming one is not.
 */
final class LearningSnapshot
{
	/** One measurement: what it was, and how much stood behind it. */
	static final class Metric
	{
		final String name;
		final double value;
		final long sample;

		Metric(String name, double value, long sample)
		{
			this.name = name;
			this.value = value;
			this.sample = sample;
		}
	}

	private final long takenAt;
	private final Map<String, Metric> metrics = new LinkedHashMap<>();

	LearningSnapshot(long takenAt)
	{
		this.takenAt = takenAt;
	}

	long takenAt()
	{
		return takenAt;
	}

	/**
	 * Records one metric.
	 *
	 * @param sample how many observations stand behind {@code value}; zero means "nothing yet", which
	 *               is a fact worth storing rather than a row worth skipping
	 */
	LearningSnapshot record(String name, double value, long sample)
	{
		if (name != null && !name.isEmpty() && Double.isFinite(value))
		{
			metrics.put(name, new Metric(name, value, Math.max(0, sample)));
		}
		return this;
	}

	List<Metric> metrics()
	{
		return new ArrayList<>(metrics.values());
	}

	Metric get(String name)
	{
		return metrics.get(name);
	}

	int size()
	{
		return metrics.size();
	}

	/**
	 * Gathers everything the companion currently knows about itself.
	 *
	 * <p>Deliberately a small, chosen set rather than everything countable. A panel with two hundred
	 * series on it is as uninformative as one with none, and the question each of these answers is
	 * "has this component learned something, and did it help".
	 */
	static LearningSnapshot of(long takenAt, CaptureRates capture, double assumedCapture,
		FillHazard hazard, FillCalibration calibration, ShadowTrader shadow, LearnedFillModel learned)
	{
		LearningSnapshot snapshot = new LearningSnapshot(takenAt);

		if (capture != null)
		{
			// The share of flow we actually win, against the share the risk appetite assumes. The gap
			// between those two is the whole of what this learner is for, so both are stored: a
			// reader seeing 0.34 needs to know it was compared against 0.50 and not against nothing.
			snapshot.record("capture.pooled", capture.pooledRate(assumedCapture),
				capture.observationCount());
			snapshot.record("capture.assumed", assumedCapture, capture.observationCount());
			snapshot.record("capture.items", capture.itemsLearned(), capture.observationCount());
		}

		if (hazard != null)
		{
			// Above 1 means waiting genuinely costs something and the memoryless assumption in
			// FillModel is wrong by that factor. At 1 this component is earning nothing, which is
			// exactly as worth plotting.
			snapshot.record("hazard.duration_dependence", hazard.durationDependence(),
				hazard.observationCount());
			snapshot.record("hazard.informative_censoring", hazard.informativeCensoringShare(),
				hazard.censoredCount());
			// The curve itself, one series per slice, so a reader can watch its shape change rather
			// than watch a single summary of it move for reasons they cannot see.
			for (int bucket = 0; bucket < FillHazard.BUCKET_MINUTES.length; bucket++)
			{
				snapshot.record("hazard.rate." + FillHazard.BUCKET_MINUTES[bucket] + "m",
					hazard.hazardRate(0, true, bucket) * 60, hazard.atRisk(bucket));
			}
		}

		if (calibration != null)
		{
			// What the health line has always printed as "durations ... pooled 0.50x". The panel was
			// showing hazard.duration_dependence for this instead, which is a different quantity --
			// how the fill hazard varies with how long an offer has been standing, not how wrong the
			// duration estimate is -- and the two disagreed on screen, 3.45x against 0.50x.
			snapshot.record("durations.pooled", calibration.pooledDurationRatio(),
				calibration.durationItemsLearned());
			snapshot.record("calibration.observations", calibration.observationCount(),
				calibration.observationCount());
			// Skill, not the Brier score itself. A raw Brier is a number that is lower when things are
			// better and carries no answer to "better than what" — which is the kind of figure this
			// whole record exists to stop putting in front of somebody. Skill is measured against the
			// uncorrected claim, so above zero means the correction is helping and zero means it is
			// not, and both are readable without a footnote.
			snapshot.record("calibration.skill", calibration.heldOutSkill(),
				calibration.heldOutCount());
		}

		if (shadow != null)
		{
			// What the refusals cost. A veto that would have made money is the only direct evidence
			// that a threshold is set too tight, and it is invisible from the accepted trades alone.
			snapshot.record("shadow.open", shadow.openCount(), shadow.openCount());
			snapshot.record("shadow.resolved", shadow.resolvedCount(), shadow.resolvedCount());
		}

		if (learned != null)
		{
			// The weight the gate has granted. Zero means the learned model has not beaten the
			// analytical one out of sample and is contributing nothing, which is the honest state for
			// most of a system's life and should look like a flat line at zero rather than a gap.
			snapshot.record("gate.weight", learned.weight(), learned.trainingRows());
		}

		return snapshot;
	}
}
