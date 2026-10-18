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
    @Test public void anUnobservedRecordedPositionDoesNotPreemptNewBuysWithAPhantomSale()throws Exception {
        assertNull("Retain the purchase record, but do not invent a location or ask to sell it",actualGuidance(2,81,0,100));
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
        return actualGuidance(quantity,cost,inInventory,price,false);
    }
    Suggestion actualGuidance(int quantity,long cost,int inInventory,int price,boolean heldPrice)throws Exception {
        return actualGuidance(quantity,cost,inInventory,price,heldPrice,false);
    }
    Suggestion actualGuidance(int quantity,long cost,int inInventory,int price,boolean heldPrice,boolean adjustment)throws Exception {
        return actualGuidance(quantity,cost,inInventory,price,heldPrice,adjustment,0);
    }
    Suggestion actualGuidance(int quantity,long cost,int inInventory,int price,boolean heldPrice,boolean adjustment,long quoteAge)throws Exception {
        var market=mock(MarketDataService.class);when(market.getSeries(anyInt(),anyString())).thenReturn(List.of());
        var offers=mock(OfferTracker.class);when(offers.listedForSale()).thenReturn(Map.of());
        when(offers.itemsBeingBought()).thenReturn(Set.of());when(offers.getOffers()).thenReturn(List.of());
        var skipped=mock(SkipList.class);when(skipped.skipped(any())).thenReturn(Set.of());
        var config=mock(FlippingFriendConfig.class);when(config.riskProfile()).thenReturn(RiskProfile.MODERATE);
        var plans=mock(TradePlans.class);
        var monitor=mock(AccountMonitor.class);
        var engine=new SuggestionEngine(market,mock(FeatureEngine.class),null,null,new Explainer(),null,tax,monitor,null,
            mock(PositionBook.class),offers,new SellTimingEngine(new FillModel(),tax),plans,config,skipped,null);
        var companion=mock(CompanionClient.class);Field field=SuggestionEngine.class.getDeclaredField("companion");
        field.setAccessible(true);field.set(engine,companion);
        var ledger=new AccountLedgerSnapshot(AccountIdentity.fromProfileKey("exact-cost"),now.getEpochSecond(),0,0,0,0,0,0,0,0,List.of())
            .withTradeEvidence(List.of(PositionProjection.ready(561,"Nature rune",quantity,cost,now.getEpochSecond(),now.getEpochSecond(),"buy","offer")),List.of());
        when(companion.tradingLedger()).thenReturn(ledger);
        engine.setObservedAccount(ledger.getAccountId());
        if(heldPrice) {
            Method pin=SuggestionEngine.class.getDeclaredMethod("advisedPrice",int.class,boolean.class,int.class,long.class);
            pin.setAccessible(true);pin.invoke(engine,561,false,price,now.getEpochSecond());
        }
        var snapshot=new MarketSnapshot(Map.of(),Map.of(561,new LatestPrice(price,now.getEpochSecond()-quoteAge,price,now.getEpochSecond())),Map.of(),Map.of(),now);
        var account=new AccountState(true,true,false,100000000,0,0,8,8,0,false,Map.of(561,inInventory),Map.of(561,inInventory),Map.of());
        when(monitor.getState()).thenReturn(account);
        if(adjustment) {
            var tracked=new TrackedOffer(0,561,false,1200,inInventory,now.getEpochSecond()-100000);
            tracked.setState("SELLING");when(offers.getOffers()).thenReturn(List.of(tracked));
            Method method=SuggestionEngine.class.getDeclaredMethod("adjustSuggestion",MarketSnapshot.class,TradingHorizon.class,Instant.class);
            method.setAccessible(true);return (Suggestion)method.invoke(engine,snapshot,horizon,now);
        }
        Method method=SuggestionEngine.class.getDeclaredMethod("sellSuggestion",MarketSnapshot.class,TradingHorizon.class,AccountState.class,Instant.class);
        method.setAccessible(true);return (Suggestion)method.invoke(engine,snapshot,horizon,account,now);
    }
    @Test public void displayedGuidanceUsesTheExactCanonicalCostForTheSuggestedQuantity()throws Exception {
        var suggestion=actualGuidance(2,81,2,42);
        assertNotNull(suggestion);assertEquals(SuggestionType.SELL,suggestion.getType());
        assertEquals(2,suggestion.getQuantity());assertEquals(3,suggestion.getExpectedProfit());
    }
    @Test public void quietVerifiedStockGetsAnExplainedNonLossSellCappedToObservedUnits()throws Exception {
        var suggestion=actualGuidance(3,5881,2,1000,false,false,1200);
        assertNotNull(suggestion);assertEquals(SuggestionType.SELL,suggestion.getType());assertEquals(2,suggestion.getQuantity());
        assertFalse(suggestion.isLossCut());assertTrue(suggestion.getDetail().contains("quiet"));
        long cost=SaleAccounting.allocateCost(5881,3,2);
        assertTrue(tax.netProceeds(561,suggestion.getPrice(),2)>=cost);
        assertEquals(tax.netProceeds(561,suggestion.getPrice(),2)-cost,suggestion.getExpectedProfit());
        assertNull("Quiet quotes never create a location or authorize personal units",actualGuidance(3,5881,0,1000,false,false,1200));
    }
    @Test public void aPartialSuggestedSaleUsesTheLedgersProportionalCostRule()throws Exception {
        var suggestion=actualGuidance(3,125,2,44);
        assertNotNull(suggestion);assertEquals(2,suggestion.getQuantity());
        assertEquals(5,suggestion.getExpectedProfit()); // 88 proceeds - floor(125 * 2 / 3) cost.
    }
    @Test public void aStabilizedPriceCannotOverrideTheExactNonLossFloor()throws Exception {
        var suggestion=actualGuidance(2,1961,2,1000,true);
        assertNotNull(suggestion);assertTrue(suggestion.getPrice()>=1001);
        assertTrue(tax.netProceeds(561,suggestion.getPrice(),suggestion.getQuantity())>=1961);
        assertEquals(tax.netProceeds(561,suggestion.getPrice(),2)-1961,suggestion.getExpectedProfit());
    }
    @Test public void anUnreachableBreakEvenDoesNotAuthorizeAnUnforcedLoss() {
        Position position=position(2,2L*Integer.MAX_VALUE);
        assertFalse(decide(position,Integer.MAX_VALUE).isSell());
        var forced=new SellTimingEngine(new FillModel(),tax).evaluate(position,
            new LatestPrice(Integer.MAX_VALUE,now.getEpochSecond(),Integer.MAX_VALUE,now.getEpochSecond()),
            null,List.of(),horizon,now,true,true);
        assertTrue(forced.isSell());assertEquals(-10000000,forced.getExpectedProfit());
    }
    @Test public void sellMaintenanceKeepsTheExactNonLossFloor()throws Exception {
        var suggestion=actualGuidance(2,1961,2,1001,false,true);
        assertNotNull(suggestion);assertEquals(SuggestionType.MODIFY_SELL,suggestion.getType());
        assertTrue(tax.netProceeds(561,suggestion.getPrice(),2)>=1961);
    }
    @Test public void sellMaintenanceAndItsRemainderProfitUseExactAllocatedCost()throws Exception {
        var full=actualGuidance(2,1961,2,1050,false,true);
        assertNotNull(full);assertEquals(97,full.getExpectedProfit());
        var partial=actualGuidance(3,125,2,44,false,true);
        assertNotNull(partial);assertEquals(2,partial.getQuantity());assertEquals(3,partial.getExpectedProfit());
    }
}
