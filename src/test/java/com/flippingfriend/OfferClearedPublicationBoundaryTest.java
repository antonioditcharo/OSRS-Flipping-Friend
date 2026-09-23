package com.flippingfriend;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.Assert;
import org.junit.Test;

public class OfferClearedPublicationBoundaryTest
{
        @Test
        public void emptyOfferStateTravelsToTheCompanionBySlot() throws Exception
        {
                Path root = Paths.get("").toAbsolutePath();
                String plugin = read(root.resolve(
                        "src/main/java/com/flippingfriend/FlippingFriendPlugin.java"));
                String client = read(root.resolve(
                        "src/main/java/com/flippingfriend/companion/CompanionClient.java"));
                String tracker = read(root.resolve(
                        "companion/src/main/java/com/flippingfriend/companion/ActiveOfferTracker.java"));

                Assert.assertTrue(plugin.contains(
                        "event.getOffer().getState() == net.runelite.api.GrandExchangeOfferState.EMPTY"));
                Assert.assertTrue(plugin.contains(
                        "publishOfferClearedToCompanion(event.getSlot())"));
                Assert.assertTrue(plugin.contains(
                        "companion.publishOfferCleared(slot)"));
                Assert.assertTrue(plugin.contains(
                        "publishOfferToCompanion(offerTracker.getOffer(event.getSlot()))"));

                Assert.assertTrue(client.contains(
                        "public void publishOfferCleared(int slot)"));
                Assert.assertTrue(client.contains(
                        "outbox.enqueue((eventId, sessionId, sequence) ->"));
                Assert.assertTrue(client.contains(".slot(slot)"));
                Assert.assertTrue(client.contains(
                        ".eventIdentity(eventId, sessionId, null)"));
                Assert.assertTrue(client.contains(".sequence(sequence)"));
                Assert.assertTrue(client.contains(
                        "postOffer(event)"));

                Assert.assertTrue(tracker.contains(
                        "if (\"EMPTY\".equals(state))"));
                Assert.assertFalse(tracker.contains(
                        "\"BOUGHT\".equals(state)"));
                Assert.assertFalse(tracker.contains(
                        "\"SOLD\".equals(state)"));
                Assert.assertFalse(tracker.contains(
                        "\"CANCELLED_BUY\".equals(state)"));
                Assert.assertFalse(tracker.contains(
                        "\"CANCELLED_SELL\".equals(state)"));
        }

        private static String read(Path path) throws Exception
        {
                return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
        }
}
