package com.flippingfriend.model;
import com.flippingfriend.companion.CompanionClient;
import com.flippingfriend.core.*;
import com.flippingfriend.session.*;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
public class CanonicalTradeOwnershipTest {
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
