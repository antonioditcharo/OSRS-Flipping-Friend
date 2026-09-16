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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
 * The same board has to produce the same answer, and an answer that gives way has to come back.
 * <p>
 * Reported from a live session: modification recommendations "flash for a brief moment before
 * another item sells", reading as advice being dropped, lost, or blocked by something else. A card
 * that appears and vanishes is indistinguishable, from the outside, from one that was never meant to
 * be there — so the thing to establish is which of the two it is.
 * <p>
 * Two properties decide it. <b>Stability:</b> nothing about the board changed between two refreshes,
 * so nothing about the advice may change either; if it does, the engine is choosing arbitrarily
 * somewhere and the player is watching it flicker. <b>Continuation:</b> a modification that is
 * legitimately outranked — by a collect or a sale, which are worth more — has to be waiting when the
 * work that displaced it is done, rather than having been consumed by being shown once.
 */
public class SuggestionChurnTest
{
	private static final int GRAPES = 1987;
	private static final String GRAPES_NAME = "Grapes";
	private static final int BARS = 2361;
	private static final String BARS_NAME = "Adamant bar";
	private static final int LOGS = 1517;
	private static final String LOGS_NAME = "Maple logs";
	/** The live case: a high-value item where 49 units is hundreds of thousands of gp of margin. */
	private static final int ORB = 29_796;
	private static final String ORB_NAME = "Awakener's orb";
	private static final int BUCKET_SECONDS = 300;

	@Rule
	public final TemporaryFolder folder = new TemporaryFolder();

	private final TaxCalculator tax = new TaxCalculator();
	private SuggestionEngine engine;
	private OfferTracker offers;
	private PositionBook positions;
	private FakeAccount account;
	private FakeMarket marketData;
	private PluginStorage storage;

	// ------------------------------------------------------------------ fixtures

	private static List<Candle> bars(int bid, int ask, int count, int stepSeconds)
	{
		List<Candle> series = new ArrayList<>(count);
		long now = Instant.now().getEpochSecond();
		for (int i = 0; i < count; i++)
		{
			int drift = (int) Math.round(bid * 0.01 * Math.sin(i * 0.37));
			series.add(new Candle(now - (long) (count - i) * stepSeconds,
				ask + drift, bid + drift, 4_000, 4_000));
		}
		return series;
	}

	/** Three items, so the engine has more than one thing it could plausibly say. */
	private final class FakeMarket extends MarketDataService
	{
		private final Map<Integer, int[]> quotes = new HashMap<>();

		FakeMarket(PluginStorage storage)
		{
			super(null, new Gson(), storage);
			quotes.put(GRAPES, new int[]{400, 424});
			quotes.put(BARS, new int[]{2_000, 2_120});
			quotes.put(LOGS, new int[]{95, 101});
			quotes.put(ORB, new int[]{282_023, 299_999});
		}

		void moveTo(int itemId, int bid, int ask)
		{
			quotes.put(itemId, new int[]{bid, ask});
		}

		@Override
		public MarketSnapshot getSnapshot()
		{
			long now = Instant.now().getEpochSecond();
			Map<Integer, ItemMetadata> metadata = new HashMap<>();
			metadata.put(GRAPES, new ItemMetadata(GRAPES, GRAPES_NAME, false, 13_000, 100));
			metadata.put(BARS, new ItemMetadata(BARS, BARS_NAME, false, 30_000, 1_000));
			metadata.put(LOGS, new ItemMetadata(LOGS, LOGS_NAME, false, 30_000, 50));
			metadata.put(ORB, new ItemMetadata(ORB, ORB_NAME, false, 50, 100_000));

			Map<Integer, LatestPrice> latest = new HashMap<>();
			Map<Integer, Candle> five = new HashMap<>();
			Map<Integer, Candle> hourly = new HashMap<>();
			for (Map.Entry<Integer, int[]> entry : quotes.entrySet())
			{
				int bid = entry.getValue()[0];
				int ask = entry.getValue()[1];
				latest.put(entry.getKey(), new LatestPrice(ask, now - 30, bid, now - 30));
				five.put(entry.getKey(), new Candle(now, ask, bid, 4_000, 4_000));
				hourly.put(entry.getKey(), new Candle(now, ask, bid, 48_000, 48_000));
			}
			return new MarketSnapshot(metadata, latest, five, hourly, Instant.now());
		}

