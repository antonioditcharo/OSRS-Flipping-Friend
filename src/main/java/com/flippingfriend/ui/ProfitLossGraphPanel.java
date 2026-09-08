package com.flippingfriend.ui;

import com.flippingfriend.model.Explainer;
import com.flippingfriend.session.FlipRecord;
import com.flippingfriend.session.TradeJournal;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JComboBox;
import javax.swing.JPanel;

class ProfitLossGraphPanel extends JPanel
{
	private static final int HEIGHT = 100;
	private static final int PADDING = 10;

	private final TradeJournal journal;
	private final Explainer explainer;
	private final JComboBox<Timeframe> timeframeSelector;
	private final GraphArea graphArea;

	enum Timeframe
	{
		ALL_TIME("All time", -1),
		THREE_MONTHS("3 month", 90 * 24 * 60 * 60L),
		ONE_MONTH("1 month", 30 * 24 * 60 * 60L),
		ONE_WEEK("1 week", 7 * 24 * 60 * 60L),
		TWENTY_FOUR_HOURS("24 hr", 24 * 60 * 60L),
		ONE_HOUR("1 hr", 60 * 60L);

		private final String name;
		private final long seconds;

		Timeframe(String name, long seconds)
		{
			this.name = name;
			this.seconds = seconds;
		}

		@Override
		public String toString()
		{
			return name;
		}
		
		public long getSeconds() 
		{
			return seconds;
		}
	}

	ProfitLossGraphPanel(TradeJournal journal, Explainer explainer)
	{
		this.journal = journal;
		this.explainer = explainer;

		setLayout(new BorderLayout());
		setBackground(UiUtils.BACKGROUND);
		
		timeframeSelector = new JComboBox<>(Timeframe.values());
		timeframeSelector.setSelectedItem(Timeframe.ONE_WEEK);
		timeframeSelector.setFocusable(false);
		timeframeSelector.addActionListener(e -> repaint());

		JPanel topPanel = new JPanel(new BorderLayout());
		topPanel.setBackground(UiUtils.BACKGROUND);
		topPanel.setBorder(javax.swing.BorderFactory.createEmptyBorder(0, 0, UiUtils.SPACE_S, 0));
		topPanel.add(timeframeSelector, BorderLayout.WEST);
		
		graphArea = new GraphArea();

		add(topPanel, BorderLayout.NORTH);
		add(graphArea, BorderLayout.CENTER);
		
		int totalHeight = topPanel.getPreferredSize().height + HEIGHT;
		setPreferredSize(new Dimension(UiUtils.CONTENT_WIDTH, totalHeight));
		setMinimumSize(new Dimension(UiUtils.CONTENT_WIDTH, totalHeight));
	}

	private class GraphArea extends JPanel
	{
		GraphArea()
		{
			setBackground(UiUtils.BACKGROUND);
			setPreferredSize(new Dimension(UiUtils.CONTENT_WIDTH, HEIGHT));
			setMinimumSize(new Dimension(0, HEIGHT));
			setMaximumSize(new Dimension(Integer.MAX_VALUE, HEIGHT));
		}

		@Override
		protected void paintComponent(Graphics g)
		{
			super.paintComponent(g);
			Graphics2D graphics = (Graphics2D) g;
			graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

			int width = getWidth() - (PADDING * 2);
			int height = getHeight() - (PADDING * 2);

			Timeframe selectedTimeframe = (Timeframe) timeframeSelector.getSelectedItem();
			if (selectedTimeframe == null) return;
			
			long cutoff = 0;
			if (selectedTimeframe.getSeconds() > 0)
			{
				cutoff = Instant.now().getEpochSecond() - selectedTimeframe.getSeconds();
			}

			List<FlipRecord> allFlips = journal.getHistory();
			List<FlipRecord> flips = new ArrayList<>();
			for (FlipRecord flip : allFlips)
			{
				if (flip.getSoldAt() >= cutoff)
				{
					flips.add(flip);
				}
			}

			if (flips.isEmpty())
			{
				graphics.setColor(UiUtils.MUTED);
				graphics.setFont(net.runelite.client.ui.FontManager.getRunescapeFont());
				graphics.drawString("No trades in this timeframe.", PADDING, getHeight() / 2);
				return;
			}

			long min = 0; // We always want 0 to be on the graph if possible or to scale relative to it
			long max = 0;
			long currentProfit = 0;
			
			long[] cumulativeProfits = new long[flips.size() + 1];
			cumulativeProfits[0] = 0; // Start at 0 profit

			for (int i = 0; i < flips.size(); i++)
			{
				currentProfit += flips.get(i).getProfit();
				cumulativeProfits[i + 1] = currentProfit;
				if (currentProfit < min) min = currentProfit;
				if (currentProfit > max) max = currentProfit;
			}
			
			if (min == max)
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

			// Draw zero line if visible
			if (min < 0 && max > 0)
			{
				double zeroRatio = (double)(0 - min) / (max - min);
				int zeroY = PADDING + (int) Math.round((1 - zeroRatio) * height);
				graphics.setColor(UiUtils.MUTED);
				graphics.drawLine(PADDING, zeroY, PADDING + width, zeroY);
			}

			// Draw line
			graphics.setColor(currentProfit >= 0 ? UiUtils.PROFIT : UiUtils.LOSS);
			graphics.setStroke(new BasicStroke(2f));
			
			int[] xs = new int[cumulativeProfits.length];
			int[] ys = new int[cumulativeProfits.length];

			for (int i = 0; i < cumulativeProfits.length; i++)
			{
				long val = cumulativeProfits[i];
				xs[i] = PADDING + (int) ((double) i / (cumulativeProfits.length - 1) * width);
				double ratio = (double)(val - min) / (max - min);
				ys[i] = PADDING + (int) Math.round((1 - ratio) * height);
			}

			graphics.drawPolyline(xs, ys, cumulativeProfits.length);
			
			// Draw current value
			graphics.setColor(UiUtils.TEXT);
			graphics.setFont(net.runelite.client.ui.FontManager.getRunescapeFont());
			graphics.drawString("Profit: " + explainer.formatGp(currentProfit), PADDING + 5, PADDING + 12);
		}
	}
}
