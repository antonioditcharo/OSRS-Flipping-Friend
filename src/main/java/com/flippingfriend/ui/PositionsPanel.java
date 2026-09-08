package com.flippingfriend.ui;

import com.flippingfriend.data.Candle;
import com.flippingfriend.data.LatestPrice;
import com.flippingfriend.data.MarketDataService;
import com.flippingfriend.model.Explainer;
import com.flippingfriend.model.TaxCalculator;
import com.flippingfriend.session.Position;
import com.flippingfriend.session.PositionBook;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.FontManager;
import net.runelite.client.util.AsyncBufferedImage;

/**
 * Everything currently held, each with its own price chart and a sense of when it should sell.
 * <p>
 * The unrealised figure is shown net of the sale tax that would be paid on the way out. A gross
 * figure would flatter every position by exactly the amount the plugin exists to account for, which
 * on thin margins is the whole difference between a profitable flip and a break-even one.
 */
class PositionsPanel extends JPanel
{
	private static final String TIMESTEP = "5m";

	private final PositionBook positions;
	private final MarketDataService marketData;
	private final ItemManager itemManager;
	private final Explainer explainer;
	private final TaxCalculator taxCalculator;
	/**
	 * What the sell engine concluded about each holding, computed on the engine thread and read here.
	 * A supplier rather than the engine itself, so painting a card cannot start a market pass.
	 */
	private final java.util.function.Supplier<java.util.Map<Integer,
		com.flippingfriend.model.PositionStatus>> statuses;

	PositionsPanel(PositionBook positions, MarketDataService marketData, ItemManager itemManager,
		Explainer explainer, TaxCalculator taxCalculator,
		java.util.function.Supplier<java.util.Map<Integer, com.flippingfriend.model.PositionStatus>> statuses)
	{
		this.statuses = statuses;
		this.positions = positions;
		this.marketData = marketData;
		this.itemManager = itemManager;
		this.explainer = explainer;
		this.taxCalculator = taxCalculator;

		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		setBackground(UiUtils.BACKGROUND);
		setAlignmentX(Component.LEFT_ALIGNMENT);
	}

	/** What was last drawn, so an unchanged refresh can be skipped rather than blinked through. */
	private String lastSignature;

	/**
	 * Closes a holding the player says they no longer have.
	 * <p>
	 * The counterpart to no longer deleting positions for being invisible: without a way to say "this
	 * is gone", anything sold outside the plugin or dropped would sit here for ever.
	 */
	private java.util.function.IntConsumer onClose = itemId -> { };

	void setOnClose(java.util.function.IntConsumer onClose)
	{
		this.onClose = onClose == null ? itemId -> { } : onClose;
	}

	void refresh()
	{
		List<Position> held = new ArrayList<>(positions.all());
		held.removeIf(position -> position.getQuantity() <= 0);
		held.sort(Comparator.comparingLong(Position::getOpenedAt).reversed());

		// Rebuild only when there is something different to draw.
		String signature = signatureOf(held);
		if (signature.equals(lastSignature))
		{
			return;
		}
		lastSignature = signature;

		if (held.isEmpty())
		{
			removeAll();
			JPanel empty = UiUtils.card();
			empty.setLayout(new BorderLayout());
			empty.add(UiUtils.mutedText("Nothing held right now. Anything the plugin buys for you will "
				+ "appear here with a price chart."), BorderLayout.CENTER);
			UiUtils.constrainWidth(empty);
			add(empty);
			revalidate();
			repaint();
			return;
		}

		// Ensure we have exactly the right number of rows
		int currentComponents = getComponentCount();
		// Some of these might be the empty state card, or just not enough PositionRow components
		boolean componentsMatch = true;
		if (currentComponents != held.size() * 2 - 1) { // includes gaps
			componentsMatch = false;
		} else {
			for (int i = 0; i < held.size(); i++) {
				Component comp = getComponent(i * 2);
				if (!(comp instanceof PositionRow)) {
					componentsMatch = false;
					break;
				}
			}
		}

		if (!componentsMatch) {
			removeAll();
			for (int i = 0; i < held.size(); i++)
			{
				PositionRow row = new PositionRow(held.get(i).getItemId());
				add(row);
				if (i < held.size() - 1)
				{
					add(UiUtils.gap(UiUtils.SPACE_S));
				}
			}
		}

		java.util.Map<Integer, com.flippingfriend.model.PositionStatus> currentStatuses = statuses.get();
		long now = Instant.now().getEpochSecond();
		
		for (int i = 0; i < held.size(); i++)
		{
			Position position = held.get(i);
			PositionRow row = (PositionRow) getComponent(i * 2);
			
			LatestPrice price = marketData.getSnapshot().latest(position.getItemId());
			int marketSell = price == null || price.getHigh() == null ? 0 : price.getHigh();
			com.flippingfriend.model.PositionStatus status = currentStatuses.get(position.getItemId());
			List<Candle> series = marketData.getSeries(position.getItemId(), TIMESTEP);
			
			row.update(position, marketSell, status, series, now);
		}

		revalidate();
		repaint();
	}

