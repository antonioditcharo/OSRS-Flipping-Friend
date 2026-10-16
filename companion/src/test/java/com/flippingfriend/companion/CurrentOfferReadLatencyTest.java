package com.flippingfriend.companion;

import com.flippingfriend.core.*;
import com.google.gson.Gson;
import java.nio.file.Path;
import java.sql.Connection;
import java.time.Instant;
import java.util.*;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.sqlite.ProgressHandler;
import static org.junit.Assert.*;

/** Bound actual SQLite work, rather than a flaky wall-clock limit or a copied SQL assertion. */
public class CurrentOfferReadLatencyTest {
    @Rule public TemporaryFolder folder = new TemporaryFolder();
    final Gson gson = new Gson();
    final String account = AccountIdentity.fromProfileKey("query-budget");
    final long at = Instant.now().getEpochSecond() - 4;

    Connection connection(SqliteStore store) throws Exception {
        var field = SqliteStore.class.getDeclaredField("connection");
        field.setAccessible(true);
        return (Connection) field.get(store);
    }

    Map<Integer,String> observe(SqliteStore store, String prefix, int sequence) throws Exception {
        Map<Integer,String> receipt = new HashMap<>();
        for (int slot=0; slot<8; slot++) {
            String id = prefix + slot;
            var event = OfferEvent.builder("fixture",at,"EMPTY").accountId(account)
                .eventIdentity(id,"session",null).authenticatedObservation(true)
                .slot(slot).sequence(sequence+slot).build();
            store.recordAndProjectOffer(event,gson.toJson(event),true);
            receipt.put(slot,id);
        }
        return receipt;
    }

    AccountSnapshot snapshot(Map<Integer,String> receipt) {
        return new AccountSnapshot("capture",at,100_000_000,0,8,8,true,false,0)
            .withAccountId(account).withAutomaticTradeScope().withOfferReadSet(receipt);
    }

    AccountSnapshot seed(SqliteStore store) throws Exception {
        var capture = snapshot(observe(store,"source-",1));
        store.recordAndReconcileAccount(capture,PositionStateView.from(capture),gson.toJson(capture));
        return capture;
    }

    void addHistory(SqliteStore store) throws Exception {
        // Only disposable fixture rows. Account-state records legitimately have no offer identity.
        Connection db = connection(store);
        db.setAutoCommit(false);
        try (var insert = db.prepareStatement("INSERT INTO event_log(observed_at,correlation_id,event_type,payload) VALUES(?,?,?,?)")) {
            for (int i=0; i<30_000; i++) {
                insert.setLong(1,at); insert.setString(2,"heartbeat-"+i);
                insert.setString(3,"ACCOUNT_STATE"); insert.setString(4,"{\"unrelated\":\""+"x".repeat(128)+"\"}");
                insert.addBatch();
            }
            insert.executeBatch(); db.commit();
        } finally { db.setAutoCommit(true); }
    }

    void budget(SqliteStore store) throws Exception {
        ProgressHandler.setHandler(connection(store),1000,new ProgressHandler() {
            int callbacks;
            @Override protected int progress() { return ++callbacks > 150 ? 1 : 0; }
        });
    }

    @Test public void heartbeatWithExistingRiskWindowDoesNotRescanHistoryForEachSlot() throws Exception {
        try (var store = new SqliteStore(folder.newFolder().toPath().resolve("heartbeat.db"))) {
            var capture = seed(store); addHistory(store); budget(store);
            try {
                store.recordAndReconcileAccount(capture,PositionStateView.from(capture),gson.toJson(capture));
                var read = store.planningProjections(account);
                assertEquals(8,read.currentEvents.size());
                assertTrue(CurrentOfferReadSet.valid(read,capture,at));
                assertEquals(100_000_000,read.riskEvidence.window.startingCapital);
                assertEquals(1,store.eventCount(RiskWindow.EVENT_TYPE));
            } finally { ProgressHandler.clearHandler(connection(store)); }
        }
    }

