package com.flippingfriend.companion;

import com.flippingfriend.core.BuyMaintenancePolicy;
import com.flippingfriend.core.OfferLifecycleAction;
import com.flippingfriend.core.PolicyAbstentionReason;
import com.flippingfriend.core.PolicyDecision;
import com.flippingfriend.core.PolicyDecisionType;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** Pure, non-activated first companion owner for open-buy maintenance decisions. */
final class CompanionBuyMaintenancePolicy implements BuyMaintenancePolicy<BuyMaintenancePolicyContext>
{
    static final String SCHEMA_VERSION = "1";
    static final String POLICY_VERSION = "companion-buy-maintenance-1";
    static final long MAX_INPUT_AGE_SECONDS = 120;

    @Override public PolicyDecision decide(BuyMaintenancePolicyContext context)
    {
        if (context == null) throw new IllegalArgumentException("context is required");
        long now = context.getDecidedAt();
        long observedAt = Math.min(context.getOfferObservedAt(), context.getMarketObservedAt());
        String recommendation = value(context.getRecommendationId(), "none");
        String offer = value(context.getOfferIdentity(), "none");
        String id = decisionId(context, recommendation, offer);
        if (now < 0 || observedAt < 0 || observedAt > now)
            return abstain(id, recommendation, offer, now, Math.max(0, Math.min(now, observedAt)),
                PolicyAbstentionReason.OFFER_STATE_INCONSISTENT, "BUY_MAINTENANCE_INPUT_INVALID");
        if (!context.isOpenBuy() || context.getSlot() < 0 || context.getItemId() <= 0
            || context.getRemainingQuantity() <= 0 || context.getOfferPrice() <= 0)
            return abstain(id, recommendation, offer, now, observedAt,
                PolicyAbstentionReason.OFFER_STATE_INCONSISTENT, "OPEN_BUY_REQUIRED");
        if (context.getMarketObservedAt() + MAX_INPUT_AGE_SECONDS < now
            || context.getCurrentLowPrice() <= 0 || context.getCurrentHighPrice() <= 0)
            return abstain(id, recommendation, offer, now, observedAt,
                PolicyAbstentionReason.MARKET_DATA_STALE, "BUY_MAINTENANCE_MARKET_STALE");
        if (context.getBuyLimitRemaining() <= 0)
            return action(id, recommendation, offer, now, observedAt,
                OfferLifecycleAction.CANCEL_BUY, "BUY_LIMIT_EXHAUSTED");
        return action(id, recommendation, offer, now, observedAt,
            OfferLifecycleAction.HOLD, "OPEN_BUY_WITHIN_CURRENT_GUARDS");
    }

    private static PolicyDecision action(String id, String recommendation, String offer, long now,
        long observedAt, OfferLifecycleAction action, String reason)
    {
        return PolicyDecision.action(SCHEMA_VERSION, POLICY_VERSION, id,
            PolicyDecisionType.BUY_MAINTENANCE, action, reason, now, observedAt, offer, recommendation);
    }
    private static PolicyDecision abstain(String id, String recommendation, String offer, long now,
        long observedAt, PolicyAbstentionReason reason, String code)
    {
        return PolicyDecision.abstain(SCHEMA_VERSION, POLICY_VERSION, id,
            PolicyDecisionType.BUY_MAINTENANCE, reason, code, now, observedAt, offer, recommendation);
    }
    private static String value(String value, String fallback)
    {
        return value == null || value.trim().isEmpty() ? fallback : value;
    }
    private static String decisionId(BuyMaintenancePolicyContext c, String recommendation, String offer)
    {
        String canonical = POLICY_VERSION + '|' + recommendation + '|' + offer + '|' + c.getSlot()
            + '|' + c.getItemId() + '|' + c.getRemainingQuantity() + '|' + c.getOfferPrice() + '|'
            + c.getCurrentLowPrice() + '|' + c.getCurrentHighPrice() + '|'
            + c.getBuyLimitRemaining() + '|' + c.getOfferObservedAt() + '|'
            + c.getMarketObservedAt() + '|' + c.getDecidedAt() + '|' + c.isOpenBuy();
        try
        {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(canonical.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder("buy-maintenance-");
            for (int i = 0; i < 16; i++) result.append(String.format("%02x", digest[i]));
            return result.toString();
        }
        catch (Exception ex)
        {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }
}
