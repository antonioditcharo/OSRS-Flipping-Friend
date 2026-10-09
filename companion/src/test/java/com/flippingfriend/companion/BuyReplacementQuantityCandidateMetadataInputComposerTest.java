package com.flippingfriend.companion;

import com.flippingfriend.data.Candle;
import com.flippingfriend.model.ItemGroups;
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

public class BuyReplacementQuantityCandidateMetadataInputComposerTest {
    private final TaxCalculator tax = new TaxCalculator();
    private final BuyReplacementQuantityCandidateMetadataInputComposer composer =
            new BuyReplacementQuantityCandidateMetadataInputComposer(tax);

    @Test public void bindsExactSourcesAndPreservesOrderedEconomics() {
        BuyReplacementCandidateFillInputContext metadata = metadata("Abyssal whip", "intent-1", 1000);
        BuyReplacementQuantityFillInputContext fill = fill(metadata);
        BuyReplacementQuantityCandidateEconomicInputContext economic = economic(fill, tax);
        BuyReplacementQuantityCandidateMetadataInputContext result = composer.compose(economic, 1100);
        assertNotNull(result);
        assertSame(economic, result.getSource());
        assertSame(fill, result.getFillSource());
        assertSame(metadata, result.getMetadataSource());
        assertSame(economic.getInputs(), result.getInputs());
        assertEquals(metadata.getItemName(), result.getItemName());
        assertEquals(ItemGroups.groupOf(metadata.getItemName()), result.getItemGroup());
        assertEquals(economic.getIntentId(), result.getIntentId());
        assertEquals(economic.getOriginalOfferIdentity(), result.getOriginalOfferIdentity());
        assertEquals(economic.getRecommendationId(), result.getRecommendationId());
        assertEquals(1000, result.getMetadataInputComposedAt());
        assertEquals(1100, result.getEconomicInputComposedAt());
        assertEquals(1100, result.getRateEvaluatedAt());
        boolean smaller = false;
        for (int i = 0; i < result.getInputs().size(); i++) {
            assertSame(economic.getInputs().get(i), result.getInputs().get(i));
            smaller |= result.getInputs().get(i).getQuantity() < result.getExactRemainderQuantity();
        }
        assertTrue(smaller);
    }

    @Test public void productionGroupsAndUnchangedNamesIncludingUngroupedArePreserved() {
        for (String name : new String[] {"Abyssal whip", "Steel arrow", "  Grimy ranarr weed  "}) {
            var result = composer.compose(economic(fill(metadata(name, "intent-1", 1000)), tax), 1100);
            assertNotNull(result);
            assertEquals(name, result.getItemName());
            assertEquals(ItemGroups.groupOf(name), result.getItemGroup());
        }
        assertEquals("", composer.compose(economic(fill(metadata("Abyssal whip", "intent-1", 1000)), tax), 1100).getItemGroup());
    }

    @Test public void missingLegacyProvenanceAndBlankNamesFailClosed() {
        var original = fill(metadata("Abyssal whip", "intent-1", 1000));
        var grid = BuyReplacementQuantityGridComposerTest.quantityFillCandidateSetForCompanionTests();
        var legacy = new BuyReplacementQuantityFillInputContext(grid, original.getInputs(),
                original.getFillCurve(), original.getEffectiveHorizonHours(), original.getSeasonalMultiplier(),
                original.getHistoryObservedAt(), original.getMarketContextObservedAt(), original.getComposedAt());
        assertNull(composer.compose(economic(legacy, tax), 1100));
        for (String name : new String[] {"", "   "})
            assertNull(metadata(name, "intent-1", 1000));
    }

    @Test public void foreignCurveLineageHistoryAndHorizonFailClosed() {
        var metadata = metadata("Abyssal whip", "intent-1", 1000);
        var original = fill(metadata);
        var grid = BuyReplacementQuantityGridComposerTest.quantityFillCandidateSetForCompanionTests();
        for (var foreign : Arrays.asList(metadata("Abyssal whip", "intent-1", 1000),
                metadata("Abyssal whip", "foreign", 1000), metadata("Abyssal whip", "intent-1", 999))) {
            var changed = new BuyReplacementQuantityFillInputContext(grid, original.getInputs(),
                    original.getFillCurve(), original.getEffectiveHorizonHours(), 1,
                    original.getHistoryObservedAt(), 1100, 1100, foreign);
            assertNull(composer.compose(economic(changed, tax), 1100));
        }
        var horizon = new BuyReplacementQuantityFillInputContext(grid, original.getInputs(),
                original.getFillCurve(), 3, 1, original.getHistoryObservedAt(), 1100, 1100, metadata);
        assertNull(composer.compose(economic(horizon, tax), 1100));
    }

    @Test public void reorderedForeignNullEmptyAndAlteredHorizonInputsFailClosed() {
        var economic = economic(fill(metadata("Abyssal whip", "intent-1", 1000)), tax);
        List<BuyReplacementQuantityCandidateEconomicInput> list = new ArrayList<>(economic.getInputs());
        assertTrue(list.size() > 1);
        Collections.reverse(list);
        assertBad(economic, list);
        list = new ArrayList<>(economic.getInputs()); list.set(0, null); assertBad(economic, list);
        assertBad(economic, Collections.emptyList());
        list = new ArrayList<>(economic.getInputs());
        list.set(0, economic(fill(metadata("Abyssal whip", "intent-1", 1000)), tax).getInputs().get(0));
        assertBad(economic, list);
        for (double h : new double[] {0, -1, Double.NaN, Double.POSITIVE_INFINITY, 3}) {
            list = new ArrayList<>(economic.getInputs());
            list.set(0, new BuyReplacementQuantityCandidateEconomicInput(list.get(0).getSource(), h));
            assertBad(economic, list);
        }
    }

