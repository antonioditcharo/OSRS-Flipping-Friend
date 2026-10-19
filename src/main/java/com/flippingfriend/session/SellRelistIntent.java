package com.flippingfriend.session;

import com.flippingfriend.data.PluginStorage;
import javax.inject.Inject;
import javax.inject.Singleton;

/** Durable guidance intent, not ownership: the exact sell offer the player was asked to reprice. */
@Singleton
public final class SellRelistIntent {
    /** Immutable workflow hint; current ledger and final sale authority still decide eligibility. */
    public static final class CollectedRemainder {
        private final int item, quantity;
        private final boolean managed;
        private CollectedRemainder(int item, int quantity, boolean managed) {
            this.item=item;this.quantity=quantity;this.managed=managed;
        }
        public int getItemId() { return item; }
        public int getQuantity() { return quantity; }
        public boolean requiresManagedCost() { return managed; }
    }
    private final PluginStorage storage;
    private String loadedAccount;
    private Intent intent;
    @Inject public SellRelistIntent(PluginStorage storage) { this.storage = storage; }
    private void load() {
        String account = storage.accountId();
        if (!java.util.Objects.equals(account, loadedAccount)) {
            loadedAccount = account;
            intent = account == null ? null : storage.readJson(storage.accountDir(account)
                .resolve("sell-relist-intent.json"), Intent.class, null);
        }
    }
    private void save() {
        if (loadedAccount != null && loadedAccount.equals(storage.accountId()))
            storage.writeJson(storage.accountDir(loadedAccount).resolve("sell-relist-intent.json"), intent, Intent.class);
    }
    public synchronized void advise(TrackedOffer offer) {
        advise(offer, storage.accountId());
    }
    public synchronized void advise(TrackedOffer offer, String expectedAccount) {
        advise(offer, expectedAccount, false);
    }
    public synchronized void advise(TrackedOffer offer, String expectedAccount, boolean managed) {
        advise(offer, expectedAccount, managed, null);
    }
    public synchronized void advise(TrackedOffer offer, String expectedAccount, boolean managed, OfferTracker tracker) {
        if (expectedAccount == null || !expectedAccount.equals(storage.accountId())) return;
        load();
        if (!expectedAccount.equals(loadedAccount) || offer == null || offer.isBuying() || offer.getRemaining() <= 0) return;
        if (!"SELLING".equals(offer.getState())) return;
        if (tracker != null) {
            TrackedOffer current = tracker.getOffer(offer.getSlot());
            if (current == null || !new OfferKey(offer).matches(current) || !"SELLING".equals(current.getState())) return;
        }
        if (intent != null && intent.matches(offer)) {
            if (managed && !intent.managed) { intent.managed = true; save(); }
            return;
        }
        intent = new Intent(); intent.item = offer.getItemId(); intent.slot = offer.getSlot();
        intent.firstSeen = offer.getFirstSeen(); intent.quantity = offer.getRemaining();
        intent.episode = offer.getEpisodeIdentity(); intent.price = offer.getPrice();
        intent.total = offer.getTotalQuantity(); intent.filled = offer.getQuantityFilled();
        intent.managed = managed;
        intent.existingSales = new java.util.ArrayList<>();
        if (tracker != null) for (TrackedOffer existing : tracker.getOffers()) {
            if (!existing.isBuying() && existing.getItemId() == intent.item && !intent.matches(existing))
                intent.existingSales.add(new OfferKey(existing));
        }
        save();
    }
    /** Called synchronously on offer changes, before a collected slot's temporary evidence is retired. */
    public synchronized void observe(OfferTracker tracker, String expectedAccount) {
        collectedRemainder(tracker, expectedAccount);
    }
    public synchronized boolean requiresManagedCost(String expectedAccount) {
        if (expectedAccount == null || !expectedAccount.equals(storage.accountId())) return true;
        load(); return intent != null && intent.managed;
    }
    public synchronized CollectedRemainder collectedRequest(OfferTracker tracker, String expectedAccount) {
        int[] remaining=collectedRemainder(tracker,expectedAccount);
        return remaining==null?null:new CollectedRemainder(remaining[0],remaining[1],intent.managed);
    }
    /** Return only the quantity proved cancelled and collected from this exact offer. */
    public synchronized int[] collectedRemainder(OfferTracker tracker) {
        return collectedRemainder(tracker, storage.accountId());
    }
    public synchronized int[] collectedRemainder(OfferTracker tracker, String expectedAccount) {
        if (expectedAccount == null || !expectedAccount.equals(storage.accountId())) return null;
        load();
        if (!expectedAccount.equals(loadedAccount)) return null;
        if (intent == null || intent.item <= 0 || intent.quantity <= 0 || intent.firstSeen <= 0) return null;
        for (TrackedOffer offer : tracker.getOffers()) {
            if (!offer.isBuying() && offer.getItemId() == intent.item && !intent.matches(offer)
                && !intent.wasAlreadyListed(offer)) {
                intent = null; save(); return null; // The player already started another sale.
            }
        }
        TrackedOffer original = tracker.getOffer(intent.slot);
        if (original != null && intent.matches(original)) {
            if ("SOLD".equals(original.getState())) { intent = null; save(); }
            return null;
        }
        TrackedOffer collected = tracker.getSettledOffer(intent.slot);
        if (collected != null && intent.matches(collected) && "SOLD".equals(collected.getState())) {
            intent = null; save(); return null;
        }
        if (intent.collectionVerified && (collected == null || !intent.matches(collected)))
            return intent.collectedRemaining > 0 && intent.collectedRemaining <= intent.quantity
                ? new int[] { intent.item, intent.collectedRemaining } : null;
        if (collected == null || !intent.matches(collected) || !collected.isCollected()
            || !"CANCELLED_SELL".equals(collected.getState())
            || collected.getQuantityFilled() < Math.max(0, intent.collectionVerified?intent.collectedFilled:intent.filled)
            || collected.getQuantityFilled() > collected.getTotalQuantity()) {
            if (intent.collectionVerified) { intent=null;save(); }
            return null;
        }
        int remaining = Math.min(intent.quantity, collected.getRemaining());
        if (remaining <= 0) { intent = null; save(); return null; }
        if (!intent.collectionVerified || remaining != intent.collectedRemaining) {
            intent.collectedRemaining=remaining;intent.collectedFilled=collected.getQuantityFilled();
            intent.collectionVerified=true;save();
        }
        return new int[] { intent.item, remaining };
    }
    private static final class Intent {
        int item, slot, quantity; long firstSeen;
        String episode; int price, total, filled;
        boolean managed;
        boolean collectionVerified; int collectedRemaining, collectedFilled;
        java.util.List<OfferKey> existingSales;
        boolean wasAlreadyListed(TrackedOffer offer) {
            if (existingSales != null) for (OfferKey key : existingSales)
                if (key != null && key.matches(offer)) return true;
            return false;
        }
        boolean matches(TrackedOffer offer) {
            return !offer.isBuying() && offer.getItemId() == item && offer.getSlot() == slot
                && offer.getFirstSeen() == firstSeen
                && java.util.Objects.equals(episode, offer.getEpisodeIdentity())
                && (price == 0 || price == offer.getPrice())
                && (total == 0 || total == offer.getTotalQuantity());
        }
    }
    private static final class OfferKey {
        final int item, slot, price, total; final long firstSeen; final String episode;
        OfferKey(TrackedOffer offer) {
            item=offer.getItemId();slot=offer.getSlot();price=offer.getPrice();total=offer.getTotalQuantity();
            firstSeen=offer.getFirstSeen();episode=offer.getEpisodeIdentity();
        }
        boolean matches(TrackedOffer offer) {
            return !offer.isBuying() && item==offer.getItemId() && slot==offer.getSlot()
                && price==offer.getPrice() && total==offer.getTotalQuantity() && firstSeen==offer.getFirstSeen()
                && java.util.Objects.equals(episode,offer.getEpisodeIdentity());
        }
    }
}
