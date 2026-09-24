package com.flippingfriend.core;

/** Pure reducer for canonical offer lifecycle observations. */
public final class OfferLifecycleReducer
{
    private OfferLifecycleReducer() { }

    public static OfferLifecycleTransition apply(OfferLifecycleProjection previous, OfferEvent event)
    {
        OfferLifecycleState incoming = OfferLifecycleClassifier.classify(event);
        OfferLifecycleState before = previous == null ? null : previous.getState();
        if (incoming == OfferLifecycleState.ERROR_RECONCILIATION)
        {
            return rejected(previous, event, "invalid source observation");
        }
        if (previous == null)
        {
            return accepted(null, projection(incoming, event), event, false);
        }
        if (incoming == OfferLifecycleState.EMPTY)
        {
            if (before == OfferLifecycleState.EMPTY)
            {
                return accepted(before, empty(event), event, sameEmpty(previous, event));
            }
            if (isTerminal(before))
            {
                return accepted(before, empty(event), event, false);
            }
            return rejected(previous, event, "offer cleared before a terminal observation");
        }
        if (before == OfferLifecycleState.EMPTY)
        {
            return accepted(before, projection(incoming, event), event, false);
        }
        String incompatibility = incompatible(previous, event);
        if (incompatibility != null)
        {
            return rejected(previous, event, incompatibility);
        }
        if (!allowed(before, incoming))
        {
            return rejected(previous, event, "invalid lifecycle transition");
        }
        if (event.getFilledQuantity() < previous.getFilledQuantity())
        {
            return rejected(previous, event, "filled quantity regressed");
        }
        if (event.getSpent() < previous.getSpent())
        {
            return rejected(previous, event, "spent amount regressed");
        }
        OfferLifecycleProjection next = projection(incoming, event);
        return accepted(before, next, event, same(previous, next));
    }

    private static String incompatible(OfferLifecycleProjection previous, OfferEvent event)
    {
        if (previous.getSlot() != event.getSlot()) return "slot changed";
        if (previous.isBuying() != event.isBuying()) return "side changed";
        if (previous.getItemId() != event.getItemId()) return "item changed";
        if (previous.getPrice() != event.getPrice()) return "price changed";
        if (previous.getTotalQuantity() != event.getTotalQuantity()) return "total quantity changed";
        if (differentKnown(previous.getOfferIdentity(), event.getOfferIdentity())) return "offer identity changed";
        if (differentKnown(previous.getSessionId(), event.getSessionId())) return "session changed during offer";
        if (sameKnown(previous.getSessionId(), event.getSessionId())
            && event.getSequence() < previous.getLastSequence()) return "sequence regressed";
        return null;
    }

    private static boolean allowed(OfferLifecycleState from, OfferLifecycleState to)
    {
        if (from == to) return true;
        switch (from)
        {
            case BUY_OPEN:
            case BUY_PARTIAL:
                return to == OfferLifecycleState.BUY_PARTIAL
                    || to == OfferLifecycleState.BUY_FILLED_UNCOLLECTED
                    || to == OfferLifecycleState.BUY_CANCELLED_UNCOLLECTED;
            case SELL_OPEN:
            case SELL_PARTIAL:
                return to == OfferLifecycleState.SELL_PARTIAL
                    || to == OfferLifecycleState.SELL_FILLED_UNCOLLECTED
                    || to == OfferLifecycleState.SELL_CANCELLED_UNCOLLECTED;
            default:
                return false;
        }
    }

    private static boolean isTerminal(OfferLifecycleState state)
    {
        return state == OfferLifecycleState.BUY_FILLED_UNCOLLECTED
            || state == OfferLifecycleState.BUY_CANCELLED_UNCOLLECTED
            || state == OfferLifecycleState.SELL_FILLED_UNCOLLECTED
            || state == OfferLifecycleState.SELL_CANCELLED_UNCOLLECTED;
    }

    private static OfferLifecycleTransition accepted(OfferLifecycleState before,
        OfferLifecycleProjection projection, OfferEvent event, boolean idempotent)
    {
        return new OfferLifecycleTransition(before, projection.getState(), action(projection.getState()),
            projection, event, true, idempotent, idempotent ? "observation already projected" : "accepted");
    }

