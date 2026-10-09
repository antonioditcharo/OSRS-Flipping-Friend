package com.flippingfriend.companion;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Collections;
import org.junit.Test;
import static org.junit.Assert.*;

/** Accessor tests use empty wrappers deliberately, not validated economic fixtures. */
public class BuyReplacementQuantitySourceAccessTest {
    @Test public void fillEvaluationExposesExactOriginalInput() {
        BuyReplacementQuantityFillInputContext input = input();
        BuyReplacementQuantityFillEvaluationSet set = fills(input);
        assertSame(input, set.getSource());
        assertSame(input.getCandidateFillInputContext(), set.getSource().getCandidateFillInputContext());
        assertEquals(1101, set.getEvaluatedAt());
        assertTrue(set.getEvaluations().isEmpty());
    }

    @Test public void viabilityExposesExactCompleteFillSet() {
        BuyReplacementQuantityFillEvaluationSet set = fills(input());
        BuyReplacementQuantityFillViabilityAssessment assessment = viability(set);
        assertSame(set, assessment.getSource());
        assertEquals(BuyReplacementQuantityFillViabilityOutcome.NOT_VIABLE, assessment.getOutcome());
        assertEquals(1102, assessment.getAssessedAt());
        assertTrue(assessment.getViableCandidates().isEmpty());
    }

    @Test public void completionExposesExactAssessment() {
        BuyReplacementQuantityFillViabilityAssessment assessment = viability(fills(input()));
        BuyReplacementQuantityRoundTripCompletionEvaluationSet set = completion(assessment);
        assertSame(assessment, set.getSource());
        assertEquals(1103, set.getEvaluatedAt());
        assertTrue(set.getEvaluations().isEmpty());
    }

    @Test public void calibrationExposesExactCompletionSetAndUnchangedChoice() {
        BuyReplacementQuantityRoundTripCompletionEvaluationSet set = completion(viability(fills(input())));
        BuyReplacementQuantityDurationCalibrationInputContext context = calibration(set);
        assertSame(set, context.getSource());
        assertTrue(context.isLearningDisabled());
        assertEquals(1.0, context.getWaitMultiplier(), 0);
        assertEquals(1104, context.getComposedAt());
    }

    @Test public void completeAccessPathPreservesMetadataIdentityAndOriginalTimes() {
        BuyReplacementQuantityFillInputContext input = input();
        BuyReplacementCandidateFillInputContext metadata = input.getCandidateFillInputContext();
        assertNotNull(metadata);
        BuyReplacementQuantityDurationCalibrationInputContext context =
                calibration(completion(viability(fills(input))));
        BuyReplacementQuantityFillInputContext retained =
                context.getSource().getSource().getSource().getSource();
        assertSame(input, retained);
        assertSame(metadata, retained.getCandidateFillInputContext());
        assertEquals(1000, metadata.getComposedAt());
        assertEquals(1100, retained.getComposedAt());
        assertEquals(input.getHistoryObservedAt(), context.getHistoryObservedAt());
        assertEquals(input.getEffectiveHorizonHours(), context.getEffectiveHorizonHours(), 0);
        assertEquals(input.getExactRemainderQuantity(), context.getExactRemainderQuantity());
        assertEquals(input.getIntentId(), context.getIntentId());
        assertEquals(input.getOriginalOfferIdentity(), context.getOriginalOfferIdentity());
        assertEquals(input.getRecommendationId(), context.getRecommendationId());
    }

    @Test public void accessDoesNotInventLegacyProvenanceOrValidateEmptyWrappers() {
        BuyReplacementQuantityFillInputContext original = input();
        BuyReplacementQuantityFillInputContext legacy = new BuyReplacementQuantityFillInputContext(
                BuyReplacementQuantityGridComposerTest.quantityFillCandidateSetForCompanionTests(),
                original.getInputs(), original.getFillCurve(), original.getEffectiveHorizonHours(),
                original.getSeasonalMultiplier(), original.getHistoryObservedAt(),
                original.getMarketContextObservedAt(), original.getComposedAt());
        BuyReplacementQuantityDurationCalibrationInputContext context =
                calibration(completion(viability(fills(legacy))));
        assertSame(legacy, context.getSource().getSource().getSource().getSource());
        assertNull(context.getSource().getSource().getSource().getSource().getCandidateFillInputContext());
        assertTrue(context.getEvaluations().isEmpty());
    }

    @Test public void absentSourcesRemainAbsentWithoutReconstruction() {
        assertNull(fills(null).getSource());
        assertNull(viability(null).getSource());
        assertNull(completion(null).getSource());
        assertNull(calibration(null).getSource());
    }

    @Test public void accessorsArePackagePrivateTypedAndRepeatable() throws Exception {
        Class<?>[] types = {BuyReplacementQuantityFillEvaluationSet.class,
                BuyReplacementQuantityFillViabilityAssessment.class,
                BuyReplacementQuantityRoundTripCompletionEvaluationSet.class,
                BuyReplacementQuantityDurationCalibrationInputContext.class};
        Class<?>[] returns = {BuyReplacementQuantityFillInputContext.class,
                BuyReplacementQuantityFillEvaluationSet.class,
                BuyReplacementQuantityFillViabilityAssessment.class,
                BuyReplacementQuantityRoundTripCompletionEvaluationSet.class};
        for (int i = 0; i < types.length; i++) {
            Method method = types[i].getDeclaredMethod("getSource");
            assertEquals(returns[i], method.getReturnType());
            assertEquals(0, method.getModifiers() &
                    (Modifier.PUBLIC | Modifier.PROTECTED | Modifier.PRIVATE | Modifier.STATIC));
        }
        BuyReplacementQuantityFillEvaluationSet set = fills(input());
        assertSame(set.getSource(), set.getSource());
        try { set.getEvaluations().clear(); fail("immutable"); }
        catch (UnsupportedOperationException expected) { }
    }

    private static BuyReplacementQuantityFillInputContext input() {
        BuyReplacementQuantityFillInputContext result = new BuyReplacementQuantityFillInputContextComposer()
                .compose(BuyReplacementQuantityGridComposerTest.quantityFillCandidateSetForCompanionTests(),
                        BuyReplacementCandidateFillInputContextComposerTest.contextForCompanionTests(),
                        1.25, 1100, 1100);
        assertNotNull(result);
        return result;
    }
    private static BuyReplacementQuantityFillEvaluationSet fills(BuyReplacementQuantityFillInputContext source) {
        return new BuyReplacementQuantityFillEvaluationSet(source, Collections.emptyList(), 1101);
    }
    private static BuyReplacementQuantityFillViabilityAssessment viability(BuyReplacementQuantityFillEvaluationSet source) {
        return new BuyReplacementQuantityFillViabilityAssessment(source,
                BuyReplacementQuantityFillViabilityOutcome.NOT_VIABLE,
                Collections.emptyList(), Collections.emptyList(), 1102);
    }
    private static BuyReplacementQuantityRoundTripCompletionEvaluationSet completion(
            BuyReplacementQuantityFillViabilityAssessment source) {
        return new BuyReplacementQuantityRoundTripCompletionEvaluationSet(source, Collections.emptyList(), 1103);
    }
    private static BuyReplacementQuantityDurationCalibrationInputContext calibration(
            BuyReplacementQuantityRoundTripCompletionEvaluationSet source) {
        return new BuyReplacementQuantityDurationCalibrationInputContext(source, true, 1.0, 1104);
    }
}
