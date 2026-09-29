package com.flippingfriend.companion;

import com.flippingfriend.core.OfferLifecycleAction;
import com.flippingfriend.core.PolicyAbstentionReason;
import com.flippingfriend.core.PolicyDecision;
import com.flippingfriend.core.PolicyDecisionType;
import org.junit.Assert;
import org.junit.Test;

public class BuyMaintenanceDecisionClientTest
{
    @Test public void acceptsOnlyCurrentLineageSafeMaintenanceDecisions()
    {
        PolicyDecision hold = action(OfferLifecycleAction.HOLD, "offer-1", "plan-1");
        Assert.assertTrue(CompanionClient.validBuyMaintenanceDecision(hold, "offer-1", "plan-1"));
        PolicyDecision cancel = action(OfferLifecycleAction.CANCEL_BUY, "offer-1", "plan-1");
        Assert.assertTrue(CompanionClient.validBuyMaintenanceDecision(cancel, "offer-1", "plan-1"));
        PolicyDecision wait = PolicyDecision.abstain("1", "policy", "decision-wait",
            PolicyDecisionType.BUY_MAINTENANCE, PolicyAbstentionReason.MARKET_DATA_STALE,
            "STALE", 20, 10, "offer-1", "plan-1");
        Assert.assertTrue(CompanionClient.validBuyMaintenanceDecision(wait, "offer-1", "plan-1"));
    }
    @Test public void rejectsWrongIdentityTypeActionAndAbstentionShape()
    {
        Assert.assertFalse(CompanionClient.validBuyMaintenanceDecision(
            action(OfferLifecycleAction.HOLD, "offer-2", "plan-1"), "offer-1", "plan-1"));
        Assert.assertFalse(CompanionClient.validBuyMaintenanceDecision(
            action(OfferLifecycleAction.HOLD, "offer-1", "plan-2"), "offer-1", "plan-1"));
        PolicyDecision entry = PolicyDecision.action("1", "policy", "entry",
            PolicyDecisionType.ENTRY, OfferLifecycleAction.PLACE_BUY, "ENTRY", 20, 10,
            "candidate", "plan-1");
        Assert.assertFalse(CompanionClient.validBuyMaintenanceDecision(entry, "offer-1", "plan-1"));
        PolicyDecision replace = action(OfferLifecycleAction.REPLACE_BUY, "offer-1", "plan-1");
        Assert.assertFalse(CompanionClient.validBuyMaintenanceDecision(replace, "offer-1", "plan-1"));
    }
    @Test public void rejectsMalformedTimestampsAndMetadata()
    {
        com.google.gson.Gson gson = new com.google.gson.Gson();
        com.google.gson.JsonObject malformed = new com.google.gson.JsonParser().parse(
            gson.toJson(action(OfferLifecycleAction.HOLD, "offer-1", "plan-1")))
            .getAsJsonObject();
        malformed.addProperty("decidedAt", 10);
        malformed.addProperty("inputObservedAt", 20);
        PolicyDecision future = gson.fromJson(malformed, PolicyDecision.class);
        Assert.assertFalse(CompanionClient.validBuyMaintenanceDecision(
            future, "offer-1", "plan-1"));
        Assert.assertFalse(CompanionClient.validBuyMaintenanceDecision(null, "offer-1", "plan-1"));
    }
    private static PolicyDecision action(OfferLifecycleAction action, String offer, String plan)
    {
        return PolicyDecision.action("1", "companion-buy-maintenance-1", "decision-" + action,
            PolicyDecisionType.BUY_MAINTENANCE, action, "REASON", 20, 10, offer, plan);
    }
}
