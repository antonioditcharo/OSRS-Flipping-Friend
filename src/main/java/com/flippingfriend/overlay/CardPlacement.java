package com.flippingfriend.overlay;

import java.awt.Rectangle;

/**
 * Where the instruction card can sit without covering the interface it is describing.
 * <p>
 * The old rule tried right of the highlighted control, flipped left, and then — when neither fitted —
 * fell back to the control's own left edge, top-aligned with it. On a fixed-mode canvas with the offer
 * editor centred, neither side of a small control has room for a card, so that fallback fired every
 * time and the card landed squarely on the price row and the +1/+10/+100 buttons. Its own javadoc
 * promised a "below" branch; the code never had one, because the anchor's height was never read.
 * <p>
 * The fix is to keep out of the whole window rather than the one control inside it, and to have four
 * real sides to try. Placing it beside a control was always going to fail: a control is small and
 * surrounded by other controls, so "beside" is still "on top of something".
 * <p>
 * Separated from the overlay so the geometry can be tested against rectangles rather than a running
 * client, which is the only way to pin an arithmetic fault like this one.
 */
final class CardPlacement
{
	/** Space between the interface and the card. */
	private static final int GAP = 12;
	/** Keep-out from the canvas edge. */
	private static final int MARGIN = 4;

	private CardPlacement()
	{
	}

	/**
	 * @param keepOut what the card must not cover — the offer editor when it is open, otherwise the
	 *                highlighted widget. Null when there is nothing to avoid.
	 * @param align   the widget the card describes, used to line the card up with it vertically. Null
	 *                falls back to the keep-out's own top.
	 */
	static Rectangle place(Rectangle keepOut, Rectangle align, int width, int height,
		int canvasWidth, int canvasHeight)
	{
		if (keepOut == null)
		{
			return new Rectangle(Math.max(MARGIN, (canvasWidth - width) / 2), 60, width, height);
		}

		int top = align == null ? keepOut.y : align.y;

		// Right, then left, then below, then above. Sides first because a card level with the row it
		// describes is easiest to read against; below and above are the fallbacks that keep it off
		// the interface when the canvas is too narrow for either side.
		int right = keepOut.x + keepOut.width + GAP;
		if (right + width <= canvasWidth - MARGIN)
		{
			return new Rectangle(right, clampY(top, height, canvasHeight), width, height);
		}

		int left = keepOut.x - GAP - width;
		if (left >= MARGIN)
		{
			return new Rectangle(left, clampY(top, height, canvasHeight), width, height);
		}

		int below = keepOut.y + keepOut.height + GAP;
		if (below + height <= canvasHeight - MARGIN)
		{
			return new Rectangle(clampX(keepOut.x, width, canvasWidth), below, width, height);
		}

		int above = keepOut.y - GAP - height;
		if (above >= MARGIN)
		{
			return new Rectangle(clampX(keepOut.x, width, canvasWidth), above, width, height);
		}

		// The card is larger than every free strip around the interface. Something has to give, and
		// it is better to overlap in the corner with the most room than to run off the canvas, which
		// would hide the instruction entirely.
		int x = keepOut.x >= canvasWidth - keepOut.x - keepOut.width
			? MARGIN
			: canvasWidth - width - MARGIN;
		return new Rectangle(Math.max(MARGIN, x), clampY(top, height, canvasHeight), width, height);
	}

	private static int clampY(int y, int height, int canvasHeight)
	{
		return Math.max(MARGIN, Math.min(y, canvasHeight - height - MARGIN));
	}

	private static int clampX(int x, int width, int canvasWidth)
	{
		return Math.max(MARGIN, Math.min(x, canvasWidth - width - MARGIN));
	}
}
