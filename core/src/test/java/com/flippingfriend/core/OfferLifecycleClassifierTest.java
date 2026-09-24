package com.flippingfriend.core;

import java.util.EnumSet;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class OfferLifecycleClassifierTest
{
    @Test
    public void canonicalVocabulariesRemainCompleteAndSeparate()
    {
        assertEquals(14, OfferLifecycleState.values().length);
        assertEquals(11, OfferLifecycleAction.values().length);
        assertTrue(EnumSet.allOf(OfferLifecycleState.class).contains(OfferLifecycleState.ERROR_RECONCILIATION));
        assertTrue(EnumSet.allOf(OfferLifecycleAction.class).contains(OfferLifecycleAction.RECONCILE));
    }

    @Test
    public void classifiesBuyObservations()
    {
        assertState(OfferLifecycleState.BUY_OPEN, event("BUYING", true, 10, 0));
        assertState(OfferLifecycleState.BUY_PARTIAL, event("BUYING", true, 10, 4));
        assertState(OfferLifecycleState.BUY_FILLED_UNCOLLECTED, event("BOUGHT", true, 10, 10));
        assertState(OfferLifecycleState.BUY_CANCELLED_UNCOLLECTED, event("CANCELLED_BUY", true, 10, 0));
        assertState(OfferLifecycleState.BUY_CANCELLED_UNCOLLECTED, event("CANCELLED_BUY", true, 10, 4));
        assertState(OfferLifecycleState.BUY_CANCELLED_UNCOLLECTED, event("CANCELLED_BUY", true, 10, 10));
    }

    @Test
    public void classifiesSellObservations()
    {
        assertState(OfferLifecycleState.SELL_OPEN, event("SELLING", false, 10, 0));
        assertState(OfferLifecycleState.SELL_PARTIAL, event("SELLING", false, 10, 4));
        assertState(OfferLifecycleState.SELL_FILLED_UNCOLLECTED, event("SOLD", false, 10, 10));
        assertState(OfferLifecycleState.SELL_CANCELLED_UNCOLLECTED, event("CANCELLED_SELL", false, 10, 0));
        assertState(OfferLifecycleState.SELL_CANCELLED_UNCOLLECTED, event("CANCELLED_SELL", false, 10, 4));
        assertState(OfferLifecycleState.SELL_CANCELLED_UNCOLLECTED, event("CANCELLED_SELL", false, 10, 10));
    }

    @Test
    public void emptyObservationNeedsNoOfferFields()
    {
        assertState(OfferLifecycleState.EMPTY, OfferEvent.builder("clear", 100, "EMPTY").slot(3).build());
    }

    @Test
    public void contradictoryAndUnknownObservationsFailClosed()
    {
        assertState(OfferLifecycleState.ERROR_RECONCILIATION, null);
        assertState(OfferLifecycleState.ERROR_RECONCILIATION, event(null, true, 10, 0));
        assertState(OfferLifecycleState.ERROR_RECONCILIATION, event(" ", true, 10, 0));
        assertState(OfferLifecycleState.ERROR_RECONCILIATION, event("UNKNOWN", true, 10, 0));
        assertState(OfferLifecycleState.ERROR_RECONCILIATION, event("BUYING", false, 10, 0));
        assertState(OfferLifecycleState.ERROR_RECONCILIATION, event("SELLING", true, 10, 0));
        assertState(OfferLifecycleState.ERROR_RECONCILIATION, event("BUYING", true, 10, 10));
        assertState(OfferLifecycleState.ERROR_RECONCILIATION, event("SELLING", false, 10, 10));
        assertState(OfferLifecycleState.ERROR_RECONCILIATION, event("BOUGHT", true, 10, 9));
        assertState(OfferLifecycleState.ERROR_RECONCILIATION, event("SOLD", false, 10, 9));
        assertState(OfferLifecycleState.ERROR_RECONCILIATION, event("BUYING", true, 0, 0));
        assertState(OfferLifecycleState.ERROR_RECONCILIATION, event("BUYING", true, -1, 0));
        assertState(OfferLifecycleState.ERROR_RECONCILIATION, event("BUYING", true, 10, -1));
        assertState(OfferLifecycleState.ERROR_RECONCILIATION, event("BUYING", true, 10, 11));
    }

    @Test
    public void contextDependentStatesAreNeverInventedBySingleEventClassification()
    {
        EnumSet<OfferLifecycleState> contextual = EnumSet.of(
            OfferLifecycleState.POSITION_AVAILABLE,
            OfferLifecycleState.REPRICE_CANCEL_PENDING,
            OfferLifecycleState.REPRICE_COLLECT_PENDING,
            OfferLifecycleState.REPRICE_READY);
        for (String raw : new String[] {"EMPTY", "BUYING", "BOUGHT", "CANCELLED_BUY", "SELLING", "SOLD", "CANCELLED_SELL"})
        {
            boolean buying = raw.contains("BUY") || "BOUGHT".equals(raw);
            int filled = "BOUGHT".equals(raw) || "SOLD".equals(raw) ? 10 : 0;
            assertFalse(contextual.contains(OfferLifecycleClassifier.classify(event(raw, buying, 10, filled))));
        }
    }

    @Test
    public void offerEventWireContractRemainsVersionOneAndLifecycleFree()
    {
        OfferEvent event = event("BUYING", true, 10, 4);

        assertEquals("1", event.getSchemaVersion());
        assertEquals("BUYING", event.getEventType());
        assertEquals(OfferLifecycleState.BUY_PARTIAL, OfferLifecycleClassifier.classify(event));

        for (java.lang.reflect.Field field : OfferEvent.class.getDeclaredFields())
        {
            assertFalse("lifecycle state must remain derived", "lifecycleState".equals(field.getName()));
            assertFalse("lifecycle action must remain derived", "lifecycleAction".equals(field.getName()));
        }
    }

    private static OfferEvent event(String state, boolean buying, int total, int filled)
    {
        return OfferEvent.builder("trace", 100, state)
            .slot(2)
            .item(4151, "Abyssal whip")
            .buying(buying)
            .price(1_000)
            .quantities(total, filled)
            .spent((long) filled * 1_000)
            .sequence(1)
            .build();
    }

    private static void assertState(OfferLifecycleState expected, OfferEvent event)
    {
        assertEquals(expected, OfferLifecycleClassifier.classify(event));
    }
}
