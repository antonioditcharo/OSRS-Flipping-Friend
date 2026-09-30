package com.flippingfriend;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Assert;
import org.junit.Test;

public class BuyMaintenancePresentationNonActivationBoundaryTest
{
    @Test public void purePresenterIsNotActivatedInPluginOrClientOrchestration() throws Exception
    {
        Path root = root();
        String plugin = read(root, "src/main/java/com/flippingfriend/FlippingFriendPlugin.java");
        String client = read(root, "src/main/java/com/flippingfriend/companion/CompanionClient.java");
        Assert.assertFalse(plugin.contains("BuyMaintenanceDecisionPresenter"));
        Assert.assertFalse(plugin.contains("PresentedBuyMaintenanceDecision"));
        Assert.assertFalse(client.contains("BuyMaintenanceDecisionPresenter.present("));
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
