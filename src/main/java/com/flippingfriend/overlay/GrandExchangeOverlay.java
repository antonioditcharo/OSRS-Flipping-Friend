package com.flippingfriend.overlay;

import com.flippingfriend.FlippingFriendConfig;
import com.flippingfriend.model.Explainer;
import com.flippingfriend.model.Suggestion;
import com.flippingfriend.model.SuggestionType;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.GrandExchangeOffer;
import net.runelite.api.GrandExchangeOfferState;
import net.runelite.api.widgets.Widget;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import com.flippingfriend.AccountMode;
import com.flippingfriend.session.OfferTracker;
import com.flippingfriend.session.TrackedOffer;
import java.time.Instant;
import net.runelite.api.WorldType;

/**
 * Draws the walkthrough over the Grand Exchange: a highlight around whatever should be clicked
 * next, and a card beside it with the exact number to type.
 * <p>
 * Strictly a renderer. It reads game state and paints; it never clicks, types, or alters a widget.
 * That line is what keeps the plugin on the right side of the game's rules, so all of the state is
 * derived and none of it is written back.
 */
public class GrandExchangeOverlay extends Overlay
{
	private static final int CARD_PADDING = 8;
	private static final int CORNER = 8;
	private static final Color CARD_BACKGROUND = new Color(24, 26, 30, 235);
	private static final Color CARD_TEXT = new Color(232, 234, 238);
	private static final Color CARD_MUTED = new Color(158, 164, 174);
	private static final Color PROFIT_GREEN = new Color(88, 214, 141);
	private static final Color WARNING_AMBER = new Color(240, 178, 78);
	/** One full pulse every 1.4 seconds: noticeable without being agitating. */
	private static final double PULSE_PERIOD_MS = 1400.0;

	private final Client client;
	private final GeWidgetResolver resolver;
	private final StepGuide stepGuide;
	private final FlippingFriendConfig config;
	private final Explainer explainer;
	private final OfferTracker offerTracker;

	@Inject
	public GrandExchangeOverlay(Client client, GeWidgetResolver resolver, StepGuide stepGuide,
		FlippingFriendConfig config, Explainer explainer, OfferTracker offerTracker)
	{
		this.client = client;
		this.resolver = resolver;
		this.stepGuide = stepGuide;
		this.config = config;
		this.explainer = explainer;
		this.offerTracker = offerTracker;

		setLayer(OverlayLayer.ABOVE_WIDGETS);
		setPosition(OverlayPosition.DYNAMIC);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (!config.showOverlay())
		{
			return null;
		}

		graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

		drawTimers(graphics);

		StepGuide.GuideState state = stepGuide.resolve(totalSlots(), occupiedSlots());
		if (state.getStep() == GuideStep.NONE)
		{
			return null;
		}

		Rectangle anchor = null;
		for (Widget target : state.getTargets())
		{
			Rectangle bounds = boundsOf(target);
			if (bounds == null)
			{
				continue;
			}
			drawHighlight(graphics, bounds);
			if (anchor == null)
			{
				anchor = bounds;
			}
		}

		if (config.showInstructionCard())
		{
			drawCard(graphics, state, anchor);
		}

		return null;
	}

	private void drawTimers(Graphics2D graphics)
	{
		long now = Instant.now().getEpochSecond();
		for (int i = 0; i < 8; i++)
		{
			TrackedOffer offer = offerTracker.getOffer(i);
			if (offer != null && ("BUYING".equals(offer.getState()) || "SELLING".equals(offer.getState())))
			{
				Widget slotWidget = resolver.getSlot(i);
				if (slotWidget != null && !slotWidget.isHidden())
				{
					Rectangle bounds = slotWidget.getBounds();
					if (bounds != null)
					{
						graphics.setFont(FontManager.getRunescapeSmallFont());
						FontMetrics metrics = graphics.getFontMetrics();

						long minutesOpen = offer.minutesOpen(now);
						String openTimeStr = formatDuration(minutesOpen);
						int openWidth = metrics.stringWidth(openTimeStr);
						int openX = bounds.x + bounds.width - openWidth - 4;
						int openY = bounds.y + 16;

						graphics.setColor(Color.BLACK);
						graphics.drawString(openTimeStr, openX + 1, openY + 1);
						graphics.setColor(CARD_MUTED);
						graphics.drawString(openTimeStr, openX, openY);

						long minutesSinceChange = offer.minutesSinceChange(now);
						String changeTimeStr = formatDuration(minutesSinceChange);
						int changeWidth = metrics.stringWidth(changeTimeStr);
						int changeX = bounds.x + (bounds.width - changeWidth) / 2;
						int changeY = bounds.y + bounds.height - 20;

						graphics.setColor(Color.BLACK);
						graphics.drawString(changeTimeStr, changeX + 1, changeY + 1);
						graphics.setColor(CARD_MUTED);
						graphics.drawString(changeTimeStr, changeX, changeY);
					}
				}
			}
		}
	}

