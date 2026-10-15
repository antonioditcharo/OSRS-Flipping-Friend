package com.flippingfriend.companion;

import com.flippingfriend.core.*;
import com.google.gson.Gson;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Instant;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

/** Crash boundaries in disposable stores only. No live database or network is used. */
public class ExecutionEvidenceAtomicityTest {
    @Rule public TemporaryFolder folder=new TemporaryFolder();
    final String account=AccountIdentity.fromProfileKey("execution-atomicity");
    final long at=Instant.now().getEpochSecond()-60;
    OfferEvent event(String id,String type,int filled,long sequence) {
        return OfferEvent.builder("fixture",at+sequence*20,type).accountId(account)
            .eventIdentity(id,"session","offer").slot(0).item(561,"Nature rune").buying(true).price(100)
            .quantities(10,filled).spent(filled*100L).sequence(sequence).firstSeenAt(at)
            .recommendation("advice",100,10,0,1,.9).build();
    }
    void failWrites(Path fixtureDatabase,boolean enabled)throws Exception {
        try(var connection=DriverManager.getConnection("jdbc:sqlite:"+fixtureDatabase);var sql=connection.createStatement()) {
            if(enabled)sql.execute("CREATE TRIGGER fixture_fail_execution BEFORE INSERT ON execution_stat "
                +"BEGIN SELECT RAISE(ABORT,'fixture execution write failure'); END");
            else sql.execute("DROP TRIGGER fixture_fail_execution");
        }
    }
    @Test public void failedStatisticsWriteCannotLeaveAPermanentDeduplicationClaim()throws Exception {
        Path db=folder.newFolder().toPath().resolve("claim.db");
        try(var store=new SqliteStore(db)) {
            var terminal=event("done","BOUGHT",10,2);failWrites(db,true);
            try {new ExecutionRecorder(store).record(terminal);fail("fixture failure missing");}
            catch(SQLException expected){assertTrue(expected.getMessage().contains("fixture execution write failure"));}
            failWrites(db,false);
            assertTrue(store.executionStats().isEmpty());
            assertTrue("A retry must recover the lost learning observation",new ExecutionRecorder(store).record(terminal));
            assertEquals(1,store.executionStats().get(561).observed);
        }
    }
    @Test public void canonicalTerminalRetryRecoversAccountingAndLearningTogether()throws Exception {
        Path db=folder.newFolder().toPath().resolve("offer.db");
        try(var store=new SqliteStore(db);var service=new CompanionService(new Gson(),store)) {
            service.offer(event("open","BUYING",0,1));failWrites(db,true);
            var terminal=event("done","BOUGHT",10,2);
            try {service.offer(terminal);fail("fixture failure missing");}
            catch(SQLException expected){assertTrue(expected.getMessage().contains("fixture execution write failure"));}
            failWrites(db,false);
            assertEquals("The failed offer must remain retryable instead of being accepted without its learning record",
                SqliteStore.EventAcceptance.NEW,service.offer(terminal));
            assertEquals(1,store.executionStats().get(561).observed);
            assertEquals(10,store.positionProjections().get(0).getQuantity());
            assertEquals(1,store.positionEventCount());
            assertEquals(10,store.buyLimitProjections().get(0).getUsedQuantity());
            assertEquals(SqliteStore.EventAcceptance.DUPLICATE,service.offer(terminal));
            assertEquals(1,store.executionStats().get(561).observed);
        }
    }
    @Test public void interruptionAtEitherExecutionCheckpointRollsBackTheWholeOfferAcrossRestart()throws Exception {
        for(String point:new String[]{"EXECUTION_CLAIM_RECORDED","EXECUTION_STATS_WRITTEN"}) {
            Path db=folder.newFolder().toPath().resolve("checkpoint.db");var gson=new Gson();
            var open=event("open","BUYING",0,1);var terminal=event("done","BOUGHT",10,2);
            try(var store=new SqliteStore(db)){store.recordAndProjectOffer(open,gson.toJson(open),true);}
            try(var store=new SqliteStore(db,stage->{if(point.equals(stage))throw new IllegalStateException("fixture "+point);})) {
                try {store.recordAndProjectOffer(terminal,gson.toJson(terminal),true);fail("checkpoint failure missing");}
                catch(IllegalStateException expected){assertEquals("fixture "+point,expected.getMessage());}
                assertEquals(0,store.eventCount("BOUGHT"));assertEquals(0,store.positionEventCount());
                assertTrue(store.buyLimitProjections().isEmpty());assertTrue(store.executionStats().isEmpty());
                assertEquals(OfferLifecycleState.BUY_OPEN,store.offerProjections().get(0).getState());
            }
            try(var store=new SqliteStore(db)) {
                assertEquals(SqliteStore.EventAcceptance.NEW,store.recordAndProjectOffer(terminal,gson.toJson(terminal),true).acceptance);
                assertEquals(1,store.executionStats().get(561).observed);
                assertEquals(1,store.executionStats().get(561).completed);
                assertEquals(1,store.positionEventCount());assertEquals(10,store.buyLimitProjections().get(0).getUsedQuantity());
            }
            try(var store=new SqliteStore(db)) {
                assertEquals(SqliteStore.EventAcceptance.DUPLICATE,store.recordAndProjectOffer(terminal,gson.toJson(terminal),true).acceptance);
                var replay=event("terminal-heartbeat","BOUGHT",10,3);
                store.recordAndProjectOffer(replay,gson.toJson(replay),true);
                assertEquals(1,store.executionStats().get(561).observed);assertEquals(1,store.positionEventCount());
            }
        }
    }
    @Test public void existingLegacyDeduplicationKeyIsPreservedWithoutRecounting()throws Exception {
        try(var store=new SqliteStore(folder.newFolder().toPath().resolve("legacy.db"))) {
            var terminal=event("done","BOUGHT",10,2);var gson=new Gson();
            assertTrue(new ExecutionRecorder(store).record(terminal));
            var open=event("open","BUYING",0,1);
            store.recordAndProjectOffer(open,gson.toJson(open),true);
            store.recordAndProjectOffer(terminal,gson.toJson(terminal),true);
            assertEquals(1,store.executionStats().get(561).observed);
            assertEquals(1,store.positionEventCount());
        }
    }
}
