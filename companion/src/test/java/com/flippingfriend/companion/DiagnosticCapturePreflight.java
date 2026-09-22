package com.flippingfriend.companion;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.zip.GZIPInputStream;

/**
 * Read-only validation of the retained diagnostic capture before an evidence probe opens it.
 *
 * <p>This class never creates, alters, moves, or deletes capture files. In particular, a WAL, SHM,
 * or journal sidecar is a refusal condition, not something this preflight attempts to clean up.
 */
final class DiagnosticCapturePreflight
{
    static final long AUTHORITATIVE_DATABASE_SIZE = 1_135_456_256L;
    static final String AUTHORITATIVE_DATABASE_SHA256 =
            "989E48254DA68F68F16DB6A871EFDE9D2B3B267A437EA2B205B7D83C6243B205";

    private static final String DATABASE_NAME = "flipping-friend-evidence.db";
    private static final String SNAP_NAME = "snap.json";
    private static final String MAPPING_NAME = "mapping.json";
    private static final String SERIES_NAME = "series-cache.json.gz";

    private DiagnosticCapturePreflight()
    {
    }

    static Capture requireAuthoritative(Path root) throws Exception
    {
        return validate(root, AUTHORITATIVE_DATABASE_SIZE, AUTHORITATIVE_DATABASE_SHA256);
    }

    static Capture validate(Path root, long expectedDatabaseSize, String expectedDatabaseSha256)
            throws Exception
    {
        if (root == null)
        {
            throw new IllegalArgumentException("capture directory is required");
        }
        if (!Files.isDirectory(root))
        {
            throw new IllegalArgumentException("capture directory is not a directory: " + root);
        }
        if (expectedDatabaseSize <= 0)
        {
            throw new IllegalArgumentException("expected database size must be positive");
        }
        if (expectedDatabaseSha256 == null
                || !expectedDatabaseSha256.matches("[0-9A-Fa-f]{64}"))
        {
            throw new IllegalArgumentException("expected database SHA-256 must contain 64 hex digits");
        }

        Path normalized = root.toAbsolutePath().normalize();
        Path evidence = requiredRegularFile(normalized.resolve(DATABASE_NAME));
        Path snap = requiredRegularFile(normalized.resolve(SNAP_NAME));
        Path mapping = requiredRegularFile(normalized.resolve(MAPPING_NAME));
        Path series = requiredRegularFile(normalized.resolve(SERIES_NAME));

        rejectSidecar(evidence.resolveSibling(evidence.getFileName() + "-wal"), "WAL");
        rejectSidecar(evidence.resolveSibling(evidence.getFileName() + "-shm"), "SHM");
        rejectSidecar(evidence.resolveSibling(evidence.getFileName() + "-journal"), "journal");

        long actualSize = Files.size(evidence);
        if (actualSize != expectedDatabaseSize)
        {
            throw new IllegalStateException("evidence database size mismatch: expected "
                    + expectedDatabaseSize + " bytes but found " + actualSize);
        }

        String actualSha256 = sha256(evidence);
        String expectedSha256 = expectedDatabaseSha256.toUpperCase(Locale.ROOT);
        if (!actualSha256.equals(expectedSha256))
        {
            throw new IllegalStateException("evidence database SHA-256 mismatch: expected "
                    + expectedSha256 + " but found " + actualSha256);
        }

        validateSnapshot(snap);
        validateMapping(mapping);
        validateSeries(series);

        return new Capture(normalized, evidence, snap, mapping, series, actualSize, actualSha256);
    }

    static String sha256(Path path) throws Exception
    {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] buffer = new byte[1024 * 1024];
        try (java.io.InputStream input = Files.newInputStream(path))
        {
            int read;
            while ((read = input.read(buffer)) >= 0)
            {
                if (read > 0)
                {
                    digest.update(buffer, 0, read);
                }
            }
        }

