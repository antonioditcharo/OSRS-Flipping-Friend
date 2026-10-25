package com.flippingfriend.companion;

import com.flippingfriend.core.*;
import com.google.gson.Gson;
import java.nio.file.Path;
import java.sql.*;
import java.util.*;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class SaleCostAmendmentsTest {
    @Rule public TemporaryFolder folder=new TemporaryFolder();
    final String account=AccountIdentity.fromProfileKey("amendment");final Gson gson=new Gson();
    OfferEvent event(String id,String episode,String type,int filled,long spent,long seq) {
        boolean buy=type.equals("BUYING") || type.equals("BOUGHT");
        var b=OfferEvent.builder("fixture",1792860000+seq,type).accountId(account).authenticatedObservation(true)
            .eventIdentity(id,"session",episode).sequence(seq).slot(0);
        if(!type.equals("EMPTY"))b.item(561,"Nature rune").buying(buy).price(buy?101:120).quantities(100,filled).spent(spent);
        return b.build();
    }
    void accept(SqliteStore store,OfferEvent event)throws Exception {store.recordAndProjectOffer(event,gson.toJson(event),true);}
    Path fixture(boolean buy)throws Exception {
        var path=folder.newFolder().toPath().resolve("fixture.db");
        try(var store=new SqliteStore(path)) {
            accept(store,event("empty",null,"EMPTY",0,0,1));
            if(buy) {
                accept(store,event("buy-open","buy","BUYING",0,0,2));accept(store,event("buy","buy","BOUGHT",100,10003,3));
                accept(store,event("collected",null,"EMPTY",0,0,4));
            }
            accept(store,event("sell-open","sell","SELLING",0,0,5));
            accept(store,event("partial","sell","SELLING",40,4800,6));
            accept(store,event("sold","sell","SOLD",100,12000,7));
        }
        // Reproduce the older writer's refused-cost row in this disposable fixture only.
        try(var c=DriverManager.getConnection("jdbc:sqlite:"+path);var s=c.createStatement()) {
            s.executeUpdate("UPDATE position_event SET applied_cost_basis=0,profit_lower=NULL,profit_upper=NULL,transition_reason='Sale lacks complete observed acquisition coverage',sale_accounting_reason='COST_BASIS_UNRESOLVED; EXACT_UNDER_RECORDED_RULE' WHERE source_event_id='sold'");
        }
        return path;
    }
    String originals(Path path)throws Exception {
        var rows=new ArrayList<List<Object>>();
        try(var c=DriverManager.getConnection("jdbc:sqlite:"+path);var s=c.createStatement()) {
            for(String table:List.of("event_log","position_event","buy_limit_event","counted_offer"))
                try(var r=s.executeQuery("SELECT * FROM "+table)) {
                    while(r.next()){var row=new ArrayList<>();for(int i=1;i<=r.getMetaData().getColumnCount();i++)row.add(r.getObject(i));rows.add(row);}
                }
        }
        return gson.toJson(rows);
    }
    @Test public void exactRemainingCostAmendmentSurvivesRestartWithoutChangingExecutionsOrCountingASaleTwice()throws Exception {
        var path=fixture(true);String original=originals(path);
        try(var store=new SqliteStore(path)) {
            assertEquals(1,store.ledgerSnapshot(account).getUnresolvedSales());
            store.amendSaleCosts(account,List.of("sold"),1792860100);
            var ledger=store.ledgerSnapshot(account);assertEquals(2,ledger.getSales());assertEquals(2,ledger.getBoundedSales());
            assertEquals(0,ledger.getUnresolvedSales());assertEquals(1797,ledger.getProfitLower());
            var sold=ledger.getRecentSales().stream().filter(s->s.getSourceEventId().equals("sold")).findFirst().get();
            assertEquals(6002,sold.getAppliedCost());assertEquals(Long.valueOf(1078),sold.getProfitLower());
            assertTrue(sold.getReason().contains(SaleCostAmendments.VERSION));
            assertTrue(ledger.getMissedFlips().stream().noneMatch(e->e.getEventId().equals("sold")));
        }
        try(var store=new SqliteStore(path)) {
            store.amendSaleCosts(account,List.of("sold"),1792860200);
            assertEquals(2,store.ledgerSnapshot(account).getSales());assertEquals(1797,store.ledgerSnapshot(account).getProfitLower());
            var read=store.planningProjections(account);assertEquals(0,read.riskEvidence.unresolvedManagedSales);
        }
        assertEquals(original,originals(path));
    }
    @Test public void missingEarlierPurchaseAndForeignAccountCannotBeAmended()throws Exception {
        for(boolean purchase:List.of(false,true)) {
            var path=fixture(purchase);String original=originals(path);
            try(var store=new SqliteStore(path)) {
                try{store.amendSaleCosts(purchase?AccountIdentity.fromProfileKey("foreign"):account,List.of("sold"),1792860100);fail();}
                catch(IllegalStateException expected) { }
                assertTrue(store.ledgerSnapshot(account).getUnresolvedSales()>0);
            }
            assertEquals(original,originals(path));
        }
    }
    @Test public void laterInvalidRequestAndTransactionFailureLeaveNoPartialAmendment()throws Exception {
        for(boolean injected:List.of(false,true)) {
            var path=fixture(true);String original=originals(path);
            try(var store=new SqliteStore(path,point->{if(injected && point.equals("SALE_COST_AMENDMENTS_WRITTEN"))throw new Exception("rollback");})) {
                try {store.amendSaleCosts(account,injected?List.of("sold"):List.of("sold","missing"),1792860100);fail();}
                catch(Exception expected) {assertNotNull(expected.getMessage());}
                assertEquals(1,store.ledgerSnapshot(account).getUnresolvedSales());
            }
            assertEquals(original,originals(path));
        }
    }
    @Test public void amendmentFeedsDurableRiskUnderTheOriginalWindowInsteadOfResettingItsLossAnchor()throws Exception {
        var f=new DurableRiskWindowTest();f.folder=folder;var path=folder.newFolder().toPath().resolve("risk.db");String source,window;
        try(var store=new SqliteStore(path)) {
            var start=f.capture(store,100000000);window=f.evaluate(store,start,100).getWindowId();
            f.buy(store,"buy",100,100);f.sell(store,"sale",100,120,12000);
        }
        try(var c=DriverManager.getConnection("jdbc:sqlite:"+path);var s=c.createStatement()) {
            try(var r=s.executeQuery("SELECT source_event_id FROM position_event WHERE effect_type='DISPOSE'")){r.next();source=r.getString(1);}
            s.executeUpdate("UPDATE position_event SET applied_cost_basis=0,profit_lower=NULL,profit_upper=NULL,transition_reason='Sale lacks complete observed acquisition coverage',sale_accounting_reason='COST_BASIS_UNRESOLVED; EXACT_UNDER_RECORDED_RULE' WHERE effect_type='DISPOSE'");
        }
        try(var store=new SqliteStore(path)) {
            var capture=f.capture(store,100001800);var before=f.evaluate(store,capture,100);assertEquals(1,before.getUnresolvedManagedSaleUpdates());
            store.amendSaleCosts(f.account,List.of(source),f.at);var after=f.evaluate(store,capture,100);
            assertTrue(after.getReason(),after.isAvailable());assertEquals(window,after.getWindowId());assertEquals(before.getLedgerCursor(),after.getLedgerCursor());
            assertEquals(Long.valueOf(1800),after.getBoundedRealizedProfitLower());assertEquals(0,after.getUnresolvedManagedSaleUpdates());
            assertEquals(Long.valueOf(0),after.getUncertainLossReserve());
        }
    }

    @Test public void backgroundQualificationDoesNotBlockCanonicalWritesAndCommitsWithoutReplayingExecutions()throws Exception {
        Path path=fixture(true);
        try(var store=new SqliteStore(path);var read=DriverManager.getConnection("jdbc:sqlite:"+path.toAbsolutePath().toUri().toASCIIString()+"?mode=ro")) {
            long revision=store.recoveryExternalRevision();read.setAutoCommit(false);
            var prepared=SaleCostAmendments.prepare(read,account,List.of("sold"),1792860100).get(0);
            long effects=store.positionEventCount();
            // This canonical append commits while the separate historical read transaction is open.
            accept(store,event("later-cleared",null,"EMPTY",0,0,8));read.rollback();
            prepared.expectedExternalRevision=revision;store.commitPreparedRecovery(prepared);
            assertEquals(effects,store.positionEventCount());assertEquals(0,store.ledgerSnapshot(account).getUnresolvedSales());
        }
    }
    @Test public void sameCountExternalPrefixEditsInvalidateAQueuedBackgroundRepair()throws Exception {
        Path path=fixture(true);
        try(var store=new SqliteStore(path);var read=DriverManager.getConnection("jdbc:sqlite:"+path.toAbsolutePath().toUri().toASCIIString()+"?mode=ro")) {
            long revision=store.recoveryExternalRevision();read.setAutoCommit(false);
            var prepared=SaleCostAmendments.prepare(read,account,List.of("sold"),1792860100).get(0);read.rollback();
            prepared.expectedExternalRevision=revision;
            try(var external=DriverManager.getConnection("jdbc:sqlite:"+path);var change=external.createStatement()) {
                change.executeUpdate("UPDATE position_event SET acquisition_cost=acquisition_cost+1 WHERE source_event_id='buy'");
            }
            try{store.commitPreparedRecovery(prepared);fail("changed financial prefix was accepted");}catch(IllegalStateException expected){}
            assertEquals(1,store.ledgerSnapshot(account).getUnresolvedSales());
        }
    }
}