	private String formatDuration(long minutes)
	{
		if (minutes < 60)
		{
			return minutes + "m";
		}
		long hours = minutes / 60;
		long mins = minutes % 60;
		return hours + "h " + mins + "m";
	}

	private void drawHighlight(Graphics2D graphics, Rectangle bounds)
	{
		Color base = config.highlightColor();
		int alpha = config.pulseHighlight() ? pulseAlpha() : 200;

		Stroke original = graphics.getStroke();
		graphics.setStroke(new BasicStroke(2f));

		graphics.setColor(new Color(base.getRed(), base.getGreen(), base.getBlue(), alpha / 6));
		graphics.fillRoundRect(bounds.x, bounds.y, bounds.width, bounds.height, CORNER, CORNER);

		graphics.setColor(new Color(base.getRed(), base.getGreen(), base.getBlue(), alpha));
		graphics.drawRoundRect(bounds.x, bounds.y, bounds.width, bounds.height, CORNER, CORNER);

		graphics.setStroke(original);
	}

	private static int pulseAlpha()
	{
		double phase = (System.currentTimeMillis() % (long) PULSE_PERIOD_MS) / PULSE_PERIOD_MS;
		double wave = (Math.sin(phase * 2 * Math.PI) + 1) / 2.0;
		return (int) (120 + wave * 135);
	}

	private void drawCard(Graphics2D graphics, StepGuide.GuideState state, Rectangle anchor)
	{
		Suggestion suggestion = state.getSuggestion();
		List<Line> lines = buildLines(state, suggestion);
		if (lines.isEmpty())
		{
			return;
		}

		int width = 0;
		int height = CARD_PADDING * 2;
		for (Line line : lines)
		{
			graphics.setFont(line.bold ? FontManager.getRunescapeBoldFont() : FontManager.getRunescapeFont());
			FontMetrics metrics = graphics.getFontMetrics();
			width = Math.max(width, metrics.stringWidth(line.text));
			height += metrics.getHeight() + line.spacingAbove;
		}
		width += CARD_PADDING * 2;

		// Keep out of the whole offer editor when it is open, not just the control being pointed at.
		// A control is small and hemmed in by other controls, so "beside the control" was still on top
		// of something -- which is how the card came to sit over the price row and the quantity
		// buttons. The highlight ring still marks the individual control, so nothing is lost by
		// moving the card further out.
		Rectangle editor = boundsOf(resolver.getSetupPanel());
		Rectangle card = CardPlacement.place(editor == null ? anchor : editor, anchor, width, height,
			client.getCanvasWidth(), client.getCanvasHeight());

		graphics.setColor(CARD_BACKGROUND);
		graphics.fillRoundRect(card.x, card.y, card.width, card.height, CORNER, CORNER);

		Color border = config.highlightColor();
		graphics.setColor(new Color(border.getRed(), border.getGreen(), border.getBlue(), 220));
		graphics.setStroke(new BasicStroke(1.5f));
		graphics.drawRoundRect(card.x, card.y, card.width, card.height, CORNER, CORNER);

		int y = card.y + CARD_PADDING;
		for (Line line : lines)
		{
			graphics.setFont(line.bold ? FontManager.getRunescapeBoldFont() : FontManager.getRunescapeFont());
			FontMetrics metrics = graphics.getFontMetrics();
			y += metrics.getAscent() + line.spacingAbove;
			graphics.setColor(line.color);
			graphics.drawString(line.text, card.x + CARD_PADDING, y);
			y += metrics.getDescent();
		}
	}



