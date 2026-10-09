package com.flippingfriend.companion;
import java.time.DateTimeException;
import java.time.Instant;
import java.util.Map;
/** Reads one item's remaining buy allowance from the ledger at an explicit time; never writes. */
final class BuyReplacementLiveLimitObserver {
    BuyReplacementLiveLimitObservation observe(BuyLimitLedger ledger,
            Map<Integer, MarketIngestionService.Item> mapping, int itemId, long at) {
        if (ledger == null || mapping == null || itemId <= 0 || at < 0) return null;
        try {
            MarketIngestionService.Item item = mapping.get(itemId);
            if (item == null || item.buyLimit <= 0) return null;
            int remaining = ledger.remainingAt(itemId, item.buyLimit, Instant.ofEpochSecond(at));
            if (remaining < 0 || remaining > item.buyLimit) return null;
            return new BuyReplacementLiveLimitObservation(itemId, item.buyLimit, remaining, at);
        } catch (DateTimeException | ArithmeticException malformed) {
            return null;
        }
    }
}
