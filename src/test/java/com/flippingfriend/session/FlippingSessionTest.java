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
import com.flippingfriend.model.ManipulationFilter;
import com.flippingfriend.model.PositionSizer;
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
 * One whole flip, start to finish, through the real engine and the real bookkeeping.
 * <p>
 * Every other test in this tree checks one decision in isolation. This one plays the part of the
 * player: it asks the engine what to do, does it, tells the engine what happened, and asks again —
 * buy, collect, sell, collect — and then checks the number that lands in the journal against the
 * arithmetic the panel claims to be doing. Nothing is stubbed between the advice and the ledger:
 * {@link SuggestionEngine}, {@link Scorer}, {@link SellTimingEngine}, {@link OfferTracker},
 * {@link PositionBook}, {@link TradeJournal} and {@link TaxCalculator} are all the real classes.
 * Only the market and the account are fixtures, because those are the two things the game supplies.
 * <p>
 * It exists because the failures that matter here are not failures of one rule. They are gaps
 * between rules: advice that is given and then forgotten, a position that is bought and never sold,
 * a sale that completes and is booked at the wrong number. A suite of unit tests can be entirely
 * green while a player cannot complete a single flip.
 */
public class FlippingSessionTest
{
	private static final int ITEM = 2361;
	private static final String ITEM_NAME = "Adamant bar";
	private static final int BID = 2_000;
	private static final int ASK = 2_120;
	private static final int BUY_LIMIT = 30_000;
	/**
	 * A second item, so a holding and a mispriced offer can exist at the same time without being the
	 * same accumulation. They used to share one item id, which quietly made the ordering test depend
	 * on the plugin being willing to sell an item it was still buying -- something it must not do.
	 */
	private static final int OTHER = 1987;
	private static final String OTHER_NAME = "Grapes";
	private static final int OTHER_BID = 400;
	private static final int OTHER_ASK = 424;
	private static final int BUCKET_SECONDS = 300;
	private static final long COINS = 100_000_000L;

	@Rule
	public final TemporaryFolder folder = new TemporaryFolder();

	private final TaxCalculator tax = new TaxCalculator();
	private SuggestionEngine engine;
	private OfferTracker offers;
	private PositionBook positions;
	private TradeJournal journal;
	private FakeAccount account;

	// ------------------------------------------------------------------ fixtures

	/** A busy book at a steady price. The drift is what gives the item a believable spread. */
	private static List<Candle> bars(int count, int stepSeconds)
	{
		List<Candle> series = new ArrayList<>(count);
		long now = Instant.now().getEpochSecond();
		for (int i = 0; i < count; i++)
		{
			int drift = (int) Math.round(40 * Math.sin(i * 0.37));
			series.add(new Candle(now - (long) (count - i) * stepSeconds,
				ASK + drift, BID + drift, 4_000, 4_000));
		}
		return series;
	}

	/** The same shape of book as {@link #bars}, at the second item's price level. */
	private static List<Candle> otherBars(int count, int stepSeconds)
	{
		List<Candle> series = new ArrayList<>(count);
		long now = Instant.now().getEpochSecond();
		for (int i = 0; i < count; i++)
		{
			int drift = (int) Math.round(8 * Math.sin(i * 0.37));
			series.add(new Candle(now - (long) (count - i) * stepSeconds,
				OTHER_ASK + drift, OTHER_BID + drift, 4_000, 4_000));
		}
		return series;
	}

	private static MarketSnapshot market()
	{
		long now = Instant.now().getEpochSecond();
		Map<Integer, ItemMetadata> metadata = new HashMap<>();
		metadata.put(ITEM, new ItemMetadata(ITEM, ITEM_NAME, false, BUY_LIMIT, 1_000));
		metadata.put(OTHER, new ItemMetadata(OTHER, OTHER_NAME, false, 13_000, 100));
		Map<Integer, LatestPrice> latest = new HashMap<>();
		latest.put(ITEM, new LatestPrice(ASK, now - 30, BID, now - 30));
		latest.put(OTHER, new LatestPrice(OTHER_ASK, now - 30, OTHER_BID, now - 30));
		Map<Integer, Candle> five = new HashMap<>();
		five.put(ITEM, new Candle(now, ASK, BID, 4_000, 4_000));
		five.put(OTHER, new Candle(now, OTHER_ASK, OTHER_BID, 4_000, 4_000));
		Map<Integer, Candle> hourly = new HashMap<>();
		hourly.put(ITEM, new Candle(now, ASK, BID, 48_000, 48_000));
		hourly.put(OTHER, new Candle(now, OTHER_ASK, OTHER_BID, 48_000, 48_000));
		return new MarketSnapshot(metadata, latest, five, hourly, Instant.now());
	}

