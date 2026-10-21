package com.flippingfriend.companion;

import com.flippingfriend.core.*;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class SaleRepriceEvidenceTest {
    final String account=AccountIdentity.fromProfileKey("source-proof");
    OfferEvent event(String id,String type,int filled,long sequence) {
        var b=OfferEvent.builder("proof",1000+sequence,type).accountId(account).authenticatedObservation(true)
            .eventIdentity(id,"session","EMPTY".equals(type)?null:"episode").slot(0).sequence(sequence);
        if(!"EMPTY".equals(type))b.item(561,"Nature rune").price(150).quantities(10,filled).spent(150L*filled).firstSeenAt(1000);
        return b.build();
    }
    @Test public void lateFillsReduceTheCapWithoutErasingTheAcceptedStallClock() {
        var first=event("first","SELLING",0,1);var selected=event("accepted","SELLING",0,600);
        var late=event("late","SELLING",2,601);var cancel=event("cancel","CANCELLED_SELL",3,602);var clear=event("clear","EMPTY",0,603);
        var evidence=SaleRepriceEvidence.prove(List.of(first,selected,late,cancel,clear),"accepted",account,561,7,2000);
        assertNotNull(evidence);assertEquals(1000,evidence.lastProgressAt);assertEquals(3,evidence.cancelled.getFilledQuantity());
        assertNull(SaleRepriceEvidence.prove(List.of(first,selected,late,cancel,clear),"accepted",account,561,8,2000));
        assertNull(SaleRepriceEvidence.prove(List.of(first,selected,late,cancel),"accepted",account,561,7,2000));
    }
    @Test public void wrongIdentityRejectedCountersMissingCancellationAndSubsequentSalesRefuse() {
        var first=event("accepted","SELLING",0,600);var cancel=event("cancel","CANCELLED_SELL",3,602);var clear=event("clear","EMPTY",0,603);
        assertNull(SaleRepriceEvidence.prove(List.of(first,clear),"accepted",account,561,7,2000));
        assertNull(SaleRepriceEvidence.prove(List.of(first,cancel,clear),"other",account,561,7,2000));
        assertNull(SaleRepriceEvidence.prove(List.of(first,event("fill","SELLING",4,601),cancel,clear),"accepted",account,561,7,2000));
        var newer=OfferEvent.builder("new",1604,"SELLING").accountId(account).authenticatedObservation(true)
            .eventIdentity("new","session","replacement").slot(1).sequence(604).item(561,"Nature rune")
            .price(140).quantities(7,0).spent(0).firstSeenAt(1604).build();
        assertNull(SaleRepriceEvidence.prove(List.of(first,cancel,clear,newer),"accepted",account,561,7,2000));
    }
    OfferEvent parallel(String id,String episode,int price,int filled,long sequence,long observed) {
        return OfferEvent.builder("parallel",observed,"SELLING").accountId(account).authenticatedObservation(true)
            .eventIdentity(id,"session",episode).slot(1).sequence(sequence).item(561,"Nature rune")
            .price(price).quantities(5,filled).spent((long)price*filled).firstSeenAt(1100).build();
    }
    @Test public void anAlreadyPresentSameItemSaleCanProgressWithoutInvalidatingTheCollectedRemainder() {
        var first=event("first","SELLING",0,1);
        var other=parallel("other-first","other-episode",145,0,100,1100);
        var selected=event("accepted","SELLING",0,1000);
        var cancel=event("cancel","CANCELLED_SELL",3,1001);var clear=event("clear","EMPTY",0,1002);
        var progress=parallel("other-progress","other-episode",145,2,1003,2003);
        var proof=SaleRepriceEvidence.prove(List.of(first,other,selected,cancel,clear,progress),"accepted",account,561,7,2100);
        assertNotNull("The second sale already existed when this exact cancellation was accepted",proof);
        assertEquals(3,proof.cancelled.getFilledQuantity());assertEquals(1000,proof.lastProgressAt);
    }
    OfferEvent changed(OfferEvent event,String field,Object value) {
        var gson=new com.google.gson.Gson();var raw=gson.toJsonTree(event).getAsJsonObject();
        raw.add(field,gson.toJsonTree(value));return gson.fromJson(raw,OfferEvent.class);
    }
    List<OfferEvent> chain(OfferEvent... tail) {
        var events=new ArrayList<>(List.of(event("first","SELLING",0,1),parallel("other-first","other-episode",145,0,100,1100),
            event("accepted","SELLING",0,1000),event("cancel","CANCELLED_SELL",3,1001),event("clear","EMPTY",0,1002)));
        events.addAll(List.of(tail));return events;
    }
    @Test public void clearingOrReusingTheParallelSlotPermanentlyRevokesItsExemption() {
        var clear=OfferEvent.builder("clear",2003,"EMPTY").accountId(account).authenticatedObservation(true)
            .eventIdentity("peer-clear","session",null).slot(1).sequence(1003).build();
        var replay=parallel("peer-replay","other-episode",145,0,1004,2004);
        assertNotNull(SaleRepriceEvidence.prove(chain(clear),"accepted",account,561,7,2100));
        assertNull(SaleRepriceEvidence.prove(chain(clear,replay),"accepted",account,561,7,2100));
        var reused=parallel("reused","new-episode",145,0,1004,2004);
        assertNull(SaleRepriceEvidence.prove(chain(clear,reused),"accepted",account,561,7,2100));
        var anotherItem=changed(parallel("other-item","different",145,0,1003,2003),"itemId",562);
        assertNull(SaleRepriceEvidence.prove(chain(anotherItem,replay),"accepted",account,561,7,2100));
    }
    @Test public void peerOrderChangesUnauthenticatedFramesAndRegressingCountersCannotBorrowTheOldExemption() {
        var heartbeat=parallel("heartbeat","other-episode",145,0,1003,2003);
        Map<String,Object> changes=new LinkedHashMap<>();changes.put("offerIdentity","different");changes.put("sessionId","different");
        changes.put("slot",2);changes.put("price",146);changes.put("totalQuantity",6);changes.put("firstSeenAt",1099);
        changes.put("authenticatedObservation",false);changes.put("accountId",AccountIdentity.fromProfileKey("other"));
        for(var change:changes.entrySet())assertNull(change.getKey(),SaleRepriceEvidence.prove(
            chain(changed(heartbeat,change.getKey(),change.getValue())),"accepted",account,561,7,2100));
        var buy=changed(changed(heartbeat,"buying",true),"eventType","BUYING");
        assertNull(SaleRepriceEvidence.prove(chain(buy),"accepted",account,561,7,2100));
        var progress=parallel("peer-fill","other-episode",145,2,1003,2003);
        assertNull(SaleRepriceEvidence.prove(chain(progress,parallel("regress","other-episode",145,1,1004,2004)),"accepted",account,561,7,2100));
        assertNull(SaleRepriceEvidence.prove(chain(progress,changed(parallel("spent-regress","other-episode",145,2,1004,2004),"spent",1)),"accepted",account,561,7,2100));
    }
    @Test public void sameSecondPresenceUsesTheDurablePrefixAndNewSalesBeforeCollectionStillSupersede() {
        var sameSecond=new ArrayList<OfferEvent>();for(var e:chain(parallel("peer-fill","other-episode",145,2,1003,2003)))
            sameSecond.add(changed(e,"observedAt",2000));
        assertNotNull(SaleRepriceEvidence.prove(sameSecond,"accepted",account,561,7,2100));
        var newBeforeCollection=new ArrayList<>(List.of(event("first","SELLING",0,1),event("accepted","SELLING",0,1000),
            parallel("unobserved-before-source","other-episode",145,0,1001,2001),
            event("cancel","CANCELLED_SELL",3,1002),event("clear","EMPTY",0,1003)));
        assertNull("An old firstSeen timestamp alone cannot prove the sale was on the accepted board",
            SaleRepriceEvidence.prove(newBeforeCollection,"accepted",account,561,7,2100));
    }
    @Test public void aLatestInvalidOrUnauthenticatedBaselineCannotResurrectOlderTrustedPresence() {
        var original=parallel("other-first","other-episode",145,0,100,1100);
        for(var invalid:List.of(changed(original,"authenticatedObservation",false),changed(original,"eventType","SOLD"))) {
            invalid=changed(changed(invalid,"eventId","invalid-baseline"),"sequence",101);
            var history=new ArrayList<>(chain(parallel("peer-fill","other-episode",145,2,1003,2003)));
            history.add(2,invalid);
            assertNull(SaleRepriceEvidence.prove(history,"accepted",account,561,7,2100));
        }
    }
}
