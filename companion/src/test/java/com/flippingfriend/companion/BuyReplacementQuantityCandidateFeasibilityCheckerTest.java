package com.flippingfriend.companion;
import com.flippingfriend.core.PortfolioCandidate;
import com.flippingfriend.core.PortfolioConstraints;
import com.flippingfriend.data.Candle;
import com.flippingfriend.model.PriceOffset;
import com.flippingfriend.model.TaxCalculator;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import static org.junit.Assert.*;
public class BuyReplacementQuantityCandidateFeasibilityCheckerTest {
    private static final int ITEM = 4151;
    private static TaxCalculator margin(long perItem) {
        return new TaxCalculator() {
            @Override public long netMarginPerItem(int item, int buy, int sell) { return perItem; }
            @Override public long netProfit(int item, int buy, int sell, int quantity) { return perItem * quantity; }
        };
    }
    private static BuyReplacementQuantityCandidateSelection selection(TaxCalculator tax) {
        var assessment = require(new BuyReplacementQuantityLiveLimitRevalidator(tax)
                .revalidate(capacity(tax), ITEM, 500, 1100, 1100));
        var set = require(new BuyReplacementQuantityCandidateBuilder(tax).build(assessment, 1100));
        return require(new BuyReplacementQuantityCandidateSelector(tax).select(set, 0, 1100));
    }
    private static PortfolioConstraints limits(int slots, long coins, long loss, long itemCap, long groupCap,
            long committedItem, String group, long committedGroup) {
        Map<Integer, Long> byItem = new HashMap<>();
        if (committedItem > 0) byItem.put(ITEM, committedItem);
        Map<String, Long> byGroup = new HashMap<>();
        if (committedGroup > 0) byGroup.put(group, committedGroup);
        return new PortfolioConstraints(slots, coins, loss, itemCap, groupCap, byItem, byGroup, 0);
    }
    private static PortfolioConstraints ample(String group) {
        return limits(1, 1_000_000_000L, 1_000_000_000L, 0, 0, 0, group, 0);
    }
    @Test public void ampleLimitsAreFeasibleAndCountCommittedCapital() {
        TaxCalculator tax = margin(50);
        var s = selection(tax);
        assertEquals(BuyReplacementQuantityCandidateSelectionOutcome.SELECTED, s.getOutcome());
        PortfolioCandidate c = s.getSelected();
        var checker = new BuyReplacementQuantityCandidateFeasibilityChecker(tax);
        var result = checker.check(s, limits(1, 1_000_000_000L, 1_000_000_000L, 0, 0, 700, c.getGroup(), 900), 1100);
        assertNotNull(result);
        assertSame(s, result.getSource());
        assertEquals(BuyReplacementQuantityCandidateFeasibilityOutcome.FEASIBLE, result.getOutcome());
        assertTrue(result.isFeasible());
        assertTrue(result.isSlotAvailable() && result.isCoinsAllowed() && result.isLossAllowed()
                && result.isItemAllowed() && result.isGroupAllowed());
        assertEquals(700 + c.getCapitalRequired(), result.getItemCapitalAfter());
        assertEquals(900 + c.getCapitalRequired(), result.getGroupCapitalAfter());
        assertEquals(1150, result.getExpiresAt());
        assertEquals(1100, result.getAssessedAt());
    }
    @Test public void coinsAndLossBudgetBoundariesAreInclusive() {
        TaxCalculator tax = margin(50);
        var s = selection(tax);
        PortfolioCandidate c = s.getSelected();
        var checker = new BuyReplacementQuantityCandidateFeasibilityChecker(tax);
        long capital = c.getCapitalRequired(), loss = c.getWorstLoss();
        String g = c.getGroup();
        assertTrue(capital > 0 && loss > 0);
        assertEquals(BuyReplacementQuantityCandidateFeasibilityOutcome.FEASIBLE,
                checker.check(s, limits(1, capital, loss, 0, 0, 0, g, 0), 1100).getOutcome());
        assertEquals(BuyReplacementQuantityCandidateFeasibilityOutcome.INSUFFICIENT_COINS,
                checker.check(s, limits(1, capital - 1, loss, 0, 0, 0, g, 0), 1100).getOutcome());
        assertEquals(BuyReplacementQuantityCandidateFeasibilityOutcome.LOSS_BUDGET_EXCEEDED,
                checker.check(s, limits(1, capital, loss - 1, 0, 0, 0, g, 0), 1100).getOutcome());
        assertEquals(BuyReplacementQuantityCandidateFeasibilityOutcome.NO_FREE_SLOT,
                checker.check(s, limits(0, capital, loss, 0, 0, 0, g, 0), 1100).getOutcome());
        var none = checker.check(s, limits(0, 0, 0, 1, 1, 0, g, 0), 1100);
        assertEquals(BuyReplacementQuantityCandidateFeasibilityOutcome.NO_FREE_SLOT, none.getOutcome());
        assertFalse(none.isSlotAvailable() || none.isCoinsAllowed() || none.isLossAllowed() || none.isItemAllowed());
    }
    @Test public void itemExposureCountsCommittedCapitalAndZeroCapIsUnlimited() {
        TaxCalculator tax = margin(50);
        var s = selection(tax);
        PortfolioCandidate c = s.getSelected();
        var checker = new BuyReplacementQuantityCandidateFeasibilityChecker(tax);
        long capital = c.getCapitalRequired();
        String g = c.getGroup();
        long big = 1_000_000_000L;
        assertEquals(BuyReplacementQuantityCandidateFeasibilityOutcome.FEASIBLE,
                checker.check(s, limits(1, big, big, 300 + capital, 0, 300, g, 0), 1100).getOutcome());
        assertEquals(BuyReplacementQuantityCandidateFeasibilityOutcome.ITEM_EXPOSURE_EXCEEDED,
                checker.check(s, limits(1, big, big, 300 + capital - 1, 0, 300, g, 0), 1100).getOutcome());
        assertEquals(BuyReplacementQuantityCandidateFeasibilityOutcome.FEASIBLE,
                checker.check(s, limits(1, big, big, 0, 0, big, g, 0), 1100).getOutcome());
    }
    @Test public void groupExposureFollowsOptimizerIncludingEmptyGroupExemption() {
        TaxCalculator tax = margin(50);
        var s = selection(tax);
        PortfolioCandidate c = s.getSelected();
        var checker = new BuyReplacementQuantityCandidateFeasibilityChecker(tax);
        long capital = c.getCapitalRequired();
        String g = c.getGroup();
        long big = 1_000_000_000L;
        var tight = checker.check(s, limits(1, big, big, 0, 400 + capital - 1, 0, g, 400), 1100);
        var exact = checker.check(s, limits(1, big, big, 0, 400 + capital, 0, g, 400), 1100);
        assertEquals(BuyReplacementQuantityCandidateFeasibilityOutcome.FEASIBLE, exact.getOutcome());
        if (g.isEmpty()) {
            assertEquals(BuyReplacementQuantityCandidateFeasibilityOutcome.FEASIBLE, tight.getOutcome());
            assertTrue(tight.isGroupAllowed());
        } else {
            assertEquals(BuyReplacementQuantityCandidateFeasibilityOutcome.GROUP_EXPOSURE_EXCEEDED, tight.getOutcome());
            assertFalse(tight.isGroupAllowed());
        }
        assertEquals(BuyReplacementQuantityCandidateFeasibilityOutcome.FEASIBLE,
                checker.check(s, limits(1, big, big, 0, 0, 0, g, big), 1100).getOutcome());
    }
    @Test public void noSelectionIsValidEvidence() {
        TaxCalculator tax = margin(-5);
        var s = selection(tax);
        assertEquals(BuyReplacementQuantityCandidateSelectionOutcome.NO_ELIGIBLE_CANDIDATE, s.getOutcome());
        var result = new BuyReplacementQuantityCandidateFeasibilityChecker(tax).check(s, ample(""), 1100);
        assertNotNull(result);
        assertEquals(BuyReplacementQuantityCandidateFeasibilityOutcome.NO_SELECTION, result.getOutcome());
        assertFalse(result.isFeasible());
        assertEquals(-1, result.getItemCapitalAfter());
    }
    @Test public void timingAndFreshnessFollowSelection() {
        TaxCalculator tax = margin(50);
        var s = selection(tax);
        var checker = new BuyReplacementQuantityCandidateFeasibilityChecker(tax);
        PortfolioConstraints l = ample(s.getSelected().getGroup());
        assertNotNull(checker.check(s, l, 1149));
        assertNull(checker.check(s, l, 1150));
        assertNull(checker.check(s, l, 1099));
        assertNull(checker.check(s, l, Long.MAX_VALUE));
    }
    @Test public void forgedOrMalformedInputsFailClosed() {
        TaxCalculator tax = margin(50);
        var s = selection(tax);
        var checker = new BuyReplacementQuantityCandidateFeasibilityChecker(tax);
        String g = s.getSelected().getGroup();
        var set = s.getSource();
        assertTrue(set.getCandidates().size() > 1);
        int other = s.getSelectedPosition() == 0 ? 1 : 0;
        var forged = new BuyReplacementQuantityCandidateSelection(set, s.getOutcome(), set.getCandidates().get(other),
                other, set.getSourceIndexes().get(other), set.getCandidates().get(other).expectedGpPerSlotHour(),
                0, 0, 0, 0, 0, 1100);
        assertNull(checker.check(forged, ample(g), 1100));
        assertNull(checker.check(s, new PortfolioConstraints(1, 1_000_000_000L, 1_000_000_000L, 0, 0,
                null, null, 25), 1100));
        Map<Integer, Long> negative = new HashMap<>();
        negative.put(ITEM, -1L);
        assertNull(checker.check(s, new PortfolioConstraints(1, 1_000_000_000L, 1_000_000_000L, 0, 0,
                negative, null, 0), 1100));
        Map<Integer, Long> huge = new HashMap<>();
        huge.put(ITEM, Long.MAX_VALUE);
        assertNull(checker.check(s, new PortfolioConstraints(1, 1_000_000_000L, 1_000_000_000L, 0, 0,
                huge, null, 0), 1100));
        assertNull(checker.check(null, ample(g), 1100));
        assertNull(checker.check(s, null, 1100));
        assertNull(new BuyReplacementQuantityCandidateFeasibilityChecker(null).check(s, ample(g), 1100));
        assertNull(new BuyReplacementQuantityCandidateFeasibilityChecker(margin(40)).check(s, ample(g), 1100));
    }
    @Test public void contractsArePackagePrivateAndFeasibilityOnly() throws Exception {
        for (Class<?> type : new Class<?>[] {BuyReplacementQuantityCandidateFeasibilityOutcome.class,
                BuyReplacementQuantityCandidateFeasibilityAssessment.class,
                BuyReplacementQuantityCandidateFeasibilityChecker.class}) {
            assertFalse(Modifier.isPublic(type.getModifiers()));
            String text = Files.readString(Path.of("src/main/java/com/flippingfriend/companion/" + type.getSimpleName() + ".java"));
            for (String forbidden : new String[] {"PortfolioOptimizer()", "new PortfolioOptimizer", "PortfolioPlan",
                    "PortfolioAllocation", "PolicyDecision", "OfferLifecycleAction", "REPLACEMENT_AUTHORIZED",
                    "SqliteStore", "BuyLimitLedger", "AccountSnapshot", "Instant.now", "currentTimeMillis",
                    "schedule", "new PortfolioCandidate"})
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
