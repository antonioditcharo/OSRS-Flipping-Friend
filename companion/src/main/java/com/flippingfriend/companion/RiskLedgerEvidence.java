package com.flippingfriend.companion;

import com.flippingfriend.core.*;
import java.util.*;

/** Ephemeral risk view derived from authoritative effect rows in the same planning read. */
final class RiskLedgerEvidence {
    final RiskWindow window;
    long maxPositionCursor, profitLower, profitUpper, boundedSales, unresolvedManagedSales, untrackedUnpricedSales;
    final Map<Integer,Long> unresolvedCosts=new HashMap<>();
    // Only capital proved in the opening portfolio or acquired after the durable cursor.
    // A historical quarantined pool was never part of the window's known starting capital.
    final Map<Integer,Long> openingCosts=new HashMap<>();
    private final Map<Integer,Long> prospectiveCosts=new HashMap<>();
    private final Map<Integer,Long> settledUnpricedCosts=new HashMap<>();
    private boolean opened;
    final Set<Integer> newReconciliationItems=new HashSet<>();
    boolean unbounded;
    RiskLedgerEvidence(RiskWindow window){this.window=window;}
    /** Detached derived inputs only; current cash, offers, market marks and authority are not cached. */
    RiskLedgerEvidence copy() {
        var copy=new RiskLedgerEvidence(window);
        copy.maxPositionCursor=maxPositionCursor;copy.profitLower=profitLower;copy.profitUpper=profitUpper;
        copy.boundedSales=boundedSales;copy.unresolvedManagedSales=unresolvedManagedSales;copy.untrackedUnpricedSales=untrackedUnpricedSales;
        copy.unresolvedCosts.putAll(unresolvedCosts);copy.openingCosts.putAll(openingCosts);
        copy.prospectiveCosts.putAll(prospectiveCosts);copy.settledUnpricedCosts.putAll(settledUnpricedCosts);
        copy.newReconciliationItems.addAll(newReconciliationItems);copy.opened=opened;copy.unbounded=unbounded;return copy;
    }

    void open(AutomaticTradeScope before) {
        if(opened || window==null)return;
        opened=true;
        for(var p:before.readyPositions())openingCosts.put(p.getItemId(),p.getTotalCost());
        prospectiveCosts.putAll(openingCosts);
    }
    void apply(long cursor,String type,int item,int quantity,long acquisitionCost,long appliedCost,
            Long lower,Long upper,PositionProjection before,boolean uncertainBefore,Set<Integer> affected) {
        maxPositionCursor=Math.max(maxPositionCursor,cursor);
        if(window==null || cursor<=window.positionCursor)return;
        if(!opened)throw new IllegalStateException("Risk opening portfolio was not captured");
        if("ACQUIRE".equals(type)) {
            if(quantity<=0 || acquisitionCost<=0)throw new IllegalArgumentException("invalid acquisition risk cost");
            prospectiveCosts.merge(item,acquisitionCost,Math::addExact);
            if(uncertainBefore)reservePool(item);
        }else if("RECONCILE".equals(type)) {
            if(affected==null || affected.isEmpty())unbounded=true;
            else newReconciliationItems.addAll(affected);
        }else if("DISPOSE".equals(type)) {
            if(quantity<=0)throw new IllegalArgumentException("invalid disposal risk quantity");
            if(lower!=null && upper!=null) {
                if(lower>upper)throw new IllegalArgumentException("invalid realized risk range");
                profitLower=Math.addExact(profitLower,lower);profitUpper=Math.addExact(profitUpper,upper);boundedSales++;
                if(appliedCost<0)throw new IllegalArgumentException("invalid applied disposal cost");
                prospectiveCosts.computeIfPresent(item,(key,cost)->Math.max(0,Math.subtractExact(cost,appliedCost)));
            }else if(lower!=null || upper!=null)throw new IllegalArgumentException("partial realized risk range");
            else if(before!=null && before.getQuantity()>0 && prospectiveCosts.getOrDefault(item,0L)>0) {
                long pool=prospectiveCosts.get(item);
                if(uncertainBefore) {
                    // Which units were sold is uncertain. Reserve all newly exposed cost once;
                    // do not assign the old pool to the sale or invent disposal/profit evidence.
                    reservePool(item);
                }else {
                    long possibleCost=SaleAccounting.allocateCost(before.getTotalCost(),before.getQuantity(),Math.min(quantity,before.getQuantity()));
                    if(possibleCost>pool)throw new IllegalStateException("Unpriced sale exceeds qualified risk capital");
                    unresolvedCosts.merge(item,possibleCost,Math::addExact);
                    settledUnpricedCosts.merge(item,possibleCost,Math::addExact);
                    prospectiveCosts.put(item,pool-possibleCost);
                }
                unresolvedManagedSales++;
            }else untrackedUnpricedSales++;
        }
    }
    private void reservePool(int item) {
        long possibleLoss=Math.addExact(settledUnpricedCosts.getOrDefault(item,0L),prospectiveCosts.getOrDefault(item,0L));
        unresolvedCosts.merge(item,possibleLoss,Math::max);
    }
}