		@Override
		public List<Candle> getSeries(int itemId, String timestep)
		{
			int[] quote = quotes.get(itemId);
			if (quote == null)
			{
				return Collections.emptyList();
			}
			int step = "5m".equals(timestep) ? BUCKET_SECONDS : 3_600;
			int count = "5m".equals(timestep) ? 300 : 400;
			return bars(quote[0], quote[1], count, step);
		}

		@Override
		public void prefetchSeries(List<Integer> itemIds, String timestep)
		{
		}
	}

	private static final class FakeAccount extends AccountMonitor
	{
		private int freeSlots = 4;
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
			return new AccountState(true, true, false, coins, 0, 0, freeSlots, 8, committed, true,
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
		Path root = folder.newFolder("churn").toPath();
		storage = TestStorage.rootedAt(root, "player");

		positions = new PositionBook(storage);
		BuyLimitTracker buyLimits = new BuyLimitTracker(storage);
		TradeJournal journal = new TradeJournal(storage, null, new AccountMonitor(null, null, null));
		TradePlans plans = new TradePlans(storage);
		offers = new OfferTracker(storage, positions, buyLimits, journal, tax, plans);

		account = new FakeAccount();
		marketData = new FakeMarket(storage);
		offers.setMarket(marketData.getSnapshot());

		FillModel fillModel = new FillModel();

		engine = new SuggestionEngine(marketData, new FeatureEngine(), new ManipulationFilter(),
			new Scorer(tax, fillModel, new PositionSizer(), new Calibrator()), new Explainer(),
			new Calibrator(), tax, account, buyLimits, positions, offers,
			new SellTimingEngine(fillModel, tax), plans, config(), new SkipList(storage),
			new ArbitrageRegistry());

		journal.startSession();
	}

	// ------------------------------------------------------------------ the board

	/** Writes a set of aged offers straight to disk, which is the only way to age one. */
	private void plant(String... offersJson) throws Exception
	{
		java.nio.file.Files.write(storage.accountDir().resolve("offers.json"),
			("[" + String.join(",", offersJson) + "]")
				.getBytes(java.nio.charset.StandardCharsets.UTF_8));
		offers.load();
	}

	private static String offerJson(int slot, int itemId, String name, boolean buying, int price,
		int quantity, int filled, long minutesOld)
	{
		long placed = Instant.now().getEpochSecond() - minutesOld * 60;
		return "{\"slot\":" + slot + ",\"itemId\":" + itemId + ",\"itemName\":\"" + name + "\","
			+ "\"buying\":" + buying + ",\"price\":" + price + ",\"totalQuantity\":" + quantity
			+ ",\"quantityFilled\":" + filled + ",\"recordedQuantity\":" + filled
			+ ",\"recordedSpent\":0,\"spent\":0,\"state\":\"" + (buying ? "BUYING" : "SELLING")
			+ "\",\"firstSeen\":" + placed + ",\"lastChanged\":" + placed
			+ ",\"journalled\":false,\"collected\":false}";
	}

	private static GrandExchangeOffer offer(int itemId, GrandExchangeOfferState state, int price,
		int total, int filled)
	{
		GrandExchangeOffer o = Mockito.mock(GrandExchangeOffer.class);
		Mockito.when(o.getState()).thenReturn(state);
		Mockito.when(o.getItemId()).thenReturn(itemId);
		Mockito.when(o.getPrice()).thenReturn(price);
		Mockito.when(o.getTotalQuantity()).thenReturn(total);
		Mockito.when(o.getQuantitySold()).thenReturn(filled);
		Mockito.when(o.getSpent()).thenReturn(price * filled);
		return o;
	}

