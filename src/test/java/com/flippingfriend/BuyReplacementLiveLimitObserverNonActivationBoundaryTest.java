package com.flippingfriend;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;
import static org.junit.Assert.*;
public class BuyReplacementLiveLimitObserverNonActivationBoundaryTest {
    @Test public void liveLimitObserverRemainsOutsideRuntimeConsumers() throws Exception {
        Path root = Path.of("").toAbsolutePath();
        while (root != null && !Files.exists(root.resolve("settings.gradle"))) root = root.getParent();
        assertNotNull(root);
        String[] tokens = {"BuyReplacementLiveLimitObservation", "BuyReplacementLiveLimitObserver", "remainingAt"};
        assertTrue(Files.isRegularFile(root.resolve(
                "companion/src/main/java/com/flippingfriend/companion/BuyReplacementLiveLimitObserver.java")));
        for (String file : new String[] {
                "companion/src/main/java/com/flippingfriend/companion/CompanionService.java",
                "companion/src/main/java/com/flippingfriend/companion/CompanionMain.java",
                "companion/src/main/java/com/flippingfriend/companion/ApiServer.java",
                "src/main/java/com/flippingfriend/companion/CompanionClient.java",
                "src/main/java/com/flippingfriend/FlippingFriendPlugin.java",
                "src/main/java/com/flippingfriend/model/SuggestionEngine.java"}) {
            String text = Files.readString(root.resolve(file));
            for (String token : tokens) assertFalse(file + " " + token, text.contains(token));
        }
    }
}
