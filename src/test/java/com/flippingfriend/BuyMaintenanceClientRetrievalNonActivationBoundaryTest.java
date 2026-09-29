package com.flippingfriend;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Assert;
import org.junit.Test;

public class BuyMaintenanceClientRetrievalNonActivationBoundaryTest
{
    @Test public void clientRetrievalExistsButPluginDoesNotConsumeIt() throws Exception
    {
        Path root = root();
        String client = read(root, "src/main/java/com/flippingfriend/companion/CompanionClient.java");
        String plugin = read(root, "src/main/java/com/flippingfriend/FlippingFriendPlugin.java");
        Assert.assertTrue(client.contains("fetchBuyMaintenanceDecision(String offerIdentity, String recommendationId)"));
        Assert.assertTrue(client.contains("get(\"policy/buy-maintenance\", PolicyDecision.class)"));
        Assert.assertTrue(client.contains("validBuyMaintenanceDecision(decision, offerIdentity, recommendationId)"));
        Assert.assertFalse(plugin.contains("fetchBuyMaintenanceDecision"));
        Assert.assertFalse(plugin.contains("validBuyMaintenanceDecision"));
        Assert.assertTrue(plugin.contains("engine.refresh(false)"));
        Assert.assertTrue(plugin.contains("companion.nextEntrySuggestion("));
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
