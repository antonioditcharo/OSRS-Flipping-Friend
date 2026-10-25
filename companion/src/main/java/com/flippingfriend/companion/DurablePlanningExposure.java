package com.flippingfriend.companion;

import com.flippingfriend.core.PositionConsistencyState;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/** Durable held cost plus outstanding open-buy reservations; null means unsafe to plan. */
final class DurablePlanningExposure {
    final Map<Integer, Long> committedByItem;
    final long reservedBuyCoins;
    final java.util.Set<Integer> unavailableEntryItems;

    private DurablePlanningExposure(Map<Integer, Long> byItem, long reserved) {
        this(byItem,reserved,java.util.Collections.emptySet());
    }
    DurablePlanningExposure(Map<Integer,Long> byItem,long reserved,java.util.Set<Integer> paused) {
        unavailableEntryItems=java.util.Collections.unmodifiableSet(new java.util.HashSet<>(paused));
        committedByItem = Collections.unmodifiableMap(new HashMap<>(byItem));
        reservedBuyCoins = reserved;
    }
    static DurablePlanningExposure forAccount(SqliteStore.PlanningProjections read,
            com.flippingfriend.core.AccountSnapshot account,long now) {
        if(account==null)return null;
        if(!account.isAutomaticTradeScope())return null; // Old publishers cannot authorize current entries.
        if(!CurrentOfferReadSet.valid(read,account,now) || read.tradeScope==null || read.tradeScope.hasUnboundedReconciliation)return null;
        DurablePlanningExposure result=read.tradeScope.hasUnassignedReconciliation || !read.tradeScope.unsafeItems.isEmpty()
            ? BoundedHistoricalExposure.calculate(read):from(read,account.getStockDeclarations(),true);
        if(result==null)return null;
        try{long total=account.getSpendableCoins();for(long value:result.committedByItem.values())total=Math.addExact(total,value);}
        catch(ArithmeticException unrepresentable){return null;}
        return result;
    }
    static String refusalReason(SqliteStore.PlanningProjections read,com.flippingfriend.core.AccountSnapshot account,long now) {
        if(!CurrentOfferReadSet.valid(read,account,now))return "Waiting for an acknowledged current cash and eight-slot capture.";
        if(read.tradeScope==null)return "Recorded trade holdings are unavailable.";
        if(read.tradeScope.hasUnboundedReconciliation) {
            var ids=new java.util.TreeSet<String>();
            read.tradeScope.reconciliationScope.forEach((id,items)->{if(items.isEmpty())ids.add(id);});
            return "New buys paused: trade observation scope is unresolved ("+ids.size()+" source records). Open Records → Trade recovery for the affected evidence.";
        }
        return "New buys paused: recorded capital or current offer exposure is contradictory or cannot be bounded. Open Records → Trade recovery.";
    }

    static DurablePlanningExposure from(SqliteStore.PlanningProjections read) {
        return from(read, Collections.emptyMap());
    }

    static DurablePlanningExposure from(SqliteStore.PlanningProjections read,
            Map<Integer, com.flippingfriend.core.StockDeclaration> declarations) {
        return from(read, declarations, false);
    }
    static DurablePlanningExposure from(SqliteStore.PlanningProjections read,
            Map<Integer, com.flippingfriend.core.StockDeclaration> declarations, boolean automatic) {
        if (read == null) return null;
        try {
            Map<Integer, Long> byItem = new HashMap<>();
            long reserved = 0;
            if (automatic) {
                if (read.tradeScope == null || read.tradeScope.hasUnassignedReconciliation
                        || !read.tradeScope.unsafeItems.isEmpty()) return null;
                for (var p : read.tradeScope.readyPositions()) byItem.put(p.getItemId(), p.getTotalCost());
            }
            for (var position : read.positions) {
                if (automatic && position != null) {
                    // Snapshot-only personal records and untracked old sales are not trade positions.
                    // Known legacy capital still guards entries; unresolved money is never ignored.
                    if (!position.isCostKnown() && position.getTotalCost() == 0) continue;
                    if (position.isCostKnown() && position.getConsistencyState() == PositionConsistencyState.READY) {
                        var atSource = read.tradeScope.positionsAtEvent.get(position.getSourceEventId());
                        boolean covered = atSource != null && atSource.getItemId() == position.getItemId()
                            && atSource.getQuantity() == position.getQuantity() && atSource.getTotalCost() == position.getTotalCost();
                        if (!covered) byItem.merge(position.getItemId(), position.getTotalCost(), Math::max);
                        continue;
                    }
                }
                var declaration = position == null || declarations == null ? null
                    : declarations.get(position.getItemId());
                if (!automatic && declaration != null && declaration.excludes(position, read.offers)) continue;
                if (position == null || position.getItemId() <= 0
                        || position.getConsistencyState() != PositionConsistencyState.READY
                        || !position.isCostKnown() || position.getQuantity() <= 0
                        || position.getTotalCost() < 0) return null;
                byItem.merge(position.getItemId(), position.getTotalCost(), Math::addExact);
            }
            for (var offer : read.offers) {
                if (offer == null) return null;
                switch (offer.getState()) {
                    case BUY_OPEN:
                    case BUY_PARTIAL:
                        if (!offer.isBuying() || offer.getItemId() <= 0
                                || offer.getPrice() <= 0 || offer.getTotalQuantity() <= 0
                                || offer.getFilledQuantity() < 0
                                || offer.getFilledQuantity() >= offer.getTotalQuantity()) return null;
                        long remaining = (long) offer.getTotalQuantity() - offer.getFilledQuantity();
                        long coins = Math.multiplyExact((long) offer.getPrice(), remaining);
                        reserved = Math.addExact(reserved, coins);
                        byItem.merge(offer.getItemId(), coins, Math::addExact);
                        break;
                    case EMPTY:
                    case BUY_FILLED_UNCOLLECTED:
                    case BUY_CANCELLED_UNCOLLECTED:
                    case SELL_OPEN:
                    case SELL_PARTIAL:
                    case SELL_FILLED_UNCOLLECTED:
                    case SELL_CANCELLED_UNCOLLECTED:
                        break;
                    default:
                        return null;
                }
            }
            long total = 0;
            for (long value : byItem.values())
                total = Math.addExact(total, value);
            return new DurablePlanningExposure(byItem, reserved);
        } catch (ArithmeticException | NullPointerException malformed) {
            return null;
        }
    }
}
