package com.flippingfriend;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.Assert;
import org.junit.Test;

public class PositionStatePublicationBoundaryTest
{
        @Test
        public void durablePositionsTravelWithAccountState() throws Exception
        {
                Path root = Paths.get("").toAbsolutePath();
                String snapshot = read(root.resolve(
                        "core/src/main/java/com/flippingfriend/core/AccountSnapshot.java"));
                String client = read(root.resolve(
                        "src/main/java/com/flippingfriend/companion/CompanionClient.java"));
                String plugin = read(root.resolve(
                        "src/main/java/com/flippingfriend/FlippingFriendPlugin.java"));

                Assert.assertTrue(snapshot.contains("List<PositionSnapshot> positions"));
                Assert.assertTrue(snapshot.contains("getPositions()"));
                Assert.assertTrue(snapshot.contains("Collections.unmodifiableList"));
                Assert.assertTrue(snapshot.contains("Collections.emptyList()"));

                Assert.assertTrue(client.contains("Collection<Position> positions"));
                Assert.assertTrue(client.contains("new java.util.ArrayList<>(positions)"));
                Assert.assertTrue(client.contains("new PositionSnapshot"));
                Assert.assertTrue(client.contains("position.getQuantity()"));
                Assert.assertTrue(client.contains("position.getTotalCost()"));
                Assert.assertTrue(client.contains("position.isCostKnown()"));
                Assert.assertTrue(client.contains("position.getOpenedAt()"));
                Assert.assertTrue(client.contains("position.getTargetSellPrice()"));
                Assert.assertTrue(client.contains("position.getStopPrice()"));
                Assert.assertTrue(client.contains("position.getPredictedSellMinutes()"));
                Assert.assertTrue(client.contains("positionSnapshots"));

                Assert.assertTrue(plugin.contains("engine.isSellOnly(), positions.all()"));
                Assert.assertTrue(plugin.contains("engine.refresh(false)"));
                Assert.assertTrue(plugin.contains("engine.buyFallback()"));
        }

        private static String read(Path path) throws Exception
        {
                return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
        }
}
