package com.flippingfriend.ui;

import com.flippingfriend.model.LearningReading;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.util.List;
import java.util.Map;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import net.runelite.client.ui.FontManager;

/**
 * What the model has actually learned, and how much it is allowed to act on.
 *
 * <p>The README described this panel and START HERE told the reader to check it. Neither was true:
 * the plugin had Recommendation, Portfolio, Holding, Recent trades, Performance and Bank Trajectory,
 * and nothing at all about learning. Everything the companion learns was visible only in a single
 * dense sentence on an HTTP endpoint nobody was reading.
 *
 * <p>That is not a cosmetic gap. The capture rate collapsed from a prior of 0.85 to 0.074 on six
 * observations and cut every order in the plan elevenfold, and the only symptom anyone could see was
 * that the trades had gone small. The number that did it was being written down the whole time.
 *
 * <p>So every row here shows the evidence next to the value. A model that has measured nothing says
 * so, and a number that has moved a long way on a handful of observations is meant to look alarming,
 * because that is exactly the shape of the failure this panel exists to catch.
 */
class LearningPanel extends JPanel
{
	/** Below this, a rate that has wandered far from its prior is far more likely wrong than right. */
	private static final long THIN_EVIDENCE = 30;

	private final JLabel captureValue = new JLabel();
	private final JLabel captureNote = new JLabel();
	private final JLabel calibrationValue = new JLabel();
	private final JLabel hazardValue = new JLabel();
	private final JLabel shadowValue = new JLabel();
	private final JLabel durationsValue = new JLabel();
	private final JLabel recordValue = new JLabel();
	private final JLabel status = new JLabel();

	LearningPanel()
	{
		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		setBackground(UiUtils.CARD);
		setBorder(UiUtils.cardBorder());
		setAlignmentX(Component.LEFT_ALIGNMENT);

		status.setFont(FontManager.getRunescapeSmallFont());
		status.setForeground(UiUtils.MUTED);
		status.setAlignmentX(Component.LEFT_ALIGNMENT);
		add(status);
		add(UiUtils.gap(UiUtils.SPACE_S));

		add(row("Order sizing", captureValue));
		captureNote.setFont(FontManager.getRunescapeSmallFont());
		captureNote.setForeground(UiUtils.MUTED);
		captureNote.setAlignmentX(Component.LEFT_ALIGNMENT);
		add(captureNote);

		add(UiUtils.gap(UiUtils.SPACE_S));
		JPanel divider = new JPanel();
		divider.setBackground(UiUtils.DIVIDER);
		divider.setPreferredSize(new Dimension(0, 1));
		divider.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
		add(divider);
		add(UiUtils.gap(UiUtils.SPACE_S));

		add(row("Fill estimates", calibrationValue));
		add(row("How long fills take", durationsValue));
		add(row("Waiting cost", hazardValue));
		add(row("Trades watched", shadowValue));
		add(row("History kept", recordValue));

		clear("Waiting for the companion.");
	}

	/** Nothing to show: say which, rather than leaving six dashes and no reason. */
	void clear(String why)
	{
		status.setText(why);
		captureValue.setText("—");
		captureValue.setForeground(UiUtils.TEXT);
		captureNote.setText(" ");
		calibrationValue.setText("—");
		durationsValue.setText("—");
		hazardValue.setText("—");
		shadowValue.setText("—");
		recordValue.setText("—");
	}

