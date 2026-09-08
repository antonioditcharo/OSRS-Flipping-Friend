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
	private final JLabel roiValue = new JLabel();
	private final JLabel flipsValue = new JLabel();
	private final JLabel winRateValue = new JLabel();
	private final JLabel holdValue = new JLabel();
	private final JLabel taxValue = new JLabel();

	private final java.util.Map<com.flippingfriend.model.MarketSector, JLabel> sectorLabels = new java.util.HashMap<>();

	private final javax.swing.JButton resetSession = new javax.swing.JButton("Reset session");
	private final javax.swing.JButton resetAllTime = new javax.swing.JButton("Reset all time");

	/** Told what to do when a reset is asked for; the panel does not reach for the journal itself. */
	private Runnable onResetSession = () -> { };
	private Runnable onResetAllTime = () -> { };

	private final JPanel lifetimePanel = new JPanel();
	private final JLabel lifetimeProfit = new JLabel();
	private final JLabel lifetimeRoi = new JLabel();
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
		add(row("ROI", roiValue));
		add(row("Flips done", flipsValue));
		add(row("Went well", winRateValue));
		add(row("Average hold", holdValue));
		add(row("Tax paid", taxValue));

		add(UiUtils.gap(UiUtils.SPACE_S));
		JLabel sectorHeading = UiUtils.small("▶ Sector Profit (Session)");
		sectorHeading.setAlignmentX(Component.LEFT_ALIGNMENT);
		sectorHeading.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
		add(sectorHeading);
		add(UiUtils.gap(UiUtils.SPACE_XS));

		JPanel sectorPanel = new JPanel();
		sectorPanel.setLayout(new BoxLayout(sectorPanel, BoxLayout.Y_AXIS));
		sectorPanel.setOpaque(false);
		sectorPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
		sectorPanel.setVisible(false);

		sectorHeading.addMouseListener(new java.awt.event.MouseAdapter() {
			@Override
			public void mouseClicked(java.awt.event.MouseEvent e) {
				boolean visible = !sectorPanel.isVisible();
				sectorPanel.setVisible(visible);
				sectorHeading.setText(visible ? "▼ Sector Profit (Session)" : "▶ Sector Profit (Session)");
			}
		});

		for (com.flippingfriend.model.MarketSector sector : com.flippingfriend.model.MarketSector.values())
		{
			JLabel sectorLabel = new JLabel("—");
			sectorLabels.put(sector, sectorLabel);
			sectorPanel.add(row(sector.getName(), sectorLabel));
		}
		add(sectorPanel);

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
		lifetimePanel.add(row("ROI", lifetimeRoi));
		lifetimePanel.add(row("Flips done", lifetimeFlips));
		lifetimePanel.add(row("Went well", lifetimeWinRate));

		add(lifetimePanel);

		// Two buttons, because they undo different things and one of them is not really undoable.
		//
		// "Reset session" only moves the line this session is counted from; every flip stays on
		// record and the all-time figures do not move. "Reset all time" is the one to be careful
		// with, so it asks first, and even having asked it RENAMES the journal rather than deleting
		// it -- that file is the only record of how the plugin's own predictions turned out, and the
		// calibrator is built from it and nothing else.
		JPanel resets = new JPanel();
		resets.setLayout(new BoxLayout(resets, BoxLayout.X_AXIS));
		resets.setOpaque(false);
		resets.setAlignmentX(Component.LEFT_ALIGNMENT);
		styleSmallButton(resetSession);
		styleSmallButton(resetAllTime);
		resetSession.addActionListener(event -> onResetSession.run());
		resetAllTime.addActionListener(event -> confirmAllTime());
		resets.add(resetSession);
		resets.add(javax.swing.Box.createRigidArea(new Dimension(UiUtils.SPACE_S, 0)));
		resets.add(resetAllTime);
		UiUtils.constrainWidth(resets);

		add(UiUtils.gap(UiUtils.SPACE_M));
		add(resets);

		update(SessionStats.empty(), SessionStats.empty());
	}

	void setOnResetSession(Runnable onResetSession)
	{
		this.onResetSession = onResetSession;
	}

	void setOnResetAllTime(Runnable onResetAllTime)
	{
		this.onResetAllTime = onResetAllTime;
	}

	/**
	 * Asks before wiping the record, and says what actually happens to it.
	 *
	 * <p>A confirmation that only says "are you sure" tells the reader nothing they did not already
	 * know. This one says where the file goes, because the honest answer -- it is renamed, not
	 * destroyed -- is the thing that makes the decision easy.
	 */
	private void confirmAllTime()
	{
		int answer = javax.swing.JOptionPane.showConfirmDialog(this,
			"Set the all-time profit, flip count and win rate back to zero?\n\n"
				+ "Your trade history is kept: the journal file is renamed with today's date rather\n"
				+ "than deleted, so it can be put back. The plugin will start learning from an empty\n"
				+ "record, which means its fill estimates lose what they have worked out so far.",
			"Reset all-time figures", javax.swing.JOptionPane.OK_CANCEL_OPTION,
			javax.swing.JOptionPane.WARNING_MESSAGE);
		if (answer == javax.swing.JOptionPane.OK_OPTION)
		{
			onResetAllTime.run();
		}
	}

	private static void styleSmallButton(javax.swing.JButton button)
	{
		button.setFont(FontManager.getRunescapeSmallFont());
		button.setForeground(UiUtils.MUTED);
		button.setBackground(UiUtils.CARD_HOVER);
		button.setFocusPainted(false);
		button.setBorder(javax.swing.BorderFactory.createEmptyBorder(3, 8, 3, 8));
	}

	void update(SessionStats session, SessionStats lifetime)
	{
		profitValue.setText(explainer.formatGp(session.getProfit()));
		profitValue.setForeground(UiUtils.profitColor(session.getProfit()));

		perHourValue.setText(session.getFlips() == 0 ? "—" : explainer.formatGp(session.getProfitPerHour()));
		perHourValue.setForeground(UiUtils.profitColor(session.getProfitPerHour()));

		double roi = session.getRoi();
		if (session.getFlips() == 0 || session.getCost() == 0) {
			roiValue.setText("—");
			roiValue.setForeground(UiUtils.TEXT);
		} else {
			roiValue.setText(String.format("%.1f%%", roi * 100));
			roiValue.setForeground(UiUtils.profitColor(session.getProfit()));
		}

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
			
			double lRoi = lifetime.getRoi();
			if (lifetime.getCost() == 0) {
				lifetimeRoi.setText("—");
				lifetimeRoi.setForeground(UiUtils.TEXT);
			} else {
				lifetimeRoi.setText(String.format("%.1f%%", lRoi * 100));
				lifetimeRoi.setForeground(UiUtils.profitColor(lifetime.getProfit()));
			}
			
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
