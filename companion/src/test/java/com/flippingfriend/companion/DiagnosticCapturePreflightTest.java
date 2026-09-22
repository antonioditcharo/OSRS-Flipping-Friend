package com.flippingfriend.companion;

import com.google.gson.JsonObject;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.GZIPOutputStream;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class DiagnosticCapturePreflightTest
{
    @Rule
    public final TemporaryFolder temporary = new TemporaryFolder();

    @Test
    public void validatesEveryFileIdentityFromTheManifest() throws Exception
    {
        Fixture fixture = fixture();
        DiagnosticCaptureManifest manifest = writeManifest(fixture, null, null);

        DiagnosticCapturePreflight.Capture capture =
                DiagnosticCapturePreflight.validate(fixture.root, manifest);

        assertEquals(fixture.evidence, capture.evidence());
        assertEquals(fixture.root.resolve("snap.json"), capture.snap());
        assertEquals(fixture.root.resolve("mapping.json"), capture.mapping());
        assertEquals(fixture.root.resolve("series-cache.json.gz"), capture.seriesCache());
    }

    @Test
    public void rejectsAManifestWithTheWrongSupportingFileIdentity() throws Exception
    {
        Fixture fixture = fixture();
        DiagnosticCaptureManifest manifest = writeManifest(
                fixture, DiagnosticCaptureManifest.SNAP_NAME,
                "0000000000000000000000000000000000000000000000000000000000000000");

        expectFailure("snap.json SHA-256 mismatch", () ->
                DiagnosticCapturePreflight.validate(fixture.root, manifest));
    }

    @Test
    public void authoritativeEntryPointRequiresTheManifest() throws Exception
    {
        Fixture fixture = fixture();

        expectFailure("manifest is not a regular file", () ->
                DiagnosticCapturePreflight.requireAuthoritative(fixture.root));
    }

    @Test
    public void authoritativeEntryPointRejectsAnotherDatabaseIdentity() throws Exception
    {
        Fixture fixture = fixture();
        writeManifest(fixture, null, null);

        expectFailure("manifest does not identify the authoritative evidence database", () ->
                DiagnosticCapturePreflight.requireAuthoritative(fixture.root));
    }

    @Test
    public void acceptsACompleteReadOnlyCaptureContract() throws Exception
    {
        Fixture fixture = fixture();

        DiagnosticCapturePreflight.Capture capture =
                DiagnosticCapturePreflight.validate(
                        fixture.root, fixture.databaseSize, fixture.databaseSha256);

        assertEquals(fixture.root.toAbsolutePath().normalize(), capture.root());
        assertEquals(fixture.evidence, capture.evidence());
        assertEquals(fixture.root.resolve("snap.json"), capture.snap());
        assertEquals(fixture.root.resolve("mapping.json"), capture.mapping());
        assertEquals(fixture.root.resolve("series-cache.json.gz"), capture.seriesCache());
        assertEquals(fixture.databaseSize, capture.databaseSize());
        assertEquals(fixture.databaseSha256, capture.databaseSha256());
    }

    @Test
    public void rejectsADatabaseWithTheWrongSize() throws Exception
    {
        Fixture fixture = fixture();

        expectFailure("size mismatch", () ->
                DiagnosticCapturePreflight.validate(
                        fixture.root, fixture.databaseSize + 1, fixture.databaseSha256));
    }

    @Test
    public void rejectsADatabaseWithTheWrongHash() throws Exception
    {
        Fixture fixture = fixture();

        expectFailure("SHA-256 mismatch", () ->
                DiagnosticCapturePreflight.validate(
                        fixture.root, fixture.databaseSize,
                        "0000000000000000000000000000000000000000000000000000000000000000"));
    }

    @Test
    public void refusesEverySqliteSidecarInsteadOfDeletingIt() throws Exception
    {
        String[] suffixes = {"-wal", "-shm", "-journal"};
        String[] labels = {"WAL", "SHM", "journal"};

        for (int index = 0; index < suffixes.length; index++)
        {
            Fixture fixture = fixture();
            Path sidecar = fixture.root.resolve("flipping-friend-evidence.db" + suffixes[index]);
            Files.write(sidecar, new byte[]{1, 2, 3});

            expectFailure(labels[index] + " sidecar must be absent", () ->
                    DiagnosticCapturePreflight.validate(
                            fixture.root, fixture.databaseSize, fixture.databaseSha256));

            assertTrue("preflight must never delete " + sidecar, Files.exists(sidecar));
        }
    }

    @Test
    public void rejectsMissingAndEmptyRequiredFiles() throws Exception
    {
        Fixture missing = fixture();
        Files.delete(missing.root.resolve("mapping.json"));

        expectFailure("required capture file is absent", () ->
                DiagnosticCapturePreflight.validate(
                        missing.root, missing.databaseSize, missing.databaseSha256));

        Fixture empty = fixture();
        Files.write(empty.root.resolve("snap.json"), new byte[0]);

        expectFailure("required capture file is empty", () ->
                DiagnosticCapturePreflight.validate(
                        empty.root, empty.databaseSize, empty.databaseSha256));
    }

    @Test
    public void validatesSnapshotMappingAndSeriesShapes() throws Exception
    {
        Fixture snapshot = fixture();
        Files.write(snapshot.root.resolve("snap.json"), "{}".getBytes(StandardCharsets.UTF_8));

        expectFailure("snap.json is missing object field latest", () ->
                DiagnosticCapturePreflight.validate(
                        snapshot.root, snapshot.databaseSize, snapshot.databaseSha256));

        Fixture mapping = fixture();
        Files.write(mapping.root.resolve("mapping.json"), "[]".getBytes(StandardCharsets.UTF_8));

        expectFailure("mapping.json must not be empty", () ->
                DiagnosticCapturePreflight.validate(
                        mapping.root, mapping.databaseSize, mapping.databaseSha256));

        Fixture series = fixture();
        writeGzip(series.root.resolve("series-cache.json.gz"), "{\"entries\":[]}");

        expectFailure("entries array must not be empty", () ->
                DiagnosticCapturePreflight.validate(
                        series.root, series.databaseSize, series.databaseSha256));
    }

    @Test
    public void authoritativeIdentityIsPinned() throws Exception
    {
        assertEquals(1_135_456_256L,
                DiagnosticCapturePreflight.AUTHORITATIVE_DATABASE_SIZE);
        assertEquals(
                "989E48254DA68F68F16DB6A871EFDE9D2B3B267A437EA2B205B7D83C6243B205",
                DiagnosticCapturePreflight.AUTHORITATIVE_DATABASE_SHA256);
    }

    private Fixture fixture() throws Exception
    {
        Path root = temporary.newFolder().toPath();
        Path evidence = root.resolve("flipping-friend-evidence.db");

        Files.write(evidence, "diagnostic database fixture".getBytes(StandardCharsets.UTF_8));
        Files.write(root.resolve("snap.json"),
                ("{\"observedAt\":12345,\"latest\":{},"
                        + "\"fiveMinute\":{},\"hourly\":{}}")
                        .getBytes(StandardCharsets.UTF_8));
        Files.write(root.resolve("mapping.json"),
                ("[{\"id\":1,\"name\":\"Fixture item\","
                        + "\"limit\":100,\"members\":false}]")
                        .getBytes(StandardCharsets.UTF_8));
        writeGzip(root.resolve("series-cache.json.gz"),
                "{\"entries\":[{\"key\":\"1@5m\",\"data\":[]}]}");

        return new Fixture(
                root,
                evidence,
                Files.size(evidence),
                DiagnosticCapturePreflight.sha256(evidence));
    }

    private static DiagnosticCaptureManifest writeManifest(Fixture fixture,
            String replacedHashName, String replacementHash) throws Exception
    {
        JsonObject root = new JsonObject();
        root.addProperty("formatVersion", 1);
        root.addProperty("captureId", "fixture-capture");
        root.addProperty("requireSidecarsAbsent", true);

        JsonObject files = new JsonObject();
        addIdentity(files, fixture.evidence, DiagnosticCaptureManifest.DATABASE_NAME,
                replacedHashName, replacementHash);
        addIdentity(files, fixture.root.resolve(DiagnosticCaptureManifest.SNAP_NAME),
                DiagnosticCaptureManifest.SNAP_NAME, replacedHashName, replacementHash);
        addIdentity(files, fixture.root.resolve(DiagnosticCaptureManifest.MAPPING_NAME),
                DiagnosticCaptureManifest.MAPPING_NAME, replacedHashName, replacementHash);
        addIdentity(files, fixture.root.resolve(DiagnosticCaptureManifest.SERIES_NAME),
                DiagnosticCaptureManifest.SERIES_NAME, replacedHashName, replacementHash);
        root.add("files", files);

        Path path = fixture.root.resolve(DiagnosticCaptureManifest.MANIFEST_NAME);
        Files.write(path, root.toString().getBytes(StandardCharsets.UTF_8));
        return DiagnosticCaptureManifest.read(path);
    }

    private static void addIdentity(JsonObject files, Path path, String name,
            String replacedHashName, String replacementHash) throws Exception
    {
        JsonObject identity = new JsonObject();
        identity.addProperty("size", Files.size(path));
        identity.addProperty("sha256", name.equals(replacedHashName)
                ? replacementHash : DiagnosticCapturePreflight.sha256(path));
        files.add(name, identity);
    }

    private static void writeGzip(Path path, String text) throws Exception
    {
        try (Writer writer = new OutputStreamWriter(
                new GZIPOutputStream(Files.newOutputStream(path)), StandardCharsets.UTF_8))
        {
            writer.write(text);
        }
    }

    private static void expectFailure(String expected, CheckedRunnable runnable) throws Exception
    {
        try
        {
            runnable.run();
            fail("expected failure containing: " + expected);
        }
        catch (IllegalArgumentException | IllegalStateException failure)
        {
            assertTrue("unexpected message: " + failure.getMessage(),
                    failure.getMessage() != null
                            && failure.getMessage().contains(expected));
        }
    }

    private interface CheckedRunnable
    {
        void run() throws Exception;
    }

    private static final class Fixture
    {
        private final Path root;
        private final Path evidence;
        private final long databaseSize;
        private final String databaseSha256;

        private Fixture(Path root, Path evidence, long databaseSize, String databaseSha256)
        {
            this.root = root;
            this.evidence = evidence;
            this.databaseSize = databaseSize;
            this.databaseSha256 = databaseSha256;
        }
    }
}
