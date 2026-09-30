package com.flippingfriend;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Assert;
import org.junit.Test;

public class BuyMaintenanceCancellationSelectionNonActivationBoundaryTest
{
    @Test public void pureSelectorExistsButProductionDoesNotConsumeIt() throws Exception
    {
        Path root = root();
        String selector = read(root, "src/main/java/com/flippingfriend/companion/BuyMaintenanceCancellationSelector.java");
        String plugin = read(root, "src/main/java/com/flippingfriend/FlippingFriendPlugin.java");
        String engine = read(root, "src/main/java/com/flippingfriend/model/SuggestionEngine.java");
        Assert.assertTrue(selector.contains("OfferEvent canonicalOpenBuy"));
        Assert.assertTrue(selector.contains("PresentedBuyMaintenanceDecision companion"));
        Assert.assertTrue(selector.contains("OfferLifecycleAction.CANCEL_BUY"));
        Assert.assertTrue(selector.contains("SuggestionType.MODIFY_BUY"));
        Assert.assertTrue(selector.contains("SuggestionType.CANCEL"));
        Assert.assertFalse(selector.contains("CompanionClient"));
        Assert.assertFalse(selector.contains("FlippingFriendPlugin"));
        Assert.assertFalse(selector.contains("runelite"));
        Assert.assertFalse(plugin.contains("BuyMaintenanceCancellationSelector"));
        Assert.assertFalse(plugin.contains("nextBuyMaintenancePresentation"));
        Assert.assertTrue(plugin.contains("engine.refresh(false)"));
        Assert.assertTrue(plugin.contains("companion.nextEntrySuggestion("));
        Assert.assertTrue(engine.contains("private Suggestion adjustSuggestion("));
        Assert.assertTrue(engine.contains("private Suggestion replaceRepricedBuy("));
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
