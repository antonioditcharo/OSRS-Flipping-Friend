package com.flippingfriend;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Assert;
import org.junit.Test;

public class EntryDecisionParityNonActivationBoundaryTest
{
    @Test public void parityEvidenceRemainsPureWhileEntryAuthorityIsActive() throws Exception
    {
        Path root = root();
        String plugin = read(root, "src/main/java/com/flippingfriend/FlippingFriendPlugin.java");
        String parity = read(root, "src/main/java/com/flippingfriend/companion/EntryDecisionParity.java");
        Assert.assertTrue(Files.isRegularFile(root.resolve(
            "src/main/java/com/flippingfriend/companion/EntryDecisionParityResult.java")));
        Assert.assertTrue(Files.isRegularFile(root.resolve(
            "src/main/java/com/flippingfriend/companion/EntryDecisionParityStatus.java")));
        Assert.assertTrue(parity.contains("Pure, side-effect-free parity comparison"));
        Assert.assertTrue(parity.contains("static EntryDecisionParityResult compare("));
        Assert.assertFalse(plugin.contains("EntryDecisionParity"));
        Assert.assertFalse(plugin.contains("nextEntryDecisionPresentation("));
        Assert.assertFalse(plugin.contains("fetchEntryDecision("));
        Assert.assertTrue(plugin.contains("companion.nextEntrySuggestion("));
        Assert.assertFalse(plugin.contains("companion.nextBuySuggestion("));
        Assert.assertFalse(plugin.contains("engine.buyFallback()"));
    }
    private static String read(Path root, String path) throws Exception
    {
        return Files.readString(root.resolve(path), StandardCharsets.UTF_8);
    }
    private static Path root()
    {
        Path current = Path.of("").toAbsolutePath();
        while (current != null && !Files.exists(current.resolve("settings.gradle"))) current = current.getParent();
        return current;
    }
}
