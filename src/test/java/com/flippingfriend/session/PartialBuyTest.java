package com.flippingfriend.session;

import com.flippingfriend.CheckInterval;
import com.flippingfriend.FlippingFriendConfig;
import com.flippingfriend.RiskProfile;
import com.flippingfriend.data.Candle;
import com.flippingfriend.data.ItemMetadata;
import com.flippingfriend.data.LatestPrice;
import com.flippingfriend.data.MarketDataService;
import com.flippingfriend.data.MarketSnapshot;
import com.flippingfriend.data.PluginStorage;
import com.flippingfriend.data.TestStorage;
import com.flippingfriend.model.Calibrator;
import com.flippingfriend.model.Explainer;
import com.flippingfriend.model.FeatureEngine;
import com.flippingfriend.model.FillModel;
import com.flippingfriend.model.LstmForecasterClient;
import com.flippingfriend.model.ManipulationFilter;
import com.flippingfriend.model.PositionSizer;
import com.flippingfriend.model.PositionStatus;
import com.flippingfriend.model.Scorer;
import com.flippingfriend.model.Suggestion;
import com.flippingfriend.model.SuggestionEngine;
import com.flippingfriend.model.SuggestionType;
import com.flippingfriend.model.TaxCalculator;
import com.flippingfriend.model.arbitrage.ArbitrageRegistry;
import com.google.gson.Gson;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.runelite.api.GrandExchangeOffer;
import net.runelite.api.GrandExchangeOfferState;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.Mockito;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Half a trade is not a holding.
 * <p>
 * The rule that nothing is ever held back is about a position that has <em>finished arriving</em> and
 * is being kept in a bank waiting for a price. It was never meant to reach into the middle of an
 * order that is still filling — and twice it did, for two different reasons.
 * <p>
 * The first guard asked "is this a sell?", which was a real question while the sell engine could
 * answer HOLD and stopped being one the moment it began answering yes to everything. So a buy that
 * had filled six hundred of three thousand was treated as a finished holding, given a sell candidate,
 * and with every slot busy the engine cancelled a <em>different</em> item's buy to make room. That is
 * the worst version of every rule involved at once: it leaves the position at the moment it holds the
 * least of it, spends a second slot to do so, and gives up a third.
 * <p>
 * The second guard asked "can we see the units?", on the reasoning that anything genuinely in hand is
 * sellable. Collecting a part-filled buy is precisely how the units become visible, so a player doing
 * the ordinary thing turned their own accumulation into a sell candidate by doing it. Reported from a
 * live session as the plugin selling items "just because they were put into my inventory".
 * <p>
 * Neither question was the right one. While our own buy for an item is working, none of that item is
 * listed: it is one accumulation, bought to be sold at one target, and selling the early part of it
 * back into the same book pays the tax twice on the same capital and abandons the planned trade. The
 * order finishes or it is cancelled, and the next pass lists the position.
 * <p>
 * One exception, and it is the part that was always right: a position through its <em>stop</em> does
 * need action, but the action is to stop buying rather than to sell around the buy, because
 * continuing to accumulate something we have decided to leave makes no sense at any slot count.
 */
public class PartialBuyTest
{
	/** The item being accumulated: cheap, deep, ordered in bulk. */
	private static final int GRAPES = 1987;
	private static final String GRAPES_NAME = "Grapes";
	private static final int GRAPES_BID = 400;
	private static final int GRAPES_ASK = 424;

	/** A second, unrelated buy. Its slot is the thing the old code went after. */
	private static final int BARS = 2361;
	private static final String BARS_NAME = "Adamant bar";
	private static final int BARS_BID = 2_000;
	private static final int BARS_ASK = 2_120;

	private static final int ORDERED = 3_000;
	private static final int FILLED = 600;
	private static final int BUCKET_SECONDS = 300;

	@Rule
	public final TemporaryFolder folder = new TemporaryFolder();

	private final TaxCalculator tax = new TaxCalculator();
	private SuggestionEngine engine;
	private OfferTracker offers;
	private PositionBook positions;
	private FakeAccount account;
	private FakeMarket marketData;

	// ------------------------------------------------------------------ fixtures

