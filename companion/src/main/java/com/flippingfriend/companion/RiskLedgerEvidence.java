package com.flippingfriend.companion;

import com.flippingfriend.core.*;
import java.util.*;

/** Ephemeral risk view derived from authoritative effect rows in the same planning read. */
final class RiskLedgerEvidence {
    final RiskWindow window;
    long maxPositionCursor, profitLower, profitUpper, boundedSales, unresolvedManagedSales, untrackedUnpricedSales;
    final Map<Integer,Long> unresolvedCosts=new HashMap<>();
    final Set<Integer> newReconciliationItems=new HashSet<>();
    boolean unbounded;
    RiskLedgerEvidence(RiskWindow window){this.window=window;}

    void apply(long cursor,String type,int item,int quantity,Long lower,Long upper,
            PositionProjection before,Set<Integer> affected) {
        maxPositionCursor=Math.max(maxPositionCursor,cursor);
        if(window==null || cursor<=window.positionCursor)return;
        if("RECONCILE".equals(type)) {
            if(affected==null || affected.isEmpty())unbounded=true;
            else newReconciliationItems.addAll(affected);
        }else if("DISPOSE".equals(type)) {
            if(quantity<=0)throw new IllegalArgumentException("invalid disposal risk quantity");
            if(lower!=null && upper!=null) {
                if(lower>upper)throw new IllegalArgumentException("invalid realized risk range");
                profitLower=Math.addExact(profitLower,lower);profitUpper=Math.addExact(profitUpper,upper);boundedSales++;
            }else if(lower!=null || upper!=null)throw new IllegalArgumentException("partial realized risk range");
            else if(before!=null && before.getQuantity()>0) {
                long possibleCost=SaleAccounting.allocateCost(before.getTotalCost(),before.getQuantity(),Math.min(quantity,before.getQuantity()));
                unresolvedCosts.merge(item,possibleCost,Math::addExact);unresolvedManagedSales++;
            }else untrackedUnpricedSales++;
        }
    }
}
