package com.flippingfriend.ui;

import com.flippingfriend.model.Explainer;
import com.flippingfriend.model.PositionStatus;
import com.flippingfriend.model.TaxCalculator;
import com.flippingfriend.session.FlipRecord;
import com.flippingfriend.session.Position;
import com.flippingfriend.session.PositionBook;
import com.flippingfriend.session.TradeJournal;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.FontManager;
import net.runelite.client.util.AsyncBufferedImage;

/**
 * The last few completed flips and any ongoing trades, read from the saved journal and position book.
 */
class HistoryPanel extends UiUtils.ScrollableColumn
{
	private static final int MAX_SHOWN = 50;

	private final TradeJournal journal;
	private final PositionBook positions;
	private final ItemManager itemManager;
	private final Explainer explainer;
	private final TaxCalculator taxCalculator;
	private final java.util.function.Supplier<java.util.Map<Integer, PositionStatus>> statuses;

	HistoryPanel(TradeJournal journal, PositionBook positions, ItemManager itemManager, Explainer explainer, TaxCalculator taxCalculator, java.util.function.Supplier<java.util.Map<Integer, PositionStatus>> statuses)
	{
		this.journal = journal;
		this.positions = positions;
		this.itemManager = itemManager;
		this.explainer = explainer;
		this.taxCalculator = taxCalculator;
		this.statuses = statuses;

		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		setBackground(UiUtils.BACKGROUND);
		setAlignmentX(Component.LEFT_ALIGNMENT);
	}

	private String lastSignature;
	
	private Runnable onTradeEdited;
	
	public void setOnTradeEdited(Runnable onTradeEdited)
	{
		this.onTradeEdited = onTradeEdited;
	}

	private static class TradeDisplayItem
	{
		final int itemId;
		final String itemName;
		final int quantity;
		final int buyPrice;
		final int sellPrice;
		final long profit;
		final String timingText;
		final boolean isOngoing;
		final FlipRecord originalRecord;

		TradeDisplayItem(int itemId, String itemName, int quantity, int buyPrice, int sellPrice, long profit, String timingText, boolean isOngoing, FlipRecord originalRecord)
		{
			this.itemId = itemId;
			this.itemName = itemName;
			this.quantity = quantity;
			this.buyPrice = buyPrice;
			this.sellPrice = sellPrice;
			this.profit = profit;
			this.timingText = timingText;
			this.isOngoing = isOngoing;
			this.originalRecord = originalRecord;
		}
	}

	void refresh()
	{
		List<TradeDisplayItem> items = new ArrayList<>();
		java.util.Map<Integer, PositionStatus> currentStatuses = statuses.get();
		long now = Instant.now().getEpochSecond();

		List<Position> held = new ArrayList<>(positions.all());
		held.sort(java.util.Comparator.comparingLong(Position::getOpenedAt).reversed());
		for (Position position : held)
		{
			PositionStatus status = currentStatuses.get(position.getItemId());
			if (status != null && status.isSelling())
			{
				int filled = status.getListedFilled();
				long profit = taxCalculator.netProfit(position.getItemId(), position.getAverageCost(), status.getListedPrice(), filled);
				String timingText = explainer.formatDuration(position.minutesHeld(now)) + " so far";
				items.add(new TradeDisplayItem(position.getItemId(), position.getItemName(), filled, position.getAverageCost(), status.getListedPrice(), profit, timingText, true, null));
			}
		}

		List<FlipRecord> recent = journal.recentFlips(MAX_SHOWN);
		for (FlipRecord record : recent)
		{
			String timingText = explainer.formatDuration(record.actualMinutes()) + "  ·  " + ago(record.getSoldAt());
			items.add(new TradeDisplayItem(record.getItemId(), record.getItemName(), record.getQuantity(), record.getBuyPrice(), record.getSellPrice(), record.getProfit(), timingText, false, record));
		}

		String signature = signatureOf(items);
		if (signature.equals(lastSignature))
		{
			return;
		}
		lastSignature = signature;

		removeAll();

		if (items.isEmpty())
		{
			JPanel empty = UiUtils.card();
			empty.setLayout(new BorderLayout());
			empty.add(UiUtils.mutedText("No completed or ongoing flips yet. Once a buy and its sell have both "
				+ "finished, the trade is saved here and kept between sessions."), BorderLayout.CENTER);
			UiUtils.constrainWidth(empty);
			add(empty);
		}
		else
		{
			for (int i = 0; i < items.size(); i++)
			{
				add(buildRow(items.get(i)));
				if (i < items.size() - 1)
				{
					add(UiUtils.gap(UiUtils.SPACE_XS));
				}
			}
		}

		revalidate();
		repaint();
	}

	private String signatureOf(List<TradeDisplayItem> items)
	{
		StringBuilder signature = new StringBuilder();
		for (TradeDisplayItem item : items)
		{
			signature.append(item.itemId).append(':')
				.append(item.quantity).append(':')
				.append(item.buyPrice).append(':')
				.append(item.sellPrice).append(':')
				.append(item.profit).append(':')
				.append(item.timingText).append(':')
				.append(item.isOngoing).append('|');
		}
		return signature.toString();
	}

	private JPanel buildRow(TradeDisplayItem item)
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
		AsyncBufferedImage image = itemManager.getImage(item.itemId);
		if (image != null)
		{
			image.addTo(icon);
		}
		
