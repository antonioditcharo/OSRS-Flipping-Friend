package com.flippingfriend.companion;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.Assume;
import org.junit.Test;

/** Diagnostic-only reconstruction of retained attributed terminal offers. */
public class DurationInflationProbe
{
        @Test
        public void explainOfferLegDurationInflation() throws Exception
        {
                String dir = System.getProperty("probe.dir");
                Assume.assumeTrue("set -Dprobe.dir to run this probe", dir != null);
                Path evidence = Paths.get(dir).resolve("flipping-friend-evidence.db");
                Assume.assumeTrue("evidence database is required", Files.isRegularFile(evidence));

                String path = evidence.toAbsolutePath().toString()
                                .replace(File.separatorChar, '/');
                String url = "jdbc:sqlite:file:" + path + "?mode=ro";
                Set<String> claims = new HashSet<>();
                Map<String, Observation> terminal = new LinkedHashMap<>();
                Map<Integer, Totals> productionItems = new HashMap<>();
                long productionRows;
                long productionCount;
                double productionOpen;
                double productionPredicted;
                int terminalRows = 0;
                int malformed = 0;
                int nonObject = 0;
                int typeMismatch = 0;
                int invalidItem = 0;
                int duplicates = 0;

                try (Connection connection = DriverManager.getConnection(url))
                {
                        try (PreparedStatement statement = connection.prepareStatement(
                                        "SELECT identity FROM counted_offer");
                                        ResultSet result = statement.executeQuery())
                        {
                                while (result.next())
                                {
                                        claims.add(result.getString(1));
                                }
                        }
                        try (PreparedStatement statement = connection.prepareStatement(
                                        "SELECT COUNT(*), COALESCE(SUM(open_observed),0), "
                                                        + "COALESCE(SUM(open_minutes),0), "
                                                        + "COALESCE(SUM(open_predicted_minutes),0) "
                                                        + "FROM execution_stat");
                                        ResultSet result = statement.executeQuery())
                        {
                                result.next();
                                productionRows = result.getLong(1);
                                productionCount = result.getLong(2);
                                productionOpen = result.getDouble(3);
                                productionPredicted = result.getDouble(4);
                        }
                        try (PreparedStatement statement = connection.prepareStatement(
                                        "SELECT item_id, open_observed, open_minutes, "
                                                        + "open_predicted_minutes FROM execution_stat "
                                                        + "WHERE open_observed > 0");
                                        ResultSet result = statement.executeQuery())
                        {
                                while (result.next())
                                {
                                        productionItems.put(result.getInt(1), new Totals(
                                                        result.getLong(2), result.getDouble(3),
                                                        result.getDouble(4)));
                                }
                        }
                        try (PreparedStatement statement = connection.prepareStatement(
                                        "SELECT payload FROM event_log WHERE event_type IN "
                                                        + "('BOUGHT','SOLD','CANCELLED_BUY','CANCELLED_SELL') "
                                                        + "ORDER BY id");
                                        ResultSet result = statement.executeQuery())
                        {
                                while (result.next())
                                {
                                        terminalRows++;
                                        JsonObject event;
                                        try
                                        {
                                                JsonElement parsed = JsonParser.parseString(result.getString(1));
                                                if (!parsed.isJsonObject())
                                                {
                                                        nonObject++;
                                                        continue;
                                                }
                                                event = parsed.getAsJsonObject();
                                        }
                                        catch (RuntimeException invalidJson)
                                        {
                                                malformed++;
                                                continue;
                                        }
                                        String type = text(event, "eventType");
                                        if (!terminal(type))
                                        {
                                                typeMismatch++;
                                                continue;
                                        }
                                        int item = integer(event, "itemId");
                                        if (item <= 0)
                                        {
                                                invalidItem++;
                                                continue;
                                        }
                                        long observed = longValue(event, "observedAt");
                                        long first = longValue(event, "firstSeenAt");
                                        long start = first > 0 ? first : observed;
                                        String identity = integer(event, "slot") + "@" + start + ":" + item;
                                        if (terminal.containsKey(identity))
                                        {
                                                duplicates++;
                                                continue;
                                        }
                                        double open = first > 0 && observed >= first
                                                        ? (observed - first) / 60.0 : 0;
                                        terminal.put(identity, new Observation(identity, item, type,
                                                        open, decimal(event, "predictedMinutes")));
                                }
                        }
                }

                Map<String, List<Observation>> groups = new LinkedHashMap<>();
                groups.put("BUY completed", new ArrayList<>());
                groups.put("BUY cancelled", new ArrayList<>());
                groups.put("SELL completed", new ArrayList<>());
                groups.put("SELL cancelled", new ArrayList<>());
                Map<Integer, Totals> retainedItems = new HashMap<>();
                int absentClaim = 0;
                int badOpen = 0;
                int badPrediction = 0;
                int counted = 0;
                for (Observation observation : terminal.values())
                {
                        if (!claims.contains(observation.identity))
                        {
                                absentClaim++;
                                continue;
                        }
                        counted++;
                        if (observation.open <= 0)
                        {
                                badOpen++;
                                continue;
                        }
                        if (observation.predicted <= 0)
                        {
                                badPrediction++;
                                continue;
                        }
                        groups.get(observation.group()).add(observation);
                        retainedItems.computeIfAbsent(observation.item, ignored -> new Totals())
                                        .add(observation.open, observation.predicted);
                }

                long retainedCount = 0;
                double retainedOpen = 0;
                double retainedPredicted = 0;
                for (Totals totals : retainedItems.values())
                {
                        retainedCount += totals.count;
                        retainedOpen += totals.open;
                        retainedPredicted += totals.predicted;
                }
                Set<Integer> compared = new HashSet<>(productionItems.keySet());
                compared.addAll(retainedItems.keySet());
                int exact = 0;
                for (int item : compared)
                {
                        if (productionItems.getOrDefault(item, new Totals()).same(
                                        retainedItems.getOrDefault(item, new Totals())))
                        {
                                exact++;
                        }
                }

                System.out.println("=== offer-leg duration inflation ===");
                System.out.println("=== production open-duration aggregate ===");
                System.out.printf("execution_stat rows       %d%n", productionRows);
                System.out.printf("open observations         %d%n", productionCount);
                System.out.printf("open minutes              %.6f%n", productionOpen);
                System.out.printf("open predicted minutes    %.6f%n", productionPredicted);
                System.out.printf("pooled actual/predicted   %.6f%n", ratio(productionOpen, productionPredicted));
                System.out.println("=== retained terminal-event reconstruction ===");
                System.out.printf("terminal rows             %d%n", terminalRows);
                System.out.printf("unique terminal identities %d%n", terminal.size());
                System.out.printf("counted terminal identities %d%n", counted);
                System.out.printf("comparable observations   %d%n", retainedCount);
                System.out.printf("open minutes              %.6f%n", retainedOpen);
                System.out.printf("predicted minutes         %.6f%n", retainedPredicted);
                System.out.printf("pooled actual/predicted   %.6f%n", ratio(retainedOpen, retainedPredicted));
                System.out.println("This is a retained-event reconstruction, not the complete production observation population.");
                System.out.println("=== reconstruction exclusions ===");
                System.out.printf("%6d  malformed terminal payload%n", malformed);
                System.out.printf("%6d  non-object terminal payload%n", nonObject);
                System.out.printf("%6d  payload terminal type mismatch%n", typeMismatch);
                System.out.printf("%6d  invalid item%n", invalidItem);
                System.out.printf("%6d  duplicate retained terminal identity%n", duplicates);
                System.out.printf("%6d  terminal identity absent from counted_offer%n", absentClaim);
                System.out.printf("%6d  claimed but non-positive open duration%n", badOpen);
                System.out.printf("%6d  claimed but non-positive prediction%n", badPrediction);
                System.out.println("=== duration by side and outcome ===");
                System.out.printf("%-18s %6s %12s %12s %9s %12s %12s %12s%n",
                                "group", "count", "open", "predicted", "ratio",
                                "median open", "median pred", "median ratio");
                for (Map.Entry<String, List<Observation>> entry : groups.entrySet())
                {
                        printGroup(entry.getKey(), entry.getValue());
                }
                System.out.println("=== prediction-to-open ratio distribution ===");
                System.out.printf("%-18s %9s %9s %9s %9s %9s%n", "group", "p10", "p25", "p50", "p75", "p90");
                for (Map.Entry<String, List<Observation>> entry : groups.entrySet())
                {
                        List<Double> ratios = new ArrayList<>();
                        for (Observation observation : entry.getValue())
                        {
                                ratios.add(ratio(observation.open, observation.predicted));
                        }
                        Collections.sort(ratios);
                        System.out.printf("%-18s %9.4f %9.4f %9.4f %9.4f %9.4f%n", entry.getKey(),
                                        percentile(ratios, .10), percentile(ratios, .25),
                                        percentile(ratios, .50), percentile(ratios, .75), percentile(ratios, .90));
                }
                System.out.println("=== production versus retained-event lineage ===");
                System.out.printf("production observations   %d%n", productionCount);
                System.out.printf("reconstructed observations %d%n", retainedCount);
                System.out.printf("missing observations      %d%n", productionCount - retainedCount);
                System.out.printf("missing open minutes      %.6f%n", productionOpen - retainedOpen);
                System.out.printf("missing predicted minutes %.6f%n", productionPredicted - retainedPredicted);
                System.out.printf("items compared            %d%n", compared.size());
                System.out.printf("exactly matching items    %d%n", exact);
                System.out.printf("differing items           %d%n", compared.size() - exact);
                System.out.println("=== historical component-decomposition limit ===");
                System.out.println("The retained event log does not preserve the original fill estimate's counterparty-wait and working-time components.");
                System.out.println("It preserves only the attributed total predicted offer-leg duration.");
                System.out.println("Historical wait-versus-working decomposition therefore cannot be recovered like-for-like from this evidence capture.");
        }

