package com.flippingfriend.companion;

import com.flippingfriend.core.PlanDiagnostics;
import com.flippingfriend.core.PortfolioCandidate;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.BufferedReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Assume;
import org.junit.Test;

/**
 * Not a test. A measurement, run against a captured copy of a live market, that answers "what did
 * the planner see, and what did it do with it".
 * <p>
 * Skipped unless {@code -Dprobe.dir=<dir>} points at a directory holding {@code snap.json} (from
 * {@code /v1/market/snapshot}), {@code mapping.json} and a copy of {@code series-cache.json.gz}.
 */
public class TradeQualityProbe
{
	private static final long COINS = 128_353_754L;
	private static final double HORIZON = 4.0;

	@Test
	public void whatThePlannerSawAndWhatItDidWithIt() throws Exception
	{
		String dir = System.getProperty("probe.dir");
		Assume.assumeTrue("set -Dprobe.dir to run this probe", dir != null);
		Path root = Paths.get(dir);

		JsonObject snap = read(root.resolve("snap.json"));
		Map<Integer, MarketIngestionService.Item> mapping = new HashMap<>();
		for (com.google.gson.JsonElement e : JsonParser.parseString(
			new String(Files.readAllBytes(root.resolve("mapping.json")), StandardCharsets.UTF_8))
			.getAsJsonArray())
		{
			JsonObject o = e.getAsJsonObject();
			int limit = o.has("limit") ? o.get("limit").getAsInt() : 0;
			mapping.put(o.get("id").getAsInt(), new MarketIngestionService.Item(
				o.get("id").getAsInt(), o.get("name").getAsString(), limit,
				o.get("members").getAsBoolean(), limit > 0));
		}

		MarketIngestionService.MarketState market = new MarketIngestionService.MarketState(mapping,
			snap.getAsJsonObject("latest"), snap.getAsJsonObject("fiveMinute"),
			snap.getAsJsonObject("hourly"), snap.get("observedAt").getAsLong());

		SeriesSource series = capturedSeries(root.resolve("series-cache.json.gz"));
		CandidateFactory factory = new CandidateFactory(series);
		// Match the live account, which runs at the boldest level: capture share 0.85, four-hour
		// horizon. The default is BALANCED, and measuring sizing against a different appetite than
		// the one that produced the complaint would answer the wrong question.
		factory.setRiskAppetite(com.flippingfriend.model.RiskAppetite.AGGRESSIVE);

		List<PortfolioCandidate> tactics = factory.build(market, HORIZON, new HashMap<>(), COINS,
			true, Instant.ofEpochSecond(snap.get("observedAt").getAsLong()));

		Path evidence = root.resolve("flipping-friend-evidence.db");
		if (Files.isRegularFile(evidence))
		{
			FillCalibration calibration;
			try (SqliteStore store = SqliteStore.openReadOnly(evidence))
			{
				calibration = FillCalibration.from(store.executionStats());
			}

			CandidateFactory calibratedFactory = new CandidateFactory(series);
			calibratedFactory.setRiskAppetite(
					com.flippingfriend.model.RiskAppetite.AGGRESSIVE);
			calibratedFactory.setCalibration(calibration);

			List<PortfolioCandidate> calibrated = calibratedFactory.build(
					market, HORIZON, new HashMap<>(), COINS, true,
					Instant.ofEpochSecond(snap.get("observedAt").getAsLong()));

			printCalibrationComparison(tactics, calibrated, calibration);
		}

		System.out.println("=== funnel ===");
		PlanDiagnostics funnel = factory.lastFunnel(tactics.size(), COINS);
		System.out.println("in feed      " + funnel.getItemsInFeed());
		System.out.println("quoted       " + funnel.getItemsQuoted());
		System.out.println("shortlisted  " + funnel.getItemsShortlisted());
		System.out.println("analysed     " + funnel.getItemsAnalysed());
		System.out.println("tactics      " + tactics.size());
		System.out.println("vetoes:");
		funnel.getVetoCounts().forEach((reason, count) ->
			System.out.printf("   %5d  %s%n", count, reason));

		System.out.println();
		System.out.println("=== shortlist, in screen order (throughput ranking) ===");
		List<Integer> ids = factory.shortlistIds();
		for (int i = 0; i < ids.size(); i++)
		{
			MarketIngestionService.Item item = mapping.get(ids.get(i));
			JsonObject latest = snap.getAsJsonObject("latest")
				.getAsJsonObject(String.valueOf(ids.get(i)));
			JsonObject hour = snap.getAsJsonObject("hourly")
				.getAsJsonObject(String.valueOf(ids.get(i)));
			long vol = hour == null ? 0
				: num(hour, "highPriceVolume") + num(hour, "lowPriceVolume");
			System.out.printf("%2d %-32s bid %10d ask %10d limit %7d hourlyVol %9d%n",
				i + 1, item == null ? "?" : item.name,
				latest == null ? 0 : num(latest, "low"), latest == null ? 0 : num(latest, "high"),
				item == null ? 0 : item.buyLimit, vol);
		}

		System.out.println();
		System.out.println("=== every tactic generated, best first ===");
		tactics.sort(Comparator.comparingDouble(PortfolioCandidate::expectedGpPerSlotHour).reversed());
		System.out.printf("%-26s %7s %7s %11s %11s %6s %6s %7s %10s%n",
			"item", "qty", "%limit", "capital", "netProfit", "buyH", "sellH", "slotH", "gp/slot-h");
		for (PortfolioCandidate c : tactics)
		{
			MarketIngestionService.Item item = mapping.get(c.getItemId());
			int limit = item == null ? 0 : item.buyLimit;
			// Scale-invariant: both sides move with the order, so this is the same ratio the sizing
			// saw. The probability is the one measured at the *final* size, which is at least as
			// high as the one sizing used -- so the fraction below is an upper bound on the fraction
			// actually applied. If even that sits on the floor, the floor is what decided the order.
			double b = c.getUnwindLoss() > 0 ? (double) c.getNetProfit() / c.getUnwindLoss() : 0;
			double kelly = CandidateFactory.kellyFraction(
				c.getBuyFillProbability() * c.getSellFillProbability(), b);
			System.out.printf("%-26s %7d %6.1f%% %11d %11d %6.2f %6.2f %7.2f %10.0f%n",
				trim(c.getItemName()), c.getQuantity(),
				limit > 0 ? 100.0 * c.getQuantity() / limit : 0,
				c.getCapitalRequired(), c.getNetProfit(), c.getBuyHours(), c.getSellHours(),
				c.expectedSlotHours(), c.expectedGpPerSlotHour());
			if (kelly < 0 || b < 0)
			{
				System.out.print("");
			}
		}

		System.out.println();
		System.out.println("=== throughput the fill model believes in, against what actually traded ===");
		System.out.printf("%-28s %10s %12s %12s %8s %9s%n",
			"item", "hourlyVol", "model buy/h", "model sell/h", "% of vol", "buyLimit");
		com.flippingfriend.model.FillModel model = new com.flippingfriend.model.FillModel();
		for (int id : ids)
		{
			List<com.flippingfriend.data.Candle> bars = series.series(id, "5m");
			if (bars.isEmpty())
			{
				continue;
			}
			com.flippingfriend.model.FillCurve curve = com.flippingfriend.model.FillCurve.from(bars);
			if (curve.isEmpty())
			{
				continue;
			}
			JsonObject latest = snap.getAsJsonObject("latest").getAsJsonObject(String.valueOf(id));
			JsonObject hour = snap.getAsJsonObject("hourly").getAsJsonObject(String.valueOf(id));
			if (latest == null || hour == null)
			{
				continue;
			}
			long vol = num(hour, "highPriceVolume") + num(hour, "lowPriceVolume");
			double buyRate = model.estimateBuy(curve, num(latest, "low"), 1, HORIZON, 1.0)
				.getUnitsPerHour();
			double sellRate = model.estimateSell(curve, num(latest, "high"), 1, HORIZON, 1.0)
				.getUnitsPerHour();
			MarketIngestionService.Item item = mapping.get(id);
			System.out.printf("%-28s %10d %12.0f %12.0f %7.1f%% %9d%n",
				trim(item == null ? "?" : item.name), vol, buyRate, sellRate,
				vol > 0 ? 100.0 * Math.min(buyRate, sellRate) / vol : 0,
				item == null ? 0 : item.buyLimit);
		}

		System.out.println();
		System.out.println("=== what actually caps the order, leg by leg (at the quoted bid/ask) ===");
		com.flippingfriend.model.FillModel live =
			new com.flippingfriend.model.FillModel().withCaptureRate(
				com.flippingfriend.model.RiskAppetite.AGGRESSIVE.getCaptureShare());
		System.out.printf("%-26s %7s %7s %6s %6s %8s %8s %8s %8s %8s %7s %6s  %s%n",
			"item", "buy/h", "sell/h", "bWait", "sWait", "buyReach", "selReach", "byLimit",
			"byCoins", "fillable", "kelly", "qty", "capped by");
		for (int id : ids)
		{
			List<com.flippingfriend.data.Candle> bars = series.series(id, "5m");
			JsonObject latest = snap.getAsJsonObject("latest").getAsJsonObject(String.valueOf(id));
			JsonObject hour = snap.getAsJsonObject("hourly").getAsJsonObject(String.valueOf(id));
			if (bars.isEmpty() || latest == null || hour == null)
			{
				continue;
			}
			long vol = num(hour, "highPriceVolume") + num(hour, "lowPriceVolume");
			if (vol < 2000)
			{
				continue;
			}
			com.flippingfriend.model.FillCurve curve =
				com.flippingfriend.model.FillCurve.overRecentHistory(bars);
			if (curve.isEmpty())
			{
				continue;
			}
			MarketIngestionService.Item item = mapping.get(id);
			int bid = num(latest, "low");
			int ask = num(latest, "high");

			// The same arithmetic quotableQuantity does, from public parts, so the ceiling that
			// actually binds can be named rather than inferred from the order that came out.
			com.flippingfriend.model.FillEstimate buySide = live.estimateBuy(curve, bid, 1, HORIZON, 1.0);
			com.flippingfriend.model.FillEstimate sellSide = live.estimateSell(curve, ask, 1, HORIZON, 1.0);
			double buyReach = buySide.getUnitsPerHour() * buySide.tradingHoursWithin(HORIZON);
			double sellReach = sellSide.getUnitsPerHour() * sellSide.tradingHoursWithin(HORIZON);
			double reachable = Math.min(buyReach, sellReach);
			long byLimit = item == null ? 0 : item.buyLimit;
			long byCoins = COINS / Math.max(1, bid);
			long fillable = Math.min(Math.min(byLimit, byCoins), (long) reachable);

			String capped = fillable == byLimit ? "buy limit"
				: fillable == byCoins ? "coins"
				: buyReach <= sellReach ? "BUY flow" : "SELL flow";
			System.out.printf("%-26s %7.0f %7.0f %6.2f %6.2f %8.0f %8.0f %8d %8d %8d %7s %6s  %s%n",
				trim(item == null ? "?" : item.name), buySide.getUnitsPerHour(),
				sellSide.getUnitsPerHour(), buySide.getWaitHours(), sellSide.getWaitHours(),
				buyReach, sellReach, byLimit, byCoins, fillable, "-", "-", capped);
		}

		System.out.println();
		System.out.println("=== the same reachability at shorter windows (bars of 5 minutes) ===");
		int[] windows = { 365, 144, 72, 48, 24 };
		System.out.printf("%-28s %9s", "item", "hourlyVol");
		for (int w : windows)
		{
			System.out.printf(" %11s", w + "b/" + (w / 12) + "h");
		}
		System.out.println();
		for (int id : ids)
		{
			List<com.flippingfriend.data.Candle> all = series.series(id, "5m");
			JsonObject latest = snap.getAsJsonObject("latest").getAsJsonObject(String.valueOf(id));
			JsonObject hour = snap.getAsJsonObject("hourly").getAsJsonObject(String.valueOf(id));
			if (all.size() < 30 || latest == null || hour == null)
			{
				continue;
			}
			long vol = num(hour, "highPriceVolume") + num(hour, "lowPriceVolume");
			if (vol < 2000)
			{
				continue;
			}
			MarketIngestionService.Item item = mapping.get(id);
			System.out.printf("%-28s %9d", trim(item == null ? "?" : item.name), vol);
			for (int w : windows)
			{
				List<com.flippingfriend.data.Candle> tail =
					all.subList(Math.max(0, all.size() - w), all.size());
				com.flippingfriend.model.FillCurve curve =
					com.flippingfriend.model.FillCurve.from(tail);
				if (curve.isEmpty())
				{
					System.out.printf(" %11s", "-");
					continue;
				}
				double buy = model.estimateBuy(curve, num(latest, "low"), 1, HORIZON, 1.0)
					.getUnitsPerHour();
				double sell = model.estimateSell(curve, num(latest, "high"), 1, HORIZON, 1.0)
					.getUnitsPerHour();
				System.out.printf(" %5.0f/%-5.0f", buy, sell);
			}
			System.out.println();
		}

		System.out.println();
		System.out.println("=== the fate of every shortlisted item ===");
		Map<Integer, Integer> tacticCount = new HashMap<>();
		for (PortfolioCandidate c : tactics)
		{
			tacticCount.merge(c.getItemId(), 1, Integer::sum);
		}
		for (int i = 0; i < ids.size(); i++)
		{
			int id = ids.get(i);
			MarketIngestionService.Item item = mapping.get(id);
			JsonObject hour = snap.getAsJsonObject("hourly").getAsJsonObject(String.valueOf(id));
			long vol = hour == null ? 0 : num(hour, "highPriceVolume") + num(hour, "lowPriceVolume");
			int made = tacticCount.getOrDefault(id, 0);
			String veto = factory.vetoFor(id);
			int fiveMin = series.series(id, "5m").size();
			System.out.printf("%2d %-32s vol %8d  5mBars %5d  tactics %2d  %s%n",
				i + 1, item == null ? "?" : trim(item.name), vol, fiveMin, made,
				veto != null ? "VETO: " + veto : (made == 0 ? "no price/size combination survived" : ""));
		}

		System.out.println();
		System.out.println("=== how many distinct items produced a tactic ===");
		List<Integer> distinct = new ArrayList<>();
		for (PortfolioCandidate c : tactics)
		{
			if (!distinct.contains(c.getItemId()))
			{
				distinct.add(c.getItemId());
			}
		}
		System.out.println(distinct.size() + " items, " + tactics.size() + " tactics");
	}

