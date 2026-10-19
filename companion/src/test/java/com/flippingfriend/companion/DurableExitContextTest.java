package com.flippingfriend.companion;

import com.flippingfriend.core.*;
import com.google.gson.Gson;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.*;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

/** Canonical acceptance/read/restart boundaries; no live files or replayed financial rows. */
public class DurableExitContextTest {
    @Rule public TemporaryFolder folder=new TemporaryFolder();
    final Gson json=new Gson();final String account=AccountIdentity.fromProfileKey("exit-context-fixture");
    static final long AT=1800000000;
    long sequence;
    OfferEvent event(String type,String episode,long start,long when,int total,int filled,long spent,OfferEvent origin) {
        var b=OfferEvent.builder("fixture",when,type).accountId(account).authenticatedObservation(true)
            .eventIdentity("event-"+(++sequence),"session",episode).sequence(sequence).slot(0);
        if(!"EMPTY".equals(type))b.item(561,"Nature rune").buying(type.equals("BUYING")||type.equals("BOUGHT")||type.equals("CANCELLED_BUY"))
            .price(type.equals("SELLING")||type.equals("SOLD")?150:101).quantities(total,filled).spent(spent).firstSeenAt(start);
        var raw=b.build();return origin==null?raw:raw.withBoundPrediction(origin,true);
    }
    OfferEvent open(String episode,long at,AcquisitionExitContext context) {
        var raw=OfferEvent.builder("fixture",at,"BUYING").accountId(account).authenticatedObservation(true)
            .eventIdentity("event-"+(++sequence),"session",episode).sequence(sequence).slot(0).item(561,"Nature rune")
            .buying(true).price(101).quantities(10,0).firstSeenAt(at)
            .recommendation("advice-"+episode,101,10,0,10,.75).predictionEvidence(null,at-1,false).exitContext(context).build();
        return raw.withBoundPrediction(raw,true);
    }
    void accept(SqliteStore store,OfferEvent event)throws Exception {store.recordAndProjectOffer(event,json.toJson(event),true);}
    void clear(SqliteStore store,long at)throws Exception {accept(store,event("EMPTY",null,0,at,0,0,0,null));}
    PositionProjection position(SqliteStore store,long at)throws Exception {return store.ledgerSnapshot(account,at).getTradingPositions().get(0);}
    @Test public void partialBuyAndSaleRetainExactOriginalContextThroughOvernightRestart()throws Exception {
        Path db=folder.newFolder().toPath().resolve("overnight.db");
        OfferEvent open;int effects;
        try(var store=new SqliteStore(db)) {
            clear(store,AT-1);open=open("buy",AT,new AcquisitionExitContext(140,90,480));accept(store,open);
            var partial=event("BUYING","buy",AT,AT+60,10,3,302,open);accept(store,partial);accept(store,partial);
            assertEquals(302,position(store,AT+60).getTotalCost());
            assertEquals(open.getExitContext(),position(store,AT+60).getExitContext());
            accept(store,event("BOUGHT","buy",AT,AT+9*3600,10,10,1007,open));clear(store,AT+9*3600+1);
            accept(store,event("SELLING","sell",AT+9*3600+2,AT+9*3600+2,10,0,0,null));
            accept(store,event("SELLING","sell",AT+9*3600+2,AT+9*3600+3,10,4,600,null));
            var p=position(store,AT+9*3600+3);
            assertEquals(6,p.getQuantity());assertEquals(605,p.getTotalCost());
            assertEquals(open.getExitContext(),p.getExitContext());
            assertNotEquals("Latest financial source is a sale, not the plan source",p.getSourceEventId(),p.getExitContext().getSourceEventId());
            effects=store.positionEventCount();assertEquals(3,effects);
        }
        try(var store=new SqliteStore(db)) {
            var ledger=store.ledgerSnapshot(account,AT+24*3600);assertTrue(ledger.isValid());
            var p=ledger.getTradingPositions().get(0);assertEquals(6,p.getQuantity());assertEquals(605,p.getTotalCost());
            assertEquals(open.getExitContext(),p.getExitContext());assertEquals(effects,store.positionEventCount());
            assertEquals(1,ledger.getSales());assertEquals(186,ledger.getProfitLower());
        }
    }
    @Test public void missingOriginalAndDifferentAcquisitionsNeverBorrowLaterAdvice()throws Exception {
        for(boolean missing:new boolean[]{true,false})try(var store=new SqliteStore(folder.newFolder().toPath().resolve("mixed.db"))) {
            long start=AT;clear(store,start-1);
            var first=open("first",start,missing?null:new AcquisitionExitContext(140,90,480));accept(store,first);
            accept(store,event("BOUGHT","first",start,start+1,10,10,1010,first));clear(store,start+2);
            var second=open("second",start+3,new AcquisitionExitContext(160,80,30));accept(store,second);
            accept(store,event("BOUGHT","second",start+3,start+4,10,10,1010,second));clear(store,start+5);
            var p=position(store,start+5);assertEquals(20,p.getQuantity());assertEquals(2020,p.getTotalCost());
            assertNull("A blended pool cannot assign a different purchase's original plan",p.getExitContext());
            accept(store,event("SELLING","sale",start+6,start+6,20,0,0,null));
            accept(store,event("SELLING","sale",start+6,start+7,20,10,1500,null));
            assertNull(position(store,start+7).getExitContext());assertEquals(1010,position(store,start+7).getTotalCost());
            accept(store,event("SOLD","sale",start+6,start+8,20,20,3000,null));clear(store,start+9);
            var third=open("third",start+10,new AcquisitionExitContext(170,90,60));accept(store,third);
            accept(store,event("BOUGHT","third",start+10,start+11,10,10,1010,third));
            assertEquals(third.getExitContext(),position(store,start+11).getExitContext());
        }
    }
    @Test public void deletingTheOriginalSourceWithdrawsOnlyExitContextWithoutFinancialRewrite()throws Exception {
        Path db=folder.newFolder().toPath().resolve("source.db");
        try(var store=new SqliteStore(db)) {
            clear(store,AT-1);var original=open("buy",AT,new AcquisitionExitContext(140,90,480));accept(store,original);
            accept(store,event("BOUGHT","buy",AT,AT+1,10,10,1010,original));
            assertNotNull(position(store,AT+1).getExitContext());int effects=store.positionEventCount();
            try(var other=DriverManager.getConnection("jdbc:sqlite:"+db);var sql=other.prepareStatement("DELETE FROM event_log WHERE event_id=?")) {
                sql.setString(1,original.getEventId());assertEquals(1,sql.executeUpdate());
            }
            var p=position(store,AT+2);assertNull(p.getExitContext());
            assertEquals(1010,p.getTotalCost());assertEquals(10,p.getQuantity());assertEquals(effects,store.positionEventCount());
        }
    }
    @Test public void lateClaimCannotReplaceAnUnattributedOriginalEvenWithValidLaterFields()throws Exception {
        try(var store=new SqliteStore(folder.newFolder().toPath().resolve("late.db"))) {
            clear(store,AT-1);var first=open("buy",AT,null);accept(store,first);
            var invented=json.toJsonTree(first).getAsJsonObject();invented.add("exitContext",json.toJsonTree(new AcquisitionExitContext(999,90,480).bind(first.getEventId())));
            var later=event("BOUGHT","buy",AT,AT+1,10,10,1010,json.fromJson(invented,OfferEvent.class));accept(store,later);
            assertNull(position(store,AT+1).getExitContext());assertEquals(1010,position(store,AT+1).getTotalCost());
        }
    }
}
