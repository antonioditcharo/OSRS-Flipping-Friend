package com.flippingfriend.core;

/** Pure fail-closed state transition. Cancellation never authorizes replacement by itself. */
public final class BuyReplacementIntentTransition
{
    private BuyReplacementIntentTransition() { }

    public static BuyReplacementIntent apply(BuyReplacementIntent current,
        BuyReplacementIntentState next, long observedAt, String offerIdentity,
        String recommendationId, boolean cancellationObserved, boolean collectionObserved,
        boolean freshEligibilityEstablished)
    {
        if (current == null) return null;
        if (next == null || observedAt < current.getCreatedAt()
            || !same(current.getOriginalOfferIdentity(), offerIdentity)
            || !same(current.getRecommendationId(), recommendationId))
            return current.advance(BuyReplacementIntentState.RECONCILIATION_REQUIRED,
                "IDENTITY_OR_TIME_MISMATCH");
        if (observedAt > current.getExpiresAt())
            return current.advance(BuyReplacementIntentState.ABANDONED, "INTENT_EXPIRED");
        BuyReplacementIntentState state = current.getState();
        if (next == BuyReplacementIntentState.ABANDONED)
            return current.advance(next, "EXPLICITLY_ABANDONED");
        if (state == BuyReplacementIntentState.CANCEL_AUTHORIZED
            && next == BuyReplacementIntentState.CANCEL_OBSERVED && cancellationObserved)
            return current.advance(next, "TERMINAL_CANCELLATION_OBSERVED");
        if (state == BuyReplacementIntentState.CANCEL_OBSERVED
            && next == BuyReplacementIntentState.COLLECTION_REQUIRED)
            return current.advance(next, "COLLECTION_REQUIRED");
        if (state == BuyReplacementIntentState.COLLECTION_REQUIRED
            && next == BuyReplacementIntentState.ASSETS_RETURNED && collectionObserved)
            return current.advance(next, "COLLECTION_OBSERVED");
        if (state == BuyReplacementIntentState.ASSETS_RETURNED
            && next == BuyReplacementIntentState.REPLACEMENT_ELIGIBILITY_PENDING)
            return current.advance(next, "FRESH_ELIGIBILITY_REQUIRED");
        if (state == BuyReplacementIntentState.REPLACEMENT_ELIGIBILITY_PENDING
            && next == BuyReplacementIntentState.REPLACEMENT_AUTHORIZED
            && collectionObserved && freshEligibilityEstablished)
            return current.advance(next, "FRESH_ELIGIBILITY_CONFIRMED");
        return current.advance(BuyReplacementIntentState.RECONCILIATION_REQUIRED,
            "INVALID_REPLACEMENT_TRANSITION");
    }

    private static boolean same(String left, String right)
    {
        return left != null && left.equals(right);
    }
}
