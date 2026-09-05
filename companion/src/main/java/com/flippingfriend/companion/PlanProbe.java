package com.flippingfriend.companion;

import com.flippingfriend.core.PlanDiagnostics;
import com.flippingfriend.core.PortfolioCandidate;
import com.flippingfriend.data.Candle;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * Runs a plan against the live market, in its own process, and says what happened to each item.
 *
 * <p>Exists because a running companion cannot be asked. The funnel reports how many items each
 * threshold turned away, which answers "why is the plan thin" only if you already know which
 * threshold to suspect — and answering "why is <em>this</em> item not in the plan" took a screenshot,
 * a database read and three rounds of guessing.
 *
 * <p>Reads through the companion's own loopback API, so it disturbs nothing: the same quotes, the
 * same history, the same code. A separate process because restarting the live one to inspect it
 * blanks the player's panel for several minutes while the series cache warms, and the question is
 * usually asked at exactly the moment they are trying to trade.
 *
 * <p>Usage: {@code PlanProbe <coins> [itemId ...]}
 */
public final class PlanProbe
{
	private static final String BASE = "http://127.0.0.1:37777/v1/";
	private static final Gson GSON = new Gson();

	public static void main(String[] args) throws Exception
	{
		long coins = args.length > 0 ? Long.parseLong(args[0]) : 47_000_000L;
		long minProfit = Long.getLong("minProfit", 5_000L);
		String appetiteName = System.getProperty("appetite", "AGGRESSIVE");
		String token = token();

		JsonObject snapshot = JsonParser.parseString(get("market/snapshot", token)).getAsJsonObject();
		JsonObject latest = snapshot.getAsJsonObject("latest");
		JsonObject fiveMinute = snapshot.getAsJsonObject("fiveMinute");
		JsonObject hourly = snapshot.getAsJsonObject("hourly");
		long observedAt = snapshot.get("observedAt").getAsLong();

		Map<Integer, MarketIngestionService.Item> mapping =
			MarketIngestionService.parseMapping(JsonParser.parseString(wiki("/mapping")));

		System.out.printf("market observed %ds ago, %d items mapped, %d quoted%n",
			Instant.now().getEpochSecond() - observedAt, mapping.size(), latest.size());

		MarketIngestionService.MarketState state =
			new MarketIngestionService.MarketState(mapping, latest, fiveMinute, hourly, observedAt);

		CandidateFactory factory = new CandidateFactory(seriesFrom(token));
		// The live planner sets this from the account snapshot; without it the probe silently runs a
		// different risk profile from the thing it is meant to reproduce.
		factory.setRiskAppetite(com.flippingfriend.model.RiskAppetite.forName(appetiteName));
		System.out.println("appetite " + appetiteName + ", minProfitPerFlip " + minProfit);
		List<PortfolioCandidate> tactics = factory.build(state, 4.0, new HashMap<>(), coins, true,
			Instant.now());

		PlanDiagnostics funnel = factory.lastFunnel(tactics.size(), coins);
		System.out.printf("%nfeed %d -> quoted %d -> shortlisted %d -> analysed %d -> tactics %d%n",
			funnel.getItemsInFeed(), funnel.getItemsQuoted(), funnel.getItemsShortlisted(),
			funnel.getItemsAnalysed(), tactics.size());

		System.out.println("\n=== where the items went ===");
		funnel.getVetoCounts().entrySet().stream()
			.sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
			.limit(20)
			.forEach(entry -> System.out.printf("%6d  %s%n", entry.getValue(), entry.getKey()));

		System.out.println("\n=== the shortlist, top 20 by throughput ===");
		List<Integer> shortlist = factory.shortlistIds();
		for (int i = 0; i < Math.min(20, shortlist.size()); i++)
		{
			MarketIngestionService.Item item = mapping.get(shortlist.get(i));
			System.out.printf("%3d. %-38s limit %6d%n", i + 1,
				item == null ? shortlist.get(i) : item.name, item == null ? 0 : item.buyLimit);
		}

		System.out.println("\n=== best tactic per item, top 15 by expected gp/slot-hour ===");
		tactics.sort((a, b) -> Double.compare(b.expectedGpPerSlotHour(), a.expectedGpPerSlotHour()));
		System.out.printf("%-34s %7s %9s %11s %7s %10s%n",
			"item", "qty", "buy", "netProfit", "p", "gp/slot-hr");
		for (int i = 0; i < Math.min(15, tactics.size()); i++)
		{
			PortfolioCandidate candidate = tactics.get(i);
			String name = candidate.getItemName() == null ? "?" : candidate.getItemName();
			System.out.printf("%-34s %7d %9d %11d %7.3f %10.0f%n",
				name.substring(0, Math.min(34, name.length())),
				candidate.getQuantity(), candidate.getTargetBuyPrice(), candidate.getNetProfit(),
				candidate.getCompletionProbability(), candidate.expectedGpPerSlotHour());
		}

		// ---- the second half of the pipeline, which is where the live plan loses them ----
		long equity = coins;
		com.flippingfriend.core.PortfolioConstraints limits =
			new com.flippingfriend.core.PortfolioConstraints(8, coins,
				(long) (equity * 0.15), (long) (equity * 1.0), (long) (equity * 1.0),
				new HashMap<>(), new HashMap<>(), minProfit);
		com.flippingfriend.core.PortfolioOptimizer optimizer =
			new com.flippingfriend.core.PortfolioOptimizer();
		com.flippingfriend.core.PortfolioPlan board =
			optimizer.optimize(tactics, limits, "probe", Instant.now().getEpochSecond());

		System.out.println("\n=== the optimizer ===");
		System.out.printf("considered %d of %d tactics, filled %d of 8 slots%n",
			optimizer.lastConsidered(), tactics.size(), board.getAllocations().size());
		optimizer.lastRejections().entrySet().stream()
			.sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
			.forEach(entry -> System.out.printf("%6d  %s%n", entry.getValue(), entry.getKey()));
		System.out.printf("plan: %.0f gp/slot-hour%n", board.getExpectedGpPerSlotHour());
		for (com.flippingfriend.core.PortfolioAllocation allocation : board.getAllocations())
		{
			PortfolioCandidate chosen = allocation.getCandidate();
			System.out.printf("  %-30s qty %5d  capital %10d  %8.0f gp/slot-hr%n",
				chosen.getItemName(), chosen.getQuantity(), chosen.getCapitalRequired(),
				chosen.expectedGpPerSlotHour());
		}

		for (int i = 1; i < args.length; i++)
		{
			int itemId = Integer.parseInt(args[i]);
			MarketIngestionService.Item item = mapping.get(itemId);
			System.out.printf("%n=== %s (%d) ===%n",
				item == null ? "unknown" : item.name, itemId);
			System.out.println("  turned away: " + factory.lastReasonFor(itemId));
			System.out.println("  shortlisted: " + shortlist.contains(itemId));
			for (PortfolioCandidate candidate : tactics)
			{
				if (candidate.getItemId() == itemId)
				{
					System.out.printf("  tactic: qty %d at %d, netProfit %d, p %.3f, %.0f gp/slot-hr%n",
						candidate.getQuantity(), candidate.getTargetBuyPrice(),
						candidate.getNetProfit(), candidate.getCompletionProbability(),
						candidate.expectedGpPerSlotHour());
					break;
				}
			}
		}
	}

