package com.flippingfriend.companion;

import com.flippingfriend.core.*;
import com.google.gson.Gson;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class PerformanceSnapshotIsolationTest {
    @Rule public TemporaryFolder folder=new TemporaryFolder();
    final Gson json=new Gson();final String account=AccountIdentity.fromProfileKey("performance-snapshot");
    final long at=Instant.now().getEpochSecond()-3600;
    OfferEvent event(String id,String state,boolean buying,int filled,long spent,long sequence,String episode) {
        var builder=OfferEvent.builder("fixture",at+sequence,state).accountId(account).authenticatedObservation(true)
            .eventIdentity(id,"session",episode).slot(0).sequence(sequence);
        if(!"EMPTY".equals(state))builder.item(561,"Nature rune").buying(buying).price(buying?101:150)
            .quantities(3,filled).spent(spent).firstSeenAt(at+(buying?2:5));
        return builder.build();
    }
    void seed(CompanionService service)throws Exception {
        service.offer(event("initial-empty","EMPTY",false,0,0,1,null));
        service.offer(event("buy-open","BUYING",true,0,0,2,"buy"));
        service.offer(event("buy-filled","BOUGHT",true,3,301,3,"buy"));
        service.offer(event("buy-collected","EMPTY",false,0,0,4,null));
        service.offer(event("sale-open","SELLING",false,0,0,5,"sale"));
        service.offer(event("sale-partial","SELLING",false,1,150,6,"sale"));
    }
    List<String> durablePrefix(Path path)throws Exception {
        var result=new ArrayList<String>();
        try(var db=DriverManager.getConnection("jdbc:sqlite:"+path.toAbsolutePath());var statement=db.createStatement()) {
            for(String query:List.of("SELECT * FROM event_log ORDER BY id","SELECT * FROM position_event ORDER BY id"))
                try(var rows=statement.executeQuery(query)) {
                    int count=rows.getMetaData().getColumnCount();while(rows.next()) {
                        var row=new ArrayList<String>();for(int c=1;c<=count;c++)row.add(rows.getString(c));result.add(row.toString());
                    }
                }
        }
        return result;
    }
    @Test public void delayedAggregationReleasesServiceAndStoreWhileKeepingItsCapturedFinancialPrefix()throws Exception {
        var path=folder.newFolder().toPath();var accountDb=path.resolve("account.db");
        var captured=new CountDownLatch(1);var release=new CountDownLatch(1);var delay=new AtomicBoolean(true);
        var threads=Executors.newFixedThreadPool(2);
        try(var legacy=new SqliteStore(path.resolve("legacy.db"));var market=new CompanionService(json,legacy);
            var store=new SqliteStore(accountDb,stage->{
                if("PERFORMANCE_INPUT_CAPTURED".equals(stage) && delay.compareAndSet(true,false)) {
                    captured.countDown();if(!release.await(10,TimeUnit.SECONDS))throw new IllegalStateException("report test release timed out");
                }
            });var service=market.forAccountStore(store,account)) {
            seed(service);var pending=threads.submit(()->service.performance("ALL"));assertTrue(captured.await(5,TimeUnit.SECONDS));
            var writer=threads.submit(()->{
                assertEquals(SqliteStore.EventAcceptance.NEW,service.offer(event("sale-done","SOLD",false,3,450,7,"sale")));
                return service.ledger();
            });
            var ledger=writer.get(5,TimeUnit.SECONDS);
            assertTrue("Actual disposal must commit before report aggregation is released",ledger.getPositions().isEmpty());
            assertEquals(2,ledger.getSales());assertFalse(pending.isDone());var prefix=durablePrefix(accountDb);
            release.countDown();var first=pending.get(5,TimeUnit.SECONDS);
            assertEquals(1,first.saleUpdates);assertEquals(0,first.completedFlips);assertEquals(100,first.appliedCost);
            assertEquals(1,first.sales.get(0).quantity);assertEquals(prefix,durablePrefix(accountDb));
            var next=service.performance("ALL");assertEquals(2,next.saleUpdates);assertEquals(1,next.completedFlips);
            assertEquals(301,next.appliedCost);assertEquals(3,next.sales.get(0).quantity);
            assertEquals(ledger.getProfitLower(),next.profitLower);assertEquals(ledger.getProfitUpper(),next.profitUpper);
            assertEquals(prefix,durablePrefix(accountDb));assertEquals(0,legacy.positionEventCount());
        }finally{release.countDown();threads.shutdownNow();}
    }
    @Test public void rolledBackSaleCannotEnterCachedCompletionAndSameIdentityRetryCommitsOnce()throws Exception {
        var path=folder.newFolder().toPath();var accountDb=path.resolve("account.db");var fail=new AtomicBoolean();
        try(var legacy=new SqliteStore(path.resolve("legacy.db"));var market=new CompanionService(json,legacy);
            var store=new SqliteStore(accountDb,stage->{if("POSITION_ACCOUNTING_APPLIED".equals(stage) && fail.get())throw new IllegalStateException("injected sale outage");});
            var service=market.forAccountStore(store,account)) {
            seed(service);var before=service.performance("ALL");var prefix=durablePrefix(accountDb);
            fail.set(true);var done=event("sale-done","SOLD",false,3,450,7,"sale");
            assertThrows(IllegalStateException.class,()->service.offer(done));assertEquals(prefix,durablePrefix(accountDb));
            var refused=service.performance("ALL");assertEquals(before.saleUpdates,refused.saleUpdates);
            assertEquals(0,refused.completedFlips);assertEquals(before.profitLower,refused.profitLower);
            assertEquals(2,service.ledger().getPositions().get(0).getQuantity());
            fail.set(false);assertEquals(SqliteStore.EventAcceptance.NEW,service.offer(done));
            assertEquals(SqliteStore.EventAcceptance.DUPLICATE,service.offer(done));
            var finalPrefix=durablePrefix(accountDb);var after=service.performance("ALL");
            assertEquals(2,after.saleUpdates);assertEquals(1,after.completedFlips);assertEquals(301,after.appliedCost);
            assertEquals(finalPrefix,durablePrefix(accountDb));
        }
    }
    @Test public void externalSourceChangeInvalidatesCompletionCacheAndReportDoesNotRepairTheSource()throws Exception {
        var path=folder.newFolder().toPath();var accountDb=path.resolve("account.db");
        try(var legacy=new SqliteStore(path.resolve("legacy.db"));var market=new CompanionService(json,legacy);
            var store=new SqliteStore(accountDb);var service=market.forAccountStore(store,account)) {
            seed(service);service.offer(event("sale-done","SOLD",false,3,450,7,"sale"));
            var before=service.performance("ALL");assertEquals(1,before.completedFlips);
            // Disposable corruption fixture: remove the observed terminal completion while keeping every financial row.
            var changed=event("sale-done","CANCELLED_SELL",false,3,450,7,"sale");
            try(var db=DriverManager.getConnection("jdbc:sqlite:"+accountDb.toAbsolutePath());
                var sql=db.prepareStatement("UPDATE event_log SET event_type=?,payload=? WHERE event_id=?")) {
                sql.setString(1,changed.getEventType());sql.setString(2,json.toJson(changed));sql.setString(3,changed.getEventId());assertEquals(1,sql.executeUpdate());
            }
            var prefix=durablePrefix(accountDb);var after=service.performance("ALL");
            assertEquals(0,after.completedFlips);assertEquals(before.saleUpdates,after.saleUpdates);
            assertEquals(before.profitLower,after.profitLower);assertEquals(before.profitUpper,after.profitUpper);
            assertEquals(prefix,durablePrefix(accountDb));
        }
    }
}
