package com.flippingfriend.companion;

import com.flippingfriend.core.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class SaleDecisionAuthorityTest {
    PolicyDecision decision(String identity,int slot,long now) {
        return PolicyDecision.action("1","companion-sale-4","decision",slot<0?PolicyDecisionType.EXIT:PolicyDecisionType.SELL_MAINTENANCE,
            slot<0?OfferLifecycleAction.PLACE_SELL:OfferLifecycleAction.CANCEL_SELL,"SALE_AUTHORIZED",now,now-1,identity,"source");
    }
    @Test public void exactOrderSourceTypeAndFreshDecisionAreRequired() {
        var decision=decision("561:120:3:source:false:-1",-1,100);
        assertTrue(CompanionClient.validSaleDecision(decision,"561:120:3:source:false:-1","source",-1,100));
        assertFalse(CompanionClient.validSaleDecision(decision,"561:120:4:source:false:-1","source",-1,100));
        assertFalse(CompanionClient.validSaleDecision(decision,"561:120:3:source:false:-1","other-source",-1,100));
        assertFalse(CompanionClient.validSaleDecision(decision,"561:120:3:source:false:-1","source",0,100));
        assertFalse(CompanionClient.validSaleDecision(decision,"561:120:3:source:false:-1","source",-1,101));
    }
    @Test public void oldItemContextSemanticsCannotAuthorizeAnAcquisitionBoundOrder() {
        for(String version:new String[]{"companion-sale-1","companion-sale-2","companion-sale-3"}) {
        var old=PolicyDecision.action("1",version,"old",PolicyDecisionType.EXIT,OfferLifecycleAction.PLACE_SELL,
            "SALE_AUTHORIZED",100,99,"order","source");
        assertFalse(CompanionClient.validSaleDecision(old,"order","source",-1,100));
        }
    }
}
