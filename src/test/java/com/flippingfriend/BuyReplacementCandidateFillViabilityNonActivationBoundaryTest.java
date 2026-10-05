package com.flippingfriend;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;
import static org.junit.Assert.*;

public class BuyReplacementCandidateFillViabilityNonActivationBoundaryTest
{
    @Test public void fillViabilityRemainsOutsideRuntimeAuthority() throws Exception
    {
        Path root = Path.of("").toAbsolutePath();
        while (!Files.exists(root.resolve("settings.gradle"))) root = root.getParent();
        String[] names = {"BuyReplacementCandidateFillViabilityOutcome",
            "BuyReplacementFillViableCandidate", "BuyReplacementCandidateFillViabilityAssessment",
            "BuyReplacementCandidateFillViabilityEvaluator"};
        for (String name : names) assertTrue(Files.isRegularFile(root.resolve(
            "companion/src/main/java/com/flippingfriend/companion/" + name + ".java")));
        for (String file : new String[]{
            "companion/src/main/java/com/flippingfriend/companion/CompanionService.java",
            "companion/src/main/java/com/flippingfriend/companion/ApiServer.java",
            "src/main/java/com/flippingfriend/companion/CompanionClient.java",
            "src/main/java/com/flippingfriend/FlippingFriendPlugin.java",
            "src/main/java/com/flippingfriend/model/SuggestionEngine.java"})
            for (String name : names)
                assertFalse(Files.readString(root.resolve(file)).contains(name));
    }
}
