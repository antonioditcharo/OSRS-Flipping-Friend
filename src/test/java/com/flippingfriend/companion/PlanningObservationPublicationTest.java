package com.flippingfriend.companion;

import com.flippingfriend.*;
import com.flippingfriend.core.*;
import com.flippingfriend.data.*;
import com.flippingfriend.session.*;
import com.flippingfriend.model.Explainer;
import com.google.gson.Gson;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import net.runelite.api.*;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/** Replay is paused or uses an in-memory sender; no port, credentials or live service is touched. */
public class PlanningObservationPublicationTest {
    @Rule public TemporaryFolder folder=new TemporaryFolder();
    @Test public void allEightDurableObservationsRetainCaptureTimeAndBlockEntriesUntilAcknowledged()throws Exception {
        var storage=TestStorage.rootedAt(folder.newFolder().toPath(),"fixture");var gson=new Gson();
        var outbox=new OfferEventOutbox(storage);var client=new CompanionClient(storage,gson,new SuggestionLedger(),outbox);
        GrandExchangeOffer[] slots=new GrandExchangeOffer[8];
        for(int i=0;i<8;i++){slots[i]=mock(GrandExchangeOffer.class);when(slots[i].getState()).thenReturn(GrandExchangeOfferState.EMPTY);}
        var state=new AccountState(true,true,false,100000000,0,0,8,8,0,false,Map.of(),Map.of(),Map.of());
        long at=Instant.now().getEpochSecond()-2;
        var capture=PlanningAccountObservation.capture(storage.accountId(),state,slots,List.of(),at);
        var config=mock(FlippingFriendConfig.class);
        client.publishAccount(state,config,0,Map.of(),Map.of(),Set.of(),Set.of(),Set.of(),false,List.of(),List.of(),storage.accountId(),capture);
        var pending=outbox.pending();assertEquals(8,pending.size());Set<Integer> seen=new HashSet<>();
        Map<Integer,String> receipt=new HashMap<>();
        for(var e:pending){assertTrue(e.isAuthenticatedObservation());assertEquals(at,e.getObservedAt());
            assertTrue(seen.add(e.getSlot()));receipt.put(e.getSlot(),e.getEventId());}
        var snapshot=new AccountSnapshot("fixture",at,100000000,0,8,8,true,false,0).withAccountId(storage.accountId())
            .withAutomaticTradeScope().withOfferReadSet(receipt);
        assertEquals(8,gson.fromJson(gson.toJson(snapshot),AccountSnapshot.class).getOfferReadSet().size());
        var plan=new PortfolioPlan("waiting",at,at+90,"UNAVAILABLE","waiting",0,List.of());
        client.acceptPlan(plan);AtomicBoolean fetched=new AtomicBoolean();
        assertNull(client.nextEntryDecisionPresentation(new Explainer(),Set.of(),Set.of(),Set.of(),(id,rank)->{fetched.set(true);return null;}));
        assertFalse(fetched.get());
        client.resumeOfferReplay();
        assertTrue(client.replayPendingOffers(e->gson.toJson(Map.of("accepted",true,"eventId",e.getEventId(),
            "sessionId",e.getSessionId(),"sequence",e.getSequence()))));
        assertTrue(outbox.pending().isEmpty());client.acceptPlan(plan);
        client.nextEntryDecisionPresentation(new Explainer(),Set.of(),Set.of(),Set.of(),(id,rank)->{fetched.set(true);return null;});
        assertTrue(fetched.get());
    }
}
