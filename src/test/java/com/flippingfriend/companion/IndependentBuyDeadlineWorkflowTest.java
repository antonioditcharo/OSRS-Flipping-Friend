package com.flippingfriend.companion;

import com.flippingfriend.*;
import com.flippingfriend.core.*;
import com.flippingfriend.data.*;
import com.flippingfriend.model.*;
import com.flippingfriend.session.*;
import java.nio.file.*;
import java.util.*;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/** Real capture/outbox -> client timing -> forced tracker metadata; no HTTP or game input. */
public class IndependentBuyDeadlineWorkflowTest {
    @Rule public TemporaryFolder folder=new TemporaryFolder();
    final long at=AcceptedBuyPlanDeliveryTest.AT;
    OfferTracker tracker(PluginStorage storage,TrackedOffer offer)throws Exception {
        Files.writeString(storage.accountDir().resolve("offers.json"),storage.gson().toJson(List.of(offer)));
        var tracker=new OfferTracker(storage,mock(PositionBook.class),mock(BuyLimitTracker.class),mock(TradeJournal.class),
            new TaxCalculator(),mock(TradePlans.class));tracker.load();return tracker;
    }
    @Test public void independentForecastFreezesPlacementDeadlineAcrossEveryCheckingCadenceAndRestart()throws Exception {
        for(int cadence:new int[]{5,30,120,480}) {
            var root=folder.newFolder().toPath();var f=new AcceptedBuyPlanDeliveryTest.Fixture(root);
            f.seconds.set(at-601);f.advice("old-learning",150);f.seconds.set(at);
            var forecast=new ExecutionForecast(ExecutionForecast.MODEL_VERSION,.35,0,1,true,561,true,100,10,3,1,
                3600,12,12,120,new FillEstimate(.75,.2,100,.1),1,.2);
            f.ledger.recorded("fresh-plan",561,true,100,10,0,12,.75,forecast,new AcquisitionExitContext(170,90,480));
            var tracked=f.offer(0,at,100,0,"BUYING");var source=f.publish(tracked,at);
            assertNull(source.getRecommendationId());assertFalse(source.isPredictionComparable());
            var tracking=tracker(f.storage,tracked);
            byte[] executions=Files.readAllBytes(f.storage.accountDir().resolve("offers.json"));
            var horizon=TradingHorizon.of(RiskProfile.MODERATE,interval(cadence));
            long firstEvaluation=at+cadence*60L;
            long deadline=f.client.freezeBuyMaintenanceDeadline(tracked,tracking,horizon.legHorizonHours()*60,firstEvaluation);
            assertEquals("Original duration must be anchored at placement, not first checking visit",at+720,deadline);
            assertArrayEquals("Workflow clocks cannot rewrite execution snapshots",executions,
                Files.readAllBytes(f.storage.accountDir().resolve("offers.json")));
            f.ackAll();var restarted=new AcceptedBuyPlanDeliveryTest.Fixture(root);
            var partial=restarted.offer(0,at,100,2,"BUYING");
            restarted.publish(partial,firstEvaluation+1);
            var restored=tracker(restarted.storage,partial);
            assertEquals(deadline,restarted.client.freezeBuyMaintenanceDeadline(partial,restored,999,firstEvaluation+30));
            long reassess=Math.max(at+720,at+60+horizon.staleOfferMinutes()*60L);
            var quote=new LatestPrice(170,reassess,100,reassess);
            assertTrue("Original deadline and actual-fill patience both apply",
                BuyRepricePricing.overdue(at,at+60,deadline,quote,horizon,reassess));
            assertFalse("Recent fills still suppress overdue cancellation",
                BuyRepricePricing.overdue(at,reassess-1,deadline,quote,horizon,reassess));
            assertNull("Independent plan must not enter the legacy recommendation policy",restarted.client.currentCanonicalOpenBuy());
        }
    }
    @Test public void unavailableCaptureDefersFallbackUntilOriginalSourceCommits()throws Exception {
        var storage=TestStorage.rootedAt(folder.newFolder().toPath(),"accepted-buy");
        var failing=new java.util.concurrent.atomic.AtomicBoolean(true);
        var outbox=new OfferEventOutbox(storage,(file,temp)->{if(failing.get())throw new java.io.IOException("fixture storage outage");});
        var f=new AcceptedBuyPlanDeliveryTest.Fixture(storage,new java.util.concurrent.atomic.AtomicLong(at),outbox);
        var forecast=new ExecutionForecast(ExecutionForecast.MODEL_VERSION,.35,0,1,true,561,true,100,10,3,1,
            3600,12,12,120,new FillEstimate(.75,.2,100,.1),1,.2);
        f.ledger.recorded("fresh",561,true,100,10,0,12,.75,forecast,new AcquisitionExitContext(170,90,480));
        var offer=f.offer(0,at,100,0,"BUYING");var tracker=tracker(storage,offer);
        f.client.captureObservedOffer(offer,storage.accountId(),at);
        var metadata=storage.accountDir().resolve("buy-maintenance-deadlines.json");
        assertEquals(0,f.client.freezeBuyMaintenanceDeadline(offer,tracker,180,at+600));
        assertFalse("Unavailable source is not proof of an unknown forecast",Files.exists(metadata));
        failing.set(false);outbox.retryUnsaved();f.client.captureObservedOffer(offer,storage.accountId(),at+601);
        assertEquals(at+720,f.client.freezeBuyMaintenanceDeadline(offer,tracker,180,at+601));
        f.ackAll();assertEquals(at+720,f.client.freezeBuyMaintenanceDeadline(offer,tracker,999,at+900));
    }
    @Test public void durablyUnknownForecastStillFreezesFallbackAndKeepsExistingDeadline()throws Exception {
        var root=folder.newFolder().toPath();var f=new AcceptedBuyPlanDeliveryTest.Fixture(root);
        var offer=f.offer(0,at,100,0,"BUYING");f.publish(offer,at);var tracker=tracker(f.storage,offer);
        assertTrue(Double.isNaN(f.client.originalBuyExpectedMinutes(offer)));
        assertEquals(at+600+180*60,f.client.freezeBuyMaintenanceDeadline(offer,tracker,180,at+600));
        f.ackAll();var restarted=new AcceptedBuyPlanDeliveryTest.Fixture(root);restarted.publish(offer,at+900);
        assertEquals(at+600+180*60,restarted.client.freezeBuyMaintenanceDeadline(offer,tracker,5,at+900));
    }
    @Test public void restartChangedCountersFutureSourceAndReplacedSessionCannotCreateFallback()throws Exception {
        var root=folder.newFolder().toPath();var f=new AcceptedBuyPlanDeliveryTest.Fixture(root);
        var offer=f.offer(0,at,100,0,"BUYING");f.publish(offer,at);f.ackAll();
        var tracker=tracker(f.storage,offer);var metadata=f.storage.accountDir().resolve("buy-maintenance-deadlines.json");
        var cold=new AcceptedBuyPlanDeliveryTest.Fixture(root);
        assertEquals(0,cold.client.freezeBuyMaintenanceDeadline(offer,tracker,180,at+600));
        assertFalse(Files.exists(metadata));
        var changed=f.offer(0,at,100,1,"BUYING");
        assertEquals(0,f.client.freezeBuyMaintenanceDeadline(changed,tracker,180,at+600));
        f.publish(offer,at+601);
        assertEquals(0,f.client.freezeBuyMaintenanceDeadline(offer,tracker,180,at+600));
        assertFalse(Files.exists(metadata));
        Files.delete(f.storage.accountDir().resolve(OfferEventOutbox.FILE_NAME));
        assertEquals(0,f.client.freezeBuyMaintenanceDeadline(offer,tracker,180,at+602));
        assertFalse(Files.exists(metadata));
    }
    @Test public void unsupportedPlanEvidenceCannotBecomeAPermanentUnknownForecast()throws Exception {
        var f=new AcceptedBuyPlanDeliveryTest.Fixture(folder.newFolder().toPath());
        f.outbox.enqueueObservedOffer((id,session,seq)->OfferEvent.builder("future-source",at,"BUYING")
            .accountId(f.storage.accountId()).authenticatedObservation(true).eventIdentity(id,session,"0@"+at+":561")
            .sequence(seq).slot(0).item(561,"Nature rune").buying(true).price(100).quantities(10,0).firstSeenAt(at)
            .acceptedBuyPlanEvidenceVersion("2").build());
        var offer=f.offer(0,at,100,0,"BUYING");var inherited=f.publish(offer,at+1);
        assertEquals("2",inherited.getAcceptedBuyPlanEvidenceVersion());assertNull(inherited.getAcceptedBuyPlanProof());
        var tracker=tracker(f.storage,offer);var sourceFile=f.storage.accountDir().resolve(OfferEventOutbox.FILE_NAME);
        byte[] retained=Files.readAllBytes(sourceFile);
        assertEquals(0,f.client.freezeBuyMaintenanceDeadline(offer,tracker,180,at+600));
        assertFalse(Files.exists(f.storage.accountDir().resolve("buy-maintenance-deadlines.json")));
        assertArrayEquals(retained,Files.readAllBytes(sourceFile));
    }
    private com.flippingfriend.CheckInterval interval(int minutes) {
        for(var value:com.flippingfriend.CheckInterval.values())if(value.getMinutes()==minutes)return value;
        throw new AssertionError("Missing cadence "+minutes);
    }
}