	private String signatureOf(List<Position> held)
	{
		java.util.Map<Integer, com.flippingfriend.model.PositionStatus> current = statuses.get();
		long now = Instant.now().getEpochSecond();
		StringBuilder signature = new StringBuilder();
		for (Position position : held)
		{
			LatestPrice price = marketData.getSnapshot().latest(position.getItemId());
			int marketSell = price == null || price.getHigh() == null ? 0 : price.getHigh();
			com.flippingfriend.model.PositionStatus status = current.get(position.getItemId());
			List<Candle> series = marketData.getSeries(position.getItemId(), TIMESTEP);

			signature.append(position.getItemId()).append(':')
				.append(position.getItemName()).append(':')
				.append(position.getQuantity()).append(':')
				.append(position.isCostKnown()).append(':')
				.append(position.getAverageCost()).append(':')
				.append(position.getTargetSellPrice()).append(':')
				.append(position.getStopPrice()).append(':')
				.append(position.getPredictedSellMinutes()).append(':')
				.append(marketSell).append(':')
				.append(heldFor(position, now)).append(':')
				.append(expectedLeft(position, now)).append(':')
				.append(series == null ? 0 : series.size()).append(':')
				.append(series == null || series.isEmpty()
					? 0 : series.get(series.size() - 1).getTimestamp()).append(':');

			if (status == null)
			{
				signature.append("-");
			}
			else
			{
				signature.append(status.summary()).append('/')
					.append(status.isBlockedBySlots()).append('/')
					.append(status.isSelling()).append('/')
					.append(status.getListedFilled()).append('/')
					.append(status.getListedTotal()).append('/')
					.append(status.getListedPrice());
			}
			signature.append('|');
		}
		return signature.toString();
	}

	private String heldFor(Position position, long now)
	{
		return explainer.formatDuration(position.minutesHeld(now));
	}

	private String expectedLeft(Position position, long now)
	{
		if (position.getPredictedSellMinutes() <= 0)
		{
			return "";
		}
		double remaining = position.getPredictedSellMinutes() - position.minutesHeld(now);
		return remaining > 0 ? explainer.formatDuration(remaining) : "longer than expected";
	}

	static final class Basis
	{
		final int price;
		final String caption;

		private Basis(int price, String caption)
		{
			this.price = price;
			this.caption = caption;
		}

		static Basis of(Position position, com.flippingfriend.model.PositionStatus listing,
			int marketSell)
		{
			if (listing != null && listing.isSelling() && listing.getListedPrice() > 0)
			{
				return new Basis(listing.getListedPrice(), "if it sells");
			}
			if (position.getTargetSellPrice() > 0)
			{
				return new Basis(position.getTargetSellPrice(), "at target");
			}
			return new Basis(marketSell, "at market");
		}
	}
	
	private static class LabelledRow extends JPanel
	{
		private final javax.swing.JTextArea valueArea;

		LabelledRow(String label)
		{
			setLayout(new BorderLayout(UiUtils.SPACE_S, 0));
			setOpaque(false);
			setAlignmentX(Component.LEFT_ALIGNMENT);

			JLabel name = UiUtils.small(label);
			name.setPreferredSize(new Dimension(66, name.getPreferredSize().height));
			name.setVerticalAlignment(SwingConstants.TOP);
			add(name, BorderLayout.WEST);
			
			valueArea = UiUtils.wrappedText("", UiUtils.MUTED, FontManager.getRunescapeSmallFont(), UiUtils.CARD_TEXT_WIDTH - 66 - UiUtils.SPACE_S);
			add(valueArea, BorderLayout.CENTER);
		}

