package com.flippingfriend.companion;

import java.io.PrintStream;
import java.nio.file.Path;
import java.nio.file.Paths;

/** Read-only command-line attestation for a manifest-backed diagnostic capture. */
public final class DiagnosticCaptureVerifier
{
    private static final String[] FILE_NAMES = {
            DiagnosticCaptureManifest.DATABASE_NAME,
            DiagnosticCaptureManifest.SNAP_NAME,
            DiagnosticCaptureManifest.MAPPING_NAME,
            DiagnosticCaptureManifest.SERIES_NAME
    };

    private DiagnosticCaptureVerifier()
    {
    }

    public static void main(String[] args) throws Exception
    {
        if (args.length != 1)
        {
            throw new IllegalArgumentException(
                    "usage: DiagnosticCaptureVerifier <capture-directory>");
        }
        attest(Paths.get(args[0]), System.out, true);
    }

    static void attest(Path root, PrintStream output, boolean requireAuthoritative) throws Exception
    {
        if (root == null)
        {
            throw new IllegalArgumentException("capture directory is required");
        }
        if (output == null)
        {
            throw new IllegalArgumentException("output is required");
        }

        Path normalized = root.toAbsolutePath().normalize();
        DiagnosticCaptureManifest manifest = DiagnosticCaptureManifest.read(
                normalized.resolve(DiagnosticCaptureManifest.MANIFEST_NAME));

        if (requireAuthoritative)
        {
            DiagnosticCapturePreflight.requireAuthoritative(normalized);
        }
        else
        {
            DiagnosticCapturePreflight.validate(normalized, manifest);
        }

        output.println("captureDirectory=" + normalized);
        output.println("captureId=" + manifest.captureId());
        for (String name : FILE_NAMES)
        {
            DiagnosticCaptureManifest.FileIdentity identity = manifest.file(name);
            output.println("file=" + name
                    + " size=" + identity.size()
                    + " sha256=" + identity.sha256());
        }
        output.println("verified=true");
    }
}
