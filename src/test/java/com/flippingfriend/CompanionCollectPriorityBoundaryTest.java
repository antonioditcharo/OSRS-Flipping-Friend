package com.flippingfriend;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.Assert;
import org.junit.Test;

public class CompanionCollectPriorityBoundaryTest
{
        @Test
        public void companionCollectPrecedesBuiltInRecoveryWithoutRemovingFallbacks() throws Exception
        {
                Path root = Paths.get("").toAbsolutePath();
                String plugin = read(root.resolve(
                        "src/main/java/com/flippingfriend/FlippingFriendPlugin.java"));
                String client = read(root.resolve(
                        "src/main/java/com/flippingfriend/companion/CompanionClient.java"));

                int companionAction = plugin.indexOf(
                        "Suggestion suggestion = companion.nextAction()");
                int builtInRecovery = plugin.indexOf(
                        "suggestion = engine.refresh(false)");
                int companionBuy = plugin.indexOf(
                        "Suggestion planned = companion.nextBuySuggestion(");
                int fallbackBuy = plugin.indexOf(
                        "suggestion = engine.buyFallback()");

                Assert.assertTrue(companionAction >= 0);
                Assert.assertTrue(builtInRecovery > companionAction);
                Assert.assertTrue(companionBuy > builtInRecovery);
                Assert.assertTrue(fallbackBuy > companionBuy);

                Assert.assertTrue(plugin.contains(
                        "if (suggestion == null)"));
                Assert.assertTrue(client.contains(
                        "get(\"action\", CompanionAction.class)"));
                Assert.assertTrue(client.contains(
                        "catch (Exception ex)"));
                Assert.assertTrue(client.contains(
                        "return null;"));

                Assert.assertTrue(client.contains(
                        "action.getType() != CompanionActionType.COLLECT"));
                Assert.assertTrue(client.contains(
                        "Suggestion.builder(SuggestionType.COLLECT)"));
                Assert.assertTrue(client.contains(
                        "action.getItemId() <= 0"));
                Assert.assertTrue(client.contains(
                        "action.getSlot() < 0"));
        }

        private static String read(Path path) throws Exception
        {
                return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
        }
}