	private List<Line> buildLines(StepGuide.GuideState state, Suggestion suggestion)
	{
		List<Line> lines = new ArrayList<>();
		GuideStep step = state.getStep();

		lines.add(new Line(step.getInstruction(), CARD_TEXT, true, 0));

		switch (step)
		{
			case SET_QUANTITY:
				lines.add(big("Quantity: " + explainer.formatNumber(suggestion.getQuantity())));
				if (state.isShowBothNumbers())
				{
					lines.add(big("Price: " + explainer.formatNumber(suggestion.getPrice())));
					lines.add(muted("Set both, then press Confirm"));
				}
				break;

			case SET_PRICE:
				lines.add(big("Price: " + explainer.formatNumber(suggestion.getPrice()) + " gp"));
				break;

			case SEARCH_ITEM:
			case PICK_SEARCH_RESULT:
				lines.add(big(suggestion.getItemName()));
				break;

			case PICK_SLOT:
			case SELL_FROM_INVENTORY:
				lines.add(big(suggestion.getItemName()));
				lines.add(muted(explainer.formatNumber(suggestion.getQuantity()) + " at "
					+ explainer.formatNumber(suggestion.getPrice()) + " gp"));
				break;

			case CONFIRM:
				lines.add(big(explainer.formatNumber(suggestion.getQuantity()) + " × "
					+ suggestion.getItemName()));
				lines.add(muted("at " + explainer.formatNumber(suggestion.getPrice()) + " gp each"));
				break;

			case CANCEL_OFFER:
				lines.add(big(suggestion.getItemName()));
				lines.add(muted("Then place it again at "
					+ explainer.formatNumber(suggestion.getPrice()) + " gp"));
				break;

			case ABANDON_OFFER:
				lines.add(big(suggestion.getItemName()));
				lines.add(muted("Nothing to place afterwards — the coins come back"));
				break;

			case COLLECT:
				lines.add(big(suggestion.getItemName()));
				break;

			case OPEN_GE:
				lines.add(muted("Talk to a Grand Exchange clerk to open it"));
				break;

			default:
				break;
		}

		if (suggestion.getType() == SuggestionType.BUY && suggestion.getExpectedProfit() > 0)
		{
			lines.add(new Line("Expected profit " + explainer.formatGp(suggestion.getExpectedProfit()),
				PROFIT_GREEN, false, 4));
		}
		else if (suggestion.isLossCut())
		{
			lines.add(new Line("Cutting a loss", WARNING_AMBER, false, 4));
		}

		return lines;
	}

	private static Line big(String text)
	{
		return new Line(text, CARD_TEXT, true, 4);
	}

	private static Line muted(String text)
	{
		return new Line(text, CARD_MUTED, false, 2);
	}

	private Rectangle boundsOf(Widget widget)
	{
		if (widget == null || widget.isHidden())
		{
			return null;
		}
		Rectangle bounds = widget.getBounds();
		if (bounds == null || bounds.width <= 0 || bounds.height <= 0)
		{
			return null;
		}
		// A couple of pixels of breathing room so the outline sits around the control, not on it.
		return new Rectangle(bounds.x - 2, bounds.y - 2, bounds.width + 4, bounds.height + 4);
	}

	/**
	 * How many slots this account actually has.
	 * <p>
	 * The client's array is always eight long regardless of membership, so highlighting it wholesale
	 * pointed a free-to-play player at five slots that do not exist. The account's real count is
	 * decided the same way everywhere else: the world says what is available and the player's own
	 * setting can override it, because someone on a members world without membership -- or planning
	 * around a subscription about to lapse -- knows better than the world type does.
	 */
	private int totalSlots()
	{
		GrandExchangeOffer[] offers = client.getGrandExchangeOffers();
		int available = offers == null ? 8 : offers.length;
		boolean membersWorld = client.getWorldType().contains(WorldType.MEMBERS);
		return Math.min(available, AccountMode.slotsFor(config.accountMode().resolveMembers(membersWorld)));
	}

	private boolean[] occupiedSlots()
	{
		GrandExchangeOffer[] offers = client.getGrandExchangeOffers();
		if (offers == null)
		{
			return new boolean[8];
		}

		boolean[] occupied = new boolean[offers.length];
		for (int i = 0; i < offers.length; i++)
		{
			GrandExchangeOffer offer = offers[i];
			occupied[i] = offer != null && offer.getState() != GrandExchangeOfferState.EMPTY;
		}
		return occupied;
	}

	private static final class Line
	{
		private final String text;
		private final Color color;
		private final boolean bold;
		private final int spacingAbove;

		Line(String text, Color color, boolean bold, int spacingAbove)
		{
			this.text = text;
			this.color = color;
			this.bold = bold;
			this.spacingAbove = spacingAbove;
		}
	}
}
