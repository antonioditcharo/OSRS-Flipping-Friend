package com.flippingfriend.companion;
import com.flippingfriend.model.TaxCalculator;
import java.util.Objects;
/** Pure fail-closed binding of retained eligibility capacity and expiry evidence. */
final class BuyReplacementQuantityCandidateCapacityInputComposer {
    // Local copy of the production plan lifetime; a reflective test keeps it in parity.
    static final long CANDIDATE_TTL_SECONDS = 150;
    private final TaxCalculator tax;
    BuyReplacementQuantityCandidateCapacityInputComposer(TaxCalculator tax) { this.tax = tax; }
    BuyReplacementQuantityCandidateCapacityInputContext compose(
            BuyReplacementQuantityCandidateMetadataInputContext source, long at) {
        if (source == null || tax == null) return null;
        try {
            return composeValid(source, at);
        } catch (NullPointerException | IllegalArgumentException | ArithmeticException malformed) {
            return null;
        }
    }
    private BuyReplacementQuantityCandidateCapacityInputContext composeValid(
            BuyReplacementQuantityCandidateMetadataInputContext source, long at) {
        long originalAt = source.getComposedAt();
        if (originalAt < 0 || at < originalAt) return null;
        BuyReplacementQuantityCandidateMetadataInputContext verified =
                new BuyReplacementQuantityCandidateMetadataInputComposer(tax)
                        .compose(source.getSource(), originalAt);
        if (verified == null || verified.getSource() != source.getSource()
                || verified.getFillSource() != source.getFillSource()
                || verified.getMetadataSource() != source.getMetadataSource()
                || !Objects.equals(verified.getItemName(), source.getItemName())
                || !Objects.equals(verified.getItemGroup(), source.getItemGroup())) return null;
        // Capacity and expiry come only from the exact retained eligibility evidence.
        BuyReplacementCandidateFillInputContext retained = source.getMetadataSource();
        int remainder = source.getExactRemainderQuantity();
        int limit = retained.getBuyLimitRemaining();
        if (remainder <= 0 || limit <= 0 || limit < remainder || source.getInputs().isEmpty()) return null;
        for (BuyReplacementQuantityCandidateEconomicInput input : source.getInputs()) {
            if (input == null) return null;
            int quantity = input.getQuantity();
            if (quantity <= 0 || quantity > limit || quantity > remainder) return null;
        }
        long evaluatedAt = retained.getEligibilityEvaluatedAt();
        long intentExpiresAt = retained.getIntentExpiresAt();
        if (evaluatedAt < 0 || intentExpiresAt < evaluatedAt) return null;
        long expiresAt = Math.min(Math.addExact(evaluatedAt, CANDIDATE_TTL_SECONDS), intentExpiresAt);
        if (at > expiresAt) return null;
        long rateAt = source.getRateEvaluatedAt();
        if (rateAt < 0 || at < rateAt
                || at - rateAt > CompanionBuyReplacementReadinessPolicy.MAX_INPUT_AGE_SECONDS) return null;
        return new BuyReplacementQuantityCandidateCapacityInputContext(source, limit, expiresAt, at);
    }
}
