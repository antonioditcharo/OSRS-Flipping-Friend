package com.flippingfriend.companion;

import java.util.Arrays;
import org.junit.Test;
import static org.junit.Assert.*;

public class BuyReplacementCandidateFillViabilityEvaluatorTest
{
    @Test public void plausibilityContractDerivesOrderedViableView()
    {
        BuyReplacementCandidateFillEvaluationSet source = source(
            evaluation(0, 500, 0.0, Double.POSITIVE_INFINITY, 0, Double.POSITIVE_INFINITY),
            evaluation(1, 501, 0.6, 1.2, 100, 0.2));
        BuyReplacementCandidateFillViabilityAssessment result =
            new BuyReplacementCandidateFillViabilityEvaluator().evaluate(source, 1000);
        assertNotNull(result);
        assertEquals(BuyReplacementCandidateFillViabilityOutcome.VIABLE, result.getOutcome());
        assertEquals(2, result.getEvaluations().size());
        assertEquals(1, result.getViableCandidates().size());
        assertEquals(1, result.getViableCandidates().get(0).getOffsetIndex());
        try { result.getViableCandidates().clear(); fail("viable view must be immutable"); }
        catch (UnsupportedOperationException expected) { }
    }

    @Test public void validAllNeverFillEvidenceIsNotViable()
    {
        BuyReplacementCandidateFillViabilityAssessment result =
            new BuyReplacementCandidateFillViabilityEvaluator().evaluate(source(
                evaluation(0, 500, 0.0, Double.POSITIVE_INFINITY, 0,
                    Double.POSITIVE_INFINITY)), 1000);
        assertNotNull(result);
        assertEquals(BuyReplacementCandidateFillViabilityOutcome.NOT_VIABLE, result.getOutcome());
        assertTrue(result.getViableCandidates().isEmpty());
    }

    @Test public void staleMissingAndMalformedEvidenceFailsClosed()
    {
        BuyReplacementCandidateFillViabilityEvaluator evaluator =
            new BuyReplacementCandidateFillViabilityEvaluator();
        assertNull(evaluator.evaluate(null, 1000));
        assertNull(evaluator.evaluate(source(evaluation(0, 500, Double.NaN, 1, 1, 1)), 1000));
        assertNull(evaluator.evaluate(source(evaluation(0, 500, 0.5, 1, 1, 1)), 1121));
    }

    private static BuyReplacementCandidateFillEvaluation evaluation(int index, int price,
        double probability, double hours, double throughput, double wait)
    {
        return new BuyReplacementCandidateFillEvaluation(index, index == 0 ? 0.0 : 0.002,
            price, 5, (long) price * 5, probability, hours, throughput, wait);
    }

    private static BuyReplacementCandidateFillEvaluationSet source(
        BuyReplacementCandidateFillEvaluation... values)
    {
        java.util.List<BuyReplacementAffordableCandidate> candidates = new java.util.ArrayList<>();
        for (BuyReplacementCandidateFillEvaluation value : values)
            candidates.add(new BuyReplacementAffordableCandidate(value.getOffsetIndex(),
                value.getOffset(), value.getBuyPrice(), value.getExactRemainderQuantity(),
                value.getTotalCost()));
        BuyReplacementCandidateFillInputContext context =
            BuyReplacementCandidateFillEvaluatorTest.contextForCompanionTests();
        return new BuyReplacementCandidateFillEvaluationSet(context,
            context.getAssessments(), candidates, Arrays.asList(values), 1000);
    }
}
