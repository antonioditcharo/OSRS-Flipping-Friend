package com.flippingfriend.companion;

import com.flippingfriend.core.OfferLifecycleAction;
import com.flippingfriend.core.PolicyAbstentionReason;
import com.flippingfriend.core.PolicyDecision;
import com.flippingfriend.core.PolicyDecisionType;
import com.flippingfriend.model.Suggestion;
import com.flippingfriend.model.SuggestionType;
import com.google.gson.Gson;
import org.junit.Assert;
import org.junit.Test;

public class EntryDecisionParityTest
{
    @Test public void exactBuyAndWaitParityRetainLineage()
    {
        PolicyDecision buyDecision = buy();
        Suggestion buy = buySuggestion(4151, "Abyssal whip", 100, 8, 120, 500,
            0.8, 42, 18, 24, "Buy 8 × Abyssal whip", "");
        EntryDecisionParityResult buyResult = EntryDecisionParity.compare(buy,
            new PresentedEntryDecision(buyDecision, buy));
        Assert.assertTrue(buyResult.isMatch());
        Assert.assertEquals("EXACT_BUY_MATCH", buyResult.getReasonCode());
        Assert.assertEquals("decision", buyResult.getDecisionId());
        Assert.assertEquals("policy", buyResult.getPolicyVersion());
        Assert.assertEquals("plan", buyResult.getRecommendationId());
        Assert.assertEquals("4151:100:120:8", buyResult.getCandidateId());

        Suggestion waiting = Suggestion.waiting("Nothing worth buying", "Wait for a new plan.");
        EntryDecisionParityResult waitResult = EntryDecisionParity.compare(waiting,
            new PresentedEntryDecision(waitDecision(), waiting));
        Assert.assertEquals(EntryDecisionParityStatus.MATCH, waitResult.getStatus());
        Assert.assertEquals("EXACT_WAIT_MATCH", waitResult.getReasonCode());
    }

    @Test public void mismatchesAreCategorizedDeterministically()
    {
        Suggestion base = buySuggestion(4151, "Abyssal whip", 100, 8, 120, 500,
            0.8, 42, 18, 24, "Buy 8 × Abyssal whip", "");
        Assert.assertEquals(EntryDecisionParityStatus.ACTION_MISMATCH,
            compare(base, Suggestion.waiting("Wait", "Reason")).getStatus());
        Assert.assertEquals(EntryDecisionParityStatus.ITEM_MISMATCH,
            compare(base, buySuggestion(4152, "Other", 100, 8, 120, 500,
                0.8, 42, 18, 24, base.getHeadline(), base.getDetail())).getStatus());
        Assert.assertEquals(EntryDecisionParityStatus.ECONOMICS_MISMATCH,
            compare(base, buySuggestion(4151, "Abyssal whip", 101, 8, 120, 500,
                0.8, 42, 18, 24, base.getHeadline(), base.getDetail())).getStatus());
        Assert.assertEquals(EntryDecisionParityStatus.ECONOMICS_MISMATCH,
            compare(base, buySuggestion(4151, "Abyssal whip", 100, 9, 120, 500,
                0.8, 42, 18, 24, base.getHeadline(), base.getDetail())).getStatus());
        Assert.assertEquals(EntryDecisionParityStatus.ECONOMICS_MISMATCH,
            compare(base, buySuggestion(4151, "Abyssal whip", 100, 8, 121, 500,
                0.8, 42, 18, 24, base.getHeadline(), base.getDetail())).getStatus());
        Assert.assertEquals(EntryDecisionParityStatus.ECONOMICS_MISMATCH,
            compare(base, buySuggestion(4151, "Abyssal whip", 100, 8, 120, 501,
                0.8, 42, 18, 24, base.getHeadline(), base.getDetail())).getStatus());
        Assert.assertEquals(EntryDecisionParityStatus.ECONOMICS_MISMATCH,
            compare(base, buySuggestion(4151, "Abyssal whip", 100, 8, 120, 500,
                0.81, 42, 18, 24, base.getHeadline(), base.getDetail())).getStatus());
        Assert.assertEquals(EntryDecisionParityStatus.ECONOMICS_MISMATCH,
            compare(base, buySuggestion(4151, "Abyssal whip", 100, 8, 120, 500,
                0.8, 43, 18, 24, base.getHeadline(), base.getDetail())).getStatus());
        Assert.assertEquals(EntryDecisionParityStatus.ECONOMICS_MISMATCH,
            compare(base, buySuggestion(4151, "Abyssal whip", 100, 8, 120, 500,
                0.8, 42, 19, 24, base.getHeadline(), base.getDetail())).getStatus());
        Assert.assertEquals(EntryDecisionParityStatus.ECONOMICS_MISMATCH,
            compare(base, buySuggestion(4151, "Abyssal whip", 100, 8, 120, 500,
                0.8, 42, 18, 25, base.getHeadline(), base.getDetail())).getStatus());
        Assert.assertEquals(EntryDecisionParityStatus.PRESENTATION_MISMATCH,
            compare(base, buySuggestion(4151, "Abyssal whip", 100, 8, 120, 500,
                0.8, 42, 18, 24, "Different", base.getDetail())).getStatus());
    }

