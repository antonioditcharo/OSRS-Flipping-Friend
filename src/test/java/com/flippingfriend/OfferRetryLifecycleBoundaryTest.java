package com.flippingfriend;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Assert;
import org.junit.Test;

public class OfferRetryLifecycleBoundaryTest
{
        @Test public void retryUsesExistingWorkerAndLifecycle() throws Exception
        {
                Path root = Path.of("").toAbsolutePath();
                String plugin = Files.readString(root.resolve("src/main/java/com/flippingfriend/FlippingFriendPlugin.java"), StandardCharsets.UTF_8);
                String client = Files.readString(root.resolve("src/main/java/com/flippingfriend/companion/CompanionClient.java"), StandardCharsets.UTF_8);
                Assert.assertTrue(plugin.contains("companion.requestPendingOfferReplay(executor)"));
                Assert.assertTrue(plugin.contains("companion.resumeOfferReplay()"));
                Assert.assertTrue(plugin.contains("companion.pauseOfferReplay()"));
                Assert.assertTrue(client.contains("replayGeneration.incrementAndGet()"));
                Assert.assertTrue(client.contains("generation == replayGeneration.get()"));
                Assert.assertFalse(client.contains("ScheduledExecutorService"));
                Assert.assertFalse(client.contains("newScheduledThreadPool"));
                Assert.assertFalse(client.contains("scheduleAtFixedRate"));
                Assert.assertFalse(client.contains("scheduleWithFixedDelay"));
        }
}