        StringBuilder hex = new StringBuilder(64);
        for (byte value : digest.digest())
        {
            hex.append(String.format(Locale.ROOT, "%02X", value & 0xff));
        }
        return hex.toString();
    }

    private static Path requiredRegularFile(Path path) throws Exception
    {
        if (!Files.isRegularFile(path))
        {
            throw new IllegalStateException("required capture file is absent: " + path);
        }
        if (Files.size(path) <= 0)
        {
            throw new IllegalStateException("required capture file is empty: " + path);
        }
        return path;
    }

    private static void rejectSidecar(Path path, String type)
    {
        if (Files.exists(path))
        {
            throw new IllegalStateException(type + " sidecar must be absent before diagnostic access: "
                    + path);
        }
    }

    private static void validateSnapshot(Path path) throws Exception
    {
        JsonElement parsed = parseJson(path);
        if (!parsed.isJsonObject())
        {
            throw new IllegalStateException("snap.json must contain a JSON object");
        }

        JsonObject object = parsed.getAsJsonObject();
        requireObject(object, "latest", path);
        requireObject(object, "fiveMinute", path);
        requireObject(object, "hourly", path);

        if (!object.has("observedAt")
                || object.get("observedAt").isJsonNull()
                || !object.get("observedAt").isJsonPrimitive())
        {
            throw new IllegalStateException("snap.json is missing observedAt");
        }

        try
        {
            object.get("observedAt").getAsLong();
        }
        catch (RuntimeException invalid)
        {
            throw new IllegalStateException("snap.json observedAt is not an integer", invalid);
        }
    }

    private static void validateMapping(Path path) throws Exception
    {
        JsonElement parsed = parseJson(path);
        if (!parsed.isJsonArray())
        {
            throw new IllegalStateException("mapping.json must contain a JSON array");
        }
        if (parsed.getAsJsonArray().size() == 0)
        {
            throw new IllegalStateException("mapping.json must not be empty");
        }
    }

    private static void validateSeries(Path path) throws Exception
    {
        JsonElement parsed;
        try (Reader reader = new InputStreamReader(
                new GZIPInputStream(Files.newInputStream(path)), StandardCharsets.UTF_8))
        {
            parsed = JsonParser.parseReader(reader);
        }

        if (!parsed.isJsonObject())
        {
            throw new IllegalStateException("series-cache.json.gz must contain a JSON object");
        }

        JsonObject object = parsed.getAsJsonObject();
        if (!object.has("entries") || !object.get("entries").isJsonArray())
        {
            throw new IllegalStateException(
                    "series-cache.json.gz is missing its entries array");
        }
        if (object.getAsJsonArray("entries").size() == 0)
        {
            throw new IllegalStateException(
                    "series-cache.json.gz entries array must not be empty");
        }
    }

    private static JsonElement parseJson(Path path) throws Exception
    {
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8))
        {
            return JsonParser.parseReader(reader);
        }
    }

    private static void requireObject(JsonObject object, String field, Path source)
    {
        if (!object.has(field) || !object.get(field).isJsonObject())
        {
            throw new IllegalStateException(source.getFileName()
                    + " is missing object field " + field);
        }
    }

    static final class Capture
    {
        private final Path root;
        private final Path evidence;
        private final Path snap;
        private final Path mapping;
        private final Path seriesCache;
        private final long databaseSize;
        private final String databaseSha256;

        private Capture(Path root, Path evidence, Path snap, Path mapping, Path seriesCache,
                long databaseSize, String databaseSha256)
        {
            this.root = root;
            this.evidence = evidence;
            this.snap = snap;
            this.mapping = mapping;
            this.seriesCache = seriesCache;
            this.databaseSize = databaseSize;
            this.databaseSha256 = databaseSha256;
        }

        Path root() { return root; }
        Path evidence() { return evidence; }
        Path snap() { return snap; }
        Path mapping() { return mapping; }
        Path seriesCache() { return seriesCache; }
        long databaseSize() { return databaseSize; }
        String databaseSha256() { return databaseSha256; }
    }
}
