package com.flippingfriend.companion;

import com.flippingfriend.core.*;
import com.google.gson.Gson;
import java.nio.file.Path;
import java.time.Instant;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

/** Authoritative source-event validation with disposable real acceptance/statistics. */
public class ExecutionPredictionAuthorityTest {
    @Rule public TemporaryFolder folder=new TemporaryFolder();
    final Gson gson=new Gson();final String account=AccountIdentity.fromProfileKey("prediction-authority");
    final long at=Instant.now().getEpochSecond();
    OfferEvent event(String id,long sequence,String type,int quantity,int filled,String plan,double predicted,
            String origin,long advisedAt,boolean comparable) {
        var b=OfferEvent.builder("fixture",at+(type.equals("BOUGHT")?10:0),type).accountId(account)
            .authenticatedObservation(true).eventIdentity(id,"session","offer").slot(0).item(561,"Nature rune")
            .buying(true).price(100).quantities(quantity,filled).spent(100L*filled).sequence(sequence).firstSeenAt(at)
            .predictionEvidence(origin,advisedAt,comparable);
        if(plan!=null)b.recommendation(plan,100,10,0,predicted,.75);
        return b.build();
    }
    void accept(SqliteStore s,OfferEvent e)throws Exception{s.recordAndProjectOffer(e,gson.toJson(e),true);}
    @Test public void serverUsesTheOriginalSourcePredictionAfterRestartRatherThanNewTerminalAdvice()throws Exception {
        Path db=folder.newFolder().toPath().resolve("restart.db");
        try(var s=new SqliteStore(db)){accept(s,event("birth",1,"BUYING",10,0,"original",12,"birth",at-1,true));}
        try(var s=new SqliteStore(db)) {
            var terminal=event("terminal",2,"BOUGHT",10,10,"replacement",120,"birth",at-1,true);accept(s,terminal);
            assertEquals(12,s.executionStats().get(561).predictedMinutes,0);
            assertEquals("Raw source metadata remains intact",gson.toJson(terminal),s.recentOfferEvents(at).get(1));
        }
    }
    @Test public void anInitiallyUnattributedOfferCannotBeScoredAgainstLaterAdvice()throws Exception {
        try(var s=new SqliteStore(folder.newFolder().toPath().resolve("unprompted.db"))) {
            accept(s,event("birth",1,"BUYING",10,0,null,0,null,0,false));
            accept(s,event("terminal",2,"BOUGHT",10,10,"late",12,"birth",at,true));
            assertEquals(0,s.executionStats().get(561).openObserved);assertEquals(0,s.executionStats().get(561).predictedMinutes,0);
            assertEquals(10,s.planningProjections(account).tradeScope.positions.get(561).getQuantity());
        }
    }
    @Test public void manualDifferentQuantityIsNotAnExactPredictionEvenIfWireClaimsComparability()throws Exception {
        try(var s=new SqliteStore(folder.newFolder().toPath().resolve("quantity.db"))) {
            accept(s,event("birth",1,"BUYING",7,0,"ten-units",12,"birth",at-1,true));
            accept(s,event("terminal",2,"BOUGHT",7,7,"ten-units",12,"birth",at-1,true));
            assertEquals(0,s.executionStats().get(561).openObserved);
            assertEquals(7,s.planningProjections(account).tradeScope.positions.get(561).getQuantity());
        }
    }
    @Test public void aSameSecondHeartbeatCannotDeclareItselfTheOriginalSource()throws Exception {
        try(var s=new SqliteStore(folder.newFolder().toPath().resolve("later-source.db"))) {
            accept(s,event("birth",1,"BUYING",10,0,null,0,null,0,false));
            accept(s,event("later",2,"BUYING",10,0,"late",12,"later",at,true));
            accept(s,event("terminal",3,"BOUGHT",10,10,"late",12,"later",at,true));
            assertEquals(0,s.executionStats().get(561).openObserved);
        }
    }
    @Test public void futureAdviceIsNotAQualifiedSource()throws Exception {
        try(var s=new SqliteStore(folder.newFolder().toPath().resolve("future.db"))) {
            accept(s,event("birth",1,"BUYING",10,0,"future",12,"birth",at+1,true));
            accept(s,event("terminal",2,"BOUGHT",10,10,"future",12,"birth",at+1,true));
            assertEquals(0,s.executionStats().get(561).openObserved);
        }
    }
    @Test public void missingProvenanceCannotInventAnOriginalPrediction()throws Exception {
        try(var s=new SqliteStore(folder.newFolder().toPath().resolve("legacy-wire.db"))) {
            accept(s,event("birth",1,"BUYING",10,0,"original",12,"birth",at-1,true));
            var json=gson.toJsonTree(event("terminal",2,"BOUGHT",10,10,"late",120,"birth",at,true)).getAsJsonObject();
            for(String name:new String[]{"predictionEvidenceVersion","predictionSourceEventId","adviceObservedAt","predictionComparable"})json.remove(name);
            accept(s,gson.fromJson(json,OfferEvent.class));
            assertEquals(0,s.executionStats().get(561).openObserved);
            assertEquals(10,s.planningProjections(account).tradeScope.positions.get(561).getQuantity());
        }
    }
}
