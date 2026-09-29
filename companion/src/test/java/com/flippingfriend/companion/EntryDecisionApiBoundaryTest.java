package com.flippingfriend.companion;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Assert;
import org.junit.Test;

public class EntryDecisionApiBoundaryTest
{
    @Test
    public void routeIsAuthenticatedAndIdentityOnly() throws Exception
    {
        Path root = repositoryRoot();
        String api = Files.readString(root.resolve(
            "companion/src/main/java/com/flippingfriend/companion/ApiServer.java"),
            StandardCharsets.UTF_8);
        String service = Files.readString(root.resolve(
            "companion/src/main/java/com/flippingfriend/companion/CompanionService.java"),
            StandardCharsets.UTF_8);

        Assert.assertTrue(api.contains(
            "server.createContext(\"/v1/policy/entry\", this::entryDecision)"));
        Assert.assertTrue(api.contains("if (!authorized(exchange)) return;"));
        Assert.assertTrue(api.contains("query.get(\"planId\")"));
        Assert.assertTrue(api.contains("query.get(\"rank\")"));
        Assert.assertTrue(service.contains(
            "PolicyDecision entryDecision(String planId, int allocationRank, long decidedAt)"));
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
}
