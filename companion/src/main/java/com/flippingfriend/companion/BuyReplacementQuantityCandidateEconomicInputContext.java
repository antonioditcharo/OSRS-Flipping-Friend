package com.flippingfriend.companion;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Ordered economic inputs retaining the complete original rate evidence. */
final class BuyReplacementQuantityCandidateEconomicInputContext {
    private final BuyReplacementQuantityExpectedGpPerSlotHourEvaluationSet source;
    private final List<BuyReplacementQuantityCandidateEconomicInput> inputs;
    private final long composedAt;

    BuyReplacementQuantityCandidateEconomicInputContext(
            BuyReplacementQuantityExpectedGpPerSlotHourEvaluationSet source,
            List<BuyReplacementQuantityCandidateEconomicInput> inputs, long composedAt) {
        this.source = source;
        this.inputs = Collections.unmodifiableList(new ArrayList<>(inputs));
        this.composedAt = composedAt;
    }

    BuyReplacementQuantityExpectedGpPerSlotHourEvaluationSet getSource() { return source; }
    List<BuyReplacementQuantityCandidateEconomicInput> getInputs() { return inputs; }
    String getIntentId() { return source.getIntentId(); }
    String getOriginalOfferIdentity() { return source.getOriginalOfferIdentity(); }
    String getRecommendationId() { return source.getRecommendationId(); }
    int getItemId() { return source.getItemId(); }
    int getExactRemainderQuantity() { return source.getExactRemainderQuantity(); }
    long getRateEvaluatedAt() { return source.getEvaluatedAt(); }
    long getComposedAt() { return composedAt; }
}