		JPanel iconWrapper = new JPanel();
		iconWrapper.setLayout(new BoxLayout(iconWrapper, BoxLayout.Y_AXIS));
		iconWrapper.setOpaque(false);
		icon.setAlignmentX(Component.CENTER_ALIGNMENT);
		iconWrapper.add(icon);
		iconWrapper.add(javax.swing.Box.createRigidArea(new Dimension(0, 4)));
		JPanel statusIndicator = new JPanel();
		statusIndicator.setPreferredSize(new Dimension(20, 4));
		statusIndicator.setMaximumSize(new Dimension(20, 4));
		statusIndicator.setBackground(item.isOngoing ? UiUtils.WARNING : UiUtils.PROFIT);
		statusIndicator.setAlignmentX(Component.CENTER_ALIGNMENT);
		iconWrapper.add(statusIndicator);
		row.add(iconWrapper, BorderLayout.WEST);

		JPanel text = new JPanel();
		text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
		text.setOpaque(false);

		JLabel name = UiUtils.label(item.itemName, UiUtils.TEXT, FontManager.getRunescapeFont());
		name.setAlignmentX(Component.LEFT_ALIGNMENT);
		text.add(name);

		JLabel detail = UiUtils.small(explainer.formatNumber(item.quantity) + " × "
			+ explainer.formatNumber(item.buyPrice) + " avg → "
			+ explainer.formatNumber(item.sellPrice) + " avg");
		detail.setAlignmentX(Component.LEFT_ALIGNMENT);
		text.add(detail);

		JLabel stateLabel = UiUtils.small(item.isOngoing ? "Ongoing" : "Complete");
		stateLabel.setForeground(item.isOngoing ? UiUtils.WARNING : UiUtils.PROFIT);
		stateLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
		text.add(stateLabel);

		JLabel timing = UiUtils.small(item.timingText);
		timing.setAlignmentX(Component.LEFT_ALIGNMENT);
		text.add(timing);

		row.add(text, BorderLayout.CENTER);

		JLabel profit = UiUtils.label(explainer.formatGp(item.profit),
			UiUtils.profitColor(item.profit), FontManager.getRunescapeBoldFont());
		profit.setVerticalAlignment(SwingConstants.TOP);
		row.add(profit, BorderLayout.EAST);

		UiUtils.constrainWidth(row);
		
		if (item.originalRecord != null || item.isOngoing)
		{
			javax.swing.JPopupMenu popupMenu = new javax.swing.JPopupMenu();
			
			javax.swing.JMenuItem editItem = new javax.swing.JMenuItem("Edit Trade");
			editItem.addActionListener(e -> {
				if (item.isOngoing) {
					promptEditOngoingTrade(item);
				} else {
					promptEditTrade(item.originalRecord);
				}
			});
			popupMenu.add(editItem);
			
			javax.swing.JMenuItem deleteItem = new javax.swing.JMenuItem("Delete Trade");
			deleteItem.addActionListener(e -> {
				int confirm = javax.swing.JOptionPane.showConfirmDialog(this, 
					"Are you sure you want to delete this trade?\nThis will remove it from your " + (item.isOngoing ? "holdings" : "journal") + " permanently.",
					"Delete Trade", javax.swing.JOptionPane.YES_NO_OPTION);
				if (confirm == javax.swing.JOptionPane.YES_OPTION) {
					if (item.isOngoing) {
						positions.setQuantity(item.itemId, 0); // Delete ongoing
					} else {
						journal.deleteFlip(item.originalRecord);
					}
					if (onTradeEdited != null) {
						onTradeEdited.run();
					}
				}
			});
			popupMenu.add(deleteItem);
			
			row.setComponentPopupMenu(popupMenu);
		}

		return row;
	}

	private void promptEditTrade(FlipRecord record)
	{
		TradeEditDialog dialog = new TradeEditDialog(
			javax.swing.SwingUtilities.getWindowAncestor(this), 
			"Edit Trade: " + record.getItemName(),
			record.getItemId(), record.getQuantity(), record.getBuyPrice(), record.getSellPrice(), false, taxCalculator
		);
		dialog.setVisible(true);

		TradeEditDialog.EditResult result = dialog.getResult();
		if (result != null)
		{
			int newQty = result.quantity;
			int newBuy = result.avgBuyPrice;
			int newSell = result.avgSellPrice;
			
			long newTax = result.exactTax;
			long newProfit = result.exactProfit;
			
			FlipRecord updated = new FlipRecord(
				record.getItemId(), record.getItemName(), newQty, newBuy, newSell,
				newTax, newProfit, record.getBoughtAt(), record.getSoldAt(),
				record.getPredictedMinutes(), record.getPredictedProfit(), record.getRiskProfile()
			);
			
			journal.updateFlip(record, updated);
			if (onTradeEdited != null) {
				onTradeEdited.run();
			}
		}
	}

	private void promptEditOngoingTrade(TradeDisplayItem item)
	{
		TradeEditDialog dialog = new TradeEditDialog(
			javax.swing.SwingUtilities.getWindowAncestor(this), 
			"Edit Ongoing Trade: " + item.itemName,
			item.itemId, item.quantity, item.buyPrice, 0, true, taxCalculator
		);
		dialog.setVisible(true);

		TradeEditDialog.EditResult result = dialog.getResult();
		if (result != null)
		{
			int newQty = result.quantity;
			int newBuy = result.avgBuyPrice;
			
			positions.setCostAndQuantity(item.itemId, newQty, newBuy);
			
			if (onTradeEdited != null) {
				onTradeEdited.run();
			}
		}
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
