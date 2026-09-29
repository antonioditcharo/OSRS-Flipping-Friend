package com.flippingfriend;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Assert;
import org.junit.Test;

public class EntryDecisionOrchestrationNonActivationBoundaryTest
{
    @Test public void orchestrationRemainsOutsideTheLivePluginPath() throws Exception
    {
        Path root = root();
        String client = Files.readString(root.resolve(
            "src/main/java/com/flippingfriend/companion/CompanionClient.java"), StandardCharsets.UTF_8);
        String plugin = Files.readString(root.resolve(
            "src/main/java/com/flippingfriend/FlippingFriendPlugin.java"), StandardCharsets.UTF_8);
        Assert.assertTrue(client.contains("nextEntryDecisionPresentation(Explainer explainer"));
        Assert.assertTrue(client.contains("this::fetchEntryDecision"));
        Assert.assertTrue(client.contains("EntryDecisionPresenter.present(decision, plan, explainer)"));
        Assert.assertFalse(plugin.contains("nextEntryDecisionPresentation("));
        Assert.assertFalse(plugin.contains("fetchEntryDecision("));
        Assert.assertFalse(plugin.contains("companion.nextBuySuggestion("));
        Assert.assertFalse(plugin.contains("engine.buyFallback()"));
    }

    private static Path root()
    {
        Path current = Path.of("").toAbsolutePath();
        while (current != null && !Files.exists(current.resolve("settings.gradle")))
            current = current.getParent();
        return current;
    }
}
