package com.flippingfriend.companion;

import org.junit.Test;
import static org.junit.Assert.*;

public class BuyReplacementPricingContextComposerTest
{
    private final CompanionBuyReplacementReadinessPolicy policy =
        new CompanionBuyReplacementReadinessPolicy();
    private final BuyReplacementPricingContextComposer composer =
        new BuyReplacementPricingContextComposer();

    @Test public void exactReadyAssessmentComposesIdentitySafePricingInputs()
    {
        BuyReplacementEligibilityContext source = context(400, 400, 880, 880, 1000, 1100);
        BuyReplacementReadinessAssessment assessment = policy.assess(source);
        BuyReplacementPricingContext result = composer.compose(source, assessment);
        assertNotNull(result);
        assertEquals(assessment.getAssessmentId(), result.getReadinessAssessmentId());
        assertEquals("intent-1", result.getIntentId());
        assertEquals("offer-1", result.getOriginalOfferIdentity());
        assertEquals("plan-1", result.getRecommendationId());
        assertEquals(400, result.getExactRemainderQuantity());
        assertEquals(1000, result.getHistoricalOriginalPrice());
        assertEquals(990, result.getCurrentLowPrice());
        assertEquals(1010, result.getCurrentHighPrice());
        assertEquals(400, result.getBuyLimitRemaining());
        assertEquals(2_000_000, result.getSpendableCoins());
    }

    @Test public void nonReadyAndMismatchedAssessmentsFailClosed()
    {
        BuyReplacementEligibilityContext source = context(400, 399, 900, 900, 1000, 1100);
        assertEquals(BuyReplacementReadinessOutcome.INELIGIBLE,
            policy.assess(source).getOutcome());
        assertNull(composer.compose(source, policy.assess(source)));

        BuyReplacementEligibilityContext ready = context(400, 400, 900, 900, 1000, 1100);
        BuyReplacementEligibilityContext other = context(399, 400, 900, 900, 1000, 1100);
        assertNull(composer.compose(other, policy.assess(ready)));
    }

    @Test public void staleExpiredAndTimestampMismatchesFailClosed()
    {
        BuyReplacementEligibilityContext stale = context(400, 400, 879, 880, 1000, 1100);
        assertNull(composer.compose(stale, policy.assess(stale)));

        BuyReplacementEligibilityContext expired = context(400, 400, 900, 900, 1001, 1000);
        assertNull(composer.compose(expired, policy.assess(expired)));

        BuyReplacementEligibilityContext ready = context(400, 400, 900, 900, 1000, 1100);
        BuyReplacementReadinessAssessment valid = policy.assess(ready);
        BuyReplacementReadinessAssessment wrongTime = new BuyReplacementReadinessAssessment(
            valid.getSchemaVersion(), valid.getPolicyVersion(), valid.getAssessmentId(),
            valid.getIntentId(), valid.getOfferIdentity(), valid.getRecommendationId(),
            valid.getSlot(), valid.getItemId(), valid.getRemainderQuantity(), valid.getOutcome(),
            valid.getReasonCode(), valid.getEvaluatedAt() - 1, valid.getInputObservedAt());
        assertNull(composer.compose(ready, wrongTime));
    }

    @Test public void pricingContextCarriesNoProposalActionOrAuthorization() throws Exception
    {
        String source = java.nio.file.Files.readString(java.nio.file.Path.of(
            "src/main/java/com/flippingfriend/companion/BuyReplacementPricingContext.java"));
        assertFalse(source.contains("proposedPrice"));
        assertFalse(source.contains("OfferLifecycleAction"));
        assertFalse(source.contains("REPLACEMENT_AUTHORIZED"));
    }

    private static BuyReplacementEligibilityContext context(int remainder, int limit,
        long accountAt, long marketAt, long evaluatedAt, long expiresAt)
    {
        return new BuyReplacementEligibilityContext("intent-1", "offer-1", "plan-1", 2,
            4151, "Abyssal whip", remainder, 1000, 990, 1010, limit, 2_000_000,
            1, true, 100, expiresAt, accountAt, marketAt, evaluatedAt);
    }
}
