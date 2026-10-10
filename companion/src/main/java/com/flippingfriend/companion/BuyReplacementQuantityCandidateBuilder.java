package com.flippingfriend.companion;
import com.flippingfriend.core.PortfolioCandidate;
import com.flippingfriend.model.TaxCalculator;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
/**
 * Pure fail-closed construction of production replacement candidates from a live-covered
 * assessment. Builds only; never ranks, selects, authorizes, persists, presents, or activates.
 */
final class BuyReplacementQuantityCandidateBuilder {
    // Relative tolerance for comparing the candidate's own floating-point recomputation with the
    // evidence chain; both apply the same production formulas, so only rounding may differ.
    static final double PARITY_TOLERANCE = 1e-9;
    private static final double MINIMUM_HORIZON_HOURS = 1.0 / 60.0;
    private final TaxCalculator tax;
    BuyReplacementQuantityCandidateBuilder(TaxCalculator tax) { this.tax = tax; }
    BuyReplacementQuantityCandidateSet build(BuyReplacementQuantityLiveLimitAssessment source, long at) {
        if (source == null || tax == null) return null;
        try {
            return buildValid(source, at);
        } catch (NullPointerException | IllegalArgumentException | ArithmeticException
                | IndexOutOfBoundsException malformed) {
            return null;
        }
    }
    private BuyReplacementQuantityCandidateSet buildValid(BuyReplacementQuantityLiveLimitAssessment source, long at) {
        long evaluatedAt = source.getEvaluatedAt();
        if (evaluatedAt < 0 || at < evaluatedAt || at > source.getExpiresAt()) return null;
        BuyReplacementQuantityLiveLimitAssessment verified = new BuyReplacementQuantityLiveLimitRevalidator(tax)
                .revalidate(source.getSource(), source.getItemId(), source.getLiveBuyLimitRemaining(),
                        source.getLiveObservedAt(), evaluatedAt);
        if (verified == null || verified.getSource() != source.getSource()
                || verified.getOutcome() != source.getOutcome()
                || verified.getEffectiveBuyLimitRemaining() != source.getEffectiveBuyLimitRemaining()
                || !verified.getCoveredIndexes().equals(source.getCoveredIndexes())
                || verified.getCoveredInputs().size() != source.getCoveredInputs().size()) return null;
        for (int k = 0; k < verified.getCoveredInputs().size(); k++)
            if (verified.getCoveredInputs().get(k) != source.getCoveredInputs().get(k)) return null;
        long maxAge = CompanionBuyReplacementReadinessPolicy.MAX_INPUT_AGE_SECONDS;
        long rateAt = source.getSource().getRateEvaluatedAt();
        if (at - source.getLiveObservedAt() > maxAge || rateAt < 0 || at - rateAt > maxAge) return null;
        BuyReplacementQuantityCandidateCapacityInputContext capacity = source.getSource();
        String name = capacity.getItemName();
        String group = capacity.getItemGroup();
        int itemId = capacity.getItemId();
        int limit = source.getEffectiveBuyLimitRemaining();
        int remainder = capacity.getExactRemainderQuantity();
        long expiresAt = capacity.getExpiresAt();
        if (name == null || name.isBlank() || group == null || itemId <= 0 || limit < 0) return null;
        List<PortfolioCandidate> candidates = new ArrayList<>();
        List<Integer> indexes = new ArrayList<>();
        for (int k = 0; k < source.getCoveredInputs().size(); k++) {
            BuyReplacementQuantityCandidateEconomicInput input = source.getCoveredInputs().get(k);
            int index = source.getCoveredIndexes().get(k);
            if (input == null || capacity.getInputs().get(index) != input) return null;
            int quantity = input.getQuantity();
            double horizon = input.getHorizonHours();
            if (quantity <= 0 || quantity > limit || quantity > remainder
                    || !(horizon >= MINIMUM_HORIZON_HOURS) || !Double.isFinite(horizon)) return null;
            PortfolioCandidate candidate = new PortfolioCandidate(itemId, name, group,
                    input.getTargetBuyPrice(), input.getEquilibriumBuyPrice(), input.getExitBuyPrice(),
                    input.getTargetSellPrice(), input.getEquilibriumSellPrice(), input.getExitSellPrice(),
                    quantity, limit, input.getNetProfit(), input.getWorstLoss(), input.getUnwindLoss(),
                    input.getBuyFillProbability(), input.getSellFillProbability(),
                    input.getBuyHours(), input.getSellHours(), horizon, expiresAt)
                    .withDisplayProbability(input.getBuyFillProbability());
            if (!consistent(candidate, input, itemId, name, group, limit, expiresAt)) return null;
            candidates.add(candidate);
            indexes.add(index);
        }
        return new BuyReplacementQuantityCandidateSet(source, candidates, indexes, at);
    }
    private static boolean consistent(PortfolioCandidate c, BuyReplacementQuantityCandidateEconomicInput input,
            int itemId, String name, String group, int limit, long expiresAt) {
        return c.getItemId() == itemId && Objects.equals(c.getItemName(), name) && Objects.equals(c.getGroup(), group)
                && c.getTargetBuyPrice() == input.getTargetBuyPrice()
                && c.getEquilibriumBuyPrice() == input.getEquilibriumBuyPrice()
                && c.getExitBuyPrice() == input.getExitBuyPrice()
                && c.getTargetSellPrice() == input.getTargetSellPrice()
                && c.getEquilibriumSellPrice() == input.getEquilibriumSellPrice()
                && c.getExitSellPrice() == input.getExitSellPrice()
                && c.getQuantity() == input.getQuantity() && c.getBuyLimitRemaining() == limit
                && c.getNetProfit() == input.getNetProfit() && c.getWorstLoss() == input.getWorstLoss()
                && c.getUnwindLoss() == input.getUnwindLoss() && c.getExpiresAt() == expiresAt
                && c.getBuyFillProbability() == input.getBuyFillProbability()
                && c.getSellFillProbability() == input.getSellFillProbability()
                && c.getBuyHours() == input.getBuyHours() && c.getSellHours() == input.getSellHours()
                && c.getHorizonHours() == input.getHorizonHours()
                && close(c.expectedProfit(), input.getExpectedProfit())
                && close(c.expectedSlotHours(), input.getExpectedSlotHours())
                && close(c.expectedGpPerSlotHour(), input.getExpectedGpPerSlotHour());
    }
    private static boolean close(double actual, double expected) {
        if (!Double.isFinite(actual) || !Double.isFinite(expected)) return false;
        return Math.abs(actual - expected) <= PARITY_TOLERANCE * Math.max(1.0, Math.abs(expected));
    }
}
