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
		//
		// This tore the panel down and rebuilt every card several times a minute whether or not
		// anything had changed, which is what made the sidebar blink -- and it did the work on the
		// Swing thread, so the cost was paid where it is most visible. The signature covers everything
		// the cards render, so a change still redraws immediately and a quiet refresh costs nothing.
		String signature = signatureOf(held);
		if (signature.equals(lastSignature))
		{
			return;
		}
		lastSignature = signature;

		removeAll();
		// Prevent tooltips from sticking on screen if the user is hovering over a component that gets removed
		javax.swing.ToolTipManager.sharedInstance().setEnabled(false);
		javax.swing.ToolTipManager.sharedInstance().setEnabled(true);

		if (held.isEmpty())
		{
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

		for (int i = 0; i < held.size(); i++)
		{
			add(buildRow(held.get(i)));
			if (i < held.size() - 1)
			{
				add(UiUtils.gap(UiUtils.SPACE_S));
			}
		}

		revalidate();
		repaint();
	}

	/**
	 * Everything the cards actually draw, reduced to a string.
	 * <p>
	 * Every value on the card is here, including the chart's series and the sell engine's status line,
	 * so a change of any kind still redraws on the very next pass. Nothing that is not drawn is here,
	 * so a pass where nothing visible moved costs a string compare instead of a rebuild.
	 */
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
				// The two clocks on the card, as they are actually written. Signing the rendered text
				// rather than the raw minute matters: "Held for" is only drawn to the minute below an
				// hour and a half and to a tenth of an hour above it, so a long-held position redraws
				// every six minutes instead of every one.
				.append(heldFor(position, now)).append(':')
				.append(expectedLeft(position, now)).append(':')
				// The chart. Its length and newest candle move together whenever it is refreshed.
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

	private JPanel buildRow(Position position)
	{
		JPanel row = new JPanel();
		row.setLayout(new BoxLayout(row, BoxLayout.Y_AXIS));
		row.setBackground(UiUtils.CARD);
		row.setBorder(UiUtils.cardBorder());
		row.setAlignmentX(Component.LEFT_ALIGNMENT);

		LatestPrice price = marketData.getSnapshot().latest(position.getItemId());
		int marketSell = price == null || price.getHigh() == null ? 0 : price.getHigh();

		row.add(buildHeader(position, marketSell));
		row.add(UiUtils.gap(UiUtils.SPACE_S));

		PriceGraphPanel graph = new PriceGraphPanel();
		graph.setAlignmentX(Component.LEFT_ALIGNMENT);
		graph.setData(marketData.getSeries(position.getItemId(), TIMESTEP), position.getAverageCost(),
			position.getTargetSellPrice(), position.getStopPrice());
		row.add(graph);

		row.add(UiUtils.gap(UiUtils.SPACE_S));
		row.add(buildFooter(position, marketSell));
		row.add(UiUtils.gap(UiUtils.SPACE_S));
		row.add(buildDismiss(position));

		UiUtils.constrainWidth(row);
		return row;
	}

	/**
	 * The "I do not have this" control.
	 * <p>
	 * Positions are no longer deleted because the plugin cannot see the item -- doing that on a bank
	 * snapshot that stops updating when the bank closes is what silently destroyed real holdings and
	 * the cost basis behind them. The price of that safety is that something disposed of outside the
	 * plugin has to be dismissed by hand, so here is the hand.
	 */
	private JPanel buildDismiss(Position position)
	{
		JPanel row = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 0, 0));
		row.setOpaque(false);
		row.setAlignmentX(Component.LEFT_ALIGNMENT);

		javax.swing.JButton dismiss = new javax.swing.JButton("Not holding this");
		dismiss.setFont(FontManager.getRunescapeSmallFont());
		dismiss.setForeground(UiUtils.MUTED);
		dismiss.setBackground(UiUtils.CARD_HOVER);
		dismiss.setFocusPainted(false);
		dismiss.setBorder(BorderFactory.createEmptyBorder(3, 8, 3, 8));
		dismiss.setToolTipText("Forget this holding. Use it when you have sold or lost the item "
			+ "outside the plugin -- it does not sell anything.");
		dismiss.addActionListener(e -> onClose.accept(position.getItemId()));
		row.add(dismiss);

		UiUtils.constrainWidth(row);
		return row;
	}

	private JPanel buildHeader(Position position, int marketSell)
	{
		JPanel header = new JPanel(new BorderLayout(UiUtils.SPACE_S, 0));
		header.setOpaque(false);
		header.setAlignmentX(Component.LEFT_ALIGNMENT);

		JLabel icon = new JLabel();
		icon.setPreferredSize(new Dimension(32, 32));
		icon.setHorizontalAlignment(SwingConstants.CENTER);
		icon.setVerticalAlignment(SwingConstants.TOP);
		AsyncBufferedImage image = itemManager.getImage(position.getItemId(), position.getQuantity(), true);
		if (image != null)
		{
			image.addTo(icon);
		}
		header.add(icon, BorderLayout.WEST);

		JPanel text = new JPanel();
		text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
		text.setOpaque(false);

		JLabel name = UiUtils.label(position.getItemName(), UiUtils.TEXT, FontManager.getRunescapeFont());
		name.setAlignmentX(Component.LEFT_ALIGNMENT);
		text.add(name);

		// Just the count. The price paid has its own row in the footer now, and carrying it here too
		// made the line long enough to be cut off mid-word once the value on the right took its space.
		JLabel detail = UiUtils.small(explainer.formatNumber(position.getQuantity()) + " held"
			+ (position.isCostKnown() ? "" : "  ·  already owned"));
		detail.setAlignmentX(Component.LEFT_ALIGNMENT);
		text.add(detail);

		header.add(text, BorderLayout.CENTER);

		// What the trade is worth if it works out, not what bailing out this second would give.
		//
		// This was computed from the instant-sell price, so a holding on its way to a target it had
		// not reached yet always read as a loss -- which is true of the moment and not of the trade.
		// The price it is aiming for is the honest basis for "what is this position worth", and the
		// Market now and Selling at rows sit directly beneath it for the other half of the picture.
		//
		// Once there is an offer on the market, though, the target stops being the answer. The plan's
		// target is a forecast, and it holds only while the offer is still priced the way the plan
		// intended -- which it frequently is not, because prices get adjusted, by the player and by
		// the reprice advice alike. A figure captioned "at target" beside an offer listed somewhere
		// else is a number about a trade nobody is making.
		//
		// So: while it is being bought the target is all there is and the caption says so. The moment
		// it is listed, the listed price is the fact and the forecast is not needed.
		Basis valuation = Basis.of(position, statuses.get().get(position.getItemId()), marketSell);
		int basis = valuation.price;
		String caption = valuation.caption;
		if (position.isCostKnown() && basis > 0)
		{
			long unrealised = taxCalculator.netProfit(position.getItemId(), position.getAverageCost(),
				basis, position.getQuantity());
			JPanel valueBlock = new JPanel();
			valueBlock.setLayout(new BoxLayout(valueBlock, BoxLayout.Y_AXIS));
			valueBlock.setOpaque(false);
			JLabel value = UiUtils.label(explainer.formatGp(unrealised), UiUtils.profitColor(unrealised),
				FontManager.getRunescapeBoldFont());
			value.setAlignmentX(Component.RIGHT_ALIGNMENT);
			valueBlock.add(value);
			// Captioned, because a figure at a price the market has not reached would otherwise read
			// as money already made.
			JLabel captionLabel = UiUtils.label(caption, UiUtils.MUTED,
				FontManager.getRunescapeSmallFont());
			captionLabel.setAlignmentX(Component.RIGHT_ALIGNMENT);
			valueBlock.add(captionLabel);
			header.add(valueBlock, BorderLayout.EAST);
		}

		UiUtils.constrainWidth(header);
		return header;
	}

	private JPanel buildFooter(Position position, int marketSell)
	{
		JPanel footer = new JPanel();
		footer.setLayout(new BoxLayout(footer, BoxLayout.Y_AXIS));
		footer.setOpaque(false);
		footer.setAlignmentX(Component.LEFT_ALIGNMENT);

		// What the plugin is actually doing with this holding, in the sell engine's own words. Without
		// it a position can sit for hours with no way to tell deliberate patience from having been
		// forgotten -- which is exactly how it looked from the outside.
		com.flippingfriend.model.PositionStatus status =
			statuses.get().get(position.getItemId());
		if (status != null && !status.summary().isEmpty())
		{
			javax.swing.JTextArea note = UiUtils.wrappedText(status.summary(),
				status.isBlockedBySlots() ? UiUtils.WARNING : UiUtils.MUTED,
				FontManager.getRunescapeSmallFont(), UiUtils.CARD_TEXT_WIDTH);
			note.setAlignmentX(Component.LEFT_ALIGNMENT);
			footer.add(note);
			footer.add(UiUtils.gap(UiUtils.SPACE_S));
		}

		long minutes = position.minutesHeld(Instant.now().getEpochSecond());

		footer.add(labelledRow("Held for", explainer.formatDuration(minutes)));

		// The money, so the decision can be checked rather than taken on trust.
		if (position.isCostKnown())
		{
			footer.add(UiUtils.gap(2));
			footer.add(labelledRow("Paid", explainer.formatNumber(position.getAverageCost()) + " gp"));

			int breakEven = taxCalculator.breakEvenSellPrice(position.getItemId(),
				position.getAverageCost());
			if (breakEven > 0)
			{
				footer.add(UiUtils.gap(2));
				footer.add(labelledRow("Break even", explainer.formatNumber(breakEven) + " gp"));
			}
		}

		if (marketSell > 0)
		{
			footer.add(UiUtils.gap(2));
			footer.add(labelledRow("Market now", explainer.formatNumber(marketSell) + " gp"));
		}

		// What the open offer is doing, when there is one. This is the single most useful line on a
		// holding that is already selling, and the card previously said nothing about it at all.
		if (status != null && status.isSelling() && status.getListedTotal() > 0)
		{
			footer.add(UiUtils.gap(2));
			footer.add(labelledRow("Listed",
				explainer.formatNumber(status.getListedFilled()) + " of "
					+ explainer.formatNumber(status.getListedTotal()) + " sold"
					+ (status.getListedPrice() > 0
						? "  ·  " + explainer.formatNumber(status.getListedPrice()) + " gp" : "")));
		}

		if (position.getTargetSellPrice() > 0)
		{
			// How far off the target is says more than the target alone: "1,120 gp" is a number,
			// "reached" is an answer.
			int target = position.getTargetSellPrice();
			String distance = marketSell <= 0
				? ""
				: marketSell >= target
					? "  ·  reached"
					: "  ·  " + explainer.formatNumber(target - marketSell) + " gp to go";
			footer.add(UiUtils.gap(2));
			footer.add(labelledRow("Selling at", explainer.formatNumber(target) + " gp" + distance));
		}

		if (position.getStopPrice() > 0)
		{
			footer.add(UiUtils.gap(2));
			footer.add(labelledRow("Cut loss at", explainer.formatNumber(position.getStopPrice()) + " gp"));
		}

		// The estimate made when the position was opened. Showing it lets the player see whether a
		// trade is running late rather than having to guess.
		if (position.getPredictedSellMinutes() > 0)
		{
			double remaining = position.getPredictedSellMinutes() - minutes;
			String text = remaining > 0
				? "~" + explainer.formatDuration(remaining) + " left"
				: "longer than expected";
			footer.add(UiUtils.gap(2));
			footer.add(labelledRow("Expected", text));
		}

		UiUtils.constrainWidth(footer);
		return footer;
	}

	private JPanel labelledRow(String label, String value)
	{
		JPanel row = new JPanel(new BorderLayout(UiUtils.SPACE_S, 0));
		row.setOpaque(false);
		row.setAlignmentX(Component.LEFT_ALIGNMENT);

		JLabel name = UiUtils.small(label);
		name.setPreferredSize(new Dimension(66, name.getPreferredSize().height));
		row.add(name, BorderLayout.WEST);
		row.add(UiUtils.small(value), BorderLayout.CENTER);

		UiUtils.constrainWidth(row);
		return row;
	}

	/**
	 * Which price a holding should be valued at, and what to call it.
	 *
	 * <p>Pulled out of the panel so it can be tested without a screen. The rule is small and it was
	 * wrong in a way nothing would have caught: everything held was valued at the plan's target, and
	 * captioned "at target", including positions that were already listed somewhere else entirely.
	 */
	static final class Basis
	{
		final int price;
		final String caption;

		private Basis(int price, String caption)
		{
			this.price = price;
			this.caption = caption;
		}

		/**
		 * @param listing what the sell engine says is happening to this holding, or null
		 * @param marketSell what buyers are paying right now
		 */
		static Basis of(Position position, com.flippingfriend.model.PositionStatus listing,
			int marketSell)
		{
			// An offer on the market is a fact; the plan's target is a forecast, and it holds only
			// while the offer is still priced the way the plan intended. Prices get adjusted -- by the
			// player, and by the plugin's own reprice advice -- so a figure captioned "at target"
			// beside an offer listed elsewhere is a number about a trade nobody is making.
			if (listing != null && listing.isSelling() && listing.getListedPrice() > 0)
			{
				return new Basis(listing.getListedPrice(), "if it sells");
			}
			// Still being bought, so the target is all there is, and the caption says as much.
			if (position.getTargetSellPrice() > 0)
			{
				return new Basis(position.getTargetSellPrice(), "at target");
			}
			return new Basis(marketSell, "at market");
		}
	}
}