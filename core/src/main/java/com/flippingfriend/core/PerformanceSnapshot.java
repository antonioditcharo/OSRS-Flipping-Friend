package com.flippingfriend.core;

import java.util.*;

/** Display-only account measurements. Never an input to entry risk or execution accounting. */
public final class PerformanceSnapshot {
    public static final class Sale {
        public final String offerIdentity, itemName;
        public final int itemId;
        public final long quantity, observedAt, updates;
        public final Long profitLower, profitUpper;
        public final boolean completed;
        public Sale(String offer,int item,String name,long quantity,long at,long updates,Long low,Long high,boolean completed) {
            this.offerIdentity=offer;itemId=item;itemName=name;this.quantity=quantity;observedAt=at;this.updates=updates;
            profitLower=low;profitUpper=high;this.completed=completed;
        }
    }
    public final String accountId, scope, sessionId;
    public final long observedAt, startedAt, positionCursor, saleUpdates, unresolvedSales, completedFlips, appliedCost;
    public final long profitLower, profitUpper;
    public final List<Sale> sales;
    /** Null means no current proven valuation. Partial marks are explicitly identified by reserve. */
    public Long unrealizedProfit, portfolioValue, unpricedCost;
    public PerformanceSnapshot(String account,String scope,String session,long at,long start,long cursor,
        long updates,long unresolved,long completed,long cost,long low,long high,List<Sale> sales) {
        accountId=AccountIdentity.require(account);this.scope=scope;sessionId=session;observedAt=at;startedAt=start;
        positionCursor=cursor;saleUpdates=updates;unresolvedSales=unresolved;completedFlips=completed;appliedCost=cost;
        profitLower=low;profitUpper=high;this.sales=Collections.unmodifiableList(new ArrayList<>(sales));
        if(!isValid())throw new IllegalArgumentException("Invalid performance measurement");
    }
    public boolean isValid() {
        return accountId!=null && accountId.matches("rs-[0-9a-f]{64}") && scope!=null && Set.of("SESSION","DAY","WEEK","ALL").contains(scope)
            && observedAt>=0 && startedAt>=0 && startedAt<=observedAt && positionCursor>=0
            && saleUpdates>=0 && unresolvedSales>=0 && unresolvedSales<=saleUpdates && completedFlips>=0
            && appliedCost>=0 && profitLower<=profitUpper && sales!=null
            && sales.stream().allMatch(s->s!=null && s.itemId>0 && s.quantity>0 && s.updates>0
                && s.observedAt>=0 && s.observedAt<=observedAt && (s.profitLower==null)==(s.profitUpper==null)
                && (s.profitLower==null || s.profitLower<=s.profitUpper))
            && (unrealizedProfit==null)==(portfolioValue==null) && (unrealizedProfit==null)==(unpricedCost==null)
            && (portfolioValue==null || portfolioValue>=0 && unpricedCost>=0);
    }
}