    @Test public void restartValidatesOriginalRiskSourcesWithinOneHistoryScanBudget() throws Exception {
        Path path = folder.newFolder().toPath().resolve("restart.db");
        AccountSnapshot capture;
        try (var store = new SqliteStore(path)) { capture=seed(store); addHistory(store); }
        try (var store = new SqliteStore(path)) {
            budget(store);
            try {
                var read = store.planningProjections(account);
                assertTrue(CurrentOfferReadSet.valid(read,capture,at));
                assertNotNull(read.riskEvidence.window);
                assertEquals(100_000_000,read.riskEvidence.window.startingCapital);
                assertEquals(8,read.currentEvents.size());
            } finally { ProgressHandler.clearHandler(connection(store)); }
        }
    }

    @Test public void historicalGapScopeAndCapitalBoundsShareOneCoherentHistoryRead() throws Exception {
        var f=new BoundedPlanningStateTest();
        try (var store=new SqliteStore(folder.newFolder().toPath().resolve("scoped-history.db"))) {
            var capture=f.snapshot(f.seed(store));
            Connection db=connection(store);
            db.setAutoCommit(false);
            try (var insert=db.prepareStatement("INSERT INTO event_log(observed_at,correlation_id,event_id,event_type,payload) VALUES(?,?,?,?,?)")) {
                for (int i=0;i<30_000;i++) {
                    var event=OfferEvent.builder("old",f.now-60,"EMPTY").accountId(f.account)
                        .eventIdentity("older-"+i,"earlier-session",null).slot(i%8).sequence(i+1).build();
                    insert.setLong(1,f.now-60);insert.setString(2,"old");insert.setString(3,event.getEventId());
                    insert.setString(4,"EMPTY");insert.setString(5,gson.toJson(event));insert.addBatch();
                }
                insert.executeBatch();db.commit();
            } finally {db.setAutoCommit(true);}
            ProgressHandler.setHandler(db,1000,new ProgressHandler() {
                int callbacks;
                @Override protected int progress() {return ++callbacks>400?1:0;}
            });
            try {
                var read=store.planningProjections(f.account);
                assertTrue(CurrentOfferReadSet.valid(read,capture,f.now));
                assertEquals(30_011,read.history.size());
                assertEquals(2,read.tradeScope.positions.get(561).getQuantity());
                var exposure=DurablePlanningExposure.forAccount(read,capture,f.now);
                assertNotNull(exposure);
                assertEquals(Long.valueOf(1000),exposure.committedByItem.get(561));
                assertEquals(Set.of(561),exposure.unavailableEntryItems);
            } finally {ProgressHandler.clearHandler(db);}
        }
    }

    void rejectBrokenCurrentSource(String mutation, String replacement) throws Exception {
        try (var store = new SqliteStore(folder.newFolder().toPath().resolve("broken.db"))) {
            seed(store);
            var capture=snapshot(observe(store,"latest-",20));
            try (var change=connection(store).prepareStatement(mutation)) {
                if (replacement!=null) change.setString(1,replacement);
                change.executeUpdate();
            }
            var read=store.planningProjections(account);
            assertTrue("A broken slot cannot be silently dropped from the read set",read.currentEvents.isEmpty());
            assertFalse(CurrentOfferReadSet.valid(read,capture,at));
        }
    }

    @Test public void missingSourceRetainsLeftJoinFailureRatherThanSevenValidSlots() throws Exception {
        rejectBrokenCurrentSource("DELETE FROM event_log WHERE event_id='latest-0'",null);
    }
    @Test public void nullSourcePointerCannotReuseEarlierSlotEvidence() throws Exception {
        rejectBrokenCurrentSource("UPDATE offer_projection SET source_event_id=NULL WHERE slot=0",null);
    }
    @Test public void malformedSourcePayloadStillWithdrawsTheEntireCurrentProof() throws Exception {
        rejectBrokenCurrentSource("UPDATE event_log SET payload=? WHERE event_id='latest-0'","{");
    }
    @Test public void foreignAccountPayloadCannotSupplyAValidCurrentSlot() throws Exception {
        String foreign=gson.toJson(OfferEvent.builder("fixture",at,"EMPTY")
            .accountId(AccountIdentity.fromProfileKey("other"))
            .eventIdentity("latest-0","session",null).authenticatedObservation(true).slot(0).sequence(20).build());
        rejectBrokenCurrentSource("UPDATE event_log SET payload=? WHERE event_id='latest-0'",foreign);
    }
}
