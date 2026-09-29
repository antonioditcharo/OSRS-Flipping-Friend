package com.flippingfriend.companion;

import com.flippingfriend.core.PolicyDecision;
import com.flippingfriend.core.PolicyDecisionType;
import com.flippingfriend.model.Suggestion;
import com.flippingfriend.model.SuggestionType;
import java.util.Objects;

/** Pure, side-effect-free parity comparison for existing and shadow entry presentations. */
final class EntryDecisionParity
{
    private EntryDecisionParity() { }

    static EntryDecisionParityResult compare(Suggestion existing, PresentedEntryDecision shadow)
    {
        if (existing == null && shadow == null)
            return result(EntryDecisionParityStatus.MATCH, "BOTH_PATHS_SILENT", null);
        if (existing == null)
            return result(EntryDecisionParityStatus.LEGACY_RESULT_MISSING,
                "LEGACY_RESULT_MISSING", decision(shadow));
        if (shadow == null)
            return result(EntryDecisionParityStatus.SHADOW_RESULT_MISSING,
                "SHADOW_RESULT_MISSING", null);
        PolicyDecision decision = shadow.getDecision();
        Suggestion presented = shadow.getSuggestion();
        if (!validLineage(decision) || presented == null)
            return result(EntryDecisionParityStatus.INVALID_SHADOW_LINEAGE,
                "INVALID_SHADOW_LINEAGE", decision);
        if (existing.getType() != presented.getType())
            return result(EntryDecisionParityStatus.ACTION_MISMATCH,
                "ACTION_TYPE", decision);
        if (existing.getType() == SuggestionType.WAIT)
        {
            if (!same(existing.getHeadline(), presented.getHeadline())
                || !same(existing.getDetail(), presented.getDetail()))
                return result(EntryDecisionParityStatus.PRESENTATION_MISMATCH,
                    "WAIT_PRESENTATION", decision);
            return result(EntryDecisionParityStatus.MATCH, "EXACT_WAIT_MATCH", decision);
        }
        if (existing.getType() != SuggestionType.BUY)
            return result(EntryDecisionParityStatus.ACTION_MISMATCH,
                "NON_ENTRY_ACTION", decision);
        if (existing.getItemId() != presented.getItemId()
            || !same(existing.getItemName(), presented.getItemName()))
            return result(EntryDecisionParityStatus.ITEM_MISMATCH,
                "ITEM_IDENTITY", decision);
        if (existing.getPrice() != presented.getPrice()
            || existing.getQuantity() != presented.getQuantity()
            || existing.getTargetSellPrice() != presented.getTargetSellPrice()
            || existing.getExpectedProfit() != presented.getExpectedProfit()
            || Double.compare(existing.getConfidence(), presented.getConfidence()) != 0
            || Double.compare(existing.getExpectedMinutes(), presented.getExpectedMinutes()) != 0
            || Double.compare(existing.getBuyFillMinutes(), presented.getBuyFillMinutes()) != 0
            || Double.compare(existing.getSellFillMinutes(), presented.getSellFillMinutes()) != 0)
            return result(EntryDecisionParityStatus.ECONOMICS_MISMATCH,
                "BUY_ECONOMICS", decision);
        if (!same(existing.getHeadline(), presented.getHeadline())
            || !same(existing.getDetail(), presented.getDetail()))
            return result(EntryDecisionParityStatus.PRESENTATION_MISMATCH,
                "BUY_PRESENTATION", decision);
        return result(EntryDecisionParityStatus.MATCH, "EXACT_BUY_MATCH", decision);
    }

    private static boolean validLineage(PolicyDecision decision)
    {
        return decision != null && "1".equals(decision.getSchemaVersion())
            && !blank(decision.getPolicyVersion()) && !blank(decision.getDecisionId())
            && !blank(decision.getRecommendationId())
            && decision.getDecisionType() == PolicyDecisionType.ENTRY;
    }

    private static EntryDecisionParityResult result(EntryDecisionParityStatus status,
        String reason, PolicyDecision decision)
    {
        return new EntryDecisionParityResult(status, reason,
            decision == null ? null : decision.getDecisionId(),
            decision == null ? null : decision.getPolicyVersion(),
            decision == null ? null : decision.getRecommendationId(),
            decision == null ? null : decision.getCandidateId());
    }

    private static PolicyDecision decision(PresentedEntryDecision presented)
    {
        return presented == null ? null : presented.getDecision();
    }

    private static boolean same(String left, String right)
    {
        return Objects.equals(left, right);
    }

    private static boolean blank(String value)
    {
        return value == null || value.trim().isEmpty();
    }
}