    @Test public void freshnessIsInclusiveAndCannotBeExtendedByLaterEconomicWrapper() {
        var economic = economic(fill(metadata("Abyssal whip", "intent-1", 1000)), tax);
        assertNotNull(composer.compose(economic, 1220));
        assertNull(composer.compose(economic, 1221));
        assertNull(composer.compose(economic, 1099));
        assertNull(composer.compose(economic, Long.MAX_VALUE));
        var later = new BuyReplacementQuantityCandidateEconomicInputContext(economic.getSource(), economic.getInputs(), 1150);
        var result = composer.compose(later, 1220);
        assertNotNull(result);
        assertEquals(1150, result.getEconomicInputComposedAt());
        assertEquals(1100, result.getRateEvaluatedAt());
        assertNull(composer.compose(later, 1221));
        assertNull(composer.compose(null, 1100));
        assertNull(new BuyReplacementQuantityCandidateMetadataInputComposer(null).compose(economic, 1100));
    }

    @Test public void negativeEconomicsRemainEvidenceAndInputsRemainImmutable() {
        TaxCalculator negative = new TaxCalculator() {
            @Override public long netMarginPerItem(int item, int buy, int sell) { return -5; }
            @Override public long netProfit(int item, int buy, int sell, int quantity) { return -5L * quantity; }
        };
        var economic = economic(fill(metadata("Abyssal whip", "intent-1", 1000)), negative);
        var binding = new BuyReplacementQuantityCandidateMetadataInputComposer(negative);
        var result = binding.compose(economic, 1100);
        assertNotNull(result);
        for (var input : result.getInputs()) {
            assertTrue(input.getExpectedProfit() < 0);
            assertTrue(input.getExpectedGpPerSlotHour() < 0);
        }
        try { result.getInputs().clear(); fail("immutable"); }
        catch (UnsupportedOperationException expected) { }
        var again = binding.compose(economic, 1100);
        assertSame(result.getSource(), again.getSource());
        assertSame(result.getMetadataSource(), again.getMetadataSource());
        assertEquals(result.getItemGroup(), again.getItemGroup());
    }

    @Test public void contractsArePackagePrivateAndOutsideAuthority() throws Exception {
        for (Class<?> type : new Class<?>[] {BuyReplacementQuantityCandidateMetadataInputContext.class,
                BuyReplacementQuantityCandidateMetadataInputComposer.class}) {
            assertFalse(Modifier.isPublic(type.getModifiers()));
            String text = Files.readString(Path.of("src/main/java/com/flippingfriend/companion/" + type.getSimpleName() + ".java"));
            for (String forbidden : new String[] {"new PortfolioCandidate", "PortfolioOptimizer", "PolicyDecision",
                    "OfferLifecycleAction", "REPLACEMENT_AUTHORIZED", "SqliteStore", "schedule", "Comparator"})
                assertFalse(forbidden, text.contains(forbidden));
        }
    }

    private void assertBad(BuyReplacementQuantityCandidateEconomicInputContext original,
            List<BuyReplacementQuantityCandidateEconomicInput> inputs) {
        assertNull(composer.compose(new BuyReplacementQuantityCandidateEconomicInputContext(
                original.getSource(), inputs, original.getComposedAt()), 1100));
    }
    private static BuyReplacementQuantityFillInputContext fill(BuyReplacementCandidateFillInputContext metadata) {
        return require(new BuyReplacementQuantityFillInputContextComposer().compose(
                BuyReplacementQuantityGridComposerTest.quantityFillCandidateSetForCompanionTests(), metadata, 1, 1100, 1100));
    }
    // Synthetic measurements are explicit test evidence; all wrappers share this one input context.
    private static BuyReplacementQuantityCandidateEconomicInputContext economic(
            BuyReplacementQuantityFillInputContext input, TaxCalculator tax) {
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
        return require(new BuyReplacementQuantityCandidateEconomicInputComposer(tax).compose(rate, 1100));
    }
    private static <T> T require(T value) { assertNotNull(value); return value; }
    private static BuyReplacementCandidateFillInputContext metadata(String name, String intent, long historyAt) {
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
        var pricing = new BuyReplacementPricingContext("assessment-1", "readiness-1", intent,
                "offer-1", "plan-1", 2, 4151, name, quantity, 1000, low, low + 1, quantity,
                20000, true, 100, 1100, 900, 900, 900, 1000);
        var strategy = new BuyReplacementPricingStrategyContext(pricing, "Balanced", offsets, 2.5);
        var candidates = new BuyReplacementPriceCandidateSet(strategy, prices);
        var affordability = new BuyReplacementCandidateAffordabilitySet(candidates, costs);
        var eligible = new BuyReplacementCandidateEligibilityEvaluator().evaluate(affordability); if (eligible == null) return null;
        return require(new BuyReplacementCandidateFillInputContextComposer().compose(eligible,
                Arrays.asList(new Candle(400, 1000, 990, 20, 20), new Candle(700, 1001, 991, 20, 20)), historyAt, 1000));
    }
}
