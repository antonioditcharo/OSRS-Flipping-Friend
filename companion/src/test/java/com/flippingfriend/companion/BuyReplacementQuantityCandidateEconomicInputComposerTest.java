package com.flippingfriend.companion;

import com.flippingfriend.model.TaxCalculator;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public class BuyReplacementQuantityCandidateEconomicInputComposerTest {
    private final TaxCalculator tax = new TaxCalculator();
    private final BuyReplacementQuantityCandidateEconomicInputComposer composer =
            new BuyReplacementQuantityCandidateEconomicInputComposer(tax);

    @Test public void bindsEveryEconomicInputInOriginalOrderWithoutNormalizing() {
        BuyReplacementQuantityExpectedGpPerSlotHourEvaluationSet source = source();
        BuyReplacementQuantityCandidateEconomicInputContext result = composer.compose(source, 1100);
        assertNotNull(result);
        assertSame(source, result.getSource());
        assertEquals(source.getEvaluations().size(), result.getInputs().size());
        assertEquals(source.getIntentId(), result.getIntentId());
        assertEquals(source.getOriginalOfferIdentity(), result.getOriginalOfferIdentity());
        assertEquals(source.getRecommendationId(), result.getRecommendationId());
        assertEquals(source.getItemId(), result.getItemId());
        assertEquals(source.getExactRemainderQuantity(), result.getExactRemainderQuantity());
        assertEquals(source.getEvaluatedAt(), result.getRateEvaluatedAt());
        assertEquals(1100, result.getComposedAt());
        for (int i = 0; i < result.getInputs().size(); i++) {
            BuyReplacementQuantityExpectedGpPerSlotHourEvaluation e = source.getEvaluations().get(i);
            BuyReplacementQuantityCandidateEconomicInput input = result.getInputs().get(i);
            assertSame(e, input.getSource());
            assertEquals(e.getBuyPrice(), input.getTargetBuyPrice());
            assertEquals(e.getBuyPrice(), input.getEquilibriumBuyPrice());
            assertEquals(e.getBuyPrice(), input.getExitBuyPrice());
            assertEquals(e.getSellPrice(), input.getTargetSellPrice());
            assertEquals(e.getSellPrice(), input.getEquilibriumSellPrice());
            assertEquals(e.getSellPrice(), input.getExitSellPrice());
            assertEquals(e.getQuantity(), input.getQuantity());
            assertEquals(e.getFillableQuantity(), input.getExactRemainderQuantity());
            assertEquals(e.getQuantityNetProfit(), input.getNetProfit());
            assertEquals(e.getQuantityWorstLoss(), input.getWorstLoss());
            assertEquals(e.getQuantityUnwindLoss(), input.getUnwindLoss());
            assertEquals(e.getBuyProbability(), input.getBuyFillProbability(), 0);
            assertEquals(e.getSellProbability(), input.getSellFillProbability(), 0);
            assertEquals(e.getCalibratedBuyHours(), input.getBuyHours(), 0);
            assertEquals(e.getCalibratedSellHours(), input.getSellHours(), 0);
            assertEquals(source.getEffectiveHorizonHours(), input.getHorizonHours(), 0);
            assertEquals(e.getQuantityExpectedProfit(), input.getExpectedProfit(), 0);
            assertEquals(e.getExpectedSlotHours(), input.getExpectedSlotHours(), 0);
            assertEquals(e.getQuantityExpectedGpPerSlotHour(), input.getExpectedGpPerSlotHour(), 0);
        }
    }

    @Test public void generatedQuantityRemainsDistinctFromExactRemainder() {
        BuyReplacementQuantityCandidateEconomicInputContext result = composer.compose(source(), 1100);
        assertNotNull(result);
        boolean below = false;
        for (BuyReplacementQuantityCandidateEconomicInput input : result.getInputs()) {
            assertEquals(result.getExactRemainderQuantity(), input.getExactRemainderQuantity());
            assertTrue(input.getQuantity() > 0);
            assertTrue(input.getQuantity() <= input.getExactRemainderQuantity());
            below |= input.getQuantity() < input.getExactRemainderQuantity();
        }
        assertTrue(below);
    }

    @Test public void inclusiveFreshnessDoesNotRefreshRateEvidence() {
        BuyReplacementQuantityExpectedGpPerSlotHourEvaluationSet source = source();
        BuyReplacementQuantityCandidateEconomicInputContext result = composer.compose(source, 1220);
        assertNotNull(result);
        assertSame(source, result.getSource());
        assertEquals(1100, result.getRateEvaluatedAt());
        assertEquals(1220, result.getComposedAt());
        assertNull(composer.compose(source, 1221));
        assertNull(composer.compose(source, 1099));
        assertNull(composer.compose(source, Long.MAX_VALUE));
        assertNull(composer.compose(null, 1100));
        assertNull(new BuyReplacementQuantityCandidateEconomicInputComposer(null).compose(source, 1100));
    }

    @Test public void reorderedForeignNullEmptyAndAlteredRateFailClosed() {
        BuyReplacementQuantityExpectedGpPerSlotHourEvaluationSet source = source();
        List<BuyReplacementQuantityExpectedGpPerSlotHourEvaluation> list = new ArrayList<>(source.getEvaluations());
        assertTrue(list.size() > 1);
        Collections.reverse(list);
        assertBad(source, list);
        list = new ArrayList<>(source.getEvaluations());
        list.set(0, source().getEvaluations().get(0));
        assertBad(source, list);
        list = new ArrayList<>(source.getEvaluations());
        list.set(0, null);
        assertBad(source, list);
        assertBad(source, Collections.emptyList());
        BuyReplacementQuantityExpectedGpPerSlotHourEvaluation first = source.getEvaluations().get(0);
        for (double rate : new double[] {Double.NaN, Double.POSITIVE_INFINITY,
                first.getQuantityExpectedGpPerSlotHour() + 1}) {
            list = new ArrayList<>(source.getEvaluations());
            list.set(0, new BuyReplacementQuantityExpectedGpPerSlotHourEvaluation(first.getSource(), rate));
            assertBad(source, list);
        }
    }

    @Test public void forgedUpstreamKellyEvidenceIsNotAccepted() {
        BuyReplacementQuantityExpectedGpPerSlotHourEvaluationSet source = source();
        BuyReplacementQuantityKellyFractionEvaluationSet kelly = source.getSource();
        List<BuyReplacementQuantityKellyFractionEvaluation> list = new ArrayList<>(kelly.getEvaluations());
        BuyReplacementQuantityKellyFractionEvaluation first = list.get(0);
        list.set(0, new BuyReplacementQuantityKellyFractionEvaluation(first.getSource(),
                first.getQuantityKellyFraction() + 0.01));
        BuyReplacementQuantityKellyFractionEvaluationSet forged =
                new BuyReplacementQuantityKellyFractionEvaluationSet(kelly.getSource(), list, 1100);
        assertNull(composer.compose(new BuyReplacementQuantityExpectedGpPerSlotHourEvaluationSet(
                forged, source.getEvaluations(), 1100), 1100));
    }

    @Test public void negativeEconomicEvidenceRemainsInputNotARefusal() {
        TaxCalculator negative = new TaxCalculator() {
            @Override public long netMarginPerItem(int item, int buy, int sell) { return -5; }
            @Override public long netProfit(int item, int buy, int sell, int quantity) { return -5L * quantity; }
        };
        BuyReplacementQuantityExpectedProfitEvaluationSet profit =
                new BuyReplacementQuantityExpectedProfitEvaluator(negative).evaluate(
                        BuyReplacementQuantityUnwindLossEvaluatorTest.evaluationSetForCompanionTests(), 1100);
        BuyReplacementQuantityWorstLossInputContext risk =
                new BuyReplacementQuantityWorstLossInputContextComposer(negative).compose(profit, 1100);
        BuyReplacementQuantityWorstLossEvaluationSet loss =
                new BuyReplacementQuantityWorstLossEvaluator(negative).evaluate(risk, 1100);
        BuyReplacementQuantityKellySizingInputContext sizing =
                new BuyReplacementQuantityKellySizingInputContextComposer(negative).compose(loss, 1100);
        BuyReplacementQuantityKellyFractionEvaluationSet fraction =
                new BuyReplacementQuantityKellyFractionEvaluator(negative).evaluate(sizing, 1100);
        BuyReplacementQuantityExpectedGpPerSlotHourEvaluationSet rate =
                new BuyReplacementQuantityExpectedGpPerSlotHourEvaluator(negative).evaluate(fraction, 1100);
        BuyReplacementQuantityCandidateEconomicInputContext result =
                new BuyReplacementQuantityCandidateEconomicInputComposer(negative).compose(rate, 1100);
        assertNotNull(result);
        assertEquals(rate.getEvaluations().size(), result.getInputs().size());
        for (BuyReplacementQuantityCandidateEconomicInput input : result.getInputs()) {
            assertTrue(input.getNetProfit() < 0);
            assertTrue(input.getExpectedProfit() < 0);
            assertTrue(input.getExpectedGpPerSlotHour() < 0);
        }
    }

    @Test public void immutableDefensiveAndDeterministic() {
        BuyReplacementQuantityExpectedGpPerSlotHourEvaluationSet source = source();
        BuyReplacementQuantityCandidateEconomicInputContext result = composer.compose(source, 1100);
        List<BuyReplacementQuantityCandidateEconomicInput> copy = new ArrayList<>(result.getInputs());
        BuyReplacementQuantityCandidateEconomicInputContext context =
                new BuyReplacementQuantityCandidateEconomicInputContext(source, copy, 1100);
        copy.clear();
        assertEquals(result.getInputs().size(), context.getInputs().size());
        try { result.getInputs().clear(); fail("immutable"); }
        catch (UnsupportedOperationException expected) { }
        BuyReplacementQuantityCandidateEconomicInputContext again = composer.compose(source, 1100);
        for (int i = 0; i < result.getInputs().size(); i++) {
            assertSame(result.getInputs().get(i).getSource(), again.getInputs().get(i).getSource());
            assertEquals(result.getInputs().get(i).getExpectedGpPerSlotHour(),
                    again.getInputs().get(i).getExpectedGpPerSlotHour(), 0);
        }
    }

    @Test public void contractsRemainPackagePrivateWithoutAuthorityOrConstruction() throws Exception {
        for (Class<?> type : new Class<?>[] {BuyReplacementQuantityCandidateEconomicInput.class,
                BuyReplacementQuantityCandidateEconomicInputContext.class,
                BuyReplacementQuantityCandidateEconomicInputComposer.class}) {
            assertFalse(Modifier.isPublic(type.getModifiers()));
            String text = Files.readString(Path.of("src/main/java/com/flippingfriend/companion/"
                    + type.getSimpleName() + ".java"));
            for (String forbidden : new String[] {"new PortfolioCandidate", "PortfolioOptimizer",
                    "PolicyDecision", "OfferLifecycleAction", "REPLACEMENT_AUTHORIZED", "SqliteStore",
                    "schedule", "Comparator", "selected"}) assertFalse(forbidden, text.contains(forbidden));
        }
    }

    private static BuyReplacementQuantityExpectedGpPerSlotHourEvaluationSet source() {
        return BuyReplacementQuantityExpectedGpPerSlotHourEvaluatorTest.evaluationSetForCompanionTests();
    }
    private void assertBad(BuyReplacementQuantityExpectedGpPerSlotHourEvaluationSet source,
            List<BuyReplacementQuantityExpectedGpPerSlotHourEvaluation> list) {
        assertNull(composer.compose(new BuyReplacementQuantityExpectedGpPerSlotHourEvaluationSet(
                source.getSource(), list, source.getEvaluatedAt()), 1100));
    }
}
