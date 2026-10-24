package com.flippingfriend.session;

import com.flippingfriend.*;
import com.flippingfriend.data.*;
import com.flippingfriend.model.*;
import java.time.Instant;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** A last public trade is not by itself permission to liquidate a whole acquisition. */
public class StopLossEvidenceTest {
    @Test public void aFreshDownsideSignalPausesOnlyAdditionalPurchasesWithoutInventingLossAuthority() {
        var p=position();var inverted=latest(806,1000);
        assertTrue(StopLossEvidence.blocksAdditionalBuy(p,inverted,now));
        assertFalse(StopLossEvidence.confirmed(p,inverted,List.of(),now));
        assertTrue(StopLossEvidence.blocksAdditionalBuy(p,new LatestPrice(806,now.getEpochSecond(),null,null),now));
        assertFalse(StopLossEvidence.blocksAdditionalBuy(p,latest(1000,990),now));
        assertFalse(StopLossEvidence.blocksAdditionalBuy(p,new LatestPrice(806,now.getEpochSecond()-121,800,now.getEpochSecond()),now));
        assertFalse(StopLossEvidence.blocksAdditionalBuy(p,new LatestPrice(806,now.getEpochSecond()+1,800,now.getEpochSecond()),now));
        p.setStopPrice(0);assertFalse(StopLossEvidence.blocksAdditionalBuy(p,latest(806,800),now));
    }
    @Test public void anyPossiblyRemainingProvenOriginCanPauseAnAdditionButCannotLiquidateOtherOrigins() {
        var p=position();
        var known=new com.flippingfriend.core.AcquisitionExitContext(1200,1100,120).bind("known");
        p.applyExitContexts(List.of(new com.flippingfriend.core.AcquisitionExitSlice("known",known,0,1),
            new com.flippingfriend.core.AcquisitionExitSlice("unknown",null,1,11000)));
        assertEquals(0,p.getStopPrice());assertTrue(StopLossEvidence.blocksAdditionalBuy(p,latest(1000,990),now));
        assertFalse(StopLossEvidence.confirmed(p,latest(1000,990),bars(1000,990),now));
        p.applyExitContexts(List.of(new com.flippingfriend.core.AcquisitionExitSlice("known",known,0,0),
            new com.flippingfriend.core.AcquisitionExitSlice("unknown",null,1,11000)));
        assertFalse("Disposed origins cannot pause later unrelated stock",StopLossEvidence.blocksAdditionalBuy(p,latest(1000,990),now));
    }
    final Instant now=Instant.ofEpochSecond(1800000600);
    final TaxCalculator tax=new TaxCalculator();
    final SellTimingEngine exits=new SellTimingEngine(new FillModel(),tax);
    final TradingHorizon horizon=TradingHorizon.of(RiskProfile.MODERATE,CheckInterval.THIRTY_MINUTES);
    Position position() {
        var p=new Position(11230,"Dragon dart",11000,10527000,now.getEpochSecond()-1800,true);
        p.setTargetSellPrice(998);p.setStopPrice(909);return p;
    }
    LatestPrice latest(int high,int low) {return new LatestPrice(high,now.getEpochSecond()-5,low,now.getEpochSecond()-5);}
    List<Candle> bars(int high,int low) {return List.of(new Candle(now.getEpochSecond()-300,high,low,30000,30000));}
    void cannotCut(LatestPrice quote,List<Candle> history) {
        var initial=exits.evaluateManagedExit(position(),quote,null,history,horizon,now,0,true,false,false);
        assertNotEquals("An unsupported spot quote must not authorize an initial loss",SellDecision.Action.CUT,initial.getAction());
        assertTrue(initial.getExpectedProfit()>=0);
        var pricing=new SaleMaintenancePricing();
        var maintenance=pricing.quote(11230,1000,11000,position(),quote,history,horizon,now,false,exits,tax,
            now.getEpochSecond()-216,now.getEpochSecond()-216);
        assertNull("A young profitable listing must stay working without confirmed stop evidence",maintenance);
        var relist=pricing.evaluateRelist(11230,1000,11000,position(),quote,history,horizon,now,false,exits,tax,
            now.getEpochSecond()-216,now.getEpochSecond()-216);
        assertTrue("Collection cannot create stop permission",relist.quote==null || !relist.quote.lossCut);
    }
    @Test public void isolatedLowPrintWithoutHistoryCannotLiquidateRecentDragonDarts() {cannotCut(latest(806,800),List.of());}
    @Test public void crossedPublicTradesCannotAuthorizeLoss() {cannotCut(latest(806,879),bars(800,790));}
    @Test public void completedTradesAboveTheOriginalStopDoNotConfirmASpotCrash() {cannotCut(latest(806,800),bars(988,964));}
    @Test public void incompleteZeroVolumeAndConflictingBucketsCannotAuthorizeLoss() {
        long end=now.getEpochSecond();
        for(var history:List.of(List.of(new Candle(end,800,790,100,100)),List.of(new Candle(end-300,800,790,0,100)),
            List.of(new Candle(end-300,800,790,100,0)),List.of(new Candle(end-300,800,790,100,100),new Candle(end-300,980,970,100,100)),
            List.of(new Candle(end-1200,800,790,100,100)),List.of(new Candle(end-300,800,850,100,100))))
            cannotCut(latest(806,800),history);
    }
    @Test public void confirmedFreshStopStillBypassesRoutinePlacementPatience() {
        var quote=latest(890,880);var history=bars(900,890);
        var initial=exits.evaluateManagedExit(position(),quote,null,history,horizon,now,0,true,false,false);
        assertEquals(SellDecision.Action.CUT,initial.getAction());
        assertTrue("An automatic loss exit must not add a model discount below the fresh sell-side print",initial.getPrice()>=880);
        var maintenance=new SaleMaintenancePricing().quote(11230,1000,11000,position(),quote,history,horizon,now,false,exits,tax,
            now.getEpochSecond()-1,now.getEpochSecond()-1);
        assertNotNull(maintenance);assertTrue(maintenance.lossCut);assertTrue(maintenance.price>=880);
    }
    @Test public void missingOriginalStopNeverBorrowsTodaysSettings() {
        var p=position();p.setStopPrice(0);
        var d=exits.evaluateManagedExit(p,latest(806,800),null,bars(800,790),horizon,now,0,true,false,false);
        assertNotEquals(SellDecision.Action.CUT,d.getAction());assertTrue(d.getExpectedProfit()>=0);
    }
}
