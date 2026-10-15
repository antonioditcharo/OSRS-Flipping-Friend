package com.flippingfriend.companion;

import com.flippingfriend.CheckInterval;
import com.flippingfriend.RiskProfile;
import com.flippingfriend.TradingHorizon;
import com.flippingfriend.core.*;
import com.flippingfriend.data.*;
import com.flippingfriend.model.*;
import com.flippingfriend.session.SellTimingEngine;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

/** Actual configured estimator, serialization and durable fixture outbox; no live files or HTTP. */
public class ExecutionForecastContractTest {
    @Rule public TemporaryFolder folder=new TemporaryFolder();
    final com.google.gson.Gson gson=new com.google.gson.Gson();
    static List<Candle> series() {
        return List.of(new Candle(1000,110,100,500,400),new Candle(1300,110,100,500,400),
            new Candle(1600,111,99,500,400),new Candle(1900,111,99,500,400));
    }
    ExecutionForecast forecast(FillModel model,boolean buying,int price,int quantity,double horizon,double multiplier) {
        var curve=FillCurve.overRecentHistory(series());
        var estimate=buying?model.estimateBuy(curve,price,quantity,horizon):model.estimateSell(curve,price,quantity,horizon);
        double total=estimate.getExpectedHours()+estimate.getWaitHours()*(multiplier-1);
        return model.forecast(561,buying,curve,price,quantity,horizon,1,estimate,multiplier,total);
    }
    @Test public void exactConfiguredModelAndBothLegInputsSurviveSerialization() {
        FillModel model=new FillModel(.3,.8,false,.2);
        for(boolean buying:new boolean[]{true,false}) {
            int price=buying?110:99;var f=forecast(model,buying,price,10,1,1.3);
            assertTrue(f.isValid());assertTrue(f.matches(561,buying,price,10));
            assertEquals(f.getExpectedTotalSeconds(),f.getRawWaitSeconds()*1.3+f.getWorkingSeconds()+f.getFloorOverheadSeconds(),1e-8);
            var copy=gson.fromJson(gson.toJson(f),ExecutionForecast.class);
            assertTrue(copy.isValid());assertEquals(f.getFingerprint(),copy.getFingerprint());
            assertNotEquals(f.getFingerprint(),forecast(model,buying,price,10,2,1.3).getFingerprint());
            assertNotEquals(f.getFingerprint(),forecast(new FillModel(.3,.8,true,.2),buying,price,10,1,1.3).getFingerprint());
            assertNotEquals(f.getFingerprint(),forecast(new FillModel(.3,.8,false,.4),buying,price,10,1,1.3).getFingerprint());
            assertFalse(f.matches(561,!buying,price,10));assertFalse(f.matches(561,buying,price+1,10));
            assertFalse(f.matches(561,buying,price,11));
        }
    }
    @Test public void floorTimeIsKeptSeparateFromWaitingAndWorking() {
        var model=new FillModel(0,0,false,.35);var f=forecast(model,true,110,1,1,1);
        assertTrue(f.isValid());assertEquals(0,f.getRawWaitSeconds(),0);
        assertEquals(60,f.getExpectedTotalSeconds(),1e-8);assertTrue(f.getFloorOverheadSeconds()>0);
    }
    @Test public void missingFieldsAndUnknownVersionsCannotInventZeroValuedInputs() {
        var original=forecast(new FillModel(),true,110,10,1,1);
        for(String key:new String[]{"schemaVersion","modelVersion","captureRate","evidenceWeight","waitWeight",
                "lumpyArrivals","buying","horizonSeconds","seasonalMultiplier","waitMultiplier","rawWaitSeconds",
                "workingSeconds","floorOverheadSeconds","expectedTotalSeconds","completionProbability","scoredBuckets"}) {
            var json=gson.toJsonTree(original).getAsJsonObject();json.remove(key);
            var missing=gson.fromJson(json,ExecutionForecast.class);
            assertFalse("Missing "+key,missing.isValid());assertNull(missing.getFingerprint());
        }
        for(String key:new String[]{"schemaVersion","modelVersion"}) {
            var json=gson.toJsonTree(original).getAsJsonObject();json.addProperty(key,"future");
            assertFalse(gson.fromJson(json,ExecutionForecast.class).isValid());
        }
    }
    @Test public void zeroProbabilityIsAClaimButNegativeOrInconsistentPartsAreRejected() {
        var original=forecast(new FillModel(),true,110,10,.00001,1);
        assertTrue(original.isValid());assertEquals(0,original.getCompletionProbability(),0);
        for(String key:new String[]{"completionProbability","rawWaitSeconds","workingSeconds","expectedTotalSeconds"}) {
            var json=gson.toJsonTree(original).getAsJsonObject();json.addProperty(key,-1);
            assertFalse(gson.fromJson(json,ExecutionForecast.class).isValid());
        }
        var json=gson.toJsonTree(original).getAsJsonObject();json.addProperty("expectedTotalSeconds",100000);
        assertFalse(gson.fromJson(json,ExecutionForecast.class).isValid());
    }
    @Test public void sellForecastUsesTheDisplayedQuantityAndPriceAndMissingHistoryStaysUnknown() {
        var engine=new SellTimingEngine(new FillModel(),new TaxCalculator());
        var horizon=TradingHorizon.of(RiskProfile.MODERATE,CheckInterval.FIFTEEN_MINUTES);
        var small=engine.forecastForOrder(561,100,10,series(),horizon);
        var large=engine.forecastForOrder(561,100,100,series(),horizon);
        assertNotNull(small);assertTrue(small.matches(561,false,100,10));
        assertTrue(large.getWorkingSeconds()>small.getWorkingSeconds());
        assertEquals(horizon.legHorizonHours()*3600,small.getHorizonSeconds(),0);
        assertNull(engine.forecastForOrder(561,100,10,List.of(),horizon));
        assertNull(Suggestion.builder(SuggestionType.SELL).legCompletionProbability(null).build().getLegCompletionProbability());
    }
    @Test public void acceptedForecastFlowsThroughPublisherAndSurvivesAcknowledgementRestartAndExpiry()throws Exception {
        Path root=folder.newFolder().toPath();var storage=TestStorage.rootedAt(root,"forecast");
        long at=Instant.now().getEpochSecond()+2;var ledger=new SuggestionLedger();
        var outbox=new OfferEventOutbox(storage);var client=new CompanionClient(storage,gson,ledger,outbox);
        var f=forecast(new FillModel(),true,110,10,1,1);
        var sell=forecast(new FillModel(),false,99,10,1,1);
        var candidate=new PortfolioCandidate(561,"Nature rune","",110,110,110,99,99,99,10,1000,0,100,100,
            f.getCompletionProbability(),sell.getCompletionProbability(),f.getExpectedTotalSeconds()/3600,
            sell.getExpectedTotalSeconds()/3600,1,at+90).withForecasts(f,sell);
        client.acceptPlan(new PortfolioPlan("original",at-2,at+90,"READY","fixture",100,List.of(new PortfolioAllocation(1,candidate,"PLACE_BUY"))));
        var accepted=client.nextEntryDecisionPresentation(new Explainer(),java.util.Set.of(),java.util.Set.of(),java.util.Set.of(),
            (plan,rank)->PolicyDecision.action("1","fixture","decision",PolicyDecisionType.ENTRY,OfferLifecycleAction.PLACE_BUY,
                "ENTRY_CANDIDATE_SELECTED",at-2,at-3,"561:110:99:10",plan));
        assertNotNull(accepted);var suggestion=accepted.getSuggestion();
        assertEquals(f.getFingerprint(),suggestion.getExecutionForecast().getFingerprint());
        client.recordPresentedAdvice(suggestion,storage.accountId());
        var method=CompanionClient.class.getDeclaredMethod("publishOffer",com.flippingfriend.session.TrackedOffer.class,
            String.class,boolean.class,long.class,boolean.class);method.setAccessible(true);
        var tracked=new com.flippingfriend.session.TrackedOffer(0,561,true,110,10,at);tracked.setState("BUYING");
        var first=(OfferEvent)method.invoke(client,tracked,storage.accountId(),true,at,false);
        assertNotNull(first);assertTrue(first.isPredictionComparable());
        assertEquals(f.getFingerprint(),first.getExecutionForecast().getFingerprint());
        assertTrue(outbox.acknowledge(first.getEventId(),first.getSessionId(),first.getSequence()));
        var restart=new CompanionClient(TestStorage.rootedAt(root,"forecast"),gson,new SuggestionLedger());
        tracked.setState("BOUGHT");tracked.setQuantityFilled(10);tracked.setSpent(1100);
        var terminal=(OfferEvent)method.invoke(restart,tracked,storage.accountId(),true,at+1000,false);
        assertTrue(terminal.isPredictionComparable());assertEquals(f.getFingerprint(),terminal.getExecutionForecast().getFingerprint());
        assertEquals(1100,terminal.getSpent());assertEquals(10,terminal.getFilledQuantity());
        assertEquals(f.getFingerprint(),terminal.withAccountId(storage.accountId()).getExecutionForecast().getFingerprint());
    }
}
