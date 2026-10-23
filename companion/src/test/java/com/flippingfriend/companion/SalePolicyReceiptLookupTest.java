package com.flippingfriend.companion;

import com.flippingfriend.core.*;
import com.google.gson.Gson;
import java.lang.reflect.*;
import java.nio.file.Path;
import java.sql.*;
import java.util.*;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

/** Exact production SQL must use identity indexes without skipping source or ambiguity checks. */
public class SalePolicyReceiptLookupTest {
    @Rule public TemporaryFolder folder=new TemporaryFolder();
    final Gson json=new Gson();
    private SalePolicyReceiptWorkflowTest fixtures(){var fixture=new SalePolicyReceiptWorkflowTest();fixture.folder=folder;return fixture;}
    private List<String> rows(Path path,String query)throws Exception {
        var result=new ArrayList<String>();try(var db=DriverManager.getConnection("jdbc:sqlite:"+path.toAbsolutePath());
            var sql=db.createStatement();var rows=sql.executeQuery(query)) {
            int columns=rows.getMetaData().getColumnCount();while(rows.next()) {
                var row=new ArrayList<String>();for(int c=1;c<=columns;c++)row.add(rows.getString(c));result.add(row.toString());
            }
        }return result;
    }
    private List<OfferEvent> history(Connection db)throws Exception {
        var result=new ArrayList<OfferEvent>();try(var sql=db.createStatement();var rows=sql.executeQuery(
                "SELECT payload FROM event_log WHERE event_id IS NOT NULL AND event_id<>'' ORDER BY id")) {
            while(rows.next())result.add(json.fromJson(rows.getString(1),OfferEvent.class));
        }return result;
    }
    private void assertIndexed(Connection db,String query,String expectedIndex)throws Exception {
        var details=new ArrayList<String>();try(var sql=db.prepareStatement("EXPLAIN QUERY PLAN "+query)) {
            sql.setString(1,"fixture");try(var rows=sql.executeQuery()){while(rows.next())details.add(rows.getString("detail"));}
        }
        String plan=String.join(" | ",details);
        assertTrue(query+" / "+plan,plan.contains("SEARCH event_log") && plan.contains(expectedIndex));
        assertFalse(plan,plan.contains("SCAN event_log"));
    }
    @Test public void indexedMetadataMigrationPreservesEveryFinancialAndSourceRowAndCoversAllLookupQueries()throws Exception {
        var fixtures=fixtures();try(var f=fixtures.new Fixture()) {
            var before=rows(f.path,"SELECT * FROM event_log ORDER BY id");var financial=rows(f.path,"SELECT * FROM position_event ORDER BY id");
            try(var db=DriverManager.getConnection("jdbc:sqlite:"+f.path.toAbsolutePath());var sql=db.createStatement()) {
                sql.execute("DROP INDEX IF EXISTS event_log_account_correlation");
                SalePolicyReceiptStore.initialize(sql);SalePolicyReceiptStore.initialize(sql);
                assertIndexed(db,SalePolicyReceiptStore.SOURCE_LOOKUP_SQL,"event_log_event_id_unique");
                assertIndexed(db,SalePolicyReceiptStore.BINDING_ROW_SQL,"event_log_event_id_unique");
                assertIndexed(db,SalePolicyReceiptStore.ACCOUNT_SOURCE_SQL,"event_log_account_correlation");
                assertIndexed(db,SalePolicyReceiptStore.ACCOUNT_CORRELATION_SQL,"event_log_account_correlation");
            }
            assertEquals(before,rows(f.path,"SELECT * FROM event_log ORDER BY id"));assertEquals(financial,rows(f.path,"SELECT * FROM position_event ORDER BY id"));
            assertNotNull(f.issue(UUID.randomUUID().toString()));
        }
    }
    private static final class Queries {
        final Map<String,Integer> executions=new HashMap<>();
        int count(String sql){return executions.getOrDefault(sql,0);}
        Connection counted(Connection db) {
            return (Connection)Proxy.newProxyInstance(Connection.class.getClassLoader(),new Class<?>[]{Connection.class},(proxy,method,args)->{
                Object result=invoke(db,method,args);
                if("prepareStatement".equals(method.getName()) && args!=null && args.length>0 && args[0] instanceof String) {
                    String sql=(String)args[0];var prepared=(PreparedStatement)result;
                    return Proxy.newProxyInstance(PreparedStatement.class.getClassLoader(),new Class<?>[]{PreparedStatement.class},(p,m,a)->{
                        if("executeQuery".equals(m.getName()))executions.merge(sql,1,Integer::sum);
                        return invoke(prepared,m,a);
                    });
                }
                return result;
            });
        }
        private static Object invoke(Object target,Method method,Object[] args)throws Throwable {
            try{return method.invoke(target,args);}catch(InvocationTargetException failure){throw failure.getCause();}
        }
    }
    @Test public void propagatedReceiptFramesLookupOnlyTheirImmutableFirstBindingWhileKeepingAllEightSourcesQualified()throws Exception {
        var fixtures=fixtures();try(var f=fixtures.new Fixture()) {
            var receipt=f.issue(UUID.randomUUID().toString());
            var accepted=AcceptedSalePolicyProof.accepted(receipt,f.now,CaptureOrder.next()).captured(CaptureOrder.next());
            var first=f.helper.event(0,"SELLING","sale",3,0,0,150,f.now+1);var bound=accepted.bindTo(first);assertNotNull(bound);
            first=first.withAcceptedSalePolicyProof(bound);f.accept(first);
            OfferEvent latest=first;
            for(int n=2;n<=40;n++) {
                latest=f.helper.event(0,"SELLING","sale",3,0,0,150,f.now+n).withAcceptedSalePolicyProof(bound);f.accept(latest);
            }
            var prefix=rows(f.path,"SELECT * FROM event_log WHERE event_type<>'ACCOUNT_STATE' ORDER BY id");var financial=rows(f.path,"SELECT * FROM position_event ORDER BY id");
            try(var db=DriverManager.getConnection("jdbc:sqlite:"+f.path.toAbsolutePath())) {
                var queries=new Queries();var history=history(db);
                var evidence=SalePolicyReceiptStore.read(queries.counted(db),f.helper.id,history);
                assertEquals(1,queries.count(SalePolicyReceiptStore.BINDING_ROW_SQL));
                assertEquals("Each of eight account sources and the exact policy source still require verification",9,
                    queries.count(SalePolicyReceiptStore.SOURCE_LOOKUP_SQL));
                assertEquals(1,evidence.eventRows.size());assertTrue(evidence.qualifies(bound,first));
                assertFalse(evidence.qualifies(bound,latest));assertTrue(SalePolicyEvidence.resolve(history,f.source.getEventId(),evidence).valid());
                // A contradictory pinned source still withdraws authority with the indexed read path.
                try(var sql=db.createStatement()){sql.executeUpdate("UPDATE event_log SET observed_at=observed_at+1 WHERE id="+receipt.getAccountRowId());}
                var rejected=SalePolicyReceiptStore.read(queries.counted(db),f.helper.id,history);
                assertTrue(rejected.receipts.isEmpty());assertFalse(SalePolicyEvidence.resolve(history,f.source.getEventId(),rejected).valid());
            }
            // Only the deliberate account metadata corruption changed; reporting never writes the source or finances.
            var after=rows(f.path,"SELECT * FROM event_log WHERE event_type<>'ACCOUNT_STATE' ORDER BY id");
            assertEquals(prefix,after);assertEquals(financial,rows(f.path,"SELECT * FROM position_event ORDER BY id"));
        }
    }
    @Test public void malformedFirstBindingClaimsCannotBorrowTheIndexedSourceRowOrAPropagatedFrame()throws Exception {
        for(String damage:List.of("source","counters","order","version")) {
            var fixtures=fixtures();try(var f=fixtures.new Fixture()) {
                var receipt=f.issue(UUID.randomUUID().toString());
                var first=f.helper.event(0,"SELLING","sale",3,0,0,150,f.now+1);
                var bound=AcceptedSalePolicyProof.accepted(receipt,f.now,CaptureOrder.next()).captured(CaptureOrder.next()).bindTo(first);assertNotNull(bound);
                var wire=json.toJsonTree(bound).getAsJsonObject();
                if("source".equals(damage))wire.addProperty("sourceEventId","different-first-source");
                if("counters".equals(damage)){wire.addProperty("filledQuantity",1);wire.addProperty("spent",150);}
                if("order".equals(damage))wire.getAsJsonObject("captureOrder").addProperty("order",bound.getAcceptedOrder().getOrder());
                if("version".equals(damage))wire.addProperty("formatVersion",2);
                var malformed=json.fromJson(wire,AcceptedSalePolicyProof.class);first=first.withAcceptedSalePolicyProof(malformed);f.accept(first);
                f.accept(f.helper.event(0,"SELLING","sale",3,0,0,150,f.now+2).withAcceptedSalePolicyProof(malformed));
                try(var db=DriverManager.getConnection("jdbc:sqlite:"+f.path.toAbsolutePath())) {
                    var queries=new Queries();var history=history(db);var evidence=SalePolicyReceiptStore.read(queries.counted(db),f.helper.id,history);
                    assertEquals(damage,0,queries.count(SalePolicyReceiptStore.BINDING_ROW_SQL));
                    assertFalse(damage,evidence.qualifies(malformed,first));
                    assertFalse(damage,SalePolicyEvidence.resolve(history,f.source.getEventId(),evidence).valid());
                }
            }
        }
    }
    @Test public void conflictingValidFirstBindingsRemainVisibleAndCannotBeDeduplicatedIntoAuthority()throws Exception {
        var fixtures=fixtures();try(var f=fixtures.new Fixture()) {
            var receipt=f.issue(UUID.randomUUID().toString());
            var first=f.helper.event(0,"SELLING","sale",3,0,0,150,f.now+1);
            var proof=AcceptedSalePolicyProof.accepted(receipt,"first-acceptance",f.now,CaptureOrder.next()).captured(CaptureOrder.next()).bindTo(first);
            assertNotNull(proof);f.accept(first.withAcceptedSalePolicyProof(proof));
            var other=f.helper.event(0,"SELLING","sale",3,0,0,150,f.now+2);
            var conflict=AcceptedSalePolicyProof.accepted(receipt,"conflicting-acceptance",f.now,CaptureOrder.next()).captured(CaptureOrder.next()).bindTo(other);
            assertNotNull(conflict);f.accept(other.withAcceptedSalePolicyProof(conflict));
            try(var db=DriverManager.getConnection("jdbc:sqlite:"+f.path.toAbsolutePath())) {
                var queries=new Queries();var history=history(db);var evidence=SalePolicyReceiptStore.read(queries.counted(db),f.helper.id,history);
                assertEquals(2,queries.count(SalePolicyReceiptStore.BINDING_ROW_SQL));
                assertTrue(evidence.qualifies(proof,first));assertTrue(evidence.qualifies(conflict,other));
                assertFalse(SalePolicyEvidence.resolve(history,f.source.getEventId(),evidence).valid());
            }
        }
    }
}
