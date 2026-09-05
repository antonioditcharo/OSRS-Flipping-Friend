package com.flippingfriend.companion;

import com.flippingfriend.FlippingFriendConfig;
import com.flippingfriend.core.AccountSnapshot;
import com.flippingfriend.core.CompanionHealth;
import com.flippingfriend.core.OfferEvent;
import com.flippingfriend.core.PortfolioAllocation;
import com.flippingfriend.core.PortfolioPlan;
import com.flippingfriend.data.PluginStorage;
import com.flippingfriend.data.Candle;
import com.flippingfriend.model.Explainer;
import com.flippingfriend.model.Suggestion;
import com.flippingfriend.model.SuggestionType;
import com.flippingfriend.session.AccountState;
import com.flippingfriend.session.TrackedOffer;
import com.google.gson.Gson;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Properties;
import java.util.UUID;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Thin, fail-closed client for the local companion. It never controls the game; it merely copies
 * observed state out and receives manual instructions back.
 */
@Singleton
public class CompanionClient
{
	private static final String BASE = "http://127.0.0.1:37777/v1/";
	private static final int TIMEOUT_MILLIS = 750;
	/** The market feed is megabytes rather than a plan, so it gets its own budget. */
	private static final int FEED_TIMEOUT_MILLIS = 5_000;
	private final PluginStorage storage;
	private final Gson gson;
	private final SuggestionLedger ledger;

	/** The item currently being recommended, kept so a re-plan does not move it. */
	private volatile int incumbentItemId;

	/**
	 * Forgets the trade being held, so a new session or a changed setting starts clean.
	 * <p>
	 * Without this the lock outlives the thing it was locking: switching risk profile kept showing the
	 * previous profile's pick for as long as it survived in the plan, and a logout carried it into the
	 * next login.
	 */
	public void clearIncumbent()
	{
		incumbentItemId = 0;
		lastBuy = null;
	}

	/** The single point where a plan becomes the one in force, so tests can reach the same state. */
	void acceptPlan(PortfolioPlan plan)
	{
		lastPlan = plan;
	}

	/**
	 * Whether the companion is in a position to answer at all, as opposed to answering "not now".
	 * <p>
	 * These are different facts and the caller has to tell them apart. A plan that says every slot is
	 * occupied is an answer; falling back to the built-in engine on the strength of it means showing
	 * the player a trade chosen by different rules, seconds after showing them one chosen by these.
	 * A plan that is absent or expired means the companion has genuinely gone quiet, and only then is
	 * the built-in engine the right thing to ask.
	 */
	public boolean hasFreshPlan()
	{
		PortfolioPlan plan = lastPlan;
		return plan != null && plan.getExpiresAt() > Instant.now().getEpochSecond();
	}
	/**
	 * The last buy actually put in front of the player, kept so a momentary gap does not replace it.
	 * <p>
	 * On three slots a single offer changing state moves the free-slot count between 0 and 1, and the
	 * plan flips between READY and "every slot is occupied" -- six were produced in four seconds
	 * during one transition. Every one of those blips used to reach the player as a different answer.
	 */
	private volatile Suggestion lastBuy;
	private volatile String lastError = "Companion has not been contacted.";
	private volatile PortfolioPlan lastPlan;
	private volatile CompanionHealth lastHealth;

	/** The one implementation of the tax rules, so the realised figure cannot drift from the predicted one. */
	private final com.flippingfriend.model.TaxCalculator taxCalculator;

	@Inject
	public CompanionClient(PluginStorage storage, Gson gson, SuggestionLedger ledger,
		com.flippingfriend.model.TaxCalculator taxCalculator)
	{
		this.storage = storage;
		this.gson = gson;
		this.ledger = ledger;
		this.taxCalculator = taxCalculator;
	}

