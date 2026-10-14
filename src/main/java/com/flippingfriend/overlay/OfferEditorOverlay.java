package com.flippingfriend.overlay;

import com.flippingfriend.FlippingFriendConfig;
import com.flippingfriend.model.Explainer;
import com.flippingfriend.model.Suggestion;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import javax.inject.Inject;
import net.runelite.api.widgets.Widget;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.TextComponent;

/**
 * Prints the target quantity and price beside the offer editor, level with the field each belongs to.
 * <p>
 * The instruction card already carries these numbers. When it is visible, omit the duplicate
 * hints so the two overlays cannot print over each other. With the card disabled, the hints
 * stay beside the corresponding input controls.
 * <p>
 * <b>Beside, not on.</b> These were centred on the control and drawn three pixels above it, which is
 * where the Grand Exchange puts its own captions and the value typed so far — so "Type 5,111,111"
 * landed on "5,087,503" and the pair read as gibberish. There is no free space inside the editor;
 * every part of it is a control or a label. {@link HintPlacement} puts the hint just outside the
 * editor at the row's own height, which is adjacent enough to read as belonging to that field and
 * cannot cover anything.
 */
public class OfferEditorOverlay extends Overlay
{
	private static final Color LABEL = new Color(255, 235, 160);

	private final net.runelite.api.Client client;
	private final StepGuide stepGuide;
	private final GeWidgetResolver resolver;
	private final FlippingFriendConfig config;
	private final Explainer explainer;

	@Inject
	public OfferEditorOverlay(net.runelite.api.Client client, StepGuide stepGuide,
		GeWidgetResolver resolver, FlippingFriendConfig config, Explainer explainer)
	{
		this.client = client;
		this.stepGuide = stepGuide;
		this.resolver = resolver;
		this.config = config;
		this.explainer = explainer;

		setLayer(OverlayLayer.ABOVE_WIDGETS);
		setPosition(OverlayPosition.DYNAMIC);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (!resolver.isSetupOpen())
		{
			return null;
		}

		Suggestion suggestion = stepGuide.getSuggestion();
		if (suggestion == null || !suggestion.isActionable() || suggestion.getPrice() <= 0)
		{
			return null;
		}
        GeEditorValues values = GeEditorValues.read(client, resolver);
        if (values == null || !values.usable() || !GeEditorValues.isOffer(suggestion)) return null;

		Widget panel = resolver.getSetupPanel();
		if (panel == null)
		{
			return null;
		}

		graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		graphics.setFont(FontManager.getRunescapeSmallFont());

        if (config.protectTradeConfirmation() && !values.matches(suggestion)) drawLockedConfirm(graphics);
        if (!config.showOfferEditorHints() || !values.contextMatches(suggestion)) return null;
        if (config.showOverlay() && config.showInstructionCard()) return null;

		// Only the field still to be filled. Both were drawn on every frame the editor was open,
		// including after both numbers were typed and the guide had moved on to Confirm, so the
		// player was still being told to type something they had already typed.
		if (!stepGuide.isQuantityDone() && suggestion.getQuantity() > 0)
		{
			drawBeside(graphics, panel, resolver.getQuantityControl(),
				"Type " + explainer.formatNumber(suggestion.getQuantity()));
		}
		if (!stepGuide.isPriceDone())
		{
			drawBeside(graphics, panel, resolver.getPriceControl(),
				"Type " + explainer.formatNumber(suggestion.getPrice()));
		}

		return null;
	}

    private void drawLockedConfirm(Graphics2D graphics) {
        Widget confirm = resolver.getConfirmButton();
        Rectangle bounds = confirm == null ? null : confirm.getBounds();
        if (bounds == null || bounds.width <= 0 || bounds.height <= 0) return;
        graphics.setColor(new Color(25, 25, 25, 225));
        graphics.fillRoundRect(bounds.x, bounds.y, bounds.width, bounds.height, 6, 6);
        graphics.setColor(new Color(230, 163, 87));
        graphics.drawRoundRect(bounds.x, bounds.y, bounds.width, bounds.height, 6, 6);
        String label = "Locked - right-click";
        FontMetrics metrics = graphics.getFontMetrics();
        if (metrics.stringWidth(label) > bounds.width - 6) label = "Locked";
        TextComponent text = new TextComponent();
        text.setText(label); text.setColor(LABEL); text.setOutline(true);
        text.setPosition(new java.awt.Point(bounds.x + Math.max(3, (bounds.width - metrics.stringWidth(label)) / 2),
            bounds.y + (bounds.height + metrics.getAscent() - metrics.getDescent()) / 2));
        text.render(graphics);
    }

	private void drawBeside(Graphics2D graphics, Widget panel, Widget widget, String text)
	{
		if (widget == null || widget.isHidden())
		{
			return;
		}

		Rectangle editor = panel.getBounds();
		Rectangle bounds = widget.getBounds();
		if (editor == null || bounds == null || bounds.width <= 0)
		{
			return;
		}

		FontMetrics metrics = graphics.getFontMetrics();
		java.awt.Point at = HintPlacement.beside(editor, bounds, metrics.stringWidth(text),
			metrics.getAscent(), metrics.getDescent(),
			client.getCanvasWidth(), client.getCanvasHeight());
		if (at == null)
		{
			// Nowhere it fits without covering something. The sidebar card has both numbers with
			// copy buttons, so saying nothing here costs nothing.
			return;
		}

		// Outlined rather than shadowed. A one-pixel offset copy gives no separation against the game
		// world behind it, which is exactly the contrast problem that made the old placement unreadable.
		TextComponent hint = new TextComponent();
		hint.setText(text);
		hint.setColor(LABEL);
		hint.setOutline(true);
		hint.setPosition(at);
		hint.render(graphics);
	}
}