        private static void printGroup(String name, List<Observation> rows)
        {
                List<Double> open = new ArrayList<>();
                List<Double> predicted = new ArrayList<>();
                List<Double> ratios = new ArrayList<>();
                double openTotal = 0;
                double predictedTotal = 0;
                for (Observation row : rows)
                {
                        open.add(row.open);
                        predicted.add(row.predicted);
                        ratios.add(ratio(row.open, row.predicted));
                        openTotal += row.open;
                        predictedTotal += row.predicted;
                }
                Collections.sort(open);
                Collections.sort(predicted);
                Collections.sort(ratios);
                System.out.printf("%-18s %6d %12.3f %12.3f %9.4f %12.3f %12.3f %12.4f%n",
                                name, rows.size(), openTotal, predictedTotal, ratio(openTotal, predictedTotal),
                                percentile(open, .50), percentile(predicted, .50), percentile(ratios, .50));
        }

        private static boolean terminal(String type)
        {
                return "BOUGHT".equals(type) || "SOLD".equals(type)
                                || "CANCELLED_BUY".equals(type) || "CANCELLED_SELL".equals(type);
        }

        private static String text(JsonObject object, String name)
        {
                return object.has(name) && !object.get(name).isJsonNull() ? object.get(name).getAsString() : "";
        }

