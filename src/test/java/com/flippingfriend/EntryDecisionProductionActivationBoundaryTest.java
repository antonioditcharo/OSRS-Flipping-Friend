package com.flippingfriend;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Assert;
import org.junit.Test;

public class EntryDecisionProductionActivationBoundaryTest
{
    @Test public void entryPolicyIsLiveAndIndependentPluginRankingIsNotReachable() throws Exception
    {
        Path root = root();
        String plugin = Files.readString(root.resolve(
            "src/main/java/com/flippingfriend/FlippingFriendPlugin.java"), StandardCharsets.UTF_8);
        Assert.assertTrue(plugin.contains("engine.refresh(false)"));
        Assert.assertTrue(plugin.contains("companion.nextEntrySuggestion("));
        Assert.assertFalse(plugin.contains("companion.nextBuySuggestion("));
        Assert.assertFalse(plugin.contains("engine.buyFallback()"));
    }

    @Test public void productionAdapterFailsClosedWhenNoValidatedPresentationExists() throws Exception
    {
        Path root = root();
        String client = Files.readString(root.resolve(
            "src/main/java/com/flippingfriend/companion/CompanionClient.java"), StandardCharsets.UTF_8);
        Assert.assertTrue(client.contains("public Suggestion nextEntrySuggestion("));
        Assert.assertTrue(client.contains("PresentedEntryDecision presented = nextEntryDecisionPresentation("));
        Assert.assertTrue(client.contains("Companion entry decision unavailable"));
        int productionStart = client.indexOf("public Suggestion nextEntrySuggestion(");
        int productionEnd = client.indexOf(
            "PresentedEntryDecision nextEntryDecisionPresentation(Explainer explainer",
            productionStart);
        Assert.assertTrue(productionStart >= 0);
        Assert.assertTrue(productionEnd > productionStart);
        Assert.assertFalse(client.substring(productionStart, productionEnd)
            .contains("buyFallback"));
    }

    private static Path root()
    {
        Path current = Path.of("").toAbsolutePath();
        while (current != null && !Files.exists(current.resolve("settings.gradle")))
            current = current.getParent();
        if (current == null) throw new IllegalStateException("Repository root not found");
        return current;
    }
}
