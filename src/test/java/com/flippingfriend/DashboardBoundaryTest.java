package com.flippingfriend;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.Assert;
import org.junit.Test;

public class DashboardBoundaryTest
{
        @Test
        public void unsupportedDashboardCannotStart()
        {
                Path root = Paths.get("").toAbsolutePath();
                Assert.assertFalse(Files.exists(root.resolve("4 - Learning Monitor.bat")));
                Assert.assertFalse(Files.exists(root.resolve("tools/monitor.ps1")));
                Assert.assertFalse(Files.exists(root.resolve("dashboard-api")));
                Assert.assertFalse(Files.exists(root.resolve("dashboard-desktop")));
                Assert.assertFalse(Files.exists(root.resolve("dashboard-ui")));
                Assert.assertFalse(Files.exists(root.resolve("dashboard.html")));
        }
}