		void update(String value)
		{
			valueArea.setText(value);
		}
	}
	
	private class PositionRow extends JPanel
	{
		private final int itemId;
		
		private final JLabel iconLabel = new JLabel();
		private final JLabel nameLabel = UiUtils.label("", UiUtils.TEXT, FontManager.getRunescapeFont());
		private final JLabel detailLabel = UiUtils.small("");
		private final JLabel valueLabel = UiUtils.label("", UiUtils.PROFIT, FontManager.getRunescapeBoldFont());
		private final JLabel captionLabel = UiUtils.label("", UiUtils.MUTED, FontManager.getRunescapeSmallFont());
		private final JPanel valueBlock = new JPanel();
		
		private final PriceGraphPanel graph = new PriceGraphPanel();
		
		private final javax.swing.JTextArea statusNote = UiUtils.wrappedText("", UiUtils.MUTED, FontManager.getRunescapeSmallFont(), UiUtils.CARD_TEXT_WIDTH);
		private final javax.swing.JTextField quantityEditField = new javax.swing.JTextField();
		
		private final LabelledRow heldForRow = new LabelledRow("Held for");
		private final LabelledRow paidRow = new LabelledRow("Paid");
		private final LabelledRow breakEvenRow = new LabelledRow("Break even");
		private final LabelledRow marketNowRow = new LabelledRow("Market now");
		private final LabelledRow listedRow = new LabelledRow("Listed");
		private final LabelledRow sellingAtRow = new LabelledRow("Selling at");
		private final LabelledRow cutLossAtRow = new LabelledRow("Cut loss at");
		private final LabelledRow expectedRow = new LabelledRow("Expected");
		
		PositionRow(int itemId)
		{
			this.itemId = itemId;
			
			setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
			setBackground(UiUtils.CARD);
			setBorder(UiUtils.cardBorder());
			setAlignmentX(Component.LEFT_ALIGNMENT);

			// Header
			JPanel header = new JPanel(new BorderLayout(UiUtils.SPACE_S, 0));
			header.setOpaque(false);
			header.setAlignmentX(Component.LEFT_ALIGNMENT);

			iconLabel.setPreferredSize(new Dimension(32, 32));
			iconLabel.setHorizontalAlignment(SwingConstants.CENTER);
			iconLabel.setVerticalAlignment(SwingConstants.TOP);
			header.add(iconLabel, BorderLayout.WEST);

			JPanel text = new JPanel();
			text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
			text.setOpaque(false);

			nameLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
			text.add(nameLabel);

			detailLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
			text.add(detailLabel);

			header.add(text, BorderLayout.CENTER);

			valueBlock.setLayout(new BoxLayout(valueBlock, BoxLayout.Y_AXIS));
			valueBlock.setOpaque(false);
			
			valueLabel.setAlignmentX(Component.RIGHT_ALIGNMENT);
			valueBlock.add(valueLabel);
			
			captionLabel.setAlignmentX(Component.RIGHT_ALIGNMENT);
			valueBlock.add(captionLabel);
			
			header.add(valueBlock, BorderLayout.EAST);
			UiUtils.constrainWidth(header);
			add(header);
			add(UiUtils.gap(UiUtils.SPACE_S));

			// Graph
			graph.setAlignmentX(Component.LEFT_ALIGNMENT);
			add(graph);
			add(UiUtils.gap(UiUtils.SPACE_S));

			// Details Content
			JPanel detailsContent = new JPanel();
			detailsContent.setLayout(new BoxLayout(detailsContent, BoxLayout.Y_AXIS));
			detailsContent.setOpaque(false);
			detailsContent.setAlignmentX(Component.LEFT_ALIGNMENT);
			
			statusNote.setAlignmentX(Component.LEFT_ALIGNMENT);
			detailsContent.add(statusNote);
			
			// We add gaps in code dynamically depending on visibility, but for simplicity we can just
			// let BoxLayout handle it or add fixed small rigid areas. But wait, if they are invisible, 
			// the gaps still take up space. 
			// A better approach is to wrap each row and its gap in a mini panel, or just override setVisible to also hide the gap.
			// For brevity, we'll just add them all. The gap before them needs to be hidden too if not visible.
			
			// Actually, BoxLayout ignores invisible components. If we just add them, it works, 
			// but what about the gap *between* them?
			// Since we have multiple conditional rows, the easiest way to handle gaps in Swing without 
			// extra classes is just to have the LabelledRow handle its own top margin, or we can use an EmptyBorder on the row itself!
			
			// Let's modify LabelledRow to have a top border of 2 pixels.
			heldForRow.setBorder(BorderFactory.createEmptyBorder(2, 0, 0, 0));
			paidRow.setBorder(BorderFactory.createEmptyBorder(2, 0, 0, 0));
			breakEvenRow.setBorder(BorderFactory.createEmptyBorder(2, 0, 0, 0));
			marketNowRow.setBorder(BorderFactory.createEmptyBorder(2, 0, 0, 0));
			listedRow.setBorder(BorderFactory.createEmptyBorder(2, 0, 0, 0));
			sellingAtRow.setBorder(BorderFactory.createEmptyBorder(2, 0, 0, 0));
			cutLossAtRow.setBorder(BorderFactory.createEmptyBorder(2, 0, 0, 0));
			expectedRow.setBorder(BorderFactory.createEmptyBorder(2, 0, 0, 0));
			
			detailsContent.add(heldForRow);
			detailsContent.add(paidRow);
			detailsContent.add(breakEvenRow);
			detailsContent.add(marketNowRow);
			detailsContent.add(listedRow);
			detailsContent.add(sellingAtRow);
			detailsContent.add(cutLossAtRow);
			detailsContent.add(expectedRow);
			
			UiUtils.constrainWidth(detailsContent);
			
			// Dismiss and Edit Quantity
			JPanel dismissRow = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 0, 0));
			dismissRow.setOpaque(false);
			dismissRow.setAlignmentX(Component.LEFT_ALIGNMENT);

