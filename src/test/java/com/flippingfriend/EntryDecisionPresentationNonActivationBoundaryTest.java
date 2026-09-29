package com.flippingfriend;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Assert;
import org.junit.Test;

public class EntryDecisionPresentationNonActivationBoundaryTest
{
    @Test public void presentationAdapterRemainsOutsideTheLiveRecommendationPath() throws Exception
    {
        Path root = root();
        String plugin = read(root, "src/main/java/com/flippingfriend/FlippingFriendPlugin.java");
        String client = read(root, "src/main/java/com/flippingfriend/companion/CompanionClient.java");
        String suggestion = read(root, "src/main/java/com/flippingfriend/model/Suggestion.java");
        Assert.assertTrue(Files.isRegularFile(root.resolve(
            "src/main/java/com/flippingfriend/companion/EntryDecisionPresenter.java")));
        Assert.assertTrue(Files.isRegularFile(root.resolve(
            "src/main/java/com/flippingfriend/companion/PresentedEntryDecision.java")));
        Assert.assertFalse(plugin.contains("EntryDecisionPresenter"));
        Assert.assertFalse(plugin.contains("PresentedEntryDecision"));
        Assert.assertFalse(plugin.contains("fetchEntryDecision("));
        Assert.assertTrue(plugin.contains("companion.nextBuySuggestion("));
        Assert.assertTrue(plugin.contains("engine.buyFallback()"));
        Assert.assertEquals("61F842B3277DACA277329E0D50585AC1CA1C8C72E3C197A452D96B225C9365FB",
            sha256(client));
        Assert.assertEquals("2488CA51432868AC9623541996CDF4B13B2DCA9E9775DC72592DA1CDCE501BA0",
            sha256(suggestion));
    }

    private static String read(Path root, String path) throws Exception
    {
        return Files.readString(root.resolve(path), StandardCharsets.UTF_8);
    }

    private static String sha256(String value) throws Exception
    {
        byte[] digest = java.security.MessageDigest.getInstance("SHA-256")
            .digest(value.getBytes(StandardCharsets.UTF_8));
        StringBuilder result = new StringBuilder();
        for (byte part : digest) result.append(String.format("%02X", part));
        return result.toString();
    }

    private static Path root()
    {
        Path current = Path.of("").toAbsolutePath();
        while (current != null && !Files.exists(current.resolve("settings.gradle")))
            current = current.getParent();
        return current;
    }
}
