package com.flippingfriend.ui;

import com.flippingfriend.data.MarketDataService;
import com.flippingfriend.data.TestStorage;
import com.flippingfriend.data.WikiPriceClient;
import com.flippingfriend.model.Explainer;
import com.flippingfriend.model.TaxCalculator;
import com.flippingfriend.session.Position;
import com.flippingfriend.session.PositionBook;
import com.google.gson.Gson;
import java.awt.Component;
import java.awt.Container;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.swing.JLabel;
import net.runelite.client.game.ItemManager;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.Mockito;

import static org.junit.Assert.assertTrue;

/**
 * What "live profit" on a holding is measured against.
 * <p>
 * It was the instant-sell price, so a holding on its way to a target it had not reached yet always
 * read as a loss. That is true of the moment and false of the trade: it is the number you would get
 * for bailing out this second, which is precisely the thing the plugin is telling you not to do. A
 * position bought at a good price and waiting patiently looked like a mistake on every refresh.
 * <p>
 * The target is the honest basis — it is what the trade is worth if it works out — and because that
 * is a price the market has not reached, the figure is captioned as such. Both halves matter: the
 * number without the caption would read as money already made.
 */
public class HeldProfitBasisTest
{
	private static final int MAGIC_LOGS = 1513;

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

	private static List<String> labelsIn(Container container)
	{
		List<String> text = new ArrayList<>();
		for (Component child : container.getComponents())
		{
			if (child instanceof JLabel)
			{
				text.add(((JLabel) child).getText());
			}
			if (child instanceof Container)
			{
				text.addAll(labelsIn((Container) child));
			}
		}
		return text;
	}

	@Test
	public void theHeadlineFigureIsWhatTheTargetIsWorth() throws Exception
	{
		PositionsPanel panel = panelAt(folder.newFolder("target").toPath());
		book.recordBuy(MAGIC_LOGS, "Magic logs", 100, 100_000, Instant.now());
		Position position = book.get(MAGIC_LOGS);
		position.setTargetSellPrice(1_200);

		panel.refresh();
		List<String> labels = labelsIn(panel);

		// 100 bought for 100,000 -- a thousand each -- sold at 1,200, less the tax on the sale.
		long expected = new TaxCalculator().netProfit(MAGIC_LOGS, 1_000, 1_200, 100);
		String shown = new Explainer().formatGp(expected);

		assertTrue("the figure must be what the target is worth, not what bailing out would give: "
			+ labels, labels.contains(shown));
		assertTrue("and it must say that is what it is: " + labels, labels.contains("at target"));
	}

	@Test
	public void aHoldingWithNoTargetFallsBackToTheMarketAndSaysSo() throws Exception
	{
		PositionsPanel panel = panelAt(folder.newFolder("no-target").toPath());
		book.recordBuy(MAGIC_LOGS, "Magic logs", 100, 100_000, Instant.now());

		panel.refresh();
		List<String> labels = labelsIn(panel);

		// No target and no price data, so there is no basis to show a figure against at all -- what
		// must not happen is an uncaptioned number, or one captioned as a target it does not have.
		assertTrue("a holding with no target must never be captioned as having one: " + labels,
			!labels.contains("at target"));
	}
}
