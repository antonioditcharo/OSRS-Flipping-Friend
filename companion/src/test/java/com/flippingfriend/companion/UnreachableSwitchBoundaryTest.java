package com.flippingfriend.companion;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Assert;
import org.junit.Test;

public class UnreachableSwitchBoundaryTest
{
        @Test
        public void unreachableCompanionSwitchesRemainRemoved() throws Exception
        {
                Path root = repositoryRoot();
                String factory = read(root.resolve("companion/src/main/java/com/flippingfriend/companion/CandidateFactory.java"));
                String planner = read(root.resolve("companion/src/main/java/com/flippingfriend/companion/PortfolioPlanner.java"));
                String engine = read(root.resolve("src/main/java/com/flippingfriend/model/SuggestionEngine.java"));

                Assert.assertFalse(factory.contains("conservativePricing"));
                Assert.assertFalse(factory.contains("setConservativePricing"));
                Assert.assertFalse(factory.contains("CONSERVATIVE_STEP"));
                Assert.assertFalse(factory.contains("waitAwareSizing"));
                Assert.assertFalse(factory.contains("setWaitAwareSizing"));
                Assert.assertTrue(factory.contains("buySide.getUnitsPerHour() * horizonHours"));
                Assert.assertTrue(factory.contains("sellSide.getUnitsPerHour() * horizonHours"));

                Assert.assertTrue(factory.contains("setLearningDisabled"));
                Assert.assertTrue(factory.contains("learningDisabled"));
                Assert.assertTrue(planner.contains("setLearningDisabled(account.isLearningDisabled())"));
                Assert.assertTrue(engine.contains("benchmarkProfitPerFlip()"));
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
