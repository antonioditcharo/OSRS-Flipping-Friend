package com.flippingfriend.companion;

/** Pure fail-closed composer from validated readiness evidence to later pricing inputs. */
final class BuyReplacementPricingContextComposer
{
    BuyReplacementPricingContext compose(BuyReplacementEligibilityContext context,
        BuyReplacementReadinessAssessment assessment)
    {
        if (context == null || assessment == null) return null;
        if (assessment.getOutcome() != BuyReplacementReadinessOutcome.READY_FOR_PRICING)
            return null;
        if (!CompanionBuyReplacementReadinessPolicy.SCHEMA_VERSION.equals(
            assessment.getSchemaVersion())
            || !CompanionBuyReplacementReadinessPolicy.POLICY_VERSION.equals(
                assessment.getPolicyVersion()))
            return null;
        if (blank(assessment.getAssessmentId()) || blank(context.getIntentId())
            || !same(context.getIntentId(), assessment.getIntentId())
            || !same(context.getOfferIdentity(), assessment.getOfferIdentity())
            || !same(context.getRecommendationId(), assessment.getRecommendationId())
            || context.getSlot() != assessment.getSlot()
            || context.getItemId() != assessment.getItemId()
            || context.getRemainderQuantity() != assessment.getRemainderQuantity())
            return null;
        long oldestInput = Math.min(context.getAccountObservedAt(), context.getMarketObservedAt());
        if (context.getEvaluatedAt() != assessment.getEvaluatedAt()
            || oldestInput != assessment.getInputObservedAt())
            return null;
        if (context.getCreatedAt() < 0 || context.getExpiresAt() <= context.getCreatedAt()
            || context.getExpiresAt() < context.getEvaluatedAt()
            || context.getAccountObservedAt() < 0 || context.getMarketObservedAt() < 0
            || context.getAccountObservedAt() > context.getEvaluatedAt()
            || context.getMarketObservedAt() > context.getEvaluatedAt()
            || context.getAccountObservedAt()
                + CompanionBuyReplacementReadinessPolicy.MAX_INPUT_AGE_SECONDS
                < context.getEvaluatedAt()
            || context.getMarketObservedAt()
                + CompanionBuyReplacementReadinessPolicy.MAX_INPUT_AGE_SECONDS
                < context.getEvaluatedAt())
            return null;
        if (blank(context.getItemName()) || context.getOriginalPrice() <= 0
            || context.getLowPrice() <= 0 || context.getHighPrice() <= 0
            || context.getRemainderQuantity() <= 0
            || context.getBuyLimitRemaining() < context.getRemainderQuantity()
            || context.getSpendableCoins() <= 0 || context.getFreeSlots() <= 0)
            return null;
        return new BuyReplacementPricingContext(assessment.getAssessmentId(),
            assessment.getPolicyVersion(), context.getIntentId(), context.getOfferIdentity(),
            context.getRecommendationId(), context.getSlot(), context.getItemId(),
            context.getItemName(), context.getRemainderQuantity(), context.getOriginalPrice(),
            context.getLowPrice(), context.getHighPrice(), context.getBuyLimitRemaining(),
            context.getSpendableCoins(), context.isMembers(), context.getCreatedAt(),
            context.getExpiresAt(), context.getAccountObservedAt(), context.getMarketObservedAt(),
            assessment.getInputObservedAt(), context.getEvaluatedAt());
    }

    private static boolean same(String left, String right)
    {
        return left != null && left.equals(right);
    }

    private static boolean blank(String value)
    {
        return value == null || value.trim().isEmpty();
    }
}
