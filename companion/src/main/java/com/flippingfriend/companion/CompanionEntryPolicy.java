package com.flippingfriend.companion;

import com.flippingfriend.core.EntryPolicy;
import com.flippingfriend.core.OfferLifecycleAction;
import com.flippingfriend.core.PolicyAbstentionReason;
import com.flippingfriend.core.PolicyDecision;
import com.flippingfriend.core.PolicyDecisionType;
import com.flippingfriend.core.PortfolioAllocation;
import com.flippingfriend.core.PortfolioCandidate;
import com.flippingfriend.core.PortfolioPlan;

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
            return unavailable(plan.getReason(), recommendationId, now, observedAt);
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

    private static PolicyDecision unavailable(String reason, String recommendationId, long now,
        long observedAt)
    {
        String text = reason == null ? "" : reason;
        if (text.equals("Market data is stale, so no buy will be suggested until a fresh snapshot arrives."))
            return abstain(recommendationId, now, observedAt,
                PolicyAbstentionReason.MARKET_DATA_STALE, "MARKET_DATA_STALE");
        if (text.startsWith("Session drawdown has reached 15%."))
            return abstain(recommendationId, now, observedAt,
                PolicyAbstentionReason.DRAWDOWN_LIMIT_REACHED, "DRAWDOWN_LIMIT_REACHED");
        if (text.startsWith("Sell-only mode:"))
            return abstain(recommendationId, now, observedAt,
                PolicyAbstentionReason.SELL_ONLY_MODE, "SELL_ONLY_MODE");
        if (text.equals("Every Grand Exchange slot is occupied."))
            return abstain(recommendationId, now, observedAt,
                PolicyAbstentionReason.NO_FREE_SLOT, "NO_FREE_SLOT");
        return abstain(recommendationId, now, observedAt,
            PolicyAbstentionReason.NO_ELIGIBLE_CANDIDATE, "NO_ELIGIBLE_CANDIDATE");
    }

    private static PolicyDecision abstain(String recommendationId, long now, long observedAt,
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
