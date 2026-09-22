package com.flippingfriend.companion;

import com.google.gson.JsonObject;
import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.GZIPOutputStream;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class DiagnosticCaptureVerifierTest
{
    @Rule
    public final TemporaryFolder temporary = new TemporaryFolder();

    @Test
    public void completeCaptureProducesDeterministicIdentityOnlyAttestation() throws Exception
    {
        Fixture fixture = fixture("complete");
        String first = attest(fixture);
        String second = attest(fixture);

        assertEquals(first, second);
        assertTrue(first.contains("captureId=fixture-capture\n"));
        assertTrue(first.endsWith("verified=true\n"));
        assertEquals(4, count(first, "file="));
        for (String name : fixture.files.keySet())
        {
            Path path = fixture.root.resolve(name);
            assertTrue(first.contains("file=" + name
                    + " size=" + Files.size(path)
                    + " sha256=" + DiagnosticCapturePreflight.sha256(path) + "\n"));
        }
    }

    @Test
    public void commandLineRequiresExactlyOneArgument() throws Exception
    {
        expectFailure("usage: DiagnosticCaptureVerifier", () ->
                DiagnosticCaptureVerifier.main(new String[0]));
        expectFailure("usage: DiagnosticCaptureVerifier", () ->
                DiagnosticCaptureVerifier.main(new String[]{"one", "two"}));
    }

    @Test
    public void missingManifestIsRefused() throws Exception
    {
        Fixture fixture = fixture("missing-manifest");
        Files.delete(fixture.manifest);
        expectFailure("manifest is not a regular file", () -> attest(fixture));
    }

    @Test
    public void wrongSupportingIdentityIsRefused() throws Exception
    {
        Fixture fixture = fixture("wrong-identity");
        writeManifest(fixture, DiagnosticCaptureManifest.SNAP_NAME,
                "0000000000000000000000000000000000000000000000000000000000000000");
        expectFailure("snap.json SHA-256 mismatch", () -> attest(fixture));
    }

    @Test
    public void missingAndEmptyCaptureFilesAreRefused() throws Exception
    {
        Fixture missing = fixture("missing-file");
        Files.delete(missing.root.resolve(DiagnosticCaptureManifest.MAPPING_NAME));
        expectFailure("required capture file is absent", () -> attest(missing));

        Fixture empty = fixture("empty-file");
        Files.write(empty.root.resolve(DiagnosticCaptureManifest.SNAP_NAME), new byte[0]);
        expectFailure("required capture file is empty", () -> attest(empty));
    }

    @Test
    public void everySqliteSidecarIsRefusedAndPreserved() throws Exception
    {
        String[] suffixes = {"-wal", "-shm", "-journal"};
        String[] labels = {"WAL", "SHM", "journal"};
        for (int index = 0; index < suffixes.length; index++)
        {
            Fixture fixture = fixture("sidecar-" + index);
            Path sidecar = fixture.root.resolve(
                    DiagnosticCaptureManifest.DATABASE_NAME + suffixes[index]);
            byte[] sentinel = new byte[]{1, 2, 3};
            Files.write(sidecar, sentinel);
            expectFailure(labels[index] + " sidecar must be absent", () -> attest(fixture));
            assertArrayEquals(sentinel, Files.readAllBytes(sidecar));
        }
    }

    @Test
    public void shapeFailuresRemainVisible() throws Exception
    {
        Fixture fixture = fixture("shape");
        Files.write(fixture.root.resolve(DiagnosticCaptureManifest.SNAP_NAME),
                "{}".getBytes(StandardCharsets.UTF_8));
        writeManifest(fixture, null, null);
        expectFailure("snap.json is missing object field latest", () -> attest(fixture));
    }

    @Test
    public void verificationLeavesManifestAndCaptureFilesByteIdentical() throws Exception
    {
        Fixture fixture = fixture("unchanged");
        Map<Path, byte[]> before = new LinkedHashMap<>();
        before.put(fixture.manifest, Files.readAllBytes(fixture.manifest));
        for (String name : fixture.files.keySet())
        {
            Path path = fixture.root.resolve(name);
            before.put(path, Files.readAllBytes(path));
        }

        attest(fixture);

        for (Map.Entry<Path, byte[]> entry : before.entrySet())
        {
            assertArrayEquals(entry.getValue(), Files.readAllBytes(entry.getKey()));
        }
    }

    private String attest(Fixture fixture) throws Exception
    {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (PrintStream output = new PrintStream(bytes, true, StandardCharsets.UTF_8.name()))
        {
            DiagnosticCaptureVerifier.attest(fixture.root, output, false);
        }
        byte[] raw = bytes.toByteArray();
        for (byte value : raw)
        {
            assertTrue("attestation must use LF line endings", value != (byte) '\r');
        }
        return new String(raw, StandardCharsets.UTF_8);
    }

    private Fixture fixture(String name) throws Exception
    {
        Path root = temporary.newFolder(name).toPath();
        Map<String, byte[]> files = new LinkedHashMap<>();
        files.put(DiagnosticCaptureManifest.DATABASE_NAME,
                "database fixture".getBytes(StandardCharsets.UTF_8));
        files.put(DiagnosticCaptureManifest.SNAP_NAME,
                "{\"observedAt\":1,\"latest\":{},\"fiveMinute\":{},\"hourly\":{}}"
                        .getBytes(StandardCharsets.UTF_8));
        files.put(DiagnosticCaptureManifest.MAPPING_NAME,
                "[{\"id\":1}]".getBytes(StandardCharsets.UTF_8));
        for (Map.Entry<String, byte[]> entry : files.entrySet())
        {
            Files.write(root.resolve(entry.getKey()), entry.getValue());
        }
        Path series = root.resolve(DiagnosticCaptureManifest.SERIES_NAME);
        try (Writer writer = new OutputStreamWriter(
                new GZIPOutputStream(Files.newOutputStream(series)), StandardCharsets.UTF_8))
        {
            writer.write("{\"entries\":[{\"key\":\"1@5m\",\"data\":[]}]}");
        }
        files.put(DiagnosticCaptureManifest.SERIES_NAME, Files.readAllBytes(series));
        Fixture fixture = new Fixture(root, files,
                root.resolve(DiagnosticCaptureManifest.MANIFEST_NAME));
        writeManifest(fixture, null, null);
        return fixture;
    }

    private void writeManifest(Fixture fixture, String replacedName, String replacementHash)
            throws Exception
    {
        JsonObject root = new JsonObject();
        root.addProperty("formatVersion", 1);
        root.addProperty("captureId", "fixture-capture");
        root.addProperty("requireSidecarsAbsent", true);
        JsonObject files = new JsonObject();
        for (String name : fixture.files.keySet())
        {
            Path path = fixture.root.resolve(name);
            JsonObject identity = new JsonObject();
            identity.addProperty("size", Files.size(path));
            identity.addProperty("sha256", name.equals(replacedName)
                    ? replacementHash : DiagnosticCapturePreflight.sha256(path));
            files.add(name, identity);
        }
        root.add("files", files);
        Files.write(fixture.manifest, root.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static int count(String text, String token)
    {
        int count = 0;
        int offset = 0;
        while ((offset = text.indexOf(token, offset)) >= 0)
        {
            count++;
            offset += token.length();
        }
        return count;
    }

    private static void expectFailure(String expected, CheckedRunnable action) throws Exception
    {
        try
        {
            action.run();
            fail("expected failure containing: " + expected);
        }
        catch (IllegalArgumentException | IllegalStateException failure)
        {
            assertTrue("unexpected message: " + failure.getMessage(),
                    failure.getMessage() != null && failure.getMessage().contains(expected));
        }
    }

    private interface CheckedRunnable
    {
        void run() throws Exception;
    }

    private static final class Fixture
    {
        private final Path root;
        private final Map<String, byte[]> files;
        private final Path manifest;

        private Fixture(Path root, Map<String, byte[]> files, Path manifest)
        {
            this.root = root;
            this.files = files;
            this.manifest = manifest;
        }
    }
}
