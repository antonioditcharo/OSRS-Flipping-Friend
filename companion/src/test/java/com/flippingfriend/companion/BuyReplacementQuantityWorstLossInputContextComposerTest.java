package com.flippingfriend.companion;

import com.flippingfriend.model.RiskAppetite;
import com.flippingfriend.model.TaxCalculator;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public class BuyReplacementQuantityWorstLossInputContextComposerTest {
    private final TaxCalculator tax = new TaxCalculator();
    private final BuyReplacementQuantityWorstLossInputContextComposer composer = new BuyReplacementQuantityWorstLossInputContextComposer(tax);
    @Test public void bindsProductionLossCutAndPreservesEveryGetterAndOrderedEvidence() throws Exception {
        BuyReplacementQuantityExpectedProfitEvaluationSet s = source(); BuyReplacementQuantityWorstLossInputContext r = composer.compose(s, 1100);
        assertNotNull(r); assertSame(s, r.getSource()); assertSame(s.getEvaluations(), r.getEvaluations());
        assertEquals(RiskAppetite.forName(s.getAppetiteName()).getLossCutPct(), r.getLossCutPct(), 0);
        assertEquals(s.getEvaluatedAt(), r.getExpectedProfitEvaluatedAt()); assertEquals(1100, r.getComposedAt());
        for (Method g : s.getClass().getDeclaredMethods()) {
            if (g.getParameterCount() != 0 || g.getName().equals("getSource") || g.getName().equals("getEvaluations") || g.getName().equals("getEvaluatedAt")) continue;
            assertEquals(g.invoke(s), r.getClass().getDeclaredMethod(g.getName()).invoke(r));
        }
        for (int i = 0; i < s.getEvaluations().size(); i++) assertSame(s.getEvaluations().get(i), r.getEvaluations().get(i));
        try { r.getEvaluations().clear(); fail("immutable upstream list"); } catch (UnsupportedOperationException expected) {}
    }
    @Test public void productionAppetitesExposeDistinctPositiveFiniteLossCuts() {
        for (RiskAppetite appetite : new RiskAppetite[] {RiskAppetite.CAUTIOUS, RiskAppetite.BALANCED, RiskAppetite.AGGRESSIVE}) {
            assertTrue(Double.isFinite(appetite.getLossCutPct())); assertTrue(appetite.getLossCutPct() > 0);
            assertSame(appetite, RiskAppetite.forName(appetite.getName()));
        }
        assertEquals(0.02, RiskAppetite.CAUTIOUS.getLossCutPct(), 0);
        assertEquals(0.05, RiskAppetite.BALANCED.getLossCutPct(), 0);
        assertEquals(0.12, RiskAppetite.AGGRESSIVE.getLossCutPct(), 0);
    }
    @Test public void inclusiveFreshnessUnavailableAndFutureTimeFailClosed() {
        assertNotNull(composer.compose(source(), 1220)); assertNull(composer.compose(source(), 1221));
        assertNull(composer.compose(source(), 1099)); assertNull(composer.compose(source(), Long.MAX_VALUE));
        assertNull(composer.compose(null, 1100)); assertNull(new BuyReplacementQuantityWorstLossInputContextComposer(null).compose(source(), 1100));
    }
    @Test public void reorderedNullEmptyAndForeignEvidenceFailClosed() {
        BuyReplacementQuantityExpectedProfitEvaluationSet s = source(); List<BuyReplacementQuantityExpectedProfitEvaluation> list = new ArrayList<>(s.getEvaluations());
        assertTrue(list.size() > 1); Collections.reverse(list); assertBad(s, list);
        list = new ArrayList<>(s.getEvaluations()); list.set(0, null); assertBad(s, list);
        list = new ArrayList<>(s.getEvaluations()); list.set(0, source().getEvaluations().get(0)); assertBad(s, list);
        assertBad(s, Collections.emptyList());
    }
    @Test public void alteredQuantityEconomicsAndProbabilityEvidenceFailClosed() {
        BuyReplacementQuantityExpectedProfitEvaluationSet s = source(); BuyReplacementQuantityExpectedProfitEvaluation e = s.getEvaluations().get(0);
        for (BuyReplacementQuantityExpectedProfitEvaluation bad : new BuyReplacementQuantityExpectedProfitEvaluation[] {
                changed(e, e.getCompletedSaleNetProfit() + 1, e.getCompletedProbability(), e.getStrandedProbability(), e.getQuantityExpectedProfit()),
                changed(e, e.getCompletedSaleNetProfit(), e.getCompletedProbability() + 0.01, e.getStrandedProbability(), e.getQuantityExpectedProfit()),
                changed(e, e.getCompletedSaleNetProfit(), e.getCompletedProbability(), e.getStrandedProbability() + 0.01, e.getQuantityExpectedProfit()),
                changed(e, e.getCompletedSaleNetProfit(), e.getCompletedProbability(), e.getStrandedProbability(), e.getQuantityExpectedProfit() + 1),
                changed(e, e.getCompletedSaleNetProfit(), e.getCompletedProbability(), e.getStrandedProbability(), Double.NaN) }) {
            List<BuyReplacementQuantityExpectedProfitEvaluation> list = new ArrayList<>(s.getEvaluations()); list.set(0, bad); assertBad(s, list);
        }
    }
    @Test public void negativeQuantityExpectedProfitRemainsValidEvidence() {
        TaxCalculator negative = new TaxCalculator() {
            @Override public long netMarginPerItem(int item, int buy, int sell) { return -5; }
            @Override public long netProfit(int item, int buy, int sell, int quantity) { return -5L * quantity; }
        };
        BuyReplacementQuantityUnwindLossEvaluationSet unwind = BuyReplacementQuantityUnwindLossEvaluatorTest.evaluationSetForCompanionTests();
        BuyReplacementQuantityExpectedProfitEvaluationSet s = new BuyReplacementQuantityExpectedProfitEvaluator(negative).evaluate(unwind, 1100);
        assertNotNull(s); assertTrue(s.getEvaluations().get(0).getQuantityExpectedProfit() < 0);
        BuyReplacementQuantityWorstLossInputContext r = new BuyReplacementQuantityWorstLossInputContextComposer(negative).compose(s, 1100);
        assertNotNull(r); assertSame(s.getEvaluations(), r.getEvaluations());
    }
    @Test public void deterministicCompositionDoesNotRefreshExpectedProfitTimestamp() {
        BuyReplacementQuantityExpectedProfitEvaluationSet s = source(); BuyReplacementQuantityWorstLossInputContext a = composer.compose(s, 1150), b = composer.compose(s, 1150);
        assertNotNull(a); assertNotNull(b); assertSame(a.getSource(), b.getSource());
        assertSame(a.getEvaluations(), b.getEvaluations()); assertEquals(a.getLossCutPct(), b.getLossCutPct(), 0);
        assertEquals(1100, a.getExpectedProfitEvaluatedAt()); assertEquals(1150, a.getComposedAt());
    }
    @Test public void contractsArePackagePrivateAndCarryNoWorstLossArithmeticOrAuthority() throws Exception {
        for (Class<?> c : new Class<?>[] {BuyReplacementQuantityWorstLossInputContext.class, BuyReplacementQuantityWorstLossInputContextComposer.class}) {
            assertFalse(Modifier.isPublic(c.getModifiers()));
            String text = java.nio.file.Files.readString(java.nio.file.Path.of("src/main/java/com/flippingfriend/companion/" + c.getSimpleName() + ".java"));
            for (String x : new String[] {"Math.max(1", "lossCutPct *", "getExpectedGpPerSlotHour", "kellyFraction", "PortfolioCandidate", "PortfolioOptimizer", "PolicyDecision", "OfferLifecycleAction", "REPLACEMENT_AUTHORIZED", "SqliteStore", "schedule", "selected", "rank"}) assertFalse(x, text.contains(x));
        }
    }
    static BuyReplacementQuantityWorstLossInputContext contextForCompanionTests() { return new BuyReplacementQuantityWorstLossInputContextComposer(new TaxCalculator()).compose(source(), 1100); }
    private static BuyReplacementQuantityExpectedProfitEvaluationSet source() { return BuyReplacementQuantityExpectedProfitEvaluatorTest.evaluationSetForCompanionTests(); }
    private void assertBad(BuyReplacementQuantityExpectedProfitEvaluationSet s, List<BuyReplacementQuantityExpectedProfitEvaluation> list) { assertNull(composer.compose(new BuyReplacementQuantityExpectedProfitEvaluationSet(s.getSource(), list, 1100), 1100)); }
    private static BuyReplacementQuantityExpectedProfitEvaluation changed(BuyReplacementQuantityExpectedProfitEvaluation e, long net, double completed, double stranded, double expected) {
        return new BuyReplacementQuantityExpectedProfitEvaluation(e.getSource(), net, completed, stranded, expected);
    }
}
