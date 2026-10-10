package com.flippingfriend.companion;
import com.flippingfriend.core.PortfolioCandidate;
import com.flippingfriend.model.TaxCalculator;
import java.util.List;
import java.util.Objects;
/**
 * Pure fail-closed single-slot preference among constructed replacement candidates, applying the
 * production optimizer's per-candidate gates and objective. Portfolio-level constraints (coins,
 * exposure, loss budget) are not applied here. Never authorizes, persists, presents, or activates.
 */
final class BuyReplacementQuantityCandidateSelector {
    private final TaxCalculator tax;
    BuyReplacementQuantityCandidateSelector(TaxCalculator tax) { this.tax = tax; }
    BuyReplacementQuantityCandidateSelection select(BuyReplacementQuantityCandidateSet source,
            long minProfitPerFlip, long at) {
        if (source == null || tax == null || minProfitPerFlip < 0) return null;
        try {
            return selectValid(source, minProfitPerFlip, at);
        } catch (NullPointerException | IllegalArgumentException | ArithmeticException
                | IndexOutOfBoundsException malformed) {
            return null;
        }
    }
    private BuyReplacementQuantityCandidateSelection selectValid(BuyReplacementQuantityCandidateSet source,
            long minProfitPerFlip, long at) {
        long builtAt = source.getBuiltAt();
        // Production treats a candidate whose expiry is not after now as expired; stale evidence is refused.
        if (builtAt < 0 || at < builtAt || at >= source.getExpiresAt()) return null;
        BuyReplacementQuantityCandidateSet verified =
                new BuyReplacementQuantityCandidateBuilder(tax).build(source.getSource(), builtAt);
        if (verified == null || verified.getSource() != source.getSource()
                || !verified.getSourceIndexes().equals(source.getSourceIndexes())
                || verified.getCandidates().size() != source.getCandidates().size()) return null;
        for (int k = 0; k < source.getCandidates().size(); k++)
            if (!same(verified.getCandidates().get(k), source.getCandidates().get(k))) return null;
        long maxAge = CompanionBuyReplacementReadinessPolicy.MAX_INPUT_AGE_SECONDS;
        long rateAt = source.getSource().getSource().getRateEvaluatedAt();
        if (at - source.getLiveObservedAt() > maxAge || rateAt < 0 || at - rateAt > maxAge) return null;
        long floor = Math.max(1, minProfitPerFlip);
        int belowMinimum = 0, noCapital = 0, cannotComplete = 0, notWorthDoing = 0;
        int best = -1;
        double bestScore = Double.NaN;
        List<PortfolioCandidate> candidates = source.getCandidates();
        for (int k = 0; k < candidates.size(); k++) {
            PortfolioCandidate c = candidates.get(k);
            if (c.getNetProfit() < floor) { belowMinimum++; continue; }
            if (c.getCapitalRequired() <= 0) { noCapital++; continue; }
            if (c.getCompletionProbability() <= 0) { cannotComplete++; continue; }
            double score = c.expectedGpPerSlotHour();
            if (!Double.isFinite(score)) return null;
            if (score <= 0) { notWorthDoing++; continue; }
            // Strictly greater keeps the earliest of equal scores, matching the stable descending sort.
            if (best < 0 || score > bestScore) { best = k; bestScore = score; }
        }
        if (best < 0) {
            return new BuyReplacementQuantityCandidateSelection(source,
                    BuyReplacementQuantityCandidateSelectionOutcome.NO_ELIGIBLE_CANDIDATE, null, -1, -1,
                    Double.NaN, minProfitPerFlip, belowMinimum, noCapital, cannotComplete, notWorthDoing, at);
        }
        return new BuyReplacementQuantityCandidateSelection(source,
                BuyReplacementQuantityCandidateSelectionOutcome.SELECTED, candidates.get(best), best,
                source.getSourceIndexes().get(best), bestScore, minProfitPerFlip,
                belowMinimum, noCapital, cannotComplete, notWorthDoing, at);
    }
    private static boolean same(PortfolioCandidate a, PortfolioCandidate b) {
        return b != null && a.getItemId() == b.getItemId() && Objects.equals(a.getItemName(), b.getItemName())
                && Objects.equals(a.getGroup(), b.getGroup())
                && a.getTargetBuyPrice() == b.getTargetBuyPrice()
                && a.getEquilibriumBuyPrice() == b.getEquilibriumBuyPrice()
                && a.getExitBuyPrice() == b.getExitBuyPrice()
                && a.getTargetSellPrice() == b.getTargetSellPrice()
                && a.getEquilibriumSellPrice() == b.getEquilibriumSellPrice()
                && a.getExitSellPrice() == b.getExitSellPrice()
                && a.getQuantity() == b.getQuantity() && a.getBuyLimitRemaining() == b.getBuyLimitRemaining()
                && a.getNetProfit() == b.getNetProfit() && a.getWorstLoss() == b.getWorstLoss()
                && a.getUnwindLoss() == b.getUnwindLoss() && a.getExpiresAt() == b.getExpiresAt()
                && a.getBuyFillProbability() == b.getBuyFillProbability()
                && a.getSellFillProbability() == b.getSellFillProbability()
                && a.getDisplayCompletionProbability() == b.getDisplayCompletionProbability()
                && a.getBuyHours() == b.getBuyHours() && a.getSellHours() == b.getSellHours()
                && a.getHorizonHours() == b.getHorizonHours();
    }
}
