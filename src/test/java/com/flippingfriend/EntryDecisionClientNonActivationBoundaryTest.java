package com.flippingfriend;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Assert;
import org.junit.Test;

public class EntryDecisionClientNonActivationBoundaryTest
{
    @Test public void clientCapabilityIsNotActivatedInPlugin() throws Exception
    {
        Path root = repositoryRoot();
        String client = read(root.resolve("src/main/java/com/flippingfriend/companion/CompanionClient.java"));
        String plugin = read(root.resolve("src/main/java/com/flippingfriend/FlippingFriendPlugin.java"));
        Assert.assertTrue(client.contains("fetchEntryDecision(String planId, int allocationRank)"));
        Assert.assertTrue(client.contains("policy/entry?planId="));
        Assert.assertTrue(client.contains("URLEncoder.encode"));
        Assert.assertFalse(plugin.contains("fetchEntryDecision("));
        Assert.assertFalse(plugin.contains("companion.nextBuySuggestion("));
        Assert.assertFalse(plugin.contains("engine.buyFallback()"));
    }

    private static String read(Path file) throws Exception
    {
        return Files.readString(file, StandardCharsets.UTF_8);
    }

    private static Path repositoryRoot()
    {
        Path current = Path.of("").toAbsolutePath();
        while (current != null && !Files.exists(current.resolve("settings.gradle")))
        {
            current = current.getParent();
        }
        if (current == null) throw new IllegalStateException("Repository root not found");
        return current;
    }
}
