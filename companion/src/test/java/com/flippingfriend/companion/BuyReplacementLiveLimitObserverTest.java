package com.flippingfriend.companion;
import com.flippingfriend.core.OfferEvent;
import com.flippingfriend.data.Candle;
import com.flippingfriend.model.PriceOffset;
import com.flippingfriend.model.TaxCalculator;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import static org.junit.Assert.*;
public class BuyReplacementLiveLimitObserverTest {
    private static final int ITEM = 4151;
    private static final int LIMIT = 70;
    private final BuyReplacementLiveLimitObserver observer = new BuyReplacementLiveLimitObserver();
    private static Map<Integer, MarketIngestionService.Item> mapping(int limit) {
        Map<Integer, MarketIngestionService.Item> mapping = new HashMap<>();
        mapping.put(ITEM, new MarketIngestionService.Item(ITEM, "Abyssal whip", limit));
        return mapping;
    }
    private static OfferEvent buy(int filled, long at, int slot) {
        return OfferEvent.builder("c", at, "FILL").slot(slot).firstSeenAt(at).item(ITEM, "Abyssal whip")
                .buying(true).price(990).quantities(LIMIT, filled).spent(990L * filled).build();
    }
    @Test public void explicitTimeReadMatchesProductionRemainingForCurrentWindows() {
        BuyLimitLedger ledger = new BuyLimitLedger();
        long now = Instant.now().getEpochSecond();
        ledger.apply(buy(20, now - 60, 0));
        ledger.apply(buy(15, now - 30, 1));
        Map<Integer, MarketIngestionService.Item> mapping = mapping(LIMIT);
        mapping.put(561, new MarketIngestionService.Item(561, "Nature rune", 10_000));
        Map<Integer, Integer> production = ledger.remaining(mapping);
        assertEquals(Integer.valueOf(35), production.get(ITEM));
        assertEquals(production.get(ITEM).intValue(), ledger.remainingAt(ITEM, LIMIT, Instant.ofEpochSecond(now)));
        assertEquals(production.get(561).intValue(), ledger.remainingAt(561, 10_000, Instant.ofEpochSecond(now)));
        BuyLimitLedger expired = new BuyLimitLedger();
        expired.apply(buy(40, now - 5 * 3600, 0));
        assertEquals(expired.remaining(mapping).get(ITEM).intValue(),
                expired.remainingAt(ITEM, LIMIT, Instant.ofEpochSecond(now)));
        assertEquals(LIMIT, expired.remainingAt(ITEM, LIMIT, Instant.ofEpochSecond(now)));
    }
    @Test public void explicitTimeUsesAnchoredInclusiveWindowAndChangesNothing() {
        BuyLimitLedger ledger = new BuyLimitLedger();
        ledger.apply(buy(66, 1000, 0));
        int tracked = ledger.trackedItems();
        long end = 1000 + BuyLimitLedger.WINDOW.getSeconds();
        assertEquals(4, ledger.remainingAt(ITEM, LIMIT, Instant.ofEpochSecond(1100)));
        assertEquals(4, ledger.remainingAt(ITEM, LIMIT, Instant.ofEpochSecond(end)));
        assertEquals(LIMIT, ledger.remainingAt(ITEM, LIMIT, Instant.ofEpochSecond(end + 1)));
        assertEquals(0, ledger.remainingAt(ITEM, 50, Instant.ofEpochSecond(1100)));
        assertEquals(tracked, ledger.trackedItems());
        assertEquals(4, ledger.remainingAt(ITEM, LIMIT, Instant.ofEpochSecond(1100)));
    }
    @Test public void observesOneItemAtTheSuppliedTime() {
        BuyLimitLedger ledger = new BuyLimitLedger();
        ledger.apply(buy(66, 1000, 0));
        BuyReplacementLiveLimitObservation observation = observer.observe(ledger, mapping(LIMIT), ITEM, 1100);
        assertNotNull(observation);
        assertEquals(ITEM, observation.getItemId());
        assertEquals(LIMIT, observation.getBuyLimit());
        assertEquals(4, observation.getRemaining());
        assertEquals(1100, observation.getObservedAt());
        BuyReplacementLiveLimitObservation untouched = observer.observe(new BuyLimitLedger(), mapping(LIMIT), ITEM, 1100);
        assertEquals(LIMIT, untouched.getRemaining());
    }
    @Test public void missingUnknownInvalidAndMalformedInputsFailClosed() {
        BuyLimitLedger ledger = new BuyLimitLedger();
        assertNull(observer.observe(null, mapping(LIMIT), ITEM, 1100));
        assertNull(observer.observe(ledger, null, ITEM, 1100));
        assertNull(observer.observe(ledger, mapping(LIMIT), 561, 1100));
        assertNull(observer.observe(ledger, mapping(LIMIT), 0, 1100));
        assertNull(observer.observe(ledger, mapping(LIMIT), ITEM, -1));
        assertNull(observer.observe(ledger, mapping(LIMIT), ITEM, Long.MAX_VALUE));
        assertNull(observer.observe(ledger, mapping(0), ITEM, 1100));
        assertNull(observer.observe(ledger, mapping(-5), ITEM, 1100));
        Map<Integer, MarketIngestionService.Item> holey = new HashMap<>();
        holey.put(ITEM, null);
        assertNull(observer.observe(ledger, holey, ITEM, 1100));
    }
    @Test public void observationFeedsLiveRevalidationAndShrinksCoverage() {
        TaxCalculator tax = new TaxCalculator();
        var capacity = capacity(tax);
        BuyLimitLedger ledger = new BuyLimitLedger();
        ledger.apply(buy(66, 1000, 0));
        var observation = observer.observe(ledger, mapping(LIMIT), ITEM, 1100);
        assertNotNull(observation);
        var result = new BuyReplacementQuantityLiveLimitRevalidator(tax).revalidate(capacity,
                observation.getItemId(), observation.getRemaining(), observation.getObservedAt(), 1100);
        assertNotNull(result);
        assertEquals(4, result.getLiveBuyLimitRemaining());
        assertEquals(4, result.getEffectiveBuyLimitRemaining());
        assertEquals(50, result.getRetainedBuyLimitRemaining());
        List<Integer> expected = new ArrayList<>();
        for (int i = 0; i < capacity.getInputs().size(); i++)
            if (capacity.getInputs().get(i).getQuantity() <= 4) expected.add(i);
        assertEquals(expected, result.getCoveredIndexes());
        assertEquals(expected.isEmpty() ? BuyReplacementQuantityLiveLimitOutcome.NOT_COVERED
                : BuyReplacementQuantityLiveLimitOutcome.COVERED, result.getOutcome());
        var unused = observer.observe(new BuyLimitLedger(), mapping(LIMIT), ITEM, 1100);
        var full = new BuyReplacementQuantityLiveLimitRevalidator(tax).revalidate(capacity,
                unused.getItemId(), unused.getRemaining(), unused.getObservedAt(), 1100);
        assertEquals(50, full.getEffectiveBuyLimitRemaining());
        assertEquals(capacity.getInputs().size(), full.getCoveredInputs().size());
    }
    @Test public void observerIsPackagePrivateReadOnlyAndClockFree() throws Exception {
        for (Class<?> type : new Class<?>[] {BuyReplacementLiveLimitObservation.class,
                BuyReplacementLiveLimitObserver.class}) {
            assertFalse(Modifier.isPublic(type.getModifiers()));
            String text = Files.readString(Path.of("src/main/java/com/flippingfriend/companion/" + type.getSimpleName() + ".java"));
            for (String forbidden : new String[] {"Instant.now", "currentTimeMillis", "Clock", ".apply(",
                    ".reconcile(", ".restore(", "new PortfolioCandidate", "PolicyDecision", "SqliteStore",
                    "REPLACEMENT_AUTHORIZED", "schedule"})
                assertFalse(forbidden, text.contains(forbidden));
        }
        assertFalse(Modifier.isPublic(BuyLimitLedger.class.getDeclaredMethod("remainingAt",
                int.class, int.class, Instant.class).getModifiers()));
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
