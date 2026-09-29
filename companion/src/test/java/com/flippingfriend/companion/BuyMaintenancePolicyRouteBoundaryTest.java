package com.flippingfriend.companion;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;
import static org.junit.Assert.assertTrue;

public class BuyMaintenancePolicyRouteBoundaryTest
{
    @Test public void routeIsAuthenticatedTypedAndDelegatesOnlyToService() throws Exception
    {
        Path root = root();
        String api = Files.readString(root.resolve(
            "companion/src/main/java/com/flippingfriend/companion/ApiServer.java"), StandardCharsets.UTF_8);
        assertTrue(api.contains("server.createContext(\"/v1/policy/buy-maintenance\", this::buyMaintenanceDecision)"));
        assertTrue(api.contains("private void buyMaintenanceDecision(HttpExchange exchange)"));
        assertTrue(api.contains("if (!authorized(exchange)) return;"));
        assertTrue(api.contains("gson.toJson(service.buyMaintenanceDecision("));
        assertTrue(api.contains("java.time.Instant.now().getEpochSecond()"));
    }
    private static Path root()
    {
        Path current = Path.of("").toAbsolutePath();
        while (current != null && !Files.exists(current.resolve("settings.gradle"))) current = current.getParent();
        return current;
    }
}