    private static OfferLifecycleTransition rejected(OfferLifecycleProjection previous,
        OfferEvent event, String reason)
    {
        OfferLifecycleProjection error = previous == null
            ? new OfferLifecycleProjection(OfferLifecycleState.ERROR_RECONCILIATION,
                event == null ? -1 : event.getSlot(), null, null, 0, 0, 0, null, false, 0, 0, 0, 0, null)
            : new OfferLifecycleProjection(OfferLifecycleState.ERROR_RECONCILIATION,
                previous.getSlot(), previous.getOfferIdentity(), previous.getSessionId(),
                previous.getLastSequence(), previous.getLastObservedAt(), previous.getItemId(),
                previous.getItemName(), previous.isBuying(), previous.getPrice(),
                previous.getTotalQuantity(), previous.getFilledQuantity(), previous.getSpent(),
                previous.getRecommendationId());
        return new OfferLifecycleTransition(previous == null ? null : previous.getState(),
            OfferLifecycleState.ERROR_RECONCILIATION, OfferLifecycleAction.RECONCILE,
            error, event, false, false, reason);
    }

    private static OfferLifecycleProjection projection(OfferLifecycleState state, OfferEvent event)
    {
        return new OfferLifecycleProjection(state, event.getSlot(), event.getOfferIdentity(),
            event.getSessionId(), event.getSequence(), event.getObservedAt(), event.getItemId(),
            event.getItemName(), event.isBuying(), event.getPrice(), event.getTotalQuantity(),
            event.getFilledQuantity(), event.getSpent(), event.getRecommendationId());
    }

    private static OfferLifecycleProjection empty(OfferEvent event)
    {
        return new OfferLifecycleProjection(OfferLifecycleState.EMPTY, event.getSlot(), null,
            event.getSessionId(), event.getSequence(), event.getObservedAt(), 0, null, false,
            0, 0, 0, 0, null);
    }

    private static OfferLifecycleAction action(OfferLifecycleState state)
    {
        if (state == OfferLifecycleState.BUY_FILLED_UNCOLLECTED
            || state == OfferLifecycleState.BUY_CANCELLED_UNCOLLECTED
            || state == OfferLifecycleState.SELL_FILLED_UNCOLLECTED
            || state == OfferLifecycleState.SELL_CANCELLED_UNCOLLECTED)
        {
            return OfferLifecycleAction.COLLECT;
        }
        return state == OfferLifecycleState.ERROR_RECONCILIATION
            ? OfferLifecycleAction.RECONCILE : OfferLifecycleAction.WAIT;
    }

    private static boolean same(OfferLifecycleProjection left, OfferLifecycleProjection right)
    {
        return left.getState() == right.getState()
            && left.getSlot() == right.getSlot()
            && equal(left.getOfferIdentity(), right.getOfferIdentity())
            && equal(left.getSessionId(), right.getSessionId())
            && left.getLastSequence() == right.getLastSequence()
            && left.getLastObservedAt() == right.getLastObservedAt()
            && left.getItemId() == right.getItemId()
            && left.isBuying() == right.isBuying()
            && left.getPrice() == right.getPrice()
            && left.getTotalQuantity() == right.getTotalQuantity()
            && left.getFilledQuantity() == right.getFilledQuantity()
            && left.getSpent() == right.getSpent()
            && equal(left.getRecommendationId(), right.getRecommendationId());
    }

    private static boolean sameEmpty(OfferLifecycleProjection previous, OfferEvent event)
    {
        return previous.getSlot() == event.getSlot()
            && previous.getLastSequence() == event.getSequence()
            && previous.getLastObservedAt() == event.getObservedAt()
            && equal(previous.getSessionId(), event.getSessionId());
    }

    private static boolean equal(String left, String right)
    {
        return left == null ? right == null : left.equals(right);
    }
    private static boolean blank(String value) { return value == null || value.trim().isEmpty(); }
    private static boolean differentKnown(String left, String right)
    {
        return !blank(left) && !blank(right) && !left.equals(right);
    }
    private static boolean sameKnown(String left, String right)
    {
        return !blank(left) && left.equals(right);
    }
}
