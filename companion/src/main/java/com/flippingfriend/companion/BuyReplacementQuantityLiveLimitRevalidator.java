package com.flippingfriend.companion;
import com.flippingfriend.model.TaxCalculator;
import java.util.ArrayList;
import java.util.List;
/** Pure fail-closed check of retained quantities against a supplied live buy-limit observation. */
final class BuyReplacementQuantityLiveLimitRevalidator {
    private final TaxCalculator tax;
    BuyReplacementQuantityLiveLimitRevalidator(TaxCalculator tax) { this.tax = tax; }
    BuyReplacementQuantityLiveLimitAssessment revalidate(
            BuyReplacementQuantityCandidateCapacityInputContext source,
            int liveItemId, int liveBuyLimitRemaining, long liveObservedAt, long at) {
        if (source == null || tax == null) return null;
        try {
            return revalidateValid(source, liveItemId, liveBuyLimitRemaining, liveObservedAt, at);
        } catch (NullPointerException | IllegalArgumentException | ArithmeticException malformed) {
            return null;
        }
    }
    private BuyReplacementQuantityLiveLimitAssessment revalidateValid(
            BuyReplacementQuantityCandidateCapacityInputContext source,
            int liveItemId, int live, long liveObservedAt, long at) {
        long capacityAt = source.getComposedAt();
        if (capacityAt < 0 || at < capacityAt || at > source.getExpiresAt()) return null;
        BuyReplacementQuantityCandidateCapacityInputContext verified =
                new BuyReplacementQuantityCandidateCapacityInputComposer(tax)
                        .compose(source.getSource(), capacityAt);
        if (verified == null || verified.getSource() != source.getSource()
                || verified.getBuyLimitRemaining() != source.getBuyLimitRemaining()
                || verified.getExpiresAt() != source.getExpiresAt()) return null;
        if (liveItemId <= 0 || liveItemId != source.getItemId() || live < 0) return null;
        long eligibilityAt = source.getEligibilityEvaluatedAt();
        if (liveObservedAt < eligibilityAt || liveObservedAt > at
                || at - liveObservedAt > CompanionBuyReplacementReadinessPolicy.MAX_INPUT_AGE_SECONDS) return null;
        long rateAt = source.getRateEvaluatedAt();
        if (rateAt < 0 || at < rateAt
                || at - rateAt > CompanionBuyReplacementReadinessPolicy.MAX_INPUT_AGE_SECONDS) return null;
        // A higher live value never widens what the original eligibility evaluation established.
        int effective = Math.min(source.getBuyLimitRemaining(), live);
        List<Integer> indexes = new ArrayList<>();
        List<BuyReplacementQuantityCandidateEconomicInput> covered = new ArrayList<>();
        List<BuyReplacementQuantityCandidateEconomicInput> inputs = source.getInputs();
        for (int i = 0; i < inputs.size(); i++) {
            BuyReplacementQuantityCandidateEconomicInput input = inputs.get(i);
            if (input == null) return null;
            if (input.getQuantity() <= effective) {
                indexes.add(i);
                covered.add(input);
            }
        }
        BuyReplacementQuantityLiveLimitOutcome outcome = covered.isEmpty()
                ? BuyReplacementQuantityLiveLimitOutcome.NOT_COVERED
                : BuyReplacementQuantityLiveLimitOutcome.COVERED;
        return new BuyReplacementQuantityLiveLimitAssessment(source, outcome, live, effective,
                liveObservedAt, indexes, covered, at);
    }
}
