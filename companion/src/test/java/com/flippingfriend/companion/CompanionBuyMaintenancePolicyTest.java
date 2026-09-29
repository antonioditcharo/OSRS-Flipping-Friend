package com.flippingfriend.companion;

import com.flippingfriend.core.OfferLifecycleAction;
import com.flippingfriend.core.PolicyAbstentionReason;
import com.flippingfriend.core.PolicyDecision;
import com.flippingfriend.core.PolicyDecisionType;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

public class CompanionBuyMaintenancePolicyTest
{
    private final CompanionBuyMaintenancePolicy policy = new CompanionBuyMaintenancePolicy();
    @Test public void healthyOpenBuyIsHeldDeterministically()
    {
        PolicyDecision one = policy.decide(context(100, true, 50));
        PolicyDecision two = policy.decide(context(100, true, 50));
        assertEquals(PolicyDecisionType.BUY_MAINTENANCE, one.getDecisionType());
        assertEquals(OfferLifecycleAction.HOLD, one.getAction());
        assertEquals("companion-buy-maintenance-1", one.getPolicyVersion());
        assertEquals(one.getDecisionId(), two.getDecisionId());
        assertEquals("offer-1", one.getCandidateId());
        assertEquals("plan-1", one.getRecommendationId());
    }
    @Test public void exhaustedLimitCancelsButDoesNotInventReplacement()
    {
        PolicyDecision decision = policy.decide(context(0, true, 50));
        assertEquals(OfferLifecycleAction.CANCEL_BUY, decision.getAction());
        assertEquals("BUY_LIMIT_EXHAUSTED", decision.getReasonCode());
    }
    @Test public void staleOrInvalidInputsAbstainExplicitly()
    {
        PolicyDecision stale = policy.decide(context(100, true, 500));
        assertEquals(OfferLifecycleAction.WAIT, stale.getAction());
        assertEquals(PolicyAbstentionReason.MARKET_DATA_STALE, stale.getAbstentionReason());
        PolicyDecision invalid = policy.decide(context(100, false, 50));
        assertEquals(PolicyAbstentionReason.OFFER_STATE_INCONSISTENT, invalid.getAbstentionReason());
    }
    @Test public void changedAuthoritativeInputChangesDecisionIdentity()
    {
        assertNotEquals(policy.decide(context(100, true, 50)).getDecisionId(),
            policy.decide(context(99, true, 50)).getDecisionId());
    }
    private static BuyMaintenancePolicyContext context(int remainingLimit, boolean open, long age)
    {
        long now = 2_000;
        return new BuyMaintenancePolicyContext("plan-1", "offer-1", 2, 561, 500, 100,
            105, 110, remainingLimit, now - 60, now - age, now, open);
    }
}
