package com.flippingfriend.companion;

import com.flippingfriend.core.PortfolioCandidate;
import com.flippingfriend.data.ItemMetadata;
import com.flippingfriend.model.TaxCalculator;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public class BuyReplacementQuantityExpectedProfitEvaluatorTest {
    private final TaxCalculator tax = new TaxCalculator();
    private final BuyReplacementQuantityExpectedProfitEvaluator evaluator = new BuyReplacementQuantityExpectedProfitEvaluator(tax);
    @Test public void matchesPortfolioCandidateForEveryGeneratedQuantityAndPreservesEvidence() throws Exception {
        BuyReplacementQuantityUnwindLossEvaluationSet s = source(); BuyReplacementQuantityExpectedProfitEvaluationSet r = evaluator.evaluate(s, 1100);
        assertNotNull(r); assertSame(s, r.getSource()); assertSame(s.getEvaluations(), r.getUnwindLossEvaluations());
        assertEquals(s.getEvaluatedAt(), r.getUnwindLossEvaluatedAt()); assertEquals(1100, r.getEvaluatedAt());
        for (Method g : s.getClass().getDeclaredMethods()) {
            if (g.getParameterCount() != 0 || g.getName().equals("getSource") || g.getName().equals("getEvaluations") || g.getName().equals("getEvaluatedAt")) continue;
            assertEquals(g.invoke(s), r.getClass().getDeclaredMethod(g.getName()).invoke(r));
        }
        boolean belowCeiling = false;
        for (int i = 0; i < r.getEvaluations().size(); i++) {
            BuyReplacementQuantityUnwindLossEvaluation a = s.getEvaluations().get(i); BuyReplacementQuantityExpectedProfitEvaluation e = r.getEvaluations().get(i);
            assertSame(a, e.getSource()); assertEquals(a.getExpectedProfit(), e.getUpstreamExpectedProfit(), 0);
            long net = tax.netProfit(s.getItemId(), a.getBuyPrice(), a.getSellPrice(), a.getQuantity());
            assertEquals(net, e.getCompletedSaleNetProfit()); assertEquals(production(a, net), e.getQuantityExpectedProfit(), 0);
            assertEquals(a.getCompletionProbability(), e.getCompletedProbability(), 0);
            assertEquals(a.getBuyProbability() * (1 - a.getSellProbability()), e.getStrandedProbability(), 0);
            if (a.getQuantity() < s.getExactRemainderQuantity()) { belowCeiling = true;
                assertNotEquals(tax.netProfit(s.getItemId(), a.getBuyPrice(), a.getSellPrice(), s.getExactRemainderQuantity()), net); }
        }
        assertTrue(belowCeiling);
    }
    @Test public void itemAwareTaxExemptionIsUsedWithoutResolutionInsideEvaluator() {
        BuyReplacementQuantityUnwindLossEvaluationSet s = source(); TaxCalculator exempt = new TaxCalculator();
        exempt.resolveExemptions(Collections.singletonList(new ItemMetadata(s.getItemId(), "Old school bond", false, 100, 0)));
        BuyReplacementQuantityExpectedProfitEvaluationSet r = new BuyReplacementQuantityExpectedProfitEvaluator(exempt).evaluate(recompute(s, exempt), 1100); assertNotNull(r);
        for (BuyReplacementQuantityExpectedProfitEvaluation e : r.getEvaluations())
            assertEquals((long)(e.getSellPrice() - e.getBuyPrice()) * e.getQuantity(), e.getCompletedSaleNetProfit());
    }
    @Test public void negativeExpectedProfitAndNegativeCompletedMarginRemainEvidence() {
        TaxCalculator negative = new TaxCalculator() {
            @Override public long netMarginPerItem(int item, int buy, int sell) { return -5; }
            @Override public long netProfit(int item, int buy, int sell, int quantity) { return -5L * quantity; }
        };
        BuyReplacementQuantityExpectedProfitEvaluationSet r =
                new BuyReplacementQuantityExpectedProfitEvaluator(negative).evaluate(source(), 1100);
        assertNotNull(r);
        for (BuyReplacementQuantityExpectedProfitEvaluation e : r.getEvaluations()) {
            assertEquals(-5L * e.getQuantity(), e.getCompletedSaleNetProfit());
            assertTrue(e.getQuantityExpectedProfit() < 0);
        }
    }
    @Test public void inclusiveFreshnessAndUnavailableAuthorityFailClosed() {
        assertNotNull(evaluator.evaluate(source(), 1220)); assertNull(evaluator.evaluate(source(), 1221));
        assertNull(evaluator.evaluate(source(), 1099)); assertNull(evaluator.evaluate(source(), Long.MAX_VALUE));
        assertNull(evaluator.evaluate(null, 1100)); assertNull(new BuyReplacementQuantityExpectedProfitEvaluator(null).evaluate(source(), 1100));
    }
    @Test public void reorderedAlteredForeignAndEmptyUnwindEvidenceFailClosed() {
        BuyReplacementQuantityUnwindLossEvaluationSet s = source(); List<BuyReplacementQuantityUnwindLossEvaluation> list = new ArrayList<>(s.getEvaluations());
        assertTrue(list.size() > 1); Collections.reverse(list); assertBad(s, list);
        list = new ArrayList<>(s.getEvaluations()); list.set(0, null); assertBad(s, list);
        BuyReplacementQuantityUnwindLossEvaluation e = s.getEvaluations().get(0);
        list = new ArrayList<>(s.getEvaluations()); list.set(0, changed(e, e.getTotalUnwindLoss() + 1)); assertBad(s, list);
        list = new ArrayList<>(s.getEvaluations()); list.set(0, changed(e, -1)); assertBad(s, list);
        list = new ArrayList<>(s.getEvaluations()); list.set(0, source().getEvaluations().get(0)); assertBad(s, list);
        assertBad(s, Collections.emptyList());
    }
    @Test public void alteredExitDriftTaxAndLossEvidenceFailsClosed() {
        BuyReplacementQuantityUnwindLossEvaluationSet s = source(); BuyReplacementQuantityUnwindLossEvaluation e = s.getEvaluations().get(0);
        for (BuyReplacementQuantityUnwindLossEvaluation bad : new BuyReplacementQuantityUnwindLossEvaluation[] {
                new BuyReplacementQuantityUnwindLossEvaluation(e.getSource(), e.getDriftFraction() + 0.01, e.getExitPrice(), e.getExitTaxPerItem(), e.getLossPerItem(), e.getTotalUnwindLoss()),
                new BuyReplacementQuantityUnwindLossEvaluation(e.getSource(), e.getDriftFraction(), e.getExitPrice() + 1, e.getExitTaxPerItem(), e.getLossPerItem(), e.getTotalUnwindLoss()),
                new BuyReplacementQuantityUnwindLossEvaluation(e.getSource(), e.getDriftFraction(), e.getExitPrice(), e.getExitTaxPerItem() + 1, e.getLossPerItem(), e.getTotalUnwindLoss()),
                new BuyReplacementQuantityUnwindLossEvaluation(e.getSource(), e.getDriftFraction(), e.getExitPrice(), e.getExitTaxPerItem(), e.getLossPerItem() + 1, e.getTotalUnwindLoss()) }) {
            List<BuyReplacementQuantityUnwindLossEvaluation> list = new ArrayList<>(s.getEvaluations()); list.set(0, bad); assertBad(s, list);
        }
    }
    @Test public void invalidInjectedTaxCoherenceFailsClosed() {
        TaxCalculator bad = new TaxCalculator() {
            @Override public long netProfit(int item, int buy, int sell, int quantity) { return super.netProfit(item, buy, sell, quantity) + 1; }
        };
        assertNull(new BuyReplacementQuantityExpectedProfitEvaluator(bad).evaluate(recompute(source(), bad), 1100));
    }
    @Test public void listIsDefensiveImmutableAndDeterministic() {
        BuyReplacementQuantityUnwindLossEvaluationSet s = source(); BuyReplacementQuantityExpectedProfitEvaluationSet r = evaluator.evaluate(s, 1100);
        List<BuyReplacementQuantityExpectedProfitEvaluation> copy = new ArrayList<>(r.getEvaluations()); BuyReplacementQuantityExpectedProfitEvaluationSet d = new BuyReplacementQuantityExpectedProfitEvaluationSet(s, copy, 1100); copy.clear();
        assertEquals(r.getEvaluations().size(), d.getEvaluations().size());
        try { r.getEvaluations().clear(); fail("immutable"); } catch (UnsupportedOperationException expected) {}
        assertEquals(r.getEvaluations().get(0).getQuantityExpectedProfit(), evaluator.evaluate(s, 1100).getEvaluations().get(0).getQuantityExpectedProfit(), 0);
    }
    @Test public void contractsRemainPackagePrivateAndOutsideLaterEconomicsOrAuthority() throws Exception {
        for (Class<?> c : new Class<?>[] {BuyReplacementQuantityExpectedProfitEvaluation.class, BuyReplacementQuantityExpectedProfitEvaluationSet.class, BuyReplacementQuantityExpectedProfitEvaluator.class}) {
            assertFalse(Modifier.isPublic(c.getModifiers()));
            String text = java.nio.file.Files.readString(java.nio.file.Path.of("src/main/java/com/flippingfriend/companion/" + c.getSimpleName() + ".java"));
            for (String x : new String[] {"getWorstLossInput", "expectedGpPerSlotHour", "kellyFraction", "PortfolioOptimizer", "PolicyDecision", "OfferLifecycleAction", "REPLACEMENT_AUTHORIZED", "SqliteStore", "schedule", "selected", "rank"}) assertFalse(x, text.contains(x));
        }
    }
    static BuyReplacementQuantityExpectedProfitEvaluationSet evaluationSetForCompanionTests() { return new BuyReplacementQuantityExpectedProfitEvaluator(new TaxCalculator()).evaluate(source(), 1100); }
    private static BuyReplacementQuantityUnwindLossEvaluationSet source() { return BuyReplacementQuantityUnwindLossEvaluatorTest.evaluationSetForCompanionTests(); }
    private static BuyReplacementQuantityUnwindLossEvaluation changed(BuyReplacementQuantityUnwindLossEvaluation e, long total) { return new BuyReplacementQuantityUnwindLossEvaluation(e.getSource(), e.getDriftFraction(), e.getExitPrice(), e.getExitTaxPerItem(), e.getLossPerItem(), total); }
    private void assertBad(BuyReplacementQuantityUnwindLossEvaluationSet s, List<BuyReplacementQuantityUnwindLossEvaluation> list) { assertNull(evaluator.evaluate(new BuyReplacementQuantityUnwindLossEvaluationSet(s.getSource(), list, 1100), 1100)); }
    private static BuyReplacementQuantityUnwindLossEvaluationSet recompute(BuyReplacementQuantityUnwindLossEvaluationSet s, TaxCalculator tax) { return new BuyReplacementQuantityUnwindLossEvaluator(tax).evaluate(s.getSource(), 1100); }
    private static double production(BuyReplacementQuantityUnwindLossEvaluation e, long net) {
        return new PortfolioCandidate(1, "fixture", "", e.getBuyPrice(), e.getBuyPrice(), e.getBuyPrice(), e.getSellPrice(), e.getSellPrice(), e.getSellPrice(),
                e.getQuantity(), e.getFillableQuantity(), net, e.getWorstLoss(), e.getTotalUnwindLoss(), e.getBuyProbability(), e.getSellProbability(),
                e.getCalibratedBuyHours(), e.getCalibratedSellHours(), 4, 1300).expectedProfit();
    }
}
