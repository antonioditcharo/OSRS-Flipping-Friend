package com.flippingfriend.companion;

import com.flippingfriend.CheckInterval;
import com.flippingfriend.RiskProfile;
import com.flippingfriend.TradingHorizon;
import com.flippingfriend.data.Candle;
import com.flippingfriend.data.ItemMetadata;
import com.flippingfriend.data.LatestPrice;
import com.flippingfriend.data.WikiPriceClient;
import com.flippingfriend.model.Calibrator;
import com.flippingfriend.model.Candidate;
import com.flippingfriend.model.FeatureEngine;
import com.flippingfriend.model.FillModel;
import com.flippingfriend.model.FilterResult;
import com.flippingfriend.model.ItemFeatures;
import com.flippingfriend.model.ManipulationFilter;
import com.flippingfriend.model.MarketContext;
import com.flippingfriend.model.PositionSizer;
import com.flippingfriend.model.TaxCalculator;
import com.flippingfriend.companion.CandidateFactory;
import com.flippingfriend.companion.CandidateFactory.QuotedItem;
import com.flippingfriend.core.PortfolioCandidate;
import com.flippingfriend.model.RiskAppetite;
import com.flippingfriend.companion.SeriesSource;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import okhttp3.OkHttpClient;

/**
 * Replays real Grand Exchange history through the live decision code and reports what it would have
 * done.
 * <p>
 * This exists so the risk profiles can be tuned against evidence rather than intuition. It uses the
 * same {@link Scorer}, {@link ManipulationFilter} and {@link FeatureEngine} the plugin uses in
 * game — not a reimplementation — so a change that looks clever here is a change that will actually
 * ship.
 * <p>
 * <b>What it cannot model, and why the numbers are optimistic.</b> The wiki publishes what traded,
 * not the order book, so the simulation cannot see queue position. When it says an offer at a price
 * the market reached would have filled, it is assuming we were near the front of that queue. Real
 * fills are slower and less certain. Treat the output as a way of comparing profiles and settings
 * against each other, not as a forecast of profit.
 * <p>
 * Run with {@code gradlew backtest}.
 */
public class Backtester
{
	private static final String TIMESTEP = "5m";
	private static final String LONG_TIMESTEP = "1h";
	private static final int BUCKET_SECONDS = 300;
	/** History needed before the first decision, so features are meaningful. */
	private static final int WARMUP = 72;
	/**
	 * Buckets left at the end for trades to resolve in.
	 * <p>
	 * This has to cover the most patient profile's full holding period. Reserving less silently
	 * penalises the slower profiles: their trades get force-closed at whatever price the window
	 * happens to end on, which reads as a strategy losing money when it is really the simulation
	 * running out of road.
	 */
	private static final int TAIL = longestHoldBuckets();
	private static final int ITEM_SAMPLE = 40;
	/** Assume eight slots working in parallel, which is what a member actually has. */
	private static final int SLOTS = 8;
	/**
	 * The bankroll being tuned for. At this size capital is almost never the binding constraint —
	 * buy limits and volume are — so tuning against a smaller bank produced sizing rules that
	 * throttled trades which were never going to exhaust it.
	 */
	private static final long STARTING_COINS = 100_000_000L;
	private static final String CACHE_FILE = "build/backtest-cache/histories.json";
	private static final String LONG_CACHE_FILE = "build/backtest-cache/histories-1h.json";
	private static final long CACHE_TTL_SECONDS = 6 * 3600;

	private static int longestHoldBuckets()
	{
		int longest = 0;
		for (RiskProfile profile : RiskProfile.values())
		{
			longest = Math.max(longest, profile.getMaxHoldMinutes());
		}
		return Math.max(48, (longest * 60) / BUCKET_SECONDS);
	}

