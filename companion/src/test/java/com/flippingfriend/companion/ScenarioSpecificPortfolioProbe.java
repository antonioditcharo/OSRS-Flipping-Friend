package com.flippingfriend.companion;

import com.flippingfriend.core.PlanDiagnostics;
import com.flippingfriend.core.PortfolioCandidate;
import com.flippingfriend.data.Candle;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.zip.GZIPInputStream;
import org.junit.Assume;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Diagnostic-only scenario-specific final portfolio sensitivity over the capture.
 *
 * <p>Every scenario enters the real {@link CandidateFactory} before candidate construction and uses
 * {@link CandidateFactory#buildForDiagnostics} to expose its complete generated per-item tactic
 * universe. The probe then applies the production-equivalent first-three-per-item boundary without
 * final portfolio optimization.
 *
 * <p>This probe does not propose or change a production calibration setting. Cancelled elapsed time
 * remains a lower bound, completed-only evidence remains selected, the 18-observation lineage gap
 * remains unresolved, and the raw uncapped scenario remains a global-only stress reference.
 * Skipped unless {@code -Dprobe.dir=<dir>} is supplied.
 */
public class ScenarioSpecificPortfolioProbe
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
    public void measureScenarioSpecificFinalPortfolioSensitivity() throws Exception
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

        Map<Integer, SqliteStore.ExecutionStat> productionStats;
        FillCalibration productionCalibration;
        try (SqliteStore store = SqliteStore.openReadOnly(evidence))
        {
            productionStats = store.executionStats();
            productionCalibration = FillCalibration.from(productionStats);
        }

        Evidence retained = retainedEvidence(evidence);
        assertEquals("production open observations", 120, retained.productionCount);
        assertEquals("retained comparable observations", 102, retained.mixed.count);
        assertEquals("retained completed observations", 40, retained.completed.count);
        assertEquals("lineage gap", 18, retained.productionCount - retained.mixed.count);
        assertEquals("production overall multiplier", 0.5, productionCalibration.overall(), 1e-12);
        assertEquals("production item corrections", 64, productionCalibration.itemsLearned());

        LinkedHashMap<String, GeneratedScenario> scenarios = new LinkedHashMap<>();
        scenarios.put("neutral", generate("neutral", "no calibration",
                FillCalibration.NEUTRAL, market, series, observedAt));
        scenarios.put("production", generate("production", "current production calibration",
                productionCalibration, market, series, observedAt));

        for (double floor : new double[] { 0.4, 0.3, 0.2, 0.1 })
        {
            DiagnosticCalibration calibration =
                    DiagnosticCalibration.fromProduction(productionStats, floor);
            scenarios.put(String.format("production floor %.2f", floor), generate(
                    String.format("production floor %.2f", floor),
                    "production population with diagnostic lower bound",
                    FillCalibration.forDiagnostics(
                            calibration.byItem, calibration.global, floor, CEILING),
                    market, series, observedAt));
        }

        DiagnosticCalibration retainedMixed =
                DiagnosticCalibration.fromTotals(retained.mixed, 0.1);
        scenarios.put("retained mixed", generate(
                "retained mixed",
                "heuristic pooled lower-bound retained population; not a survival estimator",
                FillCalibration.forDiagnostics(
                        retainedMixed.byItem, retainedMixed.global, 0.1, CEILING),
                market, series, observedAt));

        DiagnosticCalibration retainedCompleted =
                DiagnosticCalibration.fromTotals(retained.completed, 0.1);
        scenarios.put("retained completed only", generate(
                "retained completed only",
                "selected completed-outcome population; not assumed unbiased",
                FillCalibration.forDiagnostics(
                        retainedCompleted.byItem, retainedCompleted.global, 0.1, CEILING),
                market, series, observedAt));

        double rawProductionRatio =
                ratio(retained.productionOpen, retained.productionPredicted);
        scenarios.put("raw uncapped production ratio", generate(
                "raw uncapped production ratio",
                "extreme global-only stress reference; not a production candidate",
                FillCalibration.forDiagnostics(
                        Collections.emptyMap(), rawProductionRatio,
                        0.0, Double.POSITIVE_INFINITY),
                market, series, observedAt));

        GeneratedScenario neutral = scenarios.get("neutral");
        GeneratedScenario production = scenarios.get("production");

        assertBaseline(neutral.funnel, neutral.trimmed);
        assertEquals("production trimmed tactic count must reproduce",
                EXPECTED_TACTICS, production.trimmed.size());
        assertProductionPrefix(market, series, observedAt, productionCalibration, production);

        printScenarioHeader(retained, neutral);
        for (GeneratedScenario scenario : scenarios.values())
        {
            printGeneratedScenario(scenario, neutral);
        }
        printAvernic(scenarios);
        printOptimizedScenarios(scenarios, observedAt);
        printScenarioBoundary();
    }

    private static GeneratedScenario generate(String name, String label,
            FillCalibration calibration, MarketIngestionService.MarketState market,
            SeriesSource series, Instant observedAt)
    {
        CandidateFactory factory = factory(series);
        factory.setCalibration(calibration);
        List<PortfolioCandidate> full = factory.buildForDiagnostics(
                market, HORIZON, new HashMap<>(), COINS, true, observedAt);
        List<PortfolioCandidate> trimmed = trimInFactoryOrder(full);
        PlanDiagnostics funnel = factory.lastFunnel(trimmed.size(), COINS);
        return new GeneratedScenario(name, label, calibration, full, trimmed, funnel);
    }

    private static List<PortfolioCandidate> trimInFactoryOrder(
            List<PortfolioCandidate> candidates)
    {
        Map<Integer, List<PortfolioCandidate>> byItem = byItem(candidates);
        List<PortfolioCandidate> result = new ArrayList<>();
        for (List<PortfolioCandidate> item : byItem.values())
        {
            result.addAll(item.subList(0, Math.min(3, item.size())));
        }
        return result;
    }

    private static Map<Integer, List<PortfolioCandidate>> byItem(
            List<PortfolioCandidate> candidates)
    {
        Map<Integer, List<PortfolioCandidate>> result = new LinkedHashMap<>();
        for (PortfolioCandidate candidate : candidates)
        {
            result.computeIfAbsent(candidate.getItemId(), ignored -> new ArrayList<>())
                    .add(candidate);
        }
        return result;
    }

    private static void assertProductionPrefix(
            MarketIngestionService.MarketState market, SeriesSource series,
            Instant observedAt, FillCalibration calibration,
            GeneratedScenario generated)
    {
        CandidateFactory factory = factory(series);
        factory.setCalibration(calibration);
        List<PortfolioCandidate> production = factory.build(
                market, HORIZON, new HashMap<>(), COINS, true, observedAt);

        assertEquals("production build must equal diagnostic first-three trim",
                keys(production), keys(generated.trimmed));
    }

    private static void printScenarioHeader(Evidence evidence, GeneratedScenario neutral)
    {
        System.out.println("=== scenario-specific final portfolio optimization sensitivity ===");
        System.out.println("diagnostic only; no production calibration recommendation");
        System.out.println("cancelled elapsed time remains a lower bound");
        System.out.println("completed-only evidence remains selected");
        System.out.printf("production observations %d  retained comparable %d"
                        + "  retained completed %d  lineage gap %d%n",
                evidence.productionCount, evidence.mixed.count, evidence.completed.count,
                evidence.productionCount - evidence.mixed.count);
        System.out.printf("neutral full universe %d tactics across %d items;"
                        + " trimmed %d tactics across %d items%n",
                neutral.full.size(), distinctItems(neutral.full),
                neutral.trimmed.size(), distinctItems(neutral.trimmed));
    }

    private static void printGeneratedScenario(
            GeneratedScenario scenario, GeneratedScenario neutral)
    {
        Set<String> fullKeys = keys(scenario.full);
        Set<String> neutralFullKeys = keys(neutral.full);
        Set<String> trimmedKeys = keys(scenario.trimmed);
        Set<String> neutralTrimmedKeys = keys(neutral.trimmed);

        Map<Integer, List<PortfolioCandidate>> fullItems = byItem(scenario.full);
        Map<Integer, List<PortfolioCandidate>> neutralFullItems = byItem(neutral.full);
        Map<Integer, List<PortfolioCandidate>> trimmedItems = byItem(scenario.trimmed);
        Map<Integer, List<PortfolioCandidate>> neutralTrimmedItems = byItem(neutral.trimmed);

        System.out.println();
        System.out.println("--- " + scenario.name + " ---");
        System.out.println(scenario.label);
        System.out.printf("global %.6f  item corrections %d%n",
                scenario.calibration.overall(), scenario.calibration.itemsLearned());
        System.out.printf("full tactics %d  full items %d  completion<0.50 %d%n",
                scenario.full.size(), fullItems.size(), belowHalf(scenario.full));
        System.out.printf("trimmed tactics %d  trimmed items %d  completion<0.50 %d%n",
                scenario.trimmed.size(), trimmedItems.size(), belowHalf(scenario.trimmed));
        System.out.printf("full versus neutral: added %d  removed %d%n",
                difference(fullKeys, neutralFullKeys),
                difference(neutralFullKeys, fullKeys));
        System.out.printf("trimmed versus neutral: added %d  removed %d%n",
                difference(trimmedKeys, neutralTrimmedKeys),
                difference(neutralTrimmedKeys, trimmedKeys));
        System.out.printf("items with changed generated tactic set %d%n",
                changedItemSets(fullItems, neutralFullItems));
        System.out.printf("items with changed retained top tactic %d%n",
                changedTopTactics(trimmedItems, neutralTrimmedItems));
        System.out.printf("items with changed retained ordering %d%n",
                changedOrdering(trimmedItems, neutralTrimmedItems));
    }

    private static long belowHalf(List<PortfolioCandidate> candidates)
    {
        return candidates.stream()
                .filter(candidate -> candidate.getCompletionProbability() < 0.50)
                .count();
    }

    private static int changedItemSets(
            Map<Integer, List<PortfolioCandidate>> left,
            Map<Integer, List<PortfolioCandidate>> right)
    {
        Set<Integer> items = new HashSet<>(left.keySet());
        items.addAll(right.keySet());
        int changed = 0;
        for (int item : items)
        {
            if (!keys(left.getOrDefault(item, Collections.emptyList()))
                    .equals(keys(right.getOrDefault(item, Collections.emptyList()))))
            {
                changed++;
            }
        }
        return changed;
    }

    private static int changedTopTactics(
            Map<Integer, List<PortfolioCandidate>> left,
            Map<Integer, List<PortfolioCandidate>> right)
    {
        Set<Integer> items = new HashSet<>(left.keySet());
        items.addAll(right.keySet());
        int changed = 0;
        for (int item : items)
        {
            String leftTop = firstKey(left.get(item));
            String rightTop = firstKey(right.get(item));
            if (!leftTop.equals(rightTop))
            {
                changed++;
            }
        }
        return changed;
    }

    private static int changedOrdering(
            Map<Integer, List<PortfolioCandidate>> left,
            Map<Integer, List<PortfolioCandidate>> right)
    {
        Set<Integer> items = new HashSet<>(left.keySet());
        items.addAll(right.keySet());
        int changed = 0;
        for (int item : items)
        {
            List<String> leftOrder = orderedKeys(
                    left.getOrDefault(item, Collections.emptyList()));
            List<String> rightOrder = orderedKeys(
                    right.getOrDefault(item, Collections.emptyList()));
            if (!leftOrder.equals(rightOrder))
            {
                changed++;
            }
        }
        return changed;
    }

    private static String firstKey(List<PortfolioCandidate> candidates)
    {
        return candidates == null || candidates.isEmpty()
                ? "-" : key(candidates.get(0));
    }

    private static List<String> orderedKeys(List<PortfolioCandidate> candidates)
    {
        List<String> result = new ArrayList<>();
        for (PortfolioCandidate candidate : candidates)
        {
            result.add(key(candidate));
        }
        return result;
    }

    private static void printAvernic(
            Map<String, GeneratedScenario> scenarios)
    {
        System.out.println();
        System.out.println("=== Avernic defender hilt quantity-2 versus quantity-3 ===");
        for (GeneratedScenario scenario : scenarios.values())
        {
            List<PortfolioCandidate> tactics = new ArrayList<>();
            for (PortfolioCandidate candidate : scenario.full)
            {
                if ("Avernic defender hilt".equals(candidate.getItemName())
                        && (candidate.getQuantity() == 2 || candidate.getQuantity() == 3))
                {
                    tactics.add(candidate);
                }
            }

            tactics.sort(rank());

            System.out.print(scenario.name + ":");
            if (tactics.isEmpty())
            {
                System.out.println(" neither quantity present");
                continue;
            }

            for (int index = 0; index < tactics.size(); index++)
            {
                PortfolioCandidate tactic = tactics.get(index);
                System.out.printf(" rank%d=q%d(%.0f GP/h)",
                        index + 1, tactic.getQuantity(),
                        tactic.expectedGpPerSlotHour());
            }
            System.out.println();
        }
    }


    private static void printOptimizedScenarios(
            Map<String, GeneratedScenario> scenarios, Instant observedAt)
    {
        final int freeSlots = 8;
        final int totalSlots = 8;
        final long committedCoins = 0;
        final double markedSessionDrawdown = 0;
        final long minProfitPerFlip = 0;

        com.flippingfriend.core.AccountSnapshot account =
                new com.flippingfriend.core.AccountSnapshot(
                        "package-1.31-synthetic-account",
                        observedAt.getEpochSecond(),
                        COINS,
                        committedCoins,
                        freeSlots,
                        totalSlots,
                        true,
                        true,
                        markedSessionDrawdown);

        com.flippingfriend.model.RiskAppetite appetite =
                com.flippingfriend.model.RiskAppetite.BALANCED;

        long equity = account.getSpendableCoins() + account.getCommittedCoins();
        long lossBudget =
                (long) (equity * PortfolioPlanner.DRAWDOWN_LIMIT)
                        - (long) account.getMarkedSessionDrawdown();

        com.flippingfriend.core.PortfolioConstraints constraints =
                new com.flippingfriend.core.PortfolioConstraints(
                        account.getFreeSlots(),
                        account.getSpendableCoins(),
                        lossBudget,
                        (long) (equity * appetite.getItemExposureLimit()),
                        (long) (equity * appetite.getGroupExposureLimit()),
                        account.getCommittedByItem(),
                        java.util.Collections.<String, Long>emptyMap(),
                        minProfitPerFlip);

        java.util.LinkedHashMap<String, OptimizedScenario> optimized =
                new java.util.LinkedHashMap<>();

        for (GeneratedScenario scenario : scenarios.values())
        {
            java.util.List<PortfolioCandidate> offerable = new java.util.ArrayList<>();
            for (PortfolioCandidate candidate : scenario.trimmed)
            {
                if (!PortfolioPlanner.rejected(candidate, account))
                {
                    offerable.add(candidate);
                }
            }

            com.flippingfriend.core.PortfolioPlan plan =
                    new com.flippingfriend.core.PortfolioOptimizer().optimize(
                            offerable,
                            constraints,
                            "package-1.31-" + scenario.name,
                            observedAt.getEpochSecond());

            optimized.put(
                    scenario.name,
                    new OptimizedScenario(scenario, offerable, plan));
        }

        OptimizedScenario neutral = optimized.get("neutral");

        System.out.println();
        System.out.println("--- explicitly synthetic diagnostic account state ---");
        System.out.printf(
                "captured current time %d  spendable %,d gp  members true  free slots %d of %d%n",
                observedAt.getEpochSecond(),
                account.getSpendableCoins(),
                account.getFreeSlots(),
                account.getTotalSlots());
        System.out.printf(
                "existing positions 0  open offers 0  committed capital %,d gp  "
                        + "buy-limit usage 0  rejection set empty%n",
                account.getCommittedCoins());
        System.out.printf(
                "risk appetite %s  candidate leg horizon %.1f hours  minimum profit %,d gp%n",
                appetite.getName(),
                HORIZON,
                minProfitPerFlip);
        System.out.printf(
                "loss budget %,d gp  per-item cap %,d gp  per-group cap %,d gp%n",
                constraints.getSessionLossBudget(),
                constraints.getPerItemCapitalCap(),
                constraints.getPerGroupCapitalCap());
        System.out.println(
                "This account state is synthetic and is not retained, live, or recommended account state.");

        for (OptimizedScenario scenario : optimized.values())
        {
            printOptimizedScenario(scenario, neutral);
        }
    }

    private static void printOptimizedScenario(
            OptimizedScenario scenario, OptimizedScenario neutral)
    {
        java.util.List<com.flippingfriend.core.PortfolioAllocation> allocations =
                scenario.plan.getAllocations();
        java.util.Set<String> selected = allocationKeys(allocations);
        java.util.Set<String> neutralSelected =
                neutral == null
                        ? java.util.Collections.<String>emptySet()
                        : allocationKeys(neutral.plan.getAllocations());

        long capital = 0;
        long worstLoss = 0;
        long unwindLoss = 0;
        double expectedProfit = 0;
        double slotHours = 0;
        java.util.Map<String, Long> groupExposure =
                new java.util.TreeMap<>();

        for (com.flippingfriend.core.PortfolioAllocation allocation : allocations)
        {
            PortfolioCandidate candidate = allocation.getCandidate();
            capital += candidate.getCapitalRequired();
            worstLoss += candidate.getWorstLoss();
            unwindLoss += candidate.getUnwindLoss();
            expectedProfit += candidate.expectedProfit();
            slotHours += candidate.expectedSlotHours();
            groupExposure.merge(
                    candidate.getGroup(),
                    candidate.getCapitalRequired(),
                    Long::sum);
        }

        long unusedCapital = Math.max(0, COINS - capital);
        int unusedSlots = Math.max(0, 8 - allocations.size());

        System.out.println();
        System.out.println("--- " + scenario.generated.label + " ---");
        System.out.printf(
                "generated %d  trimmed %d  after rejection %d  optimized %d%n",
                scenario.generated.full.size(),
                scenario.generated.trimmed.size(),
                scenario.offerable.size(),
                allocations.size());
        System.out.printf(
                "status %s  reason %s%n",
                scenario.plan.getStatus(),
                scenario.plan.getReason());
        System.out.printf(
                "capital %,d gp  unused capital %,d gp  unused slots %d%n",
                capital,
                unusedCapital,
                unusedSlots);
        System.out.printf(
                "expected net profit %.0f gp  expected slot hours %.6f  "
                        + "expected gp per occupied slot-hour %.0f%n",
                expectedProfit,
                slotHours,
                scenario.plan.getExpectedGpPerSlotHour());
        System.out.printf(
                "worst-case loss %,d gp  unwind loss %,d gp  group exposures %s%n",
                worstLoss,
                unwindLoss,
                printableGroups(groupExposure));
        System.out.printf(
                "selected versus neutral: added %d  removed %d%n",
                difference(selected, neutralSelected),
                difference(neutralSelected, selected));

        System.out.println("selected tactics:");
        if (allocations.isEmpty())
        {
            System.out.println("  none");
        }
        else
        {
            for (com.flippingfriend.core.PortfolioAllocation allocation : allocations)
            {
                PortfolioCandidate candidate = allocation.getCandidate();
                System.out.printf(
                        "  #%d %s item %d buy %d sell %d quantity %d "
                                + "capital %,d gp expected %.0f gp slot-hours %.6f rate %.0f%n",
                        allocation.getRank(),
                        candidate.getItemName(),
                        candidate.getItemId(),
                        candidate.getBuyPrice(),
                        candidate.getSellPrice(),
                        candidate.getQuantity(),
                        candidate.getCapitalRequired(),
                        candidate.expectedProfit(),
                        candidate.expectedSlotHours(),
                        candidate.expectedGpPerSlotHour());
            }
        }

        System.out.printf(
                "Avernic optimized selection: %s%n",
                avernicSelection(allocations));
        System.out.println(
                "optimizer rejection detail: aggregate per-constraint counts are not exposed by "
                        + "PortfolioOptimizer; the plan reason above is the available optimizer result.");
    }

    private static java.util.Set<String> allocationKeys(
            java.util.List<com.flippingfriend.core.PortfolioAllocation> allocations)
    {
        java.util.Set<String> result = new java.util.LinkedHashSet<>();
        for (com.flippingfriend.core.PortfolioAllocation allocation : allocations)
        {
            result.add(key(allocation.getCandidate()));
        }
        return result;
    }

    private static String printableGroups(java.util.Map<String, Long> groups)
    {
        if (groups.isEmpty())
        {
            return "{}";
        }

        java.util.Map<String, Long> printable = new java.util.TreeMap<>();
        for (java.util.Map.Entry<String, Long> entry : groups.entrySet())
        {
            printable.put(
                    entry.getKey() == null || entry.getKey().isEmpty()
                            ? "(ungrouped)"
                            : entry.getKey(),
                    entry.getValue());
        }
        return printable.toString();
    }

    private static String avernicSelection(
            java.util.List<com.flippingfriend.core.PortfolioAllocation> allocations)
    {
        for (com.flippingfriend.core.PortfolioAllocation allocation : allocations)
        {
            PortfolioCandidate candidate = allocation.getCandidate();
            if ("Avernic defender hilt".equalsIgnoreCase(candidate.getItemName()))
            {
                return "selected quantity " + candidate.getQuantity()
                        + " at buy " + candidate.getBuyPrice()
                        + " and sell " + candidate.getSellPrice();
            }
        }
        return "not selected";
    }

    private static final class OptimizedScenario
    {
        private final GeneratedScenario generated;
        private final java.util.List<PortfolioCandidate> offerable;
        private final com.flippingfriend.core.PortfolioPlan plan;

        private OptimizedScenario(
                GeneratedScenario generated,
                java.util.List<PortfolioCandidate> offerable,
                com.flippingfriend.core.PortfolioPlan plan)
        {
            this.generated = generated;
            this.offerable =
                    java.util.Collections.unmodifiableList(
                            new java.util.ArrayList<>(offerable));
            this.plan = plan;
        }
    }

    private static void printScenarioBoundary()
    {
        System.out.println();
        System.out.println("=== interpretation boundary ===");
        System.out.println("no final portfolio optimization was performed");
        System.out.println("no scenario was promoted to production");
        System.out.println("this is in-sample sensitivity over one retained capture");
        System.out.println("the 18-observation lineage gap remains unresolved");
        System.out.println("cancelled elapsed time was not treated as realized fill time");
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

    private static Comparator<PortfolioCandidate> rank()
    {
        return Comparator.comparingDouble(PortfolioCandidate::expectedGpPerSlotHour).reversed()
                .thenComparingInt(PortfolioCandidate::getItemId)
                .thenComparingInt(PortfolioCandidate::getBuyPrice)
                .thenComparingInt(PortfolioCandidate::getSellPrice)
                .thenComparingInt(PortfolioCandidate::getQuantity);
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

    private static final class GeneratedScenario
    {
        private final String name;
        private final String label;
        private final FillCalibration calibration;
        private final List<PortfolioCandidate> full;
        private final List<PortfolioCandidate> trimmed;
        private final PlanDiagnostics funnel;

        private GeneratedScenario(String name, String label,
                FillCalibration calibration, List<PortfolioCandidate> full,
                List<PortfolioCandidate> trimmed, PlanDiagnostics funnel)
        {
            this.name = name;
            this.label = label;
            this.calibration = calibration;
            this.full = full;
            this.trimmed = trimmed;
            this.funnel = funnel;
        }
    }

}
