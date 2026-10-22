package com.flippingfriend.companion;

import com.flippingfriend.core.*;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class SaleRepriceSuccessorEvidenceTest {
    final String account=AccountIdentity.fromProfileKey("parallel-sale-successors");long sequence;
    OfferEvent sale(String id,String episode,int slot,int total,int filled,int price,long first,long observed,String type) {
        var b=OfferEvent.builder("fixture",observed,type).accountId(account).authenticatedObservation(true)
            .eventIdentity(id,"session",episode).sequence(++sequence).slot(slot);
        if(!"EMPTY".equals(type))b.item(561,"Nature rune").price(price).quantities(total,filled).spent((long)price*filled).firstSeenAt(first);
        return b.build();
    }
    List<OfferEvent> originals(int cancelledA) {
        return new ArrayList<>(List.of(sale("a-first","a",0,100,20,170,1000,1000,"SELLING"),
            sale("b-first","b",1,100,30,200,1100,1100,"SELLING"),
            sale("a-accepted","a",0,100,20,170,1000,1600,"SELLING"),
            sale("b-accepted","b",1,100,30,200,1100,1601,"SELLING"),
            sale("a-cancel","a",0,100,cancelledA,170,1000,1602,"CANCELLED_SELL"),
            sale("b-cancel","b",1,100,30,200,1100,1603,"CANCELLED_SELL"),
            sale("a-empty",null,0,0,0,0,0,1604,"EMPTY"),sale("b-empty",null,1,0,0,0,0,1605,"EMPTY")));
    }
    OfferEvent successor(String origin,String source,int quantity,int slot,String episode,long at) {
        return successor(origin,source,quantity,slot,episode,at,"advice-"+episode,CaptureOrder.next());
    }
    OfferEvent successor(String origin,String source,int quantity,int slot,String episode,long at,String advice,CaptureOrder accepted) {
        var e=sale(episode+"-first",episode,slot,quantity,0,150,at,at,"SELLING");
        var proof=ContinuationSuccessorProof.accepted(account,origin,source,advice,561,false,150,quantity,at-1,accepted)
            .captured(CaptureOrder.next()).bindTo(e);
        assertNotNull(proof);return e.withContinuationSuccessorProof(proof);
    }
    SaleRepriceEvidence prove(List<OfferEvent> events,String source,int quantity) {
        return SaleRepriceEvidence.prove(events,source,account,561,quantity,10000);
    }
    @Test public void aProvenDistinctSuccessorPreservesTheOtherRootButConsumesOnlyItsOwnBudget() {
        var history=originals(20);var child=successor("a","a-accepted",50,0,"child-a",1606);history.add(child);
        history.add(sale("child-progress","child-a",0,50,7,150,1606,1607,"SELLING")
            .withContinuationSuccessorProof(child.getContinuationSuccessorProof()));
        assertEquals(70,prove(history,"b-accepted",70).remaining);
        assertEquals(30,prove(history,"a-accepted",30).remaining);assertNull(prove(history,"a-accepted",31));
        var other=successor("b","b-accepted",70,1,"child-b",1608);history.add(other);
        assertNotNull(prove(history,"a-accepted",30));assertNull(prove(history,"b-accepted",1));
        assertEquals(2,SaleRepriceEvidence.qualifiedSuccessors(history,account,561,10000).size());
    }
    @Test public void lateOriginalFillsAndOversizedOrUnboundChildrenCannotBorrowAPeerExemption() {
        var late=originals(25);late.add(successor("a","a-accepted",80,0,"too-large",1606));
        assertNull(prove(late,"b-accepted",70));
        var unbound=originals(20);unbound.add(sale("unknown","unknown",0,80,0,150,1606,1606,"SELLING"));
        assertNull(prove(unbound,"b-accepted",70));
        var wrong=originals(20);wrong.add(successor("a","b-accepted",80,0,"wrong-source",1606));
        assertNull(prove(wrong,"b-accepted",70));
    }
    @Test public void cancellingAChildDoesNotRefundItsRootAndGrandchildrenChargeOnlyTheImmediateParent() {
        var history=originals(20);var child=successor("a","a-accepted",50,0,"child-a",1606);history.add(child);
        var accepted=sale("child-accepted","child-a",0,50,0,150,1606,2206,"SELLING")
            .withContinuationSuccessorProof(child.getContinuationSuccessorProof());history.add(accepted);
        history.add(sale("child-cancel","child-a",0,50,10,150,1606,2207,"CANCELLED_SELL")
            .withContinuationSuccessorProof(child.getContinuationSuccessorProof()));
        history.add(sale("child-empty",null,0,0,0,0,0,2208,"EMPTY"));
        history.add(successor("child-a","child-accepted",40,0,"grandchild",2209));
        assertEquals(30,prove(history,"a-accepted",30).remaining);
        assertNotNull(prove(history,"b-accepted",70));assertNull(prove(history,"child-accepted",1));
        var over=new ArrayList<>(history.subList(0,history.size()-1));
        over.add(successor("a","a-accepted",40,0,"root-refund",2209));
        assertNull(prove(over,"b-accepted",70));
    }
    @Test public void oneAcceptedOrderCannotBeReusedForAnotherEpisodeEvenWhenTheCombinedSizeFits() {
        var history=originals(20);var accepted=CaptureOrder.next();
        history.add(successor("a","a-accepted",20,0,"first-claim",1606,"same-advice",accepted));
        history.add(successor("a","a-accepted",20,2,"reused-claim",1607,"same-advice",accepted));
        assertNull(SaleRepriceEvidence.qualifiedSuccessors(history,account,561,10000));
        assertNull(prove(history,"b-accepted",70));
    }
    @Test public void aLaterAcceptanceCannotMatureATooYoungParentCancellationSource() {
        var history=originals(20);var gson=new com.google.gson.Gson();
        var young=gson.toJsonTree(history.get(2)).getAsJsonObject();young.addProperty("observedAt",1101);
        history.set(2,gson.fromJson(young,OfferEvent.class));
        history.add(successor("a","a-accepted",80,0,"later-child",1606));
        assertNotNull("A chain alone does not prove original maintenance patience",prove(history,"b-accepted",70));
        assertNull("Actual authority uses the parent's original source clock, not later acceptance time",
            SaleRepriceEvidence.prove(history,"b-accepted",account,561,70,10000,480));
    }
}
