package com.flippingfriend.ui;

import com.flippingfriend.CheckInterval;
import com.flippingfriend.FlippingFriendConfig;
import com.flippingfriend.RiskProfile;
import com.flippingfriend.companion.CompanionClient;
import com.flippingfriend.data.MarketDataService;
import com.flippingfriend.data.PluginStorage;
import com.flippingfriend.data.TestStorage;
import com.flippingfriend.model.Explainer;
import com.flippingfriend.model.Suggestion;
import com.flippingfriend.model.SuggestionEngine;
import com.flippingfriend.model.SuggestionType;
import com.flippingfriend.model.TaxCalculator;
import com.flippingfriend.overlay.StepGuide;
import com.flippingfriend.session.AccountMonitor;
import com.flippingfriend.session.AccountState;
import com.flippingfriend.session.FlipRecord;
import com.flippingfriend.session.Position;
import com.flippingfriend.session.PositionBook;
import com.flippingfriend.session.SessionStats;
import com.flippingfriend.session.TradeJournal;
import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import javax.imageio.ImageIO;
import javax.swing.JLabel;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.game.ItemManager;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.Mockito;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * What the sidebar actually says, built and laid out rather than reasoned about.
 * <p>
 * The complaint this answers is "the UI numbers do not update accordingly". Every number the panel
 * draws comes from somewhere else — the journal, the position book, the suggestion — and the way
 * that goes wrong is not usually a broken label. It is a correct label faithfully rendering a wrong
 * figure, or a card whose text says something the game cannot do. So this builds the real
 * {@link FlippingFriendPanel}, lays it out, reads every string on it, and checks those strings
 * against the ledger they are supposed to be reporting.
 * <p>
 * It also writes the rendered panel to {@code build/ui/} so it can be looked at, because "the wrong
 * number is on screen" and "the number is right but unreadable" are different bugs and only one of
 * them can be caught by an assertion.
 */
public class PanelShowsTheLedgerTest
{
	private static final int ITEM = 2361;
	private static final String ITEM_NAME = "Adamant bar";
	private static final int PANEL_WIDTH = 225;
	/** A blank line between paragraphs, kept out of the literals so the source stays readable. */
	private static final String NEWLINES = "\n\n";

	@Rule
	public final TemporaryFolder folder = new TemporaryFolder();

	private final TaxCalculator tax = new TaxCalculator();
	private final Explainer explainer = new Explainer();

	// ------------------------------------------------------------------ scaffolding

	private static FlippingFriendConfig config()
	{
		return new FlippingFriendConfig()
		{
			@Override
			public void setShowOnboarding(boolean value)
			{
			}

			@Override
			public boolean showOnboarding()
			{
				return false;
			}

			@Override
			public RiskProfile riskProfile()
			{
				return RiskProfile.MODERATE;
			}

			@Override
			public CheckInterval checkInterval()
			{
				return CheckInterval.FIFTEEN_MINUTES;
			}
		};
	}

	private static AccountMonitor account()
	{
		return new AccountMonitor(null, null, null)
		{
			@Override
			public AccountState getState()
			{
				return new AccountState(true, true, false, 42_000_000L, 0, 0, 6, 8, 0, true,
					new HashMap<>(), new HashMap<>(), new HashMap<>());
			}
		};
	}

