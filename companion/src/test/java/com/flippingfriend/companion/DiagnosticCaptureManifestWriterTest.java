package com.flippingfriend.companion;

import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class DiagnosticCaptureManifestWriterTest
{
    @Rule
    public final TemporaryFolder temporary = new TemporaryFolder();

    @Test
    public void writesAParseableVersionOneManifestWithExactIdentities() throws Exception
    {
        Fixture fixture = fixture("first");
        Path manifestPath = DiagnosticCaptureManifestWriter.write(
                fixture.root, "20260920-143030");

        DiagnosticCaptureManifest manifest = DiagnosticCaptureManifest.read(manifestPath);

        assertEquals(1, manifest.formatVersion());
        assertEquals("20260920-143030", manifest.captureId());
        assertTrue(manifest.requireSidecarsAbsent());
        assertEquals(4, manifest.files().size());

        for (Map.Entry<String, byte[]> entry : fixture.contents.entrySet())
        {
            DiagnosticCaptureManifest.FileIdentity identity = manifest.file(entry.getKey());
            Path path = fixture.root.resolve(entry.getKey());
            assertEquals(entry.getValue().length, identity.size());
            assertEquals(DiagnosticCapturePreflight.sha256(path), identity.sha256());
        }
    }

    @Test
    public void unchangedInputsProduceByteIdenticalOutput() throws Exception
    {
        Fixture first = fixture("same-a");
        Fixture second = fixture("same-b");

        Path firstManifest = DiagnosticCaptureManifestWriter.write(first.root, "stable-capture");
        Path secondManifest = DiagnosticCaptureManifestWriter.write(second.root, "stable-capture");

        assertArrayEquals(Files.readAllBytes(firstManifest), Files.readAllBytes(secondManifest));
    }

    @Test
    public void outputIsUtf8WithoutBomAndUsesLfLineEndings() throws Exception
    {
        Fixture fixture = fixture("encoding");
        byte[] output = Files.readAllBytes(
                DiagnosticCaptureManifestWriter.write(fixture.root, "utf8-capture"));

        assertTrue(output.length > 3);
        assertTrue(!(output[0] == (byte) 0xef
                && output[1] == (byte) 0xbb
                && output[2] == (byte) 0xbf));
        assertTrue(output[output.length - 1] == (byte) '\n');
        for (byte value : output)
        {
            assertTrue("manifest must not contain CR", value != (byte) '\r');
        }
        new String(output, StandardCharsets.UTF_8);
    }

    @Test
    public void invalidCaptureIdIsRejectedBeforeAnythingIsWritten() throws Exception
    {
        Fixture fixture = fixture("invalid-id");

        expectFailure("captureId must contain only", () ->
                DiagnosticCaptureManifestWriter.write(fixture.root, "../capture"));

        assertTrue(!Files.exists(fixture.manifest()));
    }

    @Test
    public void missingAndEmptyRequiredFilesAreRejected() throws Exception
    {
        Fixture missing = fixture("missing");
        Files.delete(missing.root.resolve(DiagnosticCaptureManifest.MAPPING_NAME));
        expectFailure("required capture file is absent", () ->
                DiagnosticCaptureManifestWriter.write(missing.root, "missing-capture"));
        assertTrue(!Files.exists(missing.manifest()));

        Fixture empty = fixture("empty");
        Files.write(empty.root.resolve(DiagnosticCaptureManifest.SNAP_NAME), new byte[0]);
        expectFailure("required capture file is empty", () ->
                DiagnosticCaptureManifestWriter.write(empty.root, "empty-capture"));
        assertTrue(!Files.exists(empty.manifest()));
    }

    @Test
    public void refusesEverySqliteSidecarWithoutDeletingIt() throws Exception
    {
        String[] suffixes = {"-wal", "-shm", "-journal"};
        String[] labels = {"WAL", "SHM", "journal"};

        for (int index = 0; index < suffixes.length; index++)
        {
            Fixture fixture = fixture("sidecar-" + index);
            Path sidecar = fixture.root.resolve(
                    DiagnosticCaptureManifest.DATABASE_NAME + suffixes[index]);
            Files.write(sidecar, new byte[]{1, 2, 3});

            expectFailure(labels[index] + " sidecar must be absent", () ->
                    DiagnosticCaptureManifestWriter.write(fixture.root, "sidecar-capture"));

            assertTrue(Files.exists(sidecar));
            assertArrayEquals(new byte[]{1, 2, 3}, Files.readAllBytes(sidecar));
            assertTrue(!Files.exists(fixture.manifest()));
        }
    }

    @Test
    public void existingManifestIsNeverOverwritten() throws Exception
    {
        Fixture fixture = fixture("existing");
        byte[] sentinel = "do not replace".getBytes(StandardCharsets.UTF_8);
        Files.write(fixture.manifest(), sentinel);

        try
        {
            DiagnosticCaptureManifestWriter.write(fixture.root, "existing-capture");
            fail("existing manifest must be refused");
        }
        catch (FileAlreadyExistsException expected)
        {
            assertArrayEquals(sentinel, Files.readAllBytes(fixture.manifest()));
        }
    }

    @Test
    public void captureFilesRemainByteIdenticalAfterManifestCreation() throws Exception
    {
        Fixture fixture = fixture("unchanged");
        Map<String, byte[]> before = new LinkedHashMap<>();
        for (String name : fixture.contents.keySet())
        {
            before.put(name, Files.readAllBytes(fixture.root.resolve(name)));
        }

        DiagnosticCaptureManifestWriter.write(fixture.root, "unchanged-capture");

        for (Map.Entry<String, byte[]> entry : before.entrySet())
        {
            assertArrayEquals(entry.getValue(),
                    Files.readAllBytes(fixture.root.resolve(entry.getKey())));
        }
    }

    private Fixture fixture(String name) throws Exception
    {
        Path root = temporary.newFolder(name).toPath();
        Map<String, byte[]> contents = new LinkedHashMap<>();
        contents.put(DiagnosticCaptureManifest.DATABASE_NAME,
                "database fixture".getBytes(StandardCharsets.UTF_8));
        contents.put(DiagnosticCaptureManifest.SNAP_NAME,
                "{\"observedAt\":1,\"latest\":{},\"fiveMinute\":{},\"hourly\":{}}"
                        .getBytes(StandardCharsets.UTF_8));
        contents.put(DiagnosticCaptureManifest.MAPPING_NAME,
                "[{\"id\":1}]".getBytes(StandardCharsets.UTF_8));
        contents.put(DiagnosticCaptureManifest.SERIES_NAME,
                "gzip identity fixture".getBytes(StandardCharsets.UTF_8));

        for (Map.Entry<String, byte[]> entry : contents.entrySet())
        {
            Files.write(root.resolve(entry.getKey()), entry.getValue());
        }
        return new Fixture(root, contents);
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
        private final Map<String, byte[]> contents;

        private Fixture(Path root, Map<String, byte[]> contents)
        {
            this.root = root;
            this.contents = contents;
        }

        private Path manifest()
        {
            return root.resolve(DiagnosticCaptureManifest.MANIFEST_NAME);
        }
    }
}