	public static void main(String[] args) throws Exception
	{
		System.out.println("Flipping Friend backtest");
		System.out.println("Downloading market data from the OSRS Wiki…");

		WikiPriceClient client = new WikiPriceClient(new OkHttpClient(), new Gson());

		Map<Integer, ItemMetadata> metadata = new HashMap<>();
		for (ItemMetadata item : client.fetchMapping())
		{
			metadata.put(item.getId(), item);
		}

		Map<Integer, List<Candle>> histories = loadCachedHistories(CACHE_FILE);
		Map<Integer, List<Candle>> longHistories = loadCachedHistories(LONG_CACHE_FILE);
		if (histories == null || longHistories == null)
		{
			Map<Integer, Candle> hourly = client.fetchAverages("1h");
			List<Integer> sample = pickLiquidItems(metadata, hourly);
			System.out.println("Selected " + sample.size() + " liquid items.");

			histories = new HashMap<>();
			longHistories = new HashMap<>();
			for (int itemId : sample)
			{
				try
				{
					List<Candle> series = client.fetchTimeseries(itemId, TIMESTEP);
					if (series.size() > WARMUP + TAIL)
					{
						histories.put(itemId, series);
						Thread.sleep(120);
						longHistories.put(itemId, client.fetchTimeseries(itemId, LONG_TIMESTEP));
					}
					// The wiki asks that its API not be hammered; this is a one-off analysis run.
					Thread.sleep(120);
				}
				catch (Exception ex)
				{
					System.out.println("  skipped " + itemId + ": " + ex.getMessage());
				}
			}
			saveCachedHistories(CACHE_FILE, histories);
			saveCachedHistories(LONG_CACHE_FILE, longHistories);
		}
		else
		{
			System.out.println("Using cached history (delete " + CACHE_FILE + " to refetch).");
		}

		System.out.println("Loaded history for " + histories.size() + " items.\n");

		System.out.printf("%-10s %14s %10s %8s %10s %12s%n",
			"Profile", "Profit/slot/hr", "Trades", "Win %", "Avg hold", "Worst trade");
		System.out.println("-".repeat(70));

		Map<RiskProfile, List<Result>> profileFolds = new HashMap<>();
		for (RiskProfile profile : RiskProfile.values())
		{
			List<Result> folds = runFolds(profile, metadata, histories, longHistories, 10);
			profileFolds.put(profile, folds);
			
			Result totalResult = new Result();
			for (Result f : folds) {
				totalResult.merge(f);
			}
			
			// Calculate Sharpe Ratio of GP/slot-hour across folds
			double[] gpPerHrFolds = folds.stream().mapToDouble(Result::profitPerSlotHour).toArray();
			double meanGp = totalResult.profitPerSlotHour();
			double variance = 0;
			for (double gp : gpPerHrFolds) {
				variance += (gp - meanGp) * (gp - meanGp);
			}
			double stdDev = Math.sqrt(variance / 10.0);
			double sharpeRatio = stdDev == 0 ? 0 : meanGp / stdDev;

			System.out.printf("%-10s %14s %10d %7.0f%% %9s %12s (Sharpe: %.2f)%n",
				profile.getDisplayName(),
				format(totalResult.profitPerSlotHour()),
				totalResult.trades,
				totalResult.winRate() * 100,
				String.format("%.0f min", totalResult.averageHoldMinutes()),
				format(totalResult.worstTrade),
				sharpeRatio);
		}

		// A profile that never trades is a bug, not a conservative setting, so show what stopped it.
		System.out.println("\nWhy opportunities were turned down:");
		for (RiskProfile profile : RiskProfile.values())
		{
			System.out.println("\n  " + profile.getDisplayName() + ":");
			Result totalResult = new Result();
			for (Result f : profileFolds.get(profile)) {
				totalResult.merge(f);
			}
			for (Map.Entry<String, Integer> entry : totalResult.topRejections(4))
			{
				System.out.printf("    %6d  %s%n", entry.getValue(), entry.getKey());
			}
		}

		System.out.println("\nNote: fills are simulated against prices that actually traded, but the "
			+ "\nqueue behind them is invisible. Real results will be slower and lower.");
	}

	private static class BacktestSeriesSource implements SeriesSource {
		private long currentTimestamp;
		private final Map<Integer, List<Candle>> histories;
		private final Map<Integer, List<Candle>> longHistories;

		BacktestSeriesSource(Map<Integer, List<Candle>> histories, Map<Integer, List<Candle>> longHistories) {
			this.histories = histories;
			this.longHistories = longHistories;
		}

		public void setTimestamp(long t) {
			this.currentTimestamp = t;
		}

		@Override
		public List<Candle> series(int itemId, String timestep) {
			List<Candle> src = timestep.equals(TIMESTEP) ? histories.get(itemId) : longHistories.get(itemId);
			return upTo(src, currentTimestamp);
		}
	}

	private static List<Result> runFolds(RiskProfile profile, Map<Integer, ItemMetadata> metadata,
		Map<Integer, List<Candle>> histories, Map<Integer, List<Candle>> longHistories, int numFolds)
	{
		TaxCalculator tax = new TaxCalculator();
		tax.resolveExemptions(metadata.values());

		BacktestSeriesSource source = new BacktestSeriesSource(histories, longHistories);
		CandidateFactory factory = new CandidateFactory(source, tax, TIMESTEP, LONG_TIMESTEP, BUCKET_SECONDS);
		factory.setRiskAppetite(RiskAppetite.forName(profile.name()));
		
		TradingHorizon horizon = TradingHorizon.of(profile, CheckInterval.FIVE_MINUTES);

		List<Result> folds = new ArrayList<>();
		for (int i = 0; i < numFolds; i++) {
			folds.add(new Result());
		}

		for (Map.Entry<Integer, List<Candle>> entry : histories.entrySet())
		{
			ItemMetadata item = metadata.get(entry.getKey());
			List<Candle> series = entry.getValue();
			if (item == null)
			{
				continue;
			}

			simulateItem(horizon, tax, factory, source, item, series, folds);
		}

		return folds;
	}

