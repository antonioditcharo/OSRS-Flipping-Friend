package com.flippingfriend.companion;

import com.flippingfriend.core.PlanDiagnostics;
import com.flippingfriend.core.PortfolioCandidate;
import com.flippingfriend.data.Candle;
import com.flippingfriend.model.FillCurve;
import com.flippingfriend.model.FillEstimate;
import com.flippingfriend.model.FillModel;
import com.flippingfriend.model.MarketContext;
import com.flippingfriend.model.RiskAppetite;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.File;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.IntToDoubleFunction;
import java.util.zip.GZIPInputStream;
import org.junit.Assume;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Diagnostic-only fixed-universe rank sensitivity analysis over the Package 1.25 capture.
 *
 * <p>This test does not propose or change a production calibration setting. Cancelled elapsed time
 * remains a lower bound, completed-only evidence remains selected, and retained evidence remains
 * distinct from the production aggregate. Alternative scenarios rescore the neutral survivor set and
 * do not measure scenario-specific tactic generation or per-item trimming. Skipped unless
 * {@code -Dprobe.dir=<dir>} is supplied.
 */
public class CalibrationSensitivityProbe
{
    private static final long COINS = 128_353_754L;
    private static final double HORIZON = 4.0;
    private static final double GLOBAL_PRIOR = 10.0;
    private static final double ITEM_PRIOR = 3.0;
    private static final double CEILING = 2.0;
    private static final int EXPECTED_FEED = 1739;
    private static final int EXPECTED_SHORTLIST = 90;
    private static final int EXPECTED_TACTICS = 90;
    private static final int EXPECTED_DISTINCT_ITEMS = 31;

    @Test
    public void measureCalibrationRankingSensitivity() throws Exception
    {
        String dir = System.getProperty("probe.dir");
        Assume.assumeTrue("set -Dprobe.dir to run this probe", dir != null);
        Path root = Paths.get(dir);
        Path evidence = root.resolve("flipping-friend-evidence.db");
        Assume.assumeTrue("evidence database is required", Files.isRegularFile(evidence));

        JsonObject snap = read(root.resolve("snap.json"));
        Map<Integer, MarketIngestionService.Item> mapping = mapping(root.resolve("mapping.json"));
        MarketIngestionService.MarketState market = new MarketIngestionService.MarketState(mapping,
                snap.getAsJsonObject("latest"), snap.getAsJsonObject("fiveMinute"),
                snap.getAsJsonObject("hourly"), snap.get("observedAt").getAsLong());
        SeriesSource series = capturedSeries(root.resolve("series-cache.json.gz"));
        Instant observedAt = Instant.ofEpochSecond(snap.get("observedAt").getAsLong());

        CandidateFactory neutralFactory = factory(series);
        List<PortfolioCandidate> neutral = neutralFactory.build(market, HORIZON,
                new HashMap<>(), COINS, true, observedAt);
        PlanDiagnostics funnel = neutralFactory.lastFunnel(neutral.size(), COINS);
        assertBaseline(funnel, neutral);

        Map<Integer, SqliteStore.ExecutionStat> productionStats;
        FillCalibration productionCalibration;
        try (SqliteStore store = SqliteStore.openReadOnly(evidence))
        {
            productionStats = store.executionStats();
            productionCalibration = FillCalibration.from(productionStats);
        }
        CandidateFactory productionFactory = factory(series);
        productionFactory.setCalibration(productionCalibration);
        List<PortfolioCandidate> production = productionFactory.build(market, HORIZON,
                new HashMap<>(), COINS, true, observedAt);
        assertEquals("production tactic count must reproduce", EXPECTED_TACTICS, production.size());
        assertEquals("production overall multiplier", 0.5, productionCalibration.overall(), 1e-12);
        assertEquals("production item corrections", 64, productionCalibration.itemsLearned());

        Evidence retained = retainedEvidence(evidence);
        assertEquals("production open observations", 120, retained.productionCount);
        assertEquals("retained comparable observations", 102, retained.mixed.count);
        assertEquals("retained completed observations", 40, retained.completed.count);
        assertEquals("lineage gap", 18, retained.productionCount - retained.mixed.count);

        LinkedHashMap<String, Scenario> scenarios = new LinkedHashMap<>();
        scenarios.put("neutral", Scenario.ready("neutral", "no calibration", 1.0,
                id -> 1.0, 0, 0, 0, neutral));
        scenarios.put("production", Scenario.ready("production", "current production calibration",
                productionCalibration.overall(), productionCalibration::waitMultiplier,
                productionCalibration.itemsLearned(), countAtBound(productionStats, 0.5, 0.5),
                countAtBound(productionStats, 0.5, 2.0), production));

        for (double floor : new double[] { 0.4, 0.3, 0.2, 0.1 })
        {
            DiagnosticCalibration calibration = DiagnosticCalibration.fromProduction(productionStats, floor);
            scenarios.put(String.format("production floor %.2f", floor), scenario(
                    String.format("production floor %.2f", floor),
                    "production population with diagnostic lower bound", calibration,
                    rebuild(neutral, series, observedAt, calibration::multiplier)));
        }

        DiagnosticCalibration retainedMixed = DiagnosticCalibration.fromTotals(retained.mixed, 0.1);
        scenarios.put("retained mixed", scenario("retained mixed",
                "heuristic pooled lower-bound retained population; not a survival estimator",
                retainedMixed, rebuild(neutral, series, observedAt, retainedMixed::multiplier)));

        DiagnosticCalibration retainedCompleted = DiagnosticCalibration.fromTotals(retained.completed, 0.1);
        scenarios.put("retained completed only", scenario("retained completed only",
                "selected completed-outcome population; not assumed unbiased",
                retainedCompleted, rebuild(neutral, series, observedAt, retainedCompleted::multiplier)));

        double rawProductionRatio = ratio(retained.productionOpen, retained.productionPredicted);
        scenarios.put("raw uncapped production ratio", Scenario.ready(
                "raw uncapped production ratio",
                "extreme global-only stress reference; not a production candidate",
                rawProductionRatio, id -> rawProductionRatio, 0, 0, 0,
                rebuild(neutral, series, observedAt, id -> rawProductionRatio)));

        printHeader(retained);
        for (Scenario candidate : scenarios.values())
        {
            printScenario(candidate, scenarios.get("neutral"), scenarios.get("production"));
        }
        printCrossScenarioTop(scenarios, 20);
        printBoundary();
    }