	/** Captured histories as they existed at capture time, without live cache expiry. */
	private static SeriesSource capturedSeries(Path path) throws Exception
	{
		Map<String, List<com.flippingfriend.data.Candle>> captured = new HashMap<>();
		try (java.io.Reader reader = new java.io.InputStreamReader(
				new java.util.zip.GZIPInputStream(Files.newInputStream(path)),
				StandardCharsets.UTF_8))
		{
			JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
			for (com.google.gson.JsonElement element : root.getAsJsonArray("entries"))
			{
				JsonObject stored = element.getAsJsonObject();
				String key = stored.get("key").getAsString();
				List<com.flippingfriend.data.Candle> candles = new ArrayList<>();
				for (com.google.gson.JsonElement barElement : stored.getAsJsonArray("data"))
				{
					JsonObject bar = barElement.getAsJsonObject();
					candles.add(new com.flippingfriend.data.Candle(
							bar.get("timestamp").getAsLong(),
							nullableInt(bar, "avgHighPrice"),
							nullableInt(bar, "avgLowPrice"),
							num(bar, "highPriceVolume"),
							num(bar, "lowPriceVolume")));
				}
				captured.put(key, java.util.Collections.unmodifiableList(candles));
			}
		}

		return (itemId, timestep) -> captured.getOrDefault(
				itemId + "@" + timestep, java.util.Collections.emptyList());
	}

