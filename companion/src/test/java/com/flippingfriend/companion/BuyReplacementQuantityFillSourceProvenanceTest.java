package com.flippingfriend.companion;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import org.junit.Test;
import static org.junit.Assert.*;

public class BuyReplacementQuantityFillSourceProvenanceTest {
    @Test public void composerRetainsExactOriginalContextNotAnEquivalentRebuild() {
        BuyReplacementCandidateFillInputContext fill = fill();
        BuyReplacementQuantityFillInputContext result = compose(fill, 1100);
        assertNotNull(result);
        assertSame(fill, result.getCandidateFillInputContext());
        assertNotSame(fill(), result.getCandidateFillInputContext());
        assertSame(fill.getFillCurve(), result.getFillCurve());
        assertEquals(fill.getItemName(), result.getCandidateFillInputContext().getItemName());
        assertEquals(fill.getIntentId(), result.getIntentId());
        assertEquals(fill.getOriginalOfferIdentity(), result.getOriginalOfferIdentity());
        assertEquals(fill.getRecommendationId(), result.getRecommendationId());
    }

    @Test public void preservesOriginalTimesAndAllExistingFillScalars() {
        BuyReplacementCandidateFillInputContext fill = fill();
        BuyReplacementQuantityFillInputContext result = compose(fill, 1100);
        assertNotNull(result);
        assertEquals(fill.getComposedAt(), result.getCandidateFillInputContext().getComposedAt());
        assertEquals(fill.getHistoryObservedAt(), result.getHistoryObservedAt());
        assertEquals(fill.getEffectiveHorizonHours(), result.getEffectiveHorizonHours(), 0);
        assertEquals(1.25, result.getSeasonalMultiplier(), 0);
        assertEquals(1100, result.getMarketContextObservedAt());
        assertEquals(1100, result.getComposedAt());
        assertEquals(fill.getExactRemainderQuantity(), result.getExactRemainderQuantity());
        for (int i = 0; i < result.getInputs().size(); i++) {
            assertEquals(result.getCandidates().get(i).getQuantity(), result.getInputs().get(i).getQuantity());
            assertEquals(result.getCandidates().get(i).getSizeGridIndex(), result.getInputs().get(i).getSizeGridIndex());
        }
    }

    @Test public void legacyConstructorDoesNotInventMetadataProvenance() {
        BuyReplacementQuantityFillInputContext result = compose(fill(), 1100);
        BuyReplacementQuantityFillInputContext legacy = new BuyReplacementQuantityFillInputContext(
                grid(), result.getInputs(), result.getFillCurve(), result.getEffectiveHorizonHours(),
                result.getSeasonalMultiplier(), result.getHistoryObservedAt(),
                result.getMarketContextObservedAt(), result.getComposedAt());
        assertNull(legacy.getCandidateFillInputContext());
        assertSame(result.getFillCurve(), legacy.getFillCurve());
        assertEquals(result.getInputs().size(), legacy.getInputs().size());
    }

    @Test public void originalValidationStillRejectsUnavailableAndStaleSources() {
        assertNull(compose(null, 1100));
        assertNull(compose(fill(), 1221));
        assertNull(new BuyReplacementQuantityFillInputContextComposer().compose(
                null, fill(), 1.25, 1100, 1100));
        assertNull(new BuyReplacementQuantityFillInputContextComposer().compose(
                grid(), fill(), Double.NaN, 1100, 1100));
    }

    @Test public void immutableInputsAndFinalSourceReferenceArePreserved() throws Exception {
        BuyReplacementQuantityFillInputContext result = compose(fill(), 1100);
        try { result.getInputs().clear(); fail("immutable"); }
        catch (UnsupportedOperationException expected) { }
        Field field = BuyReplacementQuantityFillInputContext.class.getDeclaredField("candidateFillInputContext");
        assertTrue(Modifier.isPrivate(field.getModifiers()));
        assertTrue(Modifier.isFinal(field.getModifiers()));
        ArrayList<BuyReplacementQuantityFillInput> copy = new ArrayList<>(result.getInputs());
        BuyReplacementQuantityFillInputContext wrapped = new BuyReplacementQuantityFillInputContext(
                grid(), copy, result.getFillCurve(), result.getEffectiveHorizonHours(),
                result.getSeasonalMultiplier(), result.getHistoryObservedAt(),
                result.getMarketContextObservedAt(), result.getComposedAt(), result.getCandidateFillInputContext());
        copy.clear();
        assertEquals(result.getInputs().size(), wrapped.getInputs().size());
        assertSame(result.getCandidateFillInputContext(), wrapped.getCandidateFillInputContext());
    }

    @Test public void repeatedCompositionRetainsTheSameSourceWithoutChangingIt() {
        BuyReplacementCandidateFillInputContext fill = fill();
        BuyReplacementQuantityFillInputContext first = compose(fill, 1100);
        BuyReplacementQuantityFillInputContext second = compose(fill, 1100);
        assertNotNull(first);
        assertNotNull(second);
        assertSame(first.getCandidateFillInputContext(), second.getCandidateFillInputContext());
        assertSame(first.getFillCurve(), second.getFillCurve());
        assertEquals(first.getInputs().size(), second.getInputs().size());
        assertEquals(1000, fill.getComposedAt());
    }

    private static BuyReplacementQuantityGridCandidateSet grid() {
        return BuyReplacementQuantityGridComposerTest.quantityFillCandidateSetForCompanionTests();
    }
    private static BuyReplacementCandidateFillInputContext fill() {
        return BuyReplacementCandidateFillInputContextComposerTest.contextForCompanionTests();
    }
    private static BuyReplacementQuantityFillInputContext compose(
            BuyReplacementCandidateFillInputContext fill, long at) {
        return new BuyReplacementQuantityFillInputContextComposer().compose(grid(), fill, 1.25, 1100, at);
    }
}