	/**
	 * @param markedDrawdown coins the session is currently down, closed trades and open positions
	 *                       together. This is what arms the drawdown circuit breaker, so passing a
	 *                       placeholder here would leave the breaker permanently disarmed while
	 *                       still appearing, in every test and in the panel, to be working.
	 */
	public void publishAccount(AccountState state, FlippingFriendConfig config, long markedDrawdown,
		java.util.Map<Integer, Long> committedByItem, java.util.Map<Integer, Integer> buyLimitUsed,
		java.util.Set<String> blocked, java.util.Set<Integer> skipped, java.util.Set<Integer> onOffer,
		boolean sellOnly)
	{
		if (!state.isLoggedIn()) return;
		long now = Instant.now().getEpochSecond();
		// What is already held goes with it. The companion's exposure ceilings were seeded empty on
		// every plan, so they limited a plan against itself and never against the book -- which is how
		// the same item could be recommended again the moment its buy was collected. The profit floor
		// travels too: it existed only in the built-in engine, whose buy result is discarded, so the
		// setting had no effect on any trade the player was actually shown.
		AccountSnapshot snapshot = new AccountSnapshot(UUID.randomUUID().toString(), now,
			state.spendableCoins(config.includeBankValue(), config.bankrollCap()), state.getCommittedCoins(),
			state.getFreeSlots(), state.getTotalSlots(), state.canBuyMembersItems(), state.isBankSeen(),
			markedDrawdown, config.riskProfile() == null ? null : config.riskProfile().name(),
			committedByItem, config.minProfitPerFlip(), buyLimitUsed,
			!config.useCalibration(),
			config.checkInterval() == null ? 0 : config.checkInterval().getMinutes(),
			blocked, skipped, onOffer, sellOnly);
		post("events/account-state", snapshot);
	}

	public void publishOffer(TrackedOffer offer)
	{
		if (offer == null) return;
		long now = Instant.now().getEpochSecond();
		String state = offer.getState() == null ? "OBSERVED" : offer.getState();

		OfferEvent.Builder event = OfferEvent.builder(UUID.randomUUID().toString(), now, state)
			.slot(offer.getSlot())
			.item(offer.getItemId(), offer.getItemName())
			.buying(offer.isBuying())
			.price(offer.getPrice())
			.quantities(offer.getTotalQuantity(), offer.getQuantityFilled())
			.spent(offer.getSpent())
			// Computed with the same TaxCalculator the engine prices trades with, so the realised
			// figure downstream cannot drift from the predicted one.
			.tax(offer.isBuying() || offer.getQuantityFilled() <= 0 ? 0
				: taxCalculator.taxFor(offer.getItemId(), offer.getPrice(), offer.getQuantityFilled()))
			.sequence(ledger.nextSequence())
			.firstSeenAt(offer.getFirstSeen());

		SuggestionLedger.Advice advice = ledger.attribute(offer.getItemId(), offer.isBuying(), now);
		if (advice != null)
		{
			event.recommendation(advice.getRecommendationId(), advice.getPrice(), advice.getQuantity(),
				advice.getQuoteAgeSeconds(), advice.getPredictedMinutes(), advice.getPredictedCompletion());
		}
		post("events/ge-offer", event.build());
	}

	/**
	 * Fetches the current plan and remembers it, whatever else happens this cycle.
	 * <p>
	 * This used to be folded into {@link #nextBuySuggestion}, which the plugin only calls when the
	 * built-in engine has nothing to sell or collect. So on any cycle where the player had something
	 * else to do, the plan behind the portfolio list was never refreshed and the panel quietly kept
	 * rendering an older and older plan.
	 *
	 * @return the plan if the companion returned a usable one, otherwise null
	 */
	public PortfolioPlan refreshPlan()
	{
		try
		{
			CompanionHealth health = get("health", CompanionHealth.class);
			lastHealth = health;
			if (health == null || !health.isReady())
			{
				lastPlan = null;
				return null;
			}
			acceptPlan(get("portfolio/current", PortfolioPlan.class));
			return lastPlan;
		}
		catch (Exception ex)
		{
			lastError = ex.getMessage();
			return null;
		}
	}

