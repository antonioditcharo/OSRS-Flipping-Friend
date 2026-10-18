package com.flippingfriend.session;

import com.flippingfriend.*;
import com.flippingfriend.data.*;
import com.flippingfriend.model.*;
import java.time.Instant;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public class QuietMarketExitTest {
    final Instant now=Instant.ofEpochSecond(1800000000);
    final TaxCalculator tax=new TaxCalculator();
    final SellTimingEngine exits=new SellTimingEngine(new FillModel(),tax);
    final TradingHorizon horizon=TradingHorizon.of(RiskProfile.MODERATE,CheckInterval.FIVE_MINUTES);
    Position stock(int quantity,long cost) {return new Position(561,"Nature rune",quantity,cost,now.getEpochSecond()-100000,true);}
    SellDecision quote(Position p,LatestPrice q,boolean sellOnly) {
        return exits.evaluateManagedExit(p,q,null,List.of(),horizon,now,0,true,sellOnly,false);
    }
    @Test public void quietHighOrLowCannotTriggerAStopOrCrossTheSpreadEvenInSellOnlyMode() {
        for(boolean sellOnly:new boolean[]{true,false})for(boolean quietHigh:new boolean[]{true,false}) {
            var p=stock(3,5881);p.setStopPrice(1800);
            var q=new LatestPrice(1000,now.getEpochSecond()-(quietHigh?1200:0),900,now.getEpochSecond()-(quietHigh?0:1200));
            var result=quote(p,q,sellOnly);
            assertEquals(SellDecision.Action.SELL,result.getAction());assertTrue(result.getReason().contains("quiet"));
            assertTrue(tax.netProceeds(561,result.getPrice(),3)>=5881);assertTrue(Double.isNaN(result.getExpectedMinutes()));
        }
    }
    @Test public void freshStopEvidenceStillCutsLosses() {
        var p=stock(3,5881);p.setStopPrice(1800);
        var result=quote(p,new LatestPrice(1000,now.getEpochSecond(),900,now.getEpochSecond()),false);
        assertEquals(SellDecision.Action.CUT,result.getAction());assertTrue(result.getExpectedProfit()<0);
    }
    @Test public void futureMissingUnknownOrImpossibleCostEvidenceCannotBecomeAQuietQuote() {
        assertFalse(quote(stock(3,5881),new LatestPrice(1000,now.getEpochSecond()+1,900,now.getEpochSecond()),false).isSell());
        assertFalse(quote(stock(3,5881),new LatestPrice(1000,null,900,now.getEpochSecond()),false).isSell());
        assertFalse(quote(stock(3,5881),null,false).isSell());
        assertFalse(quote(new Position(561,"Nature rune",3,0,now.getEpochSecond(),false),
            new LatestPrice(1000,now.getEpochSecond()-1200,900,now.getEpochSecond()),false).isSell());
        assertFalse(quote(stock(3,3L*Integer.MAX_VALUE),new LatestPrice(1000,now.getEpochSecond()-1200,900,now.getEpochSecond()),true).isSell());
    }
}
