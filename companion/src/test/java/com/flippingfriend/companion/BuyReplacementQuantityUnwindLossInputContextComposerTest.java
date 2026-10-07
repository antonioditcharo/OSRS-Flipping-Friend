package com.flippingfriend.companion;

import com.flippingfriend.model.ItemFeatures;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public class BuyReplacementQuantityUnwindLossInputContextComposerTest {
    private final BuyReplacementQuantityUnwindLossInputContextComposer composer = new BuyReplacementQuantityUnwindLossInputContextComposer();

    @Test public void preservesEveryUpstreamGetterAndOrderedEvidence() throws Exception {
        BuyReplacementQuantityExpectedSlotOccupancyEvaluationSet s = source();
        BuyReplacementQuantityUnwindLossInputContext r = compose(s, ItemFeatures.unknown(s.getItemId()), 1100, 1100);
        assertNotNull(r); assertSame(s, r.getSource());
        assertSame(s.getEvaluations(), r.getEvaluations());
        assertEquals(s.getEvaluatedAt(), r.getExpectedSlotOccupancyEvaluatedAt());
        assertEquals(990, r.getCurrentLowPrice()); assertEquals(300, r.getBucketSeconds());
        assertEquals(1100, r.getMarketObservedAt()); assertEquals(1100, r.getComposedAt());
        for (Method g : s.getClass().getDeclaredMethods()) {
            if (g.getParameterCount() != 0 || g.getName().equals("getSource") || g.getName().equals("getEvaluatedAt")) continue;
            assertEquals(g.invoke(s), r.getClass().getDeclaredMethod(g.getName()).invoke(r));
        }
        for (int i = 0; i < s.getEvaluations().size(); i++) {
            assertSame(s.getEvaluations().get(i), r.getEvaluations().get(i));
            assertEquals(s.getEvaluations().get(i).getQuantity(), r.getEvaluations().get(i).getQuantity());
        }
        try { r.getEvaluations().clear(); fail("immutable"); } catch (UnsupportedOperationException expected) {}
    }
    @Test public void capturesVolatilityScalarWithoutRetainingFeatureObject() throws Exception {
        BuyReplacementQuantityExpectedSlotOccupancyEvaluationSet s = source(); ItemFeatures f = features(s.getItemId(), 0.037);
        BuyReplacementQuantityUnwindLossInputContext r = compose(s, f, 1100, 1100); assertNotNull(r);
        assertEquals(0.037, r.getVolatility(), 0);
        for (Field field : r.getClass().getDeclaredFields()) assertNotEquals(ItemFeatures.class, field.getType());
        // Values above the production arithmetic cap remain raw inputs; no cap is applied here.
        assertEquals(0.7, compose(s, features(s.getItemId(), 0.7), 1100, 1100).getVolatility(), 0);
    }
    @Test public void zeroVolatilityIsValid() {
        BuyReplacementQuantityExpectedSlotOccupancyEvaluationSet s = source();
        assertEquals(0, compose(s, ItemFeatures.unknown(s.getItemId()), 1100, 1100).getVolatility(), 0);
    }
    @Test public void unavailableAndWrongItemFailClosed() {
        BuyReplacementQuantityExpectedSlotOccupancyEvaluationSet s = source();
        assertNull(compose(null, ItemFeatures.unknown(s.getItemId()), 1100, 1100));
        assertNull(compose(s, null, 1100, 1100));
        assertNull(compose(s, ItemFeatures.unknown(s.getItemId() + 1), 1100, 1100));
    }
    @Test public void invalidPricesBucketsAndVolatilityFailClosed() throws Exception {
        BuyReplacementQuantityExpectedSlotOccupancyEvaluationSet s = source(); ItemFeatures f = ItemFeatures.unknown(s.getItemId());
        for (int value : new int[] {0, -1}) {
            assertNull(composer.compose(s, value, f, 300, 1100, 1100));
            assertNull(composer.compose(s, 990, f, value, 1100, 1100));
        }
        for (double v : new double[] {-1, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY})
            assertNull(compose(s, features(s.getItemId(), v), 1100, 1100));
    }
    @Test public void inclusiveFreshnessAndOrderingArePreserved() {
        BuyReplacementQuantityExpectedSlotOccupancyEvaluationSet s = source(); ItemFeatures f = ItemFeatures.unknown(s.getItemId());
        assertNotNull(compose(s, f, 980, 1100)); assertNull(compose(s, f, 979, 1100));
        assertNotNull(compose(s, f, 1100, 1220)); assertNull(compose(s, f, 1100, 1221));
        assertNull(compose(s, f, 1101, 1101)); assertNull(compose(s, f, 1100, 1099));
        assertNull(compose(s, f, -1, 1100)); assertNull(compose(s, f, 1100, -1));
        assertNull(compose(s, f, Long.MAX_VALUE, Long.MAX_VALUE));
        assertNull(compose(s, f, 1100, Long.MAX_VALUE));
    }
    @Test public void malformedEmptyAndNegativeTimestampSourcesFailClosed() {
        BuyReplacementQuantityExpectedSlotOccupancyEvaluationSet s = source(); ItemFeatures f = ItemFeatures.unknown(s.getItemId());
        assertNull(compose(new BuyReplacementQuantityExpectedSlotOccupancyEvaluationSet(null, s.getEvaluations(), 1100), f, 1100, 1100));
        assertNull(compose(new BuyReplacementQuantityExpectedSlotOccupancyEvaluationSet(s.getSource(), Collections.emptyList(), 1100), f, 1100, 1100));
        List<BuyReplacementQuantityExpectedSlotOccupancyEvaluation> list = new ArrayList<>(s.getEvaluations()); list.set(0, null);
        assertNull(compose(new BuyReplacementQuantityExpectedSlotOccupancyEvaluationSet(s.getSource(), list, 1100), f, 1100, 1100));
        assertNull(compose(new BuyReplacementQuantityExpectedSlotOccupancyEvaluationSet(s.getSource(), s.getEvaluations(), -1), f, 0, 1100));
    }
    @Test public void reorderedAlteredAndForeignEvidenceFailClosed() {
        BuyReplacementQuantityExpectedSlotOccupancyEvaluationSet s = source(); ItemFeatures f = ItemFeatures.unknown(s.getItemId());
        List<BuyReplacementQuantityExpectedSlotOccupancyEvaluation> list = new ArrayList<>(s.getEvaluations());
        assertTrue(list.size() > 1); Collections.reverse(list);
        assertNull(compose(new BuyReplacementQuantityExpectedSlotOccupancyEvaluationSet(s.getSource(), list, 1100), f, 1100, 1100));
        for (double hours : new double[] {0, -1, Double.NaN, Double.POSITIVE_INFINITY, 123}) {
            list = new ArrayList<>(s.getEvaluations());
            list.set(0, new BuyReplacementQuantityExpectedSlotOccupancyEvaluation(list.get(0).getSource(), hours));
            assertNull(compose(new BuyReplacementQuantityExpectedSlotOccupancyEvaluationSet(s.getSource(), list, 1100), f, 1100, 1100));
        }
        list = new ArrayList<>(s.getEvaluations()); list.set(0, source().getEvaluations().get(0));
        assertNull(compose(new BuyReplacementQuantityExpectedSlotOccupancyEvaluationSet(s.getSource(), list, 1100), f, 1100, 1100));
    }
    @Test public void deterministicCompositionDoesNotRefreshUpstreamTimestamp() {
        BuyReplacementQuantityExpectedSlotOccupancyEvaluationSet s = source(); ItemFeatures f = ItemFeatures.unknown(s.getItemId());
        BuyReplacementQuantityUnwindLossInputContext a = compose(s, f, 1100, 1150), b = compose(s, f, 1100, 1150);
        assertNotNull(a); assertNotNull(b); assertSame(a.getSource(), b.getSource());
        assertSame(a.getEvaluations(), b.getEvaluations()); assertEquals(a.getVolatility(), b.getVolatility(), 0);
        assertEquals(1100, a.getExpectedSlotOccupancyEvaluatedAt()); assertEquals(1150, a.getComposedAt());
    }
    @Test public void contractsArePackagePrivateAndCarryNoLossArithmeticOrAuthority() throws Exception {
        for (Class<?> c : new Class<?>[] {BuyReplacementQuantityUnwindLossInputContext.class, BuyReplacementQuantityUnwindLossInputContextComposer.class}) {
            assertFalse(Modifier.isPublic(c.getModifiers()));
            String text = java.nio.file.Files.readString(java.nio.file.Path.of("src/main/java/com/flippingfriend/companion/" + c.getSimpleName() + ".java"));
            for (String x : new String[] {"unwindCost(", "TaxCalculator", "getExitPrice", "getTotalUnwindLoss", "Math.sqrt", "PortfolioCandidate", "PolicyDecision", "OfferLifecycleAction", "REPLACEMENT_AUTHORIZED", "expectedGpPerSlotHour", "SqliteStore", "FillModel", "estimateBuy", "estimateSell", "schedule", "selected"}) assertFalse(x, text.contains(x));
        }
    }
    static BuyReplacementQuantityUnwindLossInputContext contextForCompanionTests() {
        BuyReplacementQuantityExpectedSlotOccupancyEvaluationSet s = source();
        return new BuyReplacementQuantityUnwindLossInputContextComposer().compose(s, 990, ItemFeatures.unknown(s.getItemId()), 300, 1100, 1100);
    }
    private BuyReplacementQuantityUnwindLossInputContext compose(BuyReplacementQuantityExpectedSlotOccupancyEvaluationSet s, ItemFeatures f, long marketAt, long at) {
        return composer.compose(s, 990, f, 300, marketAt, at);
    }
    private static BuyReplacementQuantityExpectedSlotOccupancyEvaluationSet source() {
        return BuyReplacementQuantityExpectedSlotOccupancyEvaluatorTest.evaluationSetForCompanionTests();
    }
    private static ItemFeatures features(int itemId, double volatility) throws Exception {
        // Test-only construction of exact raw feature values, including deliberately malformed scalars.
        Constructor<?> constructor = ItemFeatures.class.getDeclaredConstructors()[0];
        constructor.setAccessible(true); Class<?>[] types = constructor.getParameterTypes();
        Object[] args = new Object[types.length];
        for (int i = 0; i < types.length; i++) {
            if (types[i] == int.class) args[i] = 0;
            else if (types[i] == double.class) args[i] = 0.0;
            else args[i] = types[i].getEnumConstants()[0];
        }
        args[0] = itemId; args[4] = volatility;
        return (ItemFeatures) constructor.newInstance(args);
    }
}