	/**
	 * The trade to recommend, held steady between plans.
	 * <p>
	 * A new plan arrives every fifteen to thirty seconds and its ranking is not stable between runs,
	 * so simply taking the top row meant the recommendation changed under the player several times a
	 * minute. Once an item has been put in front of someone it stays there while it remains a trade
	 * the plan still wants and the player has not rejected — the price and size are refreshed from
	 * each new plan, but the item does not move.
	 *
	 * @param blocked  item names the player never wants suggested, lower-cased
	 * @param skipped  item ids the player has skipped this session
	 * @param onOffer  item ids with a live Grand Exchange offer, which must not be suggested again
	 */
	public Suggestion nextBuySuggestion(Explainer explainer, Set<String> blocked, Set<Integer> skipped,
		Set<Integer> onOffer)
	{
		try
		{
			PortfolioPlan plan = lastPlan;
			if (plan == null || !"READY".equals(plan.getStatus()) || plan.getAllocations().isEmpty()
				|| plan.getExpiresAt() <= Instant.now().getEpochSecond())
			{
				// Nothing usable in the current plan. Before announcing that, check whether the trade
				// already on screen still stands: this method is only reached when the client says a
				// slot is free, so a plan claiming every slot is occupied was built from an older
				// snapshot than the one that got us here. Replacing a good suggestion with a message
				// -- and then with whatever the built-in engine picks next cycle -- is what reads as
				// the system changing its mind three times in five seconds.
				Suggestion held = held(blocked, skipped, onOffer);
				if (held != null)
				{
					return held;
				}
				return unavailable(plan == null ? lastError : plan.getReason());
			}

			PortfolioAllocation allocation = select(plan, blocked, skipped, onOffer);
			if (allocation == null)
			{
				return unavailable("Every trade in the current plan is one you have skipped or blocked.");
			}
			com.flippingfriend.core.PortfolioCandidate candidate = allocation.getCandidate();

			// Remember what we advised before showing it, so whatever the player does next can be
			// attributed to this plan rather than arriving as an anonymous offer.
			long quoteAge = Math.max(0, Instant.now().getEpochSecond() - plan.getCreatedAt());
			ledger.recorded(plan.getCorrelationId(), candidate.getItemId(), true,
				candidate.getBuyPrice(), candidate.getQuantity(), quoteAge,
				candidate.getBuyHours() * 60, candidate.getBuyFillProbability());

			Suggestion suggestion = Suggestion.builder(SuggestionType.BUY)
				.item(candidate.getItemId(), candidate.getItemName())
				.price(candidate.getBuyPrice())
				.quantity(candidate.getQuantity())
				.expectedProfit(Math.round(candidate.expectedProfit()))
				.confidence(candidate.getDisplayCompletionProbability())
				.expectedMinutes(candidate.getSlotHours() * 60)
				// Both legs, which the panel has always been able to show and was never given: the
				// companion path never called this, so every companion-sourced suggestion rendered
				// "not known" for the buy and the sell while the portfolio list two inches below
				// printed both from the same candidate.
				.fillMinutes(candidate.getBuyHours() * 60, candidate.getSellHours() * 60)
				.targetSellPrice(candidate.getSellPrice())
				// rather than against the combined figure, which carries the buy leg's difficulty too.
				.headline("Buy " + explainer.formatNumber(candidate.getQuantity()) + " × " + candidate.getItemName())
				.build();

			lastBuy = suggestion;
			return suggestion;
		}
		catch (Exception ex)
		{
			lastError = ex.getMessage();
			return unavailable(lastError);
		}
	}

	/**
	 * Records that the panel advised this sale, so the outcome can be scored against the prediction.
	 * <p>
	 * Only buy advice was ever recorded, with {@code buying = true} hardcoded at the single call site.
	 * Everything downstream keys attribution on the leg, so every sell arrived at the companion with
	 * no recommendation attached: {@code wasRecommended()} was false, {@code observeOutcome} was never
	 * called for a sale, and the completion calibrator was trained entirely on buy legs and then
	 * applied to both. The sell leg is the one carrying the real uncertainty -- a buy at the bid nearly
	 * always fills -- so the half that was missing is the half worth learning from.
	 */
	public void recordSellAdvice(Suggestion suggestion)
	{
		if (suggestion == null || suggestion.getItemId() <= 0 || suggestion.getQuantity() <= 0)
		{
			return;
		}
		// The identity that matters is the trade, not the id: the ledger decides whether this is the
		// same advice by comparing price and quantity, which is what keeps the attribution window
		// from being restamped on every refresh.
		ledger.recorded("sell-" + suggestion.getItemId(), suggestion.getItemId(), false,
			suggestion.getPrice(), suggestion.getQuantity(), 0,
			suggestion.getSellFillMinutes() > 0
				? suggestion.getSellFillMinutes() : suggestion.getExpectedMinutes(),
			suggestion.getConfidence());
	}

