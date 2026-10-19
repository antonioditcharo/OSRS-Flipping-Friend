package com.flippingfriend.companion;

import com.flippingfriend.core.*;
import com.flippingfriend.model.*;
import com.google.gson.Gson;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public class ReplacementEntryPresentationTest {
    final String account=AccountIdentity.fromProfileKey("bounded-entry");
    final long now=java.time.Instant.now().getEpochSecond();
    final Gson gson=new Gson();
    ReplacementEntry reply(int quantity,int cap) {
        var c=new PortfolioCandidate(561,"Nature rune","",100,100,100,120,120,120,quantity,1000,18L*quantity,
            1,1,.95,.95,.4,.4,1.5,now+90);
        var plan=new PortfolioPlan("fresh-bounded-plan",now,now+90,"READY","fixture",0,List.of(new PortfolioAllocation(1,c,"BUY")));
        var decision=PolicyDecision.action("1","companion-entry-1","decision",PolicyDecisionType.ENTRY,
            OfferLifecycleAction.PLACE_BUY,"ENTRY_CANDIDATE_SELECTED",now,now,561+":100:120:"+quantity,plan.getCorrelationId());
        return new ReplacementEntry(account,561,cap,now-5,plan,decision);
    }
    Suggestion present(ReplacementEntry reply) {
        return CompanionClient.presentReplacement(reply,561,6,account,now,now,new Explainer());
    }
    @Test public void theExactBoundedOrderAndItsNewPredictionArePresentedWithoutClamping() {
        var suggestion=present(reply(6,6));assertNotNull(suggestion);assertEquals(SuggestionType.BUY,suggestion.getType());
        assertEquals(6,suggestion.getQuantity());assertEquals(108,suggestion.getExpectedProfit());
        assertEquals(24,suggestion.getBuyFillMinutes(),1e-9);assertEquals(account,suggestion.getAdviceAccountId());
        assertNull("A larger authorized order is never silently trimmed by the client",present(reply(10,6)));
    }
    @Test public void versionsAccountRequestIdentityClocksAndPlanDecisionMismatchesFailClosed() {
        var original=gson.toJsonTree(reply(6,6)).getAsJsonObject();
        for(String field:List.of("version","accountId","itemId","quantityCap","accountObservedAt","plan","decision")) {
            var bad=original.deepCopy();
            if(field.equals("version"))bad.addProperty(field,"old");
            else if(field.equals("accountId"))bad.addProperty(field,AccountIdentity.fromProfileKey("other"));
            else if(field.equals("itemId")||field.equals("quantityCap"))bad.addProperty(field,999);
            else if(field.equals("accountObservedAt"))bad.addProperty(field,now-151);
            else bad.add(field,com.google.gson.JsonNull.INSTANCE);
            assertNull(field,present(gson.fromJson(bad,ReplacementEntry.class)));
        }
        for(String field:List.of("decidedAt","inputObservedAt","recommendationId","candidateId","policyVersion")) {
            var bad=original.deepCopy();var decision=bad.getAsJsonObject("decision");
            if(field.endsWith("At"))decision.addProperty(field,now+1);else decision.addProperty(field,"other");
            assertNull(field,present(gson.fromJson(bad,ReplacementEntry.class)));
        }
        var expired=original.deepCopy();expired.getAsJsonObject("plan").addProperty("expiresAt",now);
        assertNull(present(gson.fromJson(expired,ReplacementEntry.class)));
        assertNull(CompanionClient.presentReplacement(reply(6,6),561,6,account,now,now+6,new Explainer()));
    }
}
