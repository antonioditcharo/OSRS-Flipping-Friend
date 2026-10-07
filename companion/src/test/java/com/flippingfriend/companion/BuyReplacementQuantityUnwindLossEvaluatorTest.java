package com.flippingfriend.companion;

import com.flippingfriend.model.ItemFeatures;
import com.flippingfriend.model.TaxCalculator;
import com.flippingfriend.data.ItemMetadata;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public class BuyReplacementQuantityUnwindLossEvaluatorTest {
    private final TaxCalculator tax = new TaxCalculator();
    private final BuyReplacementQuantityUnwindLossEvaluator evaluator = new BuyReplacementQuantityUnwindLossEvaluator(tax);
    @Test public void productionParityUsesEveryGeneratedQuantityAndPreservesGetters() throws Exception {
        BuyReplacementQuantityUnwindLossInputContext s = source(); BuyReplacementQuantityUnwindLossEvaluationSet r = evaluator.evaluate(s, 1100);
        assertNotNull(r); assertSame(s, r.getSource()); assertSame(s.getEvaluations(), r.getSlotOccupancyEvaluations());
        assertEquals(s.getComposedAt(), r.getInputComposedAt()); assertEquals(1100, r.getEvaluatedAt());
        for (Method g : s.getClass().getDeclaredMethods()) {
            if (g.getParameterCount() != 0 || g.getName().equals("getSource") || g.getName().equals("getEvaluations") || g.getName().equals("getComposedAt")) continue;
            assertEquals(g.invoke(s), r.getClass().getDeclaredMethod(g.getName()).invoke(r));
        }
        boolean belowCeiling = false;
        assertEquals(s.getEvaluations().size(), r.getEvaluations().size());
        for (int i = 0; i < r.getEvaluations().size(); i++) {
            BuyReplacementQuantityExpectedSlotOccupancyEvaluation a = s.getEvaluations().get(i); BuyReplacementQuantityUnwindLossEvaluation e = r.getEvaluations().get(i);
            assertSame(a, e.getSource());
            for (Method g : a.getClass().getDeclaredMethods()) if (g.getParameterCount() == 0 && !g.getName().equals("getSource"))
                assertEquals(g.invoke(a), e.getClass().getDeclaredMethod(g.getName()).invoke(e));
            assertEquals(production(tax, s, a), e.getTotalUnwindLoss());
            assertEquals(e.getLossPerItem() * a.getQuantity(), e.getTotalUnwindLoss());
            if (a.getQuantity() < s.getExactRemainderQuantity()) { belowCeiling = true;
                assertNotEquals(e.getLossPerItem() * s.getExactRemainderQuantity(), e.getTotalUnwindLoss()); }
        }
        assertTrue(belowCeiling);
    }
    @Test public void volatilityCeilingDriftFloorBucketsAndAnchorsMatchProduction() throws Exception {
        for (double vol : new double[] {0, 0.001, 0.037, 0.2, 0.7})
            for (int bucket : new int[] {1, 300, 3600})
                for (int low : new int[] {1, 49, 990, Integer.MAX_VALUE}) {
                    BuyReplacementQuantityUnwindLossInputContext s = context(source().getSource(), low, vol, bucket, 1100, 1100);
                    BuyReplacementQuantityUnwindLossEvaluationSet r = evaluator.evaluate(s, 1100); assertNotNull(r);
                    for (int i = 0; i < r.getEvaluations().size(); i++) assertEquals(production(tax, s, s.getEvaluations().get(i)), r.getEvaluations().get(i).getTotalUnwindLoss());
                }
        assertEquals(0.002, evaluator.evaluate(source(), 1100).getEvaluations().get(0).getDriftFraction(), 0);
    }
    @Test public void itemAwareExemptionsAreUsedWithoutResolvingInsideEvaluator() throws Exception {
        BuyReplacementQuantityUnwindLossInputContext s = source(); TaxCalculator exempt = new TaxCalculator();
        exempt.resolveExemptions(Collections.singletonList(new ItemMetadata(s.getItemId(), "Old school bond", false, 100, 0)));
        BuyReplacementQuantityUnwindLossEvaluationSet r = new BuyReplacementQuantityUnwindLossEvaluator(exempt).evaluate(s, 1100); assertNotNull(r);
        for (int i = 0; i < r.getEvaluations().size(); i++) { assertEquals(0, r.getEvaluations().get(i).getExitTaxPerItem());
            assertEquals(production(exempt, s, s.getEvaluations().get(i)), r.getEvaluations().get(i).getTotalUnwindLoss()); }
        assertTrue(exempt.isExempt(s.getItemId()));
    }
    @Test public void exitFloorAndPerItemTaxAreRetained() {
        BuyReplacementQuantityUnwindLossInputContext s = context(source().getSource(), 1, 0, 300, 1100, 1100);
        for (BuyReplacementQuantityUnwindLossEvaluation e : evaluator.evaluate(s, 1100).getEvaluations()) {
            assertEquals(1, e.getExitPrice()); assertEquals(0, e.getExitTaxPerItem());
            assertEquals((long)e.getBuyPrice() - 1, e.getLossPerItem());
        }
        for (BuyReplacementQuantityUnwindLossEvaluation e : evaluator.evaluate(source(), 1100).getEvaluations())
            assertEquals(tax.taxPerItem(source().getItemId(), e.getExitPrice()), e.getExitTaxPerItem());
    }
    @Test public void inclusiveFreshnessDoesNotRefreshMarketAuthority() {
        assertNotNull(evaluator.evaluate(source(), 1220)); assertNull(evaluator.evaluate(source(), 1221));
        assertNull(evaluator.evaluate(source(), 1099)); assertNull(evaluator.evaluate(source(), Long.MAX_VALUE));
        BuyReplacementQuantityUnwindLossInputContext later = context(source().getSource(), 990, 0, 300, 1100, 1150);
        assertNotNull(evaluator.evaluate(later, 1220)); assertNull(evaluator.evaluate(later, 1221));
        assertNull(evaluator.evaluate(context(source().getSource(), 990, 0, 300, 1101, 1101), 1101));
    }
    @Test public void invalidScalarsTimesAndMissingAuthorityFailClosed() {
        assertNull(evaluator.evaluate(null, 1100)); assertNull(new BuyReplacementQuantityUnwindLossEvaluator(null).evaluate(source(), 1100));
        for (double v : new double[] {-1, Double.NaN, Double.POSITIVE_INFINITY})
            assertNull(evaluator.evaluate(context(source().getSource(), 990, v, 300, 1100, 1100), 1100));
        for (int x : new int[] {0, -1}) {
            assertNull(evaluator.evaluate(context(source().getSource(), x, 0, 300, 1100, 1100), 1100));
            assertNull(evaluator.evaluate(context(source().getSource(), 990, 0, x, 1100, 1100), 1100));
        }
        assertNull(evaluator.evaluate(context(null, 990, 0, 300, 1100, 1100), 1100));
        assertNull(evaluator.evaluate(context(source().getSource(), 990, 0, 300, -1, 1100), 1100));
        assertNull(evaluator.evaluate(context(source().getSource(), 990, 0, 300, 1100, -1), 1100));
    }
    @Test public void reorderedAlteredForeignAndEmptySlotEvidenceFailClosed() {
        BuyReplacementQuantityExpectedSlotOccupancyEvaluationSet s = source().getSource(); List<BuyReplacementQuantityExpectedSlotOccupancyEvaluation> list = new ArrayList<>(s.getEvaluations());
        assertTrue(list.size() > 1); Collections.reverse(list); assertBadSlots(s, list);
        list = new ArrayList<>(s.getEvaluations()); list.set(0, null); assertBadSlots(s, list);
        list = new ArrayList<>(s.getEvaluations()); list.set(0, new BuyReplacementQuantityExpectedSlotOccupancyEvaluation(list.get(0).getSource(), 123)); assertBadSlots(s, list);
        list = new ArrayList<>(s.getEvaluations()); list.set(0, source().getEvaluations().get(0)); assertBadSlots(s, list);
        assertBadSlots(s, Collections.emptyList());
    }
    @Test public void invalidInjectedTaxResultsFailClosed() {
        for (int value : new int[] {-1, Integer.MAX_VALUE}) {
            TaxCalculator bad = new TaxCalculator() { @Override public int taxPerItem(int item, int price) { return value; } };
            assertNull(new BuyReplacementQuantityUnwindLossEvaluator(bad).evaluate(source(), 1100));
        }
    }
    @Test public void defensiveImmutableAndDeterministic() {
        BuyReplacementQuantityUnwindLossInputContext s = source(); BuyReplacementQuantityUnwindLossEvaluationSet r = evaluator.evaluate(s, 1100);
        List<BuyReplacementQuantityUnwindLossEvaluation> copy = new ArrayList<>(r.getEvaluations());
        BuyReplacementQuantityUnwindLossEvaluationSet d = new BuyReplacementQuantityUnwindLossEvaluationSet(s, copy, 1100); copy.clear();
        assertEquals(r.getEvaluations().size(), d.getEvaluations().size());
        try { r.getEvaluations().clear(); fail("immutable"); } catch (UnsupportedOperationException expected) {}
        assertEquals(r.getEvaluations().get(0).getTotalUnwindLoss(), evaluator.evaluate(s, 1100).getEvaluations().get(0).getTotalUnwindLoss());
    }
    @Test public void contractsRemainPackagePrivateAndOutsideDownstreamAuthority() throws Exception {
        for (Class<?> c : new Class<?>[] {BuyReplacementQuantityUnwindLossEvaluation.class, BuyReplacementQuantityUnwindLossEvaluationSet.class, BuyReplacementQuantityUnwindLossEvaluator.class}) {
            assertFalse(Modifier.isPublic(c.getModifiers()));
            String text = java.nio.file.Files.readString(java.nio.file.Path.of("src/main/java/com/flippingfriend/companion/" + c.getSimpleName() + ".java"));
            for (String x : new String[] {"PortfolioCandidate", "PolicyDecision", "OfferLifecycleAction", "REPLACEMENT_AUTHORIZED", "expectedGpPerSlotHour", "SqliteStore", "FillModel", "estimateBuy", "estimateSell", "resolveExemptions", "schedule", "selected"}) assertFalse(x, text.contains(x));
        }
    }
    @Test public void unchangedSubMinuteHorizonAndNonfiniteBucketArithmetic() throws Exception {
        Method fixture = BuyReplacementQuantityExpectedSlotOccupancyEvaluatorTest.class.getDeclaredMethod(
                "fixture", double.class, double.class, double.class, double.class, double.class);
        fixture.setAccessible(true);
        for (double h : new double[] {0.001, 4, Double.MAX_VALUE}) {
            BuyReplacementQuantityCalibratedDurationEvaluationSet durations = (BuyReplacementQuantityCalibratedDurationEvaluationSet)
                    fixture.invoke(null, 1.0, 1.0, 0.1, 0.2, h);
            assertNotNull(durations);
            BuyReplacementQuantityExpectedSlotOccupancyEvaluationSet slots = new BuyReplacementQuantityExpectedSlotOccupancyEvaluator().evaluate(durations, 1100);
            assertNotNull(slots);
            BuyReplacementQuantityUnwindLossInputContext input = context(slots, 990, 0.037, 300, 1100, 1100);
            BuyReplacementQuantityUnwindLossEvaluationSet result = evaluator.evaluate(input, 1100);
            if (h == Double.MAX_VALUE) { assertNull(result); continue; }
            assertNotNull(result); assertEquals(h, result.getEffectiveHorizonHours(), 0);
            for (int i = 0; i < result.getEvaluations().size(); i++)
                assertEquals(production(tax, input, slots.getEvaluations().get(i)), result.getEvaluations().get(i).getTotalUnwindLoss());
        }
    }
    static BuyReplacementQuantityUnwindLossEvaluationSet evaluationSetForCompanionTests() { return new BuyReplacementQuantityUnwindLossEvaluator(new TaxCalculator()).evaluate(source(), 1100); }
    private static BuyReplacementQuantityUnwindLossInputContext source() { return BuyReplacementQuantityUnwindLossInputContextComposerTest.contextForCompanionTests(); }
    private static BuyReplacementQuantityUnwindLossInputContext context(BuyReplacementQuantityExpectedSlotOccupancyEvaluationSet s, int low, double v, int bucket, long market, long at) { return new BuyReplacementQuantityUnwindLossInputContext(s, low, v, bucket, market, at); }
    private void assertBadSlots(BuyReplacementQuantityExpectedSlotOccupancyEvaluationSet s, List<BuyReplacementQuantityExpectedSlotOccupancyEvaluation> list) {
        assertNull(evaluator.evaluate(context(new BuyReplacementQuantityExpectedSlotOccupancyEvaluationSet(s.getSource(), list, 1100), 990, 0, 300, 1100, 1100), 1100));
    }
    private static long production(TaxCalculator tax, BuyReplacementQuantityUnwindLossInputContext s, BuyReplacementQuantityExpectedSlotOccupancyEvaluation e) throws Exception {
        CandidateFactory factory = new CandidateFactory(null, tax, "5m", "1h", s.getBucketSeconds());
        Method method = CandidateFactory.class.getDeclaredMethod("unwindCost", int.class, int.class, int.class, int.class, ItemFeatures.class, double.class);
        method.setAccessible(true);
        return (Long)method.invoke(factory, s.getItemId(), e.getBuyPrice(), s.getCurrentLowPrice(), e.getQuantity(), features(s.getItemId(), s.getVolatility()), s.getEffectiveHorizonHours());
    }
    private static ItemFeatures features(int item, double volatility) throws Exception {
        java.lang.reflect.Constructor<?> c = ItemFeatures.class.getDeclaredConstructors()[0]; c.setAccessible(true);
        Class<?>[] types = c.getParameterTypes(); Object[] args = new Object[types.length];
        for (int i = 0; i < types.length; i++) {
            if (types[i] == int.class) args[i] = 0;
            else if (types[i] == double.class) args[i] = 0.0;
            else args[i] = types[i].getEnumConstants()[0];
        }
        args[0] = item; args[4] = volatility; return (ItemFeatures)c.newInstance(args);
    }
}
