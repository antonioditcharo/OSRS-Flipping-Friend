package com.flippingfriend.companion;

import com.flippingfriend.core.PolicyDecision;
import com.google.gson.Gson;
import org.junit.Assert;
import org.junit.Test;

public class EntryDecisionWireTest
{
    private final Gson gson = new Gson();

    @Test public void validBuyAndAbstentionAreAccepted()
    {
        Assert.assertTrue(valid(base("PLACE_BUY", "NONE", "candidate", "ENTRY")));
        Assert.assertTrue(valid(base("WAIT", "NO_FREE_SLOT", null, "ENTRY")));
    }

    @Test public void malformedAndIncompletePayloadsFailClosed()
    {
        Assert.assertNull(client().consumeEntryDecision("not-json"));
        Assert.assertNull(client().consumeEntryDecision(""));
        Assert.assertFalse(valid("{}"));
        Assert.assertFalse(valid(base("PLACE_BUY", "NONE", null, "ENTRY")));
        Assert.assertFalse(valid(base("WAIT", "NONE", null, "ENTRY")));
        Assert.assertFalse(valid(base("PLACE_BUY", "NO_FREE_SLOT", "candidate", "ENTRY")));
        Assert.assertFalse(valid(base("COLLECT", "NONE", "candidate", "ENTRY")));
        Assert.assertFalse(valid(base("PLACE_BUY", "NONE", "candidate", "EXIT")));
    }

    @Test public void versionsEnumsTimestampsAndRecommendationIdentityAreStrict()
    {
        Assert.assertFalse(valid(base("PLACE_BUY", "NONE", "candidate", "UNKNOWN")));
        Assert.assertFalse(valid(base("UNKNOWN", "NONE", "candidate", "ENTRY")));
        Assert.assertFalse(valid(base("PLACE_BUY", "UNKNOWN", "candidate", "ENTRY")));
        Assert.assertFalse(valid(base("PLACE_BUY", "NONE", "candidate", "ENTRY").replace("\"schemaVersion\":\"1\"", "\"schemaVersion\":\"2\"")));
        Assert.assertFalse(valid(base("PLACE_BUY", "NONE", "candidate", "ENTRY").replace("\"policyVersion\":\"policy\"", "\"policyVersion\":\" \"")));
        Assert.assertFalse(valid(base("PLACE_BUY", "NONE", "candidate", "ENTRY").replace("\"decidedAt\":10", "\"decidedAt\":-1")));
        Assert.assertFalse(valid(base("PLACE_BUY", "NONE", "candidate", "ENTRY").replace("\"inputObservedAt\":9", "\"inputObservedAt\":11")));
        Assert.assertFalse(valid(base("PLACE_BUY", "NONE", "candidate", "ENTRY").replace("\"recommendationId\":\"plan\"", "\"recommendationId\":null")));
    }

    private boolean valid(String json)
    {
        return CompanionClient.validEntryDecision(gson.fromJson(json, PolicyDecision.class));
    }

    private CompanionClient client()
    {
        return new CompanionClient(org.mockito.Mockito.mock(com.flippingfriend.data.PluginStorage.class),
            gson, new SuggestionLedger());
    }

    private static String base(String action, String abstention, String candidate, String type)
    {
        return "{\"schemaVersion\":\"1\",\"policyVersion\":\"policy\"," +
            "\"decisionId\":\"decision\",\"decisionType\":\"" + type + "\"," +
            "\"action\":\"" + action + "\",\"abstentionReason\":\"" + abstention + "\"," +
            "\"reasonCode\":\"reason\",\"decidedAt\":10,\"inputObservedAt\":9," +
            "\"candidateId\":" + (candidate == null ? "null" : "\"" + candidate + "\"") + "," +
            "\"recommendationId\":\"plan\"}";
    }
}