	/** A market service with the network taken out of it and nothing else changed. */
	private static final class FakeMarket extends MarketDataService
	{
		private final MarketSnapshot snapshot = market();
		private final List<Candle> shortSeries = bars(300, BUCKET_SECONDS);
		private final List<Candle> longSeries = bars(400, 3_600);
		private final List<Candle> otherShort = otherBars(300, BUCKET_SECONDS);
		private final List<Candle> otherLong = otherBars(400, 3_600);

		FakeMarket(PluginStorage storage)
		{
			super(null, new Gson(), storage);
		}

		@Override
		public MarketSnapshot getSnapshot()
		{
			return snapshot;
		}

		@Override
		public List<Candle> getSeries(int itemId, String timestep)
		{
			if (itemId == ITEM)
			{
				return "5m".equals(timestep) ? shortSeries : longSeries;
			}
			if (itemId == OTHER)
			{
				return "5m".equals(timestep) ? otherShort : otherLong;
			}
			return Collections.emptyList();
		}

		@Override
		public void prefetchSeries(List<Integer> itemIds, String timestep)
		{
		}
	}

	/** The account, as the player's inventory and Grand Exchange slots would report it. */
	private static final class FakeAccount extends AccountMonitor
	{
		private int freeSlots = 8;
		private long coins = COINS;
		private long committed;
		private final Map<Integer, Integer> inventory = new HashMap<>();

		FakeAccount()
		{
			super(null, null, null);
		}

