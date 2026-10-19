package com.flippingfriend.companion;

import com.flippingfriend.core.*;
import com.google.gson.Gson;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.time.Instant;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import org.sqlite.ProgressHandler;
import static org.junit.Assert.*;

/** Real acceptance/accounting in disposable stores. No wall-clock assertion or live source replay. */
public class ExecutionAcceptanceLatencyTest {
    @Rule public TemporaryFolder folder=new TemporaryFolder();
    final Gson gson=new Gson();
    final String account=AccountIdentity.fromProfileKey("completion-latency");
    final long at=Instant.now().getEpochSecond()-120;
    Connection connection(SqliteStore store)throws Exception {
        var field=SqliteStore.class.getDeclaredField("connection");field.setAccessible(true);return (Connection)field.get(store);
    }
    OfferEvent event(String id,String type,int filled,long sequence,OfferEvent origin) {
        var raw=OfferEvent.builder("fixture",at+(filled==0?0:filled==3?30:60),type).accountId(account)
            .authenticatedObservation(true).eventIdentity(id,"session","buy").sequence(sequence).slot(0)
            .item(561,"Nature rune").buying(true).price(100).quantities(10,filled).spent(100L*filled).firstSeenAt(at)
            .recommendation("displayed",100,10,0,12,.75).predictionEvidence(null,at-1,false)
            .exitContext(new AcquisitionExitContext(140,90,480)).build();
        return raw.withBoundPrediction(origin==null?raw:origin,true);
    }
    void accept(SqliteStore store,OfferEvent e)throws Exception{store.recordAndProjectOffer(e,gson.toJson(e),true);}
    void history(SqliteStore store)throws Exception {
        var db=connection(store);db.setAutoCommit(false);
        try(var sql=db.prepareStatement("INSERT INTO event_log(observed_at,correlation_id,event_id,event_type,payload) VALUES(?,?,?,?,?)")) {
            for(int i=1;i<=30_000;i++) {
                var e=OfferEvent.builder("fixture",at-60,"EMPTY").accountId(account)
                    .eventIdentity("old-"+i,"session",null).sequence(i).slot(i%8).authenticatedObservation(true).build();
                sql.setLong(1,e.getObservedAt());sql.setString(2,e.getCorrelationId());sql.setString(3,e.getEventId());
                sql.setString(4,e.getEventType());sql.setString(5,gson.toJson(e));sql.addBatch();
            }
            sql.executeBatch();db.commit();
        } finally {db.setAutoCommit(true);}
    }
    OfferEvent seed(SqliteStore store)throws Exception {
        history(store);var origin=event("birth","BUYING",0,30_001,null);accept(store,origin);
        accept(store,event("partial","BUYING",3,30_002,origin));
        var p=store.ledgerSnapshot(account,at+30).getTradingPositions().get(0);
        assertEquals(origin.getExitContext(),p.getExitContext());assertEquals(300,p.getTotalCost());
        return origin;
    }
    @Test public void completedAdvisedFillUsesTheAlreadyValidatedPrefixWithinAnIndexedTailBudget()throws Exception {
        try(var store=new SqliteStore(folder.newFolder().toPath().resolve("warm.db"))) {
            var origin=seed(store);
            ProgressHandler.setHandler(connection(store),1000,new ProgressHandler() {
                int callbacks;@Override protected int progress(){return ++callbacks>150?1:0;}
            });
            try {accept(store,event("full","BOUGHT",10,30_003,origin));}
            finally {ProgressHandler.clearHandler(connection(store));}
            assertEquals(1,store.executionStats().get(561).observed);
            assertEquals(12,store.executionStats().get(561).predictedMinutes,0);
            assertEquals(2,store.positionEventCount());assertEquals(10,store.buyLimitProjections().get(0).getUsedQuantity());
            var p=store.ledgerSnapshot(account,at+60).getTradingPositions().get(0);
            assertEquals(10,p.getQuantity());assertEquals(1000,p.getTotalCost());assertEquals(origin.getExitContext(),p.getExitContext());
        }
    }
    @Test public void externalSourceDeletionOrCorruptionWithdrawsTheWarmPredictionWithoutDiscardingFills()throws Exception {
        for(String mutation:new String[]{"DELETE FROM event_log WHERE event_id='birth'",
                "UPDATE event_log SET payload='{' WHERE event_id='old-1'"}) {
            Path db=folder.newFolder().toPath().resolve("external.db");
            try(var store=new SqliteStore(db)) {
                var origin=seed(store);
                try(var other=DriverManager.getConnection("jdbc:sqlite:"+db);var sql=other.createStatement()){sql.execute(mutation);}
                accept(store,event("full","BOUGHT",10,30_003,origin));
                assertEquals(1,store.executionStats().get(561).observed);
                assertEquals(0,store.executionStats().get(561).predictedMinutes,0);
                assertEquals(2,store.positionEventCount());assertEquals(10,store.buyLimitProjections().get(0).getUsedQuantity());
                var p=store.ledgerSnapshot(account,at+60).getTradingPositions().get(0);
                assertEquals(1000,p.getTotalCost());assertEquals(10,p.getQuantity());assertNull(p.getExitContext());
            }
        }
    }
    @Test public void malformedNewTailCannotBorrowTheWarmPrefixToPairAnExecution()throws Exception {
        try(var store=new SqliteStore(folder.newFolder().toPath().resolve("tail.db"))) {
            var origin=seed(store);store.recordEvent(at+40,"fixture","bad-tail","EMPTY","{");
            accept(store,event("full","BOUGHT",10,30_003,origin));
            assertEquals(1,store.executionStats().get(561).observed);
            assertEquals(0,store.executionStats().get(561).predictedMinutes,0);
            assertEquals(2,store.positionEventCount());
        }
    }
    @Test public void failedQualifiedTailCannotPoisonARetryOrConsumeItsFinancialAndLearningIdentity()throws Exception {
        boolean[] interrupt={false};
        try(var store=new SqliteStore(folder.newFolder().toPath().resolve("rollback.db"),point->{
            if(interrupt[0] && point.equals("EXECUTION_STATS_WRITTEN"))throw new IllegalStateException("fixture interruption");
        })) {
            var origin=seed(store);interrupt[0]=true;
            var raw=gson.toJsonTree(event("rolled-back","BOUGHT",10,30_003,origin)).getAsJsonObject();
            raw.addProperty("predictionSourceEventId","wrong-original");
            try {accept(store,gson.fromJson(raw,OfferEvent.class));fail("Expected fixture interruption");}
            catch(IllegalStateException expected){assertEquals("fixture interruption",expected.getMessage());}
            assertEquals(1,store.positionEventCount());assertTrue(store.executionStats().isEmpty());assertEquals(0,store.eventCount("BOUGHT"));
            interrupt[0]=false;var full=event("full","BOUGHT",10,30_003,origin);accept(store,full);accept(store,full);
            assertEquals(1,store.executionStats().get(561).observed);
            assertEquals(12,store.executionStats().get(561).predictedMinutes,0);assertEquals(2,store.positionEventCount());
            assertEquals(10,store.buyLimitProjections().get(0).getUsedQuantity());
        }
    }
}
