package com.flippingfriend.companion;

import java.util.List;

/** Immutable metadata binding, not a candidate or renewed authority. */
final class BuyReplacementQuantityCandidateMetadataInputContext {
    private final BuyReplacementQuantityCandidateEconomicInputContext source;
    private final BuyReplacementQuantityFillInputContext fillSource;
    private final BuyReplacementCandidateFillInputContext metadataSource;
    private final String itemName, itemGroup;
    private final long composedAt;

    BuyReplacementQuantityCandidateMetadataInputContext(
            BuyReplacementQuantityCandidateEconomicInputContext source,
            BuyReplacementQuantityFillInputContext fillSource,
            BuyReplacementCandidateFillInputContext metadataSource,
            String itemName, String itemGroup, long composedAt) {
        this.source = source;
        this.fillSource = fillSource;
        this.metadataSource = metadataSource;
        this.itemName = itemName;
        this.itemGroup = itemGroup;
        this.composedAt = composedAt;
    }
    BuyReplacementQuantityCandidateEconomicInputContext getSource() { return source; }
    BuyReplacementQuantityFillInputContext getFillSource() { return fillSource; }
    BuyReplacementCandidateFillInputContext getMetadataSource() { return metadataSource; }
    List<BuyReplacementQuantityCandidateEconomicInput> getInputs() { return source.getInputs(); }
    String getItemName() { return itemName; }
    String getItemGroup() { return itemGroup; }
    int getItemId() { return source.getItemId(); }
    String getIntentId() { return source.getIntentId(); }
    String getOriginalOfferIdentity() { return source.getOriginalOfferIdentity(); }
    String getRecommendationId() { return source.getRecommendationId(); }
    int getExactRemainderQuantity() { return source.getExactRemainderQuantity(); }
    long getEconomicInputComposedAt() { return source.getComposedAt(); }
    long getRateEvaluatedAt() { return source.getRateEvaluatedAt(); }
    long getMetadataInputComposedAt() { return metadataSource.getComposedAt(); }
    long getHistoryObservedAt() { return fillSource.getHistoryObservedAt(); }
    long getMarketContextObservedAt() { return fillSource.getMarketContextObservedAt(); }
    long getComposedAt() { return composedAt; }
}
