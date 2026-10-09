package com.flippingfriend.companion;
import com.flippingfriend.data.Candle;
import com.flippingfriend.model.PriceOffset;
import com.flippingfriend.model.TaxCalculator;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;
public class BuyReplacementQuantityLiveLimitRevalidatorTest {
    private final TaxCalculator tax = new TaxCalculator();
    private final BuyReplacementQuantityLiveLimitRevalidator revalidator =
            new BuyReplacementQuantityLiveLimitRevalidator(tax);
    @Test public void higherLiveLimitCoversEveryQuantityWithoutWideningRetainedLimit() {
        var capacity = capacity(tax);
        var result = revalidator.revalidate(capacity, 4151, 500, 1100, 1100);
        assertNotNull(result);
        assertSame(capacity, result.getSource());
        assertSame(capacity.getInputs(), result.getInputs());
        assertEquals(BuyReplacementQuantityLiveLimitOutcome.COVERED, result.getOutcome());
        assertEquals(50, result.getRetainedBuyLimitRemaining());
        assertEquals(500, result.getLiveBuyLimitRemaining());
        assertEquals(50, result.getEffectiveBuyLimitRemaining());
        assertEquals(capacity.getInputs().size(), result.getCoveredInputs().size());
        for (int i = 0; i < capacity.getInputs().size(); i++) {
            assertEquals(Integer.valueOf(i), result.getCoveredIndexes().get(i));
            assertSame(capacity.getInputs().get(i), result.getCoveredInputs().get(i));
        }
        assertEquals(5, result.getExactRemainderQuantity());
        assertEquals(1150, result.getExpiresAt());
        assertEquals(1100, result.getEvaluatedAt());
    }
    @Test public void shrunkLiveLimitPreservesOrderedMixedCoverage() {
        var capacity = capacity(tax);
        int min = Integer.MAX_VALUE, max = 0;
        for (var input : capacity.getInputs()) {
            min = Math.min(min, input.getQuantity());
            max = Math.max(max, input.getQuantity());
        }
        assertTrue(min < max);
        var result = revalidator.revalidate(capacity, 4151, min, 1100, 1100);
        assertNotNull(result);
        assertEquals(BuyReplacementQuantityLiveLimitOutcome.COVERED, result.getOutcome());
        assertEquals(min, result.getEffectiveBuyLimitRemaining());
        List<Integer> expected = new ArrayList<>();
        for (int i = 0; i < capacity.getInputs().size(); i++)
            if (capacity.getInputs().get(i).getQuantity() <= min) expected.add(i);
        assertEquals(expected, result.getCoveredIndexes());
        assertTrue(result.getCoveredInputs().size() < capacity.getInputs().size());
        for (int k = 0; k < expected.size(); k++)
            assertSame(capacity.getInputs().get(expected.get(k)), result.getCoveredInputs().get(k));
        assertEquals(5, result.getExactRemainderQuantity());
    }
    @Test public void zeroLiveLimitIsValidNotCoveredEvidence() {
        var capacity = capacity(tax);
        var result = revalidator.revalidate(capacity, 4151, 0, 1100, 1100);
        assertNotNull(result);
        assertEquals(BuyReplacementQuantityLiveLimitOutcome.NOT_COVERED, result.getOutcome());
        assertTrue(result.getCoveredInputs().isEmpty());
        assertTrue(result.getCoveredIndexes().isEmpty());
        assertEquals(capacity.getInputs().size(), result.getInputs().size());
    }
    @Test public void mismatchedNegativeStaleFutureAndPreEligibilityObservationsFailClosed() {
        var capacity = capacity(tax);
        assertNull(revalidator.revalidate(capacity, 4152, 50, 1100, 1100));
        assertNull(revalidator.revalidate(capacity, 0, 50, 1100, 1100));
        assertNull(revalidator.revalidate(capacity, 4151, -1, 1100, 1100));
        assertNull(revalidator.revalidate(capacity, 4151, 50, 1101, 1100));
        assertNull(revalidator.revalidate(capacity, 4151, 50, 999, 1100));
        assertNull(revalidator.revalidate(capacity, 4151, 50, 1029, 1150));
        assertNotNull(revalidator.revalidate(capacity, 4151, 50, 1030, 1150));
    }
    @Test public void expiryIsInclusiveAndLaterCapacityWrapperCannotExtendIt() {
        var capacity = capacity(tax);
        assertNotNull(revalidator.revalidate(capacity, 4151, 50, 1150, 1150));
        assertNull(revalidator.revalidate(capacity, 4151, 50, 1151, 1151));
        assertNull(revalidator.revalidate(capacity, 4151, 50, 1099, 1099));
        assertNull(revalidator.revalidate(capacity, 4151, 50, 1100, Long.MAX_VALUE));
        var later = new BuyReplacementQuantityCandidateCapacityInputContext(capacity.getSource(),
                capacity.getBuyLimitRemaining(), capacity.getExpiresAt(), 1140);
        assertNotNull(revalidator.revalidate(later, 4151, 50, 1150, 1150));
        assertNull(revalidator.revalidate(later, 4151, 50, 1151, 1151));
    }
    @Test public void forgedRetainedLimitExpiryAndMissingSourceFailClosed() {
        var capacity = capacity(tax);
        for (var forged : Arrays.asList(
                new BuyReplacementQuantityCandidateCapacityInputContext(capacity.getSource(), 49, 1150, 1100),
                new BuyReplacementQuantityCandidateCapacityInputContext(capacity.getSource(), 500, 1150, 1100),
                new BuyReplacementQuantityCandidateCapacityInputContext(capacity.getSource(), 50, 1200, 1100),
                new BuyReplacementQuantityCandidateCapacityInputContext(null, 50, 1150, 1100)))
            assertNull(revalidator.revalidate(forged, 4151, 50, 1100, 1100));
        assertNull(revalidator.revalidate(null, 4151, 50, 1100, 1100));
        assertNull(new BuyReplacementQuantityLiveLimitRevalidator(null).revalidate(capacity, 4151, 50, 1100, 1100));
    }
    @Test public void negativeEconomicsRemainEvidenceAndOutputsAreImmutable() {
        TaxCalculator negative = new TaxCalculator() {
            @Override public long netMarginPerItem(int item, int buy, int sell) { return -5; }
            @Override public long netProfit(int item, int buy, int sell, int quantity) { return -5L * quantity; }
        };
        var result = new BuyReplacementQuantityLiveLimitRevalidator(negative)
                .revalidate(capacity(negative), 4151, 50, 1100, 1100);
        assertNotNull(result);
        assertFalse(result.getCoveredInputs().isEmpty());
        for (var input : result.getCoveredInputs()) {
            assertTrue(input.getExpectedProfit() < 0);
            assertTrue(input.getExpectedGpPerSlotHour() < 0);
        }
        try { result.getCoveredInputs().clear(); fail("immutable"); }
        catch (UnsupportedOperationException expected) { }
        try { result.getCoveredIndexes().clear(); fail("immutable"); }
        catch (UnsupportedOperationException expected) { }
    }
    @Test public void contractsArePackagePrivateAndOutsideAuthority() throws Exception {
        for (Class<?> type : new Class<?>[] {BuyReplacementQuantityLiveLimitOutcome.class,
                BuyReplacementQuantityLiveLimitAssessment.class,
                BuyReplacementQuantityLiveLimitRevalidator.class}) {
            assertFalse(Modifier.isPublic(type.getModifiers()));
            String text = Files.readString(Path.of("src/main/java/com/flippingfriend/companion/" + type.getSimpleName() + ".java"));
            for (String forbidden : new String[] {"new PortfolioCandidate", "PortfolioOptimizer", "PolicyDecision",
                    "OfferLifecycleAction", "REPLACEMENT_AUTHORIZED", "SqliteStore", "BuyLimitLedger",
                    "Instant.now", "schedule", "Comparator"})
                assertFalse(forbidden, text.contains(forbidden));
        }
    }
    // Dedicated coherent single-context chain; synthetic fill measurements are explicit test evidence.
    private static BuyReplacementQuantityCandidateCapacityInputContext capacity(TaxCalculator tax) {
        var input = require(new BuyReplacementQuantityFillInputContextComposer().compose(
                BuyReplacementQuantityGridComposerTest.quantityFillCandidateSetForCompanionTests(),
                require(metadata()), 1, 1100, 1100));
        List<BuyReplacementQuantityFillEvaluation> measurements = new ArrayList<>();
        for (var x : input.getInputs()) measurements.add(new BuyReplacementQuantityFillEvaluation(
                x, 0.6, 1.2, 100, 0.2, 0.5, 1.4, 90, 0.3));
        var fills = new BuyReplacementQuantityFillEvaluationSet(input, measurements, 1100);
        var viable = require(new BuyReplacementQuantityFillViabilityEvaluator().evaluate(fills, 1100));
        var complete = require(new BuyReplacementQuantityRoundTripCompletionEvaluator().evaluate(viable, 1100));
        var calibration = require(new BuyReplacementQuantityDurationCalibrationInputContextComposer()
                .compose(complete, FillCalibration.NEUTRAL, false, 1100));
        var durations = require(new BuyReplacementQuantityCalibratedDurationEvaluator().evaluate(calibration, 1100));
        var slots = require(new BuyReplacementQuantityExpectedSlotOccupancyEvaluator().evaluate(durations, 1100));
        var unwindInput = new BuyReplacementQuantityUnwindLossInputContext(slots, 990, 0, 300, 1100, 1100);
        var unwind = require(new BuyReplacementQuantityUnwindLossEvaluator(tax).evaluate(unwindInput, 1100));
        var profit = require(new BuyReplacementQuantityExpectedProfitEvaluator(tax).evaluate(unwind, 1100));
        var risk = require(new BuyReplacementQuantityWorstLossInputContextComposer(tax).compose(profit, 1100));
        var worst = require(new BuyReplacementQuantityWorstLossEvaluator(tax).evaluate(risk, 1100));
        var sizing = require(new BuyReplacementQuantityKellySizingInputContextComposer(tax).compose(worst, 1100));
        var fraction = require(new BuyReplacementQuantityKellyFractionEvaluator(tax).evaluate(sizing, 1100));
        var rate = require(new BuyReplacementQuantityExpectedGpPerSlotHourEvaluator(tax).evaluate(fraction, 1100));
        var economic = require(new BuyReplacementQuantityCandidateEconomicInputComposer(tax).compose(rate, 1100));
        var meta = require(new BuyReplacementQuantityCandidateMetadataInputComposer(tax).compose(economic, 1100));
        return require(new BuyReplacementQuantityCandidateCapacityInputComposer(tax).compose(meta, 1100));
    }
    private static <T> T require(T value) { assertNotNull(value); return value; }
    private static BuyReplacementCandidateFillInputContext metadata() {
        int quantity = 5, low = 990;
        double[] offsets = {0, 0.01};
        List<BuyReplacementPriceCandidate> prices = new ArrayList<>();
        List<BuyReplacementCandidateAffordability> costs = new ArrayList<>();
        for (int i = 0; i < offsets.length; i++) {
            int price = PriceOffset.apply(low, offsets[i]);
            prices.add(new BuyReplacementPriceCandidate(i, offsets[i], price));
            costs.add(new BuyReplacementCandidateAffordability(i, offsets[i], price, quantity,
                    (long) price * quantity, BuyReplacementAffordabilityOutcome.AFFORDABLE));
        }
        var pricing = new BuyReplacementPricingContext("assessment-1", "readiness-1", "intent-1",
                "offer-1", "plan-1", 2, 4151, "Abyssal whip", quantity, 1000, low, low + 1, 50,
                20000, true, 100, 5000, 900, 900, 900, 1000);
        var strategy = new BuyReplacementPricingStrategyContext(pricing, "Balanced", offsets, 2.5);
        var candidates = new BuyReplacementPriceCandidateSet(strategy, prices);
        var affordability = new BuyReplacementCandidateAffordabilitySet(candidates, costs);
        var eligible = new BuyReplacementCandidateEligibilityEvaluator().evaluate(affordability);
        if (eligible == null) return null;
        return new BuyReplacementCandidateFillInputContextComposer().compose(eligible,
                Arrays.asList(new Candle(400, 1000, 990, 20, 20), new Candle(700, 1001, 991, 20, 20)), 1000, 1000);
    }
}
