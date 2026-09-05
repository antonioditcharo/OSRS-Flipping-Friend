package com.flippingfriend.ui;

import com.flippingfriend.model.Explainer;
import com.flippingfriend.model.Suggestion;
import com.flippingfriend.model.SuggestionType;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.FontManager;
import net.runelite.client.util.AsyncBufferedImage;

/**
 * The main card: one instruction, the numbers to type, and how long it should take.
 * <p>
 * Laid out on the assumption the reader has never flipped. The action and the two numbers they must
 * type are the most prominent things; the reasoning sits below and can be expanded. The copy buttons
 * are there because transcribing a seven-digit price by eye is how beginners lose money to a
 * misplaced digit.
 * <p>
 * Nothing here pins a pixel height. Every row sizes to its content, so a long item name or a bigger
 * game font pushes the card taller instead of having its text sliced off.
 */
class SuggestionCard extends JPanel
{
	private final ItemManager itemManager;
	private final Explainer explainer;

	private final JLabel iconLabel = new JLabel();
	private final JLabel actionLabel = new JLabel();
	/**
	 * The instruction behind the headline, where there is more to say than the headline fits.
	 * <p>
	 * The justification behind a recommendation is noise — the player wants the trade, not the
	 * reasoning — so buy and sell suggestions carry none. What is left is the part that tells someone
	 * what to actually do: why nothing is worth doing right now, or which offer to cancel and what to
	 * re-place it at. A bare headline with none of that reads as broken.
	 */
	private final JTextArea idleReason;