	private static String describe(Suggestion suggestion)
	{
		return suggestion == null ? "null"
			: suggestion.getType() + ":" + suggestion.getItemId() + "@" + suggestion.getPrice()
				+ "x" + suggestion.getQuantity();
	}

	// ------------------------------------------------------------------ stability

	@Test
	public void thesameBoardGivesTheSameAnswerEveryTime() throws Exception
	{
		// Two buy offers, both stranded below a market that has moved, so both qualify for a
		// reprice. Only one can be shown, and which one must not depend on the order a hash map
		// happens to iterate in -- that order changes as offers come and go, and the card would
		// change with it for no reason the player could see.
		plant(offerJson(0, GRAPES, GRAPES_NAME, true, 380, 5_000, 0, 90),
			offerJson(1, BARS, BARS_NAME, true, 1_900, 500, 0, 90),
			offerJson(2, LOGS, LOGS_NAME, true, 88, 10_000, 0, 90));
		account.freeSlots = 1;

		Set<String> answers = new LinkedHashSet<>();
		for (int i = 0; i < 12; i++)
		{
			answers.add(describe(engine.refresh(true)));
		}

		assertEquals("nothing changed between these refreshes, so the advice must not either: "
			+ answers, 1, answers.size());
	}

	@Test
	public void anUnrelatedOfferAppearingDoesNotMoveTheAdvice() throws Exception
	{
		// The specific way a hash map bites: adding or removing an entry can change the iteration
		// order of the ones already there. An offer settling in a slot the advice has nothing to do
		// with must not hand the card to a different item.
		plant(offerJson(0, GRAPES, GRAPES_NAME, true, 380, 5_000, 0, 90),
			offerJson(1, BARS, BARS_NAME, true, 1_900, 500, 0, 90));
		account.freeSlots = 2;

		String before = describe(engine.refresh(true));

		// A third, perfectly healthy offer arrives in another slot.
		offers.onOfferChanged(3, offer(LOGS, GrandExchangeOfferState.BUYING, 100, 100, 0));
		account.freeSlots = 1;

		String after = describe(engine.refresh(true));

		assertEquals("an unrelated slot changing must not move the recommendation: "
			+ before + " became " + after, before, after);
	}

	@Test
	public void theOfferChosenForRepricingIsTheWorstOne() throws Exception
	{
		// If only one can be shown, it should be the one costing the most to leave alone, not
		// whichever came first. Grapes is stranded by a fifth; the bars by a fraction of a percent.
		plant(offerJson(0, BARS, BARS_NAME, true, 2_100, 500, 0, 90),
			offerJson(1, GRAPES, GRAPES_NAME, true, 320, 5_000, 0, 90));
		marketData.moveTo(GRAPES, 400, 460);
		marketData.moveTo(BARS, 2_101, 2_400);
		offers.setMarket(marketData.getSnapshot());
		account.freeSlots = 2;

		Suggestion suggestion = engine.refresh(true);

		assertNotNull(suggestion);
		assertTrue("the offer furthest from the market is the one worth fixing first: "
			+ describe(suggestion), suggestion.getItemId() == GRAPES);
	}

