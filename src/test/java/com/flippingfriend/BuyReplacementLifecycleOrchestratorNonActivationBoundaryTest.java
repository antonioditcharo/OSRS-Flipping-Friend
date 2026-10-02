package com.flippingfriend;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class BuyReplacementLifecycleOrchestratorNonActivationBoundaryTest
{
    @Test public void lifecycleOrchestratorRemainsOutsideRuntimeAuthority() throws Exception
    {
        Path root = root();
        String name = "BuyReplacementLifecycleOrchestrator";
        assertTrue(Files.isRegularFile(root.resolve(
            "companion/src/main/java/com/flippingfriend/companion/" + name + ".java")));
        for (String file : new String[]{
            "companion/src/main/java/com/flippingfriend/companion/CompanionService.java",
            "companion/src/main/java/com/flippingfriend/companion/CompanionMain.java",
            "companion/src/main/java/com/flippingfriend/companion/ApiServer.java",
            "src/main/java/com/flippingfriend/companion/CompanionClient.java",
            "src/main/java/com/flippingfriend/FlippingFriendPlugin.java",
            "src/main/java/com/flippingfriend/model/SuggestionEngine.java"})
            assertFalse(Files.readString(root.resolve(file), StandardCharsets.UTF_8).contains(name));
    }

    private static Path root()
    {
        Path current = Path.of("").toAbsolutePath();
        while (current != null && !Files.exists(current.resolve("settings.gradle")))
            current = current.getParent();
        return current;
    }
}
