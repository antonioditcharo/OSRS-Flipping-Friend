package com.flippingfriend.companion;

import com.flippingfriend.core.*;
import com.google.gson.Gson;
import java.sql.*;
import java.util.*;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import org.sqlite.ProgressHandler;
import static org.junit.Assert.*;

/** Native SQL work budgets, not wall-clock assertions dependent on CI hardware. */
public class WorkflowLookupBudgetTest {
    @Rule public TemporaryFolder folder=new TemporaryFolder();
    final Gson gson=new Gson();final String account=AccountIdentity.fromProfileKey("workflow-budget");
    Connection connection(SqliteStore store)throws Exception {
        var f=SqliteStore.class.getDeclaredField("connection");f.setAccessible(true);return (Connection)f.get(store);
    }
    void budget(Connection db)throws Exception {ProgressHandler.setHandler(db,100,new ProgressHandler(){int calls;protected int progress(){return ++calls>100?1:0;}});}
    void metadata(Connection db)throws Exception {
        db.setAutoCommit(false);
        try(var s=db.prepareStatement("INSERT INTO event_log(observed_at,correlation_id,event_type,payload) VALUES(?,?,?,?)")) {
            for(int i=0;i<15000;i++){s.setLong(1,100+i);s.setString(2,"snapshot-"+i);s.setString(3,"ACCOUNT_STATE");s.setString(4,"{}");s.addBatch();}
            s.executeBatch();db.commit();
        }finally{db.setAutoCommit(true);}
    }
    @Test public void existingPerformanceSessionLookupDoesNotScanEveryLaterAccountFrame()throws Exception {
        try(var store=new SqliteStore(folder.newFolder().toPath().resolve("session.db"))) {
            store.performanceSession(account,1,false,UUID.randomUUID().toString());var db=connection(store);metadata(db);
            budget(db);try{store.performanceSession(account,20000,false,UUID.randomUUID().toString());}finally{ProgressHandler.clearHandler(db);}
            try(var s=db.createStatement();var rows=s.executeQuery("SELECT COUNT(*) FROM event_log WHERE event_type='PERFORMANCE_SESSION'")){rows.next();assertEquals(1,rows.getInt(1));}
            try(var s=db.createStatement()){s.execute("DROP INDEX event_log_type_id");}
            budget(db);try{store.performanceSession(account,20000,false,UUID.randomUUID().toString());fail("The fixture must reproduce the unindexed native scan");}
            catch(SQLException expected){assertTrue(expected.getMessage().contains("interrupt"));}finally{ProgressHandler.clearHandler(db);}
        }
    }
    @Test public void impossibleRecoveryIsRejectedBeforeDecodingAWholeAccountAndLeavesOriginalRows()throws Exception {
        try(var store=new SqliteStore(folder.newFolder().toPath().resolve("negative.db"))) {
            var db=connection(store);metadata(db);
            var sale=OfferEvent.builder("fixture",20000,"SOLD").accountId(account).eventIdentity("sale","session","sale")
                .slot(0).item(561,"Nature rune").price(120).quantities(3,3).spent(360).firstSeenAt(19000).sequence(1).authenticatedObservation(true).build();
            store.recordAndProjectOffer(sale,gson.toJson(sale),true);int before=store.positionEventCount();
            budget(db);try{SaleCostAmendments.prepare(db,account,List.of("sale"),20001);fail();}
            catch(IllegalStateException expected){assertTrue(expected.getMessage().contains("earlier recorded acquisition"));}
            finally{ProgressHandler.clearHandler(db);}
            assertEquals(before,store.positionEventCount());
            try(var s=db.createStatement();var rows=s.executeQuery("SELECT COUNT(*) FROM sale_cost_amendment")){rows.next();assertEquals(0,rows.getInt(1));}
        }
    }
    @Test public void replicaOwnershipLookupUsesTheRetainedSourceIndexAndLeavesBlankLegacyIdsUnassigned()throws Exception {
        try(var store=new SqliteStore(folder.newFolder().toPath().resolve("replica.db"))) {
            var db=connection(store);metadata(db);
            try(var s=db.createStatement()) {
                s.execute("INSERT INTO event_log(observed_at,correlation_id,event_id,event_type,payload) VALUES(1,'source','replica-source','SOLD','{\"recorded\":true}')");
                s.execute("INSERT INTO event_log(observed_at,correlation_id,event_id,event_type,payload) VALUES(1,'legacy','','SOLD','{\"legacy\":true}')");
            }
            budget(db);
            try(var s=db.prepareStatement(PilotReplication.SOURCE_LOOKUP_SQL)) {
                for(int i=0;i<20;i++) {
                    s.setString(1,"replica-source");try(var rows=s.executeQuery()){assertTrue(rows.next());assertEquals("{\"recorded\":true}",rows.getString(1));}
                    s.setString(1,"missing");try(var rows=s.executeQuery()){assertFalse(rows.next());}
                }
                s.setString(1,"");try(var rows=s.executeQuery()){assertFalse(rows.next());}
            }finally{ProgressHandler.clearHandler(db);}
            budget(db);try(var s=db.prepareStatement("SELECT payload FROM event_log WHERE event_id=? LIMIT 1")) {
                s.setString(1,"missing");s.executeQuery();fail("The old query must reproduce its account-wide scan");
            }catch(SQLException expected){assertTrue(expected.getMessage().contains("interrupt"));}finally{ProgressHandler.clearHandler(db);}
        }
    }
    @Test public void supportIndexRetainsRepeatedTerminalsPartialCancellationsAndFullLiveCounters()throws Exception {
        try(var store=new SqliteStore(folder.newFolder().toPath().resolve("support.db"))) {
            var db=connection(store);metadata(db);
            try(var s=db.prepareStatement("INSERT INTO event_log(observed_at,correlation_id,event_id,event_type,payload) VALUES(1000,?,?,?,?)")) {
                for(var type:List.of("SELLING","SOLD","CANCELLED_SELL","BUYING","BOUGHT","CANCELLED_BUY"))for(int occurrence=0;occurrence<2;occurrence++) {
                    String id=type+occurrence;boolean buying=type.contains("BUY")||type.equals("BOUGHT");
                    var event=OfferEvent.builder("fixture",1000,type).accountId(account).eventIdentity(id,"session",id).sequence(occurrence+1)
                        .slot(0).item(561,"Nature rune").buying(buying).price(120).quantities(3,type.startsWith("CANCELLED")?1:3).spent(120).build();
                    s.setString(1,id);s.setString(2,id);s.setString(3,type);s.setString(4,gson.toJson(event));s.addBatch();
                }s.executeBatch();
            }
            budget(db);try(var s=db.createStatement();var rows=s.executeQuery("SELECT COUNT(*) FROM event_log WHERE "+BackgroundTradeRecovery.TERMINAL_SOURCES)) {
                rows.next();assertEquals(12,rows.getInt(1));
            }finally{ProgressHandler.clearHandler(db);}
            try(var s=db.createStatement()){s.execute("INSERT INTO event_log(observed_at,correlation_id,event_id,event_type,payload) VALUES(1001,'bad','bad','SOLD','{')");}
            budget(db);try(var s=db.createStatement();var rows=s.executeQuery("SELECT id FROM event_log WHERE "+BackgroundTradeRecovery.INVALID_SOURCE_JSON+" LIMIT 1")) {
                assertTrue(rows.next());
            }finally{ProgressHandler.clearHandler(db);}
        }
    }
}
