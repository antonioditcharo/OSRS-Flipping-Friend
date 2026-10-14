package com.flippingfriend.session;

import com.flippingfriend.data.PluginStorage;
import javax.inject.Inject;
import javax.inject.Singleton;

/** Durable guidance intent, not ownership: the exact sell offer the player was asked to reprice. */
@Singleton
public final class SellRelistIntent {
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
        load();
        if (loadedAccount == null || offer == null || offer.isBuying() || offer.getRemaining() <= 0) return;
        if (intent != null && intent.matches(offer)) return;
        intent = new Intent(); intent.item = offer.getItemId(); intent.slot = offer.getSlot();
        intent.firstSeen = offer.getFirstSeen(); intent.quantity = offer.getRemaining();
        save();
    }
    /** Return only the quantity proved cancelled and collected from this exact offer. */
    public synchronized int[] collectedRemainder(OfferTracker tracker) {
        load();
        if (intent == null || intent.item <= 0 || intent.quantity <= 0 || intent.firstSeen <= 0) return null;
        for (TrackedOffer offer : tracker.getOffers()) {
            if (!offer.isBuying() && offer.getItemId() == intent.item && !intent.matches(offer)) {
                intent = null; save(); return null; // The player already started another sale.
            }
        }
        TrackedOffer original = tracker.getOffer(intent.slot);
        if (original != null && intent.matches(original)) {
            if ("SOLD".equals(original.getState())) { intent = null; save(); }
            return null;
        }
        TrackedOffer collected = tracker.getSettledOffer(intent.slot);
        if (collected == null || !intent.matches(collected) || !collected.isCollected()
            || !"CANCELLED_SELL".equals(collected.getState())) return null;
        int remaining = Math.min(intent.quantity, collected.getRemaining());
        if (remaining <= 0) { intent = null; save(); return null; }
        return new int[] { intent.item, remaining };
    }
    private static final class Intent {
        int item, slot, quantity; long firstSeen;
        boolean matches(TrackedOffer offer) {
            return !offer.isBuying() && offer.getItemId() == item && offer.getSlot() == slot && offer.getFirstSeen() == firstSeen;
        }
    }
}