	private static Integer nullableInt(JsonObject object, String field)
	{
		return object.has(field) && !object.get(field).isJsonNull()
				? object.get(field).getAsInt() : null;
	}

	private static void printCalibrationComparison(
			List<PortfolioCandidate> neutral,
			List<PortfolioCandidate> calibrated,
			FillCalibration calibration)
	{
		List<PortfolioCandidate> neutralRanked = new ArrayList<>(neutral);
		List<PortfolioCandidate> calibratedRanked = new ArrayList<>(calibrated);
		Comparator<PortfolioCandidate> rank = Comparator
				.comparingDouble(PortfolioCandidate::expectedGpPerSlotHour)
				.reversed();
		neutralRanked.sort(rank);
		calibratedRanked.sort(rank);

		Map<String, PortfolioCandidate> neutralByTactic = new HashMap<>();
		Map<String, Integer> neutralRanks = new HashMap<>();
		for (int i = 0; i < neutralRanked.size(); i++)
		{
			PortfolioCandidate candidate = neutralRanked.get(i);
			String key = tacticKey(candidate);
			neutralByTactic.put(key, candidate);
			neutralRanks.put(key, i + 1);
		}

		System.out.println();
		System.out.println(
				"=== neutral versus production-calibrated ranking ===");
		System.out.printf(
				"production calibration: %.2fx overall, %d item corrections%n",
				calibration.overall(), calibration.itemsLearned());
		System.out.printf(
				"%-4s %-4s %-26s %6s %8s %8s %10s %10s%n",
				"cal", "raw", "item", "qty", "rawSlot", "calSlot",
				"raw GP/h", "cal GP/h");

		int shown = Math.min(20, calibratedRanked.size());
		for (int i = 0; i < shown; i++)
		{
			PortfolioCandidate adjusted = calibratedRanked.get(i);
			String key = tacticKey(adjusted);
			PortfolioCandidate raw = neutralByTactic.get(key);
			Integer rawRank = neutralRanks.get(key);

			System.out.printf(
					"%4d %4s %-26s %6d %8s %8.2f %10s %10.0f%n",
					i + 1,
					rawRank == null ? "-" : rawRank.toString(),
					trim(adjusted.getItemName()),
					adjusted.getQuantity(),
					raw == null
							? "-"
							: String.format("%.2f", raw.expectedSlotHours()),
					adjusted.expectedSlotHours(),
					raw == null
							? "-"
							: String.format(
									"%.0f", raw.expectedGpPerSlotHour()),
					adjusted.expectedGpPerSlotHour());
		}
	}

	private static String tacticKey(PortfolioCandidate candidate)
	{
		return candidate.getItemId() + ":" + candidate.getBuyPrice() + ":"
				+ candidate.getSellPrice() + ":" + candidate.getQuantity();
	}

	private static String trim(String s)
	{
		return s == null ? "?" : s.length() > 30 ? s.substring(0, 30) : s;
	}

	private static int num(JsonObject o, String field)
	{
		return o.has(field) && !o.get(field).isJsonNull() ? o.get(field).getAsInt() : 0;
	}

	private static JsonObject read(Path path) throws Exception
	{
		try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8))
		{
			return JsonParser.parseReader(reader).getAsJsonObject();
		}
	}
}