	/** A busy book at a steady price, which is what gives an item a believable spread. */
	private static List<Candle> bars(int bid, int ask, int count, int stepSeconds)
	{
		List<Candle> series = new ArrayList<>(count);
		long now = Instant.now().getEpochSecond();
		for (int i = 0; i < count; i++)
		{
			int drift = (int) Math.round(bid * 0.02 * Math.sin(i * 0.37));
			series.add(new Candle(now - (long) (count - i) * stepSeconds,
				ask + drift, bid + drift, 4_000, 4_000));
		}
		return series;
	}

	/**
	 * The market, with the network taken out and one knob left in: the grapes price can be dropped
	 * through the position's stop without disturbing anything else.
	 */
	private final class FakeMarket extends MarketDataService
	{
		private int grapesBid = GRAPES_BID;
		private int grapesAsk = GRAPES_ASK;

		FakeMarket(PluginStorage storage)
		{
			super(null, new Gson(), storage);
		}

		void crashGrapes()
		{
			grapesBid = GRAPES_BID / 2;
			grapesAsk = GRAPES_ASK / 2;
		}

		@Override
		public MarketSnapshot getSnapshot()
		{
			long now = Instant.now().getEpochSecond();
			Map<Integer, ItemMetadata> metadata = new HashMap<>();
			metadata.put(GRAPES, new ItemMetadata(GRAPES, GRAPES_NAME, false, 13_000, 100));
			metadata.put(BARS, new ItemMetadata(BARS, BARS_NAME, false, 30_000, 1_000));

			Map<Integer, LatestPrice> latest = new HashMap<>();
			latest.put(GRAPES, new LatestPrice(grapesAsk, now - 30, grapesBid, now - 30));
			latest.put(BARS, new LatestPrice(BARS_ASK, now - 30, BARS_BID, now - 30));

			Map<Integer, Candle> five = new HashMap<>();
			five.put(GRAPES, new Candle(now, grapesAsk, grapesBid, 4_000, 4_000));
			five.put(BARS, new Candle(now, BARS_ASK, BARS_BID, 4_000, 4_000));

			Map<Integer, Candle> hourly = new HashMap<>();
			hourly.put(GRAPES, new Candle(now, grapesAsk, grapesBid, 48_000, 48_000));
			hourly.put(BARS, new Candle(now, BARS_ASK, BARS_BID, 48_000, 48_000));

			return new MarketSnapshot(metadata, latest, five, hourly, Instant.now());
		}

		@Override
		public List<Candle> getSeries(int itemId, String timestep)
		{
			int step = "5m".equals(timestep) ? BUCKET_SECONDS : 3_600;
			int count = "5m".equals(timestep) ? 300 : 400;
			if (itemId == GRAPES)
			{
				return bars(grapesBid, grapesAsk, count, step);
			}
			if (itemId == BARS)
			{
				return bars(BARS_BID, BARS_ASK, count, step);
			}
			return Collections.emptyList();
		}

		@Override
		public void prefetchSeries(List<Integer> itemIds, String timestep)
		{
		}
	}

	/** Both slots busy, which is the condition that made the old code cannibalise a buy. */
	private static final class FakeAccount extends AccountMonitor
	{
		private int freeSlots;
		private long coins = 40_000_000L;
		private long committed;
		private final Map<Integer, Integer> inventory = new HashMap<>();

		FakeAccount()
		{
			super(null, null, null);
		}

		@Override
		public AccountState getState()
		{
			return new AccountState(true, true, false, coins, 0, 0, freeSlots, 2, committed, true,
				new HashMap<>(inventory), new HashMap<>(inventory), new HashMap<>());
		}
	}

