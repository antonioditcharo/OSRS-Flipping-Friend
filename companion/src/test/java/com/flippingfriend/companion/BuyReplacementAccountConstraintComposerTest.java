package com.flippingfriend.companion;
import com.flippingfriend.core.AccountSnapshot;
import com.flippingfriend.core.PortfolioCandidate;
import com.flippingfriend.core.PortfolioConstraints;
import com.flippingfriend.core.PortfolioOptimizer;
import com.flippingfriend.core.PortfolioPlan;
import com.flippingfriend.data.Candle;
import com.flippingfriend.model.ItemGroups;
import com.flippingfriend.model.PriceOffset;
import com.flippingfriend.model.RiskAppetite;
import com.flippingfriend.model.TaxCalculator;
import com.google.gson.Gson;
import java.lang.reflect.Method;
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
public class BuyReplacementAccountConstraintComposerTest {
    private static final int ITEM = 4151;
    private static final int UNMAPPED = 561;
    private static final TaxCalculator TAX = margin(50);
    private static TaxCalculator margin(long perItem) {
        return new TaxCalculator() {
            @Override public long netMarginPerItem(int item, int buy, int sell) { return perItem; }
            @Override public long netProfit(int item, int buy, int sell, int quantity) { return perItem * quantity; }
        };
    }
    private static BuyReplacementQuantityCandidateSet built() {
        var assessment = require(new BuyReplacementQuantityLiveLimitRevalidator(TAX)
                .revalidate(capacity(TAX), ITEM, 500, 1100, 1100));
        return require(new BuyReplacementQuantityCandidateBuilder(TAX).build(assessment, 1100));
    }
    private static Map<Integer, MarketIngestionService.Item> mapping() {
        Map<Integer, MarketIngestionService.Item> mapping = new HashMap<>();
        mapping.put(ITEM, new MarketIngestionService.Item(ITEM, "Abyssal whip", 70));
        return mapping;
    }
    /** Builds a snapshot through its wire form, then checks every field the planner reads actually landed. */
    private static AccountSnapshot account(long observedAt, long spendable, long committedCoins, int freeSlots,
            double drawdown, boolean sellOnly, long committedWhip, long committedUnmapped, String blocked,
            String skipped, String onOffer) {
        String json = "{\"correlationId\":\"c\",\"observedAt\":" + observedAt + ",\"spendableCoins\":" + spendable
                + ",\"committedCoins\":" + committedCoins + ",\"freeSlots\":" + freeSlots + ",\"totalSlots\":8"
                + ",\"members\":true,\"bankSeen\":true,\"markedSessionDrawdown\":" + drawdown
                + ",\"riskAppetite\":\"Balanced\",\"minProfitPerFlip\":0,\"sellOnly\":" + sellOnly
                + ",\"committedByItem\":{\"" + ITEM + "\":" + committedWhip + ",\"" + UNMAPPED + "\":" + committedUnmapped + "}"
                + ",\"blockedItems\":[" + blocked + "],\"skippedItems\":[" + skipped + "],\"itemsOnOffer\":[" + onOffer + "]}";
        AccountSnapshot a = new Gson().fromJson(json, AccountSnapshot.class);
        assertEquals(observedAt, a.getObservedAt());
        assertEquals(spendable, a.getSpendableCoins());
        assertEquals(committedCoins, a.getCommittedCoins());
        assertEquals(freeSlots, a.getFreeSlots());
        assertEquals(drawdown, a.getMarkedSessionDrawdown(), 0.0);
        assertEquals(sellOnly, a.isSellOnly());
        assertEquals("Balanced", a.getRiskAppetite());
        assertEquals(Long.valueOf(committedWhip), a.getCommittedByItem().get(ITEM));
        assertEquals(Long.valueOf(committedUnmapped), a.getCommittedByItem().get(UNMAPPED));
        assertEquals(blocked.isEmpty() ? 0 : 1, a.getBlockedItems().size());
        assertEquals(skipped.isEmpty() ? 0 : 1, a.getSkippedItems().size());
        assertEquals(onOffer.isEmpty() ? 0 : 1, a.getItemsOnOffer().size());
        return a;
    }
    private static AccountSnapshot ready(long spendable, int freeSlots) {
        return account(1100, spendable, 4000, freeSlots, 0, false, 3000, 500, "", "", "");
    }
    private static BuyReplacementAccountConstraintContext compose(AccountSnapshot account) {
        return new BuyReplacementAccountConstraintComposer(TAX).compose(built(), account, mapping(), 1100);
    }
    @Test public void readyAccountBuildsThePlannersOwnLimits() {
        AccountSnapshot account = ready(2_000_000, 1);
        var context = compose(account);
        assertNotNull(context);
        assertEquals(BuyReplacementAccountConstraintOutcome.READY, context.getOutcome());
        long equity = 2_000_000 + 4000;
        RiskAppetite appetite = RiskAppetite.forName("Balanced");
        long budget = (long) (equity * PortfolioPlanner.DRAWDOWN_LIMIT);
        assertEquals(budget, context.getLossBudget());
        PortfolioConstraints c = context.getConstraints();
        assertEquals(1, c.getFreeSlots());
        assertEquals(2_000_000, c.getFreeCoins());
        assertEquals(budget, c.getSessionLossBudget());
        assertEquals((long) (equity * appetite.getItemExposureLimit()), c.getPerItemCapitalCap());
        assertEquals((long) (equity * appetite.getGroupExposureLimit()), c.getPerGroupCapitalCap());
        assertEquals(0, c.getMinProfitPerFlip());
        assertEquals(account.getCommittedByItem(), c.getCommittedByItem());
        Map<String, Long> groups = new HashMap<>();
        groups.merge(ItemGroups.groupOf("Abyssal whip"), 3000L, Long::sum);
        groups.merge("", 500L, Long::sum);
        assertEquals(groups, c.getCommittedByGroup());
        assertEquals(appetite.getName(), context.getAppetiteName());
        assertEquals(1150, context.getExpiresAt());
        assertEquals(1100, context.getAccountObservedAt());
    }
    @Test public void plannerHelpersMatchTheFormerInlineArithmetic() {
        AccountSnapshot account = account(1100, 3_000_000, 7000, 2, 1234.9, false, 3000, 500, "", "", "");
        long equity = 3_000_000 + 7000;
        assertEquals((long) (equity * PortfolioPlanner.DRAWDOWN_LIMIT) - 1234L, PortfolioPlanner.lossBudgetFor(account));
        var context = compose(account);
        assertEquals(PortfolioPlanner.lossBudgetFor(account), context.getLossBudget());
        assertEquals(PortfolioPlanner.committedByGroupFor(account, mapping()), context.getConstraints().getCommittedByGroup());
    }
    @Test public void drawdownBoundaryAndPrecedenceFollowThePlanner() {
        long equity = 1_000_000 + 4000;
        double frozen = (long) (equity * PortfolioPlanner.DRAWDOWN_LIMIT);
        var stop = compose(account(1100, 1_000_000, 4000, 1, frozen, true, 3000, 500, "", "", ""));
        assertEquals(BuyReplacementAccountConstraintOutcome.DRAWDOWN_LIMIT_REACHED, stop.getOutcome());
        assertEquals(0, stop.getLossBudget());
        assertNull(stop.getConstraints());
        var open = compose(account(1100, 1_000_000, 4000, 1, frozen - 1, false, 3000, 500, "", "", ""));
        assertEquals(BuyReplacementAccountConstraintOutcome.READY, open.getOutcome());
        assertEquals(1, open.getLossBudget());
    }
    @Test public void sellOnlyAndRejectedItemsAreRefusedInPlannerOrder() {
        assertEquals(BuyReplacementAccountConstraintOutcome.SELL_ONLY_MODE,
                compose(account(1100, 2_000_000, 0, 1, 0, true, 3000, 500, "\"abyssal whip\"", "", "")).getOutcome());
        for (var account : Arrays.asList(
                account(1100, 2_000_000, 0, 1, 0, false, 3000, 500, "\"abyssal whip\"", "", ""),
                account(1100, 2_000_000, 0, 1, 0, false, 3000, 500, "", "" + ITEM, ""),
                account(1100, 2_000_000, 0, 1, 0, false, 3000, 500, "", "", "" + ITEM))) {
            var context = compose(account);
            assertEquals(BuyReplacementAccountConstraintOutcome.PLAYER_REJECTED, context.getOutcome());
            assertNull(context.getConstraints());
        }
        assertEquals(BuyReplacementAccountConstraintOutcome.READY,
                compose(account(1100, 2_000_000, 0, 1, 0, false, 3000, 500, "\"dragon dagger\"", "4152", "4153")).getOutcome());
    }
    @Test public void readyLimitsDriveConstrainedSelectionWithOptimizerParity() {
        var set = built();
        for (long spendable : new long[] {2_000_000, 1_000}) {
            AccountSnapshot account = ready(spendable, 1);
            var context = require(new BuyReplacementAccountConstraintComposer(TAX).compose(set, account, mapping(), 1100));
            var ours = require(new BuyReplacementQuantityConstrainedCandidateSelector(TAX)
                    .select(set, context.getConstraints(), 1100));
            PortfolioPlan plan = new PortfolioOptimizer().optimize(new ArrayList<>(set.getCandidates()),
                    context.getConstraints(), "parity", 1100);
            String expected = ours.getOutcome() == BuyReplacementQuantityConstrainedSelectionOutcome.SELECTED
                    ? "READY" : ours.getOutcome().name();
            assertEquals(expected, plan.getOutcome().name());
            if (ours.getSelected() != null) assertSame(plan.getAllocations().get(0).getCandidate(), ours.getSelected());
        }
        var noSlot = require(new BuyReplacementAccountConstraintComposer(TAX).compose(set, ready(2_000_000, 0), mapping(), 1100));
        assertEquals(BuyReplacementQuantityConstrainedSelectionOutcome.NO_FREE_SLOT,
                new BuyReplacementQuantityConstrainedCandidateSelector(TAX).select(set, noSlot.getConstraints(), 1100).getOutcome());
    }
    @Test public void staleFutureExpiredAndMalformedInputsFailClosed() {
        var composer = new BuyReplacementAccountConstraintComposer(TAX);
        var set = built();
        assertNotNull(composer.compose(set, account(980, 2_000_000, 0, 1, 0, false, 0, 0, "", "", ""), mapping(), 1100));
        assertNull(composer.compose(set, account(979, 2_000_000, 0, 1, 0, false, 0, 0, "", "", ""), mapping(), 1100));
        assertNull(composer.compose(set, account(1101, 2_000_000, 0, 1, 0, false, 0, 0, "", "", ""), mapping(), 1100));
        assertNull(composer.compose(set, ready(2_000_000, 1), mapping(), 1150));
        assertNull(composer.compose(set, account(1100, 2_000_000, 0, 1, 0, false, -1, 0, "", "", ""), mapping(), 1100));
        assertNull(composer.compose(null, ready(2_000_000, 1), mapping(), 1100));
        assertNull(composer.compose(set, null, mapping(), 1100));
        assertNull(composer.compose(set, ready(2_000_000, 1), null, 1100));
        assertNull(new BuyReplacementAccountConstraintComposer(null).compose(set, ready(2_000_000, 1), mapping(), 1100));
        assertNull(new BuyReplacementAccountConstraintComposer(margin(40)).compose(set, ready(2_000_000, 1), mapping(), 1100));
    }
    @Test public void plannerUsesItsExtractedHelpersAndTheyStayPackagePrivate() throws Exception {
        String planner = Files.readString(Path.of("src/main/java/com/flippingfriend/companion/PortfolioPlanner.java"));
        assertTrue(planner.contains("long lossBudget = lossBudgetFor(account);"));
        assertTrue(planner.contains("Map<String, Long> committedByGroup = committedByGroupFor(account, market.mapping);"));
        assertTrue(planner.contains("PortfolioConstraints limits = constraintsFor(account, appetite, lossBudget, committedByGroup);"));
        for (Method m : PortfolioPlanner.class.getDeclaredMethods())
            if (Arrays.asList("lossBudgetFor", "committedByGroupFor", "constraintsFor").contains(m.getName())) {
                assertTrue(Modifier.isStatic(m.getModifiers()));
                assertFalse(Modifier.isPublic(m.getModifiers()) || Modifier.isPrivate(m.getModifiers()));
            }
    }
    @Test public void contractsArePackagePrivateAndEvidenceOnly() throws Exception {
        for (Class<?> type : new Class<?>[] {BuyReplacementAccountConstraintOutcome.class,
                BuyReplacementAccountConstraintContext.class, BuyReplacementAccountConstraintComposer.class}) {
            assertFalse(Modifier.isPublic(type.getModifiers()));
            String text = Files.readString(Path.of("src/main/java/com/flippingfriend/companion/" + type.getSimpleName() + ".java"));
            for (String forbidden : new String[] {"new PortfolioOptimizer", "PolicyDecision", "OfferLifecycleAction",
                    "REPLACEMENT_AUTHORIZED", "SqliteStore", "BuyLimitLedger", "Instant.now", "currentTimeMillis",
                    "schedule", "new PortfolioCandidate", ".plan(", "new PortfolioConstraints"})
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
