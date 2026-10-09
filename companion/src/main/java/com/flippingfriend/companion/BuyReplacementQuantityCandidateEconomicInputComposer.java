package com.flippingfriend.companion;

import com.flippingfriend.model.TaxCalculator;
import java.util.ArrayList;
import java.util.List;

/** Pure fail-closed binding of validated rate evidence to economic constructor inputs. */
final class BuyReplacementQuantityCandidateEconomicInputComposer {
    private final TaxCalculator tax;

    BuyReplacementQuantityCandidateEconomicInputComposer(TaxCalculator tax) { this.tax = tax; }

    BuyReplacementQuantityCandidateEconomicInputContext compose(
            BuyReplacementQuantityExpectedGpPerSlotHourEvaluationSet source, long at) {
        if (source == null || tax == null) return null;
        try {
            return composeValid(source, at);
        } catch (NullPointerException | IllegalArgumentException | ArithmeticException malformed) {
            return null;
        }
    }

    private BuyReplacementQuantityCandidateEconomicInputContext composeValid(
            BuyReplacementQuantityExpectedGpPerSlotHourEvaluationSet source, long at) {
        long previous = source.getEvaluatedAt();
        if (previous < 0 || at < previous
                || at - previous > CompanionBuyReplacementReadinessPolicy.MAX_INPUT_AGE_SECONDS
                || source.getEvaluations() == null || source.getEvaluations().isEmpty()) return null;
        // Revalidate at the original rate timestamp, never at the new composition time.
        BuyReplacementQuantityExpectedGpPerSlotHourEvaluationSet verified =
                new BuyReplacementQuantityExpectedGpPerSlotHourEvaluator(tax)
                        .evaluate(source.getSource(), previous);
        if (verified == null || verified.getEvaluations().size() != source.getEvaluations().size())
            return null;
        double horizon = source.getEffectiveHorizonHours();
        if (!Double.isFinite(horizon) || horizon <= 0) return null;
        List<BuyReplacementQuantityCandidateEconomicInput> inputs = new ArrayList<>();
        for (int i = 0; i < source.getEvaluations().size(); i++) {
            BuyReplacementQuantityExpectedGpPerSlotHourEvaluation evidence = source.getEvaluations().get(i);
            BuyReplacementQuantityExpectedGpPerSlotHourEvaluation check = verified.getEvaluations().get(i);
            if (evidence == null || evidence.getSource() != check.getSource()
                    || !Double.isFinite(evidence.getQuantityExpectedGpPerSlotHour())
                    || Double.compare(evidence.getQuantityExpectedGpPerSlotHour(),
                            check.getQuantityExpectedGpPerSlotHour()) != 0) return null;
            inputs.add(new BuyReplacementQuantityCandidateEconomicInput(evidence, horizon));
        }
        return new BuyReplacementQuantityCandidateEconomicInputContext(source, inputs, at);
    }
}
