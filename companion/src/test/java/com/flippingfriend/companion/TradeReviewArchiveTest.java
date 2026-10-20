package com.flippingfriend.companion;

import com.flippingfriend.core.*;
import com.google.gson.Gson;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

/** Annotation-only cleanup; all monetary and ownership assertions use canonical acceptance. */
public class TradeReviewArchiveTest {
    @Rule public TemporaryFolder folder=new TemporaryFolder();
    final String account=AccountIdentity.fromProfileKey("review");final Gson gson=new Gson();
    final long at=Instant.now().getEpochSecond()-100;long sequence;
    OfferEvent event(String id,String offer,String type,int item,int total,int filled,long spent) {
        var b=OfferEvent.builder("fixture",at+(++sequence),type).accountId(account).authenticatedObservation(true)
            .eventIdentity(id,"session",offer).slot(0).sequence(sequence);
        if(!"EMPTY".equals(type))b.item(item,"Nature rune").buying(type.equals("BUYING")||type.equals("BOUGHT"))
            .price(type.equals("BUYING")||type.equals("BOUGHT")?100:120).quantities(total,filled).spent(spent);
        return b.build();
    }
    void accept(SqliteStore s,OfferEvent e)throws Exception{s.recordAndProjectOffer(e,gson.toJson(e),true);}
    void unpricedSale(SqliteStore s,String id,int item,boolean clear)throws Exception {
        accept(s,event(id+"-open",id,"SELLING",item,3,0,0));
        accept(s,event(id,id,"SOLD",item,3,3,360));
        if(clear)accept(s,event(id+"-empty",null,"EMPTY",0,0,0,0));
    }
    TradeReviewSnapshot.Request request(TradeReviewSnapshot snapshot,String id,boolean archive,String... sources) {
        return new TradeReviewSnapshot.Request(account,id,snapshot.positionCursor,archive,List.of(sources));
    }
    @Test public void closedUnmatchedSalesArchiveAndRestoreAcrossRestartWithoutChangingMoneyOrRisk()throws Exception {
        Path db=folder.newFolder().toPath().resolve("archive.db");long effects;String ledgerBefore;
        try(var s=new SqliteStore(db)) {
            unpricedSale(s,"old-sale",561,true);s.recordEvent(at,"risk",RiskWindow.EVENT_TYPE,"{\"original\":true}");
            var before=s.tradeReview(account,at+50);assertEquals(TradeReviewSnapshot.ELIGIBLE,before.states.get("old-sale"));
            effects=s.positionEventCount();ledgerBefore=gson.toJson(s.ledgerSnapshot(account,at+50));
            var archive=request(before,"archive-once",true,"old-sale");s.reviewTrades(account,archive,at+50);s.reviewTrades(account,archive,at+51);
            assertEquals(1,s.eventCount(TradeReviewRead.EVENT_TYPE));assertEquals(effects,s.positionEventCount());
            assertEquals(ledgerBefore,gson.toJson(s.ledgerSnapshot(account,at+50)));assertEquals(1,s.eventCount(RiskWindow.EVENT_TYPE));
            var report=s.performance(account,"ALL",at+50);assertEquals(1,report.unresolvedSales);assertEquals(1,report.archivedUnresolvedSales);
            assertTrue(report.sales.get(0).archived);assertNull(report.sales.get(0).profitLower);assertEquals(0,report.profitLower);
        }
        try(var s=new SqliteStore(db)) {
            var archived=s.tradeReview(account,at+60);assertEquals(TradeReviewSnapshot.ARCHIVED,archived.states.get("old-sale"));
            s.reviewTrades(account,request(archived,"restore",false,"old-sale"),at+60);
            assertEquals(TradeReviewSnapshot.ELIGIBLE,s.tradeReview(account,at+60).states.get("old-sale"));
            assertEquals(0,s.performance(account,"ALL",at+60).archivedUnresolvedSales);
            assertEquals(effects,s.positionEventCount());assertEquals(ledgerBefore,gson.toJson(s.ledgerSnapshot(account,at+50)));
        }
    }
    @Test public void incompleteClosureAndRetainedAcquisitionsCannotBeArchived()throws Exception {
        try(var s=new SqliteStore(folder.newFolder().toPath().resolve("guards.db"))) {
            unpricedSale(s,"uncollected",561,false);var open=s.tradeReview(account,at+50);
            assertEquals(TradeReviewSnapshot.OPEN,open.states.get("uncollected"));
            try{s.reviewTrades(account,request(open,"bad",true,"uncollected"),at+50);fail();}catch(IllegalArgumentException expected){}
            accept(s,event("clear",null,"EMPTY",0,0,0,0));
            accept(s,event("buy-open","buy","BUYING",562,2,0,0));accept(s,event("bought","buy","BOUGHT",562,2,2,199));
            accept(s,event("buy-empty",null,"EMPTY",0,0,0,0));unpricedSale(s,"mixed-sale",562,true);
            var review=s.tradeReview(account,at+60);assertEquals(TradeReviewSnapshot.RETAINED,review.states.get("mixed-sale"));
            assertNull(s.ledgerSnapshot(account).getRecentSales().get(0).getProfitLower());
            try{s.reviewTrades(account,request(review,"bad-retained",true,"mixed-sale"),at+60);fail();}catch(IllegalArgumentException expected){}
            assertEquals(0,s.eventCount(TradeReviewRead.EVENT_TYPE));
        }
    }
    @Test public void newFailuresAreVisibleAndNewStockReopensAnArchivedItemsReview()throws Exception {
        try(var s=new SqliteStore(folder.newFolder().toPath().resolve("future.db"))) {
            unpricedSale(s,"old",561,true);var before=s.tradeReview(account,at+50);
            s.reviewTrades(account,request(before,"archive",true,"old"),at+50);
            unpricedSale(s,"new",562,true);var review=s.tradeReview(account,at+60);
            assertEquals(TradeReviewSnapshot.ELIGIBLE,review.states.get("new"));assertFalse(review.archivedSources.contains("new"));
            accept(s,event("buy-open","buy","BUYING",561,2,0,0));accept(s,event("bought","buy","BOUGHT",561,2,2,200));
            review=s.tradeReview(account,at+70);assertEquals(TradeReviewSnapshot.OPEN,review.states.get("old"));
            assertTrue(review.archivedSources.contains("old"));assertTrue(review.effectiveArchived().isEmpty());
            assertEquals(0,s.performance(account,"ALL",at+70).archivedUnresolvedSales);
        }
    }
    @Test public void staleFutureForeignAndChangedRequestIdentitiesCannotArchiveOtherRecords()throws Exception {
        try(var s=new SqliteStore(folder.newFolder().toPath().resolve("identities.db"))) {
            unpricedSale(s,"one",561,true);var before=s.tradeReview(account,at+50);unpricedSale(s,"two",562,true);
            var oldCursor=request(before,"old-cursor",true,"two");
            try{s.reviewTrades(account,oldCursor,at+60);fail();}catch(IllegalArgumentException expected){}
            var current=s.tradeReview(account,at+60);var archive=request(current,"same-id",true,"one");s.reviewTrades(account,archive,at+60);
            var changed=request(current,"same-id",true,"two");try{s.reviewTrades(account,changed,at+60);fail();}catch(IllegalArgumentException expected){}
            var foreign=request(current,"foreign",true,"two");foreign.accountId=AccountIdentity.fromProfileKey("other");
            try{s.reviewTrades(account,foreign,at+60);fail();}catch(IllegalArgumentException expected){}
            var future=request(current,"future",true,"two");future.positionCursor++;
            try{s.reviewTrades(account,future,at+60);fail();}catch(IllegalArgumentException expected){}
            assertEquals(Set.of("one"),s.tradeReview(account,at+60).archivedSources);assertEquals(1,s.eventCount(TradeReviewRead.EVENT_TYPE));
        }
    }
    @Test public void failedArchiveCommitLeavesOriginalEvidenceVisibleAndRetryUsesTheSameIdentity()throws Exception {
        Path db=folder.newFolder().toPath().resolve("rollback.db");java.util.concurrent.atomic.AtomicBoolean fail=new java.util.concurrent.atomic.AtomicBoolean();
        try(var s=new SqliteStore(db,stage->{if(fail.get() && stage.equals("TRADE_REVIEW_RECORDED"))throw new java.io.IOException("fixture failure");})) {
            unpricedSale(s,"sale",561,true);var before=s.tradeReview(account,at+50);var request=request(before,"retry",true,"sale");
            int effects=s.positionEventCount();fail.set(true);
            try{s.reviewTrades(account,request,at+50);fail();}catch(java.io.IOException expected){}
            assertEquals(0,s.eventCount(TradeReviewRead.EVENT_TYPE));assertTrue(s.tradeReview(account,at+50).archivedSources.isEmpty());
            fail.set(false);s.reviewTrades(account,request,at+50);assertEquals(1,s.eventCount(TradeReviewRead.EVENT_TYPE));
            assertEquals(effects,s.positionEventCount());assertEquals(1,s.ledgerSnapshot(account).getUnresolvedSales());
        }
    }
}
