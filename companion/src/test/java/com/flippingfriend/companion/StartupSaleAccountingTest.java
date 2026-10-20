package com.flippingfriend.companion;

import com.flippingfriend.core.*;
import com.google.gson.Gson;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

/** Replays delivery order, not financial history: offers drain before the first account POST. */
public class StartupSaleAccountingTest {
    @Rule public TemporaryFolder folder=new TemporaryFolder();
    final Gson gson=new Gson();
    final String account=AccountIdentity.fromProfileKey("startup-sales");
    final long at=Instant.now().getEpochSecond()-100;
    OfferEvent event(String id,String type,int filled,long spent,int seq) {
        var b=OfferEvent.builder("fixture",at+seq,type).accountId(account).authenticatedObservation(true)
            .eventIdentity(id,"session",type.equals("EMPTY")?null:type.equals("BUYING")||type.equals("BOUGHT")?"buy":"sell")
            .slot(0).sequence(seq);
        if(!type.equals("EMPTY"))b.item(561,"Nature rune").buying(type.equals("BUYING")||type.equals("BOUGHT"))
            .price(type.equals("BUYING")||type.equals("BOUGHT")?100:120).quantities(3,filled).spent(spent);
        return b.build();
    }
    void accept(SqliteStore s,OfferEvent e)throws Exception{s.recordAndProjectOffer(e,gson.toJson(e),true);}
    void seed(SqliteStore s)throws Exception {
        var snapshot=new AccountSnapshot("capture",at,100000,0,8,8,true,false,0).withAccountId(account).withAutomaticTradeScope();
        s.recordAndReconcileAccount(snapshot,PositionStateView.from(snapshot),gson.toJson(snapshot));
        accept(s,event("empty","EMPTY",0,0,1));accept(s,event("open","BUYING",0,0,2));
        accept(s,event("bought","BOUGHT",3,299,3));accept(s,event("clear","EMPTY",0,0,4));
        accept(s,event("listing","SELLING",0,0,5));
    }
    @Test public void restartedOfferDrainUsesRecordedScopeBeforeAnyFreshAccountSnapshot()throws Exception {
        Path db=folder.newFolder().toPath().resolve("restart.db");
        try(var s=new SqliteStore(db)){seed(s);assertTrue(s.positionProjections().isEmpty());}
        try(var s=new SqliteStore(db);var legacy=new SqliteStore(folder.newFolder().toPath().resolve("legacy.db"));
            var market=new CompanionService(gson,legacy);var service=market.forAccountStore(s,account)) {
            service.rehydrate();int before=s.positionEventCount();
            assertEquals(SqliteStore.EventAcceptance.NEW,service.offer(event("progress","SELLING",2,240,6)));
            var sale=s.ledgerSnapshot(account).getRecentSales().get(0);
            assertEquals(199,sale.getAppliedCost());assertEquals(Long.valueOf(37),sale.getProfitLower());
            assertEquals(1,s.ledgerSnapshot(account).getTradingPositions().get(0).getQuantity());
            assertEquals(100,s.ledgerSnapshot(account).getTradingPositions().get(0).getTotalCost());
            assertEquals(SqliteStore.EventAcceptance.DUPLICATE,service.offer(event("progress","SELLING",2,240,6)));
            service.offer(event("full-progress","SELLING",3,360,7));service.offer(event("terminal","SOLD",3,360,8));
            var ledger=s.ledgerSnapshot(account);assertEquals(before+2,s.positionEventCount());
            assertEquals(0,ledger.getUnresolvedSales());assertEquals(55,ledger.getProfitLower());
            assertTrue(ledger.getTradingPositions().isEmpty());assertTrue(s.positionProjections().isEmpty());
            assertEquals(0,legacy.positionEventCount());
            assertEquals(PortfolioPlanOutcome.COMPANION_STATE_UNAVAILABLE,service.plan().getOutcome());
        }
    }
    @Test public void fullProgressThenTerminalCountsOneFlipWithoutAnotherDisposalOrFinancialRewrite()throws Exception {
        Path db=folder.newFolder().toPath().resolve("complete.db");
        try(var s=new SqliteStore(db)) {
            seed(s);accept(s,event("progress","SELLING",3,360,6));
            assertEquals(0,s.performance(account,"ALL",at+20).completedFlips);
            int effects=s.positionEventCount();accept(s,event("terminal","SOLD",3,360,7));
            accept(s,event("heartbeat","SOLD",3,360,8));accept(s,event("collected","EMPTY",0,0,9));
            var report=s.performance(account,"ALL",at+20);
            assertEquals(1,report.completedFlips);assertTrue(report.sales.get(0).completed);
            assertEquals(55,report.profitLower);assertEquals(effects,s.positionEventCount());
        }
        try(var s=SqliteStore.openReadOnly(db)){assertEquals(1,s.performance(account,"ALL",at+20).completedFlips);}
    }
    @Test public void rejectedTerminalCannotCountACompletedFlip()throws Exception {
        try(var s=new SqliteStore(folder.newFolder().toPath().resolve("rejected.db"))) {
            seed(s);accept(s,event("progress","SELLING",3,360,6));
            accept(s,event("regressed-terminal","SOLD",3,359,7));
            assertEquals(0,s.performance(account,"ALL",at+20).completedFlips);
            assertFalse(s.performance(account,"ALL",at+20).sales.get(0).completed);
        }
    }
    @Test public void partialCancellationIsASaleButNotACompletedFlip()throws Exception {
        try(var s=new SqliteStore(folder.newFolder().toPath().resolve("cancel.db"))) {
            seed(s);accept(s,event("partial","SELLING",2,240,6));
            accept(s,event("cancel","CANCELLED_SELL",2,240,7));accept(s,event("clear-sale","EMPTY",0,0,8));
            var report=s.performance(account,"ALL",at+20);assertEquals(0,report.completedFlips);
            assertEquals(37,report.profitLower);assertFalse(report.sales.get(0).completed);
        }
    }
    @Test public void restoringScopeRejectsForeignAccountEvidenceAndNeverAdoptsAnotherAccountsCosts()throws Exception {
        try(var s=new SqliteStore(folder.newFolder().toPath().resolve("foreign.db"));
            var legacy=new SqliteStore(folder.newFolder().toPath().resolve("market.db"));var market=new CompanionService(gson,legacy)) {
            seed(s);int before=s.positionEventCount();
            try(var service=market.forAccountStore(s,AccountIdentity.fromProfileKey("other"))) {
                try{service.rehydrate();fail("Foreign accounting mode restored");}catch(IllegalStateException expected){}
            }
            assertEquals(before,s.positionEventCount());assertEquals(299,s.ledgerSnapshot(account).getTradingPositions().get(0).getTotalCost());
        }
    }
    @Test public void aLegacySnapshotCannotDowngradeEstablishedAutomaticAccounting()throws Exception {
        Path db=folder.newFolder().toPath().resolve("downgrade.db");
        try(var s=new SqliteStore(db);var legacy=new SqliteStore(folder.newFolder().toPath().resolve("market.db"));
            var market=new CompanionService(gson,legacy);var service=market.forAccountStore(s,account)) {
            seed(s);service.rehydrate();int snapshots=s.eventCount("ACCOUNT_STATE");
            var old=new AccountSnapshot("old-client",at+6,100000,0,8,8,true,false,0).withAccountId(account);
            try{service.account(old);fail("Legacy snapshot changed accounting mode");}catch(IllegalArgumentException expected){}
            assertEquals(snapshots,s.eventCount("ACCOUNT_STATE"));
            service.offer(event("sale","SOLD",3,360,7));
            assertEquals(55,s.ledgerSnapshot(account).getProfitLower());assertTrue(s.positionProjections().isEmpty());
        }
    }
    @Test public void accountingAuthorityDoesNotDependOnTheCallersInMemoryModeFlag()throws Exception {
        Path db=folder.newFolder().toPath().resolve("authority.db");
        try(var s=new SqliteStore(db)) {
            seed(s);var e=event("sale","SOLD",3,360,7);
            s.recordAndProjectOffer(e,gson.toJson(e),false);
            assertEquals(55,s.ledgerSnapshot(account).getProfitLower());assertEquals(0,s.ledgerSnapshot(account).getUnresolvedSales());
        }
        try(var s=new SqliteStore(db)) {
            s.pruneEvents(0);assertTrue("Accounting mode must survive snapshot retention",s.recordedAutomaticTradeScope(account));
            assertEquals(55,s.ledgerSnapshot(account).getProfitLower());
        }
    }
    @Test public void failedFirstAutomaticCaptureDoesNotLeaveAnUncommittedModeOrAdoptLegacyStock()throws Exception {
        Path db=folder.newFolder().toPath().resolve("mode-rollback.db");
        try(var s=new SqliteStore(db,stage->{if(stage.equals("ACCOUNT_EVENT_RECORDED"))throw new java.io.IOException("fixture failure");})) {
            var capture=new AccountSnapshot("capture",at,100000,0,8,8,true,false,0).withAccountId(account).withAutomaticTradeScope();
            try{s.recordAndReconcileAccount(capture,PositionStateView.from(capture),gson.toJson(capture));fail();}catch(java.io.IOException expected){}
            assertFalse(s.recordedAutomaticTradeScope(account));assertEquals(0,s.eventCount("AUTOMATIC_ACCOUNTING_V1"));assertEquals(0,s.positionEventCount());
        }
        try(var s=new SqliteStore(db)){assertFalse(s.recordedAutomaticTradeScope(account));assertEquals(0,s.eventCount("ACCOUNT_STATE"));}
    }
    @Test public void historicalAutomaticModeSurvivesALaterLegacySnapshotAndRetentionBeforeAMarkerExists()throws Exception {
        Path db=folder.newFolder().toPath().resolve("old-mode.db");
        try(var s=new SqliteStore(db)) {
            var automatic=new AccountSnapshot("auto",at,100000,0,8,8,true,false,0).withAccountId(account).withAutomaticTradeScope();
            var legacy=new AccountSnapshot("legacy",at+1,100000,0,8,8,true,false,0).withAccountId(account);
            s.recordEvent(at,"auto","ACCOUNT_STATE",gson.toJson(automatic));s.recordEvent(at+1,"legacy","ACCOUNT_STATE",gson.toJson(legacy));
            assertTrue(s.recordedAutomaticTradeScope(account));assertEquals(0,s.eventCount("AUTOMATIC_ACCOUNTING_V1"));
            s.pruneEvents(0);assertEquals(1,s.eventCount("ACCOUNT_STATE"));assertEquals(0,s.positionEventCount());
        }
        try(var s=new SqliteStore(db)){assertTrue(s.recordedAutomaticTradeScope(account));assertEquals(0,s.positionEventCount());}
    }
    @Test public void measurementReviewAndFutureMetadataNeverEnterOfferRehydration()throws Exception {
        try(var s=new SqliteStore(folder.newFolder().toPath().resolve("metadata.db"))) {
            var open=event("open","BUYING",0,0,1);var done=event("done","BOUGHT",3,299,2);
            s.recordAndProjectOffer(open,gson.toJson(open),false);s.recordAndProjectOffer(done,gson.toJson(done),false);
            s.recordEvent(at,"session",PerformanceRead.SESSION_EVENT,"{}");s.recordEvent(at,"review",TradeReviewRead.EVENT_TYPE,"{}");
            s.recordEvent(at,"future","FUTURE_METADATA","{}");
            assertEquals(List.of(gson.toJson(open),gson.toJson(done)),s.recentOfferEvents(at));
            assertEquals(1,s.positionEventCount());assertEquals(299,s.ledgerSnapshot(account).getTradingPositions().get(0).getTotalCost());
        }
    }
}
