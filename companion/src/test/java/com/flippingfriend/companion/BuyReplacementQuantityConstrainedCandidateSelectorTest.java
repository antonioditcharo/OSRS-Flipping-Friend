package com.flippingfriend.companion;
import com.flippingfriend.core.PortfolioCandidate;
import com.flippingfriend.core.PortfolioConstraints;
import com.flippingfriend.core.PortfolioOptimizer;
import com.flippingfriend.core.PortfolioPlan;
import com.flippingfriend.data.Candle;
import com.flippingfriend.model.PriceOffset;
import com.flippingfriend.model.TaxCalculator;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import static org.junit.Assert.*;
public class BuyReplacementQuantityConstrainedCandidateSelectorTest {
    private static final int ITEM = 4151;
    private static final long BIG = 1_000_000_000L;
    private static TaxCalculator margin(long perItem) {
        return new TaxCalculator() {
            @Override public long netMarginPerItem(int item, int buy, int sell) { return perItem; }
            @Override public long netProfit(int item, int buy, int sell, int quantity) { return perItem * quantity; }
        };
    }
    private static BuyReplacementQuantityCandidateSet built(TaxCalculator tax, int live) {
        var assessment = require(new BuyReplacementQuantityLiveLimitRevalidator(tax)
                .revalidate(capacity(tax), ITEM, live, 1100, 1100));
        return require(new BuyReplacementQuantityCandidateBuilder(tax).build(assessment, 1100));
    }
    private static PortfolioConstraints limits(int slots, long coins, long loss, long itemCap, long groupCap,
            long committedItem, String group, long committedGroup, long minProfit) {
        Map<Integer, Long> byItem = new HashMap<>();
        if (committedItem > 0) byItem.put(ITEM, committedItem);
        Map<String, Long> byGroup = new HashMap<>();
        if (committedGroup > 0) byGroup.put(group, committedGroup);
        return new PortfolioConstraints(slots, coins, loss, itemCap, groupCap, byItem, byGroup, minProfit);
    }
    private static PortfolioConstraints coins(long coins) { return limits(1, coins, BIG, 0, 0, 0, "", 0, 0); }
    /** Runs the real production optimizer on the same candidates and limits and requires the same answer. */
    private static BuyReplacementQuantityConstrainedSelection parity(BuyReplacementQuantityCandidateSet set,
            PortfolioConstraints c, TaxCalculator tax) {
        var ours = new BuyReplacementQuantityConstrainedCandidateSelector(tax).select(set, c, 1100);
        assertNotNull(ours);
        PortfolioPlan plan = new PortfolioOptimizer().optimize(new ArrayList<>(set.getCandidates()), c, "parity", 1100);
        assertNotNull(plan);
        String expected = ours.getOutcome() == BuyReplacementQuantityConstrainedSelectionOutcome.SELECTED
                ? "READY" : ours.getOutcome().name();
        assertEquals(expected, plan.getOutcome().name());
        var allocations = plan.getAllocations();
        if (ours.getOutcome() == BuyReplacementQuantityConstrainedSelectionOutcome.SELECTED) {
            assertEquals(1, allocations.size());
            assertSame(allocations.get(0).getCandidate(), ours.getSelected());
            assertEquals(plan.getExpectedGpPerSlotHour(), ours.getSelectedScore(), 1e-9);
        } else {
            assertTrue(allocations == null || allocations.isEmpty());
            assertNull(ours.getSelected());
        }
        return ours;
    }
    @Test public void ampleLimitsMatchUnconstrainedChoiceAndOptimizer() {
        TaxCalculator tax = margin(50);
        var set = built(tax, 500);
        var top = require(new BuyReplacementQuantityCandidateSelector(tax).select(set, 0, 1100));
        var ours = parity(set, coins(BIG), tax);
        assertEquals(BuyReplacementQuantityConstrainedSelectionOutcome.SELECTED, ours.getOutcome());
        assertSame(top.getSelected(), ours.getSelected());
        assertEquals(top.getSelectedPosition(), ours.getSelectedPosition());
        assertEquals(top.getSelectedSourceIndex(), ours.getSelectedSourceIndex());
        assertEquals(set.getCandidates().size(), ours.getStatuses().size());
        for (var s : ours.getStatuses()) assertEquals(BuyReplacementQuantityCandidateConstraintStatus.ADMISSIBLE, s);
        assertEquals(1150, ours.getExpiresAt());
        assertEquals(1100, ours.getSelectedAt());
    }
    @Test public void bestFittingCandidateIsChosenWhenTopDoesNotFit() {
        TaxCalculator tax = margin(50);
        var set = built(tax, 500);
        var top = require(new BuyReplacementQuantityCandidateSelector(tax).select(set, 0, 1100));
        long limit = top.getSelected().getCapitalRequired() - 1;
        var ours = parity(set, coins(limit), tax);
        assertEquals(BuyReplacementQuantityCandidateConstraintStatus.INSUFFICIENT_COINS,
                ours.getStatuses().get(top.getSelectedPosition()));
        assertEquals(BuyReplacementQuantityConstrainedSelectionOutcome.SELECTED, ours.getOutcome());
        assertNotSame(top.getSelected(), ours.getSelected());
        assertTrue(ours.getSelected().getCapitalRequired() <= limit);
        assertTrue(ours.getSelectedScore() <= top.getSelectedScore());
        assertEquals(set.getSourceIndexes().get(ours.getSelectedPosition()).intValue(), ours.getSelectedSourceIndex());
        for (int k = 0; k < set.getCandidates().size(); k++)
            if (ours.getStatuses().get(k) == BuyReplacementQuantityCandidateConstraintStatus.ADMISSIBLE)
                assertTrue(set.getCandidates().get(k).expectedGpPerSlotHour() <= ours.getSelectedScore());
    }
    @Test public void parityWithProductionOptimizerAcrossLimitSweep() {
        TaxCalculator tax = margin(50);
        var set = built(tax, 500);
        String g = set.getCandidates().get(0).getGroup();
        for (PortfolioCandidate c : set.getCandidates()) {
            for (long d = -1; d <= 1; d++) {
                parity(set, limits(1, c.getCapitalRequired() + d, BIG, 0, 0, 0, g, 0, 0), tax);
                parity(set, limits(1, BIG, c.getWorstLoss() + d, 0, 0, 0, g, 0, 0), tax);
                parity(set, limits(1, BIG, BIG, 300 + c.getCapitalRequired() + d, 0, 300, g, 0, 0), tax);
                parity(set, limits(1, BIG, BIG, 0, 400 + c.getCapitalRequired() + d, 0, g, 400, 0), tax);
                parity(set, limits(1, BIG, BIG, 0, 0, 0, g, 0, c.getNetProfit() + d), tax);
            }
        }
    }
    @Test public void nothingSelectedOutcomesMatchOptimizer() {
        TaxCalculator tax = margin(50);
        var set = built(tax, 500);
        assertEquals(BuyReplacementQuantityConstrainedSelectionOutcome.NO_FREE_SLOT,
                parity(set, limits(0, BIG, BIG, 0, 0, 0, "", 0, 0), tax).getOutcome());
        var none = parity(set, coins(0), tax);
        assertEquals(BuyReplacementQuantityConstrainedSelectionOutcome.PORTFOLIO_CONSTRAINT, none.getOutcome());
        assertEquals(-1, none.getSelectedPosition());
        assertTrue(Double.isNaN(none.getSelectedScore()));
        long largest = 0;
        for (PortfolioCandidate c : set.getCandidates()) largest = Math.max(largest, c.getNetProfit());
        assertEquals(BuyReplacementQuantityConstrainedSelectionOutcome.NO_ELIGIBLE_CANDIDATE,
                parity(set, limits(1, BIG, BIG, 0, 0, 0, "", 0, largest + 1), tax).getOutcome());
        TaxCalculator losing = margin(-5);
        assertEquals(BuyReplacementQuantityConstrainedSelectionOutcome.NO_ELIGIBLE_CANDIDATE,
                parity(built(losing, 500), coins(BIG), losing).getOutcome());
        var empty = built(tax, 0);
        assertTrue(empty.getCandidates().isEmpty());
        assertEquals(BuyReplacementQuantityConstrainedSelectionOutcome.NO_ELIGIBLE_CANDIDATE,
                parity(empty, coins(BIG), tax).getOutcome());
    }
    @Test public void timingFreshnessAndForgeryFailClosed() {
        TaxCalculator tax = margin(50);
        var set = built(tax, 500);
        var selector = new BuyReplacementQuantityConstrainedCandidateSelector(tax);
        assertNotNull(selector.select(set, coins(BIG), 1149));
        assertNull(selector.select(set, coins(BIG), 1150));
        assertNull(selector.select(set, coins(BIG), 1099));
        assertNull(selector.select(set, coins(BIG), Long.MAX_VALUE));
        List<PortfolioCandidate> reversed = new ArrayList<>(set.getCandidates());
        Collections.reverse(reversed);
        assertNull(selector.select(new BuyReplacementQuantityCandidateSet(set.getSource(), reversed,
                set.getSourceIndexes(), 1100), coins(BIG), 1100));
        Map<Integer, Long> negative = new HashMap<>();
        negative.put(ITEM, -1L);
        assertNull(selector.select(set, new PortfolioConstraints(1, BIG, BIG, 0, 0, negative, null, 0), 1100));
        Map<Integer, Long> huge = new HashMap<>();
        huge.put(ITEM, Long.MAX_VALUE);
        assertNull(selector.select(set, new PortfolioConstraints(1, BIG, BIG, 0, 0, huge, null, 0), 1100));
        assertNull(selector.select(null, coins(BIG), 1100));
        assertNull(selector.select(set, null, 1100));
        assertNull(new BuyReplacementQuantityConstrainedCandidateSelector(null).select(set, coins(BIG), 1100));
        assertNull(new BuyReplacementQuantityConstrainedCandidateSelector(margin(40)).select(set, coins(BIG), 1100));
    }
    @Test public void statusesAreImmutableAndSelectionIsDeterministic() {
        TaxCalculator tax = margin(50);
        var set = built(tax, 500);
        var selector = new BuyReplacementQuantityConstrainedCandidateSelector(tax);
        var first = selector.select(set, coins(BIG), 1100);
        var second = selector.select(set, coins(BIG), 1100);
        assertSame(first.getSelected(), second.getSelected());
        assertEquals(first.getStatuses(), second.getStatuses());
        try { first.getStatuses().clear(); fail("immutable"); }
        catch (UnsupportedOperationException expected) { }
    }
    @Test public void contractsArePackagePrivateAndSelectionOnly() throws Exception {
        for (Class<?> type : new Class<?>[] {BuyReplacementQuantityConstrainedSelectionOutcome.class,
                BuyReplacementQuantityCandidateConstraintStatus.class,
                BuyReplacementQuantityConstrainedSelection.class,
                BuyReplacementQuantityConstrainedCandidateSelector.class}) {
            assertFalse(Modifier.isPublic(type.getModifiers()));
            String text = Files.readString(Path.of("src/main/java/com/flippingfriend/companion/" + type.getSimpleName() + ".java"));
            for (String forbidden : new String[] {"PortfolioOptimizer", "PortfolioPlan", "PortfolioAllocation",
                    "PolicyDecision", "OfferLifecycleAction", "REPLACEMENT_AUTHORIZED", "SqliteStore",
                    "BuyLimitLedger", "AccountSnapshot", "Instant.now", "currentTimeMillis", "schedule",
                    "new PortfolioCandidate"})
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
