package com.flippingfriend.core;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class OfferLifecycleReducerTest
{
    @Test public void reconstructsFromAnyCanonicalObservation()
    {
        assertAccepted(null, event("EMPTY", false, 0, 0, 0, null), OfferLifecycleState.EMPTY);
        assertAccepted(null, event("BUYING", true, 10, 0, 0, "buy"), OfferLifecycleState.BUY_OPEN);
        assertAccepted(null, event("BUYING", true, 10, 4, 4000, "buy"), OfferLifecycleState.BUY_PARTIAL);
        assertAccepted(null, event("BOUGHT", true, 10, 10, 10000, "buy"), OfferLifecycleState.BUY_FILLED_UNCOLLECTED);
        assertAccepted(null, event("CANCELLED_BUY", true, 10, 4, 4000, "buy"), OfferLifecycleState.BUY_CANCELLED_UNCOLLECTED);
        assertAccepted(null, event("SELLING", false, 10, 0, 0, "sell"), OfferLifecycleState.SELL_OPEN);
        assertAccepted(null, event("SELLING", false, 10, 4, 4000, "sell"), OfferLifecycleState.SELL_PARTIAL);
        assertAccepted(null, event("SOLD", false, 10, 10, 10000, "sell"), OfferLifecycleState.SELL_FILLED_UNCOLLECTED);
        assertAccepted(null, event("CANCELLED_SELL", false, 10, 4, 4000, "sell"), OfferLifecycleState.SELL_CANCELLED_UNCOLLECTED);
    }

    @Test public void progressesBuyAndSellHighWaterMarks()
    {
        OfferLifecycleProjection buy = apply(null, event("BUYING", true, 10, 0, 0, "buy")).getProjection();
        buy = assertAccepted(buy, event("BUYING", true, 10, 4, 4000, "buy"), OfferLifecycleState.BUY_PARTIAL).getProjection();
        assertAccepted(buy, event("BOUGHT", true, 10, 10, 10000, "buy"), OfferLifecycleState.BUY_FILLED_UNCOLLECTED);

        OfferLifecycleProjection sell = apply(null, event("SELLING", false, 10, 0, 0, "sell")).getProjection();
        sell = assertAccepted(sell, event("SELLING", false, 10, 4, 4000, "sell"), OfferLifecycleState.SELL_PARTIAL).getProjection();
        assertAccepted(sell, event("CANCELLED_SELL", false, 10, 6, 6000, "sell"), OfferLifecycleState.SELL_CANCELLED_UNCOLLECTED);
    }

    @Test public void equalObservationIsIdempotent()
    {
        OfferEvent observation = event("BUYING", true, 10, 4, 4000, "buy");
        OfferLifecycleProjection projection = apply(null, observation).getProjection();
        OfferLifecycleTransition replay = apply(projection, observation);
        assertTrue(replay.isAccepted());
        assertTrue(replay.isIdempotent());
        assertEquals(PositionAccountingEffectType.NONE, replay.getAccountingEffect().getType());
    }

    @Test public void terminalClearsButOpenDoesNot()
    {
        OfferLifecycleProjection open = apply(null, event("BUYING", true, 10, 0, 0, "buy")).getProjection();
        assertRejected(open, event("EMPTY", false, 0, 0, 0, null));
        OfferLifecycleProjection terminal = apply(open, event("BOUGHT", true, 10, 10, 10000, "buy")).getProjection();
        OfferLifecycleTransition clear = assertAccepted(terminal, event("EMPTY", false, 0, 0, 0, null), OfferLifecycleState.EMPTY);
        assertEquals(OfferLifecycleAction.WAIT, clear.getAction());
        assertEquals(0, clear.getProjection().getItemId());
    }

    @Test public void emptyAllowsAFreshOffer()
    {
        OfferLifecycleProjection empty = apply(null, event("EMPTY", false, 0, 0, 0, null)).getProjection();
        assertAccepted(empty, event("BUYING", true, 10, 0, 0, "fresh"), OfferLifecycleState.BUY_OPEN);
        assertAccepted(empty, event("SELLING", false, 10, 0, 0, "fresh"), OfferLifecycleState.SELL_OPEN);
    }

    @Test public void rejectsRegressionsAndIdentityChanges()
    {
        OfferLifecycleProjection partial = apply(null, event("BUYING", true, 10, 5, 5000, "buy")).getProjection();
        assertRejected(partial, event("BUYING", true, 10, 4, 5000, "buy"));
        assertRejected(partial, event("BUYING", true, 10, 5, 4000, "buy"));
        assertRejected(partial, event("SELLING", false, 10, 5, 5000, "buy"));
        assertRejected(partial, event("BUYING", true, 11, 5, 5000, "buy"));
        assertRejected(partial, event("BUYING", true, 10, 5, 5000, "other"));
        OfferLifecycleProjection terminal = apply(partial, event("BOUGHT", true, 10, 10, 10000, "buy")).getProjection();
        assertRejected(terminal, event("BUYING", true, 10, 5, 5000, "buy"));
    }

    @Test public void protectsSameSessionSequenceButAllowsLegacyIdentityGaps()
    {
        OfferLifecycleProjection first = apply(null, event("BUYING", true, 10, 0, 0, "buy", "session", 2)).getProjection();
        assertRejected(first, event("BUYING", true, 10, 1, 1000, "buy", "session", 1));
        assertAccepted(null, event("BUYING", true, 10, 0, 0, null, null, 0), OfferLifecycleState.BUY_OPEN);
    }

    @Test public void onlyDeterministicActionsAreProduced()
    {
        EnumSet<OfferLifecycleAction> allowed = EnumSet.of(OfferLifecycleAction.WAIT,
            OfferLifecycleAction.COLLECT, OfferLifecycleAction.RECONCILE);
        for (OfferEvent event : new OfferEvent[] {
            event("EMPTY", false, 0, 0, 0, null),
            event("BUYING", true, 10, 0, 0, "buy"),
            event("BOUGHT", true, 10, 10, 10000, "buy"),
            event("UNKNOWN", true, 10, 0, 0, "buy")})
        {
            assertTrue(allowed.contains(apply(null, event).getAction()));
        }
    }

    @Test public void reducerDelegatesRawClassification() throws Exception
    {
        Path source = Path.of("src/main/java/com/flippingfriend/core/OfferLifecycleReducer.java");
        String text = Files.readString(source, StandardCharsets.UTF_8);
        assertTrue(text.contains("OfferLifecycleClassifier.classify(event)"));
        for (String raw : new String[] {"EMPTY", "BUYING", "BOUGHT", "CANCELLED_BUY", "SELLING", "SOLD", "CANCELLED_SELL"})
        {
            assertFalse(text.contains("\"" + raw + "\""));
        }
    }

    private static OfferLifecycleTransition apply(OfferLifecycleProjection previous, OfferEvent event)
    {
        return OfferLifecycleReducer.apply(previous, event);
    }
    private static OfferLifecycleTransition assertAccepted(OfferLifecycleProjection previous, OfferEvent event, OfferLifecycleState state)
    {
        OfferLifecycleTransition result = apply(previous, event);
        assertTrue(result.getReason(), result.isAccepted());
        assertEquals(state, result.getNewState());
        return result;
    }
    private static void assertRejected(OfferLifecycleProjection previous, OfferEvent event)
    {
        OfferLifecycleTransition result = apply(previous, event);
        assertFalse(result.isAccepted());
        assertEquals(OfferLifecycleState.ERROR_RECONCILIATION, result.getNewState());
        assertEquals(OfferLifecycleAction.RECONCILE, result.getAction());
        assertEquals(PositionAccountingEffectType.RECONCILE, result.getAccountingEffect().getType());
    }
    private static OfferEvent event(String state, boolean buying, int total, int filled, long spent, String identity)
    {
        return event(state, buying, total, filled, spent, identity, "session", 1);
    }
    private static OfferEvent event(String state, boolean buying, int total, int filled, long spent, String identity, String session, long sequence)
    {
        OfferEvent.Builder builder = OfferEvent.builder("trace", 100 + sequence, state)
            .eventIdentity("event-" + sequence, session, identity)
            .slot(2).buying(buying).sequence(sequence);
        if (!"EMPTY".equals(state))
        {
            builder.item(4151, "Abyssal whip").price(1000).quantities(total, filled).spent(spent);
        }
        return builder.build();
    }
}
