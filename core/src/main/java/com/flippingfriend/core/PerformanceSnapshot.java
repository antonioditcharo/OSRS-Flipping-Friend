package com.flippingfriend.core;

import java.util.*;

/** Display-only account measurements. Never an input to entry risk or execution accounting. */
public final class PerformanceSnapshot {
    public static final class Sale {
        public final String offerIdentity, itemName;
        public final int itemId;
        public final long quantity, observedAt, updates;
        public final Long profitLower, profitUpper;
        /** Recorded amounts only; null preserves missing or legacy detail. Matched cost is not
         * necessarily the full cost when profit is unresolved. */
        public final Long gross,matchedCost,taxLower,taxUpper;
        public final List<String> accountingReasons;
        public final boolean completed;
        public boolean archived;
        public Sale(String offer,int item,String name,long quantity,long at,long updates,Long low,Long high,boolean completed) {
            this(offer,item,name,quantity,at,updates,low,high,completed,null,null,null,null,null);
        }
        public Sale(String offer,int item,String name,long quantity,long at,long updates,Long low,Long high,boolean completed,
            Long gross,Long matchedCost,Long taxLow,Long taxHigh,List<String> reasons) {
            this.offerIdentity=offer;itemId=item;itemName=name;this.quantity=quantity;observedAt=at;this.updates=updates;
            profitLower=low;profitUpper=high;this.completed=completed;
            this.gross=gross;this.matchedCost=matchedCost;taxLower=taxLow;taxUpper=taxHigh;
            accountingReasons=reasons==null?null:Collections.unmodifiableList(new ArrayList<>(reasons));
        }
    }
    public final String accountId, scope, sessionId;
    public final long observedAt, startedAt, positionCursor, saleUpdates, unresolvedSales, completedFlips, appliedCost;
    public final long profitLower, profitUpper;
    public long archivedUnresolvedSales;
    public final List<Sale> sales;
    /** Null means no current proven valuation. Partial marks are explicitly identified by reserve. */
    public Long unrealizedProfit, portfolioValue, unpricedCost;
    /** Exact copied cash and canonical acquisition cost, independent of resale estimates. */
    public Long spendableCoins, managedAcquisitionCost;
    public List<Stock> stockValuations;
    public static final class Stock {
        public final int itemId,quantity,observedQuantity,pricedQuantity,price;
        public final String itemName,unpricedReason;
        public final long totalCost,quotedAt,netValue,pricedProfit;
        public Stock(int item,String name,int quantity,int observed,int priced,long cost,int price,long quotedAt,long net,String reason) {
            itemId=item;itemName=name;this.quantity=quantity;observedQuantity=observed;pricedQuantity=priced;
            totalCost=cost;this.price=price;this.quotedAt=quotedAt;netValue=net;unpricedReason=reason;
            pricedProfit=Math.subtractExact(net,priced==0?0:SaleAccounting.allocateCost(cost,quantity,priced));
        }
        public boolean isValid(long at) {
            if(itemId<=0 || quantity<=0 || observedQuantity<0 || observedQuantity>quantity || pricedQuantity<0
                || pricedQuantity>observedQuantity || totalCost<0 || netValue<0 || unpricedReason==null)return false;
            if(pricedQuantity==0)return price==0 && quotedAt==0 && netValue==0 && pricedProfit==0;
            return price>0 && quotedAt>0 && quotedAt<=at && netValue<=Math.multiplyExact((long)price,pricedQuantity)
                && pricedProfit==Math.subtractExact(netValue,SaleAccounting.allocateCost(totalCost,quantity,pricedQuantity));
        }
    }
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
            && archivedUnresolvedSales>=0 && archivedUnresolvedSales<=unresolvedSales
            && appliedCost>=0 && profitLower<=profitUpper && sales!=null
            && sales.stream().allMatch(s->s!=null && s.itemId>0 && s.quantity>0 && s.updates>0
                && s.observedAt>=0 && s.observedAt<=observedAt && (s.profitLower==null)==(s.profitUpper==null)
                && (s.profitLower==null || s.profitLower<=s.profitUpper)
                && (!s.archived || s.profitLower==null)
                && (s.gross==null || s.gross>=0) && (s.matchedCost==null || s.matchedCost>=0)
                && (s.taxLower==null)==(s.taxUpper==null)
                && (s.taxLower==null || s.gross!=null && s.taxLower>=0 && s.taxLower<=s.taxUpper && s.taxUpper<=s.gross)
                && (s.accountingReasons==null || s.accountingReasons.stream().allMatch(Objects::nonNull)))
            && (unrealizedProfit==null)==(portfolioValue==null) && (unrealizedProfit==null)==(unpricedCost==null)
            && (portfolioValue==null || portfolioValue>=0 && unpricedCost>=0) && validStockValuation();
    }
    private boolean validStockValuation() {
        if(spendableCoins==null && managedAcquisitionCost==null && stockValuations==null)return true; // Legacy display reads.
        if(spendableCoins==null || spendableCoins<0 || managedAcquisitionCost==null || managedAcquisitionCost<0
            || stockValuations==null || portfolioValue==null)return false;
        try {
            long cost=0,unpriced=0,net=0,profit=0;Set<Integer> items=new HashSet<>();
            for(var stock:stockValuations) {
                if(stock==null || !stock.isValid(observedAt) || !items.add(stock.itemId))return false;
                cost=Math.addExact(cost,stock.totalCost);net=Math.addExact(net,stock.netValue);profit=Math.addExact(profit,stock.pricedProfit);
                long pricedCost=stock.pricedQuantity==0?0:SaleAccounting.allocateCost(stock.totalCost,stock.quantity,stock.pricedQuantity);
                unpriced=Math.addExact(unpriced,stock.totalCost-pricedCost);
            }
            return managedAcquisitionCost==cost && unpricedCost==unpriced && portfolioValue==net && unrealizedProfit==profit;
        }catch(RuntimeException unsupported){return false;}
    }
}
