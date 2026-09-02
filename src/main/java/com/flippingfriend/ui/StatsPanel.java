package com.flippingfriend.ui;

import com.flippingfriend.model.Explainer;
import com.flippingfriend.session.SessionStats;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import net.runelite.client.ui.FontManager;

/**
 * How trading has actually gone, this session and overall.
 * <p>
 * Both figures are shown because they answer different questions. The session total is what happened
 * just now; the lifetime total, read straight from the saved journal, is the only one with enough
 * trades behind it to say whether the thing is working. Every number is after tax and counts only
 * completed flips, so nothing is flattered by positions that have not been sold yet.
 */
class StatsPanel extends JPanel
{
	private final Explainer explainer;

	private final JLabel profitValue = new JLabel();
	private final JLabel perHourValue = new JLabel();
	private final JLabel flipsValue = new JLabel();
	private final JLabel winRateValue = new JLabel();
	private final JLabel holdValue = new JLabel();
	private final JLabel taxValue = new JLabel();

	private final java.util.Map<com.flippingfriend.model.MarketSector, JLabel> sectorLabels = new java.util.HashMap<>();

	private final JPanel lifetimePanel = new JPanel();
	private final JLabel lifetimeProfit = new JLabel();
	private final JLabel lifetimeFlips = new JLabel();
	private final JLabel lifetimeWinRate = new JLabel();

	StatsPanel(Explainer explainer)
	{
		this.explainer = explainer;

		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		setBackground(UiUtils.CARD);
		setBorder(UiUtils.cardBorder());
		setAlignmentX(Component.LEFT_ALIGNMENT);

		add(row("Profit", profitValue));
		add(row("Per hour", perHourValue));
		add(row("Flips done", flipsValue));
		add(row("Went well", winRateValue));
		add(row("Average hold", holdValue));
		add(row("Tax paid", taxValue));

		add(UiUtils.gap(UiUtils.SPACE_S));
		JLabel sectorHeading = UiUtils.small("Sector Profit (Session)");
		sectorHeading.setAlignmentX(Component.LEFT_ALIGNMENT);
		add(sectorHeading);
		add(UiUtils.gap(UiUtils.SPACE_XS));

		for (com.flippingfriend.model.MarketSector sector : com.flippingfriend.model.MarketSector.values())
		{
			JLabel sectorLabel = new JLabel("—");
			sectorLabels.put(sector, sectorLabel);
			add(row(sector.getName(), sectorLabel));
		}

		lifetimePanel.setLayout(new BoxLayout(lifetimePanel, BoxLayout.Y_AXIS));
		lifetimePanel.setOpaque(false);
		lifetimePanel.setAlignmentX(Component.LEFT_ALIGNMENT);

		JPanel divider = new JPanel();
		divider.setBackground(UiUtils.DIVIDER);
		divider.setPreferredSize(new Dimension(0, 1));
		divider.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));

		lifetimePanel.add(UiUtils.gap(UiUtils.SPACE_M));
		lifetimePanel.add(divider);
		lifetimePanel.add(UiUtils.gap(UiUtils.SPACE_S));

		JLabel heading = UiUtils.small("All time");
		heading.setAlignmentX(Component.LEFT_ALIGNMENT);
		lifetimePanel.add(heading);
		lifetimePanel.add(UiUtils.gap(UiUtils.SPACE_XS));

		lifetimePanel.add(row("Profit", lifetimeProfit));
		lifetimePanel.add(row("Flips done", lifetimeFlips));
		lifetimePanel.add(row("Went well", lifetimeWinRate));

		add(lifetimePanel);

		update(SessionStats.empty(), SessionStats.empty());
	}

	void update(SessionStats session, SessionStats lifetime)
	{
		profitValue.setText(explainer.formatGp(session.getProfit()));
		profitValue.setForeground(UiUtils.profitColor(session.getProfit()));

		perHourValue.setText(session.getFlips() == 0 ? "—" : explainer.formatGp(session.getProfitPerHour()));
		perHourValue.setForeground(UiUtils.profitColor(session.getProfitPerHour()));

		flipsValue.setText(Integer.toString(session.getFlips()));
		winRateValue.setText(formatWinRate(session));
		holdValue.setText(session.getFlips() == 0
			? "—"
			: explainer.formatDuration(session.getAverageHoldMinutes()));
		taxValue.setText(explainer.formatGp(session.getTaxPaid()));

		java.util.Map<com.flippingfriend.model.MarketSector, Long> sessionSectors = session.getSectorProfits();
		for (com.flippingfriend.model.MarketSector sector : com.flippingfriend.model.MarketSector.values())
		{
			JLabel label = sectorLabels.get(sector);
			long prof = sessionSectors != null ? sessionSectors.getOrDefault(sector, 0L) : 0L;
			if (prof == 0)
			{
				label.setText("—");
				label.setForeground(UiUtils.TEXT);
			}
			else
			{
				label.setText(explainer.formatGp(prof));
				label.setForeground(UiUtils.profitColor(prof));
			}
		}

		// Hidden entirely until there is history, so a first-time panel is not full of dashes.
		boolean hasLifetime = lifetime.getFlips() > 0;
		lifetimePanel.setVisible(hasLifetime);
		if (hasLifetime)
		{
			lifetimeProfit.setText(explainer.formatGp(lifetime.getProfit()));
			lifetimeProfit.setForeground(UiUtils.profitColor(lifetime.getProfit()));
			lifetimeFlips.setText(Integer.toString(lifetime.getFlips()));
			lifetimeWinRate.setText(formatWinRate(lifetime));
		}

		UiUtils.constrainWidth(this);
	}

	private static String formatWinRate(SessionStats stats)
	{
		if (stats.getFlips() == 0)
		{
			return "—";
		}
		return Math.round(stats.getWinRate() * 100) + "%  (" + stats.getWins() + " of "
			+ stats.getFlips() + ")";
	}

	private JPanel row(String name, JLabel value)
	{
		JPanel row = new JPanel(new BorderLayout(UiUtils.SPACE_S, 0));
		row.setOpaque(false);
		row.setAlignmentX(Component.LEFT_ALIGNMENT);
		row.setBorder(javax.swing.BorderFactory.createEmptyBorder(2, 0, 2, 0));

		row.add(UiUtils.small(name), BorderLayout.WEST);

		value.setFont(FontManager.getRunescapeFont());
		value.setForeground(UiUtils.TEXT);
		value.setHorizontalAlignment(JLabel.RIGHT);
		row.add(value, BorderLayout.EAST);

		UiUtils.constrainWidth(row);
		return row;
	}
}
