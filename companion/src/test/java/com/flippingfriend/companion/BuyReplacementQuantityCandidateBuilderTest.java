package com.flippingfriend.companion;
import com.flippingfriend.core.PortfolioCandidate;
import com.flippingfriend.data.Candle;
import com.flippingfriend.model.ItemGroups;
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
public class BuyReplacementQuantityCandidateBuilderTest {
    private static final int ITEM = 4151;
    private final TaxCalculator tax = new TaxCalculator();
    private final BuyReplacementQuantityCandidateBuilder builder = new BuyReplacementQuantityCandidateBuilder(tax);
    private BuyReplacementQuantityLiveLimitAssessment assess(TaxCalculator calculator, int live) {
        var result = new BuyReplacementQuantityLiveLimitRevalidator(calculator)
                .revalidate(capacity(calculator), ITEM, live, 1100, 1100);
        assertNotNull(result);
        return result;
    }
    private static void assertMatches(PortfolioCandidate c, BuyReplacementQuantityCandidateEconomicInput input,
            int limit) {
        assertEquals(ITEM, c.getItemId());
        assertEquals("Abyssal whip", c.getItemName());
        assertEquals(ItemGroups.groupOf("Abyssal whip"), c.getGroup());
        assertEquals(input.getTargetBuyPrice(), c.getTargetBuyPrice());
        assertEquals(input.getTargetBuyPrice(), c.getEquilibriumBuyPrice());
        assertEquals(input.getTargetBuyPrice(), c.getExitBuyPrice());
        assertEquals(input.getTargetSellPrice(), c.getTargetSellPrice());
        assertEquals(input.getTargetSellPrice(), c.getEquilibriumSellPrice());
        assertEquals(input.getTargetSellPrice(), c.getExitSellPrice());
        assertEquals(input.getQuantity(), c.getQuantity());
        assertEquals(limit, c.getBuyLimitRemaining());
        assertEquals((long) input.getTargetBuyPrice() * input.getQuantity(), c.getCapitalRequired());
        assertEquals(input.getNetProfit(), c.getNetProfit());
        assertEquals(input.getWorstLoss(), c.getWorstLoss());
        assertEquals(input.getUnwindLoss(), c.getUnwindLoss());
        assertEquals(input.getBuyFillProbability(), c.getBuyFillProbability(), 0.0);
        assertEquals(input.getSellFillProbability(), c.getSellFillProbability(), 0.0);
        assertEquals(input.getBuyHours(), c.getBuyHours(), 0.0);
        assertEquals(input.getSellHours(), c.getSellHours(), 0.0);
        assertEquals(input.getHorizonHours(), c.getHorizonHours(), 0.0);
        assertEquals(1150, c.getExpiresAt());
        assertEquals(c.getCompletionProbability(), c.getDisplayCompletionProbability(), 0.0);
        assertEquals(input.getExpectedProfit(), c.expectedProfit(), 1e-6);
        assertEquals(input.getExpectedSlotHours(), c.expectedSlotHours(), 1e-9);
        assertEquals(input.getExpectedGpPerSlotHour(), c.expectedGpPerSlotHour(), 1e-6);
    }
    @Test public void buildsEveryCoveredQuantityWithEffectiveLimitAndUnchangedExpiry() {
        var assessment = assess(tax, 500);
        var set = builder.build(assessment, 1100);
        assertNotNull(set);
        assertSame(assessment, set.getSource());
        assertEquals(assessment.getInputs().size(), set.getCandidates().size());
        assertEquals(assessment.getCoveredIndexes(), set.getSourceIndexes());
        for (int k = 0; k < set.getCandidates().size(); k++)
            assertMatches(set.getCandidates().get(k), assessment.getCoveredInputs().get(k), 50);
        assertEquals(50, set.getEffectiveBuyLimitRemaining());
        assertEquals(5, set.getExactRemainderQuantity());
        assertEquals(1150, set.getExpiresAt());
        assertEquals(1100, set.getBuiltAt());
    }
    @Test public void partialCoverageBuildsOnlyCoveredQuantitiesWithOriginalIndexes() {
        int min = Integer.MAX_VALUE;
        for (var input : capacity(tax).getInputs()) min = Math.min(min, input.getQuantity());
        var assessment = assess(tax, min);
        assertTrue(assessment.getCoveredInputs().size() < assessment.getInputs().size());
        var set = builder.build(assessment, 1100);
        assertNotNull(set);
        assertEquals(assessment.getCoveredIndexes(), set.getSourceIndexes());
        assertEquals(assessment.getCoveredInputs().size(), set.getCandidates().size());
        for (int k = 0; k < set.getCandidates().size(); k++) {
            assertMatches(set.getCandidates().get(k), assessment.getCoveredInputs().get(k), min);
            assertTrue(set.getCandidates().get(k).getQuantity() <= min);
        }
    }
    @Test public void zeroCoverageYieldsValidEmptySet() {
        var set = builder.build(assess(tax, 0), 1100);
        assertNotNull(set);
        assertEquals(BuyReplacementQuantityLiveLimitOutcome.NOT_COVERED, set.getOutcome());
        assertTrue(set.getCandidates().isEmpty());
        assertTrue(set.getSourceIndexes().isEmpty());
    }
    @Test public void buildIsBoundedByAssessmentTimeExpiryAndFreshness() {
        var assessment = assess(tax, 500);
        assertNotNull(builder.build(assessment, 1150));
        assertNull(builder.build(assessment, 1151));
        assertNull(builder.build(assessment, 1099));
        assertNull(builder.build(assessment, Long.MAX_VALUE));
        var early = new BuyReplacementQuantityLiveLimitRevalidator(tax)
                .revalidate(capacity(tax), ITEM, 500, 1000, 1100);
        assertNotNull(early);
        assertNotNull(builder.build(early, 1120));
        assertNull(builder.build(early, 1121));
    }
    @Test public void forgedAssessmentsFailClosed() {
        var a = assess(tax, 500);
        List<Integer> dropped = new ArrayList<>(a.getCoveredIndexes());
        List<BuyReplacementQuantityCandidateEconomicInput> droppedInputs = new ArrayList<>(a.getCoveredInputs());
        dropped.remove(dropped.size() - 1);
        droppedInputs.remove(droppedInputs.size() - 1);
        var foreign = assess(tax, 500);
        for (var forged : Arrays.asList(
                new BuyReplacementQuantityLiveLimitAssessment(a.getSource(), a.getOutcome(), 500, 500,
                        1100, a.getCoveredIndexes(), a.getCoveredInputs(), 1100),
                new BuyReplacementQuantityLiveLimitAssessment(a.getSource(), BuyReplacementQuantityLiveLimitOutcome.NOT_COVERED,
                        500, 50, 1100, a.getCoveredIndexes(), a.getCoveredInputs(), 1100),
                new BuyReplacementQuantityLiveLimitAssessment(a.getSource(), a.getOutcome(), 500, 50,
                        1100, dropped, droppedInputs, 1100),
                new BuyReplacementQuantityLiveLimitAssessment(foreign.getSource(), a.getOutcome(), 500, 50,
                        1100, a.getCoveredIndexes(), a.getCoveredInputs(), 1100),
                new BuyReplacementQuantityLiveLimitAssessment(a.getSource(), a.getOutcome(), 500, 50,
                        1101, a.getCoveredIndexes(), a.getCoveredInputs(), 1100),
                new BuyReplacementQuantityLiveLimitAssessment(null, a.getOutcome(), 500, 50,
                        1100, a.getCoveredIndexes(), a.getCoveredInputs(), 1100)))
            assertNull(builder.build(forged, 1100));
        assertNull(builder.build(null, 1100));
        assertNull(new BuyReplacementQuantityCandidateBuilder(null).build(a, 1100));
    }
    @Test public void negativeEconomicsRemainEvidenceAndOutputsAreImmutable() {
        TaxCalculator negative = new TaxCalculator() {
            @Override public long netMarginPerItem(int item, int buy, int sell) { return -5; }
            @Override public long netProfit(int item, int buy, int sell, int quantity) { return -5L * quantity; }
        };
        var assessment = assess(negative, 500);
        var set = new BuyReplacementQuantityCandidateBuilder(negative).build(assessment, 1100);
        assertNotNull(set);
        assertFalse(set.getCandidates().isEmpty());
        for (int k = 0; k < set.getCandidates().size(); k++) {
            var c = set.getCandidates().get(k);
            assertTrue(c.getNetProfit() < 0);
            assertTrue(c.expectedProfit() < 0);
            assertTrue(c.expectedGpPerSlotHour() < 0);
            assertMatches(c, assessment.getCoveredInputs().get(k), 50);
        }
        try { set.getCandidates().clear(); fail("immutable"); }
        catch (UnsupportedOperationException expected) { }
        try { set.getSourceIndexes().clear(); fail("immutable"); }
        catch (UnsupportedOperationException expected) { }
        var again = new BuyReplacementQuantityCandidateBuilder(negative).build(assessment, 1100);
        assertEquals(set.getSourceIndexes(), again.getSourceIndexes());
        assertEquals(set.getCandidates().size(), again.getCandidates().size());
    }
    @Test public void contractsArePackagePrivateAndBuildOnly() throws Exception {
        for (Class<?> type : new Class<?>[] {BuyReplacementQuantityCandidateSet.class,
                BuyReplacementQuantityCandidateBuilder.class}) {
            assertFalse(Modifier.isPublic(type.getModifiers()));
            String text = Files.readString(Path.of("src/main/java/com/flippingfriend/companion/" + type.getSimpleName() + ".java"));
            for (String forbidden : new String[] {"PortfolioOptimizer", "PortfolioPlan", "PolicyDecision",
                    "OfferLifecycleAction", "REPLACEMENT_AUTHORIZED", "SqliteStore", "BuyLimitLedger",
                    "Instant.now", "currentTimeMillis", "schedule", "Comparator", ".sort("})
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
