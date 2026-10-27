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
            assertEquals(2,p.getExitContexts().size());
            assertEquals(first.getExitContext(),p.getExitContexts().get(0).getContext());
            assertEquals(second.getExitContext(),p.getExitContexts().get(1).getContext());
            accept(store,event("SELLING","sale",start+6,start+6,20,0,0,null));
            accept(store,event("SELLING","sale",start+6,start+7,20,10,1500,null));
            assertNull(position(store,start+7).getExitContext());assertEquals(1010,position(store,start+7).getTotalCost());
            for(var slice:position(store,start+7).getExitContexts()) {
                assertEquals(0,slice.getMinimumRemaining());assertEquals(10,slice.getMaximumRemaining());
            }
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
    @Test public void sameSecondAcceptedExitIsCanonicalAcrossFillsAndRestartWithoutLearningPair()throws Exception {
        Path db=folder.newFolder().toPath().resolve("same-second.db");OfferEvent original;
        try(var store=new SqliteStore(db)) {
            clear(store,AT-1);
            var advice=CaptureOrder.next();var capture=CaptureOrder.next();
            var raw=OfferEvent.builder("fixture",AT,"BUYING").accountId(account).authenticatedObservation(true)
                .eventIdentity("event-"+(++sequence),"session","buy").sequence(sequence).slot(0).item(561,"Nature rune")
                .buying(true).price(101).quantities(10,0).firstSeenAt(AT)
                .recommendation("advice",101,10,0,0,0).predictionEvidence(null,AT,false)
                .acceptedAdviceProof(new AcceptedAdviceProof(advice,capture)).exitContext(new AcquisitionExitContext(140,90,480)).build();
            original=raw.withBoundPrediction(raw,false);accept(store,original);
            assertFalse(original.isPredictionComparable());
            accept(store,event("BUYING","buy",AT,AT+1,10,3,303,original));
            assertEquals(original.getExitContext(),position(store,AT+1).getExitContext());
        }
        try(var store=new SqliteStore(db)) {
            accept(store,event("BOUGHT","buy",AT,AT+3600,10,10,1010,original));
            var p=position(store,AT+3600);
            assertEquals(1010,p.getTotalCost());assertEquals(original.getExitContext(),p.getExitContext());
        }
    }
    @Test public void replacementOriginsRetainRangesAndExactPooledCostAfterPartialDisposals()throws Exception {
        var scope=new AutomaticTradeScope();
        var first=new AcquisitionExitContext(1889,1685,66.55).bind("original");
        var child=new AcquisitionExitContext(1899,1695,40.17).bind("replacement");
        scope.apply("ACQUIRE",21948,"Bolts",550,975700,AT,"fill1","buy1",null,first);
        scope.apply("ACQUIRE",21948,"Bolts",607,1082888,AT+1,"fill2","buy2",null,child);
        var p=scope.positions.get(21948);assertEquals(2058588,p.getTotalCost());assertEquals(2,p.getExitContexts().size());
        assertNull(p.getExitContext());
        scope.apply("DISPOSE",21948,"Bolts",350,0,AT+2,"sale1","sell",null,null);
        p=scope.positions.get(21948);assertEquals(807,p.getQuantity());assertEquals(1435852,p.getTotalCost());
        assertEquals(200,p.getExitContexts().get(0).getMinimumRemaining());assertEquals(550,p.getExitContexts().get(0).getMaximumRemaining());
        assertEquals(257,p.getExitContexts().get(1).getMinimumRemaining());assertEquals(607,p.getExitContexts().get(1).getMaximumRemaining());
        scope.apply("DISPOSE",21948,"Bolts",807,0,AT+3,"sale2","sell",null,null);
        scope.apply("ACQUIRE",21948,"Bolts",1,1800,AT+4,"fill3","buy3",null,child);
        assertEquals(1,scope.positions.get(21948).getExitContexts().size());
        assertEquals(child,scope.positions.get(21948).getExitContext());
    }
    OfferEvent independentOpen(String episode,String adviceId,long first,long captured,int slot) {
        var raw=OfferEvent.builder("fixture",captured,"BUYING").accountId(account).authenticatedObservation(true)
            .eventIdentity("event-"+(++sequence),"session",episode).sequence(sequence).slot(slot).item(561,"Nature rune")
            .buying(true).price(101).quantities(10,0).firstSeenAt(first).build();
        var accepted=AcceptedBuyPlanProof.accepted(account,adviceId,561,101,10,first,CaptureOrder.next(),
            new AcquisitionExitContext(140,90,480),null);
        return raw.withAcceptedBuyPlanProof(accepted.captured(CaptureOrder.next()).bindTo(raw));
    }
    OfferEvent independentFill(OfferEvent origin,long when,int filled,long spent) {
        return event(filled==10?"BOUGHT":"BUYING",origin.getOfferIdentity(),origin.getFirstSeenAt(),when,10,filled,spent,null)
            .withAcceptedBuyPlanProof(origin.getAcceptedBuyPlanProof());
    }
    @Test public void conflictingLaterOriginalWithdrawsCachedContextWithoutANewEconomicEffect()throws Exception {
        Path db=folder.newFolder().toPath().resolve("cached-conflict.db");
        try(var store=new SqliteStore(db)) {
            for(int item:new int[]{562,563})accept(store,OfferEvent.builder("fixture",AT-20+item-562,"BUYING")
                .accountId(account).authenticatedObservation(true).eventIdentity("gap-"+item,"session","gap")
                .slot(7).sequence(++sequence).item(item,"Unrelated gap").buying(true).price(100).quantities(10,0).build());
            clear(store,AT-1);var original=independentOpen("first","same-advice",AT,AT+1,0);accept(store,original);
            accept(store,independentFill(original,AT+2,10,1010));clear(store,AT+3);
            var before=store.planningProjections(account).tradeScope.positions.get(561);assertNotNull(before.getExitContext());
            int effects=store.positionEventCount();
            accept(store,independentOpen("conflicting","same-advice",AT+4,AT+5,0));
            assertEquals("An unfilled new order has no accounting delta",effects,store.positionEventCount());
            var warm=store.planningProjections(account).tradeScope.positions.get(561);
            assertNull("A duplicate prospective claim revokes context immediately",warm.getExitContext());
            assertEquals(10,warm.getQuantity());assertEquals(1010,warm.getTotalCost());
            try(var cold=SqliteStore.openReadOnly(db)) {
                assertEquals(json.toJsonTree(cold.planningProjections(account).tradeScope.positions),
                    json.toJsonTree(store.planningProjections(account).tradeScope.positions));
            }
        }
    }
    @Test public void independentlyAcceptedPlanSurvivesPartialFillsRestartAndSaleWithoutLearningOrFinancialRewrite()throws Exception {
        Path db=folder.newFolder().toPath().resolve("independent.db");OfferEvent origin;
        try(var store=new SqliteStore(db)) {
            clear(store,AT-1);origin=independentOpen("buy","fresh-independent",AT,AT+1,0);accept(store,origin);
            assertFalse(origin.wasRecommended());assertFalse(ExecutionObservationLedger.qualifiesOriginal(origin));
            var partial=independentFill(origin,AT+60,3,302);accept(store,partial);accept(store,partial);
            assertEquals(302,position(store,AT+60).getTotalCost());
            assertEquals(origin.getExitContext(),position(store,AT+60).getExitContext());assertEquals(1,store.positionEventCount());
        }
        try(var store=new SqliteStore(db)) {
            accept(store,independentFill(origin,AT+3600,10,1007));clear(store,AT+3601);
            accept(store,event("SELLING","sale",AT+3602,AT+3602,10,0,0,null));
            accept(store,event("SELLING","sale",AT+3602,AT+3603,10,4,600,null));
            var p=position(store,AT+3603);assertEquals(6,p.getQuantity());assertEquals(605,p.getTotalCost());
            assertEquals(origin.getExitContext(),p.getExitContext());assertEquals(3,store.positionEventCount());
            assertEquals(186,store.ledgerSnapshot(account,AT+3603).getProfitLower());
        }
    }
    @Test public void independentPlanCannotRepairMissingOriginalOrBorrowAnotherEpisodesAcceptance()throws Exception {
        Path db=folder.newFolder().toPath().resolve("missing-independent.db");
        try(var store=new SqliteStore(db)) {
            clear(store,AT-1);var original=independentOpen("buy","independent",AT,AT+1,0);accept(store,original);
            accept(store,independentFill(original,AT+2,10,1010));assertNotNull(position(store,AT+2).getExitContext());
            int effects=store.positionEventCount();
            try(var other=DriverManager.getConnection("jdbc:sqlite:"+db);var sql=other.prepareStatement("DELETE FROM event_log WHERE event_id=?")) {
                sql.setString(1,original.getEventId());assertEquals(1,sql.executeUpdate());
            }
            assertNull(position(store,AT+3).getExitContext());assertEquals(effects,store.positionEventCount());
            assertEquals(1010,position(store,AT+3).getTotalCost());
        }
        var first=independentOpen("one","same-uuid",AT,AT+1,0);
        var second=independentOpen("two","same-uuid",AT+2,AT+3,1);
        assertTrue(AcquisitionExitEvidence.read(List.of(first,second)).isEmpty());
        var unbound=first.withAcceptedBuyPlanProof(null);
        var wire=json.toJsonTree(unbound).getAsJsonObject();wire.remove("exitContext");
        var unknown=json.fromJson(wire,OfferEvent.class);
        var late=independentFill(first,AT+5,10,1010);
        assertTrue("Later valid-looking plan cannot replace an unqualified first source",
            AcquisitionExitEvidence.read(List.of(unknown,late)).isEmpty());
    }
    @Test public void versionedUnknownOrMalformedPlanCannotFallbackToLegacyContextOrLearning()throws Exception {
        var legacy=open("legacy",AT,new AcquisitionExitContext(140,90,480));
        assertNotNull(AcquisitionExitEvidence.read(List.of(legacy)).get(legacy.getEventId()));
        for(String version:new String[]{"1","2"}) {
            var versioned=legacy.withAcceptedBuyPlanEvidenceVersion(version);
            assertTrue(AcquisitionExitEvidence.read(List.of(versioned)).isEmpty());
            if("1".equals(version))assertEquals("Strict original prediction retains timing only",10,
                BuyMaintenanceTiming.originalExpectedMinutes(versioned),0);
            else assertTrue(Double.isNaN(BuyMaintenanceTiming.originalExpectedMinutes(versioned)));
        }
        var independent=independentOpen("new","new-proof",AT,AT+1,0);
        var wire=json.toJsonTree(independent).getAsJsonObject();
        wire.getAsJsonObject("acceptedBuyPlanProof").addProperty("formatVersion",2);
        var invalid=json.fromJson(wire,OfferEvent.class);
        assertFalse(invalid.getAcceptedBuyPlanProof().isWellFormed());
        assertTrue(AcquisitionExitEvidence.read(List.of(invalid)).isEmpty());
        var withdrawn=legacy.withoutAcceptedExitContext();
        assertNull(withdrawn.getExitContext());assertNull(withdrawn.getAcceptedAdviceProof());
        assertEquals(legacy.getEventId(),withdrawn.getEventId());assertEquals(legacy.getRecommendationId(),withdrawn.getRecommendationId());
        assertEquals(legacy.isPredictionComparable(),withdrawn.isPredictionComparable());
    }
}
