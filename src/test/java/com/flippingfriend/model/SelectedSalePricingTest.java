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
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class SelectedSalePricingTest {
    @Rule public TemporaryFolder folder=new TemporaryFolder();
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
    @Test public void selectedCollectedSubsetCannotRestoreTheOldTargetWhenBucketsDisappear()throws Exception {
        var storage=TestStorage.rootedAt(folder.newFolder().toPath(),"selected-sale");
        var tracker=mock(com.flippingfriend.session.OfferTracker.class);
        var original=new com.flippingfriend.session.TrackedOffer(0,13263,false,1500,3,now.getEpochSecond()-1200);original.setState("SELLING");
        when(tracker.getOffers()).thenReturn(List.of(original));when(tracker.getOffer(0)).thenReturn(original);
        var book=new com.flippingfriend.session.SellRelistIntent(storage);
        assertTrue(book.advise(original,account,true,tracker,"accepted-sale-source"));
        original.setState("CANCELLED_SELL");original.setQuantityFilled(1);original.setCollected(true);
        when(tracker.getOffers()).thenReturn(List.of());when(tracker.getOffer(0)).thenReturn(null);when(tracker.getSettledOffer(0)).thenReturn(original);
        var reprice=book.collectedRequest(tracker,account);assertEquals(2,reprice.getQuantity());
        var market=new MarketSnapshot(Map.of(13263,new ItemMetadata(13263,"Abyssal bludgeon",true,8,0)),
            Map.of(13263,new LatestPrice(1200,now.getEpochSecond(),1150,now.getEpochSecond())),Map.of(),Map.of(),now);
        var ledger=ledger(List.of(holding()),now.getEpochSecond());long end=now.getEpochSecond()/300*300;
        var bars=List.of(new Candle(end-600,1200,1150,100,100),new Candle(end-300,1200,1150,100,100));
        var reduced=pricing.quote(13263,1,3,account,ledger,Map.of(),Set.of(),market,bars,horizon,now,false,reprice);
        assertEquals(SuggestionType.SELL,reduced.getType());assertEquals(1199,reduced.getPrice());
        var missing=pricing.quote(13263,1,3,account,ledger,Map.of(),Set.of(),market,List.of(),horizon,now,false,reprice);
        assertEquals("Missing relist support cannot restore 1400 for a selected subset",SuggestionType.WAIT,missing.getType());
        assertEquals(3203,ledger.getTradingPositions().get(0).getTotalCost());
    }
    @Test public void acceptedFiveMinuteReductionKeepsExactSelectedCostAfterLateFillAndEightHourCadenceChange()throws Exception {
        var storage=TestStorage.rootedAt(folder.newFolder().toPath(),"selected-sale");
        var tracker=mock(com.flippingfriend.session.OfferTracker.class);
        long at=now.getEpochSecond();
        var original=new com.flippingfriend.session.TrackedOffer(0,13263,false,1500,3,at-1200);original.setState("SELLING");
        when(tracker.getOffers()).thenReturn(List.of(original));when(tracker.getOffer(0)).thenReturn(original);
        var source=OfferEvent.builder("selected",at,"SELLING").accountId(account).authenticatedObservation(true)
            .eventIdentity("accepted-sale-source","session","0@"+(at-1200)+":13263").sequence(1).slot(0).item(13263,"Abyssal bludgeon")
            .price(1500).quantities(3,0).firstSeenAt(at-1200).salePolicyEvidenceVersion("1").build();
        var receipt=com.flippingfriend.companion.SalePolicyAcceptanceDeliveryTest.receipt(account,"selected-receipt",source,1199,at);
        var book=new com.flippingfriend.session.SellRelistIntent(storage);
        assertTrue(book.advise(original,account,true,tracker,source.getEventId(),receipt,at));
        original.setQuantityFilled(1);original.setSpent(1500);original.setState("CANCELLED_SELL");original.setCollected(true);
        when(tracker.getOffers()).thenReturn(List.of());when(tracker.getOffer(0)).thenReturn(null);when(tracker.getSettledOffer(0)).thenReturn(original);
        var reprice=book.collectedRequest(tracker,account);assertNotNull(reprice);assertEquals(2,reprice.getQuantity());
        assertEquals(480,reprice.getAcceptedPatienceSeconds());assertEquals(receipt,reprice.getAcceptedSalePolicyProof().getReceipt());
        long remainingCost=3203-SaleAccounting.allocateCost(3203,3,1);
        var remaining=PositionProjection.ready(13263,"Abyssal bludgeon",2,remainingCost,at-1200,at,"buy","episode")
            .withExitContext(new AcquisitionExitContext(1400,0,30).bind("original-without-stop"));
        var ledger=ledger(List.of(remaining),at);long end=at/300*300;
        var market=new MarketSnapshot(Map.of(13263,new ItemMetadata(13263,"Abyssal bludgeon",true,8,0)),
            Map.of(13263,new LatestPrice(1200,at,1150,at)),Map.of(),Map.of(),now);
        var bars=List.of(new Candle(end-600,1200,1150,100,100),new Candle(end-300,1200,1150,100,100));
        var eightHours=TradingHorizon.of(RiskProfile.MODERATE,CheckInterval.EIGHT_HOURS);
        var selected=pricing.quote(13263,1,2,account,ledger,Map.of(),Set.of(),market,bars,eightHours,now,false,reprice);
        assertEquals(SuggestionType.SELL,selected.getType());assertEquals(1199,selected.getPrice());assertEquals(1400,selected.getTargetSellPrice());
        assertFalse(selected.isLossCut());assertTrue(selected.isProfitKnown());
        assertEquals(tax.netProceeds(13263,1199,1)-SaleAccounting.allocateCost(remainingCost,2,1),selected.getExpectedProfit());
        assertEquals(tax.breakEvenSellPriceForCost(13263,SaleAccounting.allocateCost(remainingCost,2,1),1),selected.getBreakEvenPrice());
        assertEquals(SuggestionType.WAIT,pricing.quote(13263,1,2,account,ledger,Map.of(),Set.of(),market,List.of(),eightHours,now,false,reprice).getType());
        assertEquals(SuggestionType.WAIT,pricing.quote(13263,3,100,account,ledger,Map.of(),Set.of(),market,bars,eightHours,now,false,reprice).getType());
        var depressed=new MarketSnapshot(market.getMetadata(),Map.of(13263,new LatestPrice(800,at,750,at)),Map.of(),Map.of(),now);
        var lowBars=List.of(new Candle(end-600,800,750,100,100),new Candle(end-300,800,750,100,100));
        var protectedQuote=pricing.quote(13263,1,2,account,ledger,Map.of(),Set.of(),depressed,lowBars,eightHours,now,false,reprice);
        assertFalse("A past profitable reduction cannot create an unknown original stop",protectedQuote.isLossCut());
        if(protectedQuote.getType()==SuggestionType.SELL)assertTrue(protectedQuote.getExpectedProfit()>=0);
        assertEquals(remainingCost,ledger.getTradingPositions().get(0).getTotalCost());assertEquals(2,ledger.getTradingPositions().get(0).getQuantity());
    }
}
