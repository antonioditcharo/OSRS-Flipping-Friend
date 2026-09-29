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
import java.util.Arrays;
import java.util.Collections;
import org.junit.Assert;
import org.junit.Test;

public class EntryDecisionPresenterTest
{
    @Test public void buyPresentationPreservesEconomicsAndLineage()
    {
        PortfolioCandidate candidate = candidate(4151, "Abyssal whip", 100, 120, 8);
        PolicyDecision decision = buy("plan", "4151:100:120:8");
        PresentedEntryDecision presented = EntryDecisionPresenter.present(decision,
            plan("plan", new PortfolioAllocation(1, candidate, "PLACE_BUY")), new Explainer());
        Assert.assertNotNull(presented);
        Assert.assertSame(decision, presented.getDecision());
        Suggestion suggestion = presented.getSuggestion();
        Assert.assertEquals(SuggestionType.BUY, suggestion.getType());
        Assert.assertEquals(4151, suggestion.getItemId());
        Assert.assertEquals("Abyssal whip", suggestion.getItemName());
        Assert.assertEquals(100, suggestion.getPrice());
        Assert.assertEquals(8, suggestion.getQuantity());
        Assert.assertEquals(500, suggestion.getExpectedProfit());
        Assert.assertEquals(candidate.getDisplayCompletionProbability(), suggestion.getConfidence(), 0.000001);
        Assert.assertEquals(candidate.getSlotHours() * 60, suggestion.getExpectedMinutes(), 0.000001);
        Assert.assertEquals(18, suggestion.getBuyFillMinutes(), 0.000001);
        Assert.assertEquals(24, suggestion.getSellFillMinutes(), 0.000001);
        Assert.assertEquals(120, suggestion.getTargetSellPrice());
        Assert.assertEquals("Buy 8 × Abyssal whip", suggestion.getHeadline());
        Assert.assertEquals("decision", presented.getDecision().getDecisionId());
        Assert.assertEquals("policy", presented.getDecision().getPolicyVersion());
        Assert.assertEquals(10, presented.getDecision().getDecidedAt());
        Assert.assertEquals(9, presented.getDecision().getInputObservedAt());
    }

    @Test public void abstentionsProduceExplicitWaitingPresentations()
    {
        for (PolicyAbstentionReason reason : Arrays.asList(
            PolicyAbstentionReason.NO_FREE_SLOT,
            PolicyAbstentionReason.NO_ELIGIBLE_CANDIDATE,
            PolicyAbstentionReason.MARKET_DATA_STALE,
            PolicyAbstentionReason.ACCOUNT_STATE_INCONSISTENT,
            PolicyAbstentionReason.SELL_ONLY_MODE,
            PolicyAbstentionReason.DRAWDOWN_LIMIT_REACHED,
            PolicyAbstentionReason.TRADING_SUSPENDED,
            PolicyAbstentionReason.BUY_LIMIT_EXHAUSTED))
        {
            PolicyDecision decision = PolicyDecision.abstain("1", "policy", "decision",
                PolicyDecisionType.ENTRY, reason, reason.name(), 10, 9, null, "plan");
            PresentedEntryDecision presented = EntryDecisionPresenter.present(decision, plan("plan"), null);
            Assert.assertNotNull(reason.name(), presented);
            Suggestion suggestion = presented.getSuggestion();
            Assert.assertEquals(SuggestionType.WAIT, suggestion.getType());
            Assert.assertFalse(suggestion.getHeadline().isEmpty());
            Assert.assertFalse(suggestion.getDetail().isEmpty());
            Assert.assertEquals(0, suggestion.getPrice());
            Assert.assertEquals(0, suggestion.getQuantity());
        }
    }

    @Test public void linkageAndCandidateFailuresAreClosed()
    {
        PortfolioCandidate candidate = candidate(4151, "Abyssal whip", 100, 120, 8);
        PolicyDecision decision = buy("plan", "4151:100:120:8");
        Assert.assertNull(EntryDecisionPresenter.present(null, plan("plan"), new Explainer()));
        Assert.assertNull(EntryDecisionPresenter.present(decision, null, new Explainer()));
        Assert.assertNull(EntryDecisionPresenter.present(decision, plan("other",
            new PortfolioAllocation(1, candidate, "PLACE_BUY")), new Explainer()));
        Assert.assertNull(EntryDecisionPresenter.present(buy("plan", "missing"), plan("plan",
            new PortfolioAllocation(1, candidate, "PLACE_BUY")), new Explainer()));
        Assert.assertNull(EntryDecisionPresenter.present(decision, plan("plan",
            new PortfolioAllocation(1, candidate, "PLACE_BUY"),
            new PortfolioAllocation(2, candidate, "PLACE_BUY")), new Explainer()));
        Assert.assertNull(EntryDecisionPresenter.present(decision, plan("plan",
            new PortfolioAllocation(1, null, "PLACE_BUY")), new Explainer()));
        Assert.assertNull(EntryDecisionPresenter.present(decision, plan("plan",
            new PortfolioAllocation(1, candidate(0, "bad", 100, 120, 8), "PLACE_BUY")), new Explainer()));
        Assert.assertNull(EntryDecisionPresenter.present(decision, plan("plan",
            new PortfolioAllocation(1, candidate(4151, "bad", 0, 120, 8), "PLACE_BUY")), new Explainer()));
        Assert.assertNull(EntryDecisionPresenter.present(decision, plan("plan",
            new PortfolioAllocation(1, candidate(4151, "bad", 100, 0, 8), "PLACE_BUY")), new Explainer()));
        Assert.assertNull(EntryDecisionPresenter.present(decision, plan("plan",
            new PortfolioAllocation(1, candidate(4151, "bad", 100, 120, 0), "PLACE_BUY")), new Explainer()));
        Assert.assertNull(EntryDecisionPresenter.present(decision, plan("plan",
            new PortfolioAllocation(1, candidate, "PLACE_BUY")), null));
    }

    @Test public void waitMayBePresentedWithoutAPlanButRejectsAMismatchedPlan()
    {
        PolicyDecision wait = PolicyDecision.abstain("1", "policy", "decision",
            PolicyDecisionType.ENTRY, PolicyAbstentionReason.COMPANION_UNAVAILABLE,
            "COMPANION_STATE_UNAVAILABLE", 10, 9, null, "plan");
        Assert.assertNotNull(EntryDecisionPresenter.present(wait, null, null));
        Assert.assertNull(EntryDecisionPresenter.present(wait, plan("other"), null));
    }

    private static PolicyDecision buy(String recommendationId, String candidateId)
    {
        return PolicyDecision.action("1", "policy", "decision", PolicyDecisionType.ENTRY,
            OfferLifecycleAction.PLACE_BUY, "ENTRY_CANDIDATE_SELECTED", 10, 9,
            candidateId, recommendationId);
    }

    private static PortfolioPlan plan(String id, PortfolioAllocation... allocations)
    {
        return new PortfolioPlan(id, 9, 99, "READY", "ok", 1,
            allocations.length == 0 ? Collections.emptyList() : Arrays.asList(allocations));
    }

    private static PortfolioCandidate candidate(int itemId, String name, int buy, int sell, int quantity)
    {
        return new PortfolioCandidate(itemId, name, "", buy, buy, buy, sell, sell, sell,
            quantity, 1000, 500, 10, 20, 0.9, 0.8, 0.3, 0.4, 4.0, 99);
    }
}
