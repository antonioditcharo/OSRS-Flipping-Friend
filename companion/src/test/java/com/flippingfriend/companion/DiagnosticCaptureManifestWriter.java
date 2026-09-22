package com.flippingfriend.companion;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

/** Explicit, read-only capture inventory that creates only a new version-one manifest. */
public final class DiagnosticCaptureManifestWriter
{
    private static final String[] FILE_NAMES = {
            DiagnosticCaptureManifest.DATABASE_NAME,
            DiagnosticCaptureManifest.SNAP_NAME,
            DiagnosticCaptureManifest.MAPPING_NAME,
            DiagnosticCaptureManifest.SERIES_NAME
    };

    private DiagnosticCaptureManifestWriter()
    {
    }

    public static void main(String[] args) throws Exception
    {
        if (args.length != 2)
        {
            throw new IllegalArgumentException(
                    "usage: DiagnosticCaptureManifestWriter <capture-directory> <capture-id>");
        }

        Path manifest = write(Paths.get(args[0]), args[1]);
        System.out.println(manifest.toAbsolutePath().normalize());
    }

    static Path write(Path root, String captureId) throws Exception
    {
        if (root == null)
        {
            throw new IllegalArgumentException("capture directory is required");
        }

        Path normalized = root.toAbsolutePath().normalize();
        if (!Files.isDirectory(normalized))
        {
            throw new IllegalArgumentException("capture directory is not a directory: " + root);
        }

        Path manifestPath = normalized.resolve(DiagnosticCaptureManifest.MANIFEST_NAME);
        if (Files.exists(manifestPath))
        {
            throw new FileAlreadyExistsException(manifestPath.toString());
        }

        Path evidence = normalized.resolve(DiagnosticCaptureManifest.DATABASE_NAME);
        rejectSidecar(evidence.resolveSibling(evidence.getFileName() + "-wal"), "WAL");
        rejectSidecar(evidence.resolveSibling(evidence.getFileName() + "-shm"), "SHM");
        rejectSidecar(evidence.resolveSibling(evidence.getFileName() + "-journal"), "journal");

        JsonObject rootObject = new JsonObject();
        rootObject.addProperty("formatVersion", DiagnosticCaptureManifest.FORMAT_VERSION);
        rootObject.addProperty("captureId", captureId);

        JsonObject files = new JsonObject();
        for (String name : FILE_NAMES)
        {
            Path path = requiredRegularFile(normalized.resolve(name));
            JsonObject identity = new JsonObject();
            identity.addProperty("size", Files.size(path));
            identity.addProperty("sha256", DiagnosticCapturePreflight.sha256(path));
            files.add(name, identity);
        }
        rootObject.add("files", files);
        rootObject.addProperty("requireSidecarsAbsent", true);

        DiagnosticCaptureManifest.parse(rootObject);

        Gson gson = new GsonBuilder()
                .disableHtmlEscaping()
                .setPrettyPrinting()
                .create();
        byte[] output = (gson.toJson(rootObject) + "\n").getBytes(StandardCharsets.UTF_8);

        Files.write(manifestPath, output, StandardOpenOption.CREATE_NEW);
        DiagnosticCaptureManifest.read(manifestPath);
        return manifestPath;
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
            throw new IllegalStateException(
                    type + " sidecar must be absent before manifest creation: " + path);
        }
    }
}
