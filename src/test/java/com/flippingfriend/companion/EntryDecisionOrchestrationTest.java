package com.flippingfriend.companion;

import com.flippingfriend.core.OfferLifecycleAction;
import com.flippingfriend.core.PolicyAbstentionReason;
import com.flippingfriend.core.PolicyDecision;
import com.flippingfriend.core.PolicyDecisionType;
import com.flippingfriend.core.PortfolioAllocation;
import com.flippingfriend.core.PortfolioCandidate;
import com.flippingfriend.core.PortfolioPlan;
import com.flippingfriend.core.PortfolioPlanOutcome;
import com.flippingfriend.data.PluginStorage;
import com.flippingfriend.model.Explainer;
import com.flippingfriend.model.SuggestionType;
import com.google.gson.Gson;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import static org.mockito.Mockito.mock;

public class EntryDecisionOrchestrationTest
{
    private CompanionClient client;

    @Before public void setUp()
    {
        client = new CompanionClient(mock(PluginStorage.class), new Gson(), new SuggestionLedger());
    }

    @Test public void readyPlanSelectsRankRetrievesDecisionAndPresentsIt()
    {
        client.acceptPlan(plan(allocation(1, 554, "Fire rune"), allocation(2, 561, "Nature rune")));
        AtomicReference<String> planId = new AtomicReference<>();
        AtomicInteger rank = new AtomicInteger();
        PresentedEntryDecision result = client.nextEntryDecisionPresentation(new Explainer(),
            Collections.emptySet(), Collections.emptySet(), Collections.emptySet(), (id, selectedRank) ->
            {
                planId.set(id); rank.set(selectedRank);
                return buy(id, "554:100:110:50");
            });
        Assert.assertNotNull(result);
        Assert.assertEquals("plan", planId.get());
        Assert.assertEquals(1, rank.get());
        Assert.assertEquals(SuggestionType.BUY, result.getSuggestion().getType());
        Assert.assertEquals(554, result.getSuggestion().getItemId());
        Assert.assertEquals("decision", result.getDecision().getDecisionId());
    }

    @Test public void eligibilityAndIncumbentSelectionDetermineTheRequestedRank()
    {
        client.acceptPlan(plan(allocation(1, 554, "Fire rune"), allocation(2, 561, "Nature rune")));
        AtomicInteger rank = new AtomicInteger();
        client.nextEntryDecisionPresentation(new Explainer(),
            new HashSet<>(Collections.singletonList("fire rune")), Collections.emptySet(),
            Collections.emptySet(), (id, selectedRank) ->
            {
                rank.set(selectedRank); return buy(id, "561:100:110:50");
            });
        Assert.assertEquals(2, rank.get());
        client.acceptPlan(plan(allocation(1, 554, "Fire rune"), allocation(2, 561, "Nature rune")));
        client.nextEntryDecisionPresentation(new Explainer(), Collections.emptySet(),
            Collections.emptySet(), Collections.emptySet(), (id, selectedRank) ->
            {
                rank.set(selectedRank); return buy(id, "561:100:110:50");
            });
        Assert.assertEquals("the incumbent remains selected after the filter is removed", 2, rank.get());
    }

    @Test public void currentStructuredRefusalIsRetrievedAndPresented()
    {
        PortfolioPlan refusal = PortfolioPlan.unavailable("plan", PortfolioPlanOutcome.NO_FREE_SLOT,
            "display prose", System.currentTimeMillis() / 1000);
        client.acceptPlan(refusal);
        AtomicInteger rank = new AtomicInteger();
        PresentedEntryDecision result = client.nextEntryDecisionPresentation(new Explainer(),
            Collections.emptySet(), Collections.emptySet(), Collections.emptySet(), (id, selectedRank) ->
            {
                rank.set(selectedRank);
                return PolicyDecision.abstain("1", "policy", "decision", PolicyDecisionType.ENTRY,
                    PolicyAbstentionReason.NO_FREE_SLOT, "NO_FREE_SLOT", 10, 9, null, id);
            });
        Assert.assertNotNull(result);
        Assert.assertEquals(1, rank.get());
        Assert.assertEquals(SuggestionType.WAIT, result.getSuggestion().getType());
    }

    @Test public void silenceRetrievalFailureAndLinkageFailureRemainClosed()
    {
        AtomicInteger calls = new AtomicInteger();
        CompanionClient.EntryDecisionFetcher fetcher = (id, rank) -> { calls.incrementAndGet(); return null; };
        Assert.assertNull(client.nextEntryDecisionPresentation(new Explainer(), Collections.emptySet(),
            Collections.emptySet(), Collections.emptySet(), fetcher));
        Assert.assertEquals(0, calls.get());
        client.acceptPlan(new PortfolioPlan("expired", 0, 1, "READY", "old", 0,
            Collections.singletonList(allocation(1, 554, "Fire rune"))));
        Assert.assertNull(client.nextEntryDecisionPresentation(new Explainer(), Collections.emptySet(),
            Collections.emptySet(), Collections.emptySet(), fetcher));
        Assert.assertEquals(0, calls.get());
        client.acceptPlan(plan(allocation(1, 554, "Fire rune")));
        Assert.assertNull(client.nextEntryDecisionPresentation(new Explainer(), Collections.emptySet(),
            Collections.emptySet(), Collections.emptySet(), fetcher));
        Assert.assertEquals(1, calls.get());
        PresentedEntryDecision mismatch = client.nextEntryDecisionPresentation(new Explainer(),
            Collections.emptySet(), Collections.emptySet(), Collections.emptySet(),
            (id, rank) -> buy("other", "554:100:110:50"));
        Assert.assertNull(mismatch);
        Assert.assertEquals("Companion entry decision did not match the current plan.", client.lastError());
    }

    private static PolicyDecision buy(String recommendationId, String candidateId)
    {
        return PolicyDecision.action("1", "policy", "decision", PolicyDecisionType.ENTRY,
            OfferLifecycleAction.PLACE_BUY, "ENTRY_CANDIDATE_SELECTED", 10, 9,
            candidateId, recommendationId);
    }

    private static PortfolioPlan plan(PortfolioAllocation... allocations)
    {
        return new PortfolioPlan("plan", 0, Long.MAX_VALUE, "READY", "ok", 1,
            Arrays.asList(allocations));
    }

    private static PortfolioAllocation allocation(int rank, int itemId, String name)
    {
        PortfolioCandidate candidate = new PortfolioCandidate(itemId, name, "", 100, 100, 100,
            110, 110, 110, 50, 1000, 500, 10, 20, 0.9, 0.9, 0.3, 0.4, 4.0,
            Long.MAX_VALUE);
        return new PortfolioAllocation(rank, candidate, "PLACE_BUY");
    }
}
