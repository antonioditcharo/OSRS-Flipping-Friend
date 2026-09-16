package com.flippingfriend;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.Assert;
import org.junit.Test;

public class SupportedLifecycleBoundaryTest
{
        @Test
        public void onlyTheSupportedCompanionLifecycleRemains() throws Exception
        {
                Path root = Paths.get("").toAbsolutePath();
                Assert.assertFalse(Files.exists(root.resolve("3 - Background Learning.bat")));
                Assert.assertFalse(Files.exists(root.resolve("tools/install-daemon.ps1")));
                Assert.assertFalse(Files.exists(root.resolve("tools/uninstall-daemon.ps1")));
                Assert.assertTrue(Files.exists(root.resolve("tools/install-companion.ps1")));
                Assert.assertTrue(Files.exists(root.resolve("tools/uninstall-companion.ps1")));
                String update = read(root.resolve("tools/apply-update.ps1"));
                String pipeline = read(root.resolve("tools/pipeline-check.ps1"));
                Assert.assertFalse(update.contains("FlippingFriendLearning"));
                Assert.assertFalse(update.contains("flipping-friend-daemon"));
                Assert.assertFalse(update.contains("KeepDaemon"));
                Assert.assertFalse(pipeline.contains("flipping-friend-daemon"));
                Assert.assertTrue(update.contains("FlippingFriendCompanion"));
                Assert.assertTrue(pipeline.contains("127.0.0.1:37777"));
        }

        private static String read(Path path) throws Exception
        {
                return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
        }
}
