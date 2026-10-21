package com.flippingfriend.companion;

import com.flippingfriend.core.PositionProjection;
import com.flippingfriend.core.SaleAccounting;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Read-only view of accepted execution deltas in the existing authoritative event ledger. */
final class AutomaticTradeScope {
    final Map<Integer, PositionProjection> positions = new HashMap<>();
    final Set<Integer> acquiredItems = new HashSet<>();
    final Map<String, PositionProjection> positionsAtEvent = new HashMap<>();
    final Set<Integer> unsafeItems = new HashSet<>();
    boolean hasUnassignedReconciliation;
    boolean hasUnboundedReconciliation;
    final Map<String,Set<Integer>> reconciliationScope = new HashMap<>();

    void apply(String type, int item, String name, int quantity, long cost, long when,
            String event, String offer) {
        apply(type,item,name,quantity,cost,when,event,offer,null);
    }
    void apply(String type, int item, String name, int quantity, long cost, long when,
            String event, String offer, Set<Integer> affectedItems) {
        apply(type,item,name,quantity,cost,when,event,offer,affectedItems,null);
    }
    void apply(String type, int item, String name, int quantity, long cost, long when,
            String event, String offer, Set<Integer> affectedItems,com.flippingfriend.core.AcquisitionExitContext context) {
        if (item < 0 || event == null || event.isBlank() || when < 0 || quantity < 0
                || (item == 0 && !"RECONCILE".equals(type))) {
            throw new IllegalArgumentException("malformed trade scope event");
        }
        if ("RECONCILE".equals(type)) {
            if (quantity != 0 || cost != 0) throw new IllegalArgumentException("economic reconciliation effect");
            // A rejected EMPTY observation has no item. Preserve it as account-level uncertainty;
            // it cannot create an item, consume stock or silently release exposure protection.
            if (item == 0) hasUnassignedReconciliation = true;
            else unsafeItems.add(item);
            if (affectedItems != null && !affectedItems.isEmpty()) unsafeItems.addAll(affectedItems);
            else if (item == 0 || affectedItems != null) hasUnboundedReconciliation = true;
            return;
        }
        PositionProjection prior = positions.get(item);
        if ("ACQUIRE".equals(type)) {
            acquiredItems.add(item);
            if (quantity <= 0 || cost <= 0 || offer == null || offer.isBlank()) {
                unsafeItems.add(item); return;
            }
            var origins=new ArrayList<com.flippingfriend.core.AcquisitionExitSlice>(prior==null?java.util.List.of():prior.getExitContexts());
            String origin=context==null?event:context.getSourceEventId();
            boolean added=false;
            for(int i=0;i<origins.size();i++)if(origins.get(i).getSourceEventId().equals(origin)
                && java.util.Objects.equals(origins.get(i).getContext(),context)) {
                origins.set(i,origins.get(i).add(quantity));added=true;break;
            }
            if(!added)origins.add(new com.flippingfriend.core.AcquisitionExitSlice(origin,context,quantity,quantity,when));
            positions.put(item, PositionProjection.ready(item, name,
                Math.addExact(prior == null ? 0 : prior.getQuantity(), quantity),
                Math.addExact(prior == null ? 0 : prior.getTotalCost(), cost),
                prior == null ? when : prior.getOpenedAt(), when, event, offer).withExitContexts(origins));
        } else if ("DISPOSE".equals(type)) {
            if (quantity <= 0) { unsafeItems.add(item); return; }
            if (prior == null) return; // An unrelated sale never creates a holding.
            int matched = Math.min(quantity, prior.getQuantity());
            long consumed = SaleAccounting.allocateCost(prior.getTotalCost(), prior.getQuantity(), matched);
            if (matched == prior.getQuantity()) positions.remove(item);
            else {
                var origins=new ArrayList<com.flippingfriend.core.AcquisitionExitSlice>();
                for(var slice:prior.getExitContexts())origins.add(slice.afterDisposal(matched,prior.getQuantity()-matched));
                positions.put(item, PositionProjection.ready(item, prior.getItemName(),
                prior.getQuantity() - matched, prior.getTotalCost() - consumed,
                prior.getOpenedAt(), when, event, offer).withExitContexts(origins));
            }
        } else throw new IllegalArgumentException("unknown trade scope effect");
        if (positions.containsKey(item)) positionsAtEvent.put(event, positions.get(item));
    }
    List<PositionProjection> readyPositions() {
        if (hasUnboundedReconciliation) return java.util.Collections.emptyList();
        List<PositionProjection> result = new ArrayList<>();
        for (PositionProjection p : positions.values()) if (!unsafeItems.contains(p.getItemId())) result.add(p);
        result.sort(java.util.Comparator.comparingInt(PositionProjection::getItemId));
        return result;
    }
}
