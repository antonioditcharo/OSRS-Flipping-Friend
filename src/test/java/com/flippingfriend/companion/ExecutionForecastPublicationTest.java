package com.flippingfriend.companion;

import com.flippingfriend.core.*;
import com.flippingfriend.data.TestStorage;
import com.flippingfriend.model.*;
import com.google.gson.Gson;
import java.time.Instant;
import java.util.*;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

/** Actual advice producer and ledger, with fixture storage and no HTTP/game input. */
public class ExecutionForecastPublicationTest {
    @Rule public TemporaryFolder folder=new TemporaryFolder();
    SuggestionLedger ledger=new SuggestionLedger();
    CompanionClient client()throws Exception {
        return new CompanionClient(TestStorage.rootedAt(folder.newFolder().toPath(),"forecast-publication"),new Gson(),ledger);
    }
    Suggestion propose(CompanionClient client) {
        long now=Instant.now().getEpochSecond();
        var candidate=new PortfolioCandidate(561,"Nature rune","",100,100,100,110,110,110,10,1000,
            50,10,10,.8,.5,.2,.3,1,now+90).withDisplayProbability(.7);
        client.acceptPlan(new PortfolioPlan("plan",now,now+90,"READY","ok",100,List.of(new PortfolioAllocation(1,candidate,"PLACE_BUY"))));
        return client.nextBuySuggestion(new Explainer(),Set.of(),Set.of(),Set.of());
    }
    @Test public void buyEvidenceUsesTheBuyLegProbabilityRatherThanRoundTripConfidence()throws Exception {
        var client=client();Suggestion suggestion=propose(client);
        assertEquals(.35,suggestion.getConfidence(),1e-12);
        client.recordPresentedAdvice(suggestion,suggestion.getAdviceAccountId());
        var advice=ledger.attribute(561,true,Instant.now().getEpochSecond());assertNotNull(advice);
        assertEquals("The displayed buy forecast was 70%, while the round trip was 35%",.7,advice.getPredictedCompletion(),1e-12);
        var context=advice.getDisplayedPricingContext();assertNotNull(context);assertTrue(context.isValid());
        assertEquals(advice.getAcceptedAdviceId(),context.getAdviceId());assertNull(context.getSourceEventId());
        assertEquals(1,context.getAccountAlternatives().size());
    }
    @Test public void aProposedBuyIsNotEvidenceOfDisplayedAdvice()throws Exception {
        propose(client());
        assertNull("The display may reject, defer or lose this proposal",ledger.attribute(561,true,Instant.now().getEpochSecond()));
    }
    @Test public void anUnmodeledSaleDoesNotBorrowAWholeFlipDuration()throws Exception {
        var client=client();
        client.recordSellAdvice(Suggestion.builder(SuggestionType.SELL).item(561,"Nature rune").quantity(10).price(100)
            .expectedMinutes(50).build());
        var advice=ledger.attribute(561,false,Instant.now().getEpochSecond());assertNotNull(advice);
        assertEquals("A missing sell forecast stays unknown",0,advice.getPredictedMinutes(),0);
    }
    @Test public void acceptedContextFreezesActualSettingsAndUnknownProfitStaysUnknown()throws Exception {
        var client=client();Suggestion suggestion=propose(client);
        var config=new DisplayedPricingContext.Configuration("MODERATE",30,120,10000,100000000,false);
        suggestion.bindDisplayConfiguration(config);client.recordPresentedAdvice(suggestion,suggestion.getAdviceAccountId());
        var first=ledger.attribute(561,true,Instant.now().getEpochSecond()).getDisplayedPricingContext();
        assertNotNull(first);assertTrue(first.getConfigurationVersion().contains(":30:120:10000:"));
        suggestion.bindDisplayConfiguration(new DisplayedPricingContext.Configuration("HIGH",480,480,1,0,false));
        assertSame(config,suggestion.getDisplayConfiguration());
        var personal=Suggestion.builder(SuggestionType.SELL).item(561,"Nature rune").quantity(10).price(100).profitKnown(false).build();
        client.recordSellAdvice(personal);var sale=ledger.attribute(561,false,Instant.now().getEpochSecond()).getDisplayedPricingContext();
        assertNotNull(sale);assertNull(sale.getExpectedProfit());assertNull(sale.getForecastFingerprint());
    }
    @Test public void aLateOrForeignProposalCannotEnterThePresentationLedger()throws Exception {
        var client=client();Suggestion proposed=propose(client);
        client.recordPresentedAdvice(proposed,AccountIdentity.fromProfileKey("foreign"));
        assertNull(ledger.attribute(561,true,Instant.now().getEpochSecond()));
        var expired=Suggestion.builder(SuggestionType.BUY).item(561,"Nature rune").price(100).quantity(10)
            .adviceContext(proposed.getAdviceAccountId(),"expired",0,Instant.now().getEpochSecond()-1)
            .fillMinutes(12,30).legCompletionProbability(.7).build();
        client.recordPresentedAdvice(expired,proposed.getAdviceAccountId());
        assertNull(ledger.attribute(561,true,Instant.now().getEpochSecond()));
    }
    @Test public void activeEntryPolicyPresentationCarriesTheLegEvidenceToAcceptance()throws Exception {
        var client=client();propose(client);
        var presented=client.nextEntryDecisionPresentation(new Explainer(),Set.of(),Set.of(),Set.of(),
            (plan,rank)->PolicyDecision.action("1","fixture","decision",PolicyDecisionType.ENTRY,
                OfferLifecycleAction.PLACE_BUY,"ENTRY_CANDIDATE_SELECTED",Instant.now().getEpochSecond(),
                Instant.now().getEpochSecond()-1,"561:100:110:10",plan));
        assertNotNull(presented);assertNull(ledger.attribute(561,true,Instant.now().getEpochSecond()));
        var suggestion=presented.getSuggestion();assertEquals(Double.valueOf(.7),suggestion.getLegCompletionProbability());
        assertNotNull(suggestion.getAdviceAccountId());
        client.recordPresentedAdvice(suggestion,suggestion.getAdviceAccountId());
        assertEquals(.7,ledger.attribute(561,true,Instant.now().getEpochSecond()).getPredictedCompletion(),1e-12);
    }
}