			javax.swing.JButton dismiss = new javax.swing.JButton("Not holding");
			dismiss.setFont(FontManager.getRunescapeSmallFont());
			dismiss.setForeground(UiUtils.MUTED);
			dismiss.setBackground(UiUtils.CARD_HOVER);
			dismiss.setFocusPainted(false);
			dismiss.setBorder(BorderFactory.createEmptyBorder(3, 8, 3, 8));
			dismiss.setToolTipText("Forget this holding. Use it when you have sold or lost the item "
				+ "outside the plugin -- it does not sell anything.");
			dismiss.addActionListener(e -> onClose.accept(itemId));
			dismissRow.add(dismiss);

			dismissRow.add(javax.swing.Box.createRigidArea(new Dimension(8, 0)));

			quantityEditField.setPreferredSize(new Dimension(45, 22));
			quantityEditField.setFont(FontManager.getRunescapeSmallFont());
			quantityEditField.setBackground(UiUtils.CARD_HOVER);
			quantityEditField.setForeground(UiUtils.TEXT);
			quantityEditField.setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createLineBorder(UiUtils.MUTED),
				BorderFactory.createEmptyBorder(2, 4, 2, 4)));
			quantityEditField.addActionListener(e -> {
				try {
					int newQty = Integer.parseInt(quantityEditField.getText().trim());
					positions.setQuantity(itemId, newQty);
				} catch (NumberFormatException ignored) {}
			});
			dismissRow.add(quantityEditField);

			dismissRow.add(javax.swing.Box.createRigidArea(new Dimension(4, 0)));

			javax.swing.JButton updateQtyBtn = new javax.swing.JButton("Update");
			updateQtyBtn.setFont(FontManager.getRunescapeSmallFont());
			updateQtyBtn.setForeground(UiUtils.TEXT);
			updateQtyBtn.setBackground(UiUtils.CARD_HOVER);
			updateQtyBtn.setFocusPainted(false);
			updateQtyBtn.setBorder(BorderFactory.createEmptyBorder(3, 8, 3, 8));
			updateQtyBtn.addActionListener(e -> {
				try {
					int newQty = Integer.parseInt(quantityEditField.getText().trim());
					positions.setQuantity(itemId, newQty);
				} catch (NumberFormatException ignored) {}
			});
			dismissRow.add(updateQtyBtn);

			UiUtils.constrainWidth(dismissRow);
			detailsContent.add(UiUtils.gap(UiUtils.SPACE_S));
			detailsContent.add(dismissRow);
			
			CollapsibleSection detailsSection = new CollapsibleSection("Trade Details", detailsContent, true, UiUtils.CARD);
			add(detailsSection);

			UiUtils.constrainWidth(this);
		}

		void update(Position position, int marketSell, com.flippingfriend.model.PositionStatus status, List<Candle> series, long now)
		{
			AsyncBufferedImage image = itemManager.getImage(position.getItemId(), position.getQuantity(), true);
			if (image != null)
			{
				image.addTo(iconLabel);
			}
			
			nameLabel.setText(position.getItemName());
			detailLabel.setText(explainer.formatNumber(position.getQuantity()) + " held"
				+ (position.isCostKnown() ? "" : "  ·  already owned"));
				
			if (!quantityEditField.hasFocus())
			{
				quantityEditField.setText(String.valueOf(position.getQuantity()));
			}
				
			Basis valuation = Basis.of(position, status, marketSell);
			int basis = valuation.price;
			if (position.isCostKnown() && basis > 0)
			{
				long unrealised = taxCalculator.netProfit(position.getItemId(), position.getAverageCost(),
					basis, position.getQuantity());
				valueLabel.setText(explainer.formatGp(unrealised));
				valueLabel.setForeground(UiUtils.profitColor(unrealised));
				captionLabel.setText(valuation.caption);
				valueBlock.setVisible(true);
			}
			else
			{
				valueBlock.setVisible(false);
			}
			
			graph.setData(series, position.getAverageCost(), position.getTargetSellPrice(), position.getStopPrice());
			
			if (status != null && !status.summary().isEmpty())
			{
				statusNote.setText(status.summary());
				statusNote.setForeground(status.isBlockedBySlots() ? UiUtils.WARNING : UiUtils.MUTED);
				statusNote.setVisible(true);
			}
			else
			{
				statusNote.setVisible(false);
			}
			
			long minutes = position.minutesHeld(now);
			heldForRow.update(explainer.formatDuration(minutes));
			
			if (position.isCostKnown())
			{
				paidRow.update(explainer.formatNumber(position.getAverageCost()) + " gp");
				paidRow.setVisible(true);

				int breakEven = taxCalculator.breakEvenSellPrice(position.getItemId(), position.getAverageCost());
				if (breakEven > 0)
				{
					breakEvenRow.update(explainer.formatNumber(breakEven) + " gp");
					breakEvenRow.setVisible(true);
				}
				else
				{
					breakEvenRow.setVisible(false);
				}
			}
			else
			{
				paidRow.setVisible(false);
				breakEvenRow.setVisible(false);
			}

			if (marketSell > 0)
			{
				marketNowRow.update(explainer.formatNumber(marketSell) + " gp");
				marketNowRow.setVisible(true);
			}
			else
			{
				marketNowRow.setVisible(false);
			}

			if (status != null && status.isSelling() && status.getListedTotal() > 0)
			{
				listedRow.update(explainer.formatNumber(status.getListedFilled()) + " of "
						+ explainer.formatNumber(status.getListedTotal()) + " sold"
						+ (status.getListedPrice() > 0
							? "  ·  " + explainer.formatNumber(status.getListedPrice()) + " gp" : ""));
				listedRow.setVisible(true);
			}
			else
			{
				listedRow.setVisible(false);
			}

			if (position.getTargetSellPrice() > 0)
			{
				int target = position.getTargetSellPrice();
				String distance = marketSell <= 0
					? ""
					: marketSell >= target
						? "  ·  reached"
						: "  ·  " + explainer.formatNumber(target - marketSell) + " gp to go";
				sellingAtRow.update(explainer.formatNumber(target) + " gp" + distance);
				sellingAtRow.setVisible(true);
			}
			else
			{
				sellingAtRow.setVisible(false);
			}

			if (position.getStopPrice() > 0)
			{
				cutLossAtRow.update(explainer.formatNumber(position.getStopPrice()) + " gp");
				cutLossAtRow.setVisible(true);
			}
			else
			{
				cutLossAtRow.setVisible(false);
			}

			if (position.getPredictedSellMinutes() > 0)
			{
				double remaining = position.getPredictedSellMinutes() - minutes;
				String text = remaining > 0
					? "~" + explainer.formatDuration(remaining) + " left"
					: "longer than expected";
				expectedRow.update(text);
				expectedRow.setVisible(true);
			}
			else
			{
				expectedRow.setVisible(false);
			}
		}
	}
}
