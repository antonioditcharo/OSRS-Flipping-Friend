package com.flippingfriend.companion;

import com.flippingfriend.core.PortfolioCandidate;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public class BuyReplacementQuantityExpectedSlotOccupancyEvaluatorTest {
    private final BuyReplacementQuantityExpectedSlotOccupancyEvaluator evaluator = new BuyReplacementQuantityExpectedSlotOccupancyEvaluator();

    @Test public void productionParityPreservesEveryQuantityAndGetter() throws Exception {
        BuyReplacementQuantityCalibratedDurationEvaluationSet s = source();
        BuyReplacementQuantityExpectedSlotOccupancyEvaluationSet r = evaluator.evaluate(s, 1100);
        assertNotNull(r); assertSame(s, r.getSource());
        assertSame(s.getEvaluations(), r.getCalibratedDurationEvaluations());
        assertEquals(s.getEvaluatedAt(), r.getCalibratedDurationEvaluatedAt());
        assertEquals(1100, r.getEvaluatedAt());
        for (Method g : s.getClass().getDeclaredMethods()) {
            if (g.getParameterCount() != 0 || g.getName().equals("getSource")
                    || g.getName().equals("getEvaluations") || g.getName().equals("getEvaluatedAt")) continue;
            assertEquals(g.invoke(s), r.getClass().getDeclaredMethod(g.getName()).invoke(r));
        }
        assertEquals(s.getEvaluations().size(), r.getEvaluations().size());
        for (int i = 0; i < r.getEvaluations().size(); i++) {
            BuyReplacementQuantityCalibratedDurationEvaluation a = s.getEvaluations().get(i);
            BuyReplacementQuantityExpectedSlotOccupancyEvaluation e = r.getEvaluations().get(i);
            assertSame(a, e.getSource());
            for (Method g : a.getClass().getDeclaredMethods()) {
                if (g.getParameterCount() == 0 && !g.getName().equals("getSource"))
                    assertEquals(g.invoke(a), e.getClass().getDeclaredMethod(g.getName()).invoke(e));
            }
            assertEquals(production(a.getBuyProbability(), a.getSellProbability(),
                    a.getCalibratedBuyHours(), a.getCalibratedSellHours(), s.getEffectiveHorizonHours()),
                    e.getExpectedSlotHours(), 0);
        }
    }
    @Test public void arithmeticMatchesProductionAcrossAllOutcomesAndHorizonFloor() {
        for (double b : new double[] {0, 0.6, 1})
            for (double s : new double[] {0, 0.5, 1})
                for (double h : new double[] {0.001, 1.0 / 60.0, 4})
                    assertEquals(production(b, s, 1.2, 1.4, h),
                            BuyReplacementQuantityExpectedSlotOccupancyEvaluator.expectedHours(b, s, 1.2, 1.4, h), 0);
        assertEquals(4, BuyReplacementQuantityExpectedSlotOccupancyEvaluator.expectedHours(0, 1, 1.2, 1.4, 4), 0);
        assertEquals(1.2 + 1.4, BuyReplacementQuantityExpectedSlotOccupancyEvaluator.expectedHours(1, 1, 1.2, 1.4, 4), 0);
        assertEquals(1.2 + 4, BuyReplacementQuantityExpectedSlotOccupancyEvaluator.expectedHours(1, 0, 1.2, 1.4, 4), 0);
    }
    @Test public void resultFloorAndZeroDurationsMatchProduction() {
        assertEquals(1.0 / 60.0, BuyReplacementQuantityExpectedSlotOccupancyEvaluator.expectedHours(1, 1, 0, 0, 4), 0);
        BuyReplacementQuantityCalibratedDurationEvaluationSet s = fixture(1, 1, 0, 0, 4);
        assertNotNull(s); assertEquals(1.0 / 60.0, evaluator.evaluate(s, 1100).getEvaluations().get(0).getExpectedSlotHours(), 0);
    }
    @Test public void positiveSubMinuteHorizonMatchesConstructorSemantics() {
        BuyReplacementQuantityCalibratedDurationEvaluationSet s = fixture(0.6, 0.5, 0.1, 0.2, 0.001);
        assertNotNull(s); assertEquals(production(0.6, 0.5, 0.1, 0.2, 0.001),
                evaluator.evaluate(s, 1100).getEvaluations().get(0).getExpectedSlotHours(), 0);
    }
    @Test public void inclusiveFreshnessAndFutureTimeFailClosed() {
        assertNotNull(evaluator.evaluate(source(), 1220)); assertNull(evaluator.evaluate(source(), 1221));
        assertNull(evaluator.evaluate(source(), 1099)); assertNull(evaluator.evaluate(null, 1100));
    }
    @Test public void missingEmptyAndMalformedSourcesFailClosed() {
        assertNull(evaluator.evaluate(new BuyReplacementQuantityCalibratedDurationEvaluationSet(null, Collections.emptyList(), 1100), 1100));
        BuyReplacementQuantityCalibratedDurationEvaluationSet s = source();
        assertNull(evaluator.evaluate(new BuyReplacementQuantityCalibratedDurationEvaluationSet(s.getSource(), Collections.emptyList(), 1100), 1100));
        List<BuyReplacementQuantityCalibratedDurationEvaluation> list = new ArrayList<>(s.getEvaluations()); list.set(0, null);
        assertNull(evaluator.evaluate(new BuyReplacementQuantityCalibratedDurationEvaluationSet(s.getSource(), list, 1100), 1100));
        assertNull(evaluator.evaluate(new BuyReplacementQuantityCalibratedDurationEvaluationSet(s.getSource(), s.getEvaluations(), -1), 1100));
    }
    @Test public void reorderedAndAlteredDurationEvidenceFailClosed() {
        BuyReplacementQuantityCalibratedDurationEvaluationSet s = source(); List<BuyReplacementQuantityCalibratedDurationEvaluation> list = new ArrayList<>(s.getEvaluations());
        assertTrue(list.size() > 1); Collections.reverse(list);
        assertNull(evaluator.evaluate(new BuyReplacementQuantityCalibratedDurationEvaluationSet(s.getSource(), list, 1100), 1100));
        for (double value : new double[] {-1, Double.NaN, Double.POSITIVE_INFINITY, 123}) {
            list = new ArrayList<>(s.getEvaluations()); BuyReplacementQuantityCalibratedDurationEvaluation e = list.get(0);
            list.set(0, new BuyReplacementQuantityCalibratedDurationEvaluation(e.getSource(), value, e.getCalibratedSellHours()));
            assertNull(evaluator.evaluate(new BuyReplacementQuantityCalibratedDurationEvaluationSet(s.getSource(), list, 1100), 1100));
            list.set(0, new BuyReplacementQuantityCalibratedDurationEvaluation(e.getSource(), e.getCalibratedBuyHours(), value));
            assertNull(evaluator.evaluate(new BuyReplacementQuantityCalibratedDurationEvaluationSet(s.getSource(), list, 1100), 1100));
        }
    }
    @Test public void overflowAndInvalidHorizonFailClosed() {
        BuyReplacementQuantityCalibratedDurationEvaluationSet overflow = fixture(1, 1, Double.MAX_VALUE, Double.MAX_VALUE, 4);
        assertNotNull(overflow); assertNull(evaluator.evaluate(overflow, 1100));
        for (double h : new double[] {0, -1, Double.NaN, Double.POSITIVE_INFINITY}) {
            BuyReplacementQuantityCalibratedDurationEvaluationSet s = source();
            BuyReplacementQuantityDurationCalibrationInputContext bad = new BuyReplacementQuantityDurationCalibrationInputContext(null, false, 1, 1100);
            assertNull(evaluator.evaluate(new BuyReplacementQuantityCalibratedDurationEvaluationSet(bad, s.getEvaluations(), 1100), 1100));
            assertNull(fixture(0.6, 0.5, 1.2, 1.4, h));
        }
    }
    @Test public void immutableDefensiveAndDeterministic() {
        BuyReplacementQuantityCalibratedDurationEvaluationSet s = source(); BuyReplacementQuantityExpectedSlotOccupancyEvaluationSet r = evaluator.evaluate(s, 1100);
        List<BuyReplacementQuantityExpectedSlotOccupancyEvaluation> copy = new ArrayList<>(r.getEvaluations());
        BuyReplacementQuantityExpectedSlotOccupancyEvaluationSet defensive = new BuyReplacementQuantityExpectedSlotOccupancyEvaluationSet(s, copy, 1100); copy.clear();
        assertEquals(r.getEvaluations().size(), defensive.getEvaluations().size());
        try { r.getEvaluations().clear(); fail("immutable"); } catch (UnsupportedOperationException expected) {}
        assertEquals(r.getEvaluations().get(0).getExpectedSlotHours(), evaluator.evaluate(s, 1100).getEvaluations().get(0).getExpectedSlotHours(), 0);
    }
    @Test public void packagePrivatePureAndNoDownstreamAuthority() throws Exception {
        for (Class<?> c : new Class<?>[] {BuyReplacementQuantityExpectedSlotOccupancyEvaluation.class, BuyReplacementQuantityExpectedSlotOccupancyEvaluationSet.class, BuyReplacementQuantityExpectedSlotOccupancyEvaluator.class}) {
            assertFalse(Modifier.isPublic(c.getModifiers()));
            String text = java.nio.file.Files.readString(java.nio.file.Path.of("src/main/java/com/flippingfriend/companion/" + c.getSimpleName() + ".java"));
            for (String x : new String[] {"PortfolioCandidate", "PolicyDecision", "OfferLifecycleAction", "REPLACEMENT_AUTHORIZED", "expectedGpPerSlotHour", "SqliteStore", "FillModel", "estimateBuy", "estimateSell", "schedule", "selected"}) assertFalse(x, text.contains(x));
        }
    }
    static BuyReplacementQuantityExpectedSlotOccupancyEvaluationSet evaluationSetForCompanionTests() { return new BuyReplacementQuantityExpectedSlotOccupancyEvaluator().evaluate(source(), 1100); }
    private static BuyReplacementQuantityCalibratedDurationEvaluationSet source() { return BuyReplacementQuantityCalibratedDurationEvaluatorTest.evaluationSetForCompanionTests(); }
    private static double production(double b, double s, double bh, double sh, double h) {
        return new PortfolioCandidate(4151, "fixture", "", 100, 100, 100, 120, 120, 120,
                1, 1, 1, 1, 0, b, s, bh, sh, h, 1300).expectedSlotHours();
    }
    private static BuyReplacementQuantityCalibratedDurationEvaluationSet fixture(double bp, double sp, double bh, double sh, double horizon) {
        BuyReplacementQuantityFillInputContext original = BuyReplacementQuantityFillInputContextComposerTest.quantityFillContextForCompanionTests();
        BuyReplacementQuantityGridCandidateSet grid = BuyReplacementQuantityGridComposerTest.quantityFillCandidateSetForCompanionTests();
        BuyReplacementQuantityFillInputContext input = new BuyReplacementQuantityFillInputContext(grid, original.getInputs(), original.getFillCurve(), horizon,
                original.getSeasonalMultiplier(), original.getHistoryObservedAt(), original.getMarketContextObservedAt(), 1100);
        List<BuyReplacementQuantityFillEvaluation> fills = new ArrayList<>();
        List<BuyReplacementQuantityFillViableCandidate> viable = new ArrayList<>();
        List<BuyReplacementQuantityRoundTripCompletionEvaluation> complete = new ArrayList<>();
        for (BuyReplacementQuantityFillInput x : input.getInputs()) {
            BuyReplacementQuantityFillEvaluation e = new BuyReplacementQuantityFillEvaluation(x, bp, bh, 100, 0, sp, sh, 90, 0);
            fills.add(e); BuyReplacementQuantityFillViableCandidate v = new BuyReplacementQuantityFillViableCandidate(e); viable.add(v);
            complete.add(new BuyReplacementQuantityRoundTripCompletionEvaluation(v, bp * sp));
        }
        BuyReplacementQuantityFillEvaluationSet fs = new BuyReplacementQuantityFillEvaluationSet(input, fills, 1100);
        BuyReplacementQuantityFillViabilityAssessment a = new BuyReplacementQuantityFillViabilityAssessment(fs, BuyReplacementQuantityFillViabilityOutcome.VIABLE, fills, viable, 1100);
        BuyReplacementQuantityDurationCalibrationInputContext context = new BuyReplacementQuantityDurationCalibrationInputContext(
                new BuyReplacementQuantityRoundTripCompletionEvaluationSet(a, complete, 1100), false, 1, 1100);
        return new BuyReplacementQuantityCalibratedDurationEvaluator().evaluate(context, 1100);
    }
}
