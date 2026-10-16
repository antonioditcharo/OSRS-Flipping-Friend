package com.flippingfriend.companion;

import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.concurrent.TimeUnit;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class CompanionLifecycleTest
{
    @Rule public final TemporaryFolder folder = new TemporaryFolder();

    @Test public void installerGateRefusesStartupBeforeAnyAccountOrTokenAccess() throws Exception
    {
        Path root = folder.newFolder().toPath();
        Path runtime = Files.createDirectories(root.resolve("runtime"));
        try (FileChannel channel = FileChannel.open(runtime.resolve("installation.lock"),
            StandardOpenOption.CREATE, StandardOpenOption.READ, StandardOpenOption.WRITE);
             FileLock lock = channel.lock(0, 1, false))
        {
            assertNull(CompanionLifecycle.tryStart(root));
            assertFalse(Files.exists(root.resolve("companion")));
        }
        try (CompanionLifecycle ready = CompanionLifecycle.tryStart(root)) { assertNotNull(ready); }
    }

    @Test public void singletonSurvivesStartupGateReleaseUntilProcessCloses() throws Exception
    {
        Path root = folder.newFolder().toPath();
        try (CompanionLifecycle first = CompanionLifecycle.tryStart(root))
        {
            assertNotNull(first);
            first.started();
            assertNull(CompanionLifecycle.tryStart(root));
            assertFalse(Files.exists(root.resolve("companion")));
        }
        try (CompanionLifecycle next = CompanionLifecycle.tryStart(root)) { assertNotNull(next); }
    }

    @Test public void realMainHonorsGateBeforeCreatingCredentialsOrStores() throws Exception
    {
        String previous = System.getProperty("user.home");
        Path home = folder.newFolder().toPath();
        Path root = home.resolve(".runelite/osrs-flipping-friend");
        Path runtime = Files.createDirectories(root.resolve("runtime"));
        try (FileChannel channel = FileChannel.open(runtime.resolve("installation.lock"),
            StandardOpenOption.CREATE, StandardOpenOption.READ, StandardOpenOption.WRITE);
             FileLock lock = channel.lock(0, 1, false))
        {
            try { System.setProperty("user.home", home.toString()); CompanionMain.main(new String[0]); }
            finally { System.setProperty("user.home", previous); }
            assertFalse(Files.exists(root.resolve("companion")));
        }
    }

    @Test public void separateJvmCannotStartWhileFirstInstanceIsAlive() throws Exception
    {
        Path root = folder.newFolder().toPath();
        String classpath = codePath(CompanionLifecycle.class)+java.io.File.pathSeparator+
            codePath(CompanionLifecycleProbe.class)+java.io.File.pathSeparator+codePath(com.google.gson.Gson.class);
        String javaExecutable = java.nio.file.Paths.get(System.getProperty("java.home"), "bin",
            System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java").toString();
        Process first = new ProcessBuilder(javaExecutable, "-cp", classpath, CompanionLifecycleProbe.class.getName(), root.toString())
            .redirectErrorStream(true).start();
        try
        {
            java.io.BufferedReader firstOutput = new java.io.BufferedReader(new java.io.InputStreamReader(first.getInputStream(), StandardCharsets.UTF_8));
            assertEquals("STARTED", firstOutput.readLine());
            Process second = new ProcessBuilder(javaExecutable, "-cp", classpath, CompanionLifecycleProbe.class.getName(), root.toString())
                .redirectErrorStream(true).start();
            try
            {
                assertTrue(second.waitFor(10, TimeUnit.SECONDS));
                assertEquals(0, second.exitValue());
                assertTrue(new String(second.getInputStream().readAllBytes(), StandardCharsets.UTF_8).contains("REFUSED"));
            }
            finally { if (second.isAlive()) second.destroyForcibly(); }
            assertFalse(Files.exists(root.resolve("companion")));
            first.getOutputStream().write('\n'); first.getOutputStream().flush();
            assertTrue(first.waitFor(10, TimeUnit.SECONDS));
            assertEquals(0, first.exitValue());
        }
        finally { if (first.isAlive()) first.destroyForcibly(); }
    }

    private static String codePath(Class<?> type) throws Exception
    {
        return java.nio.file.Paths.get(type.getProtectionDomain().getCodeSource().getLocation().toURI()).toString();
    }

    @Test public void windowsPowerShellExclusiveHandleBlocksRealStartupGate() throws Exception
    {
        org.junit.Assume.assumeTrue(System.getProperty("os.name").startsWith("Windows"));
        Path root = folder.newFolder().toPath();
        Path runtime = Files.createDirectories(root.resolve("runtime"));
        String path = runtime.resolve("installation.lock").toString().replace("'", "''");
        Process holder = new ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive", "-Command",
            "$s=[IO.File]::Open('"+path+"','OpenOrCreate','ReadWrite','None');try{[Console]::WriteLine('LOCKED');[Console]::ReadLine()|Out-Null}finally{$s.Dispose()}")
            .redirectErrorStream(true).start();
        try
        {
            java.io.BufferedReader output = new java.io.BufferedReader(new java.io.InputStreamReader(holder.getInputStream(), StandardCharsets.UTF_8));
            assertEquals("LOCKED", output.readLine());
            assertNull(CompanionLifecycle.tryStart(root));
            assertFalse(Files.exists(root.resolve("companion")));
            holder.getOutputStream().write('\n'); holder.getOutputStream().flush();
            assertTrue(holder.waitFor(10, TimeUnit.SECONDS));
            assertEquals(0, holder.exitValue());
        }
        finally { if (holder.isAlive()) holder.destroyForcibly(); } // Disposable test helper only.
        try (CompanionLifecycle ready = CompanionLifecycle.tryStart(root)) { assertNotNull(ready); }
    }

    @Test public void incompletePairRefusesStartupWithoutTouchingAccountFiles() throws Exception
    {
        Path root = folder.newFolder().toPath();
        Path runtime = Files.createDirectories(root.resolve("runtime"));
        Path jar = runtime.resolve("flipping-friend-companion.jar");
        Path plugin = Files.createDirectories(root.resolveSibling("sideloaded-plugins")).resolve("osrs-flipping-friend.jar");
        Files.write(jar, new byte[]{1}); Files.write(plugin, new byte[]{2});
        String companionHash = hex(Files.readAllBytes(jar));
        String pluginHash = hex(Files.readAllBytes(plugin));
        Path manifest = runtime.resolve("installation.json");
        // Windows PowerShell 5.1's UTF8 manifest writer includes a BOM.
        Files.write(manifest, ("\ufeff{\"schemaVersion\":1,\"companionSha256\":\""+companionHash+"\",\"pluginSha256\":\""+pluginHash+"\"}").getBytes(StandardCharsets.UTF_8));
        CompanionLifecycle.verifyInstalledPair(root, jar);
        Files.write(jar, new byte[]{3});
        try { CompanionLifecycle.verifyInstalledPair(root, jar); fail("partial pair accepted"); }
        catch (java.io.IOException expected) { assertTrue(expected.getMessage().contains("startup refused")); }
        assertFalse(Files.exists(root.resolve("companion")));
    }

    private static String hex(byte[] bytes) throws Exception
    {
        StringBuilder result = new StringBuilder();
        for (byte value : MessageDigest.getInstance("SHA-256").digest(bytes)) result.append(String.format("%02x", value & 255));
        return result.toString();
    }
}
