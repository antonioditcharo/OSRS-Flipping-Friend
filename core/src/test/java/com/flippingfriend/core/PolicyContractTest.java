package com.flippingfriend.core;

import org.junit.Assert;
import org.junit.Test;

public class PolicyContractTest
{
    @Test
    public void actionDecisionIsVersionedAndReusesLifecycleActions()
    {
        PolicyDecision decision = PolicyDecision.action("1", "entry-1", "decision-1",
            PolicyDecisionType.ENTRY, OfferLifecycleAction.PLACE_BUY, "CANDIDATE_SELECTED",
            20, 10, "candidate-1", "recommendation-1");
        Assert.assertEquals(OfferLifecycleAction.PLACE_BUY, decision.getAction());
        Assert.assertEquals(PolicyAbstentionReason.NONE, decision.getAbstentionReason());
        Assert.assertEquals("1", decision.getSchemaVersion());
        Assert.assertEquals("entry-1", decision.getPolicyVersion());
    }

    @Test
    public void abstentionIsExplicitWaitWithMachineReadableReason()
    {
        PolicyDecision decision = PolicyDecision.abstain("1", "entry-1", "decision-2",
            PolicyDecisionType.ENTRY, PolicyAbstentionReason.MARKET_DATA_STALE,
            "MARKET_DATA_STALE", 20, 10, null, null);
        Assert.assertEquals(OfferLifecycleAction.WAIT, decision.getAction());
        Assert.assertEquals(PolicyAbstentionReason.MARKET_DATA_STALE,
            decision.getAbstentionReason());
    }

    @Test(expected = IllegalArgumentException.class)
    public void waitCannotMasqueradeAsAnAction()
    {
        PolicyDecision.action("1", "entry-1", "decision-3", PolicyDecisionType.ENTRY,
            OfferLifecycleAction.WAIT, "WAIT", 20, 10, null, null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void abstentionRequiresAReason()
    {
        PolicyDecision.abstain("1", "entry-1", "decision-4", PolicyDecisionType.ENTRY,
            PolicyAbstentionReason.NONE, "NONE", 20, 10, null, null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void observationCannotPostdateDecision()
    {
        PolicyDecision.action("1", "entry-1", "decision-5", PolicyDecisionType.ENTRY,
            OfferLifecycleAction.PLACE_BUY, "CANDIDATE_SELECTED", 10, 20, null, null);
    }

    @Test
    public void specializedContractsRemainTypedAndDependencyFree()
    {
        EntryPolicy<String> policy = context -> PolicyDecision.abstain("1", "entry-1",
            "decision-6", PolicyDecisionType.ENTRY,
            PolicyAbstentionReason.NO_ELIGIBLE_CANDIDATE, "NO_ELIGIBLE_CANDIDATE",
            20, 20, null, null);
        Assert.assertEquals(PolicyAbstentionReason.NO_ELIGIBLE_CANDIDATE,
            policy.decide("context").getAbstentionReason());
        Assert.assertTrue(TradePolicy.class.isAssignableFrom(EntryPolicy.class));
        Assert.assertTrue(TradePolicy.class.isAssignableFrom(BuyMaintenancePolicy.class));
        Assert.assertTrue(TradePolicy.class.isAssignableFrom(SellMaintenancePolicy.class));
        Assert.assertTrue(TradePolicy.class.isAssignableFrom(ExitPolicy.class));
    }
}