    private static CandidateFactory factory(SeriesSource series)
    {
        CandidateFactory factory = new CandidateFactory(series);
        factory.setRiskAppetite(RiskAppetite.AGGRESSIVE);
        return factory;
    }

    private static void assertBaseline(PlanDiagnostics funnel, List<PortfolioCandidate> tactics)
    {
        assertEquals("feed baseline drifted", EXPECTED_FEED, funnel.getItemsInFeed());
        assertEquals("quoted baseline drifted", EXPECTED_FEED, funnel.getItemsQuoted());
        assertEquals("shortlist baseline drifted", EXPECTED_SHORTLIST, funnel.getItemsShortlisted());
        assertEquals("analysis baseline drifted", EXPECTED_SHORTLIST, funnel.getItemsAnalysed());
        assertEquals("tactic baseline drifted", EXPECTED_TACTICS, tactics.size());
        assertEquals("distinct-item baseline drifted", EXPECTED_DISTINCT_ITEMS, distinctItems(tactics));
    }

    private static Scenario scenario(String name, String label, DiagnosticCalibration calibration,
            List<PortfolioCandidate> candidates)
    {
        return Scenario.ready(name, label, calibration.global, calibration::multiplier,
                calibration.byItem.size(), calibration.atFloor, calibration.atCeiling, candidates);
    }

