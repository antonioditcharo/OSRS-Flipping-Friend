package com.flippingfriend.companion;
import com.flippingfriend.data.Candle;
import com.flippingfriend.model.ItemGroups;
import com.flippingfriend.model.PriceOffset;
import com.flippingfriend.model.TaxCalculator;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;
public class BuyReplacementQuantityCandidateCapacityInputComposerTest {
    private final TaxCalculator tax = new TaxCalculator();
    private final BuyReplacementQuantityCandidateCapacityInputComposer composer =
            new BuyReplacementQuantityCandidateCapacityInputComposer(tax);
    @Test public void bindsRetainedLimitAndProductionExpiryPreservingInputs() {
        var meta = metadataContext(tax, 50, 5000);
        var result = composer.compose(meta, 1100);
        assertNotNull(result);
        assertSame(meta, result.getSource());
        assertSame(meta.getInputs(), result.getInputs());
        assertEquals(50, result.getBuyLimitRemaining());
        assertEquals(1000, result.getEligibilityEvaluatedAt());
        assertEquals(5000, result.getIntentExpiresAt());
        assertEquals(1150, result.getExpiresAt());
        assertEquals(1100, result.getComposedAt());
        assertEquals(meta.getItemName(), result.getItemName());
        assertEquals(ItemGroups.groupOf(meta.getItemName()), result.getItemGroup());
        assertEquals(5, result.getExactRemainderQuantity());
        boolean smaller = false;
        for (int i = 0; i < result.getInputs().size(); i++) {
            assertSame(meta.getInputs().get(i), result.getInputs().get(i));
            smaller |= result.getInputs().get(i).getQuantity() < result.getExactRemainderQuantity();
        }
        assertTrue(smaller);
    }
    @Test public void intentExpiryCapsCandidateExpiry() {
        var meta = metadataContext(tax, 5, 1100);
        var result = composer.compose(meta, 1100);
        assertNotNull(result);
        assertEquals(5, result.getBuyLimitRemaining());
        assertEquals(1100, result.getExpiresAt());
        assertNull(composer.compose(meta, 1101));
    }
    @Test public void expiryIsInclusiveAndLaterMetadataWrapperCannotExtendIt() {
        var meta = metadataContext(tax, 50, 5000);
        assertNotNull(composer.compose(meta, 1150));
        assertNull(composer.compose(meta, 1151));
        assertNull(composer.compose(meta, 1099));
        assertNull(composer.compose(meta, Long.MAX_VALUE));
        var later = new BuyReplacementQuantityCandidateMetadataInputContext(meta.getSource(),
                meta.getFillSource(), meta.getMetadataSource(), meta.getItemName(), meta.getItemGroup(), 1140);
        var result = composer.compose(later, 1150);
        assertNotNull(result);
        assertEquals(1150, result.getExpiresAt());
        assertNull(composer.compose(later, 1151));
    }
    @Test public void insufficientZeroCapacityAndExpiredIntentAreRefusedUpstream() {
        assertNull(metadata(4, 5000));
        assertNull(metadata(0, 5000));
        assertNull(metadata(-1, 5000));
        assertNull(metadata(5, 999));
    }
    @Test public void forgedForeignAndMissingProvenanceFailClosed() {
        var meta = metadataContext(tax, 50, 5000);
        var foreign = metadata(50, 5000);
        assertNotNull(foreign);
        for (var forged : Arrays.asList(
                wrap(meta, meta.getMetadataSource(), meta.getItemName(), "forged-group"),
                wrap(meta, meta.getMetadataSource(), "Dragon dagger", meta.getItemGroup()),
                wrap(meta, foreign, meta.getItemName(), meta.getItemGroup()),
                wrap(meta, null, meta.getItemName(), meta.getItemGroup())))
            assertNull(composer.compose(forged, 1100));
        var legacyFill = new BuyReplacementQuantityCandidateMetadataInputContext(meta.getSource(),
                null, meta.getMetadataSource(), meta.getItemName(), meta.getItemGroup(), 1100);
        assertNull(composer.compose(legacyFill, 1100));
        assertNull(composer.compose(null, 1100));
        assertNull(new BuyReplacementQuantityCandidateCapacityInputComposer(null).compose(meta, 1100));
    }
    @Test public void negativeEconomicsRemainEvidenceAndOutputsAreImmutableAndDeterministic() {
        TaxCalculator negative = new TaxCalculator() {
            @Override public long netMarginPerItem(int item, int buy, int sell) { return -5; }
            @Override public long netProfit(int item, int buy, int sell, int quantity) { return -5L * quantity; }
        };
        var meta = metadataContext(negative, 50, 5000);
        var binding = new BuyReplacementQuantityCandidateCapacityInputComposer(negative);
        var result = binding.compose(meta, 1100);
        assertNotNull(result);
        for (var input : result.getInputs()) {
            assertTrue(input.getExpectedProfit() < 0);
            assertTrue(input.getExpectedGpPerSlotHour() < 0);
        }
        try { result.getInputs().clear(); fail("immutable"); }
        catch (UnsupportedOperationException expected) { }
        var again = binding.compose(meta, 1100);
        assertSame(result.getSource(), again.getSource());
        assertEquals(result.getBuyLimitRemaining(), again.getBuyLimitRemaining());
        assertEquals(result.getExpiresAt(), again.getExpiresAt());
    }
    @Test public void localLifetimeMatchesProductionPlanLifetime() throws Exception {
        Field field = CandidateFactory.class.getDeclaredField("PLAN_TTL_SECONDS");
        field.setAccessible(true);
        assertTrue(Modifier.isStatic(field.getModifiers()));
        assertEquals(field.getLong(null), BuyReplacementQuantityCandidateCapacityInputComposer.CANDIDATE_TTL_SECONDS);
    }
    @Test public void contractsArePackagePrivateAndOutsideAuthority() throws Exception {
        for (Class<?> type : new Class<?>[] {BuyReplacementQuantityCandidateCapacityInputContext.class,
                BuyReplacementQuantityCandidateCapacityInputComposer.class}) {
            assertFalse(Modifier.isPublic(type.getModifiers()));
            String text = Files.readString(Path.of("src/main/java/com/flippingfriend/companion/" + type.getSimpleName() + ".java"));
            for (String forbidden : new String[] {"new PortfolioCandidate", "PortfolioOptimizer", "PolicyDecision",
                    "OfferLifecycleAction", "REPLACEMENT_AUTHORIZED", "SqliteStore", "BuyLimitLedger",
                    "schedule", "Comparator"})
                assertFalse(forbidden, text.contains(forbidden));
        }
    }
    private static BuyReplacementQuantityCandidateMetadataInputContext wrap(
            BuyReplacementQuantityCandidateMetadataInputContext meta,
            BuyReplacementCandidateFillInputContext source, String name, String group) {
        return new BuyReplacementQuantityCandidateMetadataInputContext(meta.getSource(),
                meta.getFillSource(), source, name, group, meta.getComposedAt());
    }
    // Dedicated coherent single-context chain; synthetic fill measurements are explicit test evidence.
    private static BuyReplacementQuantityCandidateMetadataInputContext metadataContext(
            TaxCalculator tax, int limit, long intentExpiresAt) {
        var input = require(new BuyReplacementQuantityFillInputContextComposer().compose(
                BuyReplacementQuantityGridComposerTest.quantityFillCandidateSetForCompanionTests(),
                require(metadata(limit, intentExpiresAt)), 1, 1100, 1100));
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
        return require(new BuyReplacementQuantityCandidateMetadataInputComposer(tax).compose(economic, 1100));
    }
    private static <T> T require(T value) { assertNotNull(value); return value; }
    private static BuyReplacementCandidateFillInputContext metadata(int limit, long intentExpiresAt) {
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
                "offer-1", "plan-1", 2, 4151, "Abyssal whip", quantity, 1000, low, low + 1, limit,
                20000, true, 100, intentExpiresAt, 900, 900, 900, 1000);
        var strategy = new BuyReplacementPricingStrategyContext(pricing, "Balanced", offsets, 2.5);
        var candidates = new BuyReplacementPriceCandidateSet(strategy, prices);
        var affordability = new BuyReplacementCandidateAffordabilitySet(candidates, costs);
        var eligible = new BuyReplacementCandidateEligibilityEvaluator().evaluate(affordability);
        if (eligible == null) return null;
        return new BuyReplacementCandidateFillInputContextComposer().compose(eligible,
                Arrays.asList(new Candle(400, 1000, 990, 20, 20), new Candle(700, 1001, 991, 20, 20)), 1000, 1000);
    }
}
