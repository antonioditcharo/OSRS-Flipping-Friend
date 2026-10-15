package com.flippingfriend.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Durable recorded-sale totals; unresolved sales are excluded, never valued at zero profit. */
public final class AccountLedgerSnapshot
{
    private String schemaVersion;
    private List<PositionProjection> tradingPositions;
    private List<MissedFlipEvidence> missedFlips;
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

    public AccountLedgerSnapshot withTradeEvidence(List<PositionProjection> trades, List<MissedFlipEvidence> missed) {
        AccountLedgerSnapshot copy = new AccountLedgerSnapshot(accountId, observedAt, sales, boundedSales,
            uncertainSales, taxBoundedSales, profitLower, profitUpper, taxLower, taxUpper, positions);
        copy.schemaVersion = "2";
        copy.tradingPositions = Collections.unmodifiableList(new ArrayList<>(trades));
        copy.missedFlips = Collections.unmodifiableList(new ArrayList<>(missed));
        if (!copy.isValid()) throw new IllegalArgumentException("invalid trade evidence");
        return copy;
    }
    /** Null on old servers: missing acquisition evidence must never enroll legacy positions. */
    public List<PositionProjection> getTradingPositions() { return tradingPositions; }
    public List<MissedFlipEvidence> getMissedFlips() { return missedFlips; }
    /** Totals remain readable, but an unidentified lifecycle gap cannot establish safe holdings. */
    public boolean hasUnassignedReconciliation() {
        return missedFlips != null && missedFlips.stream().anyMatch(e -> e != null
            && e.getItemId() == 0 && "RECONCILE".equals(e.getType()));
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
        return ("1".equals(schemaVersion) || "2".equals(schemaVersion))
            && (!"2".equals(schemaVersion) || (tradingPositions != null && missedFlips != null
                && tradingPositions.stream().allMatch(p -> p != null && p.getConsistencyState() == PositionConsistencyState.READY
                    && p.getItemId() > 0 && p.getQuantity() > 0 && p.getTotalCost() > 0 && p.isCostKnown() && p.getSourceEventId() != null
                    && !p.getSourceEventId().isBlank() && p.getSourceOfferIdentity() != null && !p.getSourceOfferIdentity().isBlank()
                    && p.getOpenedAt() >= 0 && p.getUpdatedAt() >= p.getOpenedAt() && p.getUpdatedAt() <= observedAt)
                && missedFlips.stream().allMatch(e -> e != null && e.isValid() && e.getObservedAt() <= observedAt))) && accountId != null && accountId.matches("rs-[0-9a-f]{64}")
            && observedAt >= 0 && sales >= 0 && boundedSales >= 0 && boundedSales <= sales
            && uncertainSales >= 0 && uncertainSales <= boundedSales && unresolvedSales == sales - boundedSales
            && taxBoundedSales >= boundedSales && taxBoundedSales <= sales && unresolvedTaxSales == sales - taxBoundedSales
            && profitLower <= profitUpper && taxLower >= 0 && taxLower <= taxUpper
            && positions != null && positions.stream().allMatch(position -> position != null
                && position.getItemId() > 0 && position.getQuantity() >= 0 && position.getTotalCost() >= 0
                && position.getConsistencyState() != null);
    }
}
