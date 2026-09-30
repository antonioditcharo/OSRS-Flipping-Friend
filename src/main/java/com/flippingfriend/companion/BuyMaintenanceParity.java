package com.flippingfriend.companion;

import com.flippingfriend.core.PolicyDecision;
import com.flippingfriend.core.PolicyDecisionType;
import com.flippingfriend.model.Suggestion;
import java.util.Objects;

/** Pure evidence-only comparison of local and companion buy-maintenance presentations. */
final class BuyMaintenanceParity
{
    private BuyMaintenanceParity() { }

    static BuyMaintenanceParityResult compare(Suggestion existing,
        PresentedBuyMaintenanceDecision shadow)
    {
        if (existing == null)
            return result(BuyMaintenanceParityStatus.LEGACY_RESULT_MISSING,
                "LEGACY_RESULT_MISSING", decision(shadow));
        if (shadow == null)
            return result(BuyMaintenanceParityStatus.SHADOW_RESULT_MISSING,
                "SHADOW_RESULT_MISSING", null);
        PolicyDecision decision = shadow.getDecision();
        Suggestion presented = shadow.getSuggestion();
        if (!validLineage(decision) || presented == null)
            return result(BuyMaintenanceParityStatus.INVALID_SHADOW_LINEAGE,
                "INVALID_SHADOW_LINEAGE", decision);
        if (existing.getType() != presented.getType())
            return result(BuyMaintenanceParityStatus.ACTION_MISMATCH,
                "ACTION_TYPE", decision);
        if (existing.getItemId() != presented.getItemId()
            || !same(existing.getItemName(), presented.getItemName()))
            return result(BuyMaintenanceParityStatus.ITEM_MISMATCH,
                "ITEM_IDENTITY", decision);
        if (existing.getSlot() != presented.getSlot()
            || existing.getPrice() != presented.getPrice()
            || existing.getQuantity() != presented.getQuantity())
            return result(BuyMaintenanceParityStatus.SOURCE_OFFER_MISMATCH,
                "SOURCE_OFFER_ECONOMICS", decision);
        if (!same(existing.getHeadline(), presented.getHeadline())
            || !same(existing.getDetail(), presented.getDetail()))
            return result(BuyMaintenanceParityStatus.PRESENTATION_MISMATCH,
                "PRESENTATION_TEXT", decision);
        return result(BuyMaintenanceParityStatus.MATCH,
            "EXACT_MAINTENANCE_MATCH", decision);
    }

    private static boolean validLineage(PolicyDecision decision)
    {
        return decision != null && "1".equals(decision.getSchemaVersion())
            && !blank(decision.getPolicyVersion()) && !blank(decision.getDecisionId())
            && !blank(decision.getCandidateId()) && !blank(decision.getRecommendationId())
            && decision.getDecisionType() == PolicyDecisionType.BUY_MAINTENANCE;
    }

    private static BuyMaintenanceParityResult result(BuyMaintenanceParityStatus status,
        String reason, PolicyDecision decision)
    {
        return new BuyMaintenanceParityResult(status, reason,
            decision == null ? null : decision.getDecisionId(),
            decision == null ? null : decision.getPolicyVersion(),
            decision == null ? null : decision.getCandidateId(),
            decision == null ? null : decision.getRecommendationId());
    }

    private static PolicyDecision decision(PresentedBuyMaintenanceDecision presented)
    {
        return presented == null ? null : presented.getDecision();
    }

    private static boolean same(String left, String right) { return Objects.equals(left, right); }
    private static boolean blank(String value) { return value == null || value.trim().isEmpty(); }
}
