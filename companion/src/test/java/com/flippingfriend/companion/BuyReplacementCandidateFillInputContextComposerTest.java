package com.flippingfriend.companion;

import com.flippingfriend.data.Candle;
import com.flippingfriend.model.PriceOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

import static org.junit.Assert.*;

public class BuyReplacementCandidateFillInputContextComposerTest
{
    private final BuyReplacementCandidateFillInputContextComposer composer =
        new BuyReplacementCandidateFillInputContextComposer();

    @Test public void eligibleEvidenceAndHistoryProduceImmutableInputs()
    {
        List<Candle> history = history();
        BuyReplacementCandidateFillInputContext result = composer.compose(eligible(), history, 1000, 1000);
        assertNotNull(result);
        assertEquals(2, result.getAssessments().size());
        assertEquals(2, result.getAffordableCandidates().size());
        assertEquals(0, result.getAffordableCandidates().get(0).getOffsetIndex());
        assertEquals(1, result.getAffordableCandidates().get(1).getOffsetIndex());
        assertFalse(result.getFillCurve().isEmpty());
        assertEquals(2.5, result.getEffectiveHorizonHours(), 0.0);
        history.get(0).setTimestamp(5000);
        assertEquals(300, result.getFillCurve().getWindowSeconds());
        try { result.getAffordableCandidates().clear(); fail("affordable inputs must be immutable"); }
        catch (UnsupportedOperationException expected) { }
    }

    @Test public void unavailableIneligibleStaleAndMalformedInputsFailClosed()
    {
        assertNull(composer.compose(null, history(), 1000, 1000));
        BuyReplacementCandidateEligibilityAssessment eligible = eligible();
        assertNull(composer.compose(eligible, null, 1000, 1000));
        assertNull(composer.compose(eligible, new ArrayList<>(), 1000, 1000));
        assertNull(composer.compose(eligible, history(), 1000, 1121));
        assertNull(composer.compose(eligible, history(), 1001, 1000));
        List<Candle> future = history();
        future.get(1).setTimestamp(1001);
        assertNull(composer.compose(eligible, future, 1000, 1000));
    }

    @Test public void contextCarriesNoEstimateRankingSelectionOrAuthorization() throws Exception
    {
        String source = java.nio.file.Files.readString(java.nio.file.Path.of(
            "src/main/java/com/flippingfriend/companion/BuyReplacementCandidateFillInputContext.java"));
        assertFalse(source.contains("FillEstimate"));
        assertFalse(source.contains("FillModel"));
        assertFalse(source.contains("selected"));
        assertFalse(source.contains("score"));
        assertFalse(source.contains("rank"));
        assertFalse(source.contains("PolicyDecision"));
        assertFalse(source.contains("OfferLifecycleAction"));
        assertFalse(source.contains("REPLACEMENT_AUTHORIZED"));
    }

    private static BuyReplacementCandidateEligibilityAssessment eligible()
    {
        int quantity = 10;
        int low = 990;
        long coins = 20_000;
        double[] offsets = {0.0, 0.01};
        List<BuyReplacementCandidateAffordability> values = new ArrayList<>();
        List<BuyReplacementPriceCandidate> prices = new ArrayList<>();
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
            quantity, 1000, low, low + 1, quantity, coins, true, 100, 1100, 900, 900, 900, 1000);
        BuyReplacementPricingStrategyContext strategy = new BuyReplacementPricingStrategyContext(
            pricing, "Balanced", offsets, 2.5);
        BuyReplacementPriceCandidateSet candidates = new BuyReplacementPriceCandidateSet(strategy, prices);
        BuyReplacementCandidateAffordabilitySet set =
            new BuyReplacementCandidateAffordabilitySet(candidates, values);
        return new BuyReplacementCandidateEligibilityEvaluator().evaluate(set);
    }

    private static List<Candle> history()
    {
        return new ArrayList<>(Arrays.asList(
            new Candle(400, 1000, 990, 20, 20),
            new Candle(700, 1001, 991, 20, 20)));
    }
}