	@Test
	public void aOneGpWobbleDoesNotTurnTheAdviceOnAndOff() throws Exception
	{
		// The flash, reproduced. "Outbid" is a strict comparison against a live quote, and a quote
		// that drifts by a gp either side of the offer price crosses it repeatedly -- so the card
		// appears, disappears, and appears again while nothing meaningful has happened. From the
		// player's side that is indistinguishable from advice being dropped, which is exactly how it
		// was reported.
		plant(offerJson(0, GRAPES, GRAPES_NAME, true, 400, 5_000, 0, 90));
		account.freeSlots = 2;

		List<String> seen = new ArrayList<>();
		for (int i = 0; i < 8; i++)
		{
			// A gp either side of the offer: noise, on an item quoted in hundreds.
			marketData.moveTo(GRAPES, i % 2 == 0 ? 401 : 399, 460);
			offers.setMarket(marketData.getSnapshot());
			seen.add(describe(engine.refresh(true)));
		}

		int flips = 0;
		for (int i = 1; i < seen.size(); i++)
		{
			if (!seen.get(i).equals(seen.get(i - 1)))
			{
				flips++;
			}
		}
		assertTrue("a one-gp wobble must not flip the recommendation back and forth: " + seen,
			flips <= 1);
	}

	@Test
	public void aDriftingMarketDoesNotRewriteTheAdviceEveryCycle() throws Exception
	{
		// The realistic version. The live feed republishes every minute and the quote moves a little
		// each time; none of that is news. Over twenty refreshes of an unchanged board the advice
		// should settle on something and stay there, not be rewritten on each new quote.
		plant(offerJson(0, GRAPES, GRAPES_NAME, true, 380, 5_000, 0, 90));
		positions.recordBuy(BARS, BARS_NAME, 100, 100L * 2_000, Instant.now());
		account.inventory.put(BARS, 100);
		account.freeSlots = 2;

		java.util.Random noise = new java.util.Random(20260913L);
		List<String> seen = new ArrayList<>();
		for (int i = 0; i < 20; i++)
		{
			// A third of a percent either way, on an item quoted with a six percent spread.
			int wobble = noise.nextInt(3) - 1;
			marketData.moveTo(GRAPES, 400 + wobble, 424 + wobble);
			marketData.moveTo(BARS, 2_000 + wobble * 6, 2_120 + wobble * 6);
			offers.setMarket(marketData.getSnapshot());
			seen.add(describe(engine.refresh(true)));
		}

		int changes = 0;
		for (int i = 1; i < seen.size(); i++)
		{
			if (!seen.get(i).equals(seen.get(i - 1)))
			{
				changes++;
			}
		}
		assertTrue("advice rewritten " + changes + " times in twenty refreshes of a board nobody "
			+ "touched: " + new LinkedHashSet<>(seen), changes <= 2);
	}

	@Test
	public void aRepriceKeepsNamingTheSamePriceWhileYouActOnIt() throws Exception
	{
		// A reprice tells the player a number to type. If the next refresh names a different one,
		// they are being asked to start again -- and they will usually see it happen halfway
		// through, because cancelling and re-placing an offer takes longer than a refresh cycle.
		plant(offerJson(0, GRAPES, GRAPES_NAME, true, 360, 5_000, 0, 90));
		account.freeSlots = 2;

		Set<Integer> prices = new LinkedHashSet<>();
		java.util.Random noise = new java.util.Random(13L);
		for (int i = 0; i < 10; i++)
		{
			int wobble = noise.nextInt(5) - 2;
			marketData.moveTo(GRAPES, 400 + wobble, 424 + wobble);
			offers.setMarket(marketData.getSnapshot());
			Suggestion suggestion = engine.refresh(true);
			assertEquals("the board did not change, so the instruction must not: "
				+ describe(suggestion), SuggestionType.MODIFY_BUY, suggestion.getType());
			prices.add(suggestion.getPrice());
		}

		assertEquals("the price on the card has to hold still while the player types it: " + prices,
			1, prices.size());
	}

	// ------------------------------------------------------------------ the promise on the card

