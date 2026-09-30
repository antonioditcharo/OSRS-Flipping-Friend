package com.flippingfriend.companion;

import com.flippingfriend.core.OfferLifecycleAction;
import com.flippingfriend.core.PolicyDecision;
import com.flippingfriend.core.PolicyDecisionType;
import com.flippingfriend.model.Suggestion;
import com.flippingfriend.model.SuggestionType;
import org.junit.Assert;
import org.junit.Test;

public class BuyMaintenanceParityTest
{
    @Test public void exactCancelPresentationMatchesAndCarriesLineage()
    {
        PolicyDecision decision = decision("offer-1", "plan-1");
        Suggestion suggestion = cancel(561, "Nature rune", 1, 100, 400,
            "Cancel your Nature rune buy offer",
            "The companion says this open buy should be cancelled. It does not authorize a replacement yet.");
        BuyMaintenanceParityResult result = BuyMaintenanceParity.compare(suggestion,
            new PresentedBuyMaintenanceDecision(decision, suggestion));
        Assert.assertTrue(result.isMatch());
        Assert.assertEquals("decision", result.getDecisionId());
        Assert.assertEquals("policy", result.getPolicyVersion());
        Assert.assertEquals("offer-1", result.getOfferIdentity());
        Assert.assertEquals("plan-1", result.getRecommendationId());
    }

    @Test public void distinguishesActionItemOfferAndPresentationMismatches()
    {
        PolicyDecision decision = decision("offer-1", "plan-1");
        Suggestion base = cancel(561, "Nature rune", 1, 100, 400, "Cancel", "No replacement");
        Assert.assertEquals(BuyMaintenanceParityStatus.ACTION_MISMATCH,
            compare(base, wait(561, "Nature rune", 1, 100, 400, "Cancel", "No replacement"), decision));
        Assert.assertEquals(BuyMaintenanceParityStatus.ITEM_MISMATCH,
            compare(base, cancel(562, "Chaos rune", 1, 100, 400, "Cancel", "No replacement"), decision));
        Assert.assertEquals(BuyMaintenanceParityStatus.SOURCE_OFFER_MISMATCH,
            compare(base, cancel(561, "Nature rune", 2, 100, 400, "Cancel", "No replacement"), decision));
        Assert.assertEquals(BuyMaintenanceParityStatus.PRESENTATION_MISMATCH,
            compare(base, cancel(561, "Nature rune", 1, 100, 400, "Different", "No replacement"), decision));
    }

    @Test public void missingResultsAndInvalidLineageAreExplicit()
    {
        Suggestion existing = cancel(561, "Nature rune", 1, 100, 400, "Cancel", "No replacement");
        Assert.assertEquals(BuyMaintenanceParityStatus.LEGACY_RESULT_MISSING,
            BuyMaintenanceParity.compare(null, new PresentedBuyMaintenanceDecision(
                decision("offer-1", "plan-1"), existing)).getStatus());
        Assert.assertEquals(BuyMaintenanceParityStatus.SHADOW_RESULT_MISSING,
            BuyMaintenanceParity.compare(existing, null).getStatus());
        PolicyDecision wrongType = PolicyDecision.action("1", "policy", "decision",
            PolicyDecisionType.ENTRY, OfferLifecycleAction.PLACE_BUY, "REASON",
            20, 10, "candidate", "plan-1");
        Assert.assertEquals(BuyMaintenanceParityStatus.INVALID_SHADOW_LINEAGE,
            BuyMaintenanceParity.compare(existing,
                new PresentedBuyMaintenanceDecision(wrongType, existing)).getStatus());
    }

    private static BuyMaintenanceParityStatus compare(Suggestion existing, Suggestion shadow,
        PolicyDecision decision)
    {
        return BuyMaintenanceParity.compare(existing,
            new PresentedBuyMaintenanceDecision(decision, shadow)).getStatus();
    }

    private static PolicyDecision decision(String offer, String plan)
    {
        return PolicyDecision.action("1", "policy", "decision",
            PolicyDecisionType.BUY_MAINTENANCE, OfferLifecycleAction.CANCEL_BUY,
            "REASON", 20, 10, offer, plan);
    }

    private static Suggestion cancel(int itemId, String name, int slot, int price, int quantity,
        String headline, String detail)
    {
        return Suggestion.builder(SuggestionType.CANCEL).item(itemId, name).slot(slot)
            .price(price).quantity(quantity).headline(headline).detail(detail).build();
    }

    private static Suggestion wait(int itemId, String name, int slot, int price, int quantity,
        String headline, String detail)
    {
        return Suggestion.builder(SuggestionType.WAIT).item(itemId, name).slot(slot)
            .price(price).quantity(quantity).headline(headline).detail(detail).build();
    }
}