	/**
	 * History from the companion where it has it, and from the wiki where it does not.
	 *
	 * <p>The fallback is what makes this usable. A probe that reads only the companion's cache
	 * measures the cache: after a restart it holds a few hundred series, so 79 of 90 shortlisted items
	 * come back as "Waiting for price history" and the plan looks catastrophic for reasons that have
	 * nothing to do with the code being tested. That is not a hypothetical — it invalidated three
	 * measurements before it was noticed, one of which nearly got a good change reverted.
	 *
	 * <p>Slower, and worth it. Roughly ninety items at two resolutions, rate-limited, against a
	 * volunteer-run API that asks only for a User-Agent in return.
	 */
	private static SeriesSource seriesFrom(String token)
	{
		// The factory analyses items in parallel, so the cache must expect it.
		Map<String, List<Candle>> cache = new java.util.concurrent.ConcurrentHashMap<>();
		return (itemId, timestep) -> cache.computeIfAbsent(itemId + "/" + timestep, key ->
		{
			List<Candle> fromCompanion = fetch(() ->
				get("market/series?id=" + itemId + "&timestep=" + timestep, token));
			if (!fromCompanion.isEmpty())
			{
				return fromCompanion;
			}
			return fetch(() ->
			{
				synchronized (PlanProbe.class)
				{
					// One at a time, with a pause. Ninety items is not a load, but a probe hammering
					// somebody else's free API in parallel is bad manners whatever the volume.
					Thread.sleep(120);
				}
				String body = wiki("/timeseries?timestep=" + timestep + "&id=" + itemId);
				JsonObject root = JsonParser.parseString(body).getAsJsonObject();
				return GSON.toJson(root.get("data"));
			});
		});
	}

	/** Parses a candle array from whatever the supplier returns, or an empty list if anything fails. */
	private static List<Candle> fetch(ThrowingSupplier supplier)
	{
		try
		{
			Candle[] series = GSON.fromJson(supplier.get(), Candle[].class);
			return series == null ? new ArrayList<>() : java.util.Arrays.asList(series);
		}
		catch (Exception unavailable)
		{
			return new ArrayList<>();
		}
	}

	private interface ThrowingSupplier
	{
		String get() throws Exception;
	}

	private static String token() throws Exception
	{
		Path file = Paths.get(System.getProperty("user.home"), ".runelite",
			"osrs-flipping-friend", "companion", "companion.properties");
		Properties properties = new Properties();
		try (java.io.Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8))
		{
			properties.load(reader);
		}
		return properties.getProperty("token");
	}

	private static String get(String path, String token) throws Exception
	{
		HttpURLConnection connection = (HttpURLConnection) new URL(BASE + path).openConnection();
		connection.setRequestProperty("X-Flipping-Friend-Token", token);
		connection.setConnectTimeout(2_000);
		connection.setReadTimeout(30_000);
		try (InputStream input = connection.getInputStream())
		{
			return new String(input.readAllBytes(), StandardCharsets.UTF_8);
		}
		finally
		{
			connection.disconnect();
		}
	}

	private static String wiki(String path) throws Exception
	{
		HttpURLConnection connection =
			(HttpURLConnection) new URL(com.flippingfriend.core.WikiApi.BASE + path).openConnection();
		connection.setRequestProperty("User-Agent", com.flippingfriend.core.WikiApi.USER_AGENT);
		connection.setConnectTimeout(5_000);
		connection.setReadTimeout(30_000);
		try (InputStream input = connection.getInputStream())
		{
			return new String(input.readAllBytes(), StandardCharsets.UTF_8);
		}
		finally
		{
			connection.disconnect();
		}
	}

	private PlanProbe()
	{
	}
}
