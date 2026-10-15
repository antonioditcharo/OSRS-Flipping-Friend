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
        var advice=ledger.attribute(561,true,Instant.now().getEpochSecond());assertNotNull(advice);
        assertEquals("The displayed buy forecast was 70%, while the round trip was 35%",.7,advice.getPredictedCompletion(),1e-12);
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
}