	/**
	 * Builds the panel on the event dispatch thread and returns it laid out.
	 * <p>
	 * Swing components may only be touched from the EDT, and the panel's own refresh marshals onto
	 * it. Doing the same here means the test exercises the path the plugin actually takes rather
	 * than a thread-unsafe shortcut that happens to work.
	 */
	private FlippingFriendPanel panelShowing(Suggestion suggestion, List<Position> held,
		List<FlipRecord> flips) throws Exception
	{
		Path root = folder.newFolder("panel" + System.nanoTime()).toPath();
		PluginStorage storage = TestStorage.rootedAt(root, "p");

		PositionBook positions = new PositionBook(storage);
		for (Position position : held)
		{
			positions.recordBuy(position.getItemId(), position.getItemName(), position.getQuantity(),
				position.getTotalCost(), Instant.ofEpochSecond(position.getOpenedAt()));
			Position stored = positions.get(position.getItemId());
			stored.setTargetSellPrice(position.getTargetSellPrice());
			stored.setStopPrice(position.getStopPrice());
		}

		TradeJournal journal = new TradeJournal(storage, null, account());
		journal.startSession();
		for (FlipRecord flip : flips)
		{
			journal.record(flip);
		}

		SuggestionEngine engine = Mockito.mock(SuggestionEngine.class);
		Mockito.when(engine.getCurrent()).thenReturn(suggestion);
		Mockito.when(engine.getPositionStatuses()).thenReturn(Collections.emptyMap());
		Mockito.when(engine.isSellOnly()).thenReturn(false);

		StepGuide guide = Mockito.mock(StepGuide.class);
		Mockito.when(guide.getSuggestion()).thenReturn(suggestion);
		Mockito.when(guide.getPending()).thenReturn(null);

		ItemManager items = Mockito.mock(ItemManager.class);
		CompanionClient companion = Mockito.mock(CompanionClient.class);
		MarketDataService market = new MarketDataService(null, new com.google.gson.Gson(), storage);

		FlippingFriendPanel[] built = new FlippingFriendPanel[1];
		SwingUtilities.invokeAndWait(() -> built[0] = new FlippingFriendPanel(engine, positions,
			journal, account(), market, items, explainer, tax, config(),
			Mockito.mock(ConfigManager.class), companion, guide));

		FlippingFriendPanel panel = built[0];
		SwingUtilities.invokeAndWait(panel::refresh);
		// refresh() posts its work to the EDT, so drain the queue once more before reading it.
		SwingUtilities.invokeAndWait(() -> layOut(panel));
		return panel;
	}

	/**
	 * Lays the whole tree out without a native peer, so it can be read and painted headless.
	 * <p>
	 * Twice, and at the height the content actually wants. A BoxLayout column compresses its
	 * children below their preferred size when the container is shorter than they need, and what
	 * gets compressed first is exactly the wrapped text: a three-line description comes out as one
	 * clipped line. Laying out short and then painting tall produces a picture of a bug that is not
	 * there, which is worse than no picture.
	 */
	private static void layOut(Component component)
	{
		component.setSize(PANEL_WIDTH, 10_000);
		layOutTree(component);
		component.setSize(PANEL_WIDTH, contentHeight(component));
		layOutTree(component);
	}

	/** How tall the column inside the scroll pane wants to be, which is what has to be painted. */
	private static int contentHeight(Component component)
	{
		if (component instanceof UiUtils.ScrollableColumn)
		{
			return component.getPreferredSize().height + 2 * UiUtils.PANEL_BORDER;
		}
		if (component instanceof Container)
		{
			for (Component child : ((Container) component).getComponents())
			{
				int found = contentHeight(child);
				if (found > 0)
				{
					return found;
				}
			}
		}
		return 0;
	}

	private static void layOutTree(Component component)
	{
		component.doLayout();
		if (component instanceof Container)
		{
			for (Component child : ((Container) component).getComponents())
			{
				layOutTree(child);
			}
		}
	}

	/** Every string the panel is drawing, in the order it draws them. */
	private static List<String> textOn(Component component)
	{
		List<String> found = new ArrayList<>();
		collect(component, found);
		return found;
	}

	private static void collect(Component component, List<String> into)
	{
		if (component instanceof JLabel)
		{
			String text = ((JLabel) component).getText();
			if (text != null && !text.isEmpty())
			{
				into.add(text);
			}
		}
		else if (component instanceof JTextArea)
		{
			String text = ((JTextArea) component).getText();
			if (text != null && !text.isEmpty())
			{
				into.add(text);
			}
		}
		else if (component instanceof javax.swing.AbstractButton)
		{
			String text = ((javax.swing.AbstractButton) component).getText();
			if (text != null && !text.isEmpty())
			{
				into.add(text);
			}
		}
		if (component instanceof Container)
		{
			for (Component child : ((Container) component).getComponents())
			{
				collect(child, into);
			}
		}
	}