	private static FlippingFriendConfig config()
	{
		return new FlippingFriendConfig()
		{
			@Override
			public void setShowOnboarding(boolean value)
			{
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

			@Override
			public boolean useCalibration()
			{
				return false;
			}
		};
	}

	@Before
	public void setUp() throws Exception
	{
		Path root = folder.newFolder("partial").toPath();
		PluginStorage storage = TestStorage.rootedAt(root, "player");

		positions = new PositionBook(storage);
		BuyLimitTracker buyLimits = new BuyLimitTracker(storage);
		TradeJournal journal = new TradeJournal(storage, null, new AccountMonitor(null, null, null));
		TradePlans plans = new TradePlans(storage);
		offers = new OfferTracker(storage, positions, buyLimits, journal, tax, plans);

		account = new FakeAccount();
		marketData = new FakeMarket(storage);
		offers.setMarket(marketData.getSnapshot());

		FillModel fillModel = new FillModel();
		LstmForecasterClient noForecaster = Mockito.mock(LstmForecasterClient.class);
		Mockito.when(noForecaster.predictBulk(Mockito.any())).thenReturn(Collections.emptyMap());

		engine = new SuggestionEngine(marketData, new FeatureEngine(), new ManipulationFilter(),
			new Scorer(tax, fillModel, new PositionSizer(), new Calibrator()), new Explainer(),
			new Calibrator(), tax, account, buyLimits, positions, offers,
			new SellTimingEngine(fillModel, tax), plans, config(), new SkipList(storage),
			noForecaster, new ArbitrageRegistry());

		journal.startSession();
	}

	// ------------------------------------------------------------------ the exchange

	private static GrandExchangeOffer offer(int itemId, GrandExchangeOfferState state, int price,
		int total, int filled)
	{
		GrandExchangeOffer o = Mockito.mock(GrandExchangeOffer.class);
		Mockito.when(o.getState()).thenReturn(state);
		Mockito.when(o.getItemId()).thenReturn(itemId);
		Mockito.when(o.getPrice()).thenReturn(price);
		Mockito.when(o.getTotalQuantity()).thenReturn(total);
		Mockito.when(o.getQuantitySold()).thenReturn(filled);
		// Pre-tax on both sides, which is what the game reports.
		Mockito.when(o.getSpent()).thenReturn(price * filled);
		return o;
	}

	/**
	 * The board as the player left it: grapes part-filled and still working, bars untouched and
	 * still working, and no slot spare.
	 */
	private void boardWithAPartFilledBuy()
	{
		account.freeSlots = 0;
		account.committed = (long) GRAPES_BID * ORDERED + (long) BARS_BID * 1_000;
		account.coins -= account.committed;

		offers.onOfferChanged(0, offer(GRAPES, GrandExchangeOfferState.BUYING, GRAPES_BID, ORDERED, 0));
		offers.onOfferChanged(1, offer(BARS, GrandExchangeOfferState.BUYING, BARS_BID, 1_000, 0));
		// The market gives us a fifth of the order. The rest is still queued.
		offers.onOfferChanged(0,
			offer(GRAPES, GrandExchangeOfferState.BUYING, GRAPES_BID, ORDERED, FILLED));
	}

	// ------------------------------------------------------------------ the rule

	@Test
	public void aPartFilledBuyIsNotAHoldingAndIsNotSold()
	{
		boardWithAPartFilledBuy();

		Position growing = positions.get(GRAPES);
		assertNotNull("the fills have to reach the book, or this test proves nothing", growing);
		assertEquals(FILLED, growing.getQuantity());

		Suggestion suggestion = engine.refresh(true);

		if (suggestion != null)
		{
			assertTrue("a buy that is still filling must not be listed for sale: "
					+ suggestion.getType() + " " + suggestion.getHeadline(),
				suggestion.getType() != SuggestionType.SELL || suggestion.getItemId() != GRAPES);
			assertTrue("and must never cost another item its slot: "
					+ suggestion.getType() + " " + suggestion.getHeadline(),
				suggestion.getType() != SuggestionType.CANCEL);
		}
	}

	@Test
	public void theCardSaysItIsStillBuyingRatherThanGoingQuiet()
	{
		boardWithAPartFilledBuy();
		engine.refresh(true);

		PositionStatus status = engine.getPositionStatuses().get(GRAPES);
		assertNotNull("waiting silently is how a plugin looks like it has forgotten", status);
		assertTrue("the wait has to be named as a wait: " + status.summary(), status.isStillBuying());
		assertTrue("with the progress on it: " + status.summary(),
			status.summary().contains(FILLED + " of " + ORDERED));
		assertTrue("and the reason it ends: " + status.summary(),
			status.summary().contains("once the buy finishes"));
	}

	@Test
	public void theExitIsDeferredNotDropped()
	{
		boardWithAPartFilledBuy();
		engine.refresh(true);

		// Nothing changes except that the buy stops: the rest fills and the player collects.
		offers.onOfferChanged(0, offer(GRAPES, GrandExchangeOfferState.BOUGHT, GRAPES_BID, ORDERED,
			ORDERED));
		offers.onOfferChanged(0, offer(GRAPES, GrandExchangeOfferState.EMPTY, GRAPES_BID, ORDERED,
			ORDERED));
		account.freeSlots = 1;
		account.committed -= (long) GRAPES_BID * ORDERED;
		account.inventory.put(GRAPES, ORDERED);

		Suggestion suggestion = engine.refresh(true);

		assertNotNull("a finished holding has to be listed on the pass that sees it", suggestion);
		assertEquals("and this is that pass: " + suggestion.getHeadline(),
			SuggestionType.SELL, suggestion.getType());
		assertEquals(GRAPES, suggestion.getItemId());
		assertEquals("all of it, not the fraction the old code would have dumped",
			ORDERED, suggestion.getQuantity());
	}

	/**
	 * Collecting the part that has arrived does not turn it into a holding.
	 * <p>
	 * This file first pinned the opposite, on the reasoning that buying at the bid while selling at
	 * the ask is the whole strategy, so units genuinely in hand should be sold whatever else is
	 * running. That is true of two <em>separate</em> trades and false of one: these units and the
	 * ones still arriving are the same accumulation, bought to be sold at the same target. Listing
	 * the early part of it back into the same book pays the tax twice on the same capital, spends a
	 * second slot to do it, and abandons the trade that was actually planned.
	 * <p>
	 * It also made visibility the test, and visibility is exactly what collecting changes. A player
	 * who collects a part-filled buy -- which is the ordinary thing to do -- turned their own
	 * accumulation into a sell candidate by doing so. Reported from a live session as the plugin
	 * "trying to sell items that are still part of an ongoing buy order, just because they were put
	 * into my inventory".
	 */
	@Test
	public void collectingThePartThatArrivedDoesNotMakeItSellable()
	{
		boardWithAPartFilledBuy();
		// The player collects the 600 that have filled so far. The buy keeps running for the rest.
		positions.recordBuy(GRAPES, GRAPES_NAME, 800, 800L * GRAPES_BID, Instant.now());
		account.inventory.put(GRAPES, 800);
		account.freeSlots = 1;

		Suggestion suggestion = engine.refresh(true);

		if (suggestion != null)
		{
			assertTrue("the units being visible is not the question -- the buy is still running: "
					+ suggestion.getType() + " " + suggestion.getHeadline(),
				suggestion.getType() != SuggestionType.SELL || suggestion.getItemId() != GRAPES);
		}

		PositionStatus status = engine.getPositionStatuses().get(GRAPES);
		assertNotNull(status);
		assertTrue("and the card still says what it is waiting for: " + status.summary(),
			status.isStillBuying());
	}

	@Test
	public void aPositionThroughItsStopStopsBuyingRatherThanSellingAroundTheBuy()
	{
		boardWithAPartFilledBuy();
		// The item falls by half. Moderate cuts at five percent below cost, so this is well through
		// the stop: continuing to accumulate it is the thing that makes no sense.
		marketData.crashGrapes();
		offers.setMarket(marketData.getSnapshot());

		Suggestion suggestion = engine.refresh(true);

		assertNotNull("a position through its stop cannot be left to keep growing", suggestion);
		assertEquals("the answer is to stop buying it, not to sell around the buy: "
			+ suggestion.getType() + " " + suggestion.getHeadline(),
			SuggestionType.CANCEL, suggestion.getType());
		assertEquals("and the offer cancelled is its own, not another item's",
			GRAPES, suggestion.getItemId());
		assertEquals("which is the slot it is actually sitting in", 0, suggestion.getSlot());

		PositionStatus status = engine.getPositionStatuses().get(GRAPES);
		assertNotNull(status);
		assertTrue("the card has to agree with the suggestion beside it: " + status.summary(),
			status.summary().contains("cancel that first"));
	}
}
