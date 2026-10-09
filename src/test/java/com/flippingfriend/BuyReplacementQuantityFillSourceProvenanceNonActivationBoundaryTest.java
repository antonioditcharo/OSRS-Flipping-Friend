package com.flippingfriend;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;
import static org.junit.Assert.*;

public class BuyReplacementQuantityFillSourceProvenanceNonActivationBoundaryTest {
    @Test public void retainedSourceHasNoRuntimeConsumer() throws Exception {
        Path root = Path.of("").toAbsolutePath();
        while (root != null && !Files.exists(root.resolve("settings.gradle"))) root = root.getParent();
        assertNotNull(root);
        String context = Files.readString(root.resolve(
                "companion/src/main/java/com/flippingfriend/companion/BuyReplacementQuantityFillInputContext.java"));
        assertTrue(context.contains("getCandidateFillInputContext()"));
        for (String file : new String[] {
                "companion/src/main/java/com/flippingfriend/companion/CompanionService.java",
                "companion/src/main/java/com/flippingfriend/companion/CompanionMain.java",
                "companion/src/main/java/com/flippingfriend/companion/ApiServer.java",
                "src/main/java/com/flippingfriend/companion/CompanionClient.java",
                "src/main/java/com/flippingfriend/FlippingFriendPlugin.java",
                "src/main/java/com/flippingfriend/model/SuggestionEngine.java"}) {
            String text = Files.readString(root.resolve(file));
            assertFalse(file, text.contains("getCandidateFillInputContext("));
            assertFalse(file, text.contains("BuyReplacementQuantityFillInputContext"));
        }
    }
}
