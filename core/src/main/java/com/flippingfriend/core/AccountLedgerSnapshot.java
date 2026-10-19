package com.flippingfriend.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Durable recorded-sale totals; unresolved sales are excluded, never valued at zero profit. */
public final class AccountLedgerSnapshot
{
    private String schemaVersion;
    private List<PositionProjection> tradingPositions;
    private List<RecordedSale> recentSales;
    private List<MissedFlipEvidence> missedFlips;
    /** Optional source-event scope. Empty item sets mean the gap still has account-wide uncertainty. */
    private java.util.Map<String, java.util.Set<Integer>> reconciliationScope;
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
    public AccountLedgerSnapshot withTradeEvidence(List<PositionProjection> trades, List<MissedFlipEvidence> missed,
            java.util.Map<String, java.util.Set<Integer>> scope) {
        AccountLedgerSnapshot copy=withTradeEvidence(trades,missed);
        if (scope == null) throw new IllegalArgumentException("reconciliation scope required");
        copy.reconciliationScope=copyScope(scope);
        if (!copy.isValid()) throw new IllegalArgumentException("invalid reconciliation scope");
        return copy;
    }
    public java.util.Map<String, java.util.Set<Integer>> getReconciliationScope() {
        return reconciliationScope == null ? null : copyScope(reconciliationScope);
    }
    private static java.util.Map<String, java.util.Set<Integer>> copyScope(
            java.util.Map<String, java.util.Set<Integer>> source) {
        java.util.Map<String,java.util.Set<Integer>> copy=new java.util.HashMap<>();
        source.forEach((key,items)->copy.put(key,items==null?null:
            Collections.unmodifiableSet(new java.util.HashSet<>(items))));
        return Collections.unmodifiableMap(copy);
    }
    /** Null on old servers: missing acquisition evidence must never enroll legacy positions. */
    public List<PositionProjection> getTradingPositions() { return tradingPositions; }
    /** Bounded diagnostic history; never a training prefix or a session denominator. Null on older companions. */
    public List<RecordedSale> getRecentSales() { return recentSales; }
    public AccountLedgerSnapshot withRecentSales(List<RecordedSale> sales) {
        if(sales==null)throw new IllegalArgumentException("sale history required");
        AccountLedgerSnapshot copy=new AccountLedgerSnapshot(accountId,observedAt,this.sales,boundedSales,uncertainSales,
            taxBoundedSales,profitLower,profitUpper,taxLower,taxUpper,positions);
        copy.schemaVersion=schemaVersion;copy.tradingPositions=tradingPositions;copy.missedFlips=missedFlips;
        copy.reconciliationScope=reconciliationScope;copy.recentSales=Collections.unmodifiableList(new ArrayList<>(sales));
        if(!copy.isValid())throw new IllegalArgumentException("invalid sale history");return copy;
    }
    public List<MissedFlipEvidence> getMissedFlips() { return missedFlips; }
    /** Totals remain readable, but an unidentified lifecycle gap cannot establish safe holdings. */
    public boolean hasUnassignedReconciliation() {
        return missedFlips != null && missedFlips.stream().anyMatch(e -> e != null
            && e.getItemId() == 0 && "RECONCILE".equals(e.getType()));
    }
    /** Historic item-zero rows remain unchanged; source proof may limit their affected items. */
    public boolean hasUnboundedReconciliation() {
        if (reconciliationScope == null) return hasUnassignedReconciliation();
        return missedFlips == null || missedFlips.stream().filter(e->e!=null && "RECONCILE".equals(e.getType()))
            .anyMatch(e->reconciliationScope.get(e.getEventId())==null
                || reconciliationScope.get(e.getEventId()).isEmpty());
    }
    private boolean validReconciliationScope() {
        if (reconciliationScope == null) return true; // Older servers keep the conservative global guard.
        if (!"2".equals(schemaVersion) || missedFlips == null || tradingPositions == null) return false;
        java.util.Set<String> expected=new java.util.HashSet<>();
        java.util.Set<Integer> affected=new java.util.HashSet<>();
        for (MissedFlipEvidence e:missedFlips) if ("RECONCILE".equals(e.getType())) {
            expected.add(e.getEventId());
            java.util.Set<Integer> items=reconciliationScope.get(e.getEventId());
            if (items==null || items.stream().anyMatch(item->item==null || item<=0)) return false;
            if (!items.isEmpty() && e.getItemId()>0 && !items.contains(e.getItemId())) return false;
            affected.addAll(items);
        }
        return expected.equals(reconciliationScope.keySet())
            && tradingPositions.stream().noneMatch(p->affected.contains(p.getItemId()))
            && (!hasUnboundedReconciliation() || tradingPositions.isEmpty());
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
        if(recentSales!=null) {
            if(recentSales.size()>100 || recentSales.size()>sales)return false;
            long prior=Long.MAX_VALUE;
            for(RecordedSale sale:recentSales) {
                if(sale==null || !sale.isValid() || sale.getRowId()>=prior || sale.getObservedAt()>observedAt)return false;
                prior=sale.getRowId();
            }
        }
        return ("1".equals(schemaVersion) || "2".equals(schemaVersion))
            && (!"2".equals(schemaVersion) || (tradingPositions != null && missedFlips != null
                && tradingPositions.stream().allMatch(p -> p != null && p.getConsistencyState() == PositionConsistencyState.READY
                    && p.getItemId() > 0 && p.getQuantity() > 0 && p.getTotalCost() > 0 && p.isCostKnown() && p.getSourceEventId() != null
                    && !p.getSourceEventId().isBlank() && p.getSourceOfferIdentity() != null && !p.getSourceOfferIdentity().isBlank()
                    && p.getOpenedAt() >= 0 && p.getUpdatedAt() >= p.getOpenedAt() && p.getUpdatedAt() <= observedAt
                    && (p.getExitContext()==null || p.getExitContext().isBound()))
                && missedFlips.stream().allMatch(e -> e != null && e.isValid() && e.getObservedAt() <= observedAt))) && accountId != null && accountId.matches("rs-[0-9a-f]{64}")
            && observedAt >= 0 && sales >= 0 && boundedSales >= 0 && boundedSales <= sales
            && uncertainSales >= 0 && uncertainSales <= boundedSales && unresolvedSales == sales - boundedSales
            && taxBoundedSales >= boundedSales && taxBoundedSales <= sales && unresolvedTaxSales == sales - taxBoundedSales
            && profitLower <= profitUpper && taxLower >= 0 && taxLower <= taxUpper
            && validReconciliationScope()
            && positions != null && positions.stream().allMatch(position -> position != null
                && position.getItemId() > 0 && position.getQuantity() >= 0 && position.getTotalCost() >= 0
                && position.getConsistencyState() != null);
    }
}
