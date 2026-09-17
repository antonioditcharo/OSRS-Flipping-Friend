package com.flippingfriend.companion;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Assert;
import org.junit.Test;

public class CompanionHttpBoundaryTest
{
        @Test
        public void activeCompanionServicesShareOneHttpClientAndIdentity() throws Exception
        {
                Path root = repositoryRoot();
                String boundary = read(root.resolve("companion/src/main/java/com/flippingfriend/companion/CompanionHttp.java"));
                String ingestion = read(root.resolve("companion/src/main/java/com/flippingfriend/companion/MarketIngestionService.java"));
                String series = read(root.resolve("companion/src/main/java/com/flippingfriend/companion/SeriesCache.java"));
                String plugin = read(root.resolve("src/main/java/com/flippingfriend/data/WikiPriceClient.java"));
                Assert.assertTrue(boundary.contains("static final OkHttpClient CLIENT = new OkHttpClient()"));
                Assert.assertTrue(boundary.contains("github.com/antonioditcharo/OSRS-Flipping-Friend"));
                Assert.assertTrue(ingestion.contains("CompanionHttp.CLIENT"));
                Assert.assertTrue(ingestion.contains("CompanionHttp.USER_AGENT"));
                Assert.assertTrue(series.contains("CompanionHttp.CLIENT"));
                Assert.assertTrue(series.contains("CompanionHttp.USER_AGENT"));
                Assert.assertFalse(ingestion.contains("new OkHttpClient()"));
                Assert.assertFalse(series.contains("new OkHttpClient()"));
                Assert.assertFalse(ingestion.contains("contact local user"));
                Assert.assertFalse(series.contains("contact local user"));
                Assert.assertTrue(plugin.contains("github.com/antonioditcharo/OSRS-Flipping-Friend"));
                Assert.assertFalse(plugin.contains("github.com/osrs-flipping-friend"));
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
