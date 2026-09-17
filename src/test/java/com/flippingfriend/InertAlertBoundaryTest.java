package com.flippingfriend;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.Assert;
import org.junit.Test;

public class InertAlertBoundaryTest
{
        @Test
        public void unusedMarketFluxAlertSubsystemRemainsAbsent() throws Exception
        {
                Path root = Paths.get("").toAbsolutePath();
                Assert.assertFalse(Files.exists(root.resolve("core/src/main/java/com/flippingfriend/model/MarketFluxIndex.java")));
                Assert.assertFalse(Files.exists(root.resolve("src/main/java/com/flippingfriend/ui/AlertManager.java")));
                Assert.assertFalse(Files.exists(root.resolve("src/main/java/com/flippingfriend/ui/AlertOverlay.java")));

                String plugin = new String(Files.readAllBytes(
                        root.resolve("src/main/java/com/flippingfriend/FlippingFriendPlugin.java")),
                        StandardCharsets.UTF_8);
                Assert.assertFalse(plugin.contains("AlertManager"));
                Assert.assertFalse(plugin.contains("AlertOverlay"));
                Assert.assertFalse(plugin.contains("alertManager"));
                Assert.assertFalse(plugin.contains("alertOverlay"));
        }
}
