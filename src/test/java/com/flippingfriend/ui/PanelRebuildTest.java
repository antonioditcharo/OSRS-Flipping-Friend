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
	public void aChangedHoldingStillRedrawsImmediately() throws Exception
	{
		PositionsPanel panel = panelAt(folder.newFolder("b").toPath());
		book.recordBuy(MAGIC_LOGS, "Magic logs", 10, 10_000, Instant.now());

		panel.refresh();
		Component card = panel.getComponent(0);

		// Skipping a redraw is only safe if a real change still lands on the next pass. This is the
		// half that a signature covering too little would break, and it would break silently.
		book.recordBuy(MAGIC_LOGS, "Magic logs", 5, 10_000, Instant.now());
		panel.refresh();

		assertNotSame("a change in the holding must redraw", card, panel.getComponent(0));
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
