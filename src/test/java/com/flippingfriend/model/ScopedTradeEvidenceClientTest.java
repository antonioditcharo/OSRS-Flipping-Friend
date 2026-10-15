package com.flippingfriend.model;

import com.flippingfriend.companion.CompanionClient;
import com.flippingfriend.core.*;
import com.flippingfriend.session.*;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class ScopedTradeEvidenceClientTest {
    private final Gson gson=new Gson();
    private JsonObject ledger() {
        var gap=new MissedFlipEvidence("gap",null,0,null,"RECONCILE",0,2,
            null,null,null,null,null,null,"missing terminal");
        var value=new AccountLedgerSnapshot(AccountIdentity.fromProfileKey("one"),100,0,0,0,0,0,0,0,0,List.of())
            .withTradeEvidence(List.of(PositionProjection.ready(562,"Clean item",10,1000,1,3,"buy","buy-offer")),List.of(gap));
        return gson.toJsonTree(value).getAsJsonObject();
    }
    @Test public void provenGapScopeAllowsOnlyTheUnrelatedDurableHolding() throws Exception {
        JsonObject wire=ledger();
        wire.add("reconciliationScope",gson.toJsonTree(java.util.Map.of("gap",java.util.Set.of(561))));
        var value=gson.fromJson(wire,AccountLedgerSnapshot.class);
        assertTrue(value.isValid()); assertTrue(value.hasUnassignedReconciliation());
        var engine=new SuggestionEngine(null,null,null,null,null,null,null,null,null,mock(PositionBook.class),null,null,
            mock(TradePlans.class),null,null,null);
        CompanionClient companion=mock(CompanionClient.class);
        var field=SuggestionEngine.class.getDeclaredField("companion"); field.setAccessible(true); field.set(engine,companion);
        when(companion.tradingLedger()).thenReturn(value);
        assertTrue(engine.hasTradeEvidence());
        assertEquals(1,engine.tradingPositions().size());
        assertEquals(562,engine.tradingPositions().iterator().next().getItemId());
    }
    @Test public void aScopeCannotExposeAnAffectedItemOrNameAnotherEvent() {
        for (var scope:List.of(java.util.Map.of("gap",java.util.Set.of(562)),
                java.util.Map.of("unrelated-event",java.util.Set.of(561)))) {
            JsonObject wire=ledger(); wire.add("reconciliationScope",gson.toJsonTree(scope));
            assertFalse(gson.fromJson(wire,AccountLedgerSnapshot.class).isValid());
        }
    }
    @Test public void uncertainOnlyHoldingsAreNotReportedAsNoPurchasesRemaining() throws Exception {
        JsonObject wire=ledger(); wire.add("tradingPositions",gson.toJsonTree(List.of()));
        wire.add("reconciliationScope",gson.toJsonTree(java.util.Map.of("gap",java.util.Set.of(561))));
        var value=gson.fromJson(wire,AccountLedgerSnapshot.class); assertTrue(value.isValid());
        var engine=new SuggestionEngine(null,null,null,null,null,null,null,null,null,mock(PositionBook.class),null,null,
            mock(TradePlans.class),null,null,null);
        CompanionClient companion=mock(CompanionClient.class);
        var field=SuggestionEngine.class.getDeclaredField("companion"); field.setAccessible(true); field.set(engine,companion);
        when(companion.tradingLedger()).thenReturn(value);
        assertFalse(engine.hasTradeEvidence()); assertTrue(engine.tradingPositions().isEmpty());
    }
    @Test public void anUnboundedScopeCannotPublishApparentlyCleanStock() {
        JsonObject wire=ledger(); wire.add("reconciliationScope",gson.toJsonTree(java.util.Map.of("gap",java.util.Set.of())));
        assertFalse(gson.fromJson(wire,AccountLedgerSnapshot.class).isValid());
        wire.add("tradingPositions",gson.toJsonTree(List.of()));
        var unavailable=gson.fromJson(wire,AccountLedgerSnapshot.class);
        assertTrue(unavailable.isValid()); assertTrue(unavailable.hasUnboundedReconciliation());
    }
    @Test public void scopeCopiesAreImmutableAndDoNotMutateTheResponse() {
        JsonObject wire=ledger(); wire.add("reconciliationScope",gson.toJsonTree(java.util.Map.of("gap",java.util.Set.of(561))));
        var value=gson.fromJson(wire,AccountLedgerSnapshot.class);
        try { value.getReconciliationScope().clear(); fail(); } catch (UnsupportedOperationException expected) { }
        try { value.getReconciliationScope().get("gap").clear(); fail(); } catch (UnsupportedOperationException expected) { }
        assertTrue(value.isValid());
    }
}