	/**
	 * Walks the sequence off a live screenshot: a 50-unit Awakener's orb buy with one filled, told to
	 * reprice, cancelled exactly as instructed, and then told to sell the single orb.
	 * <p>
	 * The card had promised, in those words, "you will be told to place the replacement at 282,024 gp
	 * straight afterwards". Doing as it said replaced a 586,800 gp plan with a 13,600 gp one.
	 */
	@Test
	public void cancellingToRepriceLeadsToTheReplacementAndNotToASale() throws Exception
	{
		// A buy stranded below the market with part of it already filled.
		plant(offerJson(0, ORB, ORB_NAME, true, 280_440, 50, 1, 90));
		positions.recordBuy(ORB, ORB_NAME, 1, 280_440L, Instant.now());
		account.coins = 30_000_000L;
		account.freeSlots = 0;

		Suggestion reprice = engine.refresh(true);
		assertEquals("the board starts on the reprice: " + describe(reprice),
			SuggestionType.MODIFY_BUY, reprice.getType());
		assertEquals(ORB, reprice.getItemId());

		// The player does exactly what the card said: cancels, and collects what it bought.
		offers.onOfferChanged(0, offer(ORB, GrandExchangeOfferState.CANCELLED_BUY, 280_440, 50, 1));
		offers.onOfferChanged(0, offer(ORB, GrandExchangeOfferState.EMPTY, 280_440, 50, 1));
		account.inventory.put(ORB, 1);
		account.freeSlots = 1;

		Suggestion next = engine.refresh(true);

		assertNotNull("the plugin promised a replacement; it has to produce one", next);
		assertTrue("cancelling on instruction must not turn the part that filled into a sale: "
			+ describe(next),
			next.getType() != SuggestionType.SELL || next.getItemId() != ORB);
		assertEquals("and the promised replacement is what comes next: " + describe(next),
			SuggestionType.BUY, next.getType());
		assertEquals(ORB, next.getItemId());
	}

	@Test
	public void aFullBoardStillDoesNotDumpTheHalfThatFilled() throws Exception
	{
		// The same gap, on a board with no slot to place the replacement into. The replacement has to
		// wait -- but waiting must not mean the partial fill becomes fair game in the meantime.
		plant(offerJson(0, ORB, ORB_NAME, true, 280_440, 50, 1, 90));
		positions.recordBuy(ORB, ORB_NAME, 1, 280_440L, Instant.now());
		account.coins = 30_000_000L;
		account.freeSlots = 0;

		assertEquals(SuggestionType.MODIFY_BUY, engine.refresh(true).getType());

		offers.onOfferChanged(0, offer(ORB, GrandExchangeOfferState.CANCELLED_BUY, 280_440, 50, 1));
		offers.onOfferChanged(0, offer(ORB, GrandExchangeOfferState.EMPTY, 280_440, 50, 1));
		account.inventory.put(ORB, 1);
		// Something else took the freed slot before the player got back to the Exchange.
		offers.onOfferChanged(1, offer(BARS, GrandExchangeOfferState.BUYING, 2_000, 100, 0));
		account.freeSlots = 0;

		Suggestion next = engine.refresh(true);

		if (next != null)
		{
			assertTrue("with nowhere to re-place it, the answer is still not to sell the part that "
					+ "filled: " + describe(next),
				next.getType() != SuggestionType.SELL || next.getItemId() != ORB);
		}

		// And it must not merely be blocked from selling it by the slot count, which is the same
		// intent wearing a different hat -- the moment a slot freed it would dump the orb instead of
		// re-placing the buy.
		com.flippingfriend.model.PositionStatus status = engine.getPositionStatuses().get(ORB);
		assertNotNull(status);
		assertTrue("the plugin has to be waiting for the replacement, not queuing up a sale: "
			+ status.summary(), !status.isBlockedBySlots());
	}

