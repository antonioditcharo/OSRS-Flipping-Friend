package com.flippingfriend.companion;

import com.flippingfriend.core.*;
import com.flippingfriend.data.*;
import com.google.gson.Gson;
import java.time.Instant;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

/** Companion risk display uses the durable profile session, never a local journal fallback. */
public class DurableRiskClientTest {
    @Rule public TemporaryFolder folder=new TemporaryFolder();
    final Gson gson=new Gson();
    @Test public void riskRequiresFreshMatchingAccountSessionAndAcknowledgedOfferEvidence()throws Exception {
        var storage=TestStorage.rootedAt(folder.newFolder().toPath(),"risk-client");
        var outbox=new OfferEventOutbox(storage);var client=new CompanionClient(storage,gson,new SuggestionLedger(),outbox);
        long now=Instant.now().getEpochSecond();
        var e=outbox.enqueue((id,session,seq)->OfferEvent.builder("fixture",now,"EMPTY")
            .eventIdentity(id,session,null).sequence(seq).slot(0).build());
        var risk=AccountRiskSnapshot.builder(storage.accountId(),"window",e.getSessionId(),now-10,0,now,now)
            .capital(100000000,99000000).realized(-1000000,-1000000,1).build();
        var plan=new PortfolioPlan("fixture",now,now+90,"READY","fixture",0,List.of()).withCanonicalRisk(risk);
        client.acceptPlan(plan);assertNull(client.currentRisk());
        assertTrue(outbox.acknowledge(e.getEventId(),e.getSessionId(),e.getSequence()));
        assertSame(risk,client.currentRisk());
        var restarted=new CompanionClient(storage,gson,new SuggestionLedger(),new OfferEventOutbox(storage));
        restarted.acceptPlan(plan);assertEquals(risk.getDrawdownUpper(),restarted.currentRisk().getDrawdownUpper());
        var foreign=AccountRiskSnapshot.builder(AccountIdentity.fromProfileKey("other"),"window",e.getSessionId(),now-10,0,now,now)
            .capital(100000000,100000000).build();
        assertFalse(client.acceptFetchedPlan(plan.withCanonicalRisk(foreign),storage.accountId(),0));assertNull(client.currentRisk());
        var wrongSession=AccountRiskSnapshot.builder(storage.accountId(),"window","other-session",now-10,0,now,now)
            .capital(100000000,100000000).build();
        client.acceptPlan(plan.withCanonicalRisk(wrongSession));assertNull(client.currentRisk());
        var expired=AccountRiskSnapshot.builder(storage.accountId(),"window",e.getSessionId(),now-20,0,now-10,now-10)
            .validUntil(now-1).capital(100000000,100000000).build();
        client.acceptPlan(plan.withCanonicalRisk(expired));assertNull(client.currentRisk());
    }
    @Test public void malformedWireRiskCannotRestoreAnAcceptedPlan()throws Exception {
        var storage=TestStorage.rootedAt(folder.newFolder().toPath(),"wire-risk");
        var client=new CompanionClient(storage,gson,new SuggestionLedger());long now=Instant.now().getEpochSecond();
        var risk=AccountRiskSnapshot.builder(storage.accountId(),"window","s",now-1,0,now,now).capital(100,100).build();
        var body=gson.toJsonTree(new PortfolioPlan("fixture",now,now+90,"READY","fixture",0,List.of()).withCanonicalRisk(risk)).getAsJsonObject();
        body.getAsJsonObject("canonicalRisk").remove("marketObservedAt");
        assertFalse(client.acceptFetchedPlan(gson.fromJson(body,PortfolioPlan.class),storage.accountId(),0));
        assertNull(client.lastPlan());
    }
}
