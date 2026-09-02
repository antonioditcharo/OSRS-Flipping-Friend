package com.flippingfriend.ui;

import com.flippingfriend.model.Explainer;
import com.flippingfriend.session.FlipRecord;
import com.flippingfriend.session.TradeJournal;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.List;
import javax.swing.JPanel;

class BankTrajectoryPanel extends JPanel
{
	private static final int HEIGHT = 100;
	private static final int PADDING = 10;

	private final TradeJournal journal;
	private final Explainer explainer;

	BankTrajectoryPanel(TradeJournal journal, Explainer explainer)
	{
		this.journal = journal;
		this.explainer = explainer;

		setBackground(UiUtils.BACKGROUND);
		setPreferredSize(new Dimension(UiUtils.CONTENT_WIDTH, HEIGHT));
		setMaximumSize(new Dimension(Integer.MAX_VALUE, HEIGHT));
	}

	@Override
	protected void paintComponent(Graphics g)
	{
		super.paintComponent(g);
		Graphics2D graphics = (Graphics2D) g;
		graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

		List<FlipRecord> flips = journal.getSessionFlips();
		int width = getWidth() - (PADDING * 2);
		int height = getHeight() - (PADDING * 2);

		if (flips.size() < 2)
		{
			graphics.setColor(UiUtils.MUTED);
			graphics.setFont(net.runelite.client.ui.FontManager.getRunescapeFont());
			graphics.drawString("Not enough data to plot bank trajectory.", PADDING, getHeight() / 2);
			return;
		}

		long min = Long.MAX_VALUE;
		long max = Long.MIN_VALUE;

		for (FlipRecord record : flips)
		{
			long val = record.getLiquidValue();
			if (val <= 0) continue;
			if (val < min) min = val;
			if (val > max) max = val;
		}
		
		if (min == Long.MAX_VALUE)
		{
			min = 0;
			max = 1000;
		}
		else if (min == max)
		{
			min -= 1000;
			max += 1000;
		}

		// Draw grid
		graphics.setColor(UiUtils.DIVIDER);
		for (int i = 0; i <= 3; i++)
		{
			int y = PADDING + height * i / 3;
			graphics.drawLine(PADDING, y, PADDING + width, y);
		}

		// Draw line
		graphics.setColor(UiUtils.PROFIT);
		graphics.setStroke(new BasicStroke(2f));
		
		int[] xs = new int[flips.size()];
		int[] ys = new int[flips.size()];

		for (int i = 0; i < flips.size(); i++)
		{
			long val = flips.get(i).getLiquidValue();
			xs[i] = PADDING + (int) ((double) i / (flips.size() - 1) * width);
			
			if (val <= 0) val = min; // Default to min for unrecorded values to avoid graph spikes
			
			double ratio = (double)(val - min) / (max - min);
			ys[i] = PADDING + (int) Math.round((1 - ratio) * height);
		}

		graphics.drawPolyline(xs, ys, flips.size());
		
		// Draw current value
		long current = flips.get(flips.size() - 1).getLiquidValue();
		graphics.setColor(UiUtils.TEXT);
		graphics.setFont(net.runelite.client.ui.FontManager.getRunescapeFont());
		graphics.drawString("Liquid GP: " + explainer.formatGp(current), PADDING + 5, PADDING + 12);
	}
}
