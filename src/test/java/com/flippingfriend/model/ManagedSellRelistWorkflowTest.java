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
        final AccountMonitor monitor=mock(AccountMonitor.class);
        final TradePlans plans=mock(TradePlans.class);
        final PluginStorage storage;
        final SuggestionEngine engine;
        Fixture(int remaining, long cost, int inventory, int price, boolean managed) throws Exception {
            storage = TestStorage.rootedAt(folder.newFolder().toPath(), "relist");
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
            when(monitor.getState()).thenReturn(state);
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
        void context(int target,int stop,double minutes,long openedAt) {
            var p=companion.tradingLedger().getTradingPositions().get(0);
            when(companion.tradingLedger()).thenReturn(ledger(List.of(PositionProjection.ready(p.getItemId(),p.getItemName(),
                p.getQuantity(),p.getTotalCost(),openedAt,now.getEpochSecond(),p.getSourceEventId(),p.getSourceOfferIdentity())
                .withExitContext(new AcquisitionExitContext(target,stop,minutes).bind("original-buy"))),List.of(),Map.of()));
        }
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
    @Test public void legacyRelistKeepsItsTargetButSupportedMaintenanceMayReduceIt()throws Exception {
        var f=new Fixture(6,5881,6,1000,true);
        f.context(1200,0,120,now.getEpochSecond()-60);
        assertEquals(1200,f.next().getPrice());assertEquals(SuggestionType.MODIFY_SELL,f.maintenance(6).getType());
    }
    private void bindReducedRelist(Fixture f)throws Exception {
        var collected=f.offers.getSettledOffer(0);
        var working=collected.copyObservation();working.setState("SELLING");working.setQuantityFilled(2);working.setCollected(false);
        when(f.offers.getOffers()).thenReturn(List.of(working));when(f.offers.getOffer(0)).thenReturn(working);
        var book=new SellRelistIntent(f.storage);
        assertTrue(book.advise(working,account,true,f.offers,"accepted-working-sale"));
        when(f.offers.getOffers()).thenReturn(List.of());when(f.offers.getOffer(0)).thenReturn(null);
        assertEquals(6,book.collectedRequest(f.offers,account).getQuantity());
        when(f.offers.getSettledOffer(0)).thenReturn(null);
        inject(f.engine,"sellRelist",new SellRelistIntent(f.storage));
    }
    @Test public void missingRelistMarketSupportCannotResurrectTheAbandonedTargetAfterRestart()throws Exception {
        var f=new Fixture(6,5001,6,1000,true);f.context(1200,0,120,now.getEpochSecond()-60);bindReducedRelist(f);
        var supported=f.next();assertEquals(SuggestionType.SELL,supported.getType());assertEquals(999,supported.getPrice());
        when(f.market.getSeries(anyInt(),anyString())).thenReturn(List.of());
        inject(f.engine,"sellRelist",new SellRelistIntent(f.storage));
        assertEquals("Lost volume evidence must withhold the relist, not restore 1200",SuggestionType.WAIT,f.next().getType());
        var retained=new SellRelistIntent(f.storage).collectedRequest(f.offers,account);
        assertNotNull(retained);assertEquals(6,retained.getQuantity());assertEquals("accepted-working-sale",retained.getSaleRepriceSource());
        assertEquals(5001,f.companion.tradingLedger().getTradingPositions().get(0).getTotalCost());
    }
    @Test public void relistWaitDoesNotHideUnrelatedReadyStockAndRecoversWhenSupportReturns()throws Exception {
        var f=new Fixture(6,5001,6,1000,true);f.context(1200,0,120,now.getEpochSecond()-60);bindReducedRelist(f);
        var supported=f.market.getSeries(561,"5m");when(f.market.getSeries(eq(561),anyString())).thenReturn(List.of());
        addStock(f,562,3,251,1000,0);
        var next=f.next();assertEquals(SuggestionType.SELL,next.getType());assertEquals(562,next.getItemId());
        assertTrue(f.engine.saleCandidates().stream().noneMatch(s->s.getItemId()==561));
        when(f.market.getSeries(eq(561),anyString())).thenReturn(supported);
        next=f.next();assertEquals(561,next.getItemId());assertEquals(999,next.getPrice());assertEquals(6,next.getQuantity());
    }
    @Test public void windDownRelistRechecksItsExactFloorAfterPriceStabilization()throws Exception {
        var f=new Fixture(6,5881,6,1000,true);f.context(1200,0,120,now.getEpochSecond()-60);bindReducedRelist(f);
        f.engine.setSellOnly(true);
        var field=SuggestionEngine.class.getDeclaredField("advisedPrices");field.setAccessible(true);
        var stabilized=(AdvisedPriceTracker)field.get(f.engine);
        int floor=tax.breakEvenSellPriceForCost(561,5881,6);
        stabilized.price(561,false,floor-1,now.getEpochSecond());
        var next=f.next();assertEquals(SuggestionType.SELL,next.getType());assertFalse(next.isLossCut());
        assertEquals("A sticky old price cannot cross the accepted maintenance floor",floor,next.getPrice());
        assertTrue(tax.netProceeds(561,next.getPrice(),6)>=5881);
    }
    @Test public void anOrdinaryRelistCannotHideAnotherHoldingsFreshStop()throws Exception {
        var f=new Fixture(6,5881,6,1000,true);var original=f.companion.tradingLedger().getTradingPositions().get(0);
        var stopped=PositionProjection.ready(562,"Chaos rune",6,6000,now.getEpochSecond()-600,now.getEpochSecond(),"buy-2","episode-2")
            .withExitContext(new AcquisitionExitContext(1200,900,120).bind("buy-2"));
        when(f.companion.tradingLedger()).thenReturn(ledger(List.of(original,stopped),List.of(),Map.of()));
        when(f.monitor.getState()).thenReturn(new AccountState(true,true,false,100000000,0,0,8,8,0,false,
            Map.of(561,6,562,6),Map.of(561,6,562,6),Map.of()));
        var snapshot=f.market.getSnapshot();var metadata=new HashMap<>(snapshot.getMetadata());metadata.put(562,new ItemMetadata(562,"Chaos rune",false,1000,0));
        when(f.market.getSnapshot()).thenReturn(new MarketSnapshot(metadata,Map.of(561,new LatestPrice(1000,now.getEpochSecond(),1000,now.getEpochSecond()),
            562,new LatestPrice(850,now.getEpochSecond(),800,now.getEpochSecond())),Map.of(),Map.of(),now));
        var next=f.next();assertEquals(562,next.getItemId());assertTrue(next.isLossCut());
        assertEquals("The unrelated relist promise remains intact",6,new SellRelistIntent(f.storage).collectedRequest(f.offers,account).getQuantity());
    }
    @Test public void aListedFreshStopOutranksAnOrdinaryRelistAndItsEarlierWriteBackoff()throws Exception {
        var f=new Fixture(6,5881,6,1000,true);var original=f.companion.tradingLedger().getTradingPositions().get(0);
        var stopped=PositionProjection.ready(562,"Chaos rune",6,6000,now.getEpochSecond()-600,now.getEpochSecond(),"buy-2","episode-2")
            .withExitContext(new AcquisitionExitContext(1200,900,120).bind("buy-2"));
        when(f.companion.tradingLedger()).thenReturn(ledger(List.of(original,stopped),List.of(),Map.of()));
        var live=new TrackedOffer(1,562,false,1200,6,now.getEpochSecond()-60);live.setState("SELLING");
        when(f.offers.getOffers()).thenReturn(List.of(live));when(f.offers.getOffer(1)).thenReturn(live);when(f.offers.listedForSale()).thenReturn(Map.of(562,6));
        var snapshot=f.market.getSnapshot();var metadata=new HashMap<>(snapshot.getMetadata());metadata.put(562,new ItemMetadata(562,"Chaos rune",false,1000,0));
        when(f.market.getSnapshot()).thenReturn(new MarketSnapshot(metadata,Map.of(561,new LatestPrice(1000,now.getEpochSecond(),1000,now.getEpochSecond()),
            562,new LatestPrice(850,now.getEpochSecond(),800,now.getEpochSecond())),Map.of(),Map.of(),now));
        var ordinary=Suggestion.builder(SuggestionType.MODIFY_SELL).item(562,"Chaos rune").slot(1).quantity(6).price(1100).maintenanceSource(live.getContinuationIdentity()).build();
        assertTrue(f.engine.deferUnsavedMaintenance(ordinary,now.getEpochSecond()));
        var next=f.next();assertEquals(SuggestionType.MODIFY_SELL,next.getType());assertEquals(562,next.getItemId());assertTrue(next.isLossCut());
        assertEquals(6,new SellRelistIntent(f.storage).collectedRequest(f.offers,account).getQuantity());
    }
    @Test public void actualManagedMaintenanceUsesSupportedDisplacementAndCurrentProgress()throws Exception {
        var f=new Fixture(6,5881,6,1100,true);
        f.context(1200,0,120,now.getEpochSecond()-100000);
        assertEquals(SuggestionType.MODIFY_SELL,f.maintenance(6,119*60,119*60).getType());
        noReprice(f.maintenance(6,150*60,60));
        assertEquals(SuggestionType.MODIFY_SELL,f.maintenance(6,150*60,10*60).getType());
    }
    @Test public void anActualFreshStopDoesNotWaitForRoutineMaintenancePatience()throws Exception {
        var f=new Fixture(6,5881,6,800,true);
        f.context(1200,900,120,now.getEpochSecond()-100000);
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
    @Test public void aCollectedSlotCanBeReusedBeforeRestartWithoutLosingManagedRelistGuidance() throws Exception {
        var f = new Fixture(3,125,6003,44,true);
        assertEquals(SuggestionType.SELL,f.next().getType()); // Checkpoint the exact cancelled/collected episode.
        when(f.offers.getSettledOffer(0)).thenReturn(null); // Slot reused; the tracker no longer retains it.
        inject(f.engine,"sellRelist",new SellRelistIntent(f.storage));
        var next=f.next();assertEquals(SuggestionType.SELL,next.getType());assertEquals(3,next.getQuantity());
        assertTrue(next.getHeadline().contains("Relist"));assertTrue(next.isProfitKnown());
        assertEquals(tax.netProceeds(561,next.getPrice(),3)-125,next.getExpectedProfit());
        // Losing price/ledger evidence still withdraws this retained hint, without changing the acquisition.
        when(f.companion.tradingLedger()).thenReturn(null);
        assertEquals(SuggestionType.WAIT,f.next().getType());
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
    private void addStock(Fixture f,int item,int quantity,long cost,int price,int stop) {
        var trades=new ArrayList<>(f.companion.tradingLedger().getTradingPositions());
        var stock=PositionProjection.ready(item,"Stock "+item,quantity,cost,now.getEpochSecond()-600,now.getEpochSecond(),"buy-"+item,"episode-"+item);
        if(stop>0)stock=stock.withExitContext(new AcquisitionExitContext(price+400,stop,120).bind("buy-"+item));
        trades.add(stock);when(f.companion.tradingLedger()).thenReturn(ledger(trades,List.of(),Map.of()));
        var capture=f.monitor.getState();var held=new HashMap<>(capture.getHoldings());held.put(item,quantity);
        var inventory=new HashMap<>(capture.getInventoryHoldings());inventory.put(item,quantity);
        when(f.monitor.getState()).thenReturn(new AccountState(true,true,false,100000000,0,0,8,8,0,false,held,inventory,Map.of()));
        var original=f.market.getSnapshot();var metadata=new HashMap<>(original.getMetadata());
        metadata.put(item,new ItemMetadata(item,"Stock "+item,false,1000,0));
        var prices=new HashMap<>(original.getLatest());prices.put(item,new LatestPrice(price,now.getEpochSecond(),price,now.getEpochSecond()));
        when(f.market.getSnapshot()).thenReturn(new MarketSnapshot(metadata,prices,Map.of(),Map.of(),now));
    }
    @Test public void queueRetainsEveryRelistWithExactCapsAndNoGenericOversizedDuplicate()throws Exception {
        var f=new Fixture(6,5881,6,1000,true);addStock(f,562,10,251,1000,0);
        var second=new TrackedOffer(1,562,false,1200,8,now.getEpochSecond()-600);second.setState("SELLING");second.setQuantityFilled(2);
        var book=new SellRelistIntent(f.storage);book.advise(second,account,true);
        second.setState("CANCELLED_SELL");second.setQuantityFilled(5);second.setCollected(true);when(f.offers.getSettledOffer(1)).thenReturn(second);
        var first=f.next();assertTrue(first.getHeadline().contains("Relist"));
        var queue=f.engine.saleCandidates();assertEquals(2,queue.size());assertSame(first,queue.get(0));
        var next=queue.get(1);assertEquals(562,next.getItemId());assertEquals(3,next.getQuantity());
        assertEquals(tax.netProceeds(562,next.getPrice(),3)-SaleAccounting.allocateCost(251,10,3),next.getExpectedProfit());
        assertEquals(2,new SellRelistIntent(f.storage).collectedRequests(f.offers,account).size());
        f.engine.setObservedAccount(AccountIdentity.fromProfileKey("another"));assertTrue(f.engine.saleCandidates().isEmpty());
    }
    @Test public void unrelatedReadyStockAndAllStopsRemainAvailableAfterTheFirstRelist()throws Exception {
        var f=new Fixture(6,5881,6,1000,true);addStock(f,562,3,251,1000,0);addStock(f,563,2,2000,800,900);
        var next=f.next();assertEquals(563,next.getItemId());assertTrue(next.isLossCut());
        var queue=f.engine.saleCandidates();assertEquals(3,queue.size());assertSame(next,queue.get(0));
        assertEquals(561,queue.get(1).getItemId());assertEquals(562,queue.get(2).getItemId());
        assertEquals(6,new SellRelistIntent(f.storage).collectedRequest(f.offers,account).getQuantity());
        when(f.companion.tradingLedger()).thenReturn(null);f.next();assertTrue(f.engine.saleCandidates().isEmpty());
    }
    @Test public void normalExitCapsToInventoryInsteadOfIncludingCachedBankStock()throws Exception {
        var f=new Fixture(6,5881,2,1000,true);inject(f.engine,"sellRelist",null);
        when(f.monitor.getState()).thenReturn(new AccountState(true,true,false,100000000,0,0,8,8,0,true,
            Map.of(561,6),Map.of(561,2),Map.of(561,4)));
        var next=f.next();assertEquals(SuggestionType.SELL,next.getType());assertEquals(2,next.getQuantity());
        assertEquals(tax.netProceeds(561,next.getPrice(),2)-SaleAccounting.allocateCost(5881,6,2),next.getExpectedProfit());
        assertEquals(6,f.companion.tradingLedger().getTradingPositions().get(0).getQuantity());
    }
    @Test public void everyListedStopPrecedesHealthyRelistsInTheFinalAuthorityQueue()throws Exception {
        var f=new Fixture(6,5881,6,1000,true);addStock(f,562,3,3000,800,900);addStock(f,563,2,2000,800,900);
        var first=new TrackedOffer(1,562,false,1200,3,now.getEpochSecond()-60);first.setState("SELLING");
        var second=new TrackedOffer(2,563,false,1200,2,now.getEpochSecond()-60);second.setState("SELLING");
        when(f.offers.getOffers()).thenReturn(List.of(first,second));when(f.offers.getOffer(1)).thenReturn(first);when(f.offers.getOffer(2)).thenReturn(second);
        when(f.offers.listedForSale()).thenReturn(Map.of(562,3,563,2));
        var next=f.next();assertEquals(SuggestionType.MODIFY_SELL,next.getType());assertTrue(next.isLossCut());
        var queue=f.engine.saleCandidates();assertEquals(3,queue.size());
        assertTrue(queue.get(0).isLossCut());assertTrue(queue.get(1).isLossCut());assertFalse(queue.get(2).isLossCut());
        assertEquals(561,queue.get(2).getItemId());
    }
    @Test public void equippedAndBankedAcquisitionsAreRetainedWithoutAnUnplaceableSellInstruction()throws Exception {
        for(boolean equipped:new boolean[]{false,true}) {
            var f=new Fixture(6,5881,0,1000,true);inject(f.engine,"sellRelist",null);
            when(f.monitor.getState()).thenReturn(new AccountState(true,true,false,100000000,0,0,8,8,0,true,
                Map.of(561,6),Map.of(),equipped?Map.of():Map.of(561,6),0,equipped?Map.of(561,6):Map.of()));
            var next=f.next();assertTrue(next==null || next.getType()!=SuggestionType.SELL);assertTrue(f.engine.saleCandidates().isEmpty());
            assertEquals(6,f.companion.tradingLedger().getTradingPositions().get(0).getQuantity());
            assertEquals(5881,f.companion.tradingLedger().getTradingPositions().get(0).getTotalCost());
        }
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
