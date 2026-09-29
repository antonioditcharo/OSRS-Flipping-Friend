package com.flippingfriend;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.Assert;
import org.junit.Test;

public class CompanionCollectPriorityBoundaryTest
{
    @Test public void companionCollectAndLocalMaintenancePrecedeAuthoritativeEntry() throws Exception
    {
        Path root = Paths.get("").toAbsolutePath();
        String plugin = read(root.resolve("src/main/java/com/flippingfriend/FlippingFriendPlugin.java"));
        String client = read(root.resolve("src/main/java/com/flippingfriend/companion/CompanionClient.java"));
        int companionAction = plugin.indexOf("Suggestion suggestion = companion.nextAction()");
        int builtInRecovery = plugin.indexOf("suggestion = engine.refresh(false)");
        int companionEntry = plugin.indexOf("companion.nextEntrySuggestion(");
        Assert.assertTrue(companionAction >= 0);
        Assert.assertTrue(builtInRecovery > companionAction);
        Assert.assertTrue(companionEntry > builtInRecovery);
        Assert.assertFalse(plugin.contains("companion.nextBuySuggestion("));
        Assert.assertFalse(plugin.contains("engine.buyFallback()"));
        Assert.assertTrue(plugin.contains("if (suggestion == null)"));
        Assert.assertTrue(client.contains("get(\"action\", CompanionAction.class)"));
        Assert.assertTrue(client.contains("action.getType() != CompanionActionType.COLLECT"));
        Assert.assertTrue(client.contains("Suggestion.builder(SuggestionType.COLLECT)"));
    }
    private static String read(Path path) throws Exception
    {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }
}
