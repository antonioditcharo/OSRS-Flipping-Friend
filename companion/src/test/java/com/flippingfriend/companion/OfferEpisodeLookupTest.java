package com.flippingfriend.companion;

import java.sql.*;
import java.util.*;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import org.sqlite.ProgressHandler;
import static org.junit.Assert.*;

public class OfferEpisodeLookupTest {
    @Rule public TemporaryFolder folder=new TemporaryFolder();
    // Freeze the previous production predicate/order, independently of the new index expressions.
    static final String OLD="SELECT id,payload FROM event_log WHERE event_id IS NOT NULL AND event_id<>'' "
        +"AND event_id<>? AND CASE WHEN json_valid(payload)=0 THEN 1 "
        +"WHEN json_type(payload)<>'object' THEN 1 "
        +"WHEN json_type(payload,'$.offerIdentity')='text' THEN json_extract(payload,'$.offerIdentity')=? "
        +"WHEN event_type='EMPTY' THEN 0 ELSE 1 END ORDER BY id DESC LIMIT 1";
    Connection connection(SqliteStore store)throws Exception {
        var field=SqliteStore.class.getDeclaredField("connection");field.setAccessible(true);return (Connection)field.get(store);
    }
    void put(Connection db,String id,String type,String payload)throws Exception {
        try(var s=db.prepareStatement("INSERT INTO event_log(observed_at,correlation_id,event_id,event_type,payload) VALUES(1,?,?,?,?)")) {
            s.setString(1,UUID.randomUUID().toString());s.setString(2,id);s.setString(3,type);s.setString(4,payload);s.executeUpdate();
        }
    }
    void equivalent(Connection db,String incoming,String identity)throws Exception {
        var actual=OfferEpisodeLookup.find(db,incoming,identity);
        try(var s=db.prepareStatement(OLD)) {
            s.setString(1,incoming);s.setString(2,identity);
            try(var r=s.executeQuery()) {
                if(!r.next()){assertNull(actual);return;}
                assertNotNull(actual);assertEquals(r.getLong(1),actual.row);assertEquals(r.getString(2),actual.payload);assertFalse(r.next());
            }
        }
    }
    @Test public void splitIndexesSelectExactlyTheOldRowAcrossMalformedMissingAndDuplicateJsonKeys()throws Exception {
        try(var store=new SqliteStore(folder.newFolder().toPath().resolve("golden.db"))) {
            var db=connection(store);int number=0;
            var payloads=List.of("{", "null", "[]", "4", "\"text\"", "true", "{}",
                "{\"offerIdentity\":null}","{\"offerIdentity\":12}","{\"offerIdentity\":[]}",
                "{\"offerIdentity\":\"needle\"}","{\"offerIdentity\":\"different\"}","{\"offerIdentity\":\"\"}",
                "{\"offerIdentity\":\"needle\",\"offerIdentity\":\"different\"}",
                "{\"offerIdentity\":\"different\",\"offerIdentity\":\"needle\"}",
                "{\"offerIdentity\":null,\"offerIdentity\":\"needle\"}","{\"offerIdentity\":{}}");
            for(var type:List.of("BUYING","EMPTY"))for(var payload:payloads) {
                put(db,"row-"+number++,type,payload);
                for(var identity:List.of("needle","different","absent",""))equivalent(db,"incoming",identity);
            }
            // Null/blank legacy IDs are excluded even when their JSON is malformed.
            put(db,null,"BUYING","{");put(db,"","BUYING","{");equivalent(db,"incoming","absent");
            put(db,"incoming-unknown","BUYING","{");equivalent(db,"incoming-unknown","absent");
            put(db,"incoming-known","BUYING","{\"offerIdentity\":\"needle\"}");equivalent(db,"incoming-known","needle");
        }
    }
    @Test public void newestOccurrenceAndUncertaintyKeepTheirOriginalOrderingAndIncomingExclusion()throws Exception {
        try(var store=new SqliteStore(folder.newFolder().toPath().resolve("ordering.db"))) {
            var db=connection(store);String payload="{\"offerIdentity\":\"needle\"}";
            put(db,"first","BUYING",payload);long first=OfferEpisodeLookup.find(db,"incoming","needle").row;
            put(db,"uncertain","BUYING","{}");assertEquals("{}",OfferEpisodeLookup.find(db,"incoming","needle").payload);
            put(db,"second","BUYING",payload);var latest=OfferEpisodeLookup.find(db,"incoming","needle");
            assertTrue(latest.row>first);assertEquals(payload,latest.payload);equivalent(db,"incoming","needle");
            assertEquals("{}",OfferEpisodeLookup.find(db,"second","needle").payload);
            put(db,"ignored-empty","EMPTY","{}");equivalent(db,"incoming","needle");
            // An EMPTY with an explicitly matching text identity was also a match in the old CASE.
            put(db,"matching-empty","EMPTY",payload);assertTrue(OfferEpisodeLookup.find(db,"incoming","needle").row>latest.row);
        }
    }
    @Test public void newIdentityChecksHaveBoundedNativeWorkInsteadOfScanningEveryOldOffer()throws Exception {
        try(var store=new SqliteStore(folder.newFolder().toPath().resolve("budget.db"))) {
            var db=connection(store);put(db,"old-match","BUYING","{\"offerIdentity\":\"needle\"}");
            db.setAutoCommit(false);
            try(var s=db.prepareStatement("INSERT INTO event_log(observed_at,correlation_id,event_id,event_type,payload) VALUES(1,?,?,'BUYING',?)")) {
                for(int i=0;i<20000;i++){s.setString(1,"later-"+i);s.setString(2,"later-"+i);s.setString(3,"{\"offerIdentity\":\"other-"+i+"\"}");s.addBatch();}
                s.executeBatch();db.commit();
            }finally{db.setAutoCommit(true);}
            budget(db);try {
                for(int i=0;i<20;i++) {
                    assertNotNull(OfferEpisodeLookup.find(db,"incoming","needle"));
                    assertNull(OfferEpisodeLookup.find(db,"incoming","missing"));
                }
            }finally{ProgressHandler.clearHandler(db);}
            budget(db);try(var s=db.prepareStatement(OLD)) {
                s.setString(1,"incoming");s.setString(2,"missing");s.executeQuery();fail("Old native JSON scan must exceed the same work budget");
            }catch(SQLException expected){assertTrue(expected.getMessage().contains("interrupt"));}finally{ProgressHandler.clearHandler(db);}
            put(db,"uncertain","BUYING","{");budget(db);
            try{assertEquals("{",OfferEpisodeLookup.find(db,"incoming","missing").payload);}
            finally{ProgressHandler.clearHandler(db);}
        }
    }
    void budget(Connection db)throws Exception {
        ProgressHandler.setHandler(db,100,new ProgressHandler(){int calls;protected int progress(){return ++calls>100?1:0;}});
    }
    @Test public void additiveIndexesPreserveRowsAndReadOnlyArchivesDoNotInstallThem()throws Exception {
        var path=folder.newFolder().toPath().resolve("archive.db");
        try(var store=new SqliteStore(path)) {
            var db=connection(store);put(db,"old","BUYING","{");put(db,"second","EMPTY","{}");
            try(var s=db.createStatement()){s.execute("DROP INDEX event_log_episode_identity");s.execute("DROP INDEX event_log_episode_unknown");}
        }
        try(var store=SqliteStore.openReadOnly(path)) {
            assertEquals(List.of("{","{}"),store.recentOfferEvents(0));
            try(var s=connection(store).createStatement();var r=s.executeQuery("SELECT COUNT(*) FROM sqlite_master WHERE name IN ('event_log_episode_identity','event_log_episode_unknown')")) {
                r.next();assertEquals(0,r.getInt(1));
            }
        }
        try(var store=new SqliteStore(path)) {
            assertEquals(List.of("{","{}"),store.recentOfferEvents(0));equivalent(connection(store),"incoming","absent");
            try(var s=connection(store).createStatement();var r=s.executeQuery("SELECT COUNT(*) FROM event_log")){r.next();assertEquals(2,r.getInt(1));}
        }
    }
}
