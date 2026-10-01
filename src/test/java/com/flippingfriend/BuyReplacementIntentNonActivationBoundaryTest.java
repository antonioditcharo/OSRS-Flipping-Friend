package com.flippingfriend;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Assert;
import org.junit.Test;

public class BuyReplacementIntentNonActivationBoundaryTest
{
    @Test public void pluginClientAndLocalReplacementRemainUnchanged() throws Exception
    {
        Path root = root();
        String plugin = read(root, "src/main/java/com/flippingfriend/FlippingFriendPlugin.java");
        String client = read(root, "src/main/java/com/flippingfriend/companion/CompanionClient.java");
        String engine = read(root, "src/main/java/com/flippingfriend/model/SuggestionEngine.java");
        Assert.assertFalse(plugin.contains("BuyReplacementIntent"));
        Assert.assertFalse(client.contains("BuyReplacementIntent"));
        Assert.assertTrue(engine.contains("private volatile Reprice reprice"));
        Assert.assertTrue(engine.contains("private Suggestion replaceRepricedBuy("));
        Assert.assertTrue(plugin.contains("suggestion = companion.selectBuyMaintenanceCancellation(suggestion)"));
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
