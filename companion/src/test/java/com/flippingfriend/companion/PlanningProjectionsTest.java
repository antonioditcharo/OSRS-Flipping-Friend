package com.flippingfriend.companion;

import com.flippingfriend.core.OfferEvent;
import com.google.gson.Gson;
import java.nio.file.Path;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class PlanningProjectionsTest {
    @Rule public final TemporaryFolder folder = new TemporaryFolder();

    private static void buy(SqliteStore store, int filled, long sequence) throws Exception {
        OfferEvent event = OfferEvent.builder("planning-read", 100 + sequence, "BUYING")
                .eventIdentity("event-" + sequence, "session", "offer")
                .slot(0).item(4151, "Abyssal whip").buying(true).price(100)
                .quantities(10, filled).spent(100L * filled).sequence(sequence).build();
        var accepted = store.recordAndProjectOffer(event, new Gson().toJson(event));
        assertEquals(SqliteStore.EventAcceptance.NEW, accepted.acceptance);
        assertTrue(accepted.transition.getReason(), accepted.transition.isAccepted());
    }

    private static void check(SqliteStore.PlanningProjections read, int filled) {
        assertEquals(1, read.positions.size());
        assertEquals(1, read.offers.size());
        assertEquals(filled, read.positions.get(0).getQuantity());
        assertEquals(100L * filled, read.positions.get(0).getTotalCost());
        assertEquals(filled, read.offers.get(0).getFilledQuantity());
        assertEquals(10, read.offers.get(0).getTotalQuantity());
        var exposure = DurablePlanningExposure.from(read);
        assertNotNull(exposure);
        assertEquals(100L * (10 - filled), exposure.reservedBuyCoins);
        assertEquals(Long.valueOf(1000L), exposure.committedByItem.get(4151));
        try { exposure.committedByItem.clear(); fail("exposure mutable"); }
        catch (UnsupportedOperationException expected) { }

    }

    @Test public void partialFillReadIsImmutableAndDetachedFromLaterWrites() throws Exception {
        Path db = folder.newFolder().toPath().resolve("account.db");
        try (SqliteStore store = new SqliteStore(db)) {
            buy(store, 0, 1);
            buy(store, 4, 2);
            var first = store.planningProjections();
            check(first, 4);
            try { first.positions.clear(); fail("positions mutable"); }
            catch (UnsupportedOperationException expected) { }
            try { first.offers.clear(); fail("offers mutable"); }
            catch (UnsupportedOperationException expected) { }
            buy(store, 6, 3);
            check(store.planningProjections(), 6);
            check(first, 4);
        }
    }

    @Test public void emptyReadAndRestartPreserveDurableFacts() throws Exception {
        Path db = folder.newFolder().toPath().resolve("account.db");
        try (SqliteStore store = new SqliteStore(db)) {
            var empty = store.planningProjections();
            assertTrue(empty.positions.isEmpty());
            assertTrue(empty.offers.isEmpty());
            buy(store, 0, 1);
            buy(store, 4, 2);
            check(store.planningProjections(), 4);
        }
        try (SqliteStore store = new SqliteStore(db)) {
            check(store.planningProjections(), 4);
        }
    }

    @Test public void cancelledPartialBuyRetainsHeldCostWithoutReservationAfterRestart() throws Exception {
        Path db = folder.newFolder().toPath().resolve("account.db");
        try (SqliteStore store = new SqliteStore(db)) {
            buy(store, 0, 1);
            buy(store, 4, 2);
            OfferEvent cancelled = OfferEvent.builder("planning-read", 103, "CANCELLED_BUY")
                    .eventIdentity("event-3", "session", "offer")
                    .slot(0).item(4151, "Abyssal whip").buying(true).price(100)
                    .quantities(10, 4).spent(400).sequence(3).build();
            var accepted = store.recordAndProjectOffer(cancelled, new Gson().toJson(cancelled));
            assertEquals(SqliteStore.EventAcceptance.NEW, accepted.acceptance);
            assertTrue(accepted.transition.getReason(), accepted.transition.isAccepted());
            var exposure = DurablePlanningExposure.from(store.planningProjections());
            assertNotNull(exposure);
            assertEquals(0L, exposure.reservedBuyCoins);
            assertEquals(Long.valueOf(400L), exposure.committedByItem.get(4151));
        }
        try (SqliteStore store = new SqliteStore(db)) {
            var exposure = DurablePlanningExposure.from(store.planningProjections());
            assertNotNull(exposure);
            assertEquals(0L, exposure.reservedBuyCoins);
            assertEquals(Long.valueOf(400L), exposure.committedByItem.get(4151));
        }
    }

    @Test public void salesReduceHeldExposureWithoutAddingSellReservations() throws Exception {
        Path db = folder.newFolder().toPath().resolve("account.db");
        try (SqliteStore store = new SqliteStore(db)) {
            buy(store, 0, 1);
            buy(store, 4, 2);
            OfferEvent cancelled = OfferEvent.builder("planning-read", 103, "CANCELLED_BUY")
                    .eventIdentity("event-3", "session", "offer")
                    .slot(0).item(4151, "Abyssal whip").buying(true).price(100)
                    .quantities(10, 4).spent(400).sequence(3).build();
            var cancellation = store.recordAndProjectOffer(cancelled, new Gson().toJson(cancelled));
            assertTrue(cancellation.transition.getReason(), cancellation.transition.isAccepted());

            for (int filled : new int[] {0, 2, 4}) {
                long sequence = 4 + filled;
                OfferEvent sale = OfferEvent.builder("planning-read", 100 + sequence,
                        filled == 4 ? "SOLD" : "SELLING")
                        .eventIdentity("sale-" + filled, "session", "sell-offer")
                        .slot(1).item(4151, "Abyssal whip").buying(false).price(200)
                        .quantities(4, filled).spent(200L * filled).sequence(sequence).build();
                var accepted = store.recordAndProjectOffer(sale, new Gson().toJson(sale));
                assertEquals(SqliteStore.EventAcceptance.NEW, accepted.acceptance);
                assertTrue(accepted.transition.getReason(), accepted.transition.isAccepted());
                var exposure = DurablePlanningExposure.from(store.planningProjections());
                assertNotNull(exposure);
                assertEquals(0L, exposure.reservedBuyCoins);
                if (filled == 4) assertTrue(exposure.committedByItem.isEmpty());
                else assertEquals(Long.valueOf(100L * (4 - filled)),
                        exposure.committedByItem.get(4151));
            }
        }
        try (SqliteStore store = new SqliteStore(db)) {
            var exposure = DurablePlanningExposure.from(store.planningProjections());
            assertNotNull(exposure);
            assertTrue(exposure.committedByItem.isEmpty());
            assertEquals(0L, exposure.reservedBuyCoins);
        }
    }
}
