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

    void apply(String type, int item, String name, int quantity, long cost, long when,
            String event, String offer) {
        if (item <= 0 || event == null || event.isBlank() || when < 0 || quantity < 0) {
            throw new IllegalArgumentException("malformed trade scope event");
        }
        if ("RECONCILE".equals(type)) { unsafeItems.add(item); return; }
        PositionProjection prior = positions.get(item);
        if ("ACQUIRE".equals(type)) {
            acquiredItems.add(item);
            if (quantity <= 0 || cost <= 0 || offer == null || offer.isBlank()) {
                unsafeItems.add(item); return;
            }
            positions.put(item, PositionProjection.ready(item, name,
                Math.addExact(prior == null ? 0 : prior.getQuantity(), quantity),
                Math.addExact(prior == null ? 0 : prior.getTotalCost(), cost),
                prior == null ? when : prior.getOpenedAt(), when, event, offer));
        } else if ("DISPOSE".equals(type)) {
            if (quantity <= 0) { unsafeItems.add(item); return; }
            if (prior == null) return; // An unrelated sale never creates a holding.
            int matched = Math.min(quantity, prior.getQuantity());
            long consumed = SaleAccounting.allocateCost(prior.getTotalCost(), prior.getQuantity(), matched);
            if (matched == prior.getQuantity()) positions.remove(item);
            else positions.put(item, PositionProjection.ready(item, prior.getItemName(),
                prior.getQuantity() - matched, prior.getTotalCost() - consumed,
                prior.getOpenedAt(), when, event, offer));
        } else throw new IllegalArgumentException("unknown trade scope effect");
        if (positions.containsKey(item)) positionsAtEvent.put(event, positions.get(item));
    }
    List<PositionProjection> readyPositions() {
        List<PositionProjection> result = new ArrayList<>();
        for (PositionProjection p : positions.values()) if (!unsafeItems.contains(p.getItemId())) result.add(p);
        result.sort(java.util.Comparator.comparingInt(PositionProjection::getItemId));
        return result;
    }
}
