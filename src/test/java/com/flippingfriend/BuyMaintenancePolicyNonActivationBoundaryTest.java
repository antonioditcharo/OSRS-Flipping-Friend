package com.flippingfriend;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Assert;
import org.junit.Test;

public class BuyMaintenancePolicyNonActivationBoundaryTest
{
    @Test public void foundationDoesNotChangeLiveMaintenanceOrEntry() throws Exception
    {
        Path root = root();
        String plugin = Files.readString(root.resolve(
            "src/main/java/com/flippingfriend/FlippingFriendPlugin.java"), StandardCharsets.UTF_8);
        String engine = Files.readString(root.resolve(
            "src/main/java/com/flippingfriend/model/SuggestionEngine.java"), StandardCharsets.UTF_8);
        Assert.assertTrue(plugin.contains("engine.refresh(false)"));
        Assert.assertTrue(plugin.contains("companion.nextEntrySuggestion("));
        Assert.assertFalse(plugin.contains("CompanionBuyMaintenancePolicy"));
        Assert.assertTrue(engine.contains("private Suggestion adjustSuggestion("));
        Assert.assertTrue(engine.contains("private Suggestion replaceRepricedBuy("));
    }
    private static Path root()
    {
        Path current = Path.of("").toAbsolutePath();
        while (current != null && !Files.exists(current.resolve("settings.gradle"))) current = current.getParent();
        return current;
    }
}
