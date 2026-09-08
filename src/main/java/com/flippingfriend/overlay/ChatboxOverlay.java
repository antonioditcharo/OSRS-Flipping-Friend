package com.flippingfriend.overlay;

import com.flippingfriend.FlippingFriendConfig;
import com.flippingfriend.model.Explainer;
import com.flippingfriend.model.Suggestion;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.ComponentID;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

public class ChatboxOverlay extends Overlay
{
	private static final Color TEXT_COLOR = new Color(232, 234, 238);
	
	private final Client client;
	private final StepGuide stepGuide;
	private final FlippingFriendConfig config;
	private final Explainer explainer;

	@Inject
	public ChatboxOverlay(Client client, StepGuide stepGuide, FlippingFriendConfig config, Explainer explainer)
	{
		this.client = client;
		this.stepGuide = stepGuide;
		this.config = config;
		this.explainer = explainer;

		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_WIDGETS);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (!config.showInstructionCard())
		{
			return null;
		}

		Suggestion suggestion = stepGuide.getSuggestion();
		if (suggestion == null || !suggestion.isActionable())
		{
			return null;
		}
		
		Widget chatbox = client.getWidget(ComponentID.CHATBOX_CONTAINER);
		if (chatbox == null || chatbox.isHidden())
		{
			return null;
		}
		
		Rectangle bounds = chatbox.getBounds();
		if (bounds == null)
		{
			return null;
		}

		String text = explainer.formatNumber(suggestion.getQuantity()) + " / " + explainer.formatNumber(suggestion.getPrice()) + " gp";
		
		graphics.setFont(FontManager.getRunescapeBoldFont());
		int textWidth = graphics.getFontMetrics().stringWidth(text);
		int textHeight = graphics.getFontMetrics().getHeight();
		int ascent = graphics.getFontMetrics().getAscent();
		
		int padding = 8;
		int cardWidth = textWidth + padding * 2;
		int cardHeight = textHeight + padding * 2;
		
		int x = bounds.x + bounds.width - cardWidth - 8;
		int y = bounds.y + 8;
		
		int corner = 8;
		
		// Draw background
		graphics.setColor(new Color(24, 26, 30, 235));
		graphics.fillRoundRect(x, y, cardWidth, cardHeight, corner, corner);
		
		// Draw border
		Color border = config.highlightColor();
		graphics.setColor(new Color(border.getRed(), border.getGreen(), border.getBlue(), 220));
		graphics.setStroke(new java.awt.BasicStroke(1.5f));
		graphics.drawRoundRect(x, y, cardWidth, cardHeight, corner, corner);
		
		// Draw text
		graphics.setColor(TEXT_COLOR);
		graphics.drawString(text, x + padding, y + padding + ascent);

		return null;
	}
}
