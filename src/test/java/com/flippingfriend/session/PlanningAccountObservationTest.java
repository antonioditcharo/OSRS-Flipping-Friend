package com.flippingfriend.session;

import com.flippingfriend.core.AccountIdentity;
import java.util.*;
import net.runelite.api.*;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class PlanningAccountObservationTest {
    final String account=AccountIdentity.fromProfileKey("snapshot");
    GrandExchangeOffer empty(){var e=mock(GrandExchangeOffer.class);when(e.getState()).thenReturn(GrandExchangeOfferState.EMPTY);return e;}
    GrandExchangeOffer[] slots(){GrandExchangeOffer[] a=new GrandExchangeOffer[8];for(int i=0;i<8;i++)a[i]=empty();return a;}
    AccountState state(int free,int total,long committed){return new AccountState(true,total==8,false,100000000,0,0,
        free,total,committed,false,Map.of(),Map.of(),Map.of());}
    @Test public void nonemptyCaptureUsesCopiedCurrentCountersAndKnownIdentity() {
        var raw=slots();var working=mock(GrandExchangeOffer.class);raw[0]=working;
        when(working.getState()).thenReturn(GrandExchangeOfferState.BUYING);when(working.getItemId()).thenReturn(561);
        when(working.getPrice()).thenReturn(100L);when(working.getTotalQuantity()).thenReturn(10);
        when(working.getQuantitySold()).thenReturn(4);when(working.getSpent()).thenReturn(400L);
        var known=new TrackedOffer(0,561,true,100,10,50);known.setState("BUYING");
        var observed=PlanningAccountObservation.capture(account,state(7,8,600),raw,List.of(known),100);
        assertNotNull(observed);known.setQuantityFilled(10);when(working.getQuantitySold()).thenReturn(10);
        var captured=observed.getSlots().get(0);assertEquals(4,captured.getQuantityFilled());
        assertEquals(400,captured.getSpent());assertEquals(50,captured.getFirstSeen());
        captured.setQuantityFilled(9);assertEquals(4,observed.getSlots().get(0).getQuantityFilled());
    }
    @Test public void missingSlotsIdentityOrContradictoryResourcesCannotBeCaptured() {
        var raw=slots();assertNull(PlanningAccountObservation.capture(account,state(7,8,0),raw,List.of(),100));
        assertNull(PlanningAccountObservation.capture(account,state(8,8,100),raw,List.of(),100));
        raw[7]=null;assertNull(PlanningAccountObservation.capture(account,state(8,8,0),raw,List.of(),100));
        assertNull(PlanningAccountObservation.capture(null,state(8,8,0),slots(),List.of(),100));
        assertNull(PlanningAccountObservation.capture(account,state(8,8,0),slots(),Arrays.asList((TrackedOffer)null),100));
    }
    @Test public void freeToPlayStillObservesAllEightPhysicalSlots() {
        var observed=PlanningAccountObservation.capture(account,state(3,3,0),slots(),List.of(),100);
        assertNotNull(observed);assertEquals(8,observed.getSlots().size());
        assertNull(PlanningAccountObservation.capture(account,state(3,3,0),new GrandExchangeOffer[3],List.of(),100));
    }
}
