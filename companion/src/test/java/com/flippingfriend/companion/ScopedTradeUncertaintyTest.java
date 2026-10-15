package com.flippingfriend.companion;

import com.flippingfriend.core.*;
import com.google.gson.Gson;
import java.nio.file.Path;
import java.util.Collections;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class ScopedTradeUncertaintyTest {
    @Rule public TemporaryFolder folder = new TemporaryFolder();
    private final String account = AccountIdentity.fromProfileKey("scoped-gaps");
    private final Gson gson = new Gson();
    private OfferEvent event(String id,int slot,int item,String state,boolean buy,int filled,long sequence) {
        OfferEvent.Builder b=OfferEvent.builder("fixture",1700000000L+sequence,state).accountId(account)
            .eventIdentity(id,"session",slot+":"+item+":"+buy).slot(slot).sequence(sequence);
        if (!"EMPTY".equals(state)) b.item(item,"Item "+item).buying(buy).price(buy?100:120)
            .quantities(10,filled).spent(filled*(buy?100L:120L));
        else b.offerIdentity(null);
        return b.build();
    }
    private void accept(SqliteStore store,OfferEvent e) throws Exception { store.recordAndProjectOffer(e,gson.toJson(e),true); }
    private void oldGap(SqliteStore store) throws Exception {
        accept(store,event("old-open",0,561,"BUYING",true,0,1));
        accept(store,event("old-fill",0,561,"BUYING",true,2,2));
        accept(store,event("old-clear",0,0,"EMPTY",false,0,3));
    }
    @Test public void unrelatedObservedPurchaseAndSaleRemainManageableAcrossRestart() throws Exception {
        Path db=folder.newFolder().toPath().resolve("fixture.db");
        try (SqliteStore store=new SqliteStore(db)) {
            oldGap(store);
            accept(store,event("new-open",1,562,"BUYING",true,0,4));
            accept(store,event("new-fill",1,562,"BOUGHT",true,10,5));
            var ledger=store.ledgerSnapshot(account);
            assertTrue(ledger.isValid()); assertTrue(ledger.hasUnassignedReconciliation());
            assertEquals(1,ledger.getTradingPositions().size());
            assertEquals(562,ledger.getTradingPositions().get(0).getItemId());
            assertEquals(10,ledger.getTradingPositions().get(0).getQuantity());
            assertEquals(1000,ledger.getTradingPositions().get(0).getTotalCost());
            assertEquals(1,ledger.getMissedFlips().size()); // The old gap is retained unchanged.
            assertEquals(0,ledger.getSales());
            assertNull(DurablePlanningExposure.from(store.planningProjections(),Collections.emptyMap(),true));
            accept(store,event("new-collected",1,0,"EMPTY",false,0,6));
            accept(store,event("sell-open",1,562,"SELLING",false,0,7));
        }
        try (SqliteStore store=new SqliteStore(db)) {
            var sold=event("sell-filled",1,562,"SOLD",false,10,8);
            accept(store,sold); accept(store,sold);
            var ledger=store.ledgerSnapshot(account);
            assertEquals(1,ledger.getSales()); assertEquals(1,ledger.getBoundedSales());
            assertEquals(0,ledger.getUnresolvedSales()); assertTrue(ledger.getTradingPositions().isEmpty());
            assertEquals(1,ledger.getMissedFlips().stream().filter(e->"RECONCILE".equals(e.getType())).count());
            assertEquals(2,store.planningProjections().tradeScope.positions.get(561).getQuantity());
        }
    }
    @Test public void anotherPurchaseOfTheAffectedItemDoesNotBecomeCleanStock() throws Exception {
        try (SqliteStore store=new SqliteStore(folder.newFolder().toPath().resolve("same.db"))) {
            oldGap(store);
            accept(store,event("same-open",1,561,"BUYING",true,0,4));
            accept(store,event("same-fill",1,561,"BOUGHT",true,10,5));
            assertTrue(store.ledgerSnapshot(account).getTradingPositions().isEmpty());
            assertEquals(12,store.planningProjections().tradeScope.positions.get(561).getQuantity());
        }
    }
    @Test public void missingSourcePayloadStillWithdrawsAllTradingOwnership() throws Exception {
        try (SqliteStore store=new SqliteStore(folder.newFolder().toPath().resolve("missing.db"))) {
            var open=event("old-open",0,561,"BUYING",true,0,1);
            store.recordAndProjectOffer(open,"incomplete legacy payload",true);
            accept(store,event("old-clear",0,0,"EMPTY",false,0,2));
            accept(store,event("new-open",1,562,"BUYING",true,0,3));
            accept(store,event("new-fill",1,562,"BOUGHT",true,10,4));
            var ledger=store.ledgerSnapshot(account);
            assertTrue(ledger.isValid()); assertTrue(ledger.getTradingPositions().isEmpty());
            assertTrue(ledger.hasUnassignedReconciliation());
        }
    }
    @Test public void readOnlyLedgerScopesEvidenceWithoutRewritingIt() throws Exception {
        Path db=folder.newFolder().toPath().resolve("read-only.db");
        int records;
        try (SqliteStore store=new SqliteStore(db)) {
            oldGap(store); accept(store,event("new-open",1,562,"BUYING",true,0,4));
            accept(store,event("new-fill",1,562,"BOUGHT",true,10,5)); records=store.positionEventCount();
        }
        try (SqliteStore store=SqliteStore.openReadOnly(db)) {
            var ledger=store.ledgerSnapshot(account);
            assertEquals(records,store.positionEventCount()); assertTrue(ledger.isValid());
            assertTrue(ledger.hasUnassignedReconciliation()); assertFalse(ledger.hasUnboundedReconciliation());
            assertEquals(java.util.Set.of(561),ledger.getReconciliationScope().get("old-clear"));
            assertEquals(1,ledger.getTradingPositions().size()); assertEquals(0,ledger.getSales());
            assertEquals("old-clear",ledger.getMissedFlips().get(0).getEventId());
            assertNull(ledger.getMissedFlips().get(0).getProfitLower());
            var decoded=gson.fromJson(gson.toJson(ledger),AccountLedgerSnapshot.class);
            assertTrue(decoded.isValid()); assertFalse(decoded.hasUnboundedReconciliation());
        }
    }
    @Test public void anotherAccountsHistoryCannotProveOwnershipInThisLedgerScope() throws Exception {
        try (SqliteStore store=new SqliteStore(folder.newFolder().toPath().resolve("foreign.db"))) {
            oldGap(store); accept(store,event("new-open",1,562,"BUYING",true,0,4));
            accept(store,event("new-fill",1,562,"BOUGHT",true,10,5));
            var foreign=store.ledgerSnapshot(AccountIdentity.fromProfileKey("other"));
            assertTrue(foreign.isValid()); assertTrue(foreign.hasUnboundedReconciliation());
            assertTrue(foreign.getTradingPositions().isEmpty());
        }
    }
}
