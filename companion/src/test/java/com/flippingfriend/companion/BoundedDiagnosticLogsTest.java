package com.flippingfriend.companion;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class BoundedDiagnosticLogsTest
{
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    @Test public void oversizedWriteRetainsAnExactSuffixAcrossBoundedArchivesAndRestart() throws Exception
    {
        Path dir = temporary.newFolder().toPath();
        byte[] input = new byte[20000];
        for (int i = 0; i < input.length; i++) input[i] = (byte)('a' + i % 26);
        try (var log = new BoundedDiagnosticLogs.Rolling(dir, "stdout.log", 512, 4, System.err))
        {
            log.write(input); assertEquals(0, log.droppedBytes());
        }
        ByteArrayOutputStream retained = new ByteArrayOutputStream();
        for (int i = 4; i >= 0; i--)
        {
            Path file = dir.resolve(i == 0 ? "stdout.log" : "stdout.log." + i);
            assertTrue(Files.size(file) <= 512);
            byte[] bytes = Files.readAllBytes(file);
            retained.write(bytes, BoundedDiagnosticLogs.HEADER.length, bytes.length - BoundedDiagnosticLogs.HEADER.length);
        }
        assertArrayEquals(java.util.Arrays.copyOfRange(input, input.length - retained.size(), input.length), retained.toByteArray());
        try (var log = new BoundedDiagnosticLogs.Rolling(dir, "stdout.log", 512, 4, System.err)) { log.write('!'); }
        assertTrue(Files.readString(dir.resolve("stdout.log")).endsWith("!"));
        try (var files = Files.list(dir)) { assertEquals(5, files.count()); }
    }

    @Test public void concurrentDiagnosticBurstsStayBoundedAndDoNotTouchAccountEvidence() throws Exception
    {
        Path root = temporary.newFolder().toPath(), logs = root.resolve("logs");
        Path account = root.resolve("flipping-friend.db"); Files.writeString(account, "durable-account-sentinel");
        try (var log = new BoundedDiagnosticLogs.Rolling(logs, "stderr.log", 1024, 4, System.err))
        {
            List<Thread> writers = new ArrayList<>();
            for (int i = 0; i < 4; i++)
            {
                Thread writer = new Thread(() -> { for (int n = 0; n < 1000; n++) log.write(new byte[100], 0, 100); });
                writers.add(writer); writer.start();
            }
            for (Thread writer : writers) writer.join();
            assertEquals(0, log.droppedBytes());
        }
        try (var files = Files.list(logs))
        {
            for (Path file : (Iterable<Path>)files::iterator) assertTrue(Files.size(file) <= 1024);
        }
        assertEquals("durable-account-sentinel", Files.readString(account));
    }

    @Test public void rotationFailureDropsDiagnosticsOnceWithoutThrowingIntoBusinessCode() throws Exception
    {
        Path dir = temporary.newFolder().toPath();
        ByteArrayOutputStream warnings = new ByteArrayOutputStream();
        try (PrintStream fallback = new PrintStream(warnings);
             var log = new BoundedDiagnosticLogs.Rolling(dir, "stdout.log", 128, 2, fallback))
        {
            Files.createDirectory(dir.resolve("stdout.log.1"));
            log.write(new byte[1000]);
            long lost = log.droppedBytes(); assertTrue(lost > 0);
            log.write(new byte[50]); assertEquals(lost + 50, log.droppedBytes());
            assertEquals(1, warnings.toString(StandardCharsets.UTF_8.name()).lines().count());
            assertTrue(Files.size(dir.resolve("stdout.log")) <= 128);
        }
    }

    @Test public void existingUnknownFileAndHardLinkedAccountDataAreNeverAppendedOrDeleted() throws Exception
    {
        Path dir = temporary.newFolder().toPath(), account = temporary.newFile().toPath();
        Files.writeString(account, "SQLite format 3 durable financial sentinel");
        Path log = dir.resolve("stdout.log");
        try { Files.createLink(log, account); }
        catch (IOException | UnsupportedOperationException unavailable) { Files.copy(account, log); }
        try { new BoundedDiagnosticLogs.Rolling(dir, "stdout.log", 512, 4, System.err); fail("Unknown file accepted"); }
        catch (IOException expected) { assertTrue(expected.getMessage().contains("Unrecognized")); }
        assertEquals("SQLite format 3 durable financial sentinel", Files.readString(account));
        assertEquals(Files.readString(account), Files.readString(log));
    }

    @Test public void aLinkedDiagnosticDirectoryRefusesBeforeOpeningItsTarget() throws Exception
    {
        Path root = temporary.newFolder().toPath(), outside = temporary.newFolder().toPath();
        Path linked = root.resolve("linked");
        try { Files.createSymbolicLink(linked, outside); }
        catch (IOException | UnsupportedOperationException unavailable) { org.junit.Assume.assumeNoException(unavailable); }
        try { new BoundedDiagnosticLogs.Rolling(linked, "stdout.log", 512, 4, System.err); fail("Link accepted"); }
        catch (IOException expected) { assertTrue(expected.getMessage().contains("linked")); }
        try (var files = Files.list(outside)) { assertEquals(0, files.count()); }
    }

    @Test public void installedStreamsPreserveUtf8AndRestoreOriginalStreamsOnClose() throws Exception
    {
        Path root = temporary.newFolder().toPath();
        PrintStream beforeOut = System.out, beforeErr = System.err;
        try (var logs = BoundedDiagnosticLogs.install(root))
        {
            System.out.println("diagnostic \u2192 retained");
            System.err.println("error remains diagnosable");
        }
        assertSame(beforeOut, System.out); assertSame(beforeErr, System.err);
        assertTrue(Files.readString(root.resolve("diagnostics/runtime-logs/stdout.log")).contains("diagnostic \u2192 retained"));
        assertTrue(Files.readString(root.resolve("diagnostics/runtime-logs/stderr.log")).contains("error remains diagnosable"));
        assertFalse(Files.exists(root.resolve("companion")));
    }
}
