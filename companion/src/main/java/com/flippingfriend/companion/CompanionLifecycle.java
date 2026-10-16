package com.flippingfriend.companion;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/** Startup barrier and single writer, acquired before credentials or any database is opened. */
final class CompanionLifecycle implements AutoCloseable
{
    private FileChannel startup;
    private FileLock startupLock;
    private FileChannel instance;
    private FileLock instanceLock;

    static void verifyInstalledPair(Path root, Path actualCode) throws Exception
    {
        Path jar = root.resolve("runtime/flipping-friend-companion.jar").toAbsolutePath().normalize();
        if (!jar.equals(actualCode.toAbsolutePath().normalize())) return; // Source/development runs.
        Path manifest = root.resolve("runtime/installation.json");
        Path plugin = root.resolveSibling("sideloaded-plugins").resolve("osrs-flipping-friend.jar");
        for (Path file : new Path[] {jar, manifest, plugin})
            if (Files.isSymbolicLink(file) || !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS))
                throw new IOException("Installed runtime pair proof is missing or linked.");
        JsonObject proof;
        try (Reader reader = Files.newBufferedReader(manifest, StandardCharsets.UTF_8))
        {
            proof = JsonParser.parseReader(reader).getAsJsonObject();
        }
        if (!proof.has("schemaVersion") || proof.get("schemaVersion").getAsInt() != 1 ||
            !matchesHash(jar, proof, "companionSha256") || !matchesHash(plugin, proof, "pluginSha256"))
            throw new IOException("Installed runtime pair does not match its manifest; startup refused.");
    }

    private static boolean matchesHash(Path path, JsonObject proof, String key) throws Exception
    {
        if (!proof.has(key) || !proof.get(key).isJsonPrimitive()) return false;
        String expected = proof.get(key).getAsString();
        if (!expected.matches("[0-9a-fA-F]{64}")) return false;
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream input = Files.newInputStream(path))
        {
            byte[] buffer = new byte[65536];
            for (int count; (count = input.read(buffer)) != -1;) digest.update(buffer, 0, count);
        }
        StringBuilder hex = new StringBuilder();
        for (byte value : digest.digest()) hex.append(String.format("%02x", value & 255));
        return expected.equalsIgnoreCase(hex.toString());
    }

    static CompanionLifecycle tryStart(Path root) throws IOException
    {
        Path runtime = root.resolve("runtime");
        Files.createDirectories(runtime);
        CompanionLifecycle result = new CompanionLifecycle();
        try
        {
            // The Windows updater holds this file with FileShare.None. A scheduled launch must
            // also honor its native byte lock, including on platforms with advisory share modes.
            result.startup = FileChannel.open(runtime.resolve("installation.lock"),
                StandardOpenOption.CREATE, StandardOpenOption.READ, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS);
            result.startupLock = result.startup.tryLock(0, 1, false);
            if (result.startupLock == null) { result.close(); return null; }
            result.instance = FileChannel.open(runtime.resolve("companion.instance.lock"),
                StandardOpenOption.CREATE, StandardOpenOption.READ, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS);
            result.instanceLock = result.instance.tryLock(0, 1, false);
            if (result.instanceLock == null) { result.close(); return null; }
            return result;
        }
        catch (OverlappingFileLockException ex) { result.close(); return null; }
        catch (java.nio.file.FileSystemException ex)
        {
            // Windows reports a sharing violation as a filesystem error. Refuse all startup work;
            // this is not evidence that an update succeeded or that the account is healthy.
            System.err.println("Companion startup lock unavailable: " + ex.getMessage());
            result.close(); return null;
        }
        catch (IOException | RuntimeException ex) { result.close(); throw ex; }
    }

    void started() throws IOException
    {
        if (startupLock != null) { startupLock.release(); startupLock = null; }
        if (startup != null) { startup.close(); startup = null; }
    }

    @Override public void close() throws IOException
    {
        try { started(); }
        finally
        {
            try { if (instanceLock != null) { instanceLock.release(); instanceLock = null; } }
            finally { if (instance != null) { instance.close(); instance = null; } }
        }
    }
}
