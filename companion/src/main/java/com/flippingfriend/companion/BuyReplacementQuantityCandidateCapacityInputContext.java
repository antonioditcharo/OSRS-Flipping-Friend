package com.flippingfriend.companion;
import java.util.List;
/** Immutable retained capacity and expiry evidence, not a candidate or current authority. */
final class BuyReplacementQuantityCandidateCapacityInputContext {
    private final BuyReplacementQuantityCandidateMetadataInputContext source;
    private final int buyLimitRemaining;
    private final long expiresAt;
    private final long composedAt;
    BuyReplacementQuantityCandidateCapacityInputContext(
            BuyReplacementQuantityCandidateMetadataInputContext source,
            int buyLimitRemaining, long expiresAt, long composedAt) {
        this.source = source;
        this.buyLimitRemaining = buyLimitRemaining;
        this.expiresAt = expiresAt;
        this.composedAt = composedAt;
    }
    BuyReplacementQuantityCandidateMetadataInputContext getSource() { return source; }
    List<BuyReplacementQuantityCandidateEconomicInput> getInputs() { return source.getInputs(); }
    int getBuyLimitRemaining() { return buyLimitRemaining; }
    long getExpiresAt() { return expiresAt; }
    String getItemName() { return source.getItemName(); }
    String getItemGroup() { return source.getItemGroup(); }
    int getItemId() { return source.getItemId(); }
    String getIntentId() { return source.getIntentId(); }
    String getOriginalOfferIdentity() { return source.getOriginalOfferIdentity(); }
    String getRecommendationId() { return source.getRecommendationId(); }
    int getExactRemainderQuantity() { return source.getExactRemainderQuantity(); }
    long getEligibilityEvaluatedAt() { return source.getMetadataSource().getEligibilityEvaluatedAt(); }
    long getIntentExpiresAt() { return source.getMetadataSource().getIntentExpiresAt(); }
    long getRateEvaluatedAt() { return source.getRateEvaluatedAt(); }
    long getMetadataComposedAt() { return source.getComposedAt(); }
    long getComposedAt() { return composedAt; }
}
