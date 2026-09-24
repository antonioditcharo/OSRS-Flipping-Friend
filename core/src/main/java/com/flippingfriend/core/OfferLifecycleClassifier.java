package com.flippingfriend.core;

/** Deterministically classifies one raw offer observation without projection context. */
public final class OfferLifecycleClassifier
{
    private OfferLifecycleClassifier()
    {
    }

    public static OfferLifecycleState classify(OfferEvent event)
    {
        if (event == null)
        {
            return OfferLifecycleState.ERROR_RECONCILIATION;
        }

        String state = event.getEventType();
        if (state == null || state.trim().isEmpty())
        {
            return OfferLifecycleState.ERROR_RECONCILIATION;
        }
        if ("EMPTY".equals(state))
        {
            return OfferLifecycleState.EMPTY;
        }

        int total = event.getTotalQuantity();
        int filled = event.getFilledQuantity();
        if (total <= 0 || filled < 0 || filled > total)
        {
            return OfferLifecycleState.ERROR_RECONCILIATION;
        }

        switch (state)
        {
            case "BUYING":
                if (!event.isBuying() || filled >= total)
                {
                    return OfferLifecycleState.ERROR_RECONCILIATION;
                }
                return filled == 0 ? OfferLifecycleState.BUY_OPEN : OfferLifecycleState.BUY_PARTIAL;
            case "BOUGHT":
                return event.isBuying() && filled == total
                    ? OfferLifecycleState.BUY_FILLED_UNCOLLECTED
                    : OfferLifecycleState.ERROR_RECONCILIATION;
            case "CANCELLED_BUY":
                return event.isBuying()
                    ? OfferLifecycleState.BUY_CANCELLED_UNCOLLECTED
                    : OfferLifecycleState.ERROR_RECONCILIATION;
            case "SELLING":
                if (event.isBuying() || filled >= total)
                {
                    return OfferLifecycleState.ERROR_RECONCILIATION;
                }
                return filled == 0 ? OfferLifecycleState.SELL_OPEN : OfferLifecycleState.SELL_PARTIAL;
            case "SOLD":
                return !event.isBuying() && filled == total
                    ? OfferLifecycleState.SELL_FILLED_UNCOLLECTED
                    : OfferLifecycleState.ERROR_RECONCILIATION;
            case "CANCELLED_SELL":
                return !event.isBuying()
                    ? OfferLifecycleState.SELL_CANCELLED_UNCOLLECTED
                    : OfferLifecycleState.ERROR_RECONCILIATION;
            default:
                return OfferLifecycleState.ERROR_RECONCILIATION;
        }
    }
}
