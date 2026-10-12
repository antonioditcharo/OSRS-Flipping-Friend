package com.flippingfriend.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Durable recorded-sale totals; unresolved sales are excluded, never valued at zero profit. */
public final class AccountLedgerSnapshot
{
    private final String schemaVersion;
    private final String accountId;
    private final long observedAt, sales, boundedSales, uncertainSales, unresolvedSales;
    private final long taxBoundedSales, unresolvedTaxSales;
    private final long profitLower, profitUpper, taxLower, taxUpper;
    private final List<PositionProjection> positions;

    public AccountLedgerSnapshot(String accountId, long observedAt, long sales, long boundedSales,
        long uncertainSales, long taxBoundedSales, long profitLower, long profitUpper, long taxLower, long taxUpper,
        List<PositionProjection> positions)
    {
        this.schemaVersion = "1";
        this.accountId = AccountIdentity.require(accountId); this.observedAt = observedAt;
        if (observedAt < 0 || sales < 0 || boundedSales < 0 || boundedSales > sales
            || uncertainSales < 0 || uncertainSales > boundedSales || taxBoundedSales < boundedSales
            || taxBoundedSales > sales || profitLower > profitUpper
            || taxLower < 0 || taxLower > taxUpper || positions == null)
            throw new IllegalArgumentException("invalid account ledger");
        this.sales = sales; this.boundedSales = boundedSales; this.uncertainSales = uncertainSales;
        this.unresolvedSales = sales - boundedSales;
        this.taxBoundedSales = taxBoundedSales; this.unresolvedTaxSales = sales - taxBoundedSales;
        this.profitLower = profitLower; this.profitUpper = profitUpper;
        this.taxLower = taxLower; this.taxUpper = taxUpper;
        this.positions = Collections.unmodifiableList(new ArrayList<>(positions));
    }

    public String getSchemaVersion() { return schemaVersion; }
    public String getAccountId() { return accountId; }
    public long getObservedAt() { return observedAt; }
    public long getSales() { return sales; }
    public long getBoundedSales() { return boundedSales; }
    public long getUncertainSales() { return uncertainSales; }
    public long getUnresolvedSales() { return unresolvedSales; }
    public long getTaxBoundedSales() { return taxBoundedSales; }
    public long getUnresolvedTaxSales() { return unresolvedTaxSales; }
    public long getProfitLower() { return profitLower; }
    public long getProfitUpper() { return profitUpper; }
    public long getTaxLower() { return taxLower; }
    public long getTaxUpper() { return taxUpper; }
    public List<PositionProjection> getPositions() { return positions; }
    public long getPositionsNeedingReview()
    { return positions.stream().filter(position -> position.getConsistencyState() == PositionConsistencyState.RECONCILE).count(); }

    public boolean isValid()
    {
        return "1".equals(schemaVersion) && accountId != null && accountId.matches("rs-[0-9a-f]{64}")
            && observedAt >= 0 && sales >= 0 && boundedSales >= 0 && boundedSales <= sales
            && uncertainSales >= 0 && uncertainSales <= boundedSales && unresolvedSales == sales - boundedSales
            && taxBoundedSales >= boundedSales && taxBoundedSales <= sales && unresolvedTaxSales == sales - taxBoundedSales
            && profitLower <= profitUpper && taxLower >= 0 && taxLower <= taxUpper
            && positions != null && positions.stream().allMatch(position -> position != null
                && position.getItemId() > 0 && position.getQuantity() >= 0 && position.getTotalCost() >= 0
                && position.getConsistencyState() != null);
    }
}
