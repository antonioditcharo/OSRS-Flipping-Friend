package com.flippingfriend;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Assert;
import org.junit.Test;

public class EntryDecisionNonActivationBoundaryTest
{
    @Test public void validatedEntryPathIsActiveAndLegacyEntryPathsAreRetired() throws Exception
    {
        Path root = root();
        String plugin = Files.readString(root.resolve(
            "src/main/java/com/flippingfriend/FlippingFriendPlugin.java"), StandardCharsets.UTF_8);
        Assert.assertTrue(plugin.contains("companion.nextEntrySuggestion("));
        Assert.assertFalse(plugin.contains("companion.nextBuySuggestion("));
        Assert.assertFalse(plugin.contains("engine.buyFallback()"));
        Assert.assertFalse(plugin.contains("fetchEntryDecision("));
    }
    private static Path root()
    {
        Path path = Path.of("").toAbsolutePath();
        while (path != null && !Files.exists(path.resolve("settings.gradle"))) path = path.getParent();
        return path;
    }
}