        private static int integer(JsonObject object, String name)
        {
                return object.has(name) && !object.get(name).isJsonNull() ? object.get(name).getAsInt() : 0;
        }

        private static long longValue(JsonObject object, String name)
        {
                return object.has(name) && !object.get(name).isJsonNull() ? object.get(name).getAsLong() : 0;
        }

        private static double decimal(JsonObject object, String name)
        {
                if (!object.has(name) || object.get(name).isJsonNull())
                {
                        return 0;
                }
                double value = object.get(name).getAsDouble();
                return Double.isFinite(value) ? value : 0;
        }

        private static double ratio(double actual, double predicted)
        {
                return predicted > 0 ? actual / predicted : Double.NaN;
        }

        private static double percentile(List<Double> sorted, double fraction)
        {
                if (sorted.isEmpty()) return Double.NaN;
                if (sorted.size() == 1) return sorted.get(0);
                double position = (sorted.size() - 1) * fraction;
                int lower = (int) Math.floor(position);
                int upper = (int) Math.ceil(position);
                if (lower == upper) return sorted.get(lower);
                double weight = position - lower;
                return sorted.get(lower) * (1 - weight) + sorted.get(upper) * weight;
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
                        this.identity = identity;
                        this.item = item;
                        this.type = type;
                        this.open = open;
                        this.predicted = predicted;
                }

                private String group()
                {
                        if ("BOUGHT".equals(type)) return "BUY completed";
                        if ("CANCELLED_BUY".equals(type)) return "BUY cancelled";
                        if ("SOLD".equals(type)) return "SELL completed";
                        return "SELL cancelled";
                }
        }

        private static final class Totals
        {
                private long count;
                private double open;
                private double predicted;

                private Totals() { }

                private Totals(long count, double open, double predicted)
                {
                        this.count = count;
                        this.open = open;
                        this.predicted = predicted;
                }

                private void add(double actual, double expected)
                {
                        count++;
                        open += actual;
                        predicted += expected;
                }

                private boolean same(Totals other)
                {
                        return count == other.count && Math.abs(open - other.open) < 1e-6
                                        && Math.abs(predicted - other.predicted) < 1e-6;
                }
        }
}
