package com.flippingfriend;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.Assert;
import org.junit.Test;

public class UpdateLifecycleBoundaryTest
{
        @Test
        public void updateCommandUsesTheGuardedSupportedLifecycle() throws Exception
        {
                Path root = Paths.get("").toAbsolutePath();
                Assert.assertTrue(Files.exists(root.resolve("4 - Update.bat")));
                Path script = root.resolve("tools/apply-update.ps1");
                Assert.assertTrue(Files.exists(script));
                String update = new String(Files.readAllBytes(script), StandardCharsets.UTF_8);
                Assert.assertTrue(update.contains("jar :companion:shadowJar"));
                Assert.assertTrue(update.contains("Wait-PortFree"));
                Assert.assertTrue(update.contains("Plugin jar not found"));
                Assert.assertTrue(update.contains("Companion jar not found"));
                Assert.assertTrue(update.contains("FlippingFriendCompanion"));
                Assert.assertTrue(update.contains("127.0.0.1:37777/v1/health"));
                Assert.assertTrue(update.contains("X-Flipping-Friend-Token"));
                Assert.assertTrue(update.contains("Update applied."));
                Assert.assertTrue(update.contains("restart it to pick up plugin changes"));
                Assert.assertFalse(update.contains("FlippingFriendLearning"));
                Assert.assertFalse(update.contains("flipping-friend-daemon"));
                Assert.assertFalse(update.contains("KeepDaemon"));
        }
}
