package com.flippingfriend.companion;

import com.flippingfriend.core.CompanionAction;
import com.flippingfriend.core.OfferEvent;
import java.util.Collection;
import java.util.Comparator;

/** Selects the highest-priority action currently owned by the companion. */
final class CompanionActionSelector
{
        CompanionAction select(Collection<OfferEvent> offers)
        {
                OfferEvent collectable = null;
                if (offers != null)
                {
                        collectable = offers.stream()
                                .filter(this::isCollectable)
                                .min(Comparator
                                        .comparingLong(CompanionActionSelector::orderingTime)
                                        .thenComparingInt(OfferEvent::getSlot))
                                .orElse(null);
                }

                if (collectable == null)
                {
                        return CompanionAction.waiting(
                                "No companion action",
                                "No completed or cancelled offer is waiting to be collected.");
                }

                String name = collectable.getItemName() == null
                        ? "Item " + collectable.getItemId()
                        : collectable.getItemName();
                String side = collectable.isBuying() ? "buy" : "sell";
                String pickup = collectable.isBuying() ? "items" : "coins";

                return CompanionAction.collect(
                        collectable.getItemId(),
                        name,
                        collectable.getSlot(),
                        collectable.getFilledQuantity(),
                        "Collect your " + name,
                        "Your " + side + " offer has finished. Collect it to free up the slot and pick up your "
                                + pickup + ".");
        }

        private boolean isCollectable(OfferEvent offer)
        {
                if (offer == null || offer.getEventType() == null || offer.getSlot() < 0)
                {
                        return false;
                }

                switch (offer.getEventType())
                {
                        case "BOUGHT":
                        case "SOLD":
                        case "CANCELLED_BUY":
                        case "CANCELLED_SELL":
                                return true;
                        default:
                                return false;
                }
        }

        private static long orderingTime(OfferEvent offer)
        {
                long observedAt = offer.getObservedAt();
                return observedAt > 0 ? observedAt : Long.MAX_VALUE;
        }
}
