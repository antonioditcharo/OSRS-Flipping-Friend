package com.flippingfriend.model;

import com.flippingfriend.*;
import com.flippingfriend.companion.CompanionClient;
import com.flippingfriend.core.*;
import com.flippingfriend.data.*;
import com.flippingfriend.session.*;
import java.time.Instant;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/** Real worker publication -> UI read, including early foreground waits and changed evidence. */
public class SaleMaintenanceDiagnosticsWorkflowTest {
    final long at=Instant.now().getEpochSecond();
    final String account=AccountIdentity.fromProfileKey("sale-diagnostics");
    final MarketDataService market=mock(MarketDataService.class);
    final AccountMonitor monitor=mock(AccountMonitor.class);
    final OfferTracker offers=mock(OfferTracker.class);
    final CompanionClient companion=mock(CompanionClient.class);
    final FlippingFriendConfig config=mock(FlippingFriendConfig.class);
    final AccountState state=mock(AccountState.class);
    final TrackedOffer offer=new TrackedOffer(0,561,false,150,3,at-3600);
    final SuggestionEngine engine;
    AccountLedgerSnapshot ledger(List<PositionProjection> positions) {
        return new AccountLedgerSnapshot(account,at,0,0,0,0,0,0,0,0,List.of()).withTradeEvidence(positions,List.of(),Map.of());
    }
    public SaleMaintenanceDiagnosticsWorkflowTest()throws Exception {
        when(config.riskProfile()).thenReturn(RiskProfile.MODERATE);when(config.checkInterval()).thenReturn(CheckInterval.FIVE_MINUTES);
        when(state.isLoggedIn()).thenReturn(true);
        // The foreground returns early; diagnostics must still publish for every active sale.
        when(state.isIronman()).thenReturn(true);when(monitor.getState()).thenReturn(state);
        offer.setState("SELLING");when(offers.getOffers()).thenReturn(List.of(offer));
        when(market.getSnapshot()).thenReturn(new MarketSnapshot(Map.of(),Map.of(561,new LatestPrice(120,at,110,at)),Map.of(),Map.of(),Instant.ofEpochSecond(at)));
        when(market.getSeries(anyInt(),anyString())).thenReturn(List.of(new Candle(at/300*300-300,120,110,100,100),new Candle(at/300*300-600,120,110,100,100)));
        when(companion.tradingLedger()).thenReturn(ledger(List.of(PositionProjection.ready(561,"Rune",3,300,at-3600,at,"source","episode"))));
        when(companion.lastLedger()).thenAnswer(call->companion.tradingLedger());
        var tax=new TaxCalculator();
        engine=new SuggestionEngine(market,null,null,null,new Explainer(),null,tax,monitor,null,mock(PositionBook.class),offers,
            new SellTimingEngine(new FillModel(),tax),null,config,null,null);
        var field=SuggestionEngine.class.getDeclaredField("companion");field.setAccessible(true);field.set(engine,companion);
        engine.setObservedAccount(account);
    }
    @Test public void workerPublishesButUiDoesNotEvaluateOrRequestAuthority() {
        engine.refresh(false);var rows=engine.saleMaintenanceDiagnostics();assertEquals(1,rows.size());
        assertEquals(SaleMaintenancePricing.Status.ELIGIBLE,rows.get(0).evaluation.status);
        clearInvocations(market,companion);engine.saleMaintenanceDiagnostics();
        verify(market,never()).getSeries(anyInt(),anyString());
        verify(companion).lastLedger();verify(companion).tradingLedger();verifyNoMoreInteractions(companion);
    }
    @Test public void recoveryCoverageSurvivesEarlyForegroundAndInvalidatesOnSourceCounterSettingsAndLogout() {
        engine.refreshRecoveryCoverage();var snapshot=engine.tradeRecovery();assertNotNull(snapshot);assertTrue(snapshot.evidenceCurrent);
        assertTrue(snapshot.items.stream().anyMatch(r->r.identity.equals(offer.getContinuationIdentity())));
        var refused=Suggestion.builder(SuggestionType.MODIFY_SELL).item(561,"Rune").slot(0).quantity(3).price(119)
            .maintenanceSource(offer.getContinuationIdentity()).build();
        engine.noteRecoveryRefusal(refused,Suggestion.waiting("Source acknowledgement pending","Wait for ordered source acceptance."));
        assertTrue(engine.tradeRecovery().items.stream().anyMatch(r->r.reason.contains("Source acknowledgement pending")));
        var current=companion.lastLedger();doReturn(current).when(companion).lastLedger();
        clearInvocations(market,companion);engine.tradeRecovery();
        verify(market,never()).getSeries(anyInt(),anyString());verify(companion,never()).tradingLedger();
        offer.setQuantityFilled(1);assertFalse(engine.tradeRecovery().evidenceCurrent);
        engine.refreshRecoveryCoverage();when(config.checkInterval()).thenReturn(CheckInterval.TWO_HOURS);assertFalse(engine.tradeRecovery().evidenceCurrent);
        engine.setObservedAccount(AccountIdentity.fromProfileKey("other"));assertNull(engine.tradeRecovery());
        engine.setObservedAccount(account);engine.refreshRecoveryCoverage();when(state.isLoggedIn()).thenReturn(false);assertNull(engine.tradeRecovery());
    }
    @Test public void changedCountersAccountSettingsAndLogoutWithdrawImmediately() {
        engine.refresh(false);offer.setQuantityFilled(1);
        assertNotNull(engine.saleMaintenanceDiagnostics().get(0).unavailable);
        engine.refresh(false);assertNotNull(engine.saleMaintenanceDiagnostics().get(0).evaluation);
        when(config.checkInterval()).thenReturn(CheckInterval.TWO_HOURS);
        assertNotNull(engine.saleMaintenanceDiagnostics().get(0).unavailable);
        engine.setObservedAccount(AccountIdentity.fromProfileKey("another"));
        assertNotNull(engine.saleMaintenanceDiagnostics().get(0).unavailable);
        when(state.isLoggedIn()).thenReturn(false);assertTrue(engine.saleMaintenanceDiagnostics().isEmpty());
    }
    @Test public void pendingLedgerAndChangedPricesNeverReusePreviousEligibility() {
        engine.refresh(false);when(companion.tradingLedger()).thenReturn(null);
        assertNotNull(engine.saleMaintenanceDiagnostics().get(0).unavailable);
        engine.refresh(false);assertNull(engine.saleMaintenanceDiagnostics().get(0).evaluation);
        assertTrue(engine.saleMaintenanceDiagnostics().get(0).unavailable.contains("Managed scope unavailable"));
        when(companion.tradingLedger()).thenReturn(ledger(List.of(PositionProjection.ready(561,"Rune",3,300,at-3600,at,"source","episode"))));
        engine.refresh(false);when(market.getSnapshot()).thenReturn(MarketSnapshot.empty());
        assertNotNull(engine.saleMaintenanceDiagnostics().get(0).unavailable);
    }
    @Test public void personalSalesAreExcludedAndPersonalExtrasAreBlocked() {
        when(companion.tradingLedger()).thenReturn(ledger(List.of()));engine.refresh(false);
        assertTrue(engine.saleMaintenanceDiagnostics().isEmpty());
        when(companion.tradingLedger()).thenReturn(ledger(List.of(PositionProjection.ready(561,"Rune",1,100,at-3600,at,"source","episode"))));
        engine.refresh(false);
        assertEquals(SaleMaintenancePricing.Status.UNPROVEN_QUANTITY,engine.saleMaintenanceDiagnostics().get(0).evaluation.status);
    }
    @Test public void historicalGapsRequireOneScopeCopyPerSaleCheckAndNewEvidenceStillRefuses()throws Exception {
        var gaps=new ArrayList<MissedFlipEvidence>();var scopes=new HashMap<String,Set<Integer>>();
        for(int i=0;i<1500;i++) {
            String id="historical-gap-"+i;
            gaps.add(new MissedFlipEvidence(id,"episode",562,"Unrelated item","RECONCILE",0,at-1,
                null,null,null,null,null,null,"Retained source uncertainty"));
            scopes.put(id,Set.of(562));
        }
        var current=ledger(List.of(PositionProjection.ready(561,"Rune",3,300,at-3600,at,"source","episode")))
            .withTradeEvidence(List.of(PositionProjection.ready(561,"Rune",3,300,at-3600,at,"source","episode")),gaps,scopes);
        class CountedScope extends HashMap<String,Set<Integer>> {
            int copies;
            @Override public void forEach(java.util.function.BiConsumer<? super String,? super Set<Integer>> action) {
                copies++;super.forEach(action);
            }
        }
        var counted=new CountedScope();counted.putAll(scopes);
        var field=AccountLedgerSnapshot.class.getDeclaredField("reconciliationScope");field.setAccessible(true);field.set(current,counted);
        when(companion.tradingLedger()).thenReturn(current);
        var check=SuggestionEngine.class.getDeclaredMethod("saleLedger",int.class,Instant.class);check.setAccessible(true);
        assertSame(current,check.invoke(engine,561,Instant.ofEpochSecond(at)));
        assertEquals("Historical gap count must not multiply full map copies",1,counted.copies);
        assertNull("The affected item remains paused",check.invoke(engine,562,Instant.ofEpochSecond(at)));
        when(companion.tradingLedger()).thenReturn(null);
        assertNull("Pending current evidence withdraws eligibility",check.invoke(engine,561,Instant.ofEpochSecond(at)));
        when(companion.tradingLedger()).thenReturn(current);
        assertNull("Freshness remains a current check",check.invoke(engine,561,Instant.ofEpochSecond(at+151)));
        engine.setObservedAccount(AccountIdentity.fromProfileKey("different-account"));
        assertNull("Account switching cannot reuse this qualification",check.invoke(engine,561,Instant.ofEpochSecond(at)));
    }
}
