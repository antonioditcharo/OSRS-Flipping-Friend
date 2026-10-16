package com.flippingfriend.companion;

import com.flippingfriend.core.*;
import com.google.gson.Gson;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Frozen synthetic prefixes only. An observation audit never authorizes accounting or trading. */
public class TradeEvidenceAuditTest {
    final Gson json=new Gson();final String account=AccountIdentity.fromProfileKey("audit");
    final String manifest="a".repeat(64);final long at=1700000000;
    OfferEvent event(String id,String type,int filled,long spent,int sequence) {
        var b=OfferEvent.builder("fixture",at+sequence,type).accountId(account).authenticatedObservation(true)
            .eventIdentity(id,"session",type.equals("EMPTY")?null:"episode").slot(0).sequence(sequence);
        if(!type.equals("EMPTY"))b.item(561,"Nature rune").buying(true).price(1000).quantities(9,filled).spent(spent).firstSeenAt(at);
        return b.build();
    }
    ExecutionSourceSnapshot source(List<OfferEvent> events,List<ExecutionSourceSnapshot.Gap> gaps) {
        List<ExecutionSourceSnapshot.Row> rows=new ArrayList<>();
        for(var e:events)rows.add(new ExecutionSourceSnapshot.Row(rows.size()+1,e.getObservedAt(),e.getEventId(),e.getEventType(),json.toJson(e)));
        return new ExecutionSourceSnapshot(account,at+100,rows,gaps);
    }
    TradeEvidenceAudit audit(OfferEvent... events) {return TradeEvidenceAudit.analyze(source(List.of(events),List.of()),manifest);}
    @Test public void completeCountersAreObservedOnceAndNeverBecomeAHoldingOrProfitClaim() {
        var report=audit(event("open","BUYING",0,0,1),event("partial","BUYING",2,1999,2),
            event("full","BUYING",9,8991,3),event("done","BOUGHT",9,8991,4),event("clear","EMPTY",0,0,5));
        assertFalse(report.authorizesAccounting);assertFalse(report.authorizesTrading);
        assertEquals(1,report.identities.size());var row=report.identities.get(0);
        assertTrue(row.flags.isEmpty());assertTrue(row.observedZeroStart);
        assertEquals(Integer.valueOf(9),row.maxObservedCumulativeFilled);
        assertEquals(Long.valueOf(8991),row.maxObservedCumulativeSpent);
        assertEquals(1,row.clears.size());assertEquals("clear",row.clears.get(0).eventId);
        var wire=json.toJsonTree(report).getAsJsonObject();
        assertFalse(wire.has("positions"));assertFalse(wire.has("profit"));assertFalse(wire.has("recoveredCost"));
    }
    @Test public void terminalAfterClearWithSameIdentityIsAmbiguousRatherThanAnotherPurchase() {
        var report=audit(event("open","BUYING",0,0,1),event("done","BOUGHT",9,8991,2),
            event("clear","EMPTY",0,0,3),event("repeated","BOUGHT",9,8991,4));
        var row=report.identities.get(0);
        assertTrue(row.flags.contains("IDENTITY_REAPPEARED_AFTER_EMPTY"));
        assertEquals(Integer.valueOf(9),row.maxObservedCumulativeFilled);
        assertEquals(Long.valueOf(8991),row.maxObservedCumulativeSpent);
        assertTrue(row.problems.stream().anyMatch(r->r.eventId.equals("repeated")));
    }
    @Test public void initialFilledBaselineAndMissingTerminalRemainExplicit() {
        var row=audit(event("first","BUYING",2,1999,1),event("clear","EMPTY",0,0,2)).identities.get(0);
        assertTrue(row.flags.contains("INITIAL_FILLED_BASELINE"));
        assertTrue(row.flags.contains("EMPTY_WITHOUT_TERMINAL"));assertFalse(row.observedZeroStart);
    }
    @Test public void cancellationCannotMakeLaterActiveCountersAProvenNewFill() {
        var row=audit(event("open","BUYING",0,0,1),event("cancel","CANCELLED_BUY",2,1999,2),
            event("active-again","BUYING",3,2998,3)).identities.get(0);
        assertTrue(row.flags.contains("TERMINAL_TO_ACTIVE"));
        assertTrue(row.problems.stream().anyMatch(r->r.eventId.equals("active-again")));
    }
    @Test public void regressedMoneyOrCounterIsNotSilentlyNormalized() {
        var row=audit(event("open","BUYING",0,0,1),event("partial","BUYING",4,4000,2),
            event("regressed","BUYING",3,3000,3)).identities.get(0);
        assertTrue(row.flags.contains("COUNTER_REGRESSION"));
        assertEquals(Integer.valueOf(4),row.maxObservedCumulativeFilled);
    }
    @Test public void contradictoryIdentityDoesNotSumOrCompareDifferentItems() {
        var first=event("open","BUYING",0,0,1);var wire=json.toJsonTree(event("other-item","BUYING",2,1999,2)).getAsJsonObject();
        wire.addProperty("itemId",562);wire.addProperty("itemName","Chaos rune");
        var row=audit(first,json.fromJson(wire,OfferEvent.class)).identities.get(0);
        assertTrue(row.flags.contains("CONFLICTING_METADATA"));assertEquals(Set.of(561,562),row.itemIds);
        assertNull(row.maxObservedCumulativeFilled);assertNull(row.maxObservedCumulativeSpent);
    }
    @Test public void originalRecordedGapAndItsSourceRemainAttached() {
        var events=List.of(event("open","BUYING",0,0,1),event("full","BUYING",9,8991,2),event("done","BOUGHT",9,8991,3));
        var gap=new ExecutionSourceSnapshot.Gap(1,at+2,"full","episode",561);
        var row=TradeEvidenceAudit.analyze(source(events,List.of(gap)),manifest).identities.get(0);
        assertTrue(row.flags.contains("RECORDED_RECONCILIATION"));
        assertEquals(List.of("full"),row.recordedGapSourceIds);
    }
    @Test public void duplicateCanonicalIdentityAndForeignAccountAreRejected() {
        var e=event("one","BUYING",0,0,1);
        try{audit(e,e);fail("duplicate canonical source");}catch(IllegalArgumentException expected){}
        var body=json.toJsonTree(e).getAsJsonObject();body.addProperty("accountId",AccountIdentity.fromProfileKey("foreign"));
        try{audit(json.fromJson(body,OfferEvent.class));fail("foreign source");}catch(IllegalArgumentException expected){}
    }
    @Test public void cleanZeroFillNewIdentityAfterClearRemainsSeparate() {
        var a=event("open","BUYING",0,0,1);var b=event("cancel","CANCELLED_BUY",0,0,2);var clear=event("clear","EMPTY",0,0,3);
        var wire=json.toJsonTree(event("new","BUYING",0,0,4)).getAsJsonObject();wire.addProperty("offerIdentity","new-episode");
        var report=audit(a,b,clear,json.fromJson(wire,OfferEvent.class));assertEquals(2,report.identities.size());
        for(var row:report.identities)assertTrue(row.flags.isEmpty());
        assertFalse(report.authorizesAccounting);assertFalse(report.authorizesTrading);
    }
}
