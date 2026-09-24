package com.flippingfriend.core;

/** Immutable incremental ownership effect derived from one lifecycle transition. */
public final class PositionAccountingEffect
{
    private final PositionAccountingEffectType type;
    private final int itemId;
    private final String itemName;
    private final int quantity;
    private final long acquisitionCost;
    private final String offerIdentity;
    private final String sourceEventId;
    private final long observedAt;
    private final String reason;

    private PositionAccountingEffect(PositionAccountingEffectType type, int itemId, String itemName,
        int quantity, long acquisitionCost, String offerIdentity, String sourceEventId,
        long observedAt, String reason)
    {
        if (type == null || quantity < 0 || acquisitionCost < 0 || observedAt < 0)
            throw new IllegalArgumentException("invalid accounting effect");
        if (type == PositionAccountingEffectType.ACQUIRE
            && (itemId <= 0 || quantity <= 0 || acquisitionCost <= 0))
            throw new IllegalArgumentException("acquisition requires item, quantity, and cost");
        if (type == PositionAccountingEffectType.DISPOSE
            && (itemId <= 0 || quantity <= 0 || acquisitionCost != 0))
            throw new IllegalArgumentException("disposal requires item and quantity only");
        if ((type == PositionAccountingEffectType.NONE || type == PositionAccountingEffectType.RECONCILE)
            && (quantity != 0 || acquisitionCost != 0))
            throw new IllegalArgumentException("non-economic effect cannot carry quantity or cost");
        if (type == PositionAccountingEffectType.RECONCILE
            && (reason == null || reason.trim().isEmpty()))
            throw new IllegalArgumentException("reconciliation requires a reason");
        this.type = type; this.itemId = itemId; this.itemName = itemName;
        this.quantity = quantity; this.acquisitionCost = acquisitionCost;
        this.offerIdentity = offerIdentity; this.sourceEventId = sourceEventId;
        this.observedAt = observedAt; this.reason = reason;
    }

    public static PositionAccountingEffect none(OfferEvent event)
    { return from(PositionAccountingEffectType.NONE, event, 0, 0, null); }
    public static PositionAccountingEffect acquire(OfferEvent event, int quantity, long cost)
    { return from(PositionAccountingEffectType.ACQUIRE, event, quantity, cost, null); }
    public static PositionAccountingEffect dispose(OfferEvent event, int quantity)
    { return from(PositionAccountingEffectType.DISPOSE, event, quantity, 0, null); }
    public static PositionAccountingEffect reconcile(OfferEvent event, String reason)
    { return from(PositionAccountingEffectType.RECONCILE, event, 0, 0, reason); }

    private static PositionAccountingEffect from(PositionAccountingEffectType type, OfferEvent event,
        int quantity, long cost, String reason)
    {
        return new PositionAccountingEffect(type, event == null ? 0 : event.getItemId(),
            event == null ? null : event.getItemName(), quantity, cost,
            event == null ? null : event.getOfferIdentity(),
            event == null ? null : event.getEventId(), event == null ? 0 : event.getObservedAt(), reason);
    }

    public PositionAccountingEffectType getType() { return type; }
    public int getItemId() { return itemId; }
    public String getItemName() { return itemName; }
    public int getQuantity() { return quantity; }
    public long getAcquisitionCost() { return acquisitionCost; }
    public String getOfferIdentity() { return offerIdentity; }
    public String getSourceEventId() { return sourceEventId; }
    public long getObservedAt() { return observedAt; }
    public String getReason() { return reason; }
}
