package com.flippingfriend.core;

/** A recorded incomplete trade update. No reconstructed money or physical ownership claims. */
public final class MissedFlipEvidence {
    private final String eventId, offerIdentity, itemName, type, reason;
    private final int itemId, quantity;
    private final long observedAt;
    private final Long acquisitionCost, grossProceeds, taxLower, taxUpper, profitLower, profitUpper;

    public MissedFlipEvidence(String eventId, String offerIdentity, int itemId, String itemName,
            String type, int quantity, long observedAt, Long acquisitionCost, Long grossProceeds,
            Long taxLower, Long taxUpper, Long profitLower, Long profitUpper, String reason) {
        this.eventId = eventId; this.offerIdentity = offerIdentity; this.itemId = itemId;
        this.itemName = itemName; this.type = type; this.quantity = quantity;
        this.observedAt = observedAt; this.acquisitionCost = acquisitionCost;
        this.grossProceeds = grossProceeds; this.taxLower = taxLower; this.taxUpper = taxUpper;
        this.profitLower = profitLower; this.profitUpper = profitUpper; this.reason = reason;
        if (!isValid()) throw new IllegalArgumentException("invalid missed trade evidence");
    }
    public boolean isValid() {
        return eventId != null && !eventId.isBlank() && itemId > 0 && quantity >= 0
            && observedAt >= 0 && type != null && reason != null && !reason.isBlank()
            && (acquisitionCost == null || acquisitionCost >= 0)
            && (grossProceeds == null || grossProceeds >= 0)
            && (taxLower == null ? taxUpper == null : taxUpper != null && taxLower >= 0 && taxLower <= taxUpper)
            && (profitLower == null ? profitUpper == null : profitUpper != null && profitLower <= profitUpper);
    }
    public String getEventId() { return eventId; }
    public String getOfferIdentity() { return offerIdentity; }
    public int getItemId() { return itemId; }
    public String getItemName() { return itemName == null ? "Item " + itemId : itemName; }
    public String getType() { return type; }
    public int getQuantity() { return quantity; }
    public long getObservedAt() { return observedAt; }
    public Long getAcquisitionCost() { return acquisitionCost; }
    public Long getGrossProceeds() { return grossProceeds; }
    public Long getTaxLower() { return taxLower; }
    public Long getTaxUpper() { return taxUpper; }
    public Long getProfitLower() { return profitLower; }
    public Long getProfitUpper() { return profitUpper; }
    public String getReason() { return reason; }
}
