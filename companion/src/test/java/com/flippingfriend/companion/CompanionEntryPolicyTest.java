package com.flippingfriend.companion;

import com.flippingfriend.core.OfferLifecycleAction;
import com.flippingfriend.core.PolicyAbstentionReason;
import com.flippingfriend.core.PolicyDecision;
import com.flippingfriend.core.PolicyDecisionType;
import com.flippingfriend.core.PortfolioAllocation;
import com.flippingfriend.core.PortfolioCandidate;
import com.flippingfriend.core.PortfolioPlan;
import com.flippingfriend.core.PortfolioPlanOutcome;
import java.util.Arrays;
import java.util.Collections;
import org.junit.Assert;
import org.junit.Test;

public class CompanionEntryPolicyTest
{
    private static final long NOW = 1_000;
    private final CompanionEntryPolicy policy = new CompanionEntryPolicy();

    private static PortfolioCandidate candidate(int item, int buy, int sell, int quantity)
    {
        return new PortfolioCandidate(item, "Item", "", buy, buy, buy, sell, sell, sell,
            quantity, 100, 1_000, 100, 10, .8, .8, 1, 1, 2, NOW + 100);
    }

    private static PortfolioPlan ready(PortfolioAllocation allocation)
    {
        return new PortfolioPlan("plan-1", 900, 1_100, "READY", "ok", 100,
            allocation == null ? Collections.emptyList() : Arrays.asList(allocation));
    }

    private PolicyDecision decide(PortfolioPlan plan, PortfolioAllocation allocation)
    {
        return policy.decide(new EntryPolicyContext(plan, allocation, NOW));
    }

    private static void assertAbstention(PolicyDecision decision, PolicyAbstentionReason reason,
        String code)
    {
        Assert.assertEquals(PolicyDecisionType.ENTRY, decision.getDecisionType());
        Assert.assertEquals(OfferLifecycleAction.WAIT, decision.getAction());
        Assert.assertEquals(reason, decision.getAbstentionReason());
        Assert.assertEquals(code, decision.getReasonCode());
    }

    @Test public void selectedCandidateProducesVersionedEntryAction()
    {
        PortfolioAllocation allocation = new PortfolioAllocation(1, candidate(4151, 100, 120, 8),
            "PLACE_BUY");
        PolicyDecision decision = decide(ready(allocation), allocation);
        Assert.assertEquals(OfferLifecycleAction.PLACE_BUY, decision.getAction());
        Assert.assertEquals(PolicyAbstentionReason.NONE, decision.getAbstentionReason());
        Assert.assertEquals("4151:100:120:8", decision.getCandidateId());
        Assert.assertEquals("plan-1", decision.getRecommendationId());
        Assert.assertEquals("companion-entry-1", decision.getPolicyVersion());
        Assert.assertEquals(decision.getDecisionId(), decide(ready(allocation), allocation).getDecisionId());
    }

    @Test public void missingAndExpiredPlansAreExplicit()
    {
        assertAbstention(decide(null, null), PolicyAbstentionReason.COMPANION_UNAVAILABLE,
            "COMPANION_PLAN_UNAVAILABLE");
        PortfolioPlan expired = new PortfolioPlan("p", 800, NOW, "READY", "ok", 0,
            Collections.emptyList());
        assertAbstention(decide(expired, null), PolicyAbstentionReason.COMPANION_UNAVAILABLE,
            "COMPANION_PLAN_EXPIRED");
    }

    @Test public void structuredOutcomesIgnoreDisplayProse()
    {
        assertOutcome(PortfolioPlanOutcome.MARKET_DATA_STALE,
            PolicyAbstentionReason.MARKET_DATA_STALE, "MARKET_DATA_STALE");
        assertOutcome(PortfolioPlanOutcome.DRAWDOWN_LIMIT_REACHED,
            PolicyAbstentionReason.DRAWDOWN_LIMIT_REACHED, "DRAWDOWN_LIMIT_REACHED");
        assertOutcome(PortfolioPlanOutcome.SELL_ONLY_MODE,
            PolicyAbstentionReason.SELL_ONLY_MODE, "SELL_ONLY_MODE");
        assertOutcome(PortfolioPlanOutcome.NO_FREE_SLOT,
            PolicyAbstentionReason.NO_FREE_SLOT, "NO_FREE_SLOT");
        assertOutcome(PortfolioPlanOutcome.PORTFOLIO_CONSTRAINT,
            PolicyAbstentionReason.RISK_CONSTRAINT, "PORTFOLIO_CONSTRAINT");
        assertOutcome(PortfolioPlanOutcome.COMPANION_STATE_UNAVAILABLE,
            PolicyAbstentionReason.ACCOUNT_STATE_UNAVAILABLE, "COMPANION_STATE_UNAVAILABLE");
    }

    @Test public void emptyAndUnselectablePlansAbstain()
    {
        assertAbstention(decide(ready(null), null), PolicyAbstentionReason.NO_ELIGIBLE_CANDIDATE,
            "NO_ELIGIBLE_CANDIDATE");
        PortfolioAllocation allocation = new PortfolioAllocation(1, candidate(1, 10, 12, 1),
            "PLACE_BUY");
        assertAbstention(decide(ready(allocation), null),
            PolicyAbstentionReason.NO_ELIGIBLE_CANDIDATE, "NO_SELECTABLE_ALLOCATION");
    }

    @Test public void invalidSelectedAllocationFailsClosed()
    {
        PortfolioAllocation invalid = new PortfolioAllocation(1, null, "PLACE_BUY");
        assertAbstention(decide(ready(invalid), invalid),
            PolicyAbstentionReason.ACCOUNT_STATE_INCONSISTENT, "INVALID_SELECTED_ALLOCATION");
    }

    private void assertOutcome(PortfolioPlanOutcome outcome,
        PolicyAbstentionReason expected, String code)
    {
        PortfolioPlan first = PortfolioPlan.unavailable("p", outcome, "first display text", 950);
        PortfolioPlan second = PortfolioPlan.unavailable("p", outcome, "entirely different text", 950);
        PolicyDecision one = decide(first, null);
        PolicyDecision two = decide(second, null);
        assertAbstention(one, expected, code);
        assertAbstention(two, expected, code);
        Assert.assertEquals(one.getDecisionId(), two.getDecisionId());
    }
}
