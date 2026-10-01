package com.flippingfriend;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Assert;
import org.junit.Test;

public class BuyMaintenanceCancellationCompositionNonActivationBoundaryTest
{
    @Test public void pluginActivatesOnlyEligibleLocalBuyMaintenanceAfterRecovery() throws Exception
    {
        Path root = root();
        String client = read(root, "src/main/java/com/flippingfriend/companion/CompanionClient.java");
        String plugin = read(root, "src/main/java/com/flippingfriend/FlippingFriendPlugin.java");
        Assert.assertTrue(client.contains("public Suggestion selectBuyMaintenanceCancellation(Suggestion existing)"));
        Assert.assertTrue(client.contains("currentCanonicalOpenBuy()"));
        Assert.assertTrue(client.contains("nextBuyMaintenancePresentation(canonicalOpenBuy, fetcher)"));
        Assert.assertTrue(client.contains("BuyMaintenanceCancellationSelector.select(existing, canonicalOpenBuy, companion)"));
        Assert.assertFalse(plugin.contains("BuyMaintenanceCancellationSelector"));
        Assert.assertFalse(plugin.contains("nextBuyMaintenancePresentation"));
        Assert.assertTrue(plugin.contains("suggestion.getType() == SuggestionType.MODIFY_BUY"));
        Assert.assertTrue(plugin.contains("suggestion.getType() == SuggestionType.CANCEL"));
        int collect = plugin.indexOf("Suggestion suggestion = companion.nextAction()");
        int recovery = plugin.indexOf("suggestion = engine.refresh(false)");
        int maintenance = plugin.indexOf("suggestion = companion.selectBuyMaintenanceCancellation(suggestion)");
        int entry = plugin.indexOf("companion.nextEntrySuggestion(");
        Assert.assertTrue(collect >= 0);
        Assert.assertTrue(recovery > collect);
        Assert.assertTrue(maintenance > recovery);
        Assert.assertTrue(entry > maintenance);
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