    private static List<PortfolioCandidate> rebuild(List<PortfolioCandidate> neutral, SeriesSource series,
            Instant observedAt, IntToDoubleFunction multiplier)
    {
        FillModel model = new FillModel().withCaptureRate(RiskAppetite.AGGRESSIVE.getCaptureShare());
        Map<Integer, FillCurve> curves = new HashMap<>();
        Map<Integer, Double> seasons = new HashMap<>();
        List<PortfolioCandidate> rebuilt = new ArrayList<>();
        for (PortfolioCandidate original : neutral)
        {
            int item = original.getItemId();
            FillCurve curve = curves.computeIfAbsent(item,
                    id -> FillCurve.overRecentHistory(series.series(id, "5m")));
            double season = seasons.computeIfAbsent(item, id -> {
                MarketContext context = MarketContext.from(series.series(id, "1h"), original.getBuyPrice());
                return context.isUsable()
                        ? context.liquidityMultiplier(observedAt.atZone(ZoneOffset.UTC).getHour()) : 1.0;
            });
            FillEstimate buy = model.estimateBuy(curve, original.getBuyPrice(), original.getQuantity(),
                    original.getHorizonHours(), season);
            FillEstimate sell = model.estimateSell(curve, original.getSellPrice(), original.getQuantity(),
                    original.getHorizonHours(), season);
            assertTrue("captured neutral tactic must remain plausible: " + key(original),
                    buy.isPlausible() && sell.isPlausible());
            double factor = multiplier.applyAsDouble(item);
            PortfolioCandidate adjusted = new PortfolioCandidate(item, original.getItemName(),
                    original.getGroup(), original.getTargetBuyPrice(), original.getEquilibriumBuyPrice(),
                    original.getExitBuyPrice(), original.getTargetSellPrice(),
                    original.getEquilibriumSellPrice(), original.getExitSellPrice(),
                    original.getQuantity(), original.getBuyLimitRemaining(), original.getNetProfit(),
                    original.getWorstLoss(), original.getUnwindLoss(),
                    original.getBuyFillProbability(), original.getSellFillProbability(),
                    correctedHours(buy, factor), correctedHours(sell, factor),
                    original.getHorizonHours(), original.getExpiresAt())
                    .withDisplayProbability(original.getDisplayCompletionProbability()
                            / Math.max(1e-12, original.getSellFillProbability()));
            rebuilt.add(adjusted);
        }
        return trimPerItem(rebuilt);
    }

    private static List<PortfolioCandidate> trimPerItem(List<PortfolioCandidate> candidates)
    {
        Map<Integer, List<PortfolioCandidate>> byItem = new LinkedHashMap<>();
        for (PortfolioCandidate candidate : candidates)
        {
            byItem.computeIfAbsent(candidate.getItemId(), ignored -> new ArrayList<>()).add(candidate);
        }
        List<PortfolioCandidate> result = new ArrayList<>();
        Comparator<PortfolioCandidate> rank = rank();
        for (List<PortfolioCandidate> item : byItem.values())
        {
            item.sort(rank);
            result.addAll(item.subList(0, Math.min(3, item.size())));
        }
        return result;
    }

    private static double correctedHours(FillEstimate estimate, double multiplier)
    {
        double wait = estimate.getWaitHours();
        if (wait <= 0 || !Double.isFinite(wait)) return estimate.getExpectedHours();
        return wait * multiplier + Math.max(0, estimate.getExpectedHours() - wait);
    }

