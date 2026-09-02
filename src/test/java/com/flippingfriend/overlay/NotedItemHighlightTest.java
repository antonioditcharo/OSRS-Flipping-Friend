package com.flippingfriend.overlay;

import net.runelite.api.Client;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.game.ItemManager;
import org.junit.Test;
import org.mockito.Mockito;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

/**
 * The sell step highlights the item to click in the Grand Exchange side panel — and for noted stacks
 * it has been highlighting nothing at all.
 * <p>
 * {@code AccountMonitor} folds notes into their unnoted form, so a position in notes carries the
 * unnoted id while the widget in the panel carries the noted one. Compared raw they never match, the
 * resolver returns null, and no highlight is drawn. Nothing looks broken: the card still says what to
 * sell, so the missing highlight reads as a design choice rather than a fault. On herbs and logs —
 * which are held in notes almost by definition — that is most holdings.
 */
public class NotedItemHighlightTest
{
	private static final int GRIMY_IRIT = 207;
	/** What the side panel actually carries for a noted stack. */
	private static final int GRIMY_IRIT_NOTED = 208;
	private static final int YEW_LOGS = 1515;

	private final Client client = Mockito.mock(Client.class);
	private final ItemManager itemManager = Mockito.mock(ItemManager.class);
	private final GeWidgetResolver resolver = new GeWidgetResolver(client, itemManager);

	private Widget sidePanelHolding(int... widgetItemIds)
	{
		Widget side = Mockito.mock(Widget.class);
		Mockito.when(side.isHidden()).thenReturn(false);
		Mockito.when(client.getWidget(InterfaceID.GeOffersSide.ITEMS)).thenReturn(side);

		Widget[] children = new Widget[widgetItemIds.length];
		for (int i = 0; i < widgetItemIds.length; i++)
		{
			Widget child = Mockito.mock(Widget.class);
			Mockito.when(child.isHidden()).thenReturn(false);
			Mockito.when(child.getItemId()).thenReturn(widgetItemIds[i]);
			children[i] = child;
		}
		Mockito.when(side.getDynamicChildren()).thenReturn(children);
		return side;
	}

	private void canonicalise(int from, int to)
	{
		Mockito.when(itemManager.canonicalize(from)).thenReturn(to);
	}

	@Test
	public void aNotedStackIsFoundFromTheUnnotedPositionId()
	{
		Widget side = sidePanelHolding(GRIMY_IRIT_NOTED);
		canonicalise(GRIMY_IRIT_NOTED, GRIMY_IRIT);
		canonicalise(GRIMY_IRIT, GRIMY_IRIT);

		assertSame("the noted stack is what the player has to click",
			side.getDynamicChildren()[0], resolver.getSideItem(GRIMY_IRIT));
	}

	@Test
	public void anUnnotedItemStillMatchesItself()
	{
		Widget side = sidePanelHolding(GRIMY_IRIT);
		canonicalise(GRIMY_IRIT, GRIMY_IRIT);

		assertSame(side.getDynamicChildren()[0], resolver.getSideItem(GRIMY_IRIT));
	}

	@Test
	public void aDifferentItemIsNotHighlighted()
	{
		// The failure that matters more than a missing highlight: pointing at the wrong stack. If
		// canonicalising ever collapsed unrelated items together, this is what would catch it.
		sidePanelHolding(YEW_LOGS);
		canonicalise(YEW_LOGS, YEW_LOGS);
		canonicalise(GRIMY_IRIT, GRIMY_IRIT);

		assertNull("only the item being sold may be highlighted", resolver.getSideItem(GRIMY_IRIT));
	}

	@Test
	public void aClosedSidePanelHighlightsNothing()
	{
		Mockito.when(client.getWidget(InterfaceID.GeOffersSide.ITEMS)).thenReturn(null);

		assertNull(resolver.getSideItem(GRIMY_IRIT));
	}

	@Test
	public void anEmptyPanelEntryIsSteppedOverRatherThanCanonicalised()
	{
		// The side panel pads itself out with empty entries carrying -1. Asking the item manager to
		// canonicalise one is not a question it has an answer to, so the id is checked first -- the
		// stub throws here to make sure it is never asked.
		Widget side = sidePanelHolding(-1, GRIMY_IRIT_NOTED);
		Mockito.when(itemManager.canonicalize(-1))
			.thenThrow(new NullPointerException("there is no item -1"));
		canonicalise(GRIMY_IRIT_NOTED, GRIMY_IRIT);
		canonicalise(GRIMY_IRIT, GRIMY_IRIT);

		assertSame("an empty entry must be skipped, not asked about",
			side.getDynamicChildren()[1], resolver.getSideItem(GRIMY_IRIT));
	}

	@Test
	public void theRightStackIsPickedOutOfAFullPanel()
	{
		Widget side = sidePanelHolding(YEW_LOGS, GRIMY_IRIT_NOTED, 1513);
		canonicalise(YEW_LOGS, YEW_LOGS);
		canonicalise(1513, 1513);
		canonicalise(GRIMY_IRIT_NOTED, GRIMY_IRIT);
		canonicalise(GRIMY_IRIT, GRIMY_IRIT);

		assertSame(side.getDynamicChildren()[1], resolver.getSideItem(GRIMY_IRIT));
	}
}