    @Test public void missingResultsAndInvalidLineageFailClosed()
    {
        Suggestion existing = buySuggestion(4151, "Abyssal whip", 100, 8, 120, 500,
            0.8, 42, 18, 24, "Buy", "");
        PresentedEntryDecision shadow = new PresentedEntryDecision(buy(), existing);
        Assert.assertEquals(EntryDecisionParityStatus.LEGACY_RESULT_MISSING,
            EntryDecisionParity.compare(null, shadow).getStatus());
        Assert.assertEquals(EntryDecisionParityStatus.SHADOW_RESULT_MISSING,
            EntryDecisionParity.compare(existing, null).getStatus());
        Assert.assertEquals(EntryDecisionParityStatus.MATCH,
            EntryDecisionParity.compare(null, null).getStatus());
        PolicyDecision invalid = new Gson().fromJson(
            new Gson().toJson(buy()).replace("\"schemaVersion\":\"1\"",
                "\"schemaVersion\":\"2\""), PolicyDecision.class);
        Assert.assertEquals(EntryDecisionParityStatus.INVALID_SHADOW_LINEAGE,
            EntryDecisionParity.compare(existing,
                new PresentedEntryDecision(invalid, existing)).getStatus());
    }

    private static EntryDecisionParityResult compare(Suggestion existing, Suggestion shadow)
    {
        return EntryDecisionParity.compare(existing, new PresentedEntryDecision(buy(), shadow));
    }

    private static PolicyDecision buy()
    {
        return PolicyDecision.action("1", "policy", "decision", PolicyDecisionType.ENTRY,
            OfferLifecycleAction.PLACE_BUY, "ENTRY_CANDIDATE_SELECTED", 10, 9,
            "4151:100:120:8", "plan");
    }

    private static PolicyDecision waitDecision()
    {
        return PolicyDecision.abstain("1", "policy", "decision", PolicyDecisionType.ENTRY,
            PolicyAbstentionReason.NO_ELIGIBLE_CANDIDATE, "NO_ELIGIBLE_CANDIDATE",
            10, 9, null, "plan");
    }

    private static Suggestion buySuggestion(int itemId, String name, int price, int quantity,
        int target, long profit, double confidence, double minutes, double buyMinutes,
        double sellMinutes, String headline, String detail)
    {
        return Suggestion.builder(SuggestionType.BUY).item(itemId, name).price(price)
            .quantity(quantity).targetSellPrice(target).expectedProfit(profit)
            .confidence(confidence).expectedMinutes(minutes)
            .fillMinutes(buyMinutes, sellMinutes).headline(headline).detail(detail).build();
    }
}