    private static Evidence retainedEvidence(Path database) throws Exception
    {
        String path = database.toAbsolutePath().toString().replace(File.separatorChar, '/');
        String url = "jdbc:sqlite:file:" + path + "?mode=ro";
        Evidence evidence = new Evidence();
        Set<String> claims = new HashSet<>();
        Map<String, Observation> terminal = new LinkedHashMap<>();
        try (Connection connection = DriverManager.getConnection(url))
        {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT identity FROM counted_offer"); ResultSet result = statement.executeQuery())
            {
                while (result.next()) claims.add(result.getString(1));
            }
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT COALESCE(SUM(open_observed),0), COALESCE(SUM(open_minutes),0), "
                            + "COALESCE(SUM(open_predicted_minutes),0) FROM execution_stat");
                    ResultSet result = statement.executeQuery())
            {
                result.next();
                evidence.productionCount = result.getInt(1);
                evidence.productionOpen = result.getDouble(2);
                evidence.productionPredicted = result.getDouble(3);
            }
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT payload FROM event_log WHERE event_type IN "
                            + "('BOUGHT','SOLD','CANCELLED_BUY','CANCELLED_SELL') ORDER BY id");
                    ResultSet result = statement.executeQuery())
            {
                while (result.next())
                {
                    JsonElement parsed;
                    try { parsed = JsonParser.parseString(result.getString(1)); }
                    catch (RuntimeException invalid) { continue; }
                    if (!parsed.isJsonObject()) continue;
                    JsonObject event = parsed.getAsJsonObject();
                    String type = text(event, "eventType");
                    if (!terminal(type)) continue;
                    int item = integer(event, "itemId");
                    if (item <= 0) continue;
                    long observed = longValue(event, "observedAt");
                    long first = longValue(event, "firstSeenAt");
                    long start = first > 0 ? first : observed;
                    String identity = integer(event, "slot") + "@" + start + ":" + item;
                    if (terminal.containsKey(identity)) continue;
                    double open = first > 0 && observed >= first ? (observed - first) / 60.0 : 0;
                    terminal.put(identity, new Observation(identity, item, type, open,
                            decimal(event, "predictedMinutes")));
                }
            }
        }
        for (Observation observation : terminal.values())
        {
            if (!claims.contains(observation.identity) || observation.open <= 0
                    || observation.predicted <= 0) continue;
            evidence.mixed.add(observation.item, observation.open, observation.predicted);
            if (observation.completed())
                evidence.completed.add(observation.item, observation.open, observation.predicted);
        }
        return evidence;
    }

    private static void printHeader(Evidence evidence)
    {
        System.out.println("=== calibration sensitivity boundaries ===");
        System.out.println("analysis only; production behavior and settings are unchanged");
        System.out.println("alternative scenarios rescore the fixed neutral 90-tactic survivor set");
        System.out.println("they do not measure scenario-specific tactic generation or per-item trimming");
        System.out.printf("production observations %d  retained mixed %d  retained completed %d  lineage gap %d%n",
                evidence.productionCount, evidence.mixed.count, evidence.completed.count,
                evidence.productionCount - evidence.mixed.count);
        System.out.println("cancelled elapsed time is a lower bound, not realized fill time");
        System.out.println("retained mixed is a heuristic pooled lower-bound population, not a survival estimator");
        System.out.println("completed-only is selected and is not assumed unbiased");
    }

    private static void printScenario(Scenario scenario, Scenario neutral, Scenario production)
    {
        List<PortfolioCandidate> ranked = ranked(scenario.candidates);

        System.out.println();
        System.out.println("=== scenario: " + scenario.name + " ===");
        System.out.println("label: " + scenario.label);
        System.out.printf("global %.6f  item corrections %d  at floor %d  at ceiling %d%n",
                scenario.global, scenario.items, scenario.atFloor, scenario.atCeiling);
        System.out.printf("feed %d  quoted %d  shortlisted %d  analysed %d  tactics %d  distinct items %d%n",
                EXPECTED_FEED, EXPECTED_FEED, EXPECTED_SHORTLIST, EXPECTED_SHORTLIST,
                scenario.candidates.size(), distinctItems(scenario.candidates));
        if ("production".equals(scenario.name))
        {
            Set<String> neutralKeys = keys(neutral.candidates);
            Set<String> productionKeys = keys(production.candidates);
            System.out.printf("production versus neutral: added %d removed %d%n",
                    difference(productionKeys, neutralKeys), difference(neutralKeys, productionKeys));
        }
        else if (!"neutral".equals(scenario.name))
        {
            System.out.println(
                    "fixed-universe scenario: tactic additions, removals, and trimming not measured");
        }
        System.out.printf("top-10 capital %,d  distinct items %d  completion<0.50 %d%n",
                ranked.stream().limit(10).mapToLong(PortfolioCandidate::getCapitalRequired).sum(),
                distinctItems(ranked.subList(0, Math.min(10, ranked.size()))),
                ranked.stream().filter(c -> c.getCompletionProbability() < 0.50).count());
        System.out.printf("%-4s %-4s %-4s %-26s %6s %8s %10s %8s%n",
                "rank", "raw", "prod", "item", "qty", "slotH", "GP/h", "complete");
        Map<String, Integer> rawRanks = ranks(neutral.candidates);
        Map<String, Integer> productionRanks = ranks(production.candidates);
        for (int i = 0; i < Math.min(20, ranked.size()); i++)
        {
            PortfolioCandidate candidate = ranked.get(i);
            String key = key(candidate);
            System.out.printf("%4d %4s %4s %-26s %6d %8.2f %10.0f %7.1f%%%n",
                    i + 1, rankText(rawRanks.get(key)), rankText(productionRanks.get(key)),
                    trim(candidate.getItemName()), candidate.getQuantity(),
                    candidate.expectedSlotHours(), candidate.expectedGpPerSlotHour(),
                    100 * candidate.getCompletionProbability());
        }
    }

    private static void printCrossScenarioTop(Map<String, Scenario> scenarios, int limit)
    {
        System.out.println();
        System.out.println("=== cross-scenario top-rank stability ===");
        Set<String> union = new LinkedHashSet<>();
        for (Scenario scenario : scenarios.values())
            ranked(scenario.candidates).stream().limit(limit).map(CalibrationSensitivityProbe::key)
                    .forEach(union::add);
        for (String key : union)
        {
            StringBuilder line = new StringBuilder(key);
            for (Scenario scenario : scenarios.values())
                line.append(" | ").append(scenario.name).append('=').append(rankText(ranks(scenario.candidates).get(key)));
            System.out.println(line);
        }
    }

    private static void printBoundary()
    {
        System.out.println();
        System.out.println("=== interpretation boundary ===");
        System.out.println("These are in-sample ranking sensitivity results, not held-out predictive validation.");
        System.out.println("No diagnostic floor, retained population, or uncapped ratio is promoted to production.");
        System.out.println("Side-separated and quantity-specific calibration are not applied because this package does not");
        System.out.println("have adequate item-level evidence for those additional assumptions.");
        System.out.println("Alternative tactic generation, per-item trimming, and final portfolio optimization");
        System.out.println("are outside this fixed-universe ranking package because production exposes no");
        System.out.println("diagnostic pre-trim seam.");
    }

    private static int countAtBound(Map<Integer, SqliteStore.ExecutionStat> stats, double floor, double bound)
    {
        DiagnosticCalibration calibration = DiagnosticCalibration.fromProduction(stats, floor);
        return Math.abs(bound - floor) < 1e-12 ? calibration.atFloor : calibration.atCeiling;
    }

    private static Comparator<PortfolioCandidate> rank()
    {
        return Comparator.comparingDouble(PortfolioCandidate::expectedGpPerSlotHour).reversed()
                .thenComparingInt(PortfolioCandidate::getItemId)
                .thenComparingInt(PortfolioCandidate::getBuyPrice)
                .thenComparingInt(PortfolioCandidate::getSellPrice)
                .thenComparingInt(PortfolioCandidate::getQuantity);
    }

    private static List<PortfolioCandidate> ranked(List<PortfolioCandidate> candidates)
    {
        List<PortfolioCandidate> result = new ArrayList<>(candidates);
        result.sort(rank());
        return result;
    }

    private static Map<String, Integer> ranks(List<PortfolioCandidate> candidates)
    {
        List<PortfolioCandidate> ranked = ranked(candidates);
        Map<String, Integer> result = new HashMap<>();
        for (int i = 0; i < ranked.size(); i++) result.put(key(ranked.get(i)), i + 1);
        return result;
    }

    private static Set<String> keys(List<PortfolioCandidate> candidates)
    {
        Set<String> result = new TreeSet<>();
        for (PortfolioCandidate candidate : candidates) result.add(key(candidate));
        return result;
    }

    private static int difference(Set<String> left, Set<String> right)
    {
        int count = 0;
        for (String key : left) if (!right.contains(key)) count++;
        return count;
    }

    private static int distinctItems(List<PortfolioCandidate> candidates)
    {
        Set<Integer> items = new HashSet<>();
        for (PortfolioCandidate candidate : candidates) items.add(candidate.getItemId());
        return items.size();
    }

    private static String key(PortfolioCandidate candidate)
    {
        return candidate.getItemId() + ":" + candidate.getBuyPrice() + ":"
                + candidate.getSellPrice() + ":" + candidate.getQuantity();
    }

    private static String rankText(Integer rank) { return rank == null ? "-" : rank.toString(); }
    private static String trim(String value) { return value.length() > 26 ? value.substring(0, 26) : value; }
    private static double ratio(double actual, double predicted) { return predicted > 0 ? actual / predicted : Double.NaN; }
    private static double clamp(double value, double floor) { return Math.max(floor, Math.min(CEILING, value)); }

    private static Map<Integer, MarketIngestionService.Item> mapping(Path path) throws Exception
    {
        Map<Integer, MarketIngestionService.Item> mapping = new HashMap<>();
        for (JsonElement element : JsonParser.parseString(
                new String(Files.readAllBytes(path), StandardCharsets.UTF_8)).getAsJsonArray())
        {
            JsonObject object = element.getAsJsonObject();
            int limit = object.has("limit") ? object.get("limit").getAsInt() : 0;
            int id = object.get("id").getAsInt();
            mapping.put(id, new MarketIngestionService.Item(id, object.get("name").getAsString(),
                    limit, object.get("members").getAsBoolean(), limit > 0));
        }
        return mapping;
    }

    private static SeriesSource capturedSeries(Path path) throws Exception
    {
        Map<String, List<Candle>> captured = new HashMap<>();
        try (Reader reader = new InputStreamReader(new GZIPInputStream(Files.newInputStream(path)),
                StandardCharsets.UTF_8))
        {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            for (JsonElement element : root.getAsJsonArray("entries"))
            {
                JsonObject stored = element.getAsJsonObject();
                List<Candle> candles = new ArrayList<>();
                for (JsonElement barElement : stored.getAsJsonArray("data"))
                {
                    JsonObject bar = barElement.getAsJsonObject();
                    candles.add(new Candle(bar.get("timestamp").getAsLong(), nullableInt(bar, "avgHighPrice"),
                            nullableInt(bar, "avgLowPrice"), integer(bar, "highPriceVolume"),
                            integer(bar, "lowPriceVolume")));
                }
                captured.put(stored.get("key").getAsString(), Collections.unmodifiableList(candles));
            }
        }
        return (itemId, timestep) -> captured.getOrDefault(itemId + "@" + timestep,
                Collections.emptyList());
    }

    private static JsonObject read(Path path) throws Exception
    {
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8))
        {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }

    private static Integer nullableInt(JsonObject object, String field)
    {
        return object.has(field) && !object.get(field).isJsonNull() ? object.get(field).getAsInt() : null;
    }
    private static String text(JsonObject object, String field)
    {
        return object.has(field) && !object.get(field).isJsonNull() ? object.get(field).getAsString() : "";
    }
    private static int integer(JsonObject object, String field)
    {
        return object.has(field) && !object.get(field).isJsonNull() ? object.get(field).getAsInt() : 0;
    }
    private static long longValue(JsonObject object, String field)
    {
        return object.has(field) && !object.get(field).isJsonNull() ? object.get(field).getAsLong() : 0;
    }
    private static double decimal(JsonObject object, String field)
    {
        if (!object.has(field) || object.get(field).isJsonNull()) return 0;
        double value = object.get(field).getAsDouble();
        return Double.isFinite(value) ? value : 0;
    }
    private static boolean terminal(String type)
    {
        return "BOUGHT".equals(type) || "SOLD".equals(type)
                || "CANCELLED_BUY".equals(type) || "CANCELLED_SELL".equals(type);
    }

    private static final class Observation
    {
        private final String identity;
        private final int item;
        private final String type;
        private final double open;
        private final double predicted;
        private Observation(String identity, int item, String type, double open, double predicted)
        {
            this.identity = identity; this.item = item; this.type = type;
            this.open = open; this.predicted = predicted;
        }
        private boolean completed() { return "BOUGHT".equals(type) || "SOLD".equals(type); }
    }

    private static final class Totals
    {
        private int count;
        private double open;
        private double predicted;
        private final Map<Integer, Totals> byItem = new HashMap<>();
        private void add(int item, double actual, double estimate)
        {
            count++; open += actual; predicted += estimate;
            Totals own = byItem.computeIfAbsent(item, ignored -> new Totals());
            own.count++; own.open += actual; own.predicted += estimate;
        }
    }

    private static final class Evidence
    {
        private int productionCount;
        private double productionOpen;
        private double productionPredicted;
        private final Totals mixed = new Totals();
        private final Totals completed = new Totals();
    }

    private static final class DiagnosticCalibration
    {
        private final double global;
        private final Map<Integer, Double> byItem;
        private final double floor;
        private final int atFloor;
        private final int atCeiling;
        private DiagnosticCalibration(double global, Map<Integer, Double> byItem, double floor)
        {
            this.global = global; this.byItem = byItem; this.floor = floor;
            int floors = 0, ceilings = 0;
            for (double value : byItem.values())
            {
                if (Math.abs(value - floor) < 1e-12) floors++;
                if (Math.abs(value - CEILING) < 1e-12) ceilings++;
            }
            this.atFloor = floors; this.atCeiling = ceilings;
        }
        private double multiplier(int item) { return byItem.getOrDefault(item, global); }
        private static DiagnosticCalibration fromProduction(
                Map<Integer, SqliteStore.ExecutionStat> stats, double floor)
        {
            Totals totals = new Totals();
            for (Map.Entry<Integer, SqliteStore.ExecutionStat> entry : stats.entrySet())
            {
                SqliteStore.ExecutionStat stat = entry.getValue();
                if (stat.openObserved <= 0 || stat.openMinutes <= 0 || stat.openPredictedMinutes <= 0) continue;
                Totals own = new Totals(); own.count = stat.openObserved;
                own.open = stat.openMinutes; own.predicted = stat.openPredictedMinutes;
                totals.count += own.count; totals.open += own.open; totals.predicted += own.predicted;
                totals.byItem.put(entry.getKey(), own);
            }
            return fromTotals(totals, floor);
        }
        private static DiagnosticCalibration fromTotals(Totals totals, double floor)
        {
            double raw = ratio(totals.open, totals.predicted);
            double global = clamp(shrink(raw, totals.count, GLOBAL_PRIOR, 1.0), floor);
            Map<Integer, Double> items = new HashMap<>();
            for (Map.Entry<Integer, Totals> entry : totals.byItem.entrySet())
            {
                Totals own = entry.getValue();
                items.put(entry.getKey(), clamp(shrink(ratio(own.open, own.predicted),
                        own.count, ITEM_PRIOR, global), floor));
            }
            return new DiagnosticCalibration(global, items, floor);
        }
        private static double shrink(double observed, int count, double prior, double fallback)
        {
            double weight = count / (count + prior);
            return weight * observed + (1 - weight) * fallback;
        }
    }

    private static final class Scenario
    {
        private final String name;
        private final String label;
        private final double global;
        private final IntToDoubleFunction multiplier;
        private final int items;
        private final int atFloor;
        private final int atCeiling;
        private final List<PortfolioCandidate> candidates;
        private Scenario(String name, String label, double global, IntToDoubleFunction multiplier,
                int items, int atFloor, int atCeiling, List<PortfolioCandidate> candidates)
        {
            this.name = name; this.label = label; this.global = global; this.multiplier = multiplier;
            this.items = items; this.atFloor = atFloor; this.atCeiling = atCeiling;
            this.candidates = candidates;
        }
        private static Scenario ready(String name, String label, double global,
                IntToDoubleFunction multiplier, int items, int atFloor, int atCeiling,
                List<PortfolioCandidate> candidates)
        {
            return new Scenario(name, label, global, multiplier, items, atFloor, atCeiling, candidates);
        }
    }
}
