package com.flippingfriend.companion;

import com.flippingfriend.core.EntryPolicy;
import com.flippingfriend.core.OfferLifecycleAction;
import com.flippingfriend.core.PolicyAbstentionReason;
import com.flippingfriend.core.PolicyDecision;
import com.flippingfriend.core.PolicyDecisionType;
import com.flippingfriend.core.PortfolioAllocation;
import com.flippingfriend.core.PortfolioCandidate;
import com.flippingfriend.core.PortfolioPlan;
import com.flippingfriend.core.PortfolioPlanOutcome;

/** Pure migration adapter from the current portfolio-plan result to the shared entry-policy contract. */
final class CompanionEntryPolicy implements EntryPolicy<EntryPolicyContext>
{
    static final String SCHEMA_VERSION = "1";
    static final String POLICY_VERSION = "companion-entry-1";

    @Override
    public PolicyDecision decide(EntryPolicyContext context)
    {
        if (context == null) throw new IllegalArgumentException("context is required");
        PortfolioPlan plan = context.getPlan();
        long now = context.getDecidedAt();
        if (plan == null)
            return abstain("none", now, now, PolicyAbstentionReason.COMPANION_UNAVAILABLE,
                "COMPANION_PLAN_UNAVAILABLE");
        String recommendationId = value(plan.getCorrelationId(), "none");
        long observedAt = Math.max(0, Math.min(now, plan.getCreatedAt()));
        if (plan.getExpiresAt() <= now)
            return abstain(recommendationId, now, observedAt,
                PolicyAbstentionReason.COMPANION_UNAVAILABLE, "COMPANION_PLAN_EXPIRED");
        if (!"READY".equals(plan.getStatus()))
            return unavailable(plan.getOutcome(), recommendationId, now, observedAt);
        if (plan.getAllocations().isEmpty())
            return abstain(recommendationId, now, observedAt,
                PolicyAbstentionReason.NO_ELIGIBLE_CANDIDATE, "NO_ELIGIBLE_CANDIDATE");
        PortfolioAllocation allocation = context.getSelectedAllocation();
        if (allocation == null)
            return abstain(recommendationId, now, observedAt,
                PolicyAbstentionReason.NO_ELIGIBLE_CANDIDATE, "NO_SELECTABLE_ALLOCATION");
        PortfolioCandidate candidate = allocation.getCandidate();
        if (candidate == null || candidate.getItemId() <= 0 || candidate.getBuyPrice() <= 0
            || candidate.getSellPrice() <= 0 || candidate.getQuantity() <= 0)
            return abstain(recommendationId, now, observedAt,
                PolicyAbstentionReason.ACCOUNT_STATE_INCONSISTENT, "INVALID_SELECTED_ALLOCATION");
        String candidateId = candidate.getItemId() + ":" + candidate.getBuyPrice() + ":"
            + candidate.getSellPrice() + ":" + candidate.getQuantity();
        return PolicyDecision.action(SCHEMA_VERSION, POLICY_VERSION,
            decisionId(recommendationId, "ENTRY_CANDIDATE_SELECTED", candidateId),
            PolicyDecisionType.ENTRY, OfferLifecycleAction.PLACE_BUY,
            "ENTRY_CANDIDATE_SELECTED", now, observedAt, candidateId, recommendationId);
    }

    private static PolicyDecision unavailable(PortfolioPlanOutcome outcome,
        String recommendationId, long now, long observedAt)
    {
        PolicyAbstentionReason abstention;
        String code;
        switch (outcome)
        {
            case MARKET_DATA_STALE: abstention = PolicyAbstentionReason.MARKET_DATA_STALE; code = "MARKET_DATA_STALE"; break;
            case DRAWDOWN_LIMIT_REACHED: abstention = PolicyAbstentionReason.DRAWDOWN_LIMIT_REACHED; code = "DRAWDOWN_LIMIT_REACHED"; break;
            case SELL_ONLY_MODE: abstention = PolicyAbstentionReason.SELL_ONLY_MODE; code = "SELL_ONLY_MODE"; break;
            case NO_FREE_SLOT: abstention = PolicyAbstentionReason.NO_FREE_SLOT; code = "NO_FREE_SLOT"; break;
            case PORTFOLIO_CONSTRAINT: abstention = PolicyAbstentionReason.RISK_CONSTRAINT; code = "PORTFOLIO_CONSTRAINT"; break;
            case COMPANION_STATE_UNAVAILABLE: abstention = PolicyAbstentionReason.ACCOUNT_STATE_UNAVAILABLE; code = "COMPANION_STATE_UNAVAILABLE"; break;
            default: abstention = PolicyAbstentionReason.NO_ELIGIBLE_CANDIDATE; code = "NO_ELIGIBLE_CANDIDATE"; break;
        }
        return abstain(recommendationId, now, observedAt, abstention, code);
    }

    static PolicyDecision abstain(String recommendationId, long now, long observedAt,
        PolicyAbstentionReason reason, String code)
    {
        return PolicyDecision.abstain(SCHEMA_VERSION, POLICY_VERSION,
            decisionId(recommendationId, code, null), PolicyDecisionType.ENTRY, reason, code,
            now, observedAt, null, recommendationId);
    }

    private static String decisionId(String recommendationId, String code, String candidateId)
    {
        return recommendationId + ":" + code + (candidateId == null ? "" : ":" + candidateId);
    }

    private static String value(String value, String fallback)
    {
        return value == null || value.trim().isEmpty() ? fallback : value;
    }
}
