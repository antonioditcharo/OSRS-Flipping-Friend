package com.flippingfriend.core;

import java.util.Collection;
import java.util.Objects;

/** Player trading intent for one exact unresolved position, never an accounting correction. */
public final class StockDeclaration {
    public enum Use { PERSONAL, NO_LONGER_HELD }
    private final Use use;
    private final int itemId, quantity;
    private final long totalCost, openedAt;
    private final boolean costKnown;
    private final String sourceEventId, sourceOfferIdentity;

    public StockDeclaration(PositionProjection position, Use use) {
        if (position == null || use == null) throw new IllegalArgumentException("position and use required");
        this.use = use; this.itemId = position.getItemId(); this.quantity = position.getQuantity();
        this.totalCost = position.getTotalCost(); this.costKnown = position.isCostKnown();
        this.openedAt = position.getOpenedAt(); this.sourceEventId = position.getSourceEventId();
        this.sourceOfferIdentity = position.getSourceOfferIdentity();
        if (!isValid()) throw new IllegalArgumentException("only unresolved unpriced stock can be reviewed");
    }
    public Use getUse() { return use; }
    public int getItemId() { return itemId; }
    public int getQuantity() { return quantity; }
    public long getTotalCost() { return totalCost; }
    public long getOpenedAt() { return openedAt; }
    public boolean isCostKnown() { return costKnown; }
    public boolean isValid() {
        return use != null && itemId > 0 && quantity >= 0 && totalCost == 0 && !costKnown && openedAt >= 0
            && (use != Use.PERSONAL || (quantity > 0 && sourceEventId == null && sourceOfferIdentity == null));
    }
    public boolean matches(PositionProjection position) {
        return isValid() && position != null && position.getConsistencyState() == PositionConsistencyState.RECONCILE
            && itemId == position.getItemId() && quantity == position.getQuantity()
            && totalCost == position.getTotalCost() && costKnown == position.isCostKnown()
            && openedAt == position.getOpenedAt() && Objects.equals(sourceEventId, position.getSourceEventId())
            && Objects.equals(sourceOfferIdentity, position.getSourceOfferIdentity());
    }
    public boolean excludes(PositionProjection position, Collection<OfferLifecycleProjection> offers) {
        if (!matches(position) || offers == null) return false;
        for (OfferLifecycleProjection offer : offers) {
            if (offer == null) return false;
            if (offer.getItemId() != itemId) continue;
            switch (offer.getState()) {
                case EMPTY: break;
                case SELL_FILLED_UNCOLLECTED:
                    if (offer.isBuying() || offer.getTotalQuantity() <= 0
                            || offer.getFilledQuantity() != offer.getTotalQuantity()) return false;
                    break;
                case BUY_CANCELLED_UNCOLLECTED:
                    if (!offer.isBuying() || offer.getFilledQuantity() > 0) return false;
                    break;
                case SELL_CANCELLED_UNCOLLECTED:
                    if (offer.isBuying() || offer.getTotalQuantity() > offer.getFilledQuantity()) return false;
                    break;
                default: return false; // Open buys, remaining sell stock and unknown offers outrank intent.
            }
        }
        return true;
    }
}