	/**
	 * @param metrics the companion's latest reading of each metric, or null when it is not reachable
	 */
	void update(List<LearningReading> metrics)
	{
		if (metrics == null || metrics.isEmpty())
		{
			clear("The companion has not written anything down yet.");
			return;
		}

		Map<String, LearningReading> by = new java.util.HashMap<>();
		for (LearningReading metric : metrics)
		{
			by.put(metric.getName(), metric);
		}

		status.setText("What the companion has worked out so far.");
		updateCapture(by.get("capture.pooled"), by.get("capture.assumed"), by.get("capture.items"));

		LearningReading observations = by.get("calibration.observations");
		LearningReading skill = by.get("calibration.skill");
		if (observations == null || observations.getValue() <= 0)
		{
			calibrationValue.setText("learning");
		}
		else if (skill == null || skill.getValue() <= 0)
		{
			calibrationValue.setText(count(observations.getValue()) + " seen, not applied yet");
		}
		else
		{
			calibrationValue.setText(percent(skill.getValue()) + " better than guessing");
		}

		// durations.pooled, not hazard.duration_dependence. The latter describes how the fill hazard
		// changes as an offer stands, which is a real number about something else entirely -- and
		// showing it under this label put 3.45x on screen while the companion's own health line said
		// 0.50x. Two numbers, one caption, and no way for a reader to tell which was being described.
		LearningReading pooledDuration = by.get("durations.pooled");
		durationsValue.setText(pooledDuration == null || pooledDuration.getValue() <= 0
			? "learning"
			: String.format("%.2fx expected, %s items", pooledDuration.getValue(),
				count(pooledDuration.getSample())));

		LearningReading weight = by.get("gate.weight");
		hazardValue.setText(weight == null || weight.getValue() <= 0
			? "not applied yet"
			: String.format("%.2fx", weight.getValue()));

		LearningReading open = by.get("shadow.open");
		LearningReading resolved = by.get("shadow.resolved");
		if (open == null && resolved == null)
		{
			shadowValue.setText("—");
		}
		else
		{
			long resolvedCount = resolved == null ? 0 : (long) resolved.getValue();
			long openCount = open == null ? 0 : (long) open.getValue();
			shadowValue.setText(resolvedCount <= 0
				? count(openCount) + " open, none settled"
				: count(resolvedCount) + " settled, " + count(openCount) + " open");
		}

		recordValue.setText(count(metrics.size()) + " measures");
	}

	/**
	 * The row that matters most, because it is the one that silently resizes every order.
	 *
	 * <p>Capture is the share of the market's flow the model expects to win, and it multiplies
	 * straight into how much it tells you to buy. Halve it and every order halves. So the assumed
	 * value is shown beside the measured one and the sample beside both: "0.07 (was 0.85), from 6
	 * offers" is a sentence someone can act on, and it is the sentence that was missing.
	 */
	private void updateCapture(LearningReading pooled, LearningReading assumed,
		LearningReading items)
	{
		if (pooled == null)
		{
			captureValue.setText("—");
			captureNote.setText(" ");
			return;
		}

		double prior = assumed == null ? 0 : assumed.getValue();
		long sample = items == null ? pooled.getSample() : (long) items.getValue();

		if (sample <= 0)
		{
			captureValue.setText(percent(pooled.getValue()) + " of the market");
			captureValue.setForeground(UiUtils.TEXT);
			captureNote.setText("Your risk level's assumption. No fills measured yet.");
			return;
		}

		captureValue.setText(percent(pooled.getValue()) + " of the market");
		captureNote.setText(String.format("Measured from %s. Your risk level assumes %s.",
			count(sample) + (sample == 1 ? " offer" : " offers"), percent(prior)));

		// Loud on purpose when a large move rests on almost nothing. This is the exact shape of the
		// failure that cut every order elevenfold, and it read as "the trades went small" for hours.
		boolean farBelow = prior > 0 && pooled.getValue() < prior / 2;
		if (farBelow && sample < THIN_EVIDENCE)
		{
			captureValue.setForeground(UiUtils.WARNING);
			captureNote.setText(captureNote.getText()
				+ " That is a long way down on very little evidence — worth checking.");
		}
		else if (farBelow)
		{
			captureValue.setForeground(UiUtils.WARNING);
		}
		else
		{
			captureValue.setForeground(UiUtils.TEXT);
		}
	}

	private static String percent(double rate)
	{
		return Math.round(rate * 100) + "%";
	}

	private static String count(double value)
	{
		return String.format("%,d", Math.round(value));
	}

	private JPanel row(String name, JLabel value)
	{
		JPanel row = new JPanel(new BorderLayout(0, UiUtils.SPACE_XS));
		row.setOpaque(false);
		row.setAlignmentX(Component.LEFT_ALIGNMENT);
		row.setBorder(javax.swing.BorderFactory.createEmptyBorder(4, 0, 4, 0));

		JLabel nameLabel = UiUtils.small(name);
		row.add(nameLabel, BorderLayout.NORTH);

		value.setFont(FontManager.getRunescapeFont());
		value.setForeground(UiUtils.TEXT);
		value.setHorizontalAlignment(JLabel.LEFT);
		row.add(value, BorderLayout.CENTER);

		return row;
	}

}
