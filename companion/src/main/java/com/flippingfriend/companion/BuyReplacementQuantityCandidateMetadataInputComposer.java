package com.flippingfriend.companion;

import com.flippingfriend.model.ItemGroups;
import com.flippingfriend.model.TaxCalculator;
import java.util.Objects;

/** Pure fail-closed metadata binding without renewed capacity or expiry authority. */
final class BuyReplacementQuantityCandidateMetadataInputComposer {
    private final TaxCalculator tax;
    BuyReplacementQuantityCandidateMetadataInputComposer(TaxCalculator tax) { this.tax = tax; }

    BuyReplacementQuantityCandidateMetadataInputContext compose(
            BuyReplacementQuantityCandidateEconomicInputContext source, long at) {
        if (source == null || tax == null) return null;
        try {
            return composeValid(source, at);
        } catch (NullPointerException | IllegalArgumentException | ArithmeticException malformed) {
            return null;
        }
    }

    private BuyReplacementQuantityCandidateMetadataInputContext composeValid(
            BuyReplacementQuantityCandidateEconomicInputContext source, long at) {
        long originalAt = source.getComposedAt();
        if (!fresh(originalAt, at) || !fresh(source.getRateEvaluatedAt(), at)) return null;
        BuyReplacementQuantityCandidateEconomicInputContext verified =
                new BuyReplacementQuantityCandidateEconomicInputComposer(tax)
                        .compose(source.getSource(), originalAt);
        if (verified == null || source.getInputs().isEmpty()
                || source.getInputs().size() != verified.getInputs().size()) return null;
        for (int i = 0; i < source.getInputs().size(); i++) {
            BuyReplacementQuantityCandidateEconomicInput input = source.getInputs().get(i);
            BuyReplacementQuantityCandidateEconomicInput check = verified.getInputs().get(i);
            if (input == null || input.getSource() != check.getSource()
                    || Double.compare(input.getHorizonHours(), check.getHorizonHours()) != 0) return null;
        }
        // Each link returns its actual retained wrapper, not a reconstructed equivalent.
        var fraction = source.getSource().getSource();
        var sizing = fraction.getSource();
        var worst = sizing.getSource();
        var risk = worst.getSource();
        var profit = risk.getSource();
        var unwind = profit.getSource();
        var unwindInput = unwind.getSource();
        var slots = unwindInput.getSource();
        var durations = slots.getSource();
        var calibration = durations.getSource();
        var completion = calibration.getSource();
        var viability = completion.getSource();
        var fills = viability.getSource();
        BuyReplacementQuantityFillInputContext fill = fills.getSource();
        BuyReplacementCandidateFillInputContext metadata = fill.getCandidateFillInputContext();
        if (metadata == null || !linked(source, fill, metadata)
                || !fresh(metadata.getComposedAt(), fill.getComposedAt())
                || metadata.getHistoryObservedAt() < 0
                || metadata.getHistoryObservedAt() > metadata.getComposedAt()
                || fill.getComposedAt() > fills.getEvaluatedAt()
                || fills.getEvaluatedAt() > viability.getAssessedAt()
                || viability.getAssessedAt() > completion.getEvaluatedAt()
                || completion.getEvaluatedAt() > calibration.getComposedAt()
                || calibration.getComposedAt() > durations.getEvaluatedAt()
                || durations.getEvaluatedAt() > slots.getEvaluatedAt()
                || slots.getEvaluatedAt() > unwindInput.getComposedAt()
                || unwindInput.getComposedAt() > unwind.getEvaluatedAt()
                || unwind.getEvaluatedAt() > profit.getEvaluatedAt()
                || profit.getEvaluatedAt() > risk.getComposedAt()
                || risk.getComposedAt() > worst.getEvaluatedAt()
                || worst.getEvaluatedAt() > sizing.getComposedAt()
                || sizing.getComposedAt() > fraction.getEvaluatedAt()
                || fraction.getEvaluatedAt() > source.getRateEvaluatedAt()) return null;
        String name = metadata.getItemName();
        if (name == null || name.trim().isEmpty()) return null;
        return new BuyReplacementQuantityCandidateMetadataInputContext(
                source, fill, metadata, name, ItemGroups.groupOf(name), at);
    }

    private static boolean linked(BuyReplacementQuantityCandidateEconomicInputContext economic,
            BuyReplacementQuantityFillInputContext fill, BuyReplacementCandidateFillInputContext metadata) {
        return economic.getItemId() > 0 && economic.getItemId() == fill.getItemId()
                && fill.getItemId() == metadata.getItemId()
                && economic.getExactRemainderQuantity() > 0
                && economic.getExactRemainderQuantity() == fill.getExactRemainderQuantity()
                && fill.getExactRemainderQuantity() == metadata.getExactRemainderQuantity()
                && same(economic.getIntentId(), fill.getIntentId(), metadata.getIntentId())
                && same(economic.getOriginalOfferIdentity(), fill.getOriginalOfferIdentity(), metadata.getOriginalOfferIdentity())
                && same(economic.getRecommendationId(), fill.getRecommendationId(), metadata.getRecommendationId())
                && same(economic.getSource().getAppetiteName(), fill.getAppetiteName(), metadata.getAppetiteName())
                && fill.getFillCurve() != null && !fill.getFillCurve().isEmpty()
                && fill.getFillCurve() == metadata.getFillCurve()
                && fill.getHistoryObservedAt() == metadata.getHistoryObservedAt()
                && Double.isFinite(fill.getEffectiveHorizonHours()) && fill.getEffectiveHorizonHours() > 0
                && Double.compare(fill.getEffectiveHorizonHours(), metadata.getEffectiveHorizonHours()) == 0
                && Double.compare(fill.getEffectiveHorizonHours(), economic.getSource().getEffectiveHorizonHours()) == 0;
    }
    private static boolean same(String a, String b, String c) {
        return a != null && !a.trim().isEmpty() && Objects.equals(a, b) && Objects.equals(b, c);
    }
    private static boolean fresh(long previous, long at) {
        return previous >= 0 && at >= previous
                && at - previous <= CompanionBuyReplacementReadinessPolicy.MAX_INPUT_AGE_SECONDS;
    }
}
