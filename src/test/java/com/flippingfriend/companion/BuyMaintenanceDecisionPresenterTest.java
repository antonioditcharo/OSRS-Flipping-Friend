package com.flippingfriend.companion;

import com.flippingfriend.core.OfferEvent;
import com.flippingfriend.core.OfferLifecycleAction;
import com.flippingfriend.core.PolicyAbstentionReason;
import com.flippingfriend.core.PolicyDecision;
import com.flippingfriend.core.PolicyDecisionType;
import com.flippingfriend.model.SuggestionType;
import org.junit.Assert;
import org.junit.Test;

public class BuyMaintenanceDecisionPresenterTest
{
    @Test public void cancelPresentationPreservesOfferEconomicsAndDoesNotInventReplacement()
    {
        PresentedBuyMaintenanceDecision result = BuyMaintenanceDecisionPresenter.present(
            action(OfferLifecycleAction.CANCEL_BUY), offer());
        Assert.assertNotNull(result);
        Assert.assertEquals(SuggestionType.CANCEL, result.getSuggestion().getType());
        Assert.assertEquals(561, result.getSuggestion().getItemId());
        Assert.assertEquals(1, result.getSuggestion().getSlot());
        Assert.assertEquals(100, result.getSuggestion().getPrice());
        Assert.assertEquals(400, result.getSuggestion().getQuantity());
        Assert.assertEquals("offer-1", result.getSuggestion().getMaintenanceOfferIdentity());
        Assert.assertTrue(result.getSuggestion().getDetail().contains("does not authorize a replacement"));
    }
    @Test public void holdAndAbstentionProduceExplicitWaitPresentations()
    {
        PresentedBuyMaintenanceDecision hold = BuyMaintenanceDecisionPresenter.present(
            action(OfferLifecycleAction.HOLD), offer());
        Assert.assertEquals(SuggestionType.WAIT, hold.getSuggestion().getType());
        PolicyDecision wait = PolicyDecision.abstain("1", "policy", "wait", PolicyDecisionType.BUY_MAINTENANCE,
            PolicyAbstentionReason.MARKET_DATA_STALE, "MARKET_STALE", 20, 10, "offer-1", "plan-1");
        Assert.assertEquals(SuggestionType.WAIT,
            BuyMaintenanceDecisionPresenter.present(wait, offer()).getSuggestion().getType());
    }
    @Test public void legacyWireIdentityMapsToTheExistingLocalWorkflowIdentityWithoutRewritingItsSource() {
        String wire="1@5:561";
        var offer=OfferEvent.builder("legacy",10,"BUYING").eventIdentity("source","session",wire)
            .slot(1).item(561,"Nature rune").buying(true).price(100).quantities(500,100).firstSeenAt(5)
            .recommendation("plan-1",100,500,10,30).build();
        var decision=PolicyDecision.action("1","policy","legacy-cancel",PolicyDecisionType.BUY_MAINTENANCE,
            OfferLifecycleAction.CANCEL_BUY,"REASON",20,10,wire,"plan-1");
        var presented=BuyMaintenanceDecisionPresenter.present(decision,offer);
        Assert.assertNotNull(presented);
        Assert.assertEquals("1:561:true:100:500:5",presented.getSuggestion().getMaintenanceOfferIdentity());
        Assert.assertEquals(wire,offer.getOfferIdentity());Assert.assertEquals(wire,presented.getDecision().getCandidateId());
    }
    @Test public void identityLifecycleAndActionMismatchesFailClosed()
    {
        Assert.assertNull(BuyMaintenanceDecisionPresenter.present(action(OfferLifecycleAction.CANCEL_BUY),
            offer("other-offer", "plan-1")));
        Assert.assertNull(BuyMaintenanceDecisionPresenter.present(action(OfferLifecycleAction.REPLACE_BUY), offer()));
        Assert.assertNull(BuyMaintenanceDecisionPresenter.present(action(OfferLifecycleAction.HOLD), null));
    }
    private static PolicyDecision action(OfferLifecycleAction action)
    {
        return PolicyDecision.action("1", "policy", "decision-" + action, PolicyDecisionType.BUY_MAINTENANCE,
            action, "REASON", 20, 10, "offer-1", "plan-1");
    }
    private static OfferEvent offer() { return offer("offer-1", "plan-1"); }
    private static OfferEvent offer(String identity, String recommendation)
    {
        return OfferEvent.builder("c", 10, "BUYING").eventIdentity("event", "session", identity)
            .slot(1).item(561, "Nature rune").buying(true).price(100).quantities(500, 100)
            .recommendation(recommendation, 100, 500, 10, 30).build();
    }
}
