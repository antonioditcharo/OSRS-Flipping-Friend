package com.flippingfriend.companion;

import com.flippingfriend.core.OfferLifecycleProjection;
import com.flippingfriend.core.OfferLifecycleState;
import com.flippingfriend.core.PositionProjection;
import java.util.Collections;
import org.junit.Test;
import static org.junit.Assert.*;

public class DurablePlanningExposureTest {
    private static OfferLifecycleProjection offer(OfferLifecycleState state,
            boolean buying, int filled) {
        return OfferLifecycleProjection.restore(state, 0, "offer", "session",
                1, 100, 4151, "Abyssal whip", buying, 100, 10, filled,
                100L * filled, null);
    }

    private static SqliteStore.PlanningProjections read(long cost,
            OfferLifecycleProjection offer) {
        return new SqliteStore.PlanningProjections(
                Collections.singletonList(PositionProjection.ready(
                        4151, "Abyssal whip", 4, cost, 100, "event", "offer")),
                Collections.singletonList(offer));
    }

    @Test public void terminalBuysAndSellsDoNotAddReservations() {
        for (OfferLifecycleState state : new OfferLifecycleState[] {
                OfferLifecycleState.BUY_CANCELLED_UNCOLLECTED,
                OfferLifecycleState.BUY_FILLED_UNCOLLECTED,
                OfferLifecycleState.SELL_OPEN,
                OfferLifecycleState.SELL_PARTIAL,
                OfferLifecycleState.SELL_FILLED_UNCOLLECTED,
                OfferLifecycleState.SELL_CANCELLED_UNCOLLECTED}) {
            boolean buying = state.name().startsWith("BUY");
            int filled = state.name().contains("FILLED") ? 10 : 4;
            var exposure = DurablePlanningExposure.from(
                    read(400, offer(state, buying, filled)));
            assertNotNull(state.name(), exposure);
            assertEquals(state.name(), 0L, exposure.reservedBuyCoins);
            assertEquals(Long.valueOf(400), exposure.committedByItem.get(4151));
        }
    }

    @Test public void unresolvedOfferAndOverflowFailClosed() {
        assertNull(DurablePlanningExposure.from(read(400,
                offer(OfferLifecycleState.ERROR_RECONCILIATION, true, 4))));
        assertNull(DurablePlanningExposure.from(read(Long.MAX_VALUE,
                offer(OfferLifecycleState.BUY_PARTIAL, true, 4))));
        assertNull(DurablePlanningExposure.from(null));
    }

    @Test public void emptyBookHasNoInventedExposure() {
        var exposure = DurablePlanningExposure.from(
                new SqliteStore.PlanningProjections(
                        Collections.emptyList(), Collections.emptyList()));
        assertNotNull(exposure);
        assertTrue(exposure.committedByItem.isEmpty());
        assertEquals(0L, exposure.reservedBuyCoins);
    }

    @Test public void aggregateExposureOverflowAcrossDifferentItemsFailsClosed() {
        var positions = java.util.Arrays.asList(
                PositionProjection.ready(4151, "Abyssal whip", 1,
                        Long.MAX_VALUE, 100, "event-a", "offer-a"),
                PositionProjection.ready(561, "Nature rune", 1,
                        1L, 100, "event-b", "offer-b"));
        assertNull(DurablePlanningExposure.from(
                new SqliteStore.PlanningProjections(
                        positions, Collections.emptyList())));
        var exact = DurablePlanningExposure.from(
                new SqliteStore.PlanningProjections(
                        Collections.singletonList(positions.get(0)),
                        Collections.emptyList()));
        assertNotNull(exact);
        assertEquals(Long.valueOf(Long.MAX_VALUE),
                exact.committedByItem.get(4151));
    }
}
