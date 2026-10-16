package com.flippingfriend.companion;

import com.flippingfriend.core.*;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.time.Instant;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class DurableProfitLedgerTest
{
    @Rule public final TemporaryFolder folder = new TemporaryFolder();
    private final String account = AccountIdentity.fromProfileKey("one");
    private final long now = Instant.parse("2026-10-11T00:00:00Z").getEpochSecond();

    private OfferEvent event(String id, String state, boolean buying, int filled, long spent, long sequence, String offer)
    {
        return OfferEvent.builder("c", now + sequence, state).accountId(account)
            .eventIdentity(id, "session", offer).slot(0).item(561, "Nature rune")
            .buying(buying).price(buying ? 101 : 100).quantities(3, filled).spent(spent).sequence(sequence).build();
    }
    private void accept(SqliteStore store, OfferEvent event) throws Exception
    { store.recordAndProjectOffer(event, new com.google.gson.Gson().toJson(event)); }
    private void buy(SqliteStore store) throws Exception
    {
        accept(store, event("buy-open", "BUYING", true, 0, 0, 1, "buy"));
        accept(store, event("buy-filled", "BOUGHT", true, 3, 301, 2, "buy"));
    }
    private void clear(SqliteStore store) throws Exception
    {
        accept(store, OfferEvent.builder("clear", now + 3, "EMPTY").accountId(account)
            .eventIdentity("clear", "session", null).slot(0).sequence(3).build());
    }

    @Test public void partialSalesConserveCostAndProfitAcrossDuplicateDeliveryAndRestart() throws Exception
    {
        Path database = folder.newFolder("restart").toPath().resolve("ledger.db");
        OfferEvent partial = event("partial", "SELLING", false, 2, 250, 5, "sell");
        try (SqliteStore store = new SqliteStore(database))
        {
            buy(store); clear(store); accept(store, event("sell-open", "SELLING", false, 0, 0, 4, "sell"));
            accept(store, partial); accept(store, partial);
            AccountLedgerSnapshot ledger = store.ledgerSnapshot(account);
            assertEquals(1, ledger.getSales()); assertEquals(45, ledger.getProfitLower()); assertEquals(46, ledger.getProfitUpper());
            assertEquals(4, ledger.getTaxLower()); assertEquals(5, ledger.getTaxUpper());
            assertEquals(1, ledger.getPositions().get(0).getQuantity()); assertEquals(101, ledger.getPositions().get(0).getTotalCost());
        }
        try (SqliteStore store = new SqliteStore(database))
        {
            accept(store, partial);
            accept(store, event("done", "SOLD", false, 3, 400, 6, "sell"));
            AccountLedgerSnapshot ledger = store.ledgerSnapshot(account);
            assertEquals(2, ledger.getSales()); assertEquals(2, ledger.getBoundedSales()); assertEquals(1, ledger.getUncertainSales());
            assertEquals(91, ledger.getProfitLower()); assertEquals(92, ledger.getProfitUpper());
            assertEquals(7, ledger.getTaxLower()); assertEquals(8, ledger.getTaxUpper());
            assertTrue(ledger.getPositions().isEmpty()); assertTrue(ledger.isValid());
        }
    }

    @Test public void failedSaleTransactionRollsBackProfitStockAndDeliveryIdentity() throws Exception
    {
        Path database = folder.newFolder("rollback").toPath().resolve("ledger.db");
        try (SqliteStore store = new SqliteStore(database))
        { buy(store); clear(store); accept(store, event("sell-open", "SELLING", false, 0, 0, 4, "sell")); }
        OfferEvent partial = event("partial", "SELLING", false, 2, 250, 5, "sell");
        try (SqliteStore store = new SqliteStore(database, stage -> {
            if ("POSITION_ACCOUNTING_APPLIED".equals(stage)) throw new IllegalStateException("fixture failure");
        }))
        {
            try { accept(store, partial); fail("failed transaction committed"); }
            catch (IllegalStateException expected) { }
            assertEquals(0, store.ledgerSnapshot(account).getSales());
            assertEquals(3, store.positionProjections().get(0).getQuantity());
        }
        try (SqliteStore store = new SqliteStore(database))
        {
            assertEquals(SqliteStore.EventAcceptance.NEW, store.recordAndProjectOffer(partial, new com.google.gson.Gson().toJson(partial)).acceptance);
            assertEquals(1, store.ledgerSnapshot(account).getSales());
        }
    }

    @Test public void missingPlacementAndMissingCostRemainDistinctFromExactProfit() throws Exception
    {
        Path database = folder.newFolder("unproven").toPath().resolve("ledger.db");
        try (SqliteStore store = new SqliteStore(database))
        {
            accept(store, event("open", "SELLING", false, 0, 0, 1, "sell"));
            accept(store, event("partial", "SELLING", false, 2, 250, 2, "sell"));
            AccountLedgerSnapshot ledger = store.ledgerSnapshot(account);
            assertEquals(1, ledger.getUnresolvedSales()); assertEquals(0, ledger.getBoundedSales());
            assertEquals(0, ledger.getProfitLower()); assertEquals(0, ledger.getProfitUpper());
            assertEquals(1, ledger.getPositionsNeedingReview());
        }
        // An observed empty slot, rather than first-seen time, establishes new-offer tax rules.
        Path second = folder.newFolder("placement").toPath().resolve("ledger.db");
        try (SqliteStore store = new SqliteStore(second))
        {
            buy(store);
            accept(store, OfferEvent.builder("old-clear", 100, "EMPTY").accountId(account)
                .eventIdentity("clear", "session", null).slot(0).sequence(3).build());
            accept(store, event("open", "SELLING", false, 0, 0, 4, "sell"));
            accept(store, event("partial", "SELLING", false, 2, 250, 5, "sell"));
            assertEquals(45, store.ledgerSnapshot(account).getProfitLower());
            assertEquals(50, store.ledgerSnapshot(account).getProfitUpper());
        }
    }

    @Test public void upgradePreservesOldDisposalsWithoutManufacturingSaleReceipts() throws Exception
    {
        Path database = folder.newFolder("upgrade").toPath().resolve("ledger.db");
        try (java.sql.Connection connection = DriverManager.getConnection("jdbc:sqlite:" + database);
             java.sql.Statement statement = connection.createStatement())
        {
            statement.execute("CREATE TABLE position_event (id INTEGER PRIMARY KEY AUTOINCREMENT,source_event_id TEXT,source_offer_identity TEXT,observed_at INTEGER NOT NULL,effect_type TEXT NOT NULL,item_id INTEGER NOT NULL,item_name TEXT,quantity INTEGER NOT NULL,acquisition_cost INTEGER NOT NULL,applied_cost_basis INTEGER NOT NULL DEFAULT 0,transition_reason TEXT,created_at INTEGER NOT NULL)");
            statement.execute("INSERT INTO position_event(source_event_id,observed_at,effect_type,item_id,quantity,acquisition_cost,applied_cost_basis,created_at) VALUES('old',100,'DISPOSE',561,3,0,301,100)");
        }
        for (int attempt = 0; attempt < 2; attempt++) try (SqliteStore store = new SqliteStore(database))
        {
            assertEquals(1, store.positionEventCount());
            AccountLedgerSnapshot ledger = store.ledgerSnapshot(account);
            assertEquals(1, ledger.getUnresolvedSales()); assertEquals(0, ledger.getBoundedSales());
            assertEquals(0, ledger.getTaxUpper()); assertEquals(0, ledger.getProfitUpper());
        }
    }
}
