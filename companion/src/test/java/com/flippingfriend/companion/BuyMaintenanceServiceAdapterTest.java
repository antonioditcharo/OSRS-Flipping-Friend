package com.flippingfriend.companion;

import com.flippingfriend.core.OfferEvent;
import com.flippingfriend.core.OfferLifecycleAction;
import com.flippingfriend.core.PolicyDecision;
import com.google.gson.JsonObject;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class BuyMaintenanceServiceAdapterTest
{
    private final BuyMaintenanceServiceAdapter adapter = new BuyMaintenanceServiceAdapter();
    @Test public void actualCompanionStateProducesPolicyDecision()
    {
        PolicyDecision decision = adapter.decide(Collections.singletonList(offer()), market(1_950, true),
            new BuyLimitLedger(), 2_000);
        assertEquals(OfferLifecycleAction.HOLD, decision.getAction());
        assertEquals("offer-1", decision.getCandidateId());
        assertEquals("plan-1", decision.getRecommendationId());
    }
    @Test public void durableLedgerExhaustionProducesCancellation()
    {
        BuyLimitLedger ledger = new BuyLimitLedger();
        ledger.apply(OfferEvent.builder("fill", java.time.Instant.now().getEpochSecond(), "BOUGHT")
            .eventIdentity("event-fill", "session", "filled-offer").slot(3).item(561, "Nature rune")
            .buying(true).price(100).quantities(100, 100).build());
        PolicyDecision decision = adapter.decide(Collections.singletonList(offer()), market(1_950, true),
            ledger, 2_000);
        assertEquals(OfferLifecycleAction.CANCEL_BUY, decision.getAction());
    }
    @Test public void missingOrMalformedMarketStateFailsClosed()
    {
        assertNull(adapter.decide(Collections.singletonList(offer()), market(1_950, false),
            new BuyLimitLedger(), 2_000));
        assertNull(adapter.decide(Collections.singletonList(offer()),
            MarketIngestionService.MarketState.empty(), new BuyLimitLedger(), 2_000));
    }
    private static OfferEvent offer()
    {
        return OfferEvent.builder("c", 1_900, "BUYING")
            .eventIdentity("event-1", "session", "offer-1").slot(1).item(561, "Nature rune")
            .buying(true).price(100).quantities(500, 100)
            .recommendation("plan-1", 100, 500, 10, 30).build();
    }
    private static MarketIngestionService.MarketState market(long observedAt, boolean complete)
    {
        Map<Integer, MarketIngestionService.Item> mapping = new HashMap<>();
        mapping.put(561, new MarketIngestionService.Item(561, "Nature rune", 100, false, true));
        JsonObject latest = new JsonObject();
        JsonObject quote = new JsonObject();
        quote.addProperty("low", 105);
        if (complete) quote.addProperty("high", 110);
        latest.add("561", quote);
        return new MarketIngestionService.MarketState(mapping, latest, new JsonObject(),
            new JsonObject(), observedAt);
    }
}
