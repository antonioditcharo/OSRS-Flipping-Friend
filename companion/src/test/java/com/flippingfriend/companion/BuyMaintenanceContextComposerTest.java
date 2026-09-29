package com.flippingfriend.companion;

import com.flippingfriend.core.OfferEvent;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class BuyMaintenanceContextComposerTest
{
    private final BuyMaintenanceContextComposer composer = new BuyMaintenanceContextComposer();
    @Test public void authoritativeInputsComposeExactContext()
    {
        BuyMaintenancePolicyContext context = composer.compose(offer("plan-1", "offer-1", 561),
            new BuyMaintenanceMarketInput(561, 105, 110, 1_950), 7_500, "plan-1", 2_000);
        assertEquals("plan-1", context.getRecommendationId());
        assertEquals("offer-1", context.getOfferIdentity());
        assertEquals(2, context.getSlot());
        assertEquals(561, context.getItemId());
        assertEquals(400, context.getRemainingQuantity());
        assertEquals(7_500, context.getBuyLimitRemaining());
    }
    @Test public void offerLineageMaySupplyTheCurrentRecommendation()
    {
        BuyMaintenancePolicyContext context = composer.compose(offer("plan-1", "offer-1", 561),
            new BuyMaintenanceMarketInput(561, 105, 110, 1_950), 100, null, 2_000);
        assertEquals("plan-1", context.getRecommendationId());
    }
    @Test public void identityAndItemMismatchesFailClosed()
    {
        assertNull(composer.compose(offer("plan-1", null, 561),
            new BuyMaintenanceMarketInput(561, 105, 110, 1_950), 100, "plan-1", 2_000));
        assertNull(composer.compose(offer("plan-1", "offer-1", 561),
            new BuyMaintenanceMarketInput(4151, 105, 110, 1_950), 100, "plan-1", 2_000));
        assertNull(composer.compose(offer("plan-1", "offer-1", 561),
            new BuyMaintenanceMarketInput(561, 105, 110, 1_950), 100, "plan-2", 2_000));
    }
    @Test public void nonOpenAndFutureInputsFailClosed()
    {
        OfferEvent closed = OfferEvent.builder("c", 1_900, "BOUGHT").eventIdentity("e", "s", "o")
            .slot(2).item(561, "Nature rune").buying(true).price(100).quantities(500, 500)
            .recommendation("plan-1", 100, 500, 10, 30).build();
        assertNull(composer.compose(closed, new BuyMaintenanceMarketInput(561, 105, 110, 1_950),
            100, "plan-1", 2_000));
        assertNull(composer.compose(offer("plan-1", "offer-1", 561),
            new BuyMaintenanceMarketInput(561, 105, 110, 2_100), 100, "plan-1", 2_000));
    }
    private static OfferEvent offer(String recommendation, String identity, int item)
    {
        OfferEvent.Builder builder = OfferEvent.builder("c", 1_900, "BUYING")
            .eventIdentity("event-1", "session-1", identity).slot(2).item(item, "Nature rune")
            .buying(true).price(100).quantities(500, 100).recommendation(recommendation, 100, 500, 10, 30);
        return builder.build();
    }
}
