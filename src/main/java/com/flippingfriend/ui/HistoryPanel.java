package com.flippingfriend.ui;

import com.flippingfriend.model.Explainer;
import com.flippingfriend.session.FlipRecord;
import com.flippingfriend.session.TradeJournal;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.time.Instant;
import java.util.List;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.FontManager;
import net.runelite.client.util.AsyncBufferedImage;

/**
 * The last few completed flips, read from the saved journal.
 * <p>
 * This is the visible proof that trades are being recorded. A profit total alone gives no way to
 * tell a working tracker from a broken one; a list of individual flips with what was paid, what it
 * sold for and how long it took does. It is also the same data the calibrator learns from, so if
 * something looks wrong here it is wrong there too.
 */
class HistoryPanel extends JPanel
{
	private static final int MAX_SHOWN = 8;

	private final TradeJournal journal;
	private final ItemManager itemManager;
	private final Explainer explainer;

	HistoryPanel(TradeJournal journal, ItemManager itemManager, Explainer explainer)
	{
		this.journal = journal;
		this.itemManager = itemManager;
		this.explainer = explainer;

		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		setBackground(UiUtils.BACKGROUND);
		setAlignmentX(Component.LEFT_ALIGNMENT);
	}

	/** What was last drawn, so an unchanged refresh can be skipped rather than blinked through. */
	private String lastSignature;

	void refresh()
	{
		List<FlipRecord> recent = journal.recentFlips(MAX_SHOWN);

		// Nothing here changes between flips, so rebuilding the list on every poll only made the
		// section blink. The signature covers the records drawn and the "x ago" line, which is the
		// one part of a finished flip that keeps moving.
		String signature = signatureOf(recent);
		if (signature.equals(lastSignature))
		{
			return;
		}
		lastSignature = signature;

		removeAll();

		if (recent.isEmpty())
		{
			JPanel empty = UiUtils.card();
			empty.setLayout(new BorderLayout());
			empty.add(UiUtils.mutedText("No completed flips yet. Once a buy and its sell have both "
				+ "finished, the trade is saved here and kept between sessions."), BorderLayout.CENTER);
			UiUtils.constrainWidth(empty);
			add(empty);
		}
		else
		{
			for (int i = 0; i < recent.size(); i++)
			{
				add(buildRow(recent.get(i)));
				if (i < recent.size() - 1)
				{
					add(UiUtils.gap(UiUtils.SPACE_XS));
				}
			}
		}

		revalidate();
		repaint();
	}

	private String signatureOf(List<FlipRecord> recent)
	{
		StringBuilder signature = new StringBuilder();
		for (FlipRecord record : recent)
		{
			signature.append(record.getItemId()).append(':')
				.append(record.getSoldAt()).append(':')
				.append(record.getProfit()).append(':')
				.append(ago(record.getSoldAt())).append('|');
		}
		return signature.toString();
	}

	private JPanel buildRow(FlipRecord record)
	{
		JPanel row = new JPanel(new BorderLayout(UiUtils.SPACE_S, 0));
		row.setBackground(UiUtils.CARD);
		row.setBorder(javax.swing.BorderFactory.createEmptyBorder(UiUtils.SPACE_S, UiUtils.SPACE_S,
			UiUtils.SPACE_S, UiUtils.SPACE_S));
		row.setAlignmentX(Component.LEFT_ALIGNMENT);

		JLabel icon = new JLabel();
		icon.setPreferredSize(new Dimension(26, 26));
		icon.setHorizontalAlignment(SwingConstants.CENTER);
		icon.setVerticalAlignment(SwingConstants.TOP);
		AsyncBufferedImage image = itemManager.getImage(record.getItemId());
		if (image != null)
		{
			image.addTo(icon);
		}
		row.add(icon, BorderLayout.WEST);

		JPanel text = new JPanel();
		text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
		text.setOpaque(false);

		JLabel name = UiUtils.label(record.getItemName(), UiUtils.TEXT, FontManager.getRunescapeFont());
		name.setAlignmentX(Component.LEFT_ALIGNMENT);
		text.add(name);

		JLabel detail = UiUtils.small(explainer.formatNumber(record.getQuantity()) + " × "
			+ explainer.formatNumber(record.getBuyPrice()) + " → "
			+ explainer.formatNumber(record.getSellPrice()));
		detail.setAlignmentX(Component.LEFT_ALIGNMENT);
		text.add(detail);

		JLabel timing = UiUtils.small(explainer.formatDuration(record.actualMinutes()) + "  ·  "
			+ ago(record.getSoldAt()));
		timing.setAlignmentX(Component.LEFT_ALIGNMENT);
		text.add(timing);

		row.add(text, BorderLayout.CENTER);

		JLabel profit = UiUtils.label(explainer.formatGp(record.getProfit()),
			UiUtils.profitColor(record.getProfit()), FontManager.getRunescapeBoldFont());
		profit.setVerticalAlignment(SwingConstants.TOP);
		row.add(profit, BorderLayout.EAST);

		UiUtils.constrainWidth(row);
		return row;
	}

	private String ago(long epochSeconds)
	{
		if (epochSeconds <= 0)
		{
			return "";
		}
		long minutes = Math.max(0, (Instant.now().getEpochSecond() - epochSeconds) / 60);
		if (minutes < 1)
		{
			return "just now";
		}
		return explainer.formatDuration(minutes) + " ago";
	}
}
