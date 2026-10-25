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
        return present(decision,plan,explainer,null);
    }
    static PresentedEntryDecision present(PolicyDecision decision, PortfolioPlan plan,
        Explainer explainer, String account)
    {
        if (!validEntryDecision(decision)) return null;
        if (plan != null && !same(decision.getRecommendationId(), plan.getCorrelationId())) return null;
        if (decision.getAction() == OfferLifecycleAction.WAIT)
        {
            return new PresentedEntryDecision(decision, waiting(decision.getAbstentionReason(),plan,account));
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
        return new PresentedEntryDecision(decision,buySuggestion(matched,plan,explainer,account));
    }

    /** One conversion for the active policy path and the legacy proposal adapter. No evidence write. */
    static Suggestion buySuggestion(PortfolioCandidate matched,PortfolioPlan plan,Explainer explainer,String account) {
        var forecast=matched.getBuyForecast();
        boolean forecastConsistent=forecast==null || (forecast.matches(matched.getItemId(),true,matched.getBuyPrice(),matched.getQuantity())
            && Math.abs(forecast.getHorizonSeconds()-matched.getHorizonHours()*3600)<=1e-9*Math.max(1,matched.getHorizonHours()*3600));
        return Suggestion.builder(SuggestionType.BUY)
            .item(matched.getItemId(), matched.getItemName())
            .price(matched.getBuyPrice())
            .quantity(matched.getQuantity())
            .expectedProfit(matched.getNetProfit())
            .confidence(matched.getDisplayCompletionProbability())
            .expectedMinutes(matched.getSlotHours() * 60)
            .fillMinutes(matched.getBuyHours() * 60, matched.getSellHours() * 60)
            .adviceContext(account,plan.getCorrelationId(),Math.max(0,java.time.Instant.now().getEpochSecond()-plan.getCreatedAt()),plan.getExpiresAt())
            .legCompletionProbability(forecastConsistent ? matched.getDisplayBuyFillProbability() : null)
            .executionForecast(forecast)
            .targetSellPrice(matched.getSellPrice())
            .headline("Buy " + explainer.formatNumber(matched.getQuantity()) + " × "
                + matched.getItemName())
            .build();
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

    private static Suggestion waiting(PolicyAbstentionReason reason,PortfolioPlan plan,String account)
    {
        switch (reason)
        {
            case MARKET_DATA_UNAVAILABLE:
            case MARKET_DATA_STALE:
                return Suggestion.waiting("Market data is not ready",
                    "No new buy will be suggested until the companion has current market data.");
            case ACCOUNT_STATE_UNAVAILABLE:
                var unavailableRisk=plan==null?null:plan.getCanonicalRisk();
                long checkedAt=java.time.Instant.now().getEpochSecond();
                if(unavailableRisk!=null && unavailableRisk.isValid() && !unavailableRisk.isAvailable()
                    && account!=null && account.equals(unavailableRisk.getAccountId())
                    && plan.getCreatedAt()<=checkedAt && plan.getExpiresAt()>checkedAt
                    && unavailableRisk.getEvaluatedAt()<=checkedAt && checkedAt-unavailableRisk.getEvaluatedAt()<=150)
                    return Suggestion.waiting("Verifying current account",unavailableRisk.getReason());
            case COMPANION_UNAVAILABLE:
                return Suggestion.waiting("Companion unavailable",
                    "No new buy will be suggested until the companion has a current account projection.");
            case ACCOUNT_STATE_INCONSISTENT:
            case RECONCILIATION_REQUIRED:
                return Suggestion.waiting("Account state needs reconciliation",
                    "No new buy will be suggested until the companion can reconcile the account state.");
            case NO_FREE_SLOT:
                return Suggestion.waiting("Keep your current offers in place",
                    "Collect completed offers as they finish; the next trade is prepared in the background.");
            case NO_ELIGIBLE_CANDIDATE:
                var diagnostics=plan==null?null:plan.getDiagnostics();
                if(diagnostics!=null && diagnostics.getTacticsGenerated()==0
                    && diagnostics.getVetoCounts().getOrDefault("Waiting for price history.",0)>0)
                    return Suggestion.waiting("Loading item history",
                        "The companion is loading market history for candidate items. It will evaluate buys as that history arrives.");
                return Suggestion.waiting("Nothing worth buying right now",
                    "The companion found no eligible entry candidate under the current settings.");
            case DRAWDOWN_LIMIT_REACHED:
                var risk=plan==null?null:plan.getCanonicalRisk();
                long now=java.time.Instant.now().getEpochSecond();
                if(risk!=null && risk.isAvailable() && account!=null && account.equals(risk.getAccountId())
                    && plan.getCreatedAt()<=now && plan.getExpiresAt()>now
                    && risk.getEvaluatedAt()<=now && now-risk.getEvaluatedAt()<=150
                    && risk.getValidUntil()>=now && risk.getRemainingLossBudget()<=0) {
                    String headline="New buys are paused";
                    if(risk.isCurrentAccountPolicy()) {
                        if(risk.isVerifiedCashPolicy() && risk.getUncertainLossReserve()>0
                            && risk.getRemainingLossBudget()>-risk.getUncertainLossReserve())
                            headline="Waiting for current holdings evidence";
                        else if(!risk.isVerifiedCashPolicy() && risk.getUnpricedManagedCostReserve()>0
                            && risk.getRemainingLossBudget()>-risk.getUnpricedManagedCostReserve())
                            headline="Waiting for stock valuation";
                    }else if(risk.getRiskCapital()>0 && risk.getUncertainLossReserve()>0)
                        headline="Trade evidence affects account risk";
                    return Suggestion.waiting(headline,risk.getReason());
                }
                // A missing/foreign/expired diagnostic cannot explain this account's refusal.
                // The policy still abstains; never turn missing explanatory data into a buy.
            case RISK_CONSTRAINT:
                return Suggestion.waiting("New buys are paused",
                    "No new entry fits the account's durable risk limits.");
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