	private static void simulateItem(TradingHorizon horizon, TaxCalculator tax, CandidateFactory factory,
		BacktestSeriesSource source, ItemMetadata item, List<Candle> series, List<Result> folds)
	{
		int end = series.size() - TAIL;
		int cooldown = 0;
		int foldSize = Math.max(1, (end - WARMUP) / folds.size());

		for (int t = WARMUP; t < end; t++)
		{
			int foldIndex = Math.min(folds.size() - 1, (t - WARMUP) / foldSize);
			Result result = folds.get(foldIndex);
			
			if (cooldown > 0)
			{
				cooldown--;
				continue;
			}

			Candle now = series.get(t);
			if (!now.hasBothSides())
			{
				continue;
			}

			source.setTimestamp(now.getTimestamp());
			Instant timestamp = Instant.ofEpochSecond(now.getTimestamp());

			LatestPrice price = new LatestPrice(now.getAvgHighPrice(), now.getTimestamp(),
				now.getAvgLowPrice(), now.getTimestamp());
			
			MarketIngestionService.Item ingestItem = new MarketIngestionService.Item(item.getId(), item.getName(), item.getBuyLimit(), item.isMembers());
			QuotedItem quoted = new QuotedItem(ingestItem, price, now, now);
			List<PortfolioCandidate> tactics = factory.build(Collections.singletonList(quoted), horizon.legHorizonHours(),
				Collections.singletonMap(item.getId(), item.getBuyLimit()), STARTING_COINS, true, timestamp);

			if (tactics.isEmpty())
			{
				result.reject("Vetoed by CandidateFactory");
				continue;
			}

			PortfolioCandidate candidate = tactics.get(0);
			Outcome outcome = resolve(tax, series, t, candidate, horizon);
			result.add(outcome);

			// One position per item at a time, and the buy limit stops us re-entering immediately.
			cooldown = (int) Math.max(1, outcome.buckets);
		}
	}

	/**
	 * Walks the future forward and decides what actually happened to the trade: did the buy fill,
	 * then did the sell, and if not, what did cutting it cost?
	 */
	private static Outcome resolve(TaxCalculator tax, List<Candle> series, int start, PortfolioCandidate candidate,
		TradingHorizon horizon)
	{
		int maxBuckets = Math.max(1, horizon.maxHoldMinutes() * 60 / BUCKET_SECONDS);
		int limit = Math.min(series.size() - 1, start + maxBuckets);

		int boughtAt = -1;
		for (int t = start + 1; t <= limit; t++)
		{
			Integer low = series.get(t).getAvgLowPrice();
			if (low != null && low <= candidate.getBuyPrice())
			{
				boughtAt = t;
				break;
			}
		}

		if (boughtAt < 0)
		{
			// Never filled. No loss, but the slot was tied up for the whole window.
			return new Outcome(0, limit - start, false);
		}

		for (int t = boughtAt + 1; t <= limit; t++)
		{
			Integer high = series.get(t).getAvgHighPrice();
			if (high != null && high >= candidate.getSellPrice())
			{
				long profit = tax.netProfit(candidate.getItemId(), candidate.getBuyPrice(),
					candidate.getSellPrice(), candidate.getQuantity());
				return new Outcome(profit, t - start, true);
			}
		}

		// Held to the end of the window without reaching the target, so it gets cut at whatever the
		// market is paying then. This is the case that makes the averages honest.
		Candle last = series.get(limit);
		int exitPrice = last.getAvgHighPrice() != null
			? last.getAvgHighPrice()
			: (int) Math.round(last.midPrice());
		long profit = tax.netProfit(candidate.getItemId(), candidate.getBuyPrice(), exitPrice,
			candidate.getQuantity());
		return new Outcome(profit, limit - start, profit > 0);
	}