	/**
	 * Prefers the trade already on screen, then the plan's own order.
	 * <p>
	 * Returning null means every row is blocked or skipped, which is a real answer and not a failure.
	 */
	PortfolioAllocation select(PortfolioPlan plan, Set<String> blocked, Set<Integer> skipped)
	{
		return select(plan, blocked, skipped, java.util.Collections.emptySet());
	}

	PortfolioAllocation select(PortfolioPlan plan, Set<String> blocked, Set<Integer> skipped,
		Set<Integer> onOffer)
	{
		PortfolioAllocation chosen = choose(plan, blocked, skipped, onOffer);
		if (chosen != null)
		{
			incumbentItemId = chosen.getCandidate().getItemId();
		}
		return chosen;
	}

	private PortfolioAllocation choose(PortfolioPlan plan, Set<String> blocked, Set<Integer> skipped,
		Set<Integer> onOffer)
	{
		PortfolioAllocation first = null;
		for (PortfolioAllocation allocation : plan.getAllocations())
		{
			if (!acceptable(allocation, blocked, skipped, onOffer))
			{
				continue;
			}
			if (allocation.getCandidate().getItemId() == incumbentItemId)
			{
				return allocation;
			}
			if (first == null)
			{
				first = allocation;
			}
		}
		return first;
	}

	private static boolean acceptable(PortfolioAllocation allocation, Set<String> blocked,
		Set<Integer> skipped, Set<Integer> onOffer)
	{
		com.flippingfriend.core.PortfolioCandidate candidate = allocation.getCandidate();
		if (candidate == null || candidate.getQuantity() <= 0)
		{
			return false;
		}
		// An item with a live offer must not be suggested again. The built-in engine has always
		// excluded these, in its own words, because otherwise "the engine would cheerfully tell you to
		// buy the thing you just bought, over and over, until it filled" -- and the companion path,
		// which is the one that now produces the buy, had no notion of them at all. Holding the
		// incumbent made that worse rather than better: before, the re-ranking would at least rotate
		// away; now it is pinned there until the offer fills.
		if (onOffer != null && onOffer.contains(candidate.getItemId()))
		{
			return false;
		}
		if (skipped != null && skipped.contains(candidate.getItemId()))
		{
			return false;
		}
		String name = candidate.getItemName();
		return blocked == null || name == null
			|| !blocked.contains(name.toLowerCase(java.util.Locale.ROOT));
	}

	public String lastError() { return lastError; }

	/** Last plan fetched by the engine thread; Swing reads this cached immutable object only. */
	public PortfolioPlan lastPlan() { return lastPlan; }
	public CompanionHealth lastHealth() { return lastHealth; }

	/**
	 * The buy already on screen, if it is still one the player could act on.
	 * <p>
	 * Held only while it remains genuinely available: not blocked, not skipped, and not already
	 * sitting in a slot. Anything else and the gap is real and should be reported.
	 */
	private Suggestion held(Set<String> blocked, Set<Integer> skipped, Set<Integer> onOffer)
	{
		Suggestion previous = lastBuy;
		if (previous == null || previous.getType() != SuggestionType.BUY)
		{
			return null;
		}
		int itemId = previous.getItemId();
		if ((skipped != null && skipped.contains(itemId))
			|| (onOffer != null && onOffer.contains(itemId)))
		{
			return null;
		}
		String name = previous.getItemName();
		if (blocked != null && name != null
			&& blocked.contains(name.toLowerCase(java.util.Locale.ROOT)))
		{
			return null;
		}
		return previous;
	}

	private Suggestion unavailable(String detail)
	{
		return Suggestion.waiting("Companion unavailable", "No new buy will be suggested until the local portfolio companion has fresh market data. " + detail);
	}

	private void post(String path, Object body)
	{
		try { request(path, "POST", gson.toJson(body)); }
		catch (Exception ex) { lastError = ex.getMessage(); }
	}

	private <T> T get(String path, Class<T> type) throws Exception
	{
		return gson.fromJson(request(path, "GET", null), type);
	}