	/**
	 * Says what is waiting behind the current step. Only appears when advice arrived mid-entry and
	 * was deliberately held back, which is the one case where the panel would otherwise be silently
	 * out of step with what the player is being walked through.
	 */
	private final JTextArea queuedLabel;
	private final JTextArea headlineLabel;
	private final JPanel numbersPanel = new JPanel();
	private final JPanel windowPanel = new JPanel();
	private final JLabel profitLabel = new JLabel();
	private final ConfidenceBar confidenceBar = new ConfidenceBar();
	private final JPanel actionsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, UiUtils.SPACE_S, 0));
	private final JButton skipButton = new JButton("Skip");
	private final JButton blockButton = new JButton("Never trade this");
	/** What was last drawn, so an unchanged update can be skipped rather than blinked through. */
	private String lastSignature;

	private Suggestion suggestion = Suggestion.idle();
	/**
	 * The rejection handlers are handed the suggestion they are rejecting, not just its id.
	 * <p>
	 * They used to receive an id and look the rest up again — from the built-in engine, which by then
	 * held a different trade entirely, because the companion's buy replaces it before anything is
	 * drawn. So "Never trade this" blocked the wrong item, or, when the other suggestion had no item
	 * name, silently did nothing. Passing the object the card is displaying removes the second lookup,
	 * and with it the chance of the two disagreeing.
	 */
	private java.util.function.Consumer<Suggestion> onSkip = ignored -> { };
	private java.util.function.Consumer<Suggestion> onBlock = ignored -> { };
	private Runnable onCardClicked = () -> { };

	SuggestionCard(ItemManager itemManager, Explainer explainer)
	{
		this.itemManager = itemManager;
		this.explainer = explainer;

		this.idleReason = UiUtils.mutedText("");
		this.queuedLabel = UiUtils.wrappedText("", UiUtils.WARNING, FontManager.getRunescapeSmallFont(),
			UiUtils.CARD_TEXT_WIDTH);
		this.queuedLabel.setVisible(false);
		this.headlineLabel = UiUtils.wrappedText("", UiUtils.TEXT, FontManager.getRunescapeBoldFont(),
			UiUtils.CARD_TEXT_WIDTH - 44);

		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		setBackground(UiUtils.CARD);
		setBorder(UiUtils.cardBorder());
		setAlignmentX(Component.LEFT_ALIGNMENT);

		addMouseListener(new java.awt.event.MouseAdapter()
		{
			@Override
			public void mouseClicked(java.awt.event.MouseEvent e)
			{
				if (SwingUtilities.isLeftMouseButton(e))
				{
					onCardClicked.run();
				}
			}
			
			@Override
			public void mouseEntered(java.awt.event.MouseEvent e)
			{
				setBackground(UiUtils.CARD_HOVER);
			}

			@Override
			public void mouseExited(java.awt.event.MouseEvent e)
			{
				setBackground(UiUtils.CARD);
			}
		});

		add(buildHeader());

		numbersPanel.setLayout(new BoxLayout(numbersPanel, BoxLayout.Y_AXIS));
		numbersPanel.setOpaque(false);
		numbersPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
		add(UiUtils.gap(UiUtils.SPACE_M));
		add(numbersPanel);

		profitLabel.setFont(FontManager.getRunescapeBoldFont());
		profitLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
		add(UiUtils.gap(UiUtils.SPACE_M));
		add(profitLabel);

		windowPanel.setLayout(new BoxLayout(windowPanel, BoxLayout.Y_AXIS));
		windowPanel.setOpaque(false);
		windowPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
		add(UiUtils.gap(UiUtils.SPACE_M));
		add(windowPanel);

		confidenceBar.setAlignmentX(Component.LEFT_ALIGNMENT);
		add(UiUtils.gap(UiUtils.SPACE_M));
		add(confidenceBar);

		actionsPanel.setOpaque(false);
		actionsPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
		styleSmallButton(skipButton);
		styleSmallButton(blockButton);
		skipButton.addActionListener(e -> onSkip.accept(suggestion));
		blockButton.addActionListener(e -> onBlock.accept(suggestion));
		actionsPanel.add(skipButton);
		actionsPanel.add(blockButton);
		add(UiUtils.gap(UiUtils.SPACE_M));
		add(actionsPanel);
	}

	void setOnSkip(java.util.function.Consumer<Suggestion> onSkip)
	{
		this.onSkip = onSkip;
	}

	void setOnBlock(java.util.function.Consumer<Suggestion> onBlock)
	{
		this.onBlock = onBlock;
	}

	void setOnCardClicked(Runnable onCardClicked)
	{
		this.onCardClicked = onCardClicked;
	}

	private JPanel buildHeader()
	{
		JPanel header = new JPanel(new BorderLayout(UiUtils.SPACE_M, 0));
		header.setOpaque(false);
		header.setAlignmentX(Component.LEFT_ALIGNMENT);

		iconLabel.setPreferredSize(new Dimension(36, 36));
		iconLabel.setHorizontalAlignment(SwingConstants.CENTER);
		iconLabel.setVerticalAlignment(SwingConstants.TOP);
		header.add(iconLabel, BorderLayout.WEST);

		JPanel text = new JPanel();
		text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
		text.setOpaque(false);

		actionLabel.setFont(FontManager.getRunescapeSmallFont());
		actionLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
		headlineLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

		queuedLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

		text.add(actionLabel);
		text.add(UiUtils.gap(2));
		text.add(headlineLabel);
		text.add(idleReason);
		text.add(queuedLabel);
		header.add(text, BorderLayout.CENTER);

		return header;
	}

	void update(Suggestion next)
	{
		update(next, null);
	}

	/**
	 * @param queued advice that arrived while an offer was half-entered and is waiting its turn, or
	 *               null. Shown rather than applied, so the player knows something needs doing
	 *               without the walkthrough moving out from under the offer they are typing.
	 */
	void update(Suggestion next, Suggestion queued)
	{
		this.suggestion = next == null ? Suggestion.idle() : next;

		// Two of the sections below tear themselves down and rebuild on every call, and the card is
		// updated on every poll whether or not the advice moved -- which is what made the top of the
		// sidebar blink. The signature covers everything drawn from here down, so identical advice
		// leaves the card alone and any change still lands on the very next poll.
		String signature = signatureOf(this.suggestion, queued);
		if (signature.equals(lastSignature))
		{
			return;
		}
		lastSignature = signature;

		updateQueued(queued);

		actionLabel.setText(suggestion.getType().getLabel().toUpperCase());
		actionLabel.setForeground(colorFor(suggestion.getType()));
		UiUtils.setWrappedText(headlineLabel, suggestion.getHeadline());
		// Shown whenever there is one. Buy and sell suggestions no longer carry a detail at all --
		// that was the "why this trade" justification, and it is gone -- so what reaches here is the
		// instructional kind: why there is nothing to do, or what to cancel and re-place it at. Gating
		// this on "not actionable" hid exactly the half worth reading, because a reprice and a cancel are
		// actionable and their detail is the instruction itself.
		String why = suggestion.getDetail();
		boolean hasDetail = why != null && !why.isEmpty();
		idleReason.setVisible(hasDetail);
		UiUtils.setWrappedText(idleReason, hasDetail ? why : "");

		updateIcon();
		updateNumbers();
		updateProfit();
		updateTradeWindow();

		confidenceBar.setValue(suggestion.getConfidence());
		confidenceBar.setVisible(suggestion.getType() == SuggestionType.BUY);

		// Shown for sales as well as purchases.
		//
		// These were on buys only, which left no way to say "not that one" about a sale -- and the
		// plugin offers to sell whatever it finds in your inventory, so the armour you took out to
		// wear gets listed alongside the stock you meant to flip. Skip drops it for this session;
		// Never trade this adds it to the blocklist and it stops being a holding at all.
		actionsPanel.setVisible(suggestion.getType() == SuggestionType.BUY
			|| suggestion.getType() == SuggestionType.SELL);

		revalidate();
		repaint();
	}

	/** Everything this card draws, reduced to a string. */
	private String signatureOf(Suggestion shown, Suggestion queued)
	{
		return new StringBuilder()
			.append(shown.getType()).append(':')
			.append(shown.getHeadline()).append(':')
			.append(shown.getDetail()).append(':')
			.append(shown.getItemId()).append(':')
			.append(shown.getPrice()).append(':')
			.append(shown.getQuantity()).append(':')
			.append(shown.getTargetSellPrice()).append(':')
			.append(shown.getExpectedProfit()).append(':')
			.append(shown.getConfidence()).append(':')
			.append(shown.isActionable()).append(':')
			.append(shown.getBuyFillMinutes()).append(':')
			.append(shown.getSellFillMinutes()).append(':')
			.append(shown.getExpectedMinutes()).append('|')
			.append(queued == null ? "-"
				: queued.isActionable() ? queued.getHeadline() : "-")
			.toString();
	}

	/** The banner for a deferred action. Hidden entirely when nothing is waiting. */
	private void updateQueued(Suggestion queued)
	{
		if (queued == null || !queued.isActionable())
		{
			queuedLabel.setVisible(false);
			return;
		}
		UiUtils.setWrappedText(queuedLabel, "Next, once you have finished here: "
			+ queued.getHeadline());
		queuedLabel.setVisible(true);
	}

	private void updateIcon()
	{
		if (suggestion.getItemId() <= 0)
		{
			iconLabel.setIcon(null);
			return;
		}
		AsyncBufferedImage image = itemManager.getImage(suggestion.getItemId());
		if (image != null)
		{
			image.addTo(iconLabel);
		}
	}

	private void updateNumbers()
	{
		numbersPanel.removeAll();
		javax.swing.ToolTipManager.sharedInstance().setEnabled(false);
		javax.swing.ToolTipManager.sharedInstance().setEnabled(true);

		if (!suggestion.isActionable() || suggestion.getPrice() <= 0)
		{
			return;
		}

		addNumberRow("Price", explainer.formatNumber(suggestion.getPrice()) + " gp",
			Integer.toString(suggestion.getPrice()));

		if (suggestion.getQuantity() > 0)
		{
			addNumberRow("Quantity", explainer.formatNumber(suggestion.getQuantity()),
				Integer.toString(suggestion.getQuantity()));
		}

		if ((suggestion.getType() == SuggestionType.BUY || suggestion.getType() == SuggestionType.MODIFY_BUY) && suggestion.getTargetSellPrice() > 0)
		{
			addNumberRow("Then sell at", explainer.formatNumber(suggestion.getTargetSellPrice()) + " gp",
				Integer.toString(suggestion.getTargetSellPrice()));
		}
	}

	/**
	 * A labelled number with a copy button, so nothing has to be transcribed by eye.
	 * <p>
	 * Two lines, because one does not fit. The sidebar gives a row about 197px; a fixed 78px label,
	 * a copy button and two gaps left roughly 69px for the value, and a price like 5,111,111 gp
	 * measures nearer 90px in the bold font -- so the one number you have to type correctly was
	 * rendered as "5,111,11...". Putting the label on its own line hands the value the best part of
	 * 150px, which holds for any price the game can produce.
	 */
	private void addNumberRow(String label, String display, String clipboardValue)
	{
		JPanel row = new JPanel();
		row.setLayout(new BoxLayout(row, BoxLayout.Y_AXIS));
		row.setOpaque(false);
		row.setAlignmentX(Component.LEFT_ALIGNMENT);

		JLabel name = UiUtils.small(label);
		name.setAlignmentX(Component.LEFT_ALIGNMENT);
		row.add(name);

		JPanel line = new JPanel(new BorderLayout(UiUtils.SPACE_S, 0));
		line.setOpaque(false);
		line.setAlignmentX(Component.LEFT_ALIGNMENT);

		JLabel value = UiUtils.label(display, UiUtils.TEXT, FontManager.getRunescapeBoldFont());
		line.add(value, BorderLayout.CENTER);

		JButton copy = new JButton("copy");
		styleSmallButton(copy);
		copy.setToolTipText("Copy " + display + " to the clipboard");
		copy.addActionListener(e -> Toolkit.getDefaultToolkit().getSystemClipboard()
			.setContents(new StringSelection(clipboardValue), null));
		line.add(copy, BorderLayout.EAST);

		UiUtils.constrainWidth(line);
		row.add(line);

		UiUtils.constrainWidth(row);
		numbersPanel.add(row);
		numbersPanel.add(UiUtils.gap(UiUtils.SPACE_S));
	}

	private void updateProfit()
	{
		if (suggestion.getExpectedProfit() != 0)
		{
			profitLabel.setText("Expected profit  " + explainer.formatGp(suggestion.getExpectedProfit()));
			profitLabel.setForeground(UiUtils.profitColor(suggestion.getExpectedProfit()));
			profitLabel.setVisible(true);
		}
		else
		{
			profitLabel.setVisible(false);
		}
	}

	/**
	 * How long this is expected to tie up a slot.
	 * <p>
	 * Worth its own block rather than a footnote: "how long until I get my money back" is the
	 * question people actually weigh before committing coins, and a trade that pays well but sits
	 * for three hours is a different proposition from one that turns over in ten minutes.
	 */
	private void updateTradeWindow()
	{
		windowPanel.removeAll();
		javax.swing.ToolTipManager.sharedInstance().setEnabled(false);
		javax.swing.ToolTipManager.sharedInstance().setEnabled(true);

		boolean buying = suggestion.getType() == SuggestionType.BUY;
		boolean selling = suggestion.getType() == SuggestionType.SELL;

		if (!buying && !selling)
		{
			windowPanel.setVisible(false);
			return;
		}

		windowPanel.setVisible(true);

		JLabel heading = UiUtils.small(buying ? "Estimated trade window" : "Estimated time to sell");
		heading.setAlignmentX(Component.LEFT_ALIGNMENT);
		windowPanel.add(heading);
		windowPanel.add(UiUtils.gap(UiUtils.SPACE_XS));

		if (buying)
		{
			double buyLeg = suggestion.getBuyFillMinutes();
			double sellLeg = suggestion.getSellFillMinutes();
			addWindowRow("Buy fills in", approx(buyLeg));
			addWindowRow("Then sells in", approx(sellLeg));
			// The two legs, not the expected slot occupancy. getExpectedMinutes() is a
			// probability-weighted figure that folds in the branches where the buy never fills and
			// where the position strands, so it is systematically shorter than the trip a player who
			// completes the flip actually experiences -- and it was labelled "Round trip" anyway.
			double roundTrip = buyLeg > 0 && sellLeg > 0 ? buyLeg + sellLeg
				: suggestion.getExpectedMinutes();
			addWindowRow("Round trip", approx(roundTrip), UiUtils.TEXT);
		}
		else
		{
			addWindowRow("Should sell in", approx(suggestion.getSellFillMinutes() > 0
				? suggestion.getSellFillMinutes()
				: suggestion.getExpectedMinutes()), UiUtils.TEXT);
		}
	}

	private void addWindowRow(String label, String value)
	{
		addWindowRow(label, value, UiUtils.MUTED);
	}

	private void addWindowRow(String label, String value, Color valueColor)
	{
		JPanel row = new JPanel(new BorderLayout(UiUtils.SPACE_S, 0));
		row.setOpaque(false);
		row.setAlignmentX(Component.LEFT_ALIGNMENT);

		JLabel name = UiUtils.small(label);
		name.setPreferredSize(new Dimension(78, name.getPreferredSize().height));
		row.add(name, BorderLayout.WEST);
		row.add(UiUtils.label(value, valueColor, FontManager.getRunescapeSmallFont()), BorderLayout.CENTER);

		UiUtils.constrainWidth(row);
		windowPanel.add(row);
		windowPanel.add(UiUtils.gap(2));
	}

	/** Always hedged, because these are model estimates and should not read as promises. */
	private String approx(double minutes)
	{
		String text = explainer.formatDuration(minutes);
		return text.startsWith("an unknown") ? "not known" : "~" + text;
	}

	private static Color colorFor(SuggestionType type)
	{
		switch (type)
		{
			case BUY:
				return UiUtils.BUY;
			case SELL:
				return UiUtils.PROFIT;
			case CANCEL:
			case MODIFY_BUY:
			case MODIFY_SELL:
			case COLLECT:
				return UiUtils.WARNING;
			default:
				return UiUtils.MUTED;
		}
	}

	private static void styleSmallButton(JButton button)
	{
		button.setFont(FontManager.getRunescapeSmallFont());
		button.setForeground(UiUtils.MUTED);
		button.setBackground(UiUtils.CARD_HOVER);
		button.setFocusPainted(false);
		button.setBorder(BorderFactory.createEmptyBorder(3, 8, 3, 8));
	}


	/** A bar rather than a percentage, because "how sure is it" reads better than "0.62". */
	private static final class ConfidenceBar extends JPanel
	{
		private static final int HEIGHT = 22;

		private double value;

		ConfidenceBar()
		{
			setOpaque(false);
			setPreferredSize(new Dimension(0, HEIGHT));
			setMaximumSize(new Dimension(Integer.MAX_VALUE, HEIGHT));
		}

		void setValue(double value)
		{
			this.value = Math.max(0, Math.min(1, value));
			repaint();
		}

		@Override
		protected void paintComponent(Graphics g)
		{
			super.paintComponent(g);
			Graphics2D graphics = (Graphics2D) g.create();
			try
			{
				graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
					RenderingHints.VALUE_ANTIALIAS_ON);

				graphics.setFont(FontManager.getRunescapeSmallFont());
				graphics.setColor(UiUtils.MUTED);
				graphics.drawString("Confidence", 0, 10);

				int barY = 14;
				int barHeight = 5;
				int width = getWidth();

				graphics.setColor(UiUtils.DIVIDER);
				graphics.fillRoundRect(0, barY, width, barHeight, 4, 4);

				graphics.setColor(value >= 0.7 ? UiUtils.PROFIT
					: value >= 0.45 ? UiUtils.WARNING : UiUtils.LOSS);
				graphics.fillRoundRect(0, barY, (int) (width * value), barHeight, 4, 4);
			}
			finally
			{
				graphics.dispose();
			}
		}
	}
}
