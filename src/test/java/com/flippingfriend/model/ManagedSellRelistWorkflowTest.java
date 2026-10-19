package com.flippingfriend.model;

import com.flippingfriend.*;
import com.flippingfriend.companion.CompanionClient;
import com.flippingfriend.core.*;
import com.flippingfriend.data.*;
import com.flippingfriend.session.*;
import java.lang.reflect.Field;
import java.time.Instant;
import java.util.*;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/** Exercises the real decision chain after a player cancels, collects and restarts. */
public class ManagedSellRelistWorkflowTest {
    @Rule public TemporaryFolder folder = new TemporaryFolder();
    private final Instant now = Instant.now();
    private final String account = AccountIdentity.fromProfileKey("relist");
    private final TaxCalculator tax = new TaxCalculator();

    private final class Fixture {
        final CompanionClient companion = mock(CompanionClient.class);
        final OfferTracker offers = mock(OfferTracker.class);
        final MarketDataService market = mock(MarketDataService.class);
        final TradePlans plans=mock(TradePlans.class);
        final SuggestionEngine engine;
        Fixture(int remaining, long cost, int inventory, int price, boolean managed) throws Exception {
            var storage = TestStorage.rootedAt(folder.newFolder().toPath(), "relist");
            var intent = new SellRelistIntent(storage);
            var sale = new TrackedOffer(0, 561, false, 1200, 10, now.getEpochSecond() - 600);
            sale.setState("SELLING"); sale.setQuantityFilled(2);
            intent.advise(sale, account, managed);
            sale.setState("CANCELLED_SELL"); sale.setQuantityFilled(4); sale.setCollected(true);
            when(offers.getOffers()).thenReturn(List.of());
            when(offers.getSettledOffer(0)).thenReturn(sale);
            when(offers.listedForSale()).thenReturn(Map.of());
            when(offers.itemsBeingBought()).thenReturn(Set.of());
            var state = new AccountState(true, true, false, 100000000, 0, 0, 8, 8, 0, false,
                Map.of(561, inventory), Map.of(561, inventory), Map.of());
            var monitor = mock(AccountMonitor.class); when(monitor.getState()).thenReturn(state);
            var metadata = mock(ItemMetadata.class); when(metadata.getName()).thenReturn("Nature rune");
            var snapshot = new MarketSnapshot(Map.of(561, metadata), Map.of(561,
                new LatestPrice(price, now.getEpochSecond(), price, now.getEpochSecond())), Map.of(), Map.of(), now);
            when(market.getSnapshot()).thenReturn(snapshot);
            long end=now.getEpochSecond()/300*300;
            when(market.getSeries(anyInt(), anyString())).thenReturn(List.of(new Candle(end-300,price,price,100,100),new Candle(end-600,price,price,100,100)));
            var config = mock(FlippingFriendConfig.class);
            when(config.riskProfile()).thenReturn(RiskProfile.MODERATE);
            when(config.checkInterval()).thenReturn(CheckInterval.FIVE_MINUTES);
            var skips = mock(SkipList.class); when(skips.skipped(any())).thenReturn(Set.of());
            engine = new SuggestionEngine(market, new FeatureEngine(), null, null, new Explainer(), null,
                tax, monitor, null, mock(PositionBook.class), offers, new SellTimingEngine(new FillModel(), tax),
                plans, config, skips, null);
            inject(engine, "companion", companion);
            inject(engine, "sellRelist", new SellRelistIntent(storage)); // Recover the actual saved intent.
            engine.setObservedAccount(account);
            var trades = managed ? List.of(PositionProjection.ready(561, "Nature rune", remaining, cost,
                now.getEpochSecond()-100000, now.getEpochSecond(), "buy", "buy-offer")) : List.<PositionProjection>of();
            when(companion.tradingLedger()).thenReturn(ledger(trades, List.of(), Map.of()));
        }
        Suggestion next() { return engine.refresh(false); }
        Suggestion maintenance(int quantity) throws Exception {
            return maintenance(quantity,100000,100000);
        }
        Suggestion maintenance(int quantity,long ageSeconds,long idleSeconds) throws Exception {
            inject(engine,"sellRelist",null);
            var live = new TrackedOffer(0,561,false,1200,quantity,now.getEpochSecond()-ageSeconds);
            live.observeFillAt(now.getEpochSecond()-idleSeconds);
            live.setState("SELLING");
            when(offers.getOffers()).thenReturn(List.of(live));
            when(offers.listedForSale()).thenReturn(Map.of(561,quantity));
            return next();
        }
    }
    @Test public void actualManagedGuidanceKeepsItsTargetAndDoesNotTurnItIntoABreakEvenReprice()throws Exception {
        var f=new Fixture(6,5881,6,1000,true);
        when(f.companion.tradingLedger()).thenReturn(ledger(List.of(PositionProjection.ready(561,"Nature rune",6,5881,
            now.getEpochSecond()-60,now.getEpochSecond(),"buy","buy-offer")),List.of(),Map.of()));
        doAnswer(call->{Position p=call.getArgument(0);p.setTargetSellPrice(1200);p.setPredictedSellMinutes(120);return null;}).when(f.plans).applyTo(any());
        assertEquals(1200,f.next().getPrice());noReprice(f.maintenance(6));
    }
    @Test public void actualManagedMaintenanceWaitsForFullPredictionAndCurrentProgress()throws Exception {
        var f=new Fixture(6,5881,6,1100,true);
        doAnswer(call->{Position p=call.getArgument(0);p.setPredictedSellMinutes(120);return null;}).when(f.plans).applyTo(any());
        noReprice(f.maintenance(6,119*60,119*60));
        noReprice(f.maintenance(6,150*60,60));
        assertEquals(SuggestionType.MODIFY_SELL,f.maintenance(6,150*60,10*60).getType());
    }
    @Test public void anActualFreshStopDoesNotWaitForRoutineMaintenancePatience()throws Exception {
        var f=new Fixture(6,5881,6,800,true);
        doAnswer(call->{Position p=call.getArgument(0);p.setStopPrice(900);p.setPredictedSellMinutes(120);return null;}).when(f.plans).applyTo(any());
        var next=f.maintenance(6,60,0);assertNotNull(next);assertEquals(SuggestionType.MODIFY_SELL,next.getType());assertTrue(next.isLossCut());
    }
    private AccountLedgerSnapshot ledger(List<PositionProjection> trades, List<MissedFlipEvidence> missed,
            Map<String, Set<Integer>> scope) {
        return new AccountLedgerSnapshot(account, now.getEpochSecond(), 0, 0, 0, 0, 0, 0, 0, 0, List.of())
            .withTradeEvidence(trades, missed, scope);
    }
    private static void inject(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name); field.setAccessible(true); field.set(target, value);
    }
    @Test public void managedRelistRetainsItsExactCostFloorAndProfitAfterRestart() throws Exception {
        var f = new Fixture(6, 5881, 6006, 1000, true);
        var next = f.next(); assertEquals(SuggestionType.SELL, next.getType());
        assertEquals(6, next.getQuantity());
        assertTrue("A relist must not turn a healthy managed trade into an unforced loss",
            tax.netProceeds(561, next.getPrice(), 6) >= 5881);
        assertTrue(next.isProfitKnown());
        assertEquals(tax.netProceeds(561, next.getPrice(), 6)-5881, next.getExpectedProfit());
    }
    @Test public void relistCapsToVisibleAndDurableRemaindersWithoutBorrowingPersonalUnits() throws Exception {
        var f = new Fixture(3, 125, 6003, 44, true);
        var next = f.next(); assertEquals(SuggestionType.SELL, next.getType());
        assertEquals(3, next.getQuantity()); assertTrue(next.isProfitKnown());
        assertEquals(tax.netProceeds(561, next.getPrice(), 3)-125, next.getExpectedProfit());
        f = new Fixture(6, 251, 2, 44, true);
        next = f.next(); assertEquals(SuggestionType.SELL, next.getType());
        assertEquals(2, next.getQuantity());
        assertEquals(tax.netProceeds(561, next.getPrice(), 2)-SaleAccounting.allocateCost(251,6,2), next.getExpectedProfit());
    }
    @Test public void pendingOrMissingLedgerCannotDowngradeAManagedRelistToPersonalGuidance() throws Exception {
        var f = new Fixture(6, 5881, 6, 1000, true);
        when(f.companion.tradingLedger()).thenReturn(null);
        assertEquals(SuggestionType.WAIT, f.next().getType());
    }
    @Test public void affectedHistoricalStockCannotRelistThroughThePersonalSalePath() throws Exception {
        var f = new Fixture(0, 0, 6, 1000, false);
        var gap = new MissedFlipEvidence("gap", "old-sale", 561, "Nature rune", "RECONCILE", 0,
            now.getEpochSecond(), null, null, null, null, null, null, "conflicting fills");
        when(f.companion.tradingLedger()).thenReturn(ledger(List.of(), List.of(gap), Map.of("gap", Set.of(561))));
        assertEquals(SuggestionType.WAIT, f.next().getType());
    }
    @Test public void aTrulyExternalObservedSaleKeepsItsCostAndProfitUnknown() throws Exception {
        var next = new Fixture(0, 0, 6, 1000, false).next();
        assertEquals(SuggestionType.SELL, next.getType()); assertEquals(6, next.getQuantity());
        assertEquals(999, next.getPrice()); assertFalse(next.isProfitKnown());
    }
    @Test public void managedIntentCannotBecomePersonalWhenThePositionDisappears() throws Exception {
        var f = new Fixture(6,5881,6,1000,true);
        when(f.companion.tradingLedger()).thenReturn(ledger(List.of(),List.of(),Map.of()));
        assertEquals(SuggestionType.WAIT,f.next().getType());
    }
    @Test public void futureQuotesWithdrawRelistAdvice() throws Exception {
        for (long delta : new long[]{60}) {
            var f = new Fixture(6,5881,6,1000,true);
            var original=f.market.getSnapshot();
            when(f.market.getSnapshot()).thenReturn(new MarketSnapshot(original.getMetadata(),
                Map.of(561,new LatestPrice(1000,now.getEpochSecond()+delta,1000,now.getEpochSecond())),Map.of(),Map.of(),now));
            assertEquals(SuggestionType.WAIT,f.next().getType());
        }
    }
    @Test public void quietManagedRelistUsesANonLossLimitWhilePersonalStockStillNeedsAFreshQuote() throws Exception {
        for(boolean managed:new boolean[]{true,false}) {
            var f=new Fixture(6,5881,6,1000,managed);var original=f.market.getSnapshot();
            when(f.market.getSnapshot()).thenReturn(new MarketSnapshot(original.getMetadata(),Map.of(561,
                new LatestPrice(1000,now.getEpochSecond()-1200,1000,now.getEpochSecond())),Map.of(),Map.of(),now));
            var next=f.next();assertEquals(managed?SuggestionType.SELL:SuggestionType.WAIT,next.getType());
            if(managed) {assertTrue(next.getDetail().contains("quiet"));assertTrue(tax.netProceeds(561,next.getPrice(),6)>=5881);}
        }
    }
    @Test public void anotherOpenBuyPreventsSellingAroundAnAccumulatingPosition() throws Exception {
        var f = new Fixture(6,5881,6,1000,true);
        when(f.offers.itemsBeingBought()).thenReturn(Set.of(561));
        assertEquals(SuggestionType.WAIT,f.next().getType());
    }
    @Test public void explicitSellOnlyCanStillAuthorizeALossAndKeepItsCostKnown() throws Exception {
        var f = new Fixture(6,5881,6,1000,true); f.engine.setSellOnly(true);
        var next=f.next(); assertEquals(SuggestionType.SELL,next.getType()); assertTrue(next.isProfitKnown());
        assertEquals(tax.netProceeds(561,next.getPrice(),6)-5881,next.getExpectedProfit());
    }
    @Test public void noInventoryWithdrawsTheRelistWithoutInventingDisposal() throws Exception {
        var f = new Fixture(6,5881,0,1000,true);
        assertEquals(SuggestionType.WAIT,f.next().getType());
        assertEquals(6,f.companion.tradingLedger().getTradingPositions().get(0).getQuantity());
    }
    private static void noReprice(Suggestion advice) {
        assertTrue("Unsafe maintenance must not produce a sell cancellation/reprice",
            advice==null || advice.getType()==SuggestionType.WAIT);
    }
    @Test public void maintenanceCannotDowngradeMissingLedgerToAnUnpricedSale() throws Exception {
        var f=new Fixture(6,5881,6,1000,true);
        when(f.companion.tradingLedger()).thenReturn(null);
        noReprice(f.maintenance(6));
    }
    @Test public void maintenanceKeepsAffectedItemsPaused() throws Exception {
        var f=new Fixture(0,0,6,1000,false);
        var gap=new MissedFlipEvidence("gap","old-sale",561,"Nature rune","RECONCILE",0,
            now.getEpochSecond(),null,null,null,null,null,null,"conflicting fills");
        when(f.companion.tradingLedger()).thenReturn(ledger(List.of(),List.of(gap),Map.of("gap",Set.of(561))));
        noReprice(f.maintenance(6));
    }
    @Test public void aMixedCostOfferCannotLoseItsKnownSubsetFloorDuringMaintenance() throws Exception {
        var f=new Fixture(3,3500,6,1000,true);
        noReprice(f.maintenance(6));
    }
    @Test public void maintenanceRequiresCurrentAccountAndLedgerTimestamp() throws Exception {
        for(boolean foreign:new boolean[]{false,true}) {
            var f=new Fixture(0,0,6,1000,false);
            var value=new AccountLedgerSnapshot(foreign?AccountIdentity.fromProfileKey("other"):account,
                now.getEpochSecond()-(foreign?0:151),0,0,0,0,0,0,0,0,List.of()).withTradeEvidence(List.of(),List.of());
            when(f.companion.tradingLedger()).thenReturn(value);
            noReprice(f.maintenance(6));
        }
    }
    @Test public void maintenanceDoesNotCancelUsingStaleOrFutureSaleQuotes() throws Exception {
        for(long offset:new long[]{-301,60}) {
            var f=new Fixture(6,5881,6,1000,true);var original=f.market.getSnapshot();
            when(f.market.getSnapshot()).thenReturn(new MarketSnapshot(original.getMetadata(),Map.of(561,
                new LatestPrice(1000,now.getEpochSecond()+offset,1000,now.getEpochSecond())),Map.of(),Map.of(),now));
            noReprice(f.maintenance(6));
        }
    }
    @Test public void cleanExternalSaleMaintenanceStillQuotesWithoutInventingProfit() throws Exception {
        var advice=new Fixture(0,0,6,1000,false).maintenance(6);
        assertNotNull(advice);assertEquals(SuggestionType.MODIFY_SELL,advice.getType());assertFalse(advice.isProfitKnown());
    }
    @Test public void aProvenUnrelatedGapDoesNotFreezeThisSale() throws Exception {
        var f=new Fixture(6,5881,6,1000,true);
        var gap=new MissedFlipEvidence("gap","other-sale",562,"Other item","RECONCILE",0,
            now.getEpochSecond(),null,null,null,null,null,null,"conflicting fills");
        var trades=f.companion.tradingLedger().getTradingPositions();
        when(f.companion.tradingLedger()).thenReturn(ledger(trades,List.of(gap),Map.of("gap",Set.of(562))));
        assertEquals(SuggestionType.SELL,f.next().getType());
        var advice=f.maintenance(6);assertNotNull(advice);assertEquals(SuggestionType.MODIFY_SELL,advice.getType());
        assertTrue(advice.isProfitKnown());assertTrue(tax.netProceeds(561,advice.getPrice(),6)>=5881);
    }
    @Test public void anUnidentifiedGapWithdrawsMaintenanceAndRelistingTogether() throws Exception {
        var f=new Fixture(0,0,6,1000,false);
        var gap=new MissedFlipEvidence("gap",null,0,null,"RECONCILE",0,
            now.getEpochSecond(),null,null,null,null,null,null,"missing item");
        when(f.companion.tradingLedger()).thenReturn(ledger(List.of(),List.of(gap),Map.of("gap",Set.of())));
        assertEquals(SuggestionType.WAIT,f.next().getType());noReprice(f.maintenance(6));
    }
    @Test public void aFutureLedgerOrMissingQuoteTimeCannotAuthorizeMaintenance() throws Exception {
        var f=new Fixture(0,0,6,1000,false);
        when(f.companion.tradingLedger()).thenReturn(new AccountLedgerSnapshot(account,now.getEpochSecond()+60,
            0,0,0,0,0,0,0,0,List.of()).withTradeEvidence(List.of(),List.of()));
        noReprice(f.maintenance(6));
        f=new Fixture(0,0,6,1000,false);var original=f.market.getSnapshot();
        when(f.market.getSnapshot()).thenReturn(new MarketSnapshot(original.getMetadata(),Map.of(561,
            new LatestPrice(1000,null,1000,now.getEpochSecond())),Map.of(),Map.of(),now));
        noReprice(f.maintenance(6));
    }
}
