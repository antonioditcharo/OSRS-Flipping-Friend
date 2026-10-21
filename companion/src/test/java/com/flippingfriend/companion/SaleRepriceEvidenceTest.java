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
}
