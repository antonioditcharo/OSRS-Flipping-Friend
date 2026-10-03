package com.flippingfriend.companion;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

import static org.junit.Assert.*;

public class BuyReplacementCandidateAffordabilityComposerTest
{
    private final BuyReplacementCandidateAffordabilityComposer composer =
        new BuyReplacementCandidateAffordabilityComposer();

    @Test public void classifiesMixedCandidatesWithoutFilteringOrResizing()
    {
        BuyReplacementCandidateAffordabilitySet result = composer.compose(set(400, 396_500,
            candidates(990, new double[]{0.0, 0.002, 0.006})));
        assertNotNull(result);
        assertEquals(3, result.getAssessments().size());
        assertAssessment(result, 0, 990, 396_000L, BuyReplacementAffordabilityOutcome.AFFORDABLE);
        assertAssessment(result, 1, 992, 396_800L, BuyReplacementAffordabilityOutcome.UNAFFORDABLE);
        assertAssessment(result, 2, 996, 398_400L, BuyReplacementAffordabilityOutcome.UNAFFORDABLE);
    }

    @Test public void exactBalanceIsAffordableAndLargeProductsRemainExact()
    {
        BuyReplacementCandidateAffordabilitySet exact = composer.compose(set(400, 396_000,
            candidates(990, new double[]{0.0})));
        assertAssessment(exact, 0, 990, 396_000L, BuyReplacementAffordabilityOutcome.AFFORDABLE);
        int price = 2_000_000_000;
        int quantity = Integer.MAX_VALUE;
        BuyReplacementCandidateAffordabilitySet large = composer.compose(setAtLow(quantity,
            Long.MAX_VALUE, price, Collections.singletonList(
                new BuyReplacementPriceCandidate(0, 0.0, price))));
        assertEquals((long) price * quantity, large.getAssessments().get(0).getTotalCost());
    }

    @Test public void duplicatesOrderAndProvenanceArePreserved()
    {
        BuyReplacementCandidateAffordabilitySet result = composer.compose(setAtLow(10, 100, 5,
            candidates(5, new double[]{0.0, 0.002, 0.006, 0.012})));
        assertEquals(4, result.getAssessments().size());
        assertEquals(50, result.getAssessments().get(0).getTotalCost());
        for (int i = 1; i < 4; i++)
        {
            assertEquals(i, result.getAssessments().get(i).getOffsetIndex());
            assertEquals(6, result.getAssessments().get(i).getBuyPrice());
            assertEquals(60, result.getAssessments().get(i).getTotalCost());
        }
    }

    @Test public void resultCollectionIsUnmodifiableAndDeterministic()
    {
        BuyReplacementCandidateAffordabilitySet first = composer.compose(set(10, 20_000,
            candidates(990, new double[]{0.0, 0.01})));
        BuyReplacementCandidateAffordabilitySet second = composer.compose(set(10, 20_000,
            candidates(990, new double[]{0.0, 0.01})));
        try
        {
            first.getAssessments().clear();
            fail("assessment collection must be unmodifiable");
        }
        catch (UnsupportedOperationException expected)
        {
        }
        assertEquals(second.getAssessments().get(1).getTotalCost(),
            first.getAssessments().get(1).getTotalCost());
    }

    @Test public void malformedCandidateEvidenceFailsClosed()
    {
        assertNull(composer.compose(null));
        assertNull(composer.compose(set(10, 20_000, Collections.emptyList())));
        assertNull(composer.compose(set(10, 20_000, Arrays.asList(
            new BuyReplacementPriceCandidate(0, 0.0, 990), null))));
        assertNull(composer.compose(set(10, 20_000, Collections.singletonList(
            new BuyReplacementPriceCandidate(1, 0.0, 990)))));
        assertNull(composer.compose(set(10, 20_000, Collections.singletonList(
            new BuyReplacementPriceCandidate(0, 0.002, 999)))));
    }

    @Test public void affordabilityContractsCarryNoSelectionSizingOrAuthorization() throws Exception
    {
        String source = java.nio.file.Files.readString(java.nio.file.Path.of(
            "src/main/java/com/flippingfriend/companion/BuyReplacementCandidateAffordabilitySet.java"));
        assertFalse(source.contains("selected"));
        assertFalse(source.contains("preferred"));
        assertFalse(source.contains("winner"));
        assertFalse(source.contains("FillModel"));
        assertFalse(source.contains("PortfolioCandidate"));
        assertFalse(source.contains("PolicyDecision"));
        assertFalse(source.contains("OfferLifecycleAction"));
        assertFalse(source.contains("REPLACEMENT_AUTHORIZED"));
    }

    private static void assertAssessment(BuyReplacementCandidateAffordabilitySet set, int index,
        int price, long cost, BuyReplacementAffordabilityOutcome outcome)
    {
        BuyReplacementCandidateAffordability value = set.getAssessments().get(index);
        assertEquals(index, value.getOffsetIndex());
        assertEquals(price, value.getBuyPrice());
        assertEquals(set.getExactRemainderQuantity(), value.getExactRemainderQuantity());
        assertEquals(cost, value.getTotalCost());
        assertEquals(outcome, value.getOutcome());
    }

    private static List<BuyReplacementPriceCandidate> candidates(int low, double[] offsets)
    {
        List<BuyReplacementPriceCandidate> result = new ArrayList<>();
        for (int i = 0; i < offsets.length; i++) result.add(new BuyReplacementPriceCandidate(i,
            offsets[i], com.flippingfriend.model.PriceOffset.apply(low, offsets[i])));
        return result;
    }

    private static BuyReplacementPriceCandidateSet set(int quantity, long coins,
        List<BuyReplacementPriceCandidate> candidates)
    {
        return setAtLow(quantity, coins, 990, candidates);
    }

    private static BuyReplacementPriceCandidateSet setAtLow(int quantity, long coins, int low,
        List<BuyReplacementPriceCandidate> candidates)
    {
        BuyReplacementPricingContext pricing = new BuyReplacementPricingContext("assessment-1",
            "readiness-1", "intent-1", "offer-1", "plan-1", 2, 4151,
            "Abyssal whip", quantity, 1000, low, low == Integer.MAX_VALUE ? Integer.MAX_VALUE : low + 1,
            quantity, coins, true, 100, 1100, 900, 900, 900, 1000);
        BuyReplacementPricingStrategyContext strategy = new BuyReplacementPricingStrategyContext(
            pricing, "Balanced", new double[]{0.0}, 2.5);
        return new BuyReplacementPriceCandidateSet(strategy, candidates);
    }
}
