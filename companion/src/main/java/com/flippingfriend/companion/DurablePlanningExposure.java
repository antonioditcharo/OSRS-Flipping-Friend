package com.flippingfriend.companion;

import com.flippingfriend.core.PositionConsistencyState;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/** Durable held cost plus outstanding open-buy reservations; null means unsafe to plan. */
final class DurablePlanningExposure {
    final Map<Integer, Long> committedByItem;
    final long reservedBuyCoins;

    private DurablePlanningExposure(Map<Integer, Long> byItem, long reserved) {
        committedByItem = Collections.unmodifiableMap(new HashMap<>(byItem));
        reservedBuyCoins = reserved;
    }

    static DurablePlanningExposure from(SqliteStore.PlanningProjections read) {
        if (read == null) return null;
        try {
            Map<Integer, Long> byItem = new HashMap<>();
            long reserved = 0;
            for (var position : read.positions) {
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
