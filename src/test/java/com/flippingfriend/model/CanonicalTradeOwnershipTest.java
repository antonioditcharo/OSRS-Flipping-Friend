package com.flippingfriend.model;
import com.flippingfriend.companion.CompanionClient;
import com.flippingfriend.core.*;
import com.flippingfriend.session.*;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
public class CanonicalTradeOwnershipTest {
    @Test public void durableExitContextWinsOverUnrelatedItemAdviceAndLegacyPlansRemainUnknown() throws Exception {
        var local=new TradePlans(mock(com.flippingfriend.data.PluginStorage.class));
        local.plan(561,999,800,20,5000);
        var engine=new SuggestionEngine(null,null,null,null,null,null,null,null,null,mock(PositionBook.class),null,null,
            local,null,null,null);
        CompanionClient companion=mock(CompanionClient.class);
        var field=SuggestionEngine.class.getDeclaredField("companion");field.setAccessible(true);field.set(engine,companion);
        var p=PositionProjection.ready(561,"Nature rune",6,600,1,2,"partial-sale","sale");
        var context=new AcquisitionExitContext(140,90,480).bind("original-buy");
        for(boolean known:new boolean[]{true,false}) {
            var ledger=new AccountLedgerSnapshot(AccountIdentity.fromProfileKey("one"),100,0,0,0,0,0,0,0,0,List.of())
                .withTradeEvidence(List.of(known?p.withExitContext(context):p),List.of());
            when(companion.tradingLedger()).thenReturn(ledger);
            var held=engine.tradingPositions().iterator().next();
            assertEquals(known?140:0,held.getTargetSellPrice());assertEquals(known?90:0,held.getStopPrice());
            assertEquals(known?480:0,held.getPredictedSellMinutes(),0);
            assertEquals(6,held.getQuantity());assertEquals(600,held.getTotalCost());
        }
    }
    @Test public void unidentifiedReconciliationMeansUnavailableHoldingsRatherThanNoPurchases() throws Exception {
        var engine=new SuggestionEngine(null,null,null,null,null,null,null,null,null,mock(PositionBook.class),null,null,
            mock(TradePlans.class),null,null,null);
        CompanionClient companion=mock(CompanionClient.class);
        var field=SuggestionEngine.class.getDeclaredField("companion"); field.setAccessible(true); field.set(engine,companion);
        var unresolved = new MissedFlipEvidence("unidentified-clear",null,0,null,"RECONCILE",0,2,
            null,null,null,null,null,null,"offer cleared before a terminal observation");
        var ledger=new AccountLedgerSnapshot(AccountIdentity.fromProfileKey("one"),100,1,1,0,1,98,98,2,2,List.of())
            .withTradeEvidence(List.of(PositionProjection.ready(561,"Nature rune",60,6000,1,2,"partial","sell")),
                List.of(unresolved));
        assertTrue(ledger.isValid()); // Recorded totals remain usable independently.
        when(companion.tradingLedger()).thenReturn(ledger);
        assertFalse(engine.hasTradeEvidence());
        assertTrue(engine.tradingPositions().isEmpty());
        assertEquals(98,ledger.getProfitLower());
    }
    @Test public void liveExitOwnershipUsesOnlyRemainingDurableAcquisitions() throws Exception {
        PositionBook local=mock(PositionBook.class);
        when(local.all()).thenReturn(List.of(new Position(561,"Nature rune",6100,10000,1,true)));
        var engine=new SuggestionEngine(null,null,null,null,null,null,null,null,null,local,null,null,
            mock(TradePlans.class),null,null,null);
        CompanionClient companion=mock(CompanionClient.class);
        var field=SuggestionEngine.class.getDeclaredField("companion"); field.setAccessible(true); field.set(engine,companion);
        var ledger=new AccountLedgerSnapshot(AccountIdentity.fromProfileKey("one"),100,0,0,0,0,0,0,0,0,List.of())
            .withTradeEvidence(List.of(PositionProjection.ready(561,"Nature rune",60,6000,1,2,"partial","sell")),List.of());
        when(companion.tradingLedger()).thenReturn(ledger);
        var held=engine.tradingPositions(); assertEquals(1,held.size());
        assertEquals(60,held.iterator().next().getQuantity()); assertEquals(6000,held.iterator().next().getTotalCost());
        when(companion.tradingLedger()).thenReturn(null);
        assertTrue(engine.tradingPositions().isEmpty()); // No local or personal fallback during outages.
        when(companion.tradingLedger()).thenReturn(new AccountLedgerSnapshot(ledger.getAccountId(),100,
            0,0,0,0,0,0,0,0,List.of(PositionProjection.ready(561,"Nature rune",6100,10000,1,null,null))));
        assertTrue(engine.tradingPositions().isEmpty()); // Old server has no acquisition proof.
    }
}
