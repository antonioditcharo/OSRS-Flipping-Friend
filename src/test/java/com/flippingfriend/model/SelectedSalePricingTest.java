package com.flippingfriend.model;

import com.flippingfriend.CheckInterval;
import com.flippingfriend.RiskProfile;
import com.flippingfriend.TradingHorizon;
import com.flippingfriend.core.*;
import com.flippingfriend.data.*;
import com.flippingfriend.session.SellTimingEngine;
import java.time.Instant;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class SelectedSalePricingTest {
    private final Instant now=Instant.now();
    private final String account=AccountIdentity.fromProfileKey("selected-sale");
    private final TaxCalculator tax=new TaxCalculator();
    private final SelectedSalePricing pricing=new SelectedSalePricing(new SellTimingEngine(new FillModel(),tax),tax);
    private final TradingHorizon horizon=TradingHorizon.of(RiskProfile.MODERATE,CheckInterval.FIVE_MINUTES);
    private PositionProjection holding() {
        return PositionProjection.ready(13263,"Abyssal bludgeon",3,3203,now.getEpochSecond()-60,now.getEpochSecond(),"buy","episode")
            .withExitContext(new AcquisitionExitContext(1400,900,30).bind("original"));
    }
    private AccountLedgerSnapshot ledger(List<PositionProjection> positions,long at) {
        return new AccountLedgerSnapshot(account,at,0,0,0,0,0,0,0,0,List.of()).withTradeEvidence(positions,List.of(),Map.of());
    }
    private Suggestion quote(int selected,int inventory,AccountLedgerSnapshot ledger,Map<Integer,Integer> listed,Set<Integer> buying) {
        var market=new MarketSnapshot(Map.of(13263,new ItemMetadata(13263,"Abyssal bludgeon",true,8,0)),Map.of(13263,new LatestPrice(1000,now.getEpochSecond(),950,now.getEpochSecond())),Map.of(),Map.of(),now);
        long end=now.getEpochSecond()/300*300;
        return pricing.quote(13263,selected,inventory,account,ledger,listed,buying,market,
            List.of(new Candle(end-600,1400,950,100,100),new Candle(end-300,1400,950,100,100)),horizon,now,false);
    }
    @Test public void selectingOneOfThreeManagedUnitsKeepsItsPlanAndExactSubsetCost() {
        var result=quote(1,3,ledger(List.of(holding()),now.getEpochSecond()),Map.of(),Set.of());
        assertEquals(SuggestionType.SELL,result.getType());assertEquals(1,result.getQuantity());assertEquals(1400,result.getPrice());
        assertTrue(result.isProfitKnown());assertFalse(result.isLossCut());
        assertEquals(tax.netProceeds(13263,1400,1)-SaleAccounting.allocateCost(3203,3,1),result.getExpectedProfit());
        assertNotNull(result.getExecutionForecast());assertEquals(1,result.getExecutionForecast().getQuantity());
    }
    @Test public void personalExtrasAndAlreadyListedUnitsCannotBorrowTheManagedPlan() {
        var ledger=ledger(List.of(holding()),now.getEpochSecond());
        assertEquals(SuggestionType.WAIT,quote(4,104,ledger,Map.of(),Set.of()).getType());
        assertEquals(SuggestionType.WAIT,quote(2,3,ledger,Map.of(13263,2),Set.of()).getType());
        assertEquals(SuggestionType.WAIT,quote(1,0,ledger,Map.of(),Set.of()).getType());
        assertEquals(SuggestionType.WAIT,quote(1,3,ledger,Map.of(),Set.of(13263)).getType());
    }
    @Test public void absentStaleOrForeignLedgerCannotBecomeGenericPricing() {
        assertEquals(SuggestionType.WAIT,quote(1,3,null,Map.of(),Set.of()).getType());
        assertEquals(SuggestionType.WAIT,quote(1,3,ledger(List.of(),now.getEpochSecond()-151),Map.of(),Set.of()).getType());
        var foreign=new AccountLedgerSnapshot(AccountIdentity.fromProfileKey("other"),now.getEpochSecond(),0,0,0,0,0,0,0,0,List.of()).withTradeEvidence(List.of(),List.of(),Map.of());
        assertEquals(SuggestionType.WAIT,quote(1,3,foreign,Map.of(),Set.of()).getType());
    }
    @Test public void personalSelectionWithFreshEmptyLedgerKeepsUnknownCostAndProfit() {
        var result=quote(1,3,ledger(List.of(),now.getEpochSecond()),Map.of(),Set.of());
        assertEquals(SuggestionType.SELL,result.getType());assertFalse(result.isProfitKnown());assertEquals(999,result.getPrice());
    }
    @Test public void missingOriginalStopCannotTurnASelectedHoldingIntoALossCut() {
        var unknown=PositionProjection.ready(13263,"Abyssal bludgeon",3,6003,now.getEpochSecond()-60,now.getEpochSecond(),"legacy","episode");
        var result=quote(1,3,ledger(List.of(unknown),now.getEpochSecond()),Map.of(),Set.of());
        assertEquals(SuggestionType.SELL,result.getType());assertFalse(result.isLossCut());
        assertTrue(result.getExpectedProfit()>=0);
    }
}
