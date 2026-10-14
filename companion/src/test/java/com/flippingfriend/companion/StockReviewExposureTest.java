package com.flippingfriend.companion;

import com.flippingfriend.core.*;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class StockReviewExposureTest {
    private PositionProjection unknown() {
        return PositionProjection.reconcile(100, "Synthetic personal stock", 3, 0, false, 10, 20, null, null, "unknown cost");
    }
    private DurablePlanningExposure exposure(PositionProjection position, StockDeclaration declaration,
            OfferLifecycleProjection... offers) {
        return DurablePlanningExposure.from(new SqliteStore.PlanningProjections(Arrays.asList(position,
            PositionProjection.ready(101, "Known stock", 2, 2000, 10, null, null)), Arrays.asList(offers)),
            Collections.singletonMap(100, declaration));
    }
    private OfferLifecycleProjection offer(OfferLifecycleState state, boolean buying, int filled) {
        return OfferLifecycleProjection.restore(state, 0, "offer", "session", 1, 20, 100,
            "Synthetic stock", buying, 100, 3, filled, 100L * filled, null);
    }
    @Test public void declaredPersonalStockKeepsKnownCapitalAndOtherBuyReservations() {
        PositionProjection p = unknown();
        var other = OfferLifecycleProjection.restore(OfferLifecycleState.BUY_PARTIAL, 1, "other", "s", 1, 20,
            102, "Other buy", true, 100, 10, 4, 400, null);
        var result = exposure(p, new StockDeclaration(p, StockDeclaration.Use.PERSONAL), other);
        assertNotNull(result); assertFalse(result.committedByItem.containsKey(100));
        assertEquals(Long.valueOf(2000), result.committedByItem.get(101));
        assertEquals(Long.valueOf(600), result.committedByItem.get(102)); assertEquals(600, result.reservedBuyCoins);
    }
    @Test public void declarationsCannotHideOpenBuysUncollectedItemsOrRemainingSells() {
        PositionProjection p = unknown();
        for (StockDeclaration.Use use : StockDeclaration.Use.values()) {
            StockDeclaration d = new StockDeclaration(p, use);
            for (OfferLifecycleState state : new OfferLifecycleState[]{OfferLifecycleState.BUY_OPEN,
                OfferLifecycleState.BUY_PARTIAL, OfferLifecycleState.BUY_FILLED_UNCOLLECTED,
                OfferLifecycleState.BUY_CANCELLED_UNCOLLECTED, OfferLifecycleState.SELL_OPEN,
                OfferLifecycleState.SELL_PARTIAL, OfferLifecycleState.SELL_CANCELLED_UNCOLLECTED,
                OfferLifecycleState.ERROR_RECONCILIATION}) {
                int filled = state == OfferLifecycleState.BUY_FILLED_UNCOLLECTED ? 3 : 1;
                assertNull(state.name(), exposure(p, d, offer(state, state.name().startsWith("BUY"), filled)));
            }
        }
    }
    @Test public void changedQuantityCostOrNewSourceInvalidatesReview() {
        PositionProjection p = unknown(); StockDeclaration d = new StockDeclaration(p, StockDeclaration.Use.NO_LONGER_HELD);
        assertNull(exposure(PositionProjection.reconcile(100, "stock", 4, 0, false, 10, 30, null, null, "new units"), d));
        assertNull(exposure(PositionProjection.reconcile(100, "stock", 3, 0, false, 10, 30, "new-event", "new-offer", "new evidence"), d));
        var known = exposure(PositionProjection.ready(100, "stock", 3, 300, 10, 30, "new-event", "new-offer"), d);
        assertNotNull(known); assertEquals(Long.valueOf(300), known.committedByItem.get(100));
    }
    @Test public void malformedOrAbsentReviewCannotIgnoreUnknownStock() {
        PositionProjection p = unknown();
        assertNull(exposure(p, null));
        StockDeclaration invalid = new com.google.gson.Gson().fromJson("{\"itemId\":100,\"quantity\":3}", StockDeclaration.class);
        assertNull(exposure(p, invalid));
    }
    @Test public void accountIdentityAndExposureCopiesPreserveDeclarations() {
        var p = unknown();
        var account = SnapshotReconciliationTest.account(100).withStockDeclarations(Collections.singletonMap(100,
            new StockDeclaration(p, StockDeclaration.Use.PERSONAL))).withAccountId(AccountIdentity.fromProfileKey("one"))
            .withPlanningExposure(Collections.singletonMap(101, 2000L));
        assertTrue(account.getStockDeclarations().get(100).matches(p));
        assertEquals(AccountIdentity.fromProfileKey("one"), account.getAccountId());
        assertEquals(Long.valueOf(2000), account.getCommittedByItem().get(101));
    }
}
