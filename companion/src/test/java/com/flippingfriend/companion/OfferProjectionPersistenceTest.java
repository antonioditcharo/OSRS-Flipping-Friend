package com.flippingfriend.companion;

import com.flippingfriend.core.OfferEvent;
import com.flippingfriend.core.OfferLifecycleProjection;
import com.flippingfriend.core.OfferLifecycleState;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class OfferProjectionPersistenceTest
{
    @Test public void projectionAdvancesAtomicallyAndSurvivesRestart() throws Exception
    {
        Path database = Files.createTempDirectory("offer-projection").resolve("test.db");
        OfferEvent open = event("event-1", "BUYING", 0, 1);
        OfferEvent partial = event("event-2", "BUYING", 4, 2);
        try (SqliteStore store = new SqliteStore(database))
        {
            assertEquals(SqliteStore.EventAcceptance.NEW,
                store.recordAndProjectOffer(open, "open").acceptance);
            assertEquals(SqliteStore.EventAcceptance.DUPLICATE,
                store.recordAndProjectOffer(open, "duplicate").acceptance);
            assertEquals(SqliteStore.EventAcceptance.NEW,
                store.recordAndProjectOffer(partial, "partial").acceptance);
            assertEquals(2, store.recentOfferEvents(0).size());
        }
        try (SqliteStore reopened = new SqliteStore(database))
        {
            List<OfferLifecycleProjection> rows = reopened.offerProjections();
            assertEquals(1, rows.size());
            assertEquals(OfferLifecycleState.BUY_PARTIAL, rows.get(0).getState());
            assertEquals(4, rows.get(0).getFilledQuantity());
            assertEquals(4000, rows.get(0).getSpent());
        }
    }

    @Test public void invalidTransitionPersistsReconciliationAndPriorFacts() throws Exception
    {
        Path database = Files.createTempDirectory("offer-reconciliation").resolve("test.db");
        try (SqliteStore store = new SqliteStore(database))
        {
            store.recordAndProjectOffer(event("event-1", "BUYING", 5, 1), "partial");
            SqliteStore.ProjectedEventAcceptance result =
                store.recordAndProjectOffer(event("event-2", "BUYING", 4, 2), "regression");
            assertEquals(SqliteStore.EventAcceptance.NEW, result.acceptance);
            assertTrue(!result.transition.isAccepted());
            OfferLifecycleProjection projection = store.offerProjections().get(0);
            assertEquals(OfferLifecycleState.ERROR_RECONCILIATION, projection.getState());
            assertEquals(5, projection.getFilledQuantity());
            assertEquals(5000, projection.getSpent());
        }
    }

    private static OfferEvent event(String eventId, String state, int filled, long sequence)
    {
        return OfferEvent.builder("trace", 100 + sequence, state)
            .eventIdentity(eventId, "session", "slot-2-offer")
            .slot(2).item(4151, "Abyssal whip").buying(true).price(1000)
            .quantities(10, filled).spent((long) filled * 1000).sequence(sequence).build();
    }
}
