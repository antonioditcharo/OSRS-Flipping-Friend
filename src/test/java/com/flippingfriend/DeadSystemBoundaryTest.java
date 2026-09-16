package com.flippingfriend;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.Assert;
import org.junit.Test;

public class DeadSystemBoundaryTest
{
        @Test
        public void deadEnginesAndSettingsStayRemoved() throws Exception
        {
                Path root = Paths.get("").toAbsolutePath();
                Assert.assertFalse(Files.exists(root.resolve("core/src/main/java/com/flippingfriend/model/ArbitrageEngine.java")));
                Assert.assertFalse(Files.exists(root.resolve("src/main/java/com/flippingfriend/data/JagexPriceClient.java")));
                String config = new String(Files.readAllBytes(root.resolve("src/main/java/com/flippingfriend/FlippingFriendConfig.java")), java.nio.charset.StandardCharsets.UTF_8);
                Assert.assertFalse(config.contains("shadowTrading"));
                Assert.assertTrue(Files.exists(root.resolve("src/main/java/com/flippingfriend/model/arbitrage/ArbitrageRegistry.java")));
                Assert.assertTrue(Files.exists(root.resolve("companion/src/main/java/com/flippingfriend/companion/Backtester.java")));
        }
}
