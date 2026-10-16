package com.flippingfriend.companion;

import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
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
}
