package com.flippingfriend;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;
import static org.junit.Assert.*;
public class BuyReplacementQuantityCandidateSelectorNonActivationBoundaryTest {
    @Test public void candidateSelectorRemainsOutsideRuntimeConsumers() throws Exception {
        Path root = Path.of("").toAbsolutePath();
        while (root != null && !Files.exists(root.resolve("settings.gradle"))) root = root.getParent();
        assertNotNull(root);
        String[] tokens = {"BuyReplacementQuantityCandidateSelectionOutcome",
                "BuyReplacementQuantityCandidateSelection", "BuyReplacementQuantityCandidateSelector"};
        for (String token : tokens) assertTrue(Files.isRegularFile(root.resolve(
                "companion/src/main/java/com/flippingfriend/companion/" + token + ".java")));
        for (String file : new String[] {
                "companion/src/main/java/com/flippingfriend/companion/CompanionService.java",
                "companion/src/main/java/com/flippingfriend/companion/CompanionMain.java",
                "companion/src/main/java/com/flippingfriend/companion/ApiServer.java",
                "companion/src/main/java/com/flippingfriend/companion/CandidateFactory.java",
                "companion/src/main/java/com/flippingfriend/companion/PortfolioPlanner.java",
                "companion/src/main/java/com/flippingfriend/companion/CompanionEntryPolicy.java",
                "src/main/java/com/flippingfriend/companion/CompanionClient.java",
                "src/main/java/com/flippingfriend/FlippingFriendPlugin.java",
                "src/main/java/com/flippingfriend/model/SuggestionEngine.java"}) {
            Path path = root.resolve(file);
            if (!Files.isRegularFile(path)) continue;
            String text = Files.readString(path);
            for (String token : tokens) assertFalse(file + " " + token, text.contains(token));
        }
    }
}
