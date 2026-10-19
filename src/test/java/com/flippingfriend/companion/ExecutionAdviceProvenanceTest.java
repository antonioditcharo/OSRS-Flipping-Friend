package com.flippingfriend.companion;

import com.flippingfriend.core.OfferEvent;
import com.flippingfriend.data.*;
import com.flippingfriend.session.TrackedOffer;
import com.google.gson.Gson;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.time.Instant;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

/** Actual outgoing publisher/outbox; fixture-only files and no HTTP or game input. */
public class ExecutionAdviceProvenanceTest {
    @Rule public TemporaryFolder folder=new TemporaryFolder();
    final Gson gson=new Gson();
    static final long now=1_700_000_000;
    static final class Fixture {
        final PluginStorage storage;
        final OfferEventOutbox outbox;
        final SuggestionLedger ledger=new SuggestionLedger(java.time.Clock.fixed(Instant.ofEpochSecond(now-1),java.time.ZoneOffset.UTC));
        final CompanionClient client;
        Fixture(Path root) {
            storage=TestStorage.rootedAt(root,"execution-advice");outbox=new OfferEventOutbox(storage);
            client=new CompanionClient(storage,new Gson(),ledger,outbox);
        }
        void advice(String id,int price,int quantity,double minutes) {ledger.recorded(id,561,true,price,quantity,0,minutes,.75);}
        OfferEvent publish(TrackedOffer offer,long at)throws Exception {
            Method method=CompanionClient.class.getDeclaredMethod("publishOffer",TrackedOffer.class,String.class,boolean.class,long.class,boolean.class);
            method.setAccessible(true);
            OfferEvent event=(OfferEvent)method.invoke(client,offer,storage.accountId(),true,at,false);
            assertNotNull("Source accounting event must remain publishable",event);return event;
        }
    }
    TrackedOffer offer(long start,int price,int quantity,String state,int filled) {
        TrackedOffer offer=new TrackedOffer(0,561,true,price,quantity,start);offer.setItemName("Nature rune");
        offer.setState(state);offer.setQuantityFilled(filled);offer.setSpent((long)price*filled);return offer;
    }
    @Test public void laterAdviceCannotReplaceAnOffersOriginalPrediction()throws Exception {
        Fixture f=new Fixture(folder.newFolder().toPath());f.advice("original",100,10,12);
        OfferEvent first=f.publish(offer(now,100,10,"BUYING",0),now);assertEquals("original",first.getRecommendationId());
        f.advice("replacement",110,10,120);
        OfferEvent terminal=f.publish(offer(now,100,10,"BOUGHT",10),now+30);
        assertEquals("The settled offer must be judged against its original prediction","original",terminal.getRecommendationId());
        assertEquals(12,terminal.getPredictedMinutes(),0);
    }
    @Test public void adviceCreatedAfterAnOfferStartedCannotClaimIt()throws Exception {
        Fixture f=new Fixture(folder.newFolder().toPath());f.advice("late",100,10,12);
        OfferEvent event=f.publish(offer(now-60,100,10,"BUYING",0),now);
        assertNull("An existing personal offer cannot acquire future advice",event.getRecommendationId());
        assertEquals(0,event.getPredictedMinutes(),0);
    }
    @Test public void anUnattributedOfferStaysUnattributedWhenAdviceAppearsLater()throws Exception {
        Fixture f=new Fixture(folder.newFolder().toPath());assertNull(f.publish(offer(now,100,10,"BUYING",0),now).getRecommendationId());
        f.advice("later",100,10,12);
        assertNull(f.publish(offer(now,100,10,"BUYING",3),now+10).getRecommendationId());
    }
    @Test public void aManualDifferentPriceOrQuantityDoesNotBorrowAnExactPrediction()throws Exception {
        Fixture f=new Fixture(folder.newFolder().toPath());f.advice("different-order",100,10,12);
        OfferEvent event=f.publish(offer(now,101,7,"BUYING",0),now);
        assertEquals("A prediction for ten at 100 does not score seven at 101",0,event.getPredictedMinutes(),0);
        assertEquals(0,event.getPredictedCompletion(),0);
    }
    @Test public void acknowledgedOriginalPredictionSurvivesClientRestart()throws Exception {
        Path root=folder.newFolder().toPath();Fixture before=new Fixture(root);before.advice("original",100,10,12);
        OfferEvent first=before.publish(offer(now,100,10,"BUYING",0),now);
        assertTrue(before.outbox.acknowledge(first.getEventId(),first.getSessionId(),first.getSequence()));
        Fixture after=new Fixture(root);after.advice("after-restart",110,10,120);
        OfferEvent terminal=after.publish(offer(now,100,10,"BOUGHT",10),now+30);
        assertEquals("original",terminal.getRecommendationId());assertEquals(12,terminal.getPredictedMinutes(),0);
    }
    @Test public void longRunningOfferDoesNotLoseItsBoundPredictionWhenAdviceExpires()throws Exception {
        Fixture f=new Fixture(folder.newFolder().toPath());f.advice("original",100,10,12);
        f.publish(offer(now,100,10,"BUYING",0),now);
        OfferEvent terminal=f.publish(offer(now,100,10,"BOUGHT",10),now+601);
        assertEquals("original",terminal.getRecommendationId());assertEquals(12,terminal.getPredictedMinutes(),0);
    }
    @Test public void originalExitSurvivesPartialFillsAcknowledgementOvernightRestartAndNewItemAdvice()throws Exception {
        Path root=folder.newFolder().toPath();Fixture first=new Fixture(root);
        first.ledger.recorded("original",561,true,100,10,0,12,.75,null,
            new com.flippingfriend.core.AcquisitionExitContext(140,90,480));
        var open=first.publish(offer(now,100,10,"BUYING",0),now);
        var partial=first.publish(offer(now,100,10,"BUYING",3),now+60);
        assertEquals(open.getEventId(),partial.getExitContext().getSourceEventId());
        for(var e:first.outbox.pending())assertTrue(first.outbox.acknowledge(e.getEventId(),e.getSessionId(),e.getSequence()));
        Fixture restart=new Fixture(root);
        restart.ledger.recorded("later",561,true,100,10,0,120,.9,null,
            new com.flippingfriend.core.AcquisitionExitContext(110,80,20));
        var bought=restart.publish(offer(now,100,10,"BOUGHT",10),now+8*3600);
        assertEquals(partial.getExitContext(),bought.getExitContext());
        assertEquals(140,bought.getExitContext().getTargetSellPrice());
        assertEquals(90,bought.getExitContext().getStopPrice());
        assertEquals(480,bought.getExitContext().getPredictedSellMinutes(),0);
        assertEquals(1000,bought.getSpent());assertEquals(10,bought.getFilledQuantity());
    }
    @Test public void actualAcceptedAdviceSuppliesTheSellLegAndStopToTheOriginalBinding()throws Exception {
        Fixture f=new Fixture(folder.newFolder().toPath());
        var suggestion=com.flippingfriend.model.Suggestion.builder(com.flippingfriend.model.SuggestionType.BUY)
            .item(561,"Nature rune").price(100).quantity(10).targetSellPrice(140).fillMinutes(12,480)
            .legCompletionProbability(.75).build();
        f.client.recordPresentedAdvice(suggestion,f.storage.accountId(),90);
        var open=f.publish(offer(now,100,10,"BUYING",0),now);
        assertNotNull(open.getExitContext());assertEquals(140,open.getExitContext().getTargetSellPrice());
        assertEquals(90,open.getExitContext().getStopPrice());assertEquals(480,open.getExitContext().getPredictedSellMinutes(),0);
        assertEquals(12,open.getPredictedMinutes(),0);
    }
    @Test public void unobservedOrMismatchedOriginalsCannotBorrowAnExitPlan()throws Exception {
        for(int variant=0;variant<4;variant++) {
            Fixture f=new Fixture(folder.newFolder().toPath());
            f.ledger.recorded("plan",561,true,100,10,0,12,.75,null,
                new com.flippingfriend.core.AcquisitionExitContext(140,90,480));
            var source=f.publish(offer(variant==0?now-60:now,variant==1?101:100,variant==2?9:10,
                "BUYING",variant==3?3:0),now);
            assertNull(source.getExitContext());
            assertNotNull(source.getEventId());assertTrue(source.getSequence()>0);
        }
    }
    @Test public void refreshedPredictionCannotBorrowAnOlderIdenticalOrdersAdviceClock()throws Exception {
        Fixture f=new Fixture(folder.newFolder().toPath());
        var field=SuggestionLedger.class.getDeclaredField("outstanding");field.setAccessible(true);
        @SuppressWarnings("unchecked") var advice=(java.util.Map<Long,SuggestionLedger.Advice>)field.get(f.ledger);
        advice.put(((long)561<<1)|1,new SuggestionLedger.Advice("old",100,10,0,12,.75,now-60));
        f.advice("future-prediction",100,10,120);
        OfferEvent observed=f.publish(offer(now-30,100,10,"BUYING",0),now-30);
        assertNull("A new prediction does not exist at the older display timestamp",observed.getRecommendationId());
    }
}
