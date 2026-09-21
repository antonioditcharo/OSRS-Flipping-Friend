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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.Assume;
import org.junit.Test;

/** Diagnostic-only analysis of completed durations and censored cancellation times. */
public class CensoredDurationProbe
{
        @Test
        public void validateCensoredDurationTreatment() throws Exception
        {
                String dir = System.getProperty("probe.dir");
                Assume.assumeTrue("set -Dprobe.dir to run this probe", dir != null);
                Path evidence = Paths.get(dir).resolve("flipping-friend-evidence.db");
                Assume.assumeTrue("evidence database is required", Files.isRegularFile(evidence));

                String path = evidence.toAbsolutePath().toString().replace(File.separatorChar, '/');
                String url = "jdbc:sqlite:file:" + path + "?mode=ro";
                Set<String> claims = new HashSet<>();
                Map<String, Observation> terminal = new LinkedHashMap<>();
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
                                while (result.next()) claims.add(result.getString(1));
                        }
                        try (PreparedStatement statement = connection.prepareStatement(
                                        "SELECT COALESCE(SUM(open_observed),0), "
                                                        + "COALESCE(SUM(open_minutes),0), "
                                                        + "COALESCE(SUM(open_predicted_minutes),0) "
                                                        + "FROM execution_stat");
                                        ResultSet result = statement.executeQuery())
                        {
                                result.next();
                                productionCount = result.getLong(1);
                                productionOpen = result.getDouble(2);
                                productionPredicted = result.getDouble(3);
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
                                        terminal.put(identity, new Observation(identity, type, open,
                                                        decimal(event, "predictedMinutes"),
                                                        integer(event, "totalQuantity"),
                                                        integer(event, "filledQuantity"),
                                                        integer(event, "suggestedQuantity")));
                                }
                        }
                }

                Map<String, List<Observation>> groups = new LinkedHashMap<>();
                groups.put("BUY completed", new ArrayList<>());
                groups.put("BUY cancelled", new ArrayList<>());
                groups.put("SELL completed", new ArrayList<>());
                groups.put("SELL cancelled", new ArrayList<>());
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
                }

                List<Observation> completed = combine(groups.get("BUY completed"), groups.get("SELL completed"));
                List<Observation> cancelled = combine(groups.get("BUY cancelled"), groups.get("SELL cancelled"));
                List<Observation> retained = combine(completed, cancelled);
                Totals completedTotals = totals(completed);
                Totals cancelledTotals = totals(cancelled);
                Totals retainedTotals = totals(retained);

                System.out.println("=== censored-duration treatment ===");
                System.out.println("Current production arithmetic pools every comparable settled offer's elapsed open minutes");
                System.out.println("and attributed predicted minutes, then divides the two totals.");
                System.out.println("For a cancelled offer, elapsed open time is a right-censoring lower bound on latent fill time;");
                System.out.println("the current pooled ratio nevertheless uses that lower bound as an ordinary numeric contribution.");
                System.out.println("It is therefore a heuristic pooled lower-bound ratio, not a realized-fill estimator");
                System.out.println("and not a censoring-aware survival estimator.");
                System.out.println("=== production aggregate ===");
                System.out.printf("open observations          %d%n", productionCount);
                System.out.printf("open minutes               %.6f%n", productionOpen);
                System.out.printf("open predicted minutes     %.6f%n", productionPredicted);
                System.out.printf("pooled elapsed/predicted   %.6f%n", ratio(productionOpen, productionPredicted));
                System.out.println("=== retained reconstruction ===");
                System.out.printf("terminal rows              %d%n", terminalRows);
                System.out.printf("unique terminal identities %d%n", terminal.size());
                System.out.printf("counted terminal identities %d%n", counted);
                System.out.printf("comparable observations    %d%n", retained.size());
                System.out.printf("production observations    %d%n", productionCount);
                System.out.printf("lineage gap                %d%n", productionCount - retained.size());
                System.out.println("Retained reconstruction and production aggregate are distinct populations.");
                System.out.println("=== reconstruction exclusions ===");
                System.out.printf("%6d  malformed terminal payload%n", malformed);
                System.out.printf("%6d  non-object terminal payload%n", nonObject);
                System.out.printf("%6d  payload terminal type mismatch%n", typeMismatch);
                System.out.printf("%6d  invalid item%n", invalidItem);
                System.out.printf("%6d  duplicate retained terminal identity%n", duplicates);
                System.out.printf("%6d  terminal identity absent from counted_offer%n", absentClaim);
                System.out.printf("%6d  claimed but non-positive open duration%n", badOpen);
                System.out.printf("%6d  claimed but non-positive prediction%n", badPrediction);
                System.out.println("=== retained outcome comparison ===");
                printTotals("completed only", completedTotals);
                printTotals("cancelled only", cancelledTotals);
                printTotals("current mixed pool", retainedTotals);
                System.out.printf("mixed minus completed ratio %+.6f%n",
                                retainedTotals.ratio() - completedTotals.ratio());
                System.out.println("A cancellation moves the pooled ratio down when its elapsed/predicted ratio is below");
                System.out.println("the pre-existing pool, and up when it is above it; cancellation does not have one fixed direction.");
                System.out.println("=== side and outcome ===");
                System.out.printf("%-18s %6s %12s %12s %9s %9s %9s %9s %9s %9s%n",
                                "group", "count", "open", "predicted", "pooled", "p10", "p25", "p50", "p75", "p90");
                for (Map.Entry<String, List<Observation>> entry : groups.entrySet())
                {
                        printGroup(entry.getKey(), entry.getValue());
                }
                System.out.println("=== cancellation timing buckets ===");
                System.out.println("early: elapsed/predicted < 0.25");
                System.out.println("intermediate: 0.25 <= elapsed/predicted < 1.00");
                System.out.println("late: elapsed/predicted >= 1.00");
                printBuckets("BUY cancelled", groups.get("BUY cancelled"));
                printBuckets("SELL cancelled", groups.get("SELL cancelled"));
                printBuckets("all cancelled", cancelled);
                System.out.println("Bucket boundaries are descriptive diagnostics, not statistical standards.");
                System.out.println("=== quantity by side and outcome ===");
                System.out.printf("%-18s %6s %6s %6s %8s %8s %8s %8s%n",
                                "group", "rows", "total+", "sugg+", "tot p50", "tot max", "sug p50", "sug max");
                for (Map.Entry<String, List<Observation>> entry : groups.entrySet())
                {
                        printQuantity(entry.getKey(), entry.getValue());
                }
                System.out.println("totalQuantity describes the observed offer; suggestedQuantity describes attributed advice.");
                System.out.println("The event schema has no cancellation-reason field, so player cancellation cannot be separated");
                System.out.println("from replacement, logout, shutdown, or another cause using this retained capture.");
                System.out.println("Partial filledQuantity is retained but does not reveal why the remaining offer was cancelled.");
                System.out.println("=== inference boundary ===");
                System.out.println("Quick player-controlled cancellation can lower the heuristic pooled ratio and make predictions");
                System.out.println("appear more inflated; sufficiently late cancellation can raise it. Cause is not identified here.");
                System.out.println("A censoring-aware survival analysis would require an explicit estimand and defensible assumptions");
                System.out.println("about censoring independence, offer comparability, side, quantity, and retained lineage.");
                System.out.println("This capture cannot demonstrate those assumptions, and the unresolved lineage gap remains.");
                System.out.println("Package 1.28 should compare diagnostic scenarios only: current mixed pool, completed-only,");
                System.out.println("side-separated pools, quantity-stratified summaries where populated, and an explicitly defined");
                System.out.println("censoring-aware method only if its assumptions and required fields can be supported.");
        }

        private static void printTotals(String name, Totals totals)
        {
                System.out.printf("%-20s count %3d  open %10.3f  predicted %10.3f  ratio %.6f%n",
                                name, totals.count, totals.open, totals.predicted, totals.ratio());
        }

        private static void printGroup(String name, List<Observation> rows)
        {
                Totals total = totals(rows);
                List<Double> ratios = ratios(rows);
                System.out.printf("%-18s %6d %12.3f %12.3f %9.4f %9.4f %9.4f %9.4f %9.4f %9.4f%n",
                                name, rows.size(), total.open, total.predicted, total.ratio(),
                                percentile(ratios, .10), percentile(ratios, .25), percentile(ratios, .50),
                                percentile(ratios, .75), percentile(ratios, .90));
        }

        private static void printBuckets(String name, List<Observation> rows)
        {
                int early = 0;
                int intermediate = 0;
                int late = 0;
                for (Observation row : rows)
                {
                        double value = ratio(row.open, row.predicted);
                        if (value < .25) early++;
                        else if (value < 1.0) intermediate++;
                        else late++;
                }
                System.out.printf("%-18s count %3d  early %3d  intermediate %3d  late %3d%n",
                                name, rows.size(), early, intermediate, late);
        }

        private static void printQuantity(String name, List<Observation> rows)
        {
                List<Double> total = new ArrayList<>();
                List<Double> suggested = new ArrayList<>();
                for (Observation row : rows)
                {
                        if (row.totalQuantity > 0) total.add((double) row.totalQuantity);
                        if (row.suggestedQuantity > 0) suggested.add((double) row.suggestedQuantity);
                }
                Collections.sort(total);
                Collections.sort(suggested);
                System.out.printf("%-18s %6d %6d %6d %8.1f %8.0f %8.1f %8.0f%n",
                                name, rows.size(), total.size(), suggested.size(), percentile(total, .50),
                                maximum(total), percentile(suggested, .50), maximum(suggested));
        }

        private static List<Observation> combine(List<Observation> first, List<Observation> second)
        {
                List<Observation> combined = new ArrayList<>(first);
                combined.addAll(second);
                return combined;
        }

        private static Totals totals(List<Observation> rows)
        {
                Totals result = new Totals();
                for (Observation row : rows)
                {
                        result.count++;
                        result.open += row.open;
                        result.predicted += row.predicted;
                }
                return result;
        }

        private static List<Double> ratios(List<Observation> rows)
        {
                List<Double> result = new ArrayList<>();
                for (Observation row : rows) result.add(ratio(row.open, row.predicted));
                Collections.sort(result);
                return result;
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
                if (!object.has(name) || object.get(name).isJsonNull()) return 0;
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

        private static double maximum(List<Double> sorted)
        {
                return sorted.isEmpty() ? Double.NaN : sorted.get(sorted.size() - 1);
        }

        private static final class Observation
        {
                private final String identity;
                private final String type;
                private final double open;
                private final double predicted;
                private final int totalQuantity;
                private final int filledQuantity;
                private final int suggestedQuantity;

                private Observation(String identity, String type, double open, double predicted,
                                int totalQuantity, int filledQuantity, int suggestedQuantity)
                {
                        this.identity = identity;
                        this.type = type;
                        this.open = open;
                        this.predicted = predicted;
                        this.totalQuantity = totalQuantity;
                        this.filledQuantity = filledQuantity;
                        this.suggestedQuantity = suggestedQuantity;
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
                private int count;
                private double open;
                private double predicted;

                private double ratio()
                {
                        return CensoredDurationProbe.ratio(open, predicted);
                }
        }
}
