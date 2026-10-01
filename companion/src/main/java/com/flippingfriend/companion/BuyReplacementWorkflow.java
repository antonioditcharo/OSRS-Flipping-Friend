package com.flippingfriend.companion;

import com.flippingfriend.core.BuyReplacementIntent;
import com.flippingfriend.core.OfferEvent;
import com.flippingfriend.core.OfferLifecycleAction;
import com.flippingfriend.core.PolicyAbstentionReason;
import com.flippingfriend.core.PolicyDecision;
import com.flippingfriend.core.PolicyDecisionType;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** Non-activated identity-safe bridge from an authorized cancellation to durable replacement intent. */
final class BuyReplacementWorkflow
{
    static final long INTENT_LIFETIME_SECONDS = 300;
    private final SqliteStore store;

    BuyReplacementWorkflow(SqliteStore store)
    {
        if (store == null) throw new IllegalArgumentException("store is required");
        this.store = store;
    }

    BuyReplacementIntent recordCancellation(PolicyDecision decision, OfferEvent offer,
        long createdAt) throws Exception
    {
        if (!valid(decision, offer, createdAt)) return null;
        BuyReplacementIntent intent = BuyReplacementIntent.cancelAuthorized(
            intentId(decision, offer, createdAt), offer.getOfferIdentity(),
            offer.getRecommendationId(), offer.getSlot(), offer.getItemId(), offer.getItemName(),
            offer.getPrice(), offer.getTotalQuantity(), offer.getFilledQuantity(), createdAt,
            createdAt + INTENT_LIFETIME_SECONDS);
        store.saveBuyReplacementIntent(intent);
        return intent;
    }

    private static boolean valid(PolicyDecision decision, OfferEvent offer, long createdAt)
    {
        return decision != null && offer != null && createdAt >= 0
            && decision.getDecisionType() == PolicyDecisionType.BUY_MAINTENANCE
            && decision.getAction() == OfferLifecycleAction.CANCEL_BUY
            && decision.getAbstentionReason() == PolicyAbstentionReason.NONE
            && offer.isBuying() && "BUYING".equals(offer.getEventType())
            && offer.getSlot() >= 0 && offer.getItemId() > 0 && !blank(offer.getItemName())
            && offer.getPrice() > 0 && offer.getTotalQuantity() > 0
            && offer.getFilledQuantity() >= 0 && offer.getFilledQuantity() < offer.getTotalQuantity()
            && same(offer.getOfferIdentity(), decision.getCandidateId())
            && same(offer.getRecommendationId(), decision.getRecommendationId());
    }

    private static String intentId(PolicyDecision decision, OfferEvent offer, long createdAt)
    {
        String canonical = decision.getDecisionId() + '|' + offer.getOfferIdentity() + '|'
            + offer.getRecommendationId() + '|' + offer.getSlot() + '|' + offer.getItemId()
            + '|' + offer.getPrice() + '|' + offer.getTotalQuantity() + '|'
            + offer.getFilledQuantity() + '|' + createdAt;
        try
        {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(canonical.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder("buy-replacement-");
            for (int i = 0; i < 16; i++) result.append(String.format("%02x", digest[i]));
            return result.toString();
        }
        catch (Exception ex) { throw new IllegalStateException("SHA-256 is unavailable", ex); }
    }
    private static boolean same(String left, String right) { return left != null && left.equals(right); }
    private static boolean blank(String value) { return value == null || value.trim().isEmpty(); }
}
