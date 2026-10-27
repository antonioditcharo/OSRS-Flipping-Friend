package com.flippingfriend;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Assert;
import org.junit.Test;

public class EntryDecisionPresentationNonActivationBoundaryTest
{
    @Test public void presentationAdapterIsReachedOnlyThroughTheClientAuthority() throws Exception
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
        Assert.assertTrue(plugin.contains("companion.nextEntrySuggestion("));
        Assert.assertTrue(client.contains("EntryDecisionPresenter.present(decision, plan, explainer, requestedAccount)"));
        Assert.assertFalse(plugin.contains("companion.nextBuySuggestion("));
        Assert.assertFalse(plugin.contains("engine.buyFallback()"));
        // Reviewed additive sale-policy bindings and bounded prospective display metadata.
        // These capture settings/alternatives only; entry authority remains in CompanionClient.
        Assert.assertEquals("997B73472F0158BD4EDE4738C69D204D3F4BAADEDE2C1886DA6FB00DEC6C2E00",
            sha256(suggestion));
    }
    private static String read(Path root, String path) throws Exception
    {
        return Files.readString(root.resolve(path), StandardCharsets.UTF_8);
    }
    private static String sha256(String value) throws Exception
    {
        // Git may check text out as CRLF on Windows. Preserve the source boundary while
        // hashing the same content on both supported development platforms.
        value = value.replace("\r\n", "\n");
        byte[] digest = java.security.MessageDigest.getInstance("SHA-256")
            .digest(value.getBytes(StandardCharsets.UTF_8));
        StringBuilder result = new StringBuilder();
        for (byte part : digest) result.append(String.format("%02X", part));
        return result.toString();
    }
    @Test public void sourceBoundaryIgnoresCheckoutLineEndings() throws Exception
    {
        Assert.assertEquals(sha256("first\nsecond\n"), sha256("first\r\nsecond\r\n"));
        Assert.assertNotEquals(sha256("first\nsecond\n"), sha256("first\nchanged\n"));
    }
    private static Path root()
    {
        Path current = Path.of("").toAbsolutePath();
        while (current != null && !Files.exists(current.resolve("settings.gradle"))) current = current.getParent();
        return current;
    }
}
