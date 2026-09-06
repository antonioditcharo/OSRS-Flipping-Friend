package com.flippingfriend.ui;

import com.flippingfriend.data.MarketDataService;
import com.flippingfriend.data.TestStorage;
import com.flippingfriend.data.WikiPriceClient;
import com.flippingfriend.model.Explainer;
import com.flippingfriend.model.TaxCalculator;
import com.flippingfriend.session.PositionBook;
import com.google.gson.Gson;
import java.awt.Component;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Collections;
import net.runelite.client.game.ItemManager;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.Mockito;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * The sidebar must not redraw itself when nothing has changed.
 * <p>
 * Every section here rebuilt from scratch on every poll — several times a minute, tearing down every
 * card and constructing a new one whether or not a single number had moved. That is what the panel
 * looked like from the outside: a visible flash as the column emptied and refilled. It is also work
 * done on the Swing thread, which is the one place the cost is guaranteed to be seen.
 * <p>
 * Component <em>identity</em> is what these assert, not component count. A rebuild that happened to
 * produce the same number of cards would pass a count check while still flashing, and the flash is
 * the fault being fixed.
 */
public class PanelRebuildTest
{
	private static final int MAGIC_LOGS = 1513;
	private static final int YEW_LOGS = 1515;

	@Rule
	public final TemporaryFolder folder = new TemporaryFolder();

	private PositionBook book;

	private PositionsPanel panelAt(Path root)
	{
		book = new PositionBook(TestStorage.rootedAt(root, "p"));
		book.load();

		MarketDataService market = new MarketDataService(Mockito.mock(WikiPriceClient.class),
			new Gson(), TestStorage.rootedAt(root, "p"));

		return new PositionsPanel(book, market, Mockito.mock(ItemManager.class), new Explainer(),
			new TaxCalculator(), Collections::emptyMap);
	}

	@Test
	public void anUnchangedRefreshDoesNotRebuildTheCards() throws Exception
	{
		PositionsPanel panel = panelAt(folder.newFolder("a").toPath());
		book.recordBuy(MAGIC_LOGS, "Magic logs", 10, 10_000, Instant.now());

		panel.refresh();
		assertTrue("the holding should have produced a card", panel.getComponentCount() > 0);
		Component card = panel.getComponent(0);

		panel.refresh();
		panel.refresh();

		assertSame("a refresh with nothing changed must leave the card alone", card,
			panel.getComponent(0));
	}

	@Test
	public void aChangedHoldingStillShowsTheChange()
	{
		PositionsPanel panel = panelAt(folder.getRoot().toPath());
		book.recordBuy(MAGIC_LOGS, "Magic logs", 10, 10_000, Instant.now());

		panel.refresh();
		assertTrue("premise: ten held is on screen", renderedText(panel).contains("10 held"));

		// Skipping a redraw is only safe if a real change still lands on the next pass. This is the
		// half that a signature covering too little would break, and it would break silently.
		book.recordBuy(MAGIC_LOGS, "Magic logs", 5, 10_000, Instant.now());
		panel.refresh();

		// Asserted on what is drawn rather than on component identity.
		//
		// It used to check that the card object had been replaced, which was the right question while
		// every refresh tore the panel down and built new cards. The rows are now kept and their
		// labels updated in place -- a better answer to the flicker this class exists about -- and
		// under that strategy the card SHOULD be the same object. Identity stopped being evidence of
		// anything; the text is the thing a player can actually see.
		assertTrue("the new quantity has to reach the screen: " + renderedText(panel),
			renderedText(panel).contains("15 held"));
		assertFalse("and the old one must not still be there",
			renderedText(panel).contains("10 held"));
	}

	/** Every piece of text the panel is currently displaying, wherever it sits in the tree. */
	private static String renderedText(java.awt.Container root)
	{
		StringBuilder text = new StringBuilder();
		for (Component child : root.getComponents())
		{
			if (child instanceof javax.swing.JLabel)
			{
				text.append(((javax.swing.JLabel) child).getText()).append(' ');
			}
			else if (child instanceof javax.swing.text.JTextComponent)
			{
				text.append(((javax.swing.text.JTextComponent) child).getText()).append(' ');
			}
			if (child instanceof java.awt.Container)
			{
				text.append(renderedText((java.awt.Container) child));
			}
		}
		return text.toString();
	}

	@Test
	public void aNewHoldingRedraws() throws Exception
	{
		PositionsPanel panel = panelAt(folder.newFolder("c").toPath());
		book.recordBuy(MAGIC_LOGS, "Magic logs", 10, 10_000, Instant.now());
		panel.refresh();
		int before = panel.getComponentCount();

		book.recordBuy(YEW_LOGS, "Yew logs", 20, 300, Instant.now());
		panel.refresh();

		assertTrue("a second holding must appear", panel.getComponentCount() > before);
	}

	@Test
	public void anEmptyPanelIsDrawnOnceAndLeftAlone() throws Exception
	{
		PositionsPanel panel = panelAt(folder.newFolder("d").toPath());

		panel.refresh();
		assertTrue("the empty-state card should have been drawn", panel.getComponentCount() > 0);
		Component empty = panel.getComponent(0);

		panel.refresh();

		assertSame("nothing held is still nothing changed", empty, panel.getComponent(0));
	}
}
