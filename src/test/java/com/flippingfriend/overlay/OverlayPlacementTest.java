package com.flippingfriend.overlay;

import java.awt.Point;
import java.awt.Rectangle;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Where the overlay is allowed to draw.
 * <p>
 * Both faults here were coordinate arithmetic, and both were invisible from the code: the numbers
 * were computed correctly against the wrong rectangle. The hint was centred on its control and put
 * three pixels above it, which is where the Grand Exchange draws its own captions and the value you
 * have typed — so "Type 5,111,111" and "5,087,503" occupied the same pixels. The instruction card
 * tried each side of the highlighted control, found neither fitted, and fell back to the control's
 * own left edge, landing on the price row and the +1/+10/+100 buttons.
 * <p>
 * These are tested against rectangles rather than a running client, because that is the only way to
 * assert the thing that actually matters: nothing the plugin draws may intersect the interface it is
 * asking you to use.
 */
public class OverlayPlacementTest
{
	/** Fixed mode, which is where both faults appeared: there is no room beside a centred editor. */
	private static final int CANVAS_WIDTH = 765;
	private static final int CANVAS_HEIGHT = 503;

	/** The Set-up-offer window, roughly as it sits in the reported screenshot. */
	private static final Rectangle EDITOR = new Rectangle(170, 140, 480, 300);
	/** The small "Enter price" control inside it. */
	private static final Rectangle PRICE = new Rectangle(600, 300, 30, 20);
	/** The "Enter quantity" control, a row higher and further left. */
	private static final Rectangle QUANTITY = new Rectangle(355, 300, 30, 20);

	private static final int ASCENT = 12;
	private static final int DESCENT = 3;

	private static Rectangle glyphBox(Point at, int width)
	{
		return new Rectangle(at.x, at.y - ASCENT, width, ASCENT + DESCENT);
	}

	@Test
	public void aHintNeverLandsOnTheInterface()
	{
		// The reported case: a wide price hint against a narrow control inside a busy panel.
		int width = 90;
		Point at = HintPlacement.beside(EDITOR, PRICE, width, ASCENT, DESCENT,
			1200, CANVAS_HEIGHT);

		assertNotNull("there is room to the right of the editor on a wide canvas", at);
		assertFalse("and the hint must not touch the editor",
			glyphBox(at, width).intersects(EDITOR));
	}

	@Test
	public void aHintLinesUpWithTheFieldItBelongsTo()
	{
		// Vertical alignment is the only thing connecting the hint to its field once it is outside
		// the panel, so it has to be right. Anything else and the price hint reads as the quantity.
		Point at = HintPlacement.beside(EDITOR, PRICE, 90, ASCENT, DESCENT, 1200, CANVAS_HEIGHT);

		int middle = PRICE.y + PRICE.height / 2;
		Rectangle box = glyphBox(at, 90);
		assertTrue("the hint should straddle the control's midline",
			box.y <= middle && box.y + box.height >= middle);
	}

	@Test
	public void twoHintsCannotCollide()
	{
		// They are drawn on the same frame from the same side. Different rows is what keeps them
		// apart, and if that ever stopped being true they would overprint each other exactly the way
		// the old placement overprinted the game.
		Rectangle upper = new Rectangle(355, 240, 30, 20);
		Point quantity = HintPlacement.beside(EDITOR, upper, 60, ASCENT, DESCENT, 1200, CANVAS_HEIGHT);
		Point price = HintPlacement.beside(EDITOR, PRICE, 90, ASCENT, DESCENT, 1200, CANVAS_HEIGHT);

		assertFalse(glyphBox(quantity, 60).intersects(glyphBox(price, 90)));
	}

	@Test
	public void aHintFlipsToTheOtherSideRatherThanOverlap()
	{
		// The editor pushed hard against the right edge. Left is the only place left.
		Rectangle rightEdge = new Rectangle(400, 140, 360, 300);
		Point at = HintPlacement.beside(rightEdge, PRICE, 90, ASCENT, DESCENT,
			CANVAS_WIDTH, CANVAS_HEIGHT);

		assertNotNull(at);
		assertTrue("it must be left of the editor", at.x + 90 <= rightEdge.x);
	}

	@Test
	public void aHintWithNowhereToGoIsNotDrawn()
	{
		// An editor filling the canvas. Drawing nothing is the right answer -- the sidebar card
		// carries both numbers with copy buttons, so a missing hint costs nothing and a hint over the
		// interface costs the player the number they were reading.
		Rectangle everything = new Rectangle(0, 0, CANVAS_WIDTH, CANVAS_HEIGHT);

		assertNull(HintPlacement.beside(everything, PRICE, 90, ASCENT, DESCENT,
			CANVAS_WIDTH, CANVAS_HEIGHT));
	}

	@Test
	public void theCardNeverLandsOnTheOfferEditor()
	{
		// The exact reported failure. On a fixed-mode canvas neither side of the editor has room for
		// a card, and the old rule answered that by placing it at the editor's own left edge.
		Rectangle card = CardPlacement.place(EDITOR, QUANTITY, 180, 90,
			CANVAS_WIDTH, CANVAS_HEIGHT);

		assertFalse("the card must not cover the window it is describing: " + card,
			card.intersects(EDITOR));
	}

	@Test
	public void theCardUsesTheSideWhenThereIsRoom()
	{
		Rectangle card = CardPlacement.place(EDITOR, QUANTITY, 180, 90, 1200, CANVAS_HEIGHT);

		assertTrue("a wide canvas should put it to the right", card.x >= EDITOR.x + EDITOR.width);
		assertFalse(card.intersects(EDITOR));
	}

	@Test
	public void theCardFallsBelowWhenNeitherSideFits()
	{
		// The branch the old javadoc promised and the old code never had -- it never read the
		// anchor's height, so there was no way for it to go under anything.
		Rectangle tall = new Rectangle(20, 20, 700, 200);
		Rectangle card = CardPlacement.place(tall, null, 180, 90, CANVAS_WIDTH, CANVAS_HEIGHT);

		assertTrue("it should sit under the interface", card.y >= tall.y + tall.height);
		assertFalse(card.intersects(tall));
	}

	@Test
	public void theCardStaysOnTheCanvasEvenWhenItCannotAvoidTheInterface()
	{
		// An interface with no free strip anywhere. Overlapping in the emptiest corner is bad;
		// running off the canvas and hiding the instruction entirely is worse.
		Rectangle everything = new Rectangle(0, 0, CANVAS_WIDTH, CANVAS_HEIGHT);
		Rectangle card = CardPlacement.place(everything, null, 180, 90, CANVAS_WIDTH, CANVAS_HEIGHT);

		assertTrue(card.x >= 0);
		assertTrue(card.y >= 0);
		assertTrue(card.x + card.width <= CANVAS_WIDTH);
		assertTrue(card.y + card.height <= CANVAS_HEIGHT);
	}
}
