package com.flippingfriend.core;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class DisplayedPricingContextTest {
    final String account=AccountIdentity.fromProfileKey("display-context");
    @Test public void displayAssociationUsesTheExistingWindowWithoutCreatingPredictionEvidence() {
        long at=1800000000;
        var context=new DisplayedPricingContext(account,"advice","BUY",561,100,10,at,CaptureOrder.next(),
            null,null,null,null,List.of(),null).bind("event",CaptureOrder.next());
        for(int age:new int[]{0,180,599,600,601}) {
            var event=OfferEvent.builder("c",at+age,"BUYING").accountId(account).eventIdentity("event","s","o")
                .authenticatedObservation(true).slot(0).item(561,"Rune").buying(true).price(100).quantities(10,0)
                .sequence(1).spent(0).firstSeenAt(at+age).build();
            assertEquals(age<=600,context.matches(event));assertFalse(event.isPredictionComparable());assertNull(event.getExecutionForecast());
        }
    }
    @Test public void originalSourceRetainsOptionalContextWithoutChangingFinancialOrPredictionIdentity() {
        var accepted=CaptureOrder.next();
        var context=new DisplayedPricingContext(account,"advice","SELL",4151,100,3,1800000000,accepted,null,"unknown cost",null,
            new DisplayedPricingContext.Configuration("MODERATE",30,60,10000,100000000,false),List.of(),null);
        var bound=context.bind("event",CaptureOrder.next());
        var event=OfferEvent.builder("c",1800000001,"SELLING").accountId(account).eventIdentity("event","session","offer")
            .authenticatedObservation(true).item(4151,"Whip").slot(0).buying(false).price(100).quantities(3,0)
            .sequence(1).spent(0).firstSeenAt(1800000001).displayedPricingContext(bound).build();
        assertTrue(bound.matches(event));assertNull(bound.getExpectedProfit());
        var original=event.withBoundPrediction(event,false);assertSame(bound,original.getDisplayedPricingContext());
        var later=OfferEvent.builder("c",1800000002,"SELLING").accountId(account).eventIdentity("later","session","offer")
            .authenticatedObservation(true).item(4151,"Whip").slot(0).buying(false).price(100).quantities(3,0)
            .sequence(2).spent(0).firstSeenAt(1800000001).displayedPricingContext(bound).build().withBoundPrediction(original,false);
        assertNull(later.getDisplayedPricingContext());assertEquals("later",later.getEventId());assertEquals(2,later.getSequence());
        assertFalse(bound.matches(later));assertFalse(bound.matches(event.withAccountId(AccountIdentity.fromProfileKey("other"))));
        assertEquals("event",event.getEventId());assertEquals(0,event.getSpent());assertNull(event.getExecutionForecast());
        try{context.bind("bad",accepted);fail();}catch(IllegalArgumentException expected){}
        try{bound.bind("other",CaptureOrder.next());fail();}catch(IllegalStateException expected){}
    }
}
