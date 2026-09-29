package com.flippingfriend;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Assert;
import org.junit.Test;

public class BuyMaintenanceServiceIntegrationNonActivationBoundaryTest
{
    @Test public void serviceIntegrationIsInternalAndNotRoutedOrRetrieved() throws Exception
    {
        Path root = root();
        String service = read(root, "companion/src/main/java/com/flippingfriend/companion/CompanionService.java");
        String api = read(root, "companion/src/main/java/com/flippingfriend/companion/ApiServer.java");
        String client = read(root, "src/main/java/com/flippingfriend/companion/CompanionClient.java");
        String plugin = read(root, "src/main/java/com/flippingfriend/FlippingFriendPlugin.java");
        Assert.assertTrue(service.contains("PolicyDecision buyMaintenanceDecision(long decidedAt)"));
        Assert.assertTrue(service.contains("new BuyMaintenanceServiceAdapter().decide("));
        Assert.assertFalse(api.contains("policy/buy-maintenance"));
        Assert.assertFalse(client.contains("fetchBuyMaintenanceDecision"));
        Assert.assertFalse(plugin.contains("buyMaintenanceDecision"));
        Assert.assertTrue(plugin.contains("engine.refresh(false)"));
    }
    private static String read(Path root, String file) throws Exception
    {
        return Files.readString(root.resolve(file), StandardCharsets.UTF_8);
    }
    private static Path root()
    {
        Path current = Path.of("").toAbsolutePath();
        while (current != null && !Files.exists(current.resolve("settings.gradle"))) current = current.getParent();
        return current;
    }
}
