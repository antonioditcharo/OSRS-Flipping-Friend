package com.flippingfriend.companion;
import com.flippingfriend.core.PortfolioCandidate;
import com.flippingfriend.data.Candle;
import com.flippingfriend.model.PriceOffset;
import com.flippingfriend.model.TaxCalculator;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;
public class BuyReplacementQuantityCandidateSelectorTest {
    private static final int ITEM = 4151;
    private static TaxCalculator margin(long perItem) {
        return new TaxCalculator() {
            @Override public long netMarginPerItem(int item, int buy, int sell) { return perItem; }
            @Override public long netProfit(int item, int buy, int sell, int quantity) { return perItem * quantity; }
        };
    }
    private static BuyReplacementQuantityCandidateSet built(TaxCalculator tax, int live, long observedAt) {
        var assessment = require(new BuyReplacementQuantityLiveLimitRevalidator(tax)
                .revalidate(capacity(tax), ITEM, live, observedAt, 1100));
        return require(new BuyReplacementQuantityCandidateBuilder(tax).build(assessment, 1100));
    }
    @Test public void selectsHighestScoringEligibleCandidateWithOriginalIndex() {
        TaxCalculator tax = margin(50);
        var set = built(tax, 500, 1100);
        assertTrue(set.getCandidates().size() > 1);
        var selection = new BuyReplacementQuantityCandidateSelector(tax).select(set, 0, 1100);
        assertNotNull(selection);
        assertSame(set, selection.getSource());
        assertEquals(BuyReplacementQuantityCandidateSelectionOutcome.SELECTED, selection.getOutcome());
        int expected = -1;
        double top = Double.NEGATIVE_INFINITY;
        for (int k = 0; k < set.getCandidates().size(); k++) {
            double score = set.getCandidates().get(k).expectedGpPerSlotHour();
            assertTrue(score > 0);
            if (score > top) { top = score; expected = k; }
        }
        assertEquals(expected, selection.getSelectedPosition());
        assertSame(set.getCandidates().get(expected), selection.getSelected());
        assertEquals(set.getSourceIndexes().get(expected).intValue(), selection.getSelectedSourceIndex());
        assertEquals(top, selection.getSelectedScore(), 0.0);
        for (PortfolioCandidate c : set.getCandidates())
            assertTrue(c.expectedGpPerSlotHour() <= selection.getSelectedScore());
        assertEquals(0, selection.getBelowMinimumCount() + selection.getNoCapitalCount()
                + selection.getCannotCompleteCount() + selection.getNotWorthDoingCount());
        assertEquals(1150, selection.getExpiresAt());
        assertEquals(1100, selection.getSelectedAt());
    }
    @Test public void losingCandidatesAreNeverSelected() {
        TaxCalculator tax = margin(-5);
        var set = built(tax, 500, 1100);
        var selection = new BuyReplacementQuantityCandidateSelector(tax).select(set, 0, 1100);
        assertNotNull(selection);
        assertEquals(BuyReplacementQuantityCandidateSelectionOutcome.NO_ELIGIBLE_CANDIDATE, selection.getOutcome());
        assertNull(selection.getSelected());
        assertEquals(-1, selection.getSelectedPosition());
        assertEquals(-1, selection.getSelectedSourceIndex());
        assertTrue(Double.isNaN(selection.getSelectedScore()));
        assertEquals(set.getCandidates().size(), selection.getBelowMinimumCount());
    }
    @Test public void minimumProfitFloorFiltersLikeProductionOptimizer() {
        TaxCalculator tax = margin(50);
        var set = built(tax, 500, 1100);
        var selector = new BuyReplacementQuantityCandidateSelector(tax);
        long smallest = Long.MAX_VALUE, largest = Long.MIN_VALUE;
        for (PortfolioCandidate c : set.getCandidates()) {
            smallest = Math.min(smallest, c.getNetProfit());
            largest = Math.max(largest, c.getNetProfit());
        }
        var none = selector.select(set, largest + 1, 1100);
        assertEquals(BuyReplacementQuantityCandidateSelectionOutcome.NO_ELIGIBLE_CANDIDATE, none.getOutcome());
        assertEquals(set.getCandidates().size(), none.getBelowMinimumCount());
        var floor = selector.select(set, largest, 1100);
        assertEquals(BuyReplacementQuantityCandidateSelectionOutcome.SELECTED, floor.getOutcome());
        assertTrue(floor.getSelected().getNetProfit() >= largest);
        int below = 0;
        for (PortfolioCandidate c : set.getCandidates()) if (c.getNetProfit() < largest) below++;
        assertEquals(below, floor.getBelowMinimumCount());
        assertNull(selector.select(set, -1, 1100));
        assertTrue(smallest >= 1);
    }
    @Test public void emptySetIsValidNoEligibleEvidence() {
        TaxCalculator tax = margin(50);
        var set = built(tax, 0, 1100);
        assertTrue(set.getCandidates().isEmpty());
        var selection = new BuyReplacementQuantityCandidateSelector(tax).select(set, 0, 1100);
        assertNotNull(selection);
        assertEquals(BuyReplacementQuantityCandidateSelectionOutcome.NO_ELIGIBLE_CANDIDATE, selection.getOutcome());
        assertEquals(0, selection.getCandidateCount());
    }
    @Test public void selectionIsStrictlyBeforeExpiryAndWithinFreshness() {
        TaxCalculator tax = margin(50);
        var selector = new BuyReplacementQuantityCandidateSelector(tax);
        var set = built(tax, 500, 1100);
        assertNotNull(selector.select(set, 0, 1149));
        assertNull(selector.select(set, 0, 1150));
        assertNull(selector.select(set, 0, 1099));
        assertNull(selector.select(set, 0, Long.MAX_VALUE));
        var early = built(tax, 500, 1000);
        assertNotNull(selector.select(early, 0, 1120));
        assertNull(selector.select(early, 0, 1121));
    }
    @Test public void forgedCandidateSetsFailClosed() {
        TaxCalculator tax = margin(50);
        var selector = new BuyReplacementQuantityCandidateSelector(tax);
        var set = built(tax, 500, 1100);
        var foreign = built(margin(40), 500, 1100);
        List<PortfolioCandidate> reversed = new ArrayList<>(set.getCandidates());
        Collections.reverse(reversed);
        List<PortfolioCandidate> dropped = new ArrayList<>(set.getCandidates());
        List<Integer> droppedIndexes = new ArrayList<>(set.getSourceIndexes());
        dropped.remove(dropped.size() - 1);
        droppedIndexes.remove(droppedIndexes.size() - 1);
        for (var forged : Arrays.asList(
                new BuyReplacementQuantityCandidateSet(set.getSource(), reversed, set.getSourceIndexes(), 1100),
                new BuyReplacementQuantityCandidateSet(set.getSource(), dropped, droppedIndexes, 1100),
                new BuyReplacementQuantityCandidateSet(set.getSource(), foreign.getCandidates(), set.getSourceIndexes(), 1100),
                new BuyReplacementQuantityCandidateSet(set.getSource(), set.getCandidates(), set.getSourceIndexes(), 1099),
                new BuyReplacementQuantityCandidateSet(null, set.getCandidates(), set.getSourceIndexes(), 1100)))
            assertNull(selector.select(forged, 0, 1100));
        assertNull(selector.select(null, 0, 1100));
        assertNull(new BuyReplacementQuantityCandidateSelector(null).select(set, 0, 1100));
        assertNull(new BuyReplacementQuantityCandidateSelector(margin(40)).select(set, 0, 1100));
    }
    @Test public void selectionIsDeterministic() {
        TaxCalculator tax = margin(50);
        var set = built(tax, 500, 1100);
        var selector = new BuyReplacementQuantityCandidateSelector(tax);
        var first = selector.select(set, 0, 1100);
        var second = selector.select(set, 0, 1100);
        assertEquals(first.getSelectedPosition(), second.getSelectedPosition());
        assertSame(first.getSelected(), second.getSelected());
        assertEquals(first.getSelectedScore(), second.getSelectedScore(), 0.0);
    }
    @Test public void contractsArePackagePrivateAndPreferenceOnly() throws Exception {
        for (Class<?> type : new Class<?>[] {BuyReplacementQuantityCandidateSelectionOutcome.class,
                BuyReplacementQuantityCandidateSelection.class, BuyReplacementQuantityCandidateSelector.class}) {
            assertFalse(Modifier.isPublic(type.getModifiers()));
            String text = Files.readString(Path.of("src/main/java/com/flippingfriend/companion/" + type.getSimpleName() + ".java"));
            for (String forbidden : new String[] {"PortfolioOptimizer", "PortfolioPlan", "PortfolioAllocation",
                    "PolicyDecision", "OfferLifecycleAction", "REPLACEMENT_AUTHORIZED", "SqliteStore",
                    "BuyLimitLedger", "Instant.now", "currentTimeMillis", "schedule", "new PortfolioCandidate"})
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