		@Override
		public AccountState getState()
		{
			Map<Integer, Integer> holdings = new HashMap<>(inventory);
			return new AccountState(true, true, false, coins, 0, 0, freeSlots, 8, committed, true,
				holdings, new HashMap<>(inventory), new HashMap<>());
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

	private PluginStorage storage;

	@Before
	public void setUp() throws Exception
	{
		Path root = folder.newFolder("session").toPath();
		storage = TestStorage.rootedAt(root, "player");

		positions = new PositionBook(storage);
		BuyLimitTracker buyLimits = new BuyLimitTracker(storage);
		journal = new TradeJournal(storage, null, new AccountMonitor(null, null, null));
		TradePlans plans = new TradePlans(storage);
		offers = new OfferTracker(storage, positions, buyLimits, journal, tax, plans);

		account = new FakeAccount();
		FakeMarket marketData = new FakeMarket(storage);
		offers.setMarket(marketData.getSnapshot());

		FeatureEngine features = new FeatureEngine();
		FillModel fillModel = new FillModel();

		engine = new SuggestionEngine(marketData, features, new ManipulationFilter(),
			new Scorer(tax, fillModel, new PositionSizer(), new Calibrator()), new Explainer(),
			new Calibrator(), tax, account, buyLimits, positions, offers,
			new SellTimingEngine(fillModel, tax), plans, config(), new SkipList(storage),
			new ArbitrageRegistry());

		journal.startSession();
	}

	// ------------------------------------------------------------------ the exchange

	private static GrandExchangeOffer offer(GrandExchangeOfferState state, boolean buying, int price,
		int total, int filled)
	{
		GrandExchangeOffer o = Mockito.mock(GrandExchangeOffer.class);
		Mockito.when(o.getState()).thenReturn(state);
		Mockito.when(o.getItemId()).thenReturn(ITEM);
		Mockito.when(o.getPrice()).thenReturn(price);
		Mockito.when(o.getTotalQuantity()).thenReturn(total);
		Mockito.when(o.getQuantitySold()).thenReturn(filled);
		// Pre-tax on both sides, which is what the game reports.
		Mockito.when(o.getSpent()).thenReturn(price * filled);
		return o;
	}

	/** The player places the offer the plugin asked for. */
	private void place(int slot, boolean buying, int price, int quantity)
	{
		account.freeSlots--;
		if (buying)
		{
			account.committed += (long) price * quantity;
			account.coins -= (long) price * quantity;
		}
		else
		{
			account.inventory.remove(ITEM);
		}
		offers.onOfferChanged(slot, offer(
			buying ? GrandExchangeOfferState.BUYING : GrandExchangeOfferState.SELLING,
			buying, price, quantity, 0));
	}

	/** The player lists the second item for sale. */
	private void placeOther(int slot, int price, int quantity)
	{
		account.freeSlots--;
		account.inventory.remove(OTHER);
		GrandExchangeOffer o = Mockito.mock(GrandExchangeOffer.class);
		Mockito.when(o.getState()).thenReturn(GrandExchangeOfferState.SELLING);
		Mockito.when(o.getItemId()).thenReturn(OTHER);
		Mockito.when(o.getPrice()).thenReturn(price);
		Mockito.when(o.getTotalQuantity()).thenReturn(quantity);
		Mockito.when(o.getQuantitySold()).thenReturn(0);
		Mockito.when(o.getSpent()).thenReturn(0);
		offers.onOfferChanged(slot, o);
	}

	/** The market fills it. */
	private void fill(int slot, boolean buying, int price, int quantity)
	{
		offers.onOfferChanged(slot, offer(
			buying ? GrandExchangeOfferState.BOUGHT : GrandExchangeOfferState.SOLD,
			buying, price, quantity, quantity));
	}

	/** The player collects, which frees the slot and moves the goods or the coins. */
	private void collect(int slot, boolean buying, int price, int quantity)
	{
		offers.onOfferChanged(slot, offer(GrandExchangeOfferState.EMPTY, buying, price, quantity,
			quantity));
		account.freeSlots++;
		if (buying)
		{
			account.committed -= (long) price * quantity;
			account.inventory.merge(ITEM, quantity, Integer::sum);
		}
		else
		{
			account.coins += tax.netProceeds(ITEM, price, quantity);
		}
	}

	// ------------------------------------------------------------------ the flip

	@Test
	public void aPlayerCanCompleteAWholeFlipByFollowingTheAdvice()
	{
		// ---- 1. it has to have something to say -------------------------------------------------
		Suggestion buy = engine.refresh(true);
		assertNotNull(buy);
		assertEquals("a busy item with a six-percent spread has to produce a trade, not a shrug: "
			+ buy.getHeadline() + " -- " + buy.getDetail(), SuggestionType.BUY, buy.getType());
		assertEquals(ITEM, buy.getItemId());

		int buyPrice = buy.getPrice();
		int quantity = buy.getQuantity();
		int target = buy.getTargetSellPrice();

		assertTrue("the price has to be somewhere a buyer could actually sit: " + buyPrice,
			buyPrice >= BID - 20 && buyPrice <= BID + 20);
		assertTrue("and the order has to be worth placing", quantity > 0);
		assertTrue("with an exit above the entry: " + target + " against " + buyPrice,
			target > buyPrice);
		assertEquals("the profit on the card is the after-tax margin on what it tells you to buy",
			tax.netProfit(ITEM, buyPrice, target, quantity), buy.getExpectedProfit());
		assertTrue("which has to clear the player's own minimum",
			buy.getExpectedProfit() >= config().minProfitPerFlip());

		// ---- 2. place it, and the advice must not repeat itself ---------------------------------
		place(0, true, buyPrice, quantity);
		Suggestion afterPlacing = engine.refresh(true);
		assertTrue("an offer already on the board must not be suggested again: "
				+ afterPlacing.getType() + " " + afterPlacing.getHeadline(),
			afterPlacing.getType() != SuggestionType.BUY
				|| afterPlacing.getItemId() != ITEM);

		// ---- 3. it fills, and collecting comes first --------------------------------------------
		fill(0, true, buyPrice, quantity);
		Suggestion afterFilling = engine.refresh(true);
		assertEquals("a finished offer is holding a slot, so collecting outranks everything else",
			SuggestionType.COLLECT, afterFilling.getType());
		assertEquals(ITEM, afterFilling.getItemId());

		collect(0, true, buyPrice, quantity);
		Position held = positions.get(ITEM);
		assertNotNull("the purchase has to reach the position book", held);
		assertEquals(quantity, held.getQuantity());
		assertEquals("at what was actually paid", buyPrice, held.getAverageCost());
		assertEquals("carrying the exit the engine planned when it chose the trade",
			target, held.getTargetSellPrice());
		assertTrue("and a stop under it", held.getStopPrice() > 0 && held.getStopPrice() < buyPrice);

		// ---- 4. it is in the inventory, so it is told to be sold ---------------------------------
		Suggestion sell = engine.refresh(true);
		assertEquals("an item sitting in the inventory is a sale waiting to happen: "
			+ sell.getHeadline(), SuggestionType.SELL, sell.getType());
		assertEquals(ITEM, sell.getItemId());
		assertEquals("all of it", quantity, sell.getQuantity());

		int sellPrice = sell.getPrice();
		assertTrue("the ask has to be near the top of the book, not given away: " + sellPrice,
			sellPrice >= ASK - Math.round(ASK * 0.011) && sellPrice <= ASK + Math.round(ASK * 0.003));
		assertEquals("and the figure on the card is the after-tax profit on the whole position",
			tax.netProfit(ITEM, buyPrice, sellPrice, quantity), sell.getExpectedProfit());

		// ---- 5. place the sale; it must not be asked for twice -----------------------------------
		place(1, false, sellPrice, quantity);
		Suggestion afterListing = engine.refresh(true);
		assertTrue("a position already on the market is not a second sale: "
				+ afterListing.getType() + " " + afterListing.getHeadline(),
			afterListing.getType() != SuggestionType.SELL);

		// ---- 6. it sells, and the ledger has to be right ----------------------------------------
		fill(1, false, sellPrice, quantity);

		List<FlipRecord> history = journal.getHistory();
		assertEquals("one flip, however many instalments it filled in", 1, history.size());
		FlipRecord record = history.get(0);

		long expectedTax = tax.taxFor(ITEM, sellPrice, quantity);
		long expectedProfit = (long) (sellPrice - buyPrice) * quantity - expectedTax;

		assertEquals(quantity, record.getQuantity());
		assertEquals(buyPrice, record.getBuyPrice());
		assertEquals(sellPrice, record.getSellPrice());
		assertEquals("the sale tax is what the game charges", expectedTax, record.getTax());
		assertEquals("and the profit has the tax taken out of it", expectedProfit, record.getProfit());
		assertTrue("which is strictly less than the gross margin", record.getProfit()
			< (long) (sellPrice - buyPrice) * quantity);

		// ---- 7. and the panel's numbers are that ledger ------------------------------------------
		SessionStats stats = journal.sessionStats();
		assertEquals(1, stats.getFlips());
		assertEquals(1, stats.getWins());
		assertEquals("the Profit row is the journal, not a gross figure",
			expectedProfit, stats.getProfit());
		assertEquals("and the Tax paid row is the tax that was actually deducted",
			expectedTax, stats.getTaxPaid());

		collect(1, false, sellPrice, quantity);
		assertTrue("the position is closed once the coins are in hand",
			positions.get(ITEM) == null || positions.get(ITEM).getQuantity() == 0);
	}

	@Test
	public void theCoinsTheLedgerClaimsAreTheCoinsTheAccountEndsWith()
	{
		// The end-to-end arithmetic check. The account fixture moves coins exactly as the exchange
		// does -- full price out on a buy, proceeds less tax back on a sale -- so the change in the
		// coin balance across a whole flip has to equal the profit the journal recorded. If the tax
		// is being booked wrongly, these two disagree by exactly the fee.
		long before = account.getState().getInventoryCoins();

		Suggestion buy = engine.refresh(true);
		int buyPrice = buy.getPrice();
		int quantity = buy.getQuantity();
		place(0, true, buyPrice, quantity);
		fill(0, true, buyPrice, quantity);
		collect(0, true, buyPrice, quantity);

		Suggestion sell = engine.refresh(true);
		int sellPrice = sell.getPrice();
		place(1, false, sellPrice, quantity);
		fill(1, false, sellPrice, quantity);
		collect(1, false, sellPrice, quantity);

		long after = account.getState().getInventoryCoins();
		assertEquals("the journal has to agree with the coin pouch to the gp",
			after - before, journal.sessionStats().getProfit());
	}

	/**
	 * The round trip the commit message called unfinished: "still needs order modification fixed".
	 * <p>
	 * The Grand Exchange cannot change the price of a running offer, so repricing one is three
	 * separate actions -- cancel, collect, place again -- and the first two destroy every trace of
	 * why. The engine issued the cancel and then had nothing further to say about the item, so the
	 * player abandoned a working offer and got, at best, whatever the planner happened to rank top
	 * next. This walks the whole sequence.
	 */
	@Test
	public void repricingAnOfferActuallyEndsWithTheOfferRepriced() throws Exception
	{
		// An offer placed half an hour ago at 1,900, while sellers have moved to 2,000. It is being
		// skipped over and will sit there indefinitely. Written to disk rather than placed, because
		// the engine will not nag about an offer the player has not had time to look at, and that
		// clock starts when the offer is first seen.
		plantStaleBuyOffer();

		// ---- 1. it notices, and says what the game can actually do -------------------------------
		Suggestion reprice = engine.refresh(true);
		assertEquals("an offer nobody can reach has to be called out", SuggestionType.MODIFY_BUY,
			reprice.getType());
		assertEquals(ITEM, reprice.getItemId());
		assertTrue("at a price that is actually competitive: " + reprice.getPrice(),
			reprice.getPrice() > 1_900);
		assertTrue("and the instruction has to be one the Grand Exchange supports -- there is no "
				+ "way to edit a running offer: " + reprice.getDetail(),
			reprice.getDetail().contains("cancel this one"));

		// ---- 2. the player cancels, and collecting comes next ------------------------------------
		offers.onOfferChanged(0, offer(GrandExchangeOfferState.CANCELLED_BUY, true, 1_900, 5_000, 0));
		Suggestion afterCancel = engine.refresh(true);
		assertEquals("a cancelled offer still holds its slot until it is collected",
			SuggestionType.COLLECT, afterCancel.getType());

		offers.onOfferChanged(0, offer(GrandExchangeOfferState.EMPTY, true, 1_900, 5_000, 0));
		account.freeSlots = 8;
		account.committed = 0;

		// ---- 3. and the replacement is actually placed -------------------------------------------
		Suggestion replacement = engine.refresh(true);
		assertEquals("the whole point of cancelling was to put the offer back in higher",
			SuggestionType.BUY, replacement.getType());
		assertEquals(ITEM, replacement.getItemId());
		assertTrue("at the price worked out from the market as it is now, not the stale one: "
				+ replacement.getPrice(), replacement.getPrice() > 1_900);
		assertEquals("and for what the cancelled offer had left to buy. Without the intent carried "
				+ "across the cancel, the engine simply screened the market again and came back with "
				+ "a 14,969-unit order it had chosen from scratch -- a different trade wearing the "
				+ "same item's name.", 5_000, replacement.getQuantity());
		assertTrue("and it says what it is: " + replacement.getHeadline(),
			replacement.getHeadline().contains("again")
				|| replacement.getDetail().contains("replacement"));
	}

	/**
	 * A holding and a mispriced offer at the same time. The sale goes first, and the modification is
	 * still there afterwards.
	 * <p>
	 * Both halves matter and they pull against each other. Sells rank above modifications, so a
	 * reprice that is ready has to give way -- and the complaint about this plugin is that things
	 * which give way never come back. Deferred is not dropped, and this is where the difference gets
	 * checked.
	 */
	@Test
	public void aSaleGoesFirstAndTheModificationIsNotLost() throws Exception
	{
		// A buy offer placed half an hour ago at 1,900 while sellers moved to 2,000: it is being
		// skipped over and wants repricing.
		plantStaleBuyOffer();
		// And, at the same time, a holding that has never been listed. A different item on purpose:
		// a holding of the very thing we are still buying is one accumulation, and the engine will
		// not list it until that buy stops -- correctly, but it would make this an ordering test that
		// passes for the wrong reason.
		positions.recordBuy(OTHER, OTHER_NAME, 800, 800L * OTHER_BID, Instant.now());
		account.inventory.put(OTHER, 800);

		Suggestion first = engine.refresh(true);
		assertEquals("the sale outranks the housekeeping: " + first.getHeadline(),
			SuggestionType.SELL, first.getType());
		assertEquals(OTHER, first.getItemId());

		// Place it. Now nothing is held unlisted, so the modification is what is left.
		placeOther(2, first.getPrice(), first.getQuantity());
		Suggestion second = engine.refresh(true);
		assertEquals("and the reprice is still waiting, not discarded: " + second.getHeadline(),
			SuggestionType.MODIFY_BUY, second.getType());
		assertEquals(ITEM, second.getItemId());
	}

	/**
	 * The sequence the player described: they act on a reprice, and the plugin forgets the trade.
	 * <p>
	 * Every step here is one the player actually performs, and after each one the plugin has to name
	 * the next one rather than moving on to something unrelated. The step that used to be missing is
	 * the last: the offer was cancelled on the plugin's instruction and never replaced.
	 */
	@Test
	public void everyStepOfAModificationIsNamedInTurn() throws Exception
	{
		plantStaleBuyOffer();

		assertEquals("first, that the offer is unreachable", SuggestionType.MODIFY_BUY,
			engine.refresh(true).getType());

		// The player clicks abort. The offer still holds its slot, and its contents are waiting.
		offers.onOfferChanged(0, offer(GrandExchangeOfferState.CANCELLED_BUY, true, 1_900, 5_000, 0));
		assertEquals("then, that it has to be collected", SuggestionType.COLLECT,
			engine.refresh(true).getType());

		offers.onOfferChanged(0, offer(GrandExchangeOfferState.EMPTY, true, 1_900, 5_000, 0));
		account.freeSlots = 8;
		account.committed = 0;

		Suggestion replacement = engine.refresh(true);
		assertEquals("and then, that the replacement goes in", SuggestionType.BUY,
			replacement.getType());
		assertEquals(ITEM, replacement.getItemId());
		assertEquals("for what the cancelled order had left to buy", 5_000,
			replacement.getQuantity());
	}

	/**
	 * A modification the player has not finished is held steady, and released the moment they are
	 * done with it.
	 * <p>
	 * The plugin pins the card while the offer editor is open on that item, so it cannot change under
	 * someone who is typing into it. That pin is returned ahead of every other decision, so a pin
	 * that is never released does not merely delay the next recommendation -- it drops all of them,
	 * for as long as it stands. It used to be released only when the whole Grand Exchange window was
	 * closed, and cancelling an offer closes the editor while leaving the Exchange open.
	 */
	@Test
	public void aHeldCardIsReleasedRatherThanWedgingTheEngine() throws Exception
	{
		plantStaleBuyOffer();
		Suggestion reprice = engine.refresh(true);
		assertEquals(SuggestionType.MODIFY_BUY, reprice.getType());

		engine.setPendingAdjustment(reprice);
		assertTrue("while it is pinned, it is what the plugin says",
			engine.refresh(true).sameAs(reprice));

		engine.setPendingAdjustment(null);
		offers.onOfferChanged(0, offer(GrandExchangeOfferState.CANCELLED_BUY, true, 1_900, 5_000, 0));
		assertEquals("and once released, the chain runs on", SuggestionType.COLLECT,
			engine.refresh(true).getType());
	}

	/** A buy placed half an hour ago at a price the market has left behind. */
	private void plantStaleBuyOffer() throws Exception
	{
		long halfAnHourAgo = Instant.now().getEpochSecond() - 1_800;
		java.nio.file.Files.write(storage.accountDir().resolve("offers.json"),
			("[{\"slot\":0,\"itemId\":" + ITEM + ",\"itemName\":\"" + ITEM_NAME + "\","
				+ "\"buying\":true,\"price\":1900,\"totalQuantity\":5000,"
				+ "\"quantityFilled\":0,\"recordedQuantity\":0,\"recordedSpent\":0,"
				+ "\"spent\":0,\"state\":\"BUYING\",\"firstSeen\":" + halfAnHourAgo + ","
				+ "\"lastChanged\":" + halfAnHourAgo + ",\"journalled\":false,"
				+ "\"collected\":false}]").getBytes(java.nio.charset.StandardCharsets.UTF_8));
		offers.load();
		account.freeSlots = 7;
		account.committed = 1_900L * 5_000;
	}

	@Test
	public void aHoldingIsNotStrandedWhenItsPriceArrives()
	{
		// The complaint that things were bought and then never sold. The position reaches its target
		// and the engine has to act on it -- not hold it for the six-hour horizon limit because the
		// exit is worth less than an entry-sized bar.
		Suggestion buy = engine.refresh(true);
		place(0, true, buy.getPrice(), buy.getQuantity());
		fill(0, true, buy.getPrice(), buy.getQuantity());
		collect(0, true, buy.getPrice(), buy.getQuantity());

		Suggestion sell = engine.refresh(true);
		assertEquals("a holding whose price is there is sold, not sat on", SuggestionType.SELL,
			sell.getType());
		assertTrue("and it is a sale rather than a loss cut", !sell.isLossCut());
	}
}
