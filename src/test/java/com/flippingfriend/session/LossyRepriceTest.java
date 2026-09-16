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
 * Nothing is marked down through what it cost, and nothing is entered at a loss.
 * <p>
 * Both halves were reported from one live session. A position entered for 90,000 gp of expected
 * profit was told, a quarter of an hour after being listed, to drop its price by nearly 200,000 —
 * and a working buy offer was repeatedly told to chase the price up into a round trip that no longer
 * made money at all.
 * <p>
 * There was a break-even floor for the first of these, written from a journal full of exactly those
 * losses, and it did nothing. Three separate reasons, each enough on its own:
 * <ul>
 *   <li>It lived in the second of two sell branches, and the first — "your ask is above the market",
 *       with no floor at all — returned before it was ever reached. That branch's condition is a
 *       strict subset of the second's, so the floor was unreachable on essentially every offer.</li>
 *   <li>Its escape hatch was "ask the sell engine, and do nothing unless it says sell". That worked
 *       while the engine could answer HOLD. Under the never-hold rule it answers sell every time, so
 *       the hatch stopped existing.</li>
 *   <li>Its only test called {@code repriceAllowed} directly. A predicate can be perfectly correct
 *       and perfectly green while nothing reachable calls it, which is what had happened — so every
 *       assertion in this file goes through {@link SuggestionEngine#refresh} instead.</li>
 * </ul>
 * <p>
 * Listing is not the same as marking down. An offer already on the market is already listed, and the
 * never-hold rule has nothing further to say about the price it sits at. Only the stop authorises a
 * loss.
 */
public class LossyRepriceTest
{
	private static final int ITEM = 1761;
	private static final String ITEM_NAME = "Soft clay";
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

	/** One item, at whatever price the test wants it to be at right now. */
	private final class FakeMarket extends MarketDataService
	{
		private int bid = 119;
		private int ask = 126;

		FakeMarket(PluginStorage storage)
		{
			super(null, new Gson(), storage);
		}

		void moveTo(int newBid, int newAsk)
		{
			bid = newBid;
			ask = newAsk;
		}

		@Override
		public MarketSnapshot getSnapshot()
		{
			long now = Instant.now().getEpochSecond();
			Map<Integer, ItemMetadata> metadata = new HashMap<>();
			metadata.put(ITEM, new ItemMetadata(ITEM, ITEM_NAME, false, 13_000, 50));
			Map<Integer, LatestPrice> latest = new HashMap<>();
			latest.put(ITEM, new LatestPrice(ask, now - 30, bid, now - 30));
			Map<Integer, Candle> five = new HashMap<>();
			five.put(ITEM, new Candle(now, ask, bid, 4_000, 4_000));
			Map<Integer, Candle> hourly = new HashMap<>();
			hourly.put(ITEM, new Candle(now, ask, bid, 48_000, 48_000));
			return new MarketSnapshot(metadata, latest, five, hourly, Instant.now());
		}

		@Override
		public List<Candle> getSeries(int itemId, String timestep)
		{
			int step = "5m".equals(timestep) ? BUCKET_SECONDS : 3_600;
			int count = "5m".equals(timestep) ? 300 : 400;
			return itemId == ITEM ? bars(bid, ask, count, step) : Collections.emptyList();
		}

		@Override
		public void prefetchSeries(List<Integer> itemIds, String timestep)
		{
		}
	}

	private static final class FakeAccount extends AccountMonitor
	{
		private int freeSlots = 6;
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

	private PluginStorage storage;

	@Before
	public void setUp() throws Exception
	{
		Path root = folder.newFolder("reprice").toPath();
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
		LstmForecasterClient noForecaster = Mockito.mock(LstmForecasterClient.class);
		Mockito.when(noForecaster.predictBulk(Mockito.any())).thenReturn(Collections.emptyMap());

		engine = new SuggestionEngine(marketData, new FeatureEngine(), new ManipulationFilter(),
			new Scorer(tax, fillModel, new PositionSizer(), new Calibrator()), new Explainer(),
			new Calibrator(), tax, account, buyLimits, positions, offers,
			new SellTimingEngine(fillModel, tax), plans, config(), new SkipList(storage),
			noForecaster, new ArbitrageRegistry());

		journal.startSession();
	}

	// ------------------------------------------------------------------ the board

	/**
	 * An offer that has been on the book for {@code minutesOld} minutes. Written to storage and
	 * reloaded, because that is the only way to age one without waiting.
	 */
	private void plantOffer(boolean buying, int price, int quantity, long minutesOld) throws Exception
	{
		long placed = Instant.now().getEpochSecond() - minutesOld * 60;
		java.nio.file.Files.write(storage.accountDir().resolve("offers.json"),
			("[{\"slot\":0,\"itemId\":" + ITEM + ",\"itemName\":\"" + ITEM_NAME + "\","
				+ "\"buying\":" + buying + ",\"price\":" + price + ",\"totalQuantity\":" + quantity
				+ ",\"quantityFilled\":0,\"recordedQuantity\":0,\"recordedSpent\":0,"
				+ "\"spent\":0,\"state\":\"" + (buying ? "BUYING" : "SELLING") + "\","
				+ "\"firstSeen\":" + placed + ",\"lastChanged\":" + placed
				+ ",\"journalled\":false,\"collected\":false}]")
				.getBytes(java.nio.charset.StandardCharsets.UTF_8));
		offers.load();
		account.freeSlots = 7;
	}

	/** Bought at {@code unitCost}, currently listed, and healthy enough not to be through its stop. */
	private void holding(int quantity, int unitCost)
	{
		positions.recordBuy(ITEM, ITEM_NAME, quantity, (long) quantity * unitCost, Instant.now());
	}

	// ------------------------------------------------------------------ the sell side

	@Test
	public void aSaleIsNeverRepricedBelowWhatItCost() throws Exception
	{
		// The journal entry this is drawn from: Soft clay bought at 119, repriced to 116, twice,
		// for a realised loss of 25,540 gp. Break-even after tax is above 119, so 116 is a loss on
		// every unit and the engine had no business naming that price.
		holding(9_000, 119);
		plantOffer(false, 130, 9_000, 90);
		marketData.moveTo(112, 116);
		offers.setMarket(marketData.getSnapshot());

		Suggestion suggestion = engine.refresh(true);

		if (suggestion != null && suggestion.getType() == SuggestionType.MODIFY_SELL)
		{
			int breakEven = tax.breakEvenSellPrice(ITEM, 119);
			assertTrue("repriced to " + suggestion.getPrice() + " against a break-even of "
				+ breakEven + " -- that is a decision to take a loss, and only the stop makes it",
				suggestion.getPrice() >= breakEven);
		}
	}

	@Test
	public void aHealthySaleIsLeftAloneRatherThanChasedIntoALoss() throws Exception
	{
		// Already listed at break-even with the market below it. There is no move available that is
		// not a loss, the position is nowhere near its stop, and the honest answer is to do nothing:
		// the holding is on the market, which is all the never-hold rule ever asked for.
		holding(9_000, 119);
		int breakEven = tax.breakEvenSellPrice(ITEM, 119);
		plantOffer(false, breakEven, 9_000, 90);
		marketData.moveTo(114, 118);
		offers.setMarket(marketData.getSnapshot());

		Suggestion suggestion = engine.refresh(true);

		if (suggestion != null)
		{
			assertTrue("nothing here justifies touching the offer: " + suggestion.getType() + " "
					+ suggestion.getHeadline(),
				suggestion.getType() != SuggestionType.MODIFY_SELL);
		}
	}

	@Test
	public void aFreshSaleIsGivenTimeBeforeItsPriceIsQuestioned() throws Exception
	{
		// The reported case, in its own units: fifteen minutes into a leg the plan expected to take
		// two hours. Being unfilled after an eighth of the predicted time is not evidence that the
		// price is wrong, and the only thing acting on it can buy is a worse one.
		holding(9_000, 119);
		Position position = positions.get(ITEM);
		position.setPredictedSellMinutes(120);
		plantOffer(false, 130, 9_000, 15);
		marketData.moveTo(112, 116);
		offers.setMarket(marketData.getSnapshot());

		Suggestion suggestion = engine.refresh(true);

		if (suggestion != null)
		{
			assertTrue("fifteen minutes into a two-hour leg is impatience, not information: "
					+ suggestion.getType() + " " + suggestion.getHeadline(),
				suggestion.getType() != SuggestionType.MODIFY_SELL);
		}
	}

	// ------------------------------------------------------------------ the buy side

	@Test
	public void aBuyIsNotChasedIntoATradeThatLosesMoney() throws Exception
	{
		// The spread has closed to nothing while the offer sat there. Re-placing at one over the bid
		// and exiting at one under the ask is a round trip that cannot cover the tax, so telling the
		// player to cancel a working offer and enter it is advice to lose money on purpose.
		plantOffer(true, 100, 9_000, 90);
		marketData.moveTo(118, 119);
		offers.setMarket(marketData.getSnapshot());

		Suggestion suggestion = engine.refresh(true);

		assertNotNull("an offer being skipped over still has to be answered", suggestion);
		assertTrue("a repriced buy has to be worth placing: " + suggestion.getType() + " "
				+ suggestion.getHeadline() + " -- " + suggestion.getExpectedProfit() + " gp",
			suggestion.getType() != SuggestionType.MODIFY_BUY);
	}

	@Test
	public void theSlotIsFreedRatherThanLeftOnADeadOffer() throws Exception
	{
		// And the answer is not silence. The offer is being skipped over and is no longer worth
		// having, so the useful thing to say is to take the slot back.
		plantOffer(true, 100, 9_000, 90);
		marketData.moveTo(118, 119);
		offers.setMarket(marketData.getSnapshot());

		Suggestion suggestion = engine.refresh(true);

		assertNotNull(suggestion);
		assertEquals("the slot is the thing worth recovering: " + suggestion.getHeadline(),
			SuggestionType.CANCEL, suggestion.getType());
		assertEquals(ITEM, suggestion.getItemId());
	}

	@Test
	public void aBuyStillWorthPlacingIsStillRepriced() throws Exception
	{
		// The gate must not swallow the case it was built around. A wide spread with the offer
		// stranded below it is exactly what repricing is for.
		plantOffer(true, 100, 9_000, 90);
		marketData.moveTo(119, 135);
		offers.setMarket(marketData.getSnapshot());

		Suggestion suggestion = engine.refresh(true);

		assertNotNull(suggestion);
		assertEquals("a profitable reprice is the whole point of the branch: "
			+ suggestion.getHeadline(), SuggestionType.MODIFY_BUY, suggestion.getType());
		assertTrue("and it has to be worth doing: " + suggestion.getExpectedProfit(),
			suggestion.getExpectedProfit() > 0);
	}
}
