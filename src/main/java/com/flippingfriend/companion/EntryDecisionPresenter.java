package com.flippingfriend.companion;

import com.flippingfriend.core.OfferLifecycleAction;
import com.flippingfriend.core.PolicyAbstentionReason;
import com.flippingfriend.core.PolicyDecision;
import com.flippingfriend.core.PolicyDecisionType;
import com.flippingfriend.core.PortfolioAllocation;
import com.flippingfriend.core.PortfolioCandidate;
import com.flippingfriend.core.PortfolioPlan;
import com.flippingfriend.model.Explainer;
import com.flippingfriend.model.Suggestion;
import com.flippingfriend.model.SuggestionType;

/** Pure conversion from companion entry decisions to existing manual presentation objects. */
final class EntryDecisionPresenter
{
    private EntryDecisionPresenter() { }

    static PresentedEntryDecision present(PolicyDecision decision, PortfolioPlan plan,
        Explainer explainer)
    {
        if (!validEntryDecision(decision)) return null;
        if (plan != null && !same(decision.getRecommendationId(), plan.getCorrelationId())) return null;
        if (decision.getAction() == OfferLifecycleAction.WAIT)
        {
            return new PresentedEntryDecision(decision, waiting(decision.getAbstentionReason()));
        }
        if (plan == null || explainer == null || blank(decision.getCandidateId())) return null;
        PortfolioCandidate matched = null;
        for (PortfolioAllocation allocation : plan.getAllocations())
        {
            if (allocation == null || allocation.getCandidate() == null) continue;
            PortfolioCandidate candidate = allocation.getCandidate();
            if (!validCandidate(candidate) || !decision.getCandidateId().equals(candidateId(candidate))) continue;
            if (matched != null) return null;
            matched = candidate;
        }
        if (matched == null) return null;
        Suggestion suggestion = Suggestion.builder(SuggestionType.BUY)
            .item(matched.getItemId(), matched.getItemName())
            .price(matched.getBuyPrice())
            .quantity(matched.getQuantity())
            .expectedProfit(matched.getNetProfit())
            .confidence(matched.getDisplayCompletionProbability())
            .expectedMinutes(matched.getSlotHours() * 60)
            .fillMinutes(matched.getBuyHours() * 60, matched.getSellHours() * 60)
            .targetSellPrice(matched.getSellPrice())
            .headline("Buy " + explainer.formatNumber(matched.getQuantity()) + " × "
                + matched.getItemName())
            .build();
        return new PresentedEntryDecision(decision, suggestion);
    }

    private static boolean validEntryDecision(PolicyDecision decision)
    {
        if (decision == null || decision.getDecisionType() != PolicyDecisionType.ENTRY
            || blank(decision.getRecommendationId()) || decision.getAction() == null
            || decision.getAbstentionReason() == null) return false;
        if (decision.getAction() == OfferLifecycleAction.WAIT)
            return decision.getAbstentionReason() != PolicyAbstentionReason.NONE;
        return decision.getAction() == OfferLifecycleAction.PLACE_BUY
            && decision.getAbstentionReason() == PolicyAbstentionReason.NONE;
    }

    private static boolean validCandidate(PortfolioCandidate candidate)
    {
        return candidate.getItemId() > 0 && candidate.getBuyPrice() > 0
            && candidate.getSellPrice() > 0 && candidate.getQuantity() > 0;
    }

    private static String candidateId(PortfolioCandidate candidate)
    {
        return candidate.getItemId() + ":" + candidate.getBuyPrice() + ":"
            + candidate.getSellPrice() + ":" + candidate.getQuantity();
    }

    private static Suggestion waiting(PolicyAbstentionReason reason)
    {
        switch (reason)
        {
            case MARKET_DATA_UNAVAILABLE:
            case MARKET_DATA_STALE:
                return Suggestion.waiting("Market data is not ready",
                    "No new buy will be suggested until the companion has current market data.");
            case ACCOUNT_STATE_UNAVAILABLE:
            case COMPANION_UNAVAILABLE:
                return Suggestion.waiting("Companion unavailable",
                    "No new buy will be suggested until the companion has a current account projection.");
            case ACCOUNT_STATE_INCONSISTENT:
            case RECONCILIATION_REQUIRED:
                return Suggestion.waiting("Account state needs reconciliation",
                    "No new buy will be suggested until the companion can reconcile the account state.");
            case NO_FREE_SLOT:
                return Suggestion.waiting("All Grand Exchange slots are busy",
                    "No new buy can be placed until a slot becomes available.");
            case NO_ELIGIBLE_CANDIDATE:
                return Suggestion.waiting("Nothing worth buying right now",
                    "The companion found no eligible entry candidate under the current settings.");
            case RISK_CONSTRAINT:
            case DRAWDOWN_LIMIT_REACHED:
                return Suggestion.waiting("New buys are paused",
                    "No new entry fits the current session risk limits.");
            case SELL_ONLY_MODE:
                return Suggestion.waiting("Sell-only mode is on",
                    "No new buy will be suggested while sell-only mode is active.");
            case TRADING_SUSPENDED:
                return Suggestion.waiting("Trading is paused",
                    "No new buy will be suggested while trading is suspended.");
            default:
                return Suggestion.waiting("No new buy right now",
                    "The companion deliberately declined to open a new position.");
        }
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