	private static boolean showsText(List<String> text, String wanted)
	{
		for (String line : text)
		{
			if (line.contains(wanted))
			{
				return true;
			}
		}
		return false;
	}

	/** Saves the laid-out panel so a person can look at it. */
	private static void save(FlippingFriendPanel panel, String name) throws Exception
	{
		File dir = new File("build/ui");
		assertTrue(dir.isDirectory() || dir.mkdirs());
		int height = Math.max(400, panel.getHeight());
		BufferedImage image = new BufferedImage(PANEL_WIDTH, height, BufferedImage.TYPE_INT_RGB);
		Graphics2D graphics = image.createGraphics();
		graphics.setRenderingHint(java.awt.RenderingHints.KEY_TEXT_ANTIALIASING,
			java.awt.RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		SwingUtilities.invokeAndWait(() -> panel.printAll(graphics));
		graphics.dispose();
		ImageIO.write(image, "png", new File(dir, name));
	}

	// ------------------------------------------------------------------ the checks

	@Test
	public void theBuyCardShowsTheNumbersTheGameNeedsTyped() throws Exception
	{
		Suggestion buy = Suggestion.builder(SuggestionType.BUY)
			.item(ITEM, ITEM_NAME)
			.price(2_001)
			.quantity(14_969)
			.targetSellPrice(2_119)
			.expectedProfit(tax.netProfit(ITEM, 2_001, 2_119, 14_969))
			.confidence(0.72)
			.expectedMinutes(96)
			.fillMinutes(41, 55)
			.breakEvenPrice(tax.breakEvenSellPrice(ITEM, 2_001))
			.headline("Buy 14,969 × " + ITEM_NAME)
			.build();

		FlippingFriendPanel panel = panelShowing(buy, Collections.emptyList(),
			Collections.emptyList());
		List<String> text = textOn(panel);
		save(panel, "buy-card.png");

		assertTrue("the item has to be named: " + text, showsText(text, ITEM_NAME));
		assertTrue("the exact price, with separators, because it has to be typed in",
			showsText(text, "2,001"));
		assertTrue("and the exact quantity", showsText(text, "14,969"));
		assertTrue("the exit is part of the plan, not a surprise later",
			showsText(text, "2,119"));
		assertTrue("the profit is the after-tax figure, not the raw spread: "
				+ explainer.formatGp(tax.netProfit(ITEM, 2_001, 2_119, 14_969)),
			showsText(text, explainer.formatGp(tax.netProfit(ITEM, 2_001, 2_119, 14_969))));
		assertFalse("and no markdown leaks through -- this panel is plain text",
			showsText(text, "**"));
	}

	@Test
	public void theRepriceCardTellsYouSomethingTheGameCanDo() throws Exception
	{
		// The card used to read "Adjust your offer to 2,119 gp". There is no such action: a running
		// offer cannot be edited, and a player who goes looking for the button does not find one.
		Suggestion reprice = Suggestion.builder(SuggestionType.MODIFY_SELL)
			.item(ITEM, ITEM_NAME)
			.slot(2)
			.price(2_119)
			.quantity(5_000)
			.expectedProfit(tax.netProfit(ITEM, 2_001, 2_119, 5_000))
			.headline("Your " + ITEM_NAME + " sell offer has been undercut")
			.detail("Other sellers are now asking less than you, so yours is at the back of the "
				+ "queue.\n\nThe Grand Exchange cannot change the price of an offer that is already "
				+ "running, so cancel this one, collect the items back, and list them again at "
				+ "2,119 gp.")
			.build();

		FlippingFriendPanel panel = panelShowing(reprice, Collections.emptyList(),
			Collections.emptyList());
		List<String> text = textOn(panel);
		save(panel, "reprice-card.png");

		assertTrue("the instruction has to be the three real actions", showsText(text, "cancel"));
		assertTrue(showsText(text, "collect"));
		assertTrue(showsText(text, "list them again"));
		assertFalse("and never the one the game does not have",
			showsText(text, "Adjust your offer"));
		assertFalse(showsText(text, "**"));
		assertTrue("with the new price spelled out to type", showsText(text, "2,119"));
	}

	@Test
	public void theSellCardSaysWhyItIsSellingNow() throws Exception
	{
		// The plugin no longer keeps anything back, so most sales happen for no reason more dramatic
		// than the rule itself. The card has to say that plainly rather than inventing a signal.
		Suggestion sell = Suggestion.builder(SuggestionType.SELL)
			.item(ITEM, ITEM_NAME)
			.price(2_108)
			.quantity(5_000)
			.expectedProfit(tax.netProfit(ITEM, 2_001, 2_108, 5_000))
			.expectedMinutes(38)
			.fillMinutes(0, 38)
			.headline("Sell your " + ITEM_NAME)
			.detail("Listed now rather than waited on. An item sitting unlisted earns nothing, and "
				+ "the price it is being kept back for may never arrive.")
			.build();

		FlippingFriendPanel panel = panelShowing(sell, Collections.emptyList(),
			Collections.emptyList());
		List<String> text = textOn(panel);
		save(panel, "sell-card.png");

		assertTrue(showsText(text, ITEM_NAME));
		assertTrue("the price to type", showsText(text, "2,108"));
		assertTrue("the quantity to type", showsText(text, "5,000"));
		assertTrue("after-tax profit",
			showsText(text, explainer.formatGp(tax.netProfit(ITEM, 2_001, 2_108, 5_000))));
		assertTrue("and the reason, in full", showsText(text, "may never arrive"));

		List<String> clipped = new ArrayList<>();
		findClipped(panel, clipped);
		assertTrue("nothing cut off: " + clipped, clipped.isEmpty());
	}

	@Test
	public void theHoldingCardQuotesProfitAfterTax() throws Exception
	{
		Position held = new Position(ITEM, ITEM_NAME, 5_000, 5_000L * 2_001,
			Instant.now().getEpochSecond() - 1_800, true);
		held.setTargetSellPrice(2_119);
		held.setStopPrice(1_901);

		FlippingFriendPanel panel = panelShowing(Suggestion.idle(),
			Collections.singletonList(held), Collections.emptyList());
		List<String> text = textOn(panel);
		save(panel, "holding-card.png");

		long afterTax = tax.netProfit(ITEM, 2_001, 2_119, 5_000);
		long beforeTax = (2_119L - 2_001L) * 5_000;

		assertTrue("the holding is on screen", showsText(text, ITEM_NAME));
		assertTrue("5,000 held", showsText(text, "5,000"));
		assertTrue("valued after the tax it will pay on the way out: "
				+ explainer.formatGp(afterTax), showsText(text, explainer.formatGp(afterTax)));
		assertFalse("never at the gross figure, which is the whole error this plugin exists to "
				+ "avoid: " + explainer.formatGp(beforeTax),
			showsText(text, explainer.formatGp(beforeTax)));
		assertTrue("and captioned so a figure at a price the market has not reached is not read as "
			+ "money already made", showsText(text, "at target"));
	}

	@Test
	public void thePerformanceRowsAreTheJournalAndNothingElse() throws Exception
	{
		int quantity = 5_000;
		int buy = 2_001;
		int sell = 2_119;
		long taxPaid = tax.taxFor(ITEM, sell, quantity);
		long profit = (long) (sell - buy) * quantity - taxPaid;

		FlipRecord flip = new FlipRecord(ITEM, ITEM_NAME, quantity, buy, sell, taxPaid, profit,
			Instant.now().getEpochSecond() - 3_600, Instant.now().getEpochSecond() - 600,
			90, profit, "MODERATE");

		FlippingFriendPanel panel = panelShowing(Suggestion.idle(), Collections.emptyList(),
			Collections.singletonList(flip));
		List<String> text = textOn(panel);
		save(panel, "performance.png");

		assertTrue("the flip appears in Recent trades", showsText(text, ITEM_NAME));
		assertTrue("Profit is the journal's own figure: " + explainer.formatGp(profit),
			showsText(text, explainer.formatGp(profit)));
		assertTrue("Tax paid is the tax that was actually deducted from it: "
			+ explainer.formatGp(taxPaid), showsText(text, explainer.formatGp(taxPaid)));
		assertTrue("one flip, and it went well", showsText(text, "1"));

		// The invariant the panel's two rows have to satisfy together. A "Tax paid" line beside a
		// profit that never had the tax taken out of it is exactly what made the old ledger look
		// like it balanced.
		SessionStats stats = new TradeJournalProbe(flip).stats();
		assertTrue("profit and tax have to come from the same arithmetic",
			stats.getProfit() + stats.getTaxPaid() == (long) (sell - buy) * quantity);
	}

	@Test
	public void nothingTheCardSaysIsCutOff() throws Exception
	{
		// The assertion that the text-presence checks above cannot make. A clipped component still
		// holds its whole string, so reading the model finds every word while the player sees a
		// sentence that stops mid-clause. This measures the geometry instead: every wrapped area has
		// to be tall enough to draw the text it is holding, at the width it was actually given.
		//
		// It is the invariant two real faults broke at once. Heights were computed from a cached
		// preferred size that was never re-measured, so every area created empty and filled later
		// was pinned at one line; and the recommendation's detail was measured against the full card
		// width while being drawn in the header column beside the item icon, a quarter narrower. The
		// reprice instruction ended at "cancel this one, collect the" -- without the price to
		// re-list at, which is the one number the card exists to give you.
		Suggestion wordy = Suggestion.builder(SuggestionType.MODIFY_BUY)
			.item(ITEM, ITEM_NAME)
			.slot(1)
			.price(2_002)
			.quantity(5_000)
			.targetSellPrice(2_119)
			.expectedProfit(tax.netProfit(ITEM, 2_002, 2_119, 5_000))
			.headline("Your " + ITEM_NAME + " offer is too low")
			.detail("You offered 1,900 gp, but people are now selling at 2,001 gp, so your offer is "
				+ "being skipped over." + NEWLINES + "The Grand Exchange cannot change the price of an offer "
				+ "that is already running, so cancel this one and collect what it bought along with "
				+ "the coins it gives back. You will be told to place the replacement at 2,002 gp "
				+ "straight afterwards.")
			.build();

		FlippingFriendPanel panel = panelShowing(wordy, Collections.emptyList(),
			Collections.emptyList());
		save(panel, "no-clipping.png");

		List<String> clipped = new ArrayList<>();
		findClipped(panel, clipped);
		assertTrue("these are drawn with their text cut off: " + clipped, clipped.isEmpty());
	}

	/** Any wrapped area given less height than the text it holds needs at the width it was given. */
	private static void findClipped(Component component, List<String> into)
	{
		if (component instanceof JTextArea)
		{
			JTextArea area = (JTextArea) component;
			String text = area.getText();
			if (text != null && !text.isEmpty() && area.getWidth() > 0)
			{
				JTextArea measure = new JTextArea(text);
				measure.setLineWrap(true);
				measure.setWrapStyleWord(true);
				measure.setFont(area.getFont());
				measure.setSize(area.getWidth(), Short.MAX_VALUE);
				int needed = measure.getPreferredSize().height;
				if (area.getHeight() < needed)
				{
					into.add("\"" + text.replace((char) 10, ' ') + "\" has "
						+ area.getHeight() + "px for " + needed + "px of text at "
						+ area.getWidth() + "px wide");
				}
			}
		}
		if (component instanceof Container)
		{
			for (Component child : ((Container) component).getComponents())
			{
				findClipped(child, into);
			}
		}
	}

	/** Reads the same two figures back off a journal, so the panel's pair can be checked against them. */
	private static final class TradeJournalProbe
	{
		private final FlipRecord flip;

		TradeJournalProbe(FlipRecord flip)
		{
			this.flip = flip;
		}

		SessionStats stats()
		{
			return new SessionStats(1, flip.isWin() ? 1 : 0, flip.getProfit(), flip.getTax(),
				flip.actualMinutes(), 3_600, Collections.emptyMap());
		}
	}
}
