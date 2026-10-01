package com.flippingfriend.core;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class BuyReplacementIntentTest
{
    @Test public void cancellationDoesNotAuthorizeReplacement()
    {
        BuyReplacementIntent intent = intent();
        assertEquals(BuyReplacementIntentState.CANCEL_AUTHORIZED, intent.getState());
        assertEquals(400, intent.getRemainderQuantity());
    }

    @Test public void requiresCancellationCollectionAndFreshEligibility()
    {
        BuyReplacementIntent intent = intent();
        intent = move(intent, BuyReplacementIntentState.CANCEL_OBSERVED, true, false, false);
        intent = move(intent, BuyReplacementIntentState.COLLECTION_REQUIRED, false, false, false);
        intent = move(intent, BuyReplacementIntentState.ASSETS_RETURNED, false, true, false);
        intent = move(intent, BuyReplacementIntentState.REPLACEMENT_ELIGIBILITY_PENDING, false, true, false);
        assertEquals(BuyReplacementIntentState.REPLACEMENT_ELIGIBILITY_PENDING, intent.getState());
        intent = move(intent, BuyReplacementIntentState.REPLACEMENT_AUTHORIZED, false, true, true);
        assertEquals(BuyReplacementIntentState.REPLACEMENT_AUTHORIZED, intent.getState());
    }

    @Test public void invalidOrderAndMissingFreshnessFailClosed()
    {
        BuyReplacementIntent invalid = move(intent(), BuyReplacementIntentState.ASSETS_RETURNED,
            false, true, false);
        assertEquals(BuyReplacementIntentState.RECONCILIATION_REQUIRED, invalid.getState());
        BuyReplacementIntent pending = intent();
        pending = move(pending, BuyReplacementIntentState.CANCEL_OBSERVED, true, false, false);
        pending = move(pending, BuyReplacementIntentState.COLLECTION_REQUIRED, false, false, false);
        pending = move(pending, BuyReplacementIntentState.ASSETS_RETURNED, false, true, false);
        pending = move(pending, BuyReplacementIntentState.REPLACEMENT_ELIGIBILITY_PENDING, false, true, false);
        pending = move(pending, BuyReplacementIntentState.REPLACEMENT_AUTHORIZED, false, true, false);
        assertEquals(BuyReplacementIntentState.RECONCILIATION_REQUIRED, pending.getState());
    }

    @Test public void mismatchedLineageAndExpiryNeverAuthorizeReplacement()
    {
        BuyReplacementIntent mismatch = BuyReplacementIntentTransition.apply(intent(),
            BuyReplacementIntentState.CANCEL_OBSERVED, 110, "other", "plan-1", true, false, false);
        assertEquals(BuyReplacementIntentState.RECONCILIATION_REQUIRED, mismatch.getState());
        BuyReplacementIntent expired = BuyReplacementIntentTransition.apply(intent(),
            BuyReplacementIntentState.CANCEL_OBSERVED, 201, "offer-1", "plan-1", true, false, false);
        assertEquals(BuyReplacementIntentState.ABANDONED, expired.getState());
    }

    private static BuyReplacementIntent move(BuyReplacementIntent current,
        BuyReplacementIntentState next, boolean cancelled, boolean collected, boolean fresh)
    {
        return BuyReplacementIntentTransition.apply(current, next, 110, "offer-1", "plan-1",
            cancelled, collected, fresh);
    }

    private static BuyReplacementIntent intent()
    {
        return BuyReplacementIntent.cancelAuthorized("intent-1", "offer-1", "plan-1", 2,
            4151, "Abyssal whip", 1000, 500, 100, 100, 200);
    }
}
