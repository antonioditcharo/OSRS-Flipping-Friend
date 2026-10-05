package com.flippingfriend.companion;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

import static org.junit.Assert.*;

public class BuyReplacementCandidateEligibilityEvaluatorTest
{
    private final BuyReplacementCandidateEligibilityEvaluator evaluator =
        new BuyReplacementCandidateEligibilityEvaluator();

    @Test public void mixedEvidenceIsEligibleAndRetainsCompleteEvidence()
    {
        BuyReplacementCandidateEligibilityAssessment result = evaluator.evaluate(setAtLow(396_500, 400, 990,
            assessments(400, 396_500, 990, new double[]{0.0, 0.002, 0.006})));
        assertNotNull(result);
        assertEquals(BuyReplacementCandidateEligibilityOutcome.ELIGIBLE, result.getOutcome());
        assertEquals(3, result.getAssessments().size());
        assertEquals(1, result.getAffordableCandidates().size());
        assertEquals(0, result.getAffordableCandidates().get(0).getOffsetIndex());
        assertEquals(400, result.getAffordableCandidates().get(0).getExactRemainderQuantity());
    }

    @Test public void allUnaffordableEvidenceIsValidButIneligible()
    {
        BuyReplacementCandidateEligibilityAssessment result = evaluator.evaluate(setAtLow(1, 400, 990,
            assessments(400, 1, 990, new double[]{0.0, 0.002})));
        assertNotNull(result);
        assertEquals(BuyReplacementCandidateEligibilityOutcome.INELIGIBLE, result.getOutcome());
        assertEquals(2, result.getAssessments().size());
        assertTrue(result.getAffordableCandidates().isEmpty());
    }

    @Test public void duplicateAffordableCandidatesPreserveOriginalIndexesAndOrder()
    {
        BuyReplacementCandidateEligibilityAssessment result = evaluator.evaluate(setAtLow(100,
            10, 5, assessments(10, 100, 5, new double[]{0.0, 0.002, 0.006, 0.012})));
        assertEquals(4, result.getAffordableCandidates().size());
        for (int i = 0; i < 4; i++)
        {
            assertEquals(i, result.getAffordableCandidates().get(i).getOffsetIndex());
            assertEquals(i == 0 ? 5 : 6, result.getAffordableCandidates().get(i).getBuyPrice());
        }
    }

    @Test public void bothViewsAreUnmodifiableAndEvaluationIsDeterministic()
    {
        BuyReplacementCandidateEligibilityAssessment first = evaluator.evaluate(set(20_000,
            assessments(10, 20_000, 990, new double[]{0.0, 0.01})));
        BuyReplacementCandidateEligibilityAssessment second = evaluator.evaluate(set(20_000,
            assessments(10, 20_000, 990, new double[]{0.0, 0.01})));
        try { first.getAssessments().clear(); fail("complete evidence must be unmodifiable"); }
        catch (UnsupportedOperationException expected) { }
        try { first.getAffordableCandidates().clear(); fail("affordable view must be unmodifiable"); }
        catch (UnsupportedOperationException expected) { }
        assertEquals(second.getOutcome(), first.getOutcome());
        assertEquals(second.getAffordableCandidates().size(), first.getAffordableCandidates().size());
    }

    @Test public void alteredOrIncompleteEvidenceFailsClosed()
    {
        assertNull(evaluator.evaluate(null));
        assertNull(evaluator.evaluate(set(100, Collections.emptyList())));
        assertNull(evaluator.evaluate(set(100, Arrays.asList(assessment(0, 0.0, 990, 10, 9900,
            BuyReplacementAffordabilityOutcome.UNAFFORDABLE), null))));
        assertNull(evaluator.evaluate(set(100, Collections.singletonList(assessment(1, 0.0, 990,
            10, 9900, BuyReplacementAffordabilityOutcome.UNAFFORDABLE)))));
        assertNull(evaluator.evaluate(set(100, Collections.singletonList(assessment(0, 0.0, 999,
            10, 9990, BuyReplacementAffordabilityOutcome.UNAFFORDABLE)))));
        assertNull(evaluator.evaluate(set(100, Collections.singletonList(assessment(0, 0.0, 990,
            11, 10890, BuyReplacementAffordabilityOutcome.UNAFFORDABLE)))));
        assertNull(evaluator.evaluate(set(100, Collections.singletonList(assessment(0, 0.0, 990,
            10, 9899, BuyReplacementAffordabilityOutcome.UNAFFORDABLE)))));
        assertNull(evaluator.evaluate(set(9900, Collections.singletonList(assessment(0, 0.0, 990,
            10, 9900, BuyReplacementAffordabilityOutcome.UNAFFORDABLE)))));
    }

    @Test public void eligibilityContractsCarryNoSelectionRankingOrAuthorization() throws Exception
    {
        String source = java.nio.file.Files.readString(java.nio.file.Path.of(
            "src/main/java/com/flippingfriend/companion/BuyReplacementCandidateEligibilityAssessment.java"));
        assertFalse(source.contains("selected"));
        assertFalse(source.contains("preferred"));
        assertFalse(source.contains("winner"));
        assertFalse(source.contains("score"));
        assertFalse(source.contains("rank"));
        assertFalse(source.contains("PolicyDecision"));
        assertFalse(source.contains("OfferLifecycleAction"));
        assertFalse(source.contains("REPLACEMENT_AUTHORIZED"));
    }

    private static List<BuyReplacementCandidateAffordability> assessments(int quantity, long coins,
        int low, double[] offsets)
    {
        List<BuyReplacementCandidateAffordability> result = new ArrayList<>();
        for (int i = 0; i < offsets.length; i++)
        {
            int price = com.flippingfriend.model.PriceOffset.apply(low, offsets[i]);
            long cost = (long) price * quantity;
            result.add(assessment(i, offsets[i], price, quantity, cost,
                cost <= coins ? BuyReplacementAffordabilityOutcome.AFFORDABLE
                    : BuyReplacementAffordabilityOutcome.UNAFFORDABLE));
        }
        return result;
    }

    private static BuyReplacementCandidateAffordability assessment(int index, double offset,
        int price, int quantity, long cost, BuyReplacementAffordabilityOutcome outcome)
    {
        return new BuyReplacementCandidateAffordability(index, offset, price, quantity, cost, outcome);
    }

    private static BuyReplacementCandidateAffordabilitySet set(long coins,
        List<BuyReplacementCandidateAffordability> values)
    {
        return setAtLow(coins, 10, 990, values);
    }

    private static BuyReplacementCandidateAffordabilitySet setAtLow(long coins, int quantity, int low,
        List<BuyReplacementCandidateAffordability> values)
    {
        BuyReplacementPricingContext pricing = new BuyReplacementPricingContext("assessment-1",
            "readiness-1", "intent-1", "offer-1", "plan-1", 2, 4151, "Abyssal whip",
            quantity, 1000, low, low + 1, quantity, coins, true, 100, 1100, 900, 900, 900, 1000);
        BuyReplacementPricingStrategyContext strategy = new BuyReplacementPricingStrategyContext(
            pricing, "Balanced", new double[]{0.0}, 2.5);
        BuyReplacementPriceCandidateSet candidates = new BuyReplacementPriceCandidateSet(strategy,
            Collections.singletonList(new BuyReplacementPriceCandidate(0, 0.0, low)));
        return new BuyReplacementCandidateAffordabilitySet(candidates, values);
    }
}
