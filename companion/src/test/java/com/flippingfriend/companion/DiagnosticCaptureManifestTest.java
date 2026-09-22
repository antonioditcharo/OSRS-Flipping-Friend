package com.flippingfriend.companion;

import com.google.gson.JsonObject;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class DiagnosticCaptureManifestTest
{
    private static final String HASH_A =
            "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";
    private static final String HASH_B =
            "BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB";
    private static final String HASH_C =
            "CCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCC";
    private static final String HASH_D =
            "DDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDD";

    @Rule
    public final TemporaryFolder temporary = new TemporaryFolder();

    @Test
    public void acceptsTheCompleteVersionOneContract() throws Exception
    {
        DiagnosticCaptureManifest manifest = DiagnosticCaptureManifest.parse(valid());

        assertEquals(1, manifest.formatVersion());
        assertEquals("20260920-143030", manifest.captureId());
        assertTrue(manifest.requireSidecarsAbsent());
        assertEquals(4, manifest.files().size());

        DiagnosticCaptureManifest.FileIdentity database =
                manifest.file(DiagnosticCaptureManifest.DATABASE_NAME);

        assertEquals(DiagnosticCaptureManifest.DATABASE_NAME, database.name());
        assertEquals(1_135_456_256L, database.size());
        assertEquals(HASH_A, database.sha256());
    }

    @Test
    public void readsAManifestWithoutOpeningReferencedCaptureFiles() throws Exception
    {
        Path file = temporary.newFile(DiagnosticCaptureManifest.MANIFEST_NAME).toPath();
        Files.write(file, valid().toString().getBytes(StandardCharsets.UTF_8));

        DiagnosticCaptureManifest manifest = DiagnosticCaptureManifest.read(file);

        assertEquals("20260920-143030", manifest.captureId());
        assertEquals(4, manifest.files().size());
    }

    @Test
    public void rejectsUnsupportedVersionsAndInvalidCaptureIds() throws Exception
    {
        JsonObject version = valid();
        version.addProperty("formatVersion", 2);
        expectFailure("unsupported manifest formatVersion", version);

        JsonObject blank = valid();
        blank.addProperty("captureId", "");
        expectFailure("captureId must not be empty", blank);

        JsonObject traversal = valid();
        traversal.addProperty("captureId", "../capture");
        expectFailure("captureId must contain only", traversal);
    }

    @Test
    public void requiresSidecarAbsenceToBeExplicitlyTrue() throws Exception
    {
        JsonObject absent = valid();
        absent.remove("requireSidecarsAbsent");
        expectFailure("requireSidecarsAbsent must be explicitly true", absent);

        JsonObject falseValue = valid();
        falseValue.addProperty("requireSidecarsAbsent", false);
        expectFailure("requireSidecarsAbsent must be explicitly true", falseValue);
    }

    @Test
    public void requiresExactlyTheFourFixedCaptureFiles() throws Exception
    {
        JsonObject missing = valid();
        missing.getAsJsonObject("files").remove(DiagnosticCaptureManifest.SNAP_NAME);
        expectFailure("files must contain exactly", missing);

        JsonObject extra = valid();
        extra.getAsJsonObject("files").add("other.json", identity(1, HASH_A));
        expectFailure("files must contain exactly", extra);
    }

    @Test
    public void rejectsInvalidSizesAndHashes() throws Exception
    {
        JsonObject zero = valid();
        zero.getAsJsonObject("files")
                .getAsJsonObject(DiagnosticCaptureManifest.MAPPING_NAME)
                .addProperty("size", 0);
        expectFailure("file size must be positive", zero);

        JsonObject fractional = valid();
        fractional.getAsJsonObject("files")
                .getAsJsonObject(DiagnosticCaptureManifest.MAPPING_NAME)
                .addProperty("size", 1.5);
        expectFailure("size must be an integer", fractional);

        JsonObject hash = valid();
        hash.getAsJsonObject("files")
                .getAsJsonObject(DiagnosticCaptureManifest.MAPPING_NAME)
                .addProperty("sha256", "not-a-hash");
        expectFailure("file SHA-256 must contain 64 hex digits", hash);
    }

    @Test
    public void rejectsUnknownFieldsAtEveryManifestLevel() throws Exception
    {
        JsonObject top = valid();
        top.addProperty("notes", "not part of version one");
        expectFailure("unknown manifest field: notes", top);

        JsonObject identity = valid();
        identity.getAsJsonObject("files")
                .getAsJsonObject(DiagnosticCaptureManifest.SNAP_NAME)
                .addProperty("path", "/tmp/snap.json");
        expectFailure("unknown manifest field: path", identity);
    }

    @Test
    public void exposedFileMapIsReadOnly() throws Exception
    {
        DiagnosticCaptureManifest manifest = DiagnosticCaptureManifest.parse(valid());
        Map<String, DiagnosticCaptureManifest.FileIdentity> files = manifest.files();

        try
        {
            files.clear();
            fail("manifest files must be immutable");
        }
        catch (UnsupportedOperationException expected)
        {
            assertEquals(4, manifest.files().size());
        }
    }

    private static JsonObject valid()
    {
        JsonObject root = new JsonObject();
        root.addProperty("formatVersion", 1);
        root.addProperty("captureId", "20260920-143030");
        root.addProperty("requireSidecarsAbsent", true);

        JsonObject files = new JsonObject();
        files.add(DiagnosticCaptureManifest.DATABASE_NAME,
                identity(1_135_456_256L, HASH_A));
        files.add(DiagnosticCaptureManifest.SNAP_NAME, identity(200, HASH_B));
        files.add(DiagnosticCaptureManifest.MAPPING_NAME, identity(300, HASH_C));
        files.add(DiagnosticCaptureManifest.SERIES_NAME, identity(400, HASH_D));

        root.add("files", files);
        return root;
    }

    private static JsonObject identity(long size, String sha256)
    {
        JsonObject identity = new JsonObject();
        identity.addProperty("size", size);
        identity.addProperty("sha256", sha256);
        return identity;
    }

    private static void expectFailure(String expected, JsonObject manifest) throws Exception
    {
        try
        {
            DiagnosticCaptureManifest.parse(manifest);
            fail("expected failure containing: " + expected);
        }
        catch (IllegalArgumentException failure)
        {
            assertTrue("unexpected message: " + failure.getMessage(),
                    failure.getMessage() != null
                            && failure.getMessage().contains(expected));
        }
    }
}
