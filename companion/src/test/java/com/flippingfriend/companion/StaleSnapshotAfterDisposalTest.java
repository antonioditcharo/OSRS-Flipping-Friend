package com.flippingfriend.companion;

import com.flippingfriend.core.AccountSnapshot;
import com.flippingfriend.core.OfferEvent;
import com.flippingfriend.core.PositionSnapshot;
import com.google.gson.Gson;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;
import static org.junit.Assert.*;

/** Regression for an older ownership snapshot arriving after a canonical sale. */
public class StaleSnapshotAfterDisposalTest {
    private static AccountSnapshot snapshot(long at) {
        return SnapshotReconciliationTest.account(at,
                new PositionSnapshot(4151, "Abyssal whip", 10, 1000L, true, 100, 0, 0, 0));
    }
    private static OfferEvent sale(String id, long at, String state, int filled, long proceeds, long sequence) {
        return OfferEvent.builder("sale-regression", at, state)
                .eventIdentity(id, "session-regression", "sell-regression")
                .slot(0).item(4151, "Abyssal whip").buying(false).price(200)
                .quantities(10, filled).spent(proceeds).sequence(sequence).build();
    }
    private static void record(SqliteStore store, OfferEvent event) throws Exception {
        SqliteStore.ProjectedEventAcceptance accepted = store.recordAndProjectOffer(event, new Gson().toJson(event));
        assertEquals(SqliteStore.EventAcceptance.NEW, accepted.acceptance);
        assertNotNull(accepted.transition);
        assertTrue(accepted.transition.getReason(), accepted.transition.isAccepted());
    }
    @Test public void olderSnapshotCannotResurrectFullySoldStockAfterRestart() throws Exception {
        checkFullSale(100);
    }
    @Test public void equalTimestampCannotResurrectFullySoldStockAfterRestart() throws Exception {
        checkFullSale(300);
    }
    private void checkFullSale(long snapshotAt) throws Exception {
        Path db = Files.createTempDirectory("stale-full-sale").resolve("account.db");
        AccountSnapshot original = snapshot(snapshotAt);
        try (SqliteStore store = new SqliteStore(db)) {
            store.recordAndReconcileAccount(original, PositionStateView.from(original), new Gson().toJson(original));
            record(store, sale("sell-open", 200, "SELLING", 0, 0, 1));
            record(store, sale("sell-full", 300, "SOLD", 10, 2000, 2));
            assertTrue("sale must remove the position before replay", store.positionProjections().isEmpty());
        }
        try (SqliteStore store = new SqliteStore(db)) {
            store.recordAndReconcileAccount(original, PositionStateView.from(original), new Gson().toJson(original));
            assertTrue("older snapshot must not restore already-sold stock", store.positionProjections().isEmpty());
        }
    }
    @Test public void olderSnapshotCannotUndoPartialSale() throws Exception {
        checkPartialSale(100);
    }
    @Test public void equalTimestampCannotUndoPartialSale() throws Exception {
        checkPartialSale(300);
    }
    private void checkPartialSale(long snapshotAt) throws Exception {
        Path db = Files.createTempDirectory("stale-partial-sale").resolve("account.db");
        AccountSnapshot original = snapshot(snapshotAt);
        try (SqliteStore store = new SqliteStore(db)) {
            store.recordAndReconcileAccount(original, PositionStateView.from(original), new Gson().toJson(original));
            record(store, sale("partial-open", 200, "SELLING", 0, 0, 1));
            record(store, sale("partial-fill", 300, "SELLING", 4, 800, 2));
            assertEquals(6, store.positionProjections().get(0).getQuantity());
            assertEquals(600L, store.positionProjections().get(0).getTotalCost());
            store.recordAndReconcileAccount(original, PositionStateView.from(original), new Gson().toJson(original));
            assertEquals("older snapshot must not undo disposal", 6, store.positionProjections().get(0).getQuantity());
            assertEquals(600L, store.positionProjections().get(0).getTotalCost());
        }
    }
}
