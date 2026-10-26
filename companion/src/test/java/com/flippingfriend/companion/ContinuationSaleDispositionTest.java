package com.flippingfriend.companion;

import com.flippingfriend.core.*;
import com.google.gson.Gson;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.*;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class ContinuationSaleDispositionTest {
    @Rule public TemporaryFolder folder=new TemporaryFolder();
    final String account=AccountIdentity.fromProfileKey("dispositions");
    final Gson gson=new Gson();
    OfferEvent sale(String id,String identity,int slot,int filled,long sequence,long firstSeen,boolean authenticated) {
        return OfferEvent.builder("fixture",1000+sequence,filled==3?"SOLD":"SELLING").accountId(account)
            .eventIdentity(id,"session",identity).authenticatedObservation(authenticated).slot(slot).item(28997,"Dual macuahuitl")
            .buying(false).price(120).quantities(3,filled).spent(120L*filled).sequence(sequence).firstSeenAt(firstSeen).build();
    }
    void accept(SqliteStore store,OfferEvent event)throws Exception {store.recordAndProjectOffer(event,gson.toJson(event),true);}
    @Test public void exactRecordedSaleSurvivesCollectionRestartAndReadsWithoutFinancialReplay()throws Exception {
        Path db=folder.newFolder().toPath().resolve("ledger.db");
        try(var store=new SqliteStore(db)) {
            accept(store,sale("open","sale",1,0,1,1001,true));
            accept(store,sale("partial","sale",1,1,2,1001,true));
            accept(store,sale("sold","sale",1,3,3,1001,true));
            accept(store,OfferEvent.builder("fixture",1004,"EMPTY").accountId(account).eventIdentity("clear","session",null)
                .authenticatedObservation(true).slot(1).sequence(4).build());
            var ledger=store.ledgerSnapshot(account,1010);int effects=store.positionEventCount();
            var proof=ledger.getContinuationDispositions().get(0);
            assertEquals("partial",proof.getSourceEventId());assertEquals("sale",proof.getOfferIdentity());assertEquals(1001,proof.getFirstSeenAt());
            for(int i=0;i<3;i++)assertEquals(1,store.ledgerSnapshot(account,1010).getContinuationDispositions().size());
            assertEquals(effects,store.positionEventCount());assertEquals(2,ledger.getUnresolvedSales());
        }
        try(var store=SqliteStore.openReadOnly(db)) {
            assertEquals("partial",store.ledgerSnapshot(account,1010).getContinuationDispositions().get(0).getSourceEventId());
            assertEquals(2,store.positionEventCount()); // Unknown acquisition cost stays unknown.
        }
    }
    @Test public void validatedContinuationAppendRetainsFirstDisposalAndWithdrawsLaterContradictionsWithoutRescanning() {
        var reads=new java.util.concurrent.atomic.AtomicInteger();
        var values=List.of(sale("open","sale",1,0,1,1001,true),sale("partial","sale",1,1,2,1001,true));
        List<OfferEvent> prefix=new AbstractList<>(){public int size(){return values.size();}public OfferEvent get(int i){reads.incrementAndGet();return values.get(i);}};
        var index=new ContinuationSaleRead.Index();var first=index.read(prefix,Set.of("partial"),Set.of());int work=reads.get();
        var tail=new ArrayList<>(values);tail.add(sale("later","sale",1,2,3,1001,true));
        index.appendCanonical(prefix,tail);
        assertSame(first,index.read(tail,Set.of("partial"),Set.of()));assertEquals(work,reads.get());
        assertEquals("partial",index.read(tail,Set.of("partial","later"),Set.of()).get(0).getSourceEventId());
        var contradiction=new ArrayList<>(tail);contradiction.add(sale("changed","sale",2,2,4,1001,true));
        index.appendCanonical(tail,contradiction);assertTrue(index.read(contradiction,Set.of("partial","later"),Set.of()).isEmpty());
        assertEquals(work,reads.get());
        assertTrue(index.read(new ArrayList<>(values),Set.of("partial"),Set.of("open")).isEmpty());
        assertEquals("partial",index.read(new ArrayList<>(values),Set.of("partial"),Set.of()).get(0).getSourceEventId());
    }
    @Test public void noFillUnauthenticatedRejectedOrChangedOriginalCannotProveADisposition()throws Exception {
        var origin=sale("open","sale",1,0,1,1001,true);var partial=sale("partial","sale",1,1,2,1001,true);
        assertTrue(ContinuationSaleRead.read(List.of(origin),Set.of(),Set.of()).isEmpty());
        assertTrue(ContinuationSaleRead.read(List.of(origin,partial),Set.of("partial"),Set.of("partial")).isEmpty());
        assertTrue(ContinuationSaleRead.read(List.of(sale("open","sale",1,0,1,1001,false),partial),Set.of("partial"),Set.of()).isEmpty());
        assertTrue(ContinuationSaleRead.read(List.of(origin,sale("partial","sale",1,1,2,1002,true)),Set.of("partial"),Set.of()).isEmpty());
        assertTrue(ContinuationSaleRead.read(List.of(origin,sale("partial","sale",1,1,2,1001,false)),Set.of("partial"),Set.of()).isEmpty());
    }
    @Test public void latestPlacementWinsEvenWhenAnOlderPersonalListingFillsLater()throws Exception {
        var history=List.of(sale("old-open","old",0,0,1,900,true),sale("new-open","new",1,0,2,1002,true),
            sale("new-fill","new",1,1,3,1002,true),sale("old-fill","old",0,1,4,900,true));
        var proof=ContinuationSaleRead.read(history,Set.of("old-fill","new-fill"),Set.of()).get(0);
        assertEquals("new-fill",proof.getSourceEventId());assertEquals(1002,proof.getFirstSeenAt());
    }
    @Test public void cacheWithdrawsAfterRejectedTailAndExternalSourceChanges()throws Exception {
        Path db=folder.newFolder().toPath().resolve("ledger.db");
        try(var store=new SqliteStore(db)) {
            accept(store,sale("open","sale",1,0,1,1001,true));accept(store,sale("fill","sale",1,1,2,1001,true));
            assertEquals(1,store.ledgerSnapshot(account,1010).getContinuationDispositions().size());
            accept(store,sale("regressed","sale",1,0,3,1001,true));
            assertTrue(store.ledgerSnapshot(account,1010).getContinuationDispositions().isEmpty());
        }
        try(var store=new SqliteStore(db)) {
            assertTrue(store.ledgerSnapshot(account,1010).getContinuationDispositions().isEmpty());
            try(var external=DriverManager.getConnection("jdbc:sqlite:"+db);var s=external.createStatement()) {
                s.executeUpdate("UPDATE event_log SET payload='{}' WHERE event_id='fill'");
            }
            assertTrue(store.ledgerSnapshot(account,1010).getContinuationDispositions().isEmpty());
        }
    }
    @Test public void dispositionIsNotLimitedByTheRecentHundredSalesOrAnotherAccountsRead()throws Exception {
        Path db=folder.newFolder().toPath().resolve("ledger.db");
        try(var store=new SqliteStore(db)) {
            for(int i=0;i<101;i++) {
                String identity="sale-"+i;long seq=i*3+1;
                for(var e:List.of(sale("open-"+i,identity,0,0,seq,1000+seq,true),sale("sold-"+i,identity,0,3,seq+1,1000+seq,true))) {
                    var json=gson.toJsonTree(e).getAsJsonObject();json.addProperty("itemId",i==0?28997:100+i);
                    accept(store,gson.fromJson(json,OfferEvent.class));
                }
                accept(store,OfferEvent.builder("fixture",1000+seq+2,"EMPTY").accountId(account)
                    .eventIdentity("clear-"+i,"session",null).authenticatedObservation(true).slot(0).sequence(seq+2).build());
            }
            var ledger=store.ledgerSnapshot(account,1400);assertEquals(100,ledger.getRecentSales().size());
            assertEquals(101,ledger.getContinuationDispositions().size());
            assertTrue(ledger.getRecentSales().stream().noneMatch(sale->sale.getItemId()==28997));
            assertEquals(1001,ledger.getContinuationDispositions().stream().filter(sale->sale.getItemId()==28997).findFirst().get().getFirstSeenAt());
            assertTrue(store.ledgerSnapshot(AccountIdentity.fromProfileKey("other"),1400).getContinuationDispositions().isEmpty());
        }
    }
    @Test public void failedSaleAcceptanceCannotLeakACachedDisposition()throws Exception {
        Path db=folder.newFolder().toPath().resolve("ledger.db");
        try(var store=new SqliteStore(db)){accept(store,sale("open","sale",1,0,1,1001,true));}
        try(var store=new SqliteStore(db,stage->{if("POSITION_ACCOUNTING_APPLIED".equals(stage))throw new java.io.IOException("failure");})) {
            try{accept(store,sale("fill","sale",1,1,2,1001,true));fail();}catch(java.io.IOException expected){}
            assertTrue(store.ledgerSnapshot(account,1010).getContinuationDispositions().isEmpty());assertEquals(0,store.positionEventCount());
        }
    }
    @Test public void externallyChangedSourceMustStillMatchItsExactFinancialRow()throws Exception {
        Path db=folder.newFolder().toPath().resolve("ledger.db");
        try(var store=new SqliteStore(db)) {
            accept(store,sale("open","sale",1,0,1,1001,true));accept(store,sale("fill","sale",1,1,2,1001,true));
            assertEquals(1,store.ledgerSnapshot(account,1010).getContinuationDispositions().size());
            try(var external=DriverManager.getConnection("jdbc:sqlite:"+db);var s=external.createStatement()) {
                s.executeUpdate("UPDATE event_log SET payload=json_set(payload,'$.itemId',562) WHERE event_id IS NOT NULL");
            }
            assertTrue(store.ledgerSnapshot(account,1010).getContinuationDispositions().isEmpty());
            assertEquals(1,store.positionEventCount());
        }
    }
}
