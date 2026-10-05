package com.flippingfriend.companion;

import com.flippingfriend.data.Candle;
import com.flippingfriend.model.FillModel;
import com.flippingfriend.model.PriceOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

import static org.junit.Assert.*;

public class BuyReplacementCandidateFillEvaluatorTest
{
    private final BuyReplacementCandidateFillEvaluator evaluator =
        new BuyReplacementCandidateFillEvaluator(new FillModel());

    @Test public void evaluatesEveryAffordableCandidateInOriginalOrder()
    {
        BuyReplacementCandidateFillEvaluationSet result = evaluator.evaluate(context(), 1000);
        assertNotNull(result);
        assertEquals(3, result.getAssessments().size());
        assertEquals(3, result.getAffordableCandidates().size());
        assertEquals(3, result.getEvaluations().size());
        for (int i = 0; i < 3; i++)
        {
            BuyReplacementAffordableCandidate candidate = result.getAffordableCandidates().get(i);
            BuyReplacementCandidateFillEvaluation evaluation = result.getEvaluations().get(i);
            assertEquals(candidate.getOffsetIndex(), evaluation.getOffsetIndex());
            assertEquals(candidate.getOffset(), evaluation.getOffset(), 0.0);
            assertEquals(candidate.getBuyPrice(), evaluation.getBuyPrice());
            assertEquals(candidate.getExactRemainderQuantity(), evaluation.getExactRemainderQuantity());
            assertEquals(candidate.getTotalCost(), evaluation.getTotalCost());
            assertTrue(evaluation.getProbability() >= 0 && evaluation.getProbability() <= 1);
            assertTrue(evaluation.getUnitsPerHour() >= 0);
        }
        assertEquals(5, result.getExactRemainderQuantity());
        assertEquals(2.5, result.getEffectiveHorizonHours(), 0.0);
    }

    @Test public void equalPricesAndCostsRemainDistinctMeasuredEvidence()
    {
        BuyReplacementCandidateFillEvaluationSet result = evaluator.evaluate(context(), 1000);
        assertNotNull(result);
        assertEquals(result.getEvaluations().get(1).getBuyPrice(),
            result.getEvaluations().get(2).getBuyPrice());
        assertEquals(result.getEvaluations().get(1).getTotalCost(),
            result.getEvaluations().get(2).getTotalCost());
        assertEquals(1, result.getEvaluations().get(1).getOffsetIndex());
        assertEquals(2, result.getEvaluations().get(2).getOffsetIndex());
        try { result.getEvaluations().clear(); fail("fill evidence must be immutable"); }
        catch (UnsupportedOperationException expected) { }
    }

    @Test public void weakFillEvidenceIsPreservedRatherThanFiltered()
    {
        BuyReplacementCandidateFillEvaluationSet result = evaluator.evaluate(context(), 1000);
        assertNotNull(result);
        assertEquals(3, result.getEvaluations().size());
        assertEquals(0.0, result.getEvaluations().get(0).getProbability(), 0.0);
        assertTrue(Double.isInfinite(result.getEvaluations().get(0).getExpectedHours()));
    }

    @Test public void unavailableStaleAndInvalidInputsFailClosed()
    {
        assertNull(evaluator.evaluate(null, 1000));
        assertNull(new BuyReplacementCandidateFillEvaluator(null).evaluate(context(), 1000));
        assertNull(evaluator.evaluate(context(), 1121));
        assertNull(evaluator.evaluate(context(), 999));
    }

    @Test public void contractsCarryNoRankingSelectionEconomicsOrAuthorization() throws Exception
    {
        for (String file : new String[]{"BuyReplacementCandidateFillEvaluation.java",
            "BuyReplacementCandidateFillEvaluationSet.java"})
        {
            String source = java.nio.file.Files.readString(java.nio.file.Path.of(
                "src/main/java/com/flippingfriend/companion/" + file));
            assertFalse(source.contains("selected"));
            assertFalse(source.contains("preferred"));
            assertFalse(source.contains("winner"));
            assertFalse(source.contains("score"));
            assertFalse(source.contains("rank"));
            assertFalse(source.contains("profit"));
            assertFalse(source.contains("tax"));
            assertFalse(source.contains("PolicyDecision"));
            assertFalse(source.contains("OfferLifecycleAction"));
            assertFalse(source.contains("REPLACEMENT_AUTHORIZED"));
        }
    }

    private static BuyReplacementCandidateFillInputContext context()
    {
        int quantity = 5;
        int low = 500;
        long coins = 10_000;
        double[] offsets = {0.0, 0.002, 0.0021};
        List<BuyReplacementPriceCandidate> prices = new ArrayList<>();
        List<BuyReplacementCandidateAffordability> values = new ArrayList<>();
        for (int i = 0; i < offsets.length; i++)
        {
            int price = PriceOffset.apply(low, offsets[i]);
            long cost = (long) price * quantity;
            prices.add(new BuyReplacementPriceCandidate(i, offsets[i], price));
            values.add(new BuyReplacementCandidateAffordability(i, offsets[i], price, quantity,
                cost, BuyReplacementAffordabilityOutcome.AFFORDABLE));
        }
        BuyReplacementPricingContext pricing = new BuyReplacementPricingContext("assessment-1",
            "readiness-1", "intent-1", "offer-1", "plan-1", 2, 4151, "Abyssal whip",
            quantity, 1000, low, 510, quantity, coins, true, 100, 1100, 900, 900, 900, 1000);
        BuyReplacementPricingStrategyContext strategy = new BuyReplacementPricingStrategyContext(
            pricing, "Balanced", offsets, 2.5);
        BuyReplacementPriceCandidateSet candidates = new BuyReplacementPriceCandidateSet(strategy, prices);
        BuyReplacementCandidateAffordabilitySet set =
            new BuyReplacementCandidateAffordabilitySet(candidates, values);
        BuyReplacementCandidateEligibilityAssessment eligible =
            new BuyReplacementCandidateEligibilityEvaluator().evaluate(set);
        List<Candle> history = new ArrayList<>(Arrays.asList(
            new Candle(400, 510, 600, 100, 100),
            new Candle(700, 511, 601, 100, 100)));
        return new BuyReplacementCandidateFillInputContextComposer().compose(
            eligible, history, 1000, 1000);
    }
}
