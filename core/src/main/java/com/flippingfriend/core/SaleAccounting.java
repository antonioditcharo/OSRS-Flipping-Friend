package com.flippingfriend.core;

import com.flippingfriend.model.TaxCalculator;

/** Bounds from an observed gross delta; a divisible average does not prove equal fill prices. */
public final class SaleAccounting
{
    public static final String CURRENT_POLICY = "GE_2025_05_29";
    public static final String UNPROVEN_PLACEMENT = "PLACEMENT_UNPROVEN";
    // Do not guess the exact update time on 29 May. Empty-slot evidence from the next day is safe.
    public static final long CURRENT_POLICY_SAFE_FROM = java.time.Instant.parse("2025-05-30T00:00:00Z").getEpochSecond();

    private final long gross;
    private final Long taxLower, taxUpper, profitLower, profitUpper;
    private final String reason;

    private SaleAccounting(long gross, Long taxLower, Long taxUpper, Long cost, String reason)
    {
        this.gross = gross; this.taxLower = taxLower; this.taxUpper = taxUpper;
        this.profitLower = cost == null || taxUpper == null ? null : gross - taxUpper - cost;
        this.profitUpper = cost == null || taxLower == null ? null : gross - taxLower - cost;
        this.reason = cost == null ? "COST_BASIS_UNRESOLVED; " + reason : reason;
    }

    public static SaleAccounting calculate(long gross, int quantity, int minimumPrice,
        String itemName, String policy, Long cost)
    {
        if (quantity <= 0 || (cost != null && cost < 0)) throw new IllegalArgumentException("invalid sale accounting");
        if (minimumPrice <= 0 || gross < (long) minimumPrice * quantity
            || gross > (long) Integer.MAX_VALUE * quantity)
            return new SaleAccounting(gross, null, null, cost, "PROCEEDS_UNRESOLVED");
        if (!CURRENT_POLICY.equals(policy))
        {
            long upper = UNPROVEN_PLACEMENT.equals(policy)
                ? Math.min(gross / 50, (long) quantity * TaxCalculator.MAX_TAX_PER_ITEM) : gross;
            return new SaleAccounting(gross, 0L, upper, cost, "PLACEMENT_TAX_RULE_UNPROVEN");
        }
        if (itemName == null || itemName.isBlank() || itemName.matches("Item \\d+"))
            return new SaleAccounting(gross, 0L, Math.min(gross / 50,
                (long) quantity * TaxCalculator.MAX_TAX_PER_ITEM), cost, "ITEM_TAX_CLASSIFICATION_UNRESOLVED");
        if (TaxCalculator.isExemptName(itemName))
            return new SaleAccounting(gross, 0L, 0L, cost, "EXEMPT_UNDER_RECORDED_RULE");
        if (quantity == 1 || gross == (long) minimumPrice * quantity)
        {
            long price = quantity == 1 ? gross : minimumPrice;
            long tax = (long) quantity * Math.min(price / 50, TaxCalculator.MAX_TAX_PER_ITEM);
            return new SaleAccounting(gross, tax, tax, cost, "EXACT_UNDER_RECORDED_RULE");
        }
        long lower = (long) quantity * Math.min(minimumPrice / 50, TaxCalculator.MAX_TAX_PER_ITEM);
        long upper = Math.min(gross / 50, (long) quantity * TaxCalculator.MAX_TAX_PER_ITEM);
        long largestPossiblePrice = gross - (long) (quantity - 1) * minimumPrice;
        if (largestPossiblePrice < TaxCalculator.CAP_THRESHOLD)
            lower = Math.max(lower, gross / 50 - (quantity - 1));
        return new SaleAccounting(gross, lower, upper, cost,
            lower == upper ? "EXACT_UNDER_RECORDED_RULE" : "AGGREGATE_FILL_TAX_RANGE");
    }

    /** Conserves every coin, including the last unit, without overflowing cost * quantity. */
    public static long allocateCost(long totalCost, int ownedQuantity, int soldQuantity)
    {
        if (totalCost < 0 || ownedQuantity <= 0 || soldQuantity < 0 || soldQuantity > ownedQuantity)
            throw new IllegalArgumentException("invalid cost allocation");
        return (totalCost / ownedQuantity) * soldQuantity
            + ((totalCost % ownedQuantity) * soldQuantity) / ownedQuantity;
    }

    public long getGross() { return gross; }
    public Long getTaxLower() { return taxLower; }
    public Long getTaxUpper() { return taxUpper; }
    public Long getProfitLower() { return profitLower; }
    public Long getProfitUpper() { return profitUpper; }
    public String getReason() { return reason; }
}
