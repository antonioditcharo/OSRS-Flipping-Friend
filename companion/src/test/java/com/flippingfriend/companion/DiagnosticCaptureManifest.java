package com.flippingfriend.companion;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Strict, test-side description of one diagnostic evidence capture.
 *
 * <p>The manifest contains identities only. It contains no market payloads, account state, trades,
 * credentials, or executable instructions. Parsing it does not open any file named by the manifest.
 */
final class DiagnosticCaptureManifest
{
    static final int FORMAT_VERSION = 1;
    static final String MANIFEST_NAME = "diagnostic-capture-manifest.json";

    static final String DATABASE_NAME = "flipping-friend-evidence.db";
    static final String SNAP_NAME = "snap.json";
    static final String MAPPING_NAME = "mapping.json";
    static final String SERIES_NAME = "series-cache.json.gz";

    private static final Set<String> REQUIRED_FILES =
            Set.of(DATABASE_NAME, SNAP_NAME, MAPPING_NAME, SERIES_NAME);

    private final int formatVersion;
    private final String captureId;
    private final Map<String, FileIdentity> files;
    private final boolean requireSidecarsAbsent;

    private DiagnosticCaptureManifest(int formatVersion, String captureId,
            Map<String, FileIdentity> files, boolean requireSidecarsAbsent)
    {
        this.formatVersion = formatVersion;
        this.captureId = captureId;
        this.files = Collections.unmodifiableMap(new LinkedHashMap<>(files));
        this.requireSidecarsAbsent = requireSidecarsAbsent;
    }

    static DiagnosticCaptureManifest read(Path path) throws Exception
    {
        if (path == null)
        {
            throw new IllegalArgumentException("manifest path is required");
        }
        if (!Files.isRegularFile(path))
        {
            throw new IllegalArgumentException("manifest is not a regular file: " + path);
        }

        JsonElement parsed;
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8))
        {
            parsed = JsonParser.parseReader(reader);
        }

        if (!parsed.isJsonObject())
        {
            throw new IllegalArgumentException("manifest must contain a JSON object");
        }

        return parse(parsed.getAsJsonObject());
    }

    static DiagnosticCaptureManifest parse(JsonObject root)
    {
        if (root == null)
        {
            throw new IllegalArgumentException("manifest object is required");
        }

        rejectUnknownFields(root, Set.of(
                "formatVersion",
                "captureId",
                "files",
                "requireSidecarsAbsent"));

        int version = requiredInteger(root, "formatVersion");
        if (version != FORMAT_VERSION)
        {
            throw new IllegalArgumentException(
                    "unsupported manifest formatVersion: " + version);
        }

        String captureId = requiredString(root, "captureId");
        if (!captureId.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,127}"))
        {
            throw new IllegalArgumentException(
                    "captureId must contain only letters, digits, period, underscore, or hyphen");
        }

        if (!root.has("requireSidecarsAbsent")
                || !root.get("requireSidecarsAbsent").isJsonPrimitive()
                || !root.get("requireSidecarsAbsent").getAsJsonPrimitive().isBoolean()
                || !root.get("requireSidecarsAbsent").getAsBoolean())
        {
            throw new IllegalArgumentException(
                    "requireSidecarsAbsent must be explicitly true");
        }

        if (!root.has("files") || !root.get("files").isJsonObject())
        {
            throw new IllegalArgumentException("files must contain a JSON object");
        }

        JsonObject fileObject = root.getAsJsonObject("files");
        if (!fileObject.keySet().equals(REQUIRED_FILES))
        {
            throw new IllegalArgumentException(
                    "files must contain exactly " + REQUIRED_FILES);
        }

        Map<String, FileIdentity> files = new LinkedHashMap<>();
        for (String name : REQUIRED_FILES)
        {
            validateFixedFileName(name);
            JsonElement entry = fileObject.get(name);
            if (entry == null || !entry.isJsonObject())
            {
                throw new IllegalArgumentException(
                        "file identity must be an object: " + name);
            }

            JsonObject identity = entry.getAsJsonObject();
            rejectUnknownFields(identity, Set.of("size", "sha256"));

            long size = requiredLong(identity, "size");
            if (size <= 0)
            {
                throw new IllegalArgumentException(
                        "file size must be positive: " + name);
            }

            String sha256 = requiredString(identity, "sha256")
                    .toUpperCase(Locale.ROOT);
            if (!sha256.matches("[0-9A-F]{64}"))
            {
                throw new IllegalArgumentException(
                        "file SHA-256 must contain 64 hex digits: " + name);
            }

            files.put(name, new FileIdentity(name, size, sha256));
        }

        return new DiagnosticCaptureManifest(version, captureId, files, true);
    }

    private static void validateFixedFileName(String name)
    {
        if (name.contains("/")
                || name.contains("\\")
                || name.contains("..")
                || !REQUIRED_FILES.contains(name))
        {
            throw new IllegalArgumentException("invalid capture filename: " + name);
        }
    }

    private static void rejectUnknownFields(JsonObject object, Set<String> allowed)
    {
        for (String field : object.keySet())
        {
            if (!allowed.contains(field))
            {
                throw new IllegalArgumentException("unknown manifest field: " + field);
            }
        }
    }

    private static String requiredString(JsonObject object, String field)
    {
        if (!object.has(field)
                || object.get(field).isJsonNull()
                || !object.get(field).isJsonPrimitive()
                || !object.get(field).getAsJsonPrimitive().isString())
        {
            throw new IllegalArgumentException(field + " must be a string");
        }

        String value = object.get(field).getAsString();
        if (value.isEmpty())
        {
            throw new IllegalArgumentException(field + " must not be empty");
        }
        return value;
    }

    private static int requiredInteger(JsonObject object, String field)
    {
        long value = requiredLong(object, field);
        if (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE)
        {
            throw new IllegalArgumentException(field + " is outside the integer range");
        }
        return (int) value;
    }

    private static long requiredLong(JsonObject object, String field)
    {
        if (!object.has(field)
                || object.get(field).isJsonNull()
                || !object.get(field).isJsonPrimitive()
                || !object.get(field).getAsJsonPrimitive().isNumber())
        {
            throw new IllegalArgumentException(field + " must be an integer");
        }

        String value = object.get(field).getAsString();
        if (!value.matches("-?[0-9]+"))
        {
            throw new IllegalArgumentException(field + " must be an integer");
        }

        try
        {
            return Long.parseLong(value);
        }
        catch (NumberFormatException invalid)
        {
            throw new IllegalArgumentException(field + " is outside the long range", invalid);
        }
    }

    int formatVersion() { return formatVersion; }
    String captureId() { return captureId; }
    boolean requireSidecarsAbsent() { return requireSidecarsAbsent; }
    Map<String, FileIdentity> files() { return files; }

    FileIdentity file(String name)
    {
        FileIdentity identity = files.get(name);
        if (identity == null)
        {
            throw new IllegalArgumentException("manifest does not contain file: " + name);
        }
        return identity;
    }

    static final class FileIdentity
    {
        private final String name;
        private final long size;
        private final String sha256;

        private FileIdentity(String name, long size, String sha256)
        {
            this.name = name;
            this.size = size;
            this.sha256 = sha256;
        }

        String name() { return name; }
        long size() { return size; }
        String sha256() { return sha256; }
    }
}
