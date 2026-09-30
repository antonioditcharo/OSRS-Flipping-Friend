package com.flippingfriend;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Assert;
import org.junit.Test;

public class BuyMaintenanceParityNonActivationBoundaryTest
{
    @Test public void clientCanComposeParityEvidenceButPluginDoesNotConsumeIt() throws Exception
    {
        Path root = root();
        String plugin = read(root, "src/main/java/com/flippingfriend/FlippingFriendPlugin.java");
        String client = read(root, "src/main/java/com/flippingfriend/companion/CompanionClient.java");
        String parity = read(root, "src/main/java/com/flippingfriend/companion/BuyMaintenanceParity.java");
        Assert.assertTrue(parity.contains("static BuyMaintenanceParityResult compare("));
        Assert.assertTrue(client.contains("compareBuyMaintenancePresentation(Suggestion existing,"));
        Assert.assertTrue(client.contains("BuyMaintenanceParity.compare(existing, shadow)"));
        Assert.assertFalse(plugin.contains("BuyMaintenanceParity"));
        Assert.assertFalse(plugin.contains("compareBuyMaintenancePresentation"));
        Assert.assertFalse(plugin.contains("nextBuyMaintenancePresentation"));
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
        while (current != null && !Files.exists(current.resolve("settings.gradle")))
            current = current.getParent();
        return current;
    }
}