	/**
	 * Downloading forty timeseries politely takes minutes, which is far too slow a loop for tuning
	 * thresholds. The cache makes re-runs instant; delete it to test against fresh data.
	 */
	private static Map<Integer, List<Candle>> loadCachedHistories(String cacheFile)
	{
		try
		{
			Path file = Paths.get(cacheFile);
			if (!Files.isRegularFile(file))
			{
				return null;
			}
			if (Files.getLastModifiedTime(file).toInstant().plusSeconds(CACHE_TTL_SECONDS)
				.isBefore(Instant.now()))
			{
				return null;
			}
			try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8))
			{
				Type type = new TypeToken<Map<Integer, List<Candle>>>()
				{
				}.getType();
				return new Gson().fromJson(reader, type);
			}
		}
		catch (Exception ex)
		{
			return null;
		}
	}

	private static void saveCachedHistories(String cacheFile, Map<Integer, List<Candle>> histories)
	{
		try
		{
			Path file = Paths.get(cacheFile);
			Files.createDirectories(file.getParent());
			try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8))
			{
				new Gson().toJson(histories, writer);
			}
		}
		catch (Exception ex)
		{
			System.out.println("could not cache histories: " + ex.getMessage());
		}
	}

	/** The part of a series that had already happened at the moment being simulated. */
	private static List<Candle> upTo(List<Candle> series, long timestamp)
	{
		if (series == null || series.isEmpty())
		{
			return java.util.Collections.emptyList();
		}
		int end = 0;
		while (end < series.size() && series.get(end).getTimestamp() <= timestamp)
		{
			end++;
		}
		return series.subList(0, end);
	}

	private static List<Integer> pickLiquidItems(Map<Integer, ItemMetadata> metadata,
		Map<Integer, Candle> hourly)
	{
		List<Map.Entry<Integer, Candle>> ranked = new ArrayList<>(hourly.entrySet());
		ranked.removeIf(entry -> !metadata.containsKey(entry.getKey())
			|| entry.getValue().getTotalVolume() <= 0
			|| !entry.getValue().hasBothSides());

		ranked.sort(Comparator.comparingInt((Map.Entry<Integer, Candle> e) ->
			e.getValue().getTotalVolume()).reversed());

		List<Integer> sample = new ArrayList<>();
		// Spread the sample across the liquidity range rather than taking only the very busiest,
		// which would flatter any strategy that depends on volume.
		int step = Math.max(1, ranked.size() / (ITEM_SAMPLE * 4));
		for (int i = 0; i < ranked.size() && sample.size() < ITEM_SAMPLE; i += step)
		{
			sample.add(ranked.get(i).getKey());
		}
		return sample;
	}

	private static String format(long amount)
	{
		if (Math.abs(amount) >= 1_000_000)
		{
			return String.format("%.2fm", amount / 1_000_000.0);
		}
		if (Math.abs(amount) >= 1_000)
		{
			return String.format("%.1fk", amount / 1_000.0);
		}
		return Long.toString(amount);
	}

	private static final class Outcome
	{
		private final long profit;
		private final long buckets;
		private final boolean win;

		Outcome(long profit, long buckets, boolean win)
		{
			this.profit = profit;
			this.buckets = buckets;
			this.win = win;
		}
	}

	private static final class Result
	{
		private final Map<String, Integer> rejections = new HashMap<>();
		private long profit;
		private long totalBuckets;
		private int trades;
		private int wins;
		private long worstTrade;

		void reject(String reason)
		{
			rejections.merge(reason == null ? "unknown" : reason, 1, Integer::sum);
		}

		/** The handful of reasons that actually shaped the outcome, most common first. */
		List<Map.Entry<String, Integer>> topRejections(int count)
		{
			List<Map.Entry<String, Integer>> sorted = new ArrayList<>(rejections.entrySet());
			sorted.sort(Comparator.comparingInt((Map.Entry<String, Integer> e) -> e.getValue()).reversed());
			return sorted.subList(0, Math.min(count, sorted.size()));
		}

		void add(Outcome outcome)
		{
			profit += outcome.profit;
			totalBuckets += outcome.buckets;
			trades++;
			if (outcome.win)
			{
				wins++;
			}
			worstTrade = Math.min(worstTrade, outcome.profit);
		}

		void merge(Result other) {
			for (Map.Entry<String, Integer> entry : other.rejections.entrySet()) {
				this.rejections.merge(entry.getKey(), entry.getValue(), Integer::sum);
			}
			this.profit += other.profit;
			this.totalBuckets += other.totalBuckets;
			this.trades += other.trades;
			this.wins += other.wins;
			this.worstTrade = Math.min(this.worstTrade, other.worstTrade);
		}

		double winRate()
		{
			return trades == 0 ? 0 : (double) wins / trades;
		}

		double averageHoldMinutes()
		{
			return trades == 0 ? 0 : (totalBuckets * (BUCKET_SECONDS / 60.0)) / trades;
		}

		/** Profit divided by the slot-time it consumed, which is the resource actually being spent. */
		long profitPerSlotHour()
		{
			double slotHours = totalBuckets * (BUCKET_SECONDS / 3600.0) / SLOTS;
			return slotHours <= 0 ? 0 : (long) (profit / slotHours);
		}
	}
}
