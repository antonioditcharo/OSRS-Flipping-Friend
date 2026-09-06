package com.flippingfriend.model;

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
import com.flippingfriend.data.WikiPriceClient;
import com.flippingfriend.session.AccountMonitor;
import com.flippingfriend.session.AccountState;
import com.flippingfriend.session.BuyLimitTracker;
import com.flippingfriend.session.OfferTracker;
import com.flippingfriend.session.PositionBook;
import com.flippingfriend.session.SellTimingEngine;
import com.flippingfriend.session.SkipList;
import com.flippingfriend.session.TradeJournal;
import com.flippingfriend.session.TradePlans;
import com.google.gson.Gson;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.mockito.Mockito;

/**
 * A {@link SuggestionEngine} that can be built in a test, with a market you control.
 *
 * <p>Nothing in this repository could construct one. Fifteen dependencies, most reaching for a
 * client, a network or a disk, so every decision the engine makes was reachable only by running the
 * game — and that gap hid three separate bugs in a single day. Each time the rule was written down,
 * commented, and in two cases unit tested in isolation, while the branch meant to apply it did
 * something else and nothing could ask it a question.
 *
 * <p>The three, for the record: an offer being edited was skipped by the eight-minute "do not nag"
 * gate, so the engine formed no opinion about it and went on serving a price pinned before the edit
 * began; a reprice floor was enforced at one of its two call sites, and the unguarded one ran first;
 * and a sell fell through a guard into "listing this banked item" and sold anyway.
 *
 * <h2>Why this is not a second implementation</h2>
 *
 * <p>Every part is the real class. The two doubles subclass {@link MarketDataService} and
 * {@link AccountMonitor} and override only the accessors that would otherwise reach for a network or
 * a client. No production code was changed to accommodate any of this, and nothing here stubs the
 * engine, the offer book, the position book, the sell engine or the tax rules — a harness that stubs
 * the thing under test proves nothing about it.
 */
final class EngineHarness
{
	private final PluginStorage storage;

	final PositionBook positions;
	final OfferTracker offers;
	final BuyLimitTracker buyLimits;
	final TradePlans tradePlans;
	final SkipList skipList;
	final TradeJournal journal;
	final FlippingFriendConfig config;
	final Market market;
	final Account account;
	final SuggestionEngine engine;

	EngineHarness(Path root)
	{
		storage = TestStorage.rootedAt(root, "harness");
		positions = new PositionBook(storage);
		buyLimits = new BuyLimitTracker(storage);
		tradePlans = new TradePlans(storage);
		skipList = new SkipList(storage);
		journal = new TradeJournal(storage, null, new AccountMonitor(null, null, null));
		TaxCalculator tax = new TaxCalculator();
		offers = new OfferTracker(storage, positions, buyLimits, journal, tax, tradePlans);

		config = Mockito.mock(FlippingFriendConfig.class);
		Mockito.when(config.riskProfile()).thenReturn(RiskProfile.HIGH);
		Mockito.when(config.checkInterval()).thenReturn(CheckInterval.CONSTANT);
		Mockito.when(config.minProfitPerFlip()).thenReturn(5_000);
		Mockito.when(config.blockedItems()).thenReturn("");
		Mockito.when(config.includeBankValue()).thenReturn(true);
		Mockito.when(config.useCalibration()).thenReturn(true);

		market = new Market(storage);
		account = new Account();

		engine = new SuggestionEngine(market, new FeatureEngine(), new ManipulationFilter(),
			new Scorer(tax, new FillModel(), new PositionSizer(), new Calibrator()),
			new Explainer(), new Calibrator(), tax, account, buyLimits, positions, offers,
			new SellTimingEngine(new FillModel(), tax), tradePlans, config, skipList);
	}

	/** Logged in, members, with coins and every slot free unless a test says otherwise. */
	void loggedInWith(long coins, int freeSlots)
	{
		account.set(new AccountState(true, true, false, coins, 0, 0, freeSlots, 8, 0, true,
			new HashMap<>(), new HashMap<>(), new HashMap<>()));
	}

