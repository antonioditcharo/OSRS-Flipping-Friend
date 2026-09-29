package com.flippingfriend;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Assert;
import org.junit.Test;

public class EntryDecisionParityNonActivationBoundaryTest
{
    @Test public void parityEvidenceRemainsPureAndOutsideTheLivePluginPath() throws Exception
    {
        Path root = root();
        String plugin = read(root, "src/main/java/com/flippingfriend/FlippingFriendPlugin.java");
        String client = read(root, "src/main/java/com/flippingfriend/companion/CompanionClient.java");
        Assert.assertTrue(Files.isRegularFile(root.resolve(
            "src/main/java/com/flippingfriend/companion/EntryDecisionParity.java")));
        Assert.assertTrue(Files.isRegularFile(root.resolve(
            "src/main/java/com/flippingfriend/companion/EntryDecisionParityResult.java")));
        Assert.assertTrue(Files.isRegularFile(root.resolve(
            "src/main/java/com/flippingfriend/companion/EntryDecisionParityStatus.java")));
        Assert.assertFalse(plugin.contains("EntryDecisionParity"));
        Assert.assertFalse(plugin.contains("nextEntryDecisionPresentation("));
        Assert.assertFalse(plugin.contains("fetchEntryDecision("));
        Assert.assertTrue(plugin.contains("companion.nextBuySuggestion("));
        Assert.assertTrue(plugin.contains("engine.buyFallback()"));
        Assert.assertEquals("2F9371E88F6BFFBE3D48FE793B4B2A54AB1796123DE9F726AEAB92F8DD09BC84",
            sha256(client));
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
