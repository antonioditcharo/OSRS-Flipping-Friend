package com.flippingfriend.companion;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class CompanionActionRouteBoundaryTest
{
        @Test
        public void authenticatedActionRouteServesTheCompanionAction() throws Exception
        {
                Path root = repositoryRoot();
                String api = read(root.resolve(
                        "companion/src/main/java/com/flippingfriend/companion/ApiServer.java"));
                String service = read(root.resolve(
                        "companion/src/main/java/com/flippingfriend/companion/CompanionService.java"));
                String client = read(root.resolve(
                        "src/main/java/com/flippingfriend/companion/CompanionClient.java"));
                String plugin = read(root.resolve(
                        "src/main/java/com/flippingfriend/FlippingFriendPlugin.java"));

                assertTrue(api.contains(
                        "server.createContext(\"/v1/action\", this::action)"));
                assertTrue(api.contains(
                        "private void action(HttpExchange exchange) throws IOException"));
                assertTrue(api.contains(
                        "if (!authorized(exchange)) return;"));
                assertTrue(api.contains(
                        "respond(exchange, 200, gson.toJson(service.action()))"));

                assertTrue(service.contains(
                        "CompanionAction action()"));
                assertTrue(service.contains(
                        "actionSelector.select(activeOffers.getActiveOffers())"));

                assertFalse(client.contains(
                        "get(\"action\", CompanionAction.class)"));
                assertTrue(plugin.contains(
                        "engine.refresh(false)"));
                assertTrue(plugin.contains(
                        "engine.buyFallback()"));
        }

        private static Path repositoryRoot()
        {
                Path current = Path.of("").toAbsolutePath();
                while (current != null)
                {
                        if (Files.exists(current.resolve("settings.gradle")))
                        {
                                return current;
                        }
                        current = current.getParent();
                }
                throw new IllegalStateException("Repository root not found");
        }

        private static String read(Path path) throws Exception
        {
                return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
        }
}