	/**
	 * An offer that has been standing for {@code minutes}, written and loaded the way a real one is.
	 *
	 * <p>The reprice loop ignores anything younger than eight minutes, so a test about repricing needs
	 * an offer older than that -- and an offer created through onOfferChanged is always seconds old,
	 * with no setter and no clock to inject. Rather than open a seam in TrackedOffer purely for tests,
	 * this writes the offer file the tracker persists and asks it to load, which exercises the real
	 * serialisation on the way through.
	 *
	 * <p>Pinning the offer instead does NOT work, and finding out why was worth the detour: the pin
	 * short-circuits the whole decision chain, so a pinned offer wins over collecting, selling and
	 * everything else. That is correct -- it is what stops the player being yanked off a trade they
	 * are typing -- but it makes the pin useless for testing what outranks what.
	 */
	void standingOffer(int slot, int itemId, String name, boolean buying, int price, int quantity,
		long minutes)
	{
		long since = Instant.now().getEpochSecond() - minutes * 60;
		String json = "[{\"slot\":" + slot + ",\"itemId\":" + itemId + ",\"itemName\":\"" + name
			+ "\",\"buying\":" + buying + ",\"price\":" + price + ",\"totalQuantity\":" + quantity
			+ ",\"quantityFilled\":0,\"recordedQuantity\":0,\"recordedSpent\":0,\"spent\":0"
			+ ",\"state\":\"" + (buying ? "BUYING" : "SELLING") + "\",\"firstSeen\":" + since
			+ ",\"lastChanged\":" + since + ",\"collected\":false,\"journalled\":false"
			+ ",\"pendingQuantity\":0,\"pendingBackedQuantity\":0,\"pendingUnbackedQuantity\":0"
			+ ",\"pendingCostBasis\":0,\"pendingGross\":0,\"pendingTax\":0,\"pendingOpenedAt\":0"
			+ ",\"pendingPredictedMinutes\":0,\"pendingPredictedProfit\":0}]";
		try
		{
			java.nio.file.Files.write(storage.accountDir().resolve("offers.json"),
				json.getBytes(java.nio.charset.StandardCharsets.UTF_8));
		}
		catch (Exception failed)
		{
			throw new AssertionError("could not write the offer fixture", failed);
		}
		offers.load();
	}

	/** The market, as the engine sees it. Prices and history are whatever a test puts here. */
	static final class Market extends MarketDataService
	{
		private MarketSnapshot snapshot = MarketSnapshot.empty();
		private final Map<String, List<Candle>> series = new HashMap<>();

		Market(PluginStorage storage)
		{
			super(Mockito.mock(WikiPriceClient.class), new Gson(), storage);
		}

		@Override
		public MarketSnapshot getSnapshot()
		{
			return snapshot;
		}

		@Override
		public List<Candle> getSeries(int itemId, String timestep)
		{
			return series.getOrDefault(itemId + "/" + timestep, Collections.emptyList());
		}

		/**
		 * One item trading steadily between {@code bid} and {@code ask} for a day.
		 *
		 * <p>Deliberately flat. A test about which price the engine picks should not also be a test
		 * of what it makes of a trend nobody asked about — and a fixture that drifts turns a failure
		 * into an argument about the fixture.
		 */
		void trading(int itemId, String name, int bid, int ask, int buyLimit)
		{
			long now = Instant.now().getEpochSecond();

			Map<Integer, ItemMetadata> metadata = new HashMap<>(snapshot.getMetadata());
			Map<Integer, LatestPrice> latest = new HashMap<>(snapshot.getLatest());
			Map<Integer, Candle> bars = new HashMap<>(snapshot.getFiveMinute());

			metadata.put(itemId, new ItemMetadata(itemId, name, false, buyLimit, ask));
			latest.put(itemId, new LatestPrice(ask, now, bid, now));
			bars.put(itemId, new Candle(now, ask, bid, 5_000, 5_000));

			snapshot = new MarketSnapshot(metadata, latest, bars, bars, Instant.ofEpochSecond(now));

			List<Candle> history = new ArrayList<>();
			for (int i = 0; i < 288; i++)
			{
				history.add(new Candle(now - (288 - i) * 300L, ask, bid, 5_000, 5_000));
			}
			series.put(itemId + "/5m", history);
		}
	}

	/** The account, as the engine sees it. */
	static final class Account extends AccountMonitor
	{
		private AccountState state = AccountState.loggedOut();

		Account()
		{
			super(null, null, null);
		}

		@Override
		public AccountState getState()
		{
			return state;
		}

		void set(AccountState state)
		{
			this.state = state;
		}
	}
}
