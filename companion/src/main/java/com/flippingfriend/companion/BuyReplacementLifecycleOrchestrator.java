package com.flippingfriend.companion;

import com.flippingfriend.core.BuyReplacementIntent;
import com.flippingfriend.core.BuyReplacementIntentState;
import com.flippingfriend.core.BuyReplacementIntentTransition;
import com.flippingfriend.core.OfferEvent;
import com.flippingfriend.core.OfferLifecycleProjection;
import com.flippingfriend.core.OfferLifecycleState;
import com.flippingfriend.core.OfferLifecycleTransition;

/** Non-activated identity-safe bridge from canonical cancellation and collection evidence. */
final class BuyReplacementLifecycleOrchestrator
{
    BuyReplacementIntent advance(BuyReplacementIntent intent,
        OfferLifecycleProjection previous, OfferLifecycleTransition transition)
    {
        if (intent == null || previous == null || transition == null || !transition.isAccepted())
            return intent;
        if (intent.getState() == BuyReplacementIntentState.CANCEL_AUTHORIZED
            || intent.getState() == BuyReplacementIntentState.CANCEL_OBSERVED)
            return cancellation(intent, previous, transition);
        if (intent.getState() == BuyReplacementIntentState.COLLECTION_REQUIRED
            || intent.getState() == BuyReplacementIntentState.ASSETS_RETURNED)
            return collection(intent, previous, transition);
        return intent;
    }

    private static BuyReplacementIntent cancellation(BuyReplacementIntent intent,
        OfferLifecycleProjection previous, OfferLifecycleTransition transition)
    {
        OfferLifecycleProjection next = transition.getProjection();
        if (next == null || transition.isIdempotent()
            || transition.getNewState() != OfferLifecycleState.BUY_CANCELLED_UNCOLLECTED
            || (transition.getPreviousState() != OfferLifecycleState.BUY_OPEN
                && transition.getPreviousState() != OfferLifecycleState.BUY_PARTIAL)
            || previous.getState() != transition.getPreviousState()
            || !matches(intent, next) || !same(previous.getOfferIdentity(), next.getOfferIdentity()))
            return intent;
        BuyReplacementIntent result = intent;
        if (result.getState() == BuyReplacementIntentState.CANCEL_AUTHORIZED)
            result = BuyReplacementIntentTransition.apply(result,
                BuyReplacementIntentState.CANCEL_OBSERVED, next.getLastObservedAt(),
                next.getOfferIdentity(), next.getRecommendationId(), true, false, false);
        if (result.getState() == BuyReplacementIntentState.CANCEL_OBSERVED)
            result = BuyReplacementIntentTransition.apply(result,
                BuyReplacementIntentState.COLLECTION_REQUIRED, next.getLastObservedAt(),
                next.getOfferIdentity(), next.getRecommendationId(), false, false, false);
        return result;
    }

    private static BuyReplacementIntent collection(BuyReplacementIntent intent,
        OfferLifecycleProjection previous, OfferLifecycleTransition transition)
    {
        OfferLifecycleProjection next = transition.getProjection();
        OfferEvent source = transition.getSourceEvent();
        if (next == null || source == null || transition.isIdempotent()
            || transition.getPreviousState() != OfferLifecycleState.BUY_CANCELLED_UNCOLLECTED
            || transition.getNewState() != OfferLifecycleState.EMPTY
            || previous.getState() != OfferLifecycleState.BUY_CANCELLED_UNCOLLECTED
            || next.getState() != OfferLifecycleState.EMPTY
            || !"EMPTY".equals(source.getEventType())
            || previous.getSlot() != next.getSlot() || previous.getSlot() != source.getSlot()
            || !matches(intent, previous))
            return intent;
        BuyReplacementIntent result = intent;
        if (result.getState() == BuyReplacementIntentState.COLLECTION_REQUIRED)
            result = BuyReplacementIntentTransition.apply(result,
                BuyReplacementIntentState.ASSETS_RETURNED, source.getObservedAt(),
                previous.getOfferIdentity(), previous.getRecommendationId(), false, true, false);
        if (result.getState() == BuyReplacementIntentState.ASSETS_RETURNED)
            result = BuyReplacementIntentTransition.apply(result,
                BuyReplacementIntentState.REPLACEMENT_ELIGIBILITY_PENDING, source.getObservedAt(),
                previous.getOfferIdentity(), previous.getRecommendationId(), false, true, false);
        return result;
    }

    private static boolean matches(BuyReplacementIntent intent, OfferLifecycleProjection projection)
    {
        return projection.getSlot() == intent.getSlot() && projection.isBuying()
            && projection.getItemId() == intent.getItemId()
            && projection.getPrice() == intent.getOriginalPrice()
            && projection.getTotalQuantity() == intent.getOriginalQuantity()
            && projection.getFilledQuantity() == intent.getFilledQuantity()
            && same(projection.getOfferIdentity(), intent.getOriginalOfferIdentity())
            && same(projection.getRecommendationId(), intent.getRecommendationId());
    }

    private static boolean same(String left, String right)
    {
        return left != null && left.equals(right);
    }
}
