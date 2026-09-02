package com.flippingfriend.overlay;

import java.awt.Point;
import java.awt.Rectangle;

/**
 * Where a "type this number" hint can go without landing on the game's own text.
 * <p>
 * It used to be centred on the control and drawn three pixels above it, which is precisely where the
 * Grand Exchange draws its captions and the value you have typed so far. A price hint is far wider
 * than the small button it was centred on, so it bled over both neighbours as well: "Type 5,111,111"
 * and "5,087,503" occupied the same pixels and the result read as neither.
 * <p>
 * There is no free space <em>inside</em> the offer editor — every part of it is a control or a label,
 * which is why every in-panel position is wrong. So the hint goes immediately outside the editor,
 * level with the row it belongs to. Vertical alignment is what ties it to its field; nothing has to
 * be drawn over anything to make the connection, and the two hints can never collide because they sit
 * on different rows.
 * <p>
 * Separated from the overlay so the geometry can be tested against rectangles instead of a running
 * game client. Every one of these faults is a coordinate arithmetic fault, and coordinate arithmetic
 * is exactly what a test can pin.
 */
final class HintPlacement
{
	/** Space between the editor's edge and the hint. Enough to read as separate, not as a caption. */
	private static final int GAP = 6;
	/** Keep-out from the canvas edge, so a hint is never half off-screen. */
	private static final int MARGIN = 4;

	private HintPlacement()
	{
	}

	/**
	 * The baseline origin for a hint, or null when there is nowhere it fits.
	 * <p>
	 * Right of the editor first, then left, then nothing. Returning null is a real answer: the
	 * sidebar card carries both numbers with copy buttons, so a hidden hint costs nothing, while a
	 * hint drawn over the interface costs the player the number they were reading.
	 *
	 * @param panel    the whole offer editor
	 * @param control  the field the hint refers to, used only for its vertical position
	 * @param width    measured width of the text
	 * @param ascent   font ascent, for centring on the control's midline
	 * @param descent  font descent
	 */
	static Point beside(Rectangle panel, Rectangle control, int width, int ascent, int descent,
		int canvasWidth, int canvasHeight)
	{
		if (panel == null || control == null || width <= 0)
		{
			return null;
		}

		int x;
		if (panel.x + panel.width + GAP + width <= canvasWidth - MARGIN)
		{
			x = panel.x + panel.width + GAP;
		}
		else if (panel.x - GAP - width >= MARGIN)
		{
			x = panel.x - GAP - width;
		}
		else
		{
			return null;
		}

		// Centred on the control's midline: half the glyph box above it, half below.
		int middle = control.y + control.height / 2;
		int baseline = middle + (ascent - descent) / 2;
		baseline = Math.max(MARGIN + ascent, Math.min(baseline, canvasHeight - MARGIN - descent));

		return new Point(x, baseline);
	}
}