	/**
	 * The whole market feed as the companion has it, or null when it is not running.
	 * <p>
	 * This is what makes a cold start warm. The companion polls the wiki every sixty seconds whether
	 * or not the game is open, so on the loopback this is a few megabytes of already-fetched JSON and
	 * a few milliseconds -- against a fresh download from the internet, which the plugin used to do on
	 * every launch while refusing to advise. The data is a minute old at worst, so nothing is decided
	 * on a stale price to buy the speed.
	 * <p>
	 * A longer read timeout than the rest: the payload is genuinely large, and the ordinary
	 * three-quarters of a second is sized for a plan, not a feed.
	 */
	public MarketFeed fetchMarketFeed()
	{
		try
		{
			return gson.fromJson(request("market/snapshot", "GET", null, FEED_TIMEOUT_MILLIS),
				MarketFeed.class);
		}
		catch (Exception ex)
		{
			// Not an error. The companion may simply not be running, and everything still works from
			// the wiki -- more slowly, which is the behaviour this replaces rather than breaks.
			lastError = ex.getMessage();
			return null;
		}
	}

	/**
	 * One item's history from the companion's warm cache, or null when it has none.
	 * <p>
	 * The companion keeps a persistent per-item cache and warms it continuously. Asking it first
	 * turns the plugin's sixty sequential wiki round trips -- the "checking the market" wait -- into
	 * sixty loopback reads.
	 */
	public java.util.List<Candle> fetchSeries(int itemId, String timestep)
	{
		try
		{
			Candle[] series = gson.fromJson(
				request("market/series?id=" + itemId + "&timestep=" + timestep, "GET", null),
				Candle[].class);
			return series == null || series.length == 0 ? null : java.util.Arrays.asList(series);
		}
		catch (Exception ex)
		{
			return null;
		}
	}

	/**
	 * Every metric's latest reading, for the learning panel, or null when the companion is not up.
	 *
	 * <p>One request for the whole panel. The alternative is a call per metric against
	 * {@code learning/history}, which reads a full trajectory off disk to use the last row of it,
	 * eighteen times over -- and a panel that expensive to draw gets refreshed too rarely to be worth
	 * looking at.
	 */
	public java.util.List<com.flippingfriend.model.LearningReading> fetchLearning()
	{
		try
		{
			com.flippingfriend.model.LearningReading[] latest = gson.fromJson(
				request("learning/summary", "GET", null),
				com.flippingfriend.model.LearningReading[].class);
			return latest == null ? null : java.util.Arrays.asList(latest);
		}
		catch (Exception ex)
		{
			return null;
		}
	}

	/** The three all-item payloads, in the wiki's own shape. */
	public static final class MarketFeed
	{
		private long observedAt;
		private com.google.gson.JsonObject latest;
		private com.google.gson.JsonObject fiveMinute;
		private com.google.gson.JsonObject hourly;

		public long getObservedAt() { return observedAt; }
		public com.google.gson.JsonObject getLatest() { return latest; }
		public com.google.gson.JsonObject getFiveMinute() { return fiveMinute; }
		public com.google.gson.JsonObject getHourly() { return hourly; }
	}

	private String request(String path, String method, String body) throws Exception
	{
		return request(path, method, body, TIMEOUT_MILLIS);
	}

	private String request(String path, String method, String body, int readTimeoutMillis)
		throws Exception
	{
		String token = token();
		if (token == null) throw new IllegalStateException("Start the Flipping Friend companion first.");
		HttpURLConnection connection = (HttpURLConnection) new URL(BASE + path).openConnection();
		connection.setRequestMethod(method);
		connection.setConnectTimeout(TIMEOUT_MILLIS);
		connection.setReadTimeout(readTimeoutMillis);
		connection.setRequestProperty("X-Flipping-Friend-Token", token);
		connection.setRequestProperty("Content-Type", "application/json");
		if (body != null)
		{
			connection.setDoOutput(true);
			try (OutputStream output = connection.getOutputStream()) { output.write(body.getBytes(StandardCharsets.UTF_8)); }
		}
		int code = connection.getResponseCode();
		if (code < 200 || code >= 300) throw new IllegalStateException("Companion returned " + code);
		try (InputStream input = connection.getInputStream()) { return new String(input.readAllBytes(), StandardCharsets.UTF_8); }
		finally { connection.disconnect(); }
	}

	private String token() throws Exception
	{
		Path file = storage.root().resolve("companion").resolve("companion.properties");
		if (!Files.isRegularFile(file)) return null;
		Properties properties = new Properties();
		try (java.io.Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) { properties.load(reader); }
		return properties.getProperty("token");
	}
}
