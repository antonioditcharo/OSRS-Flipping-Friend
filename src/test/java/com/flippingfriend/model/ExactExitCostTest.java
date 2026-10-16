package com.flippingfriend.model;

import com.flippingfriend.*;
import com.flippingfriend.companion.CompanionClient;
import com.flippingfriend.core.*;
import com.flippingfriend.data.*;
import com.flippingfriend.session.*;
import java.lang.reflect.*;
import java.time.Instant;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/** Actual exit pricing and canonical-to-guide conversion; no game, HTTP or live account files. */
public class ExactExitCostTest {
    final TaxCalculator tax=new TaxCalculator();
    final Instant now=Instant.now();
    final TradingHorizon horizon=TradingHorizon.of(RiskProfile.MODERATE,CheckInterval.FIFTEEN_MINUTES);
    Position position(int quantity,long cost) {return new Position(561,"Nature rune",quantity,cost,now.getEpochSecond(),true);}
    SellDecision decide(Position position,int price) {
        return new SellTimingEngine(new FillModel(),tax).evaluate(position,
            new LatestPrice(price,now.getEpochSecond(),price,now.getEpochSecond()),null,List.of(),horizon,now,true,false);
    }
    @Test public void exitDecisionSubtractsTheExactPoolCostRatherThanARoundedUnitAverage() {
        var decision=decide(position(2,81),42);
        assertEquals(42,decision.getPrice());assertEquals(3,decision.getExpectedProfit());
    }
    @Test public void aFractionalAverageCannotMakeALossLookLikeBreakEven() {
        var decision=decide(position(2,81),40);
        assertTrue(decision.isSell());assertTrue(tax.netProceeds(561,decision.getPrice(),2)>=81);
    }
    Suggestion actualGuidance(int quantity,long cost,int inInventory,int price)throws Exception {
        var market=mock(MarketDataService.class);when(market.getSeries(anyInt(),anyString())).thenReturn(List.of());
        var offers=mock(OfferTracker.class);when(offers.listedForSale()).thenReturn(Map.of());
        when(offers.itemsBeingBought()).thenReturn(Set.of());when(offers.getOffers()).thenReturn(List.of());
        var skipped=mock(SkipList.class);when(skipped.skipped(any())).thenReturn(Set.of());
        var config=mock(FlippingFriendConfig.class);when(config.riskProfile()).thenReturn(RiskProfile.MODERATE);
        var plans=mock(TradePlans.class);
        var engine=new SuggestionEngine(market,mock(FeatureEngine.class),null,null,new Explainer(),null,tax,null,null,
            mock(PositionBook.class),offers,new SellTimingEngine(new FillModel(),tax),plans,config,skipped,null);
        var companion=mock(CompanionClient.class);Field field=SuggestionEngine.class.getDeclaredField("companion");
        field.setAccessible(true);field.set(engine,companion);
        var ledger=new AccountLedgerSnapshot(AccountIdentity.fromProfileKey("exact-cost"),now.getEpochSecond(),0,0,0,0,0,0,0,0,List.of())
            .withTradeEvidence(List.of(PositionProjection.ready(561,"Nature rune",quantity,cost,now.getEpochSecond(),now.getEpochSecond(),"buy","offer")),List.of());
        when(companion.tradingLedger()).thenReturn(ledger);
        var snapshot=new MarketSnapshot(Map.of(),Map.of(561,new LatestPrice(price,now.getEpochSecond(),price,now.getEpochSecond())),Map.of(),Map.of(),now);
        var account=new AccountState(true,true,false,100000000,0,0,8,8,0,false,Map.of(561,inInventory),Map.of(561,inInventory),Map.of());
        Method method=SuggestionEngine.class.getDeclaredMethod("sellSuggestion",MarketSnapshot.class,TradingHorizon.class,AccountState.class,Instant.class);
        method.setAccessible(true);return (Suggestion)method.invoke(engine,snapshot,horizon,account,now);
    }
    @Test public void displayedGuidanceUsesTheExactCanonicalCostForTheSuggestedQuantity()throws Exception {
        var suggestion=actualGuidance(2,81,2,42);
        assertNotNull(suggestion);assertEquals(SuggestionType.SELL,suggestion.getType());
        assertEquals(2,suggestion.getQuantity());assertEquals(3,suggestion.getExpectedProfit());
    }
    @Test public void aPartialSuggestedSaleUsesTheLedgersProportionalCostRule()throws Exception {
        var suggestion=actualGuidance(3,125,2,44);
        assertNotNull(suggestion);assertEquals(2,suggestion.getQuantity());
        assertEquals(5,suggestion.getExpectedProfit()); // 88 proceeds - floor(125 * 2 / 3) cost.
    }
}
