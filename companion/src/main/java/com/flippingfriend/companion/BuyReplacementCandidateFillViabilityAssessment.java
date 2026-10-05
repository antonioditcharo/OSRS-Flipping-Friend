package com.flippingfriend.companion;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Immutable complete fill evidence plus a separate ordered viable-only view. */
final class BuyReplacementCandidateFillViabilityAssessment
{
    private final BuyReplacementCandidateFillEvaluationSet source;
    private final BuyReplacementCandidateFillViabilityOutcome outcome;
    private final List<BuyReplacementCandidateFillEvaluation> evaluations;
    private final List<BuyReplacementFillViableCandidate> viableCandidates;
    private final long assessedAt;

    BuyReplacementCandidateFillViabilityAssessment(BuyReplacementCandidateFillEvaluationSet source,
        BuyReplacementCandidateFillViabilityOutcome outcome,
        List<BuyReplacementCandidateFillEvaluation> evaluations,
        List<BuyReplacementFillViableCandidate> viableCandidates, long assessedAt)
    {
        this.source = source;
        this.outcome = outcome;
        this.evaluations = Collections.unmodifiableList(new ArrayList<>(evaluations));
        this.viableCandidates = Collections.unmodifiableList(new ArrayList<>(viableCandidates));
        this.assessedAt = assessedAt;
    }

    String getIntentId() { return source.getIntentId(); }
    String getOriginalOfferIdentity() { return source.getOriginalOfferIdentity(); }
    String getRecommendationId() { return source.getRecommendationId(); }
    int getSlot() { return source.getSlot(); }
    int getItemId() { return source.getItemId(); }
    String getItemName() { return source.getItemName(); }
    int getExactRemainderQuantity() { return source.getExactRemainderQuantity(); }
    String getAppetiteName() { return source.getAppetiteName(); }
    double getEffectiveHorizonHours() { return source.getEffectiveHorizonHours(); }
    long getFillEvaluatedAt() { return source.getEvaluatedAt(); }
    BuyReplacementCandidateFillViabilityOutcome getOutcome() { return outcome; }
    List<BuyReplacementCandidateFillEvaluation> getEvaluations() { return evaluations; }
    List<BuyReplacementFillViableCandidate> getViableCandidates() { return viableCandidates; }
    long getAssessedAt() { return assessedAt; }
}
