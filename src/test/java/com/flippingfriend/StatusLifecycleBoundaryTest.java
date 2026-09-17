package com.flippingfriend;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.Assert;
import org.junit.Test;

public class StatusLifecycleBoundaryTest
{
        @Test
        public void statusCommandIsReadOnlyAndChecksTheSupportedLifecycle() throws Exception
        {
                Path root = Paths.get("").toAbsolutePath();
                Assert.assertTrue(Files.exists(root.resolve("3 - Status.bat")));
                Path script = root.resolve("tools/status.ps1");
                Assert.assertTrue(Files.exists(script));
                String status = new String(Files.readAllBytes(script), StandardCharsets.UTF_8);
                Assert.assertTrue(status.contains("FlippingFriendCompanion"));
                Assert.assertTrue(status.contains("companion.properties"));
                Assert.assertTrue(status.contains("127.0.0.1:37777/v1/health"));
                Assert.assertTrue(status.contains("X-Flipping-Friend-Token"));
                Assert.assertTrue(status.contains("READY"));
                Assert.assertTrue(status.contains("DEGRADED"));
                Assert.assertTrue(status.contains("SETUP INCOMPLETE"));
                Assert.assertFalse(status.contains("Start-ScheduledTask"));
                Assert.assertFalse(status.contains("Stop-ScheduledTask"));
                Assert.assertFalse(status.contains("Register-ScheduledTask"));
                Assert.assertFalse(status.contains("Unregister-ScheduledTask"));
                Assert.assertFalse(status.contains("Start-Process"));
                Assert.assertFalse(status.contains("Stop-Process"));
                Assert.assertFalse(status.contains("Remove-Item"));
                Assert.assertFalse(status.contains("Set-Content"));
        }
}