	@Test
	public void theCardSaysWhatItIsWaitingFor() throws Exception
	{
		plant(offerJson(0, ORB, ORB_NAME, true, 280_440, 50, 1, 90));
		positions.recordBuy(ORB, ORB_NAME, 1, 280_440L, Instant.now());
		account.coins = 30_000_000L;
		account.freeSlots = 0;
		engine.refresh(true);

		offers.onOfferChanged(0, offer(ORB, GrandExchangeOfferState.CANCELLED_BUY, 280_440, 50, 1));
		offers.onOfferChanged(0, offer(ORB, GrandExchangeOfferState.EMPTY, 280_440, 50, 1));
		account.inventory.put(ORB, 1);
		offers.onOfferChanged(1, offer(BARS, GrandExchangeOfferState.BUYING, 2_000, 100, 0));
		account.freeSlots = 0;
		engine.refresh(true);

		com.flippingfriend.model.PositionStatus status =
			engine.getPositionStatuses().get(ORB);
		assertNotNull("a holding the plugin is deliberately leaving alone has to say so", status);
		assertTrue("a plugin that is waiting must not look like one that has forgotten: "
			+ status.summary(), status.summary().contains("replacement"));
	}

	// ------------------------------------------------------------------ continuation

	@Test
	public void aModificationOutrankedByACollectComesBackAfterwards() throws Exception
	{
		// The reported sequence. A reprice is on screen; an offer finishes elsewhere; collecting
		// outranks it, as it should. The question is what happens next -- and the answer has to be
		// that the reprice is still there, not that showing it once used it up.
		plant(offerJson(0, GRAPES, GRAPES_NAME, true, 380, 5_000, 0, 90));
		account.freeSlots = 2;

		Suggestion before = engine.refresh(true);
		assertEquals("the board starts on the reprice: " + describe(before),
			SuggestionType.MODIFY_BUY, before.getType());

		// Something else finishes and needs collecting.
		offers.onOfferChanged(1, offer(BARS, GrandExchangeOfferState.BUYING, 2_000, 100, 0));
		offers.onOfferChanged(1, offer(BARS, GrandExchangeOfferState.BOUGHT, 2_000, 100, 100));
		account.freeSlots = 1;

		Suggestion displacing = engine.refresh(true);
		assertEquals("a finished offer holds a slot, so collecting outranks housekeeping: "
			+ describe(displacing), SuggestionType.COLLECT, displacing.getType());

		// The player collects it.
		offers.onOfferChanged(1, offer(BARS, GrandExchangeOfferState.EMPTY, 2_000, 100, 100));
		account.freeSlots = 2;
		account.inventory.put(BARS, 100);

		Suggestion after = engine.refresh(true);
		assertNotNull("the reprice was deferred, not consumed", after);
		assertTrue("the reprice has to still be available once the collect is done, or it was "
				+ "dropped: " + describe(after),
			after.getType() == SuggestionType.MODIFY_BUY
				|| after.getType() == SuggestionType.SELL);
	}

	@Test
	public void aModificationSurvivesASaleTakingPriorityOverIt() throws Exception
	{
		// The same question with a sale rather than a collect, which is the ordering the player
		// asked for: sells first, then modifications. A sale is worth more than housekeeping, so it
		// wins -- but it must not take the housekeeping with it.
		plant(offerJson(0, GRAPES, GRAPES_NAME, true, 380, 5_000, 0, 90));
		positions.recordBuy(BARS, BARS_NAME, 100, 100L * 2_000, Instant.now());
		account.inventory.put(BARS, 100);
		account.freeSlots = 2;

		Suggestion sale = engine.refresh(true);
		assertEquals("an unlisted holding outranks a reprice: " + describe(sale),
			SuggestionType.SELL, sale.getType());
		assertEquals(BARS, sale.getItemId());

		// The player lists it, which is the whole of what the sale asked for.
		offers.onOfferChanged(1, offer(BARS, GrandExchangeOfferState.SELLING,
			sale.getPrice(), sale.getQuantity(), 0));
		account.inventory.remove(BARS);
		account.freeSlots = 1;

		Suggestion after = engine.refresh(true);
		assertNotNull("the reprice must not have gone anywhere", after);
		assertEquals("with the sale placed, the deferred reprice is what is left: "
			+ describe(after), SuggestionType.MODIFY_BUY, after.getType());
		assertEquals(GRAPES, after.getItemId());
	}
}
