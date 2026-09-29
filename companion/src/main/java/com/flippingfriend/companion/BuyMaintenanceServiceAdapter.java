package com.flippingfriend.companion;

import com.flippingfriend.core.OfferEvent;
import com.flippingfriend.core.PolicyDecision;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/** Read-only adapter from actual companion-owned projections to pure maintenance orchestration. */
final class BuyMaintenanceServiceAdapter
{
    private final BuyMaintenanceDecisionOrchestrator orchestrator;
    BuyMaintenanceServiceAdapter()
    {
        this(new BuyMaintenanceDecisionOrchestrator());
    }
    BuyMaintenanceServiceAdapter(BuyMaintenanceDecisionOrchestrator orchestrator)
    {
        if (orchestrator == null) throw new IllegalArgumentException("orchestrator is required");
        this.orchestrator = orchestrator;
    }
    PolicyDecision decide(Collection<OfferEvent> activeOffers,
        MarketIngestionService.MarketState market, BuyLimitLedger buyLimits, long decidedAt)
    {
        if (market == null || buyLimits == null || market.mapping == null || market.latest == null)
            return null;
        Map<Integer, BuyMaintenanceMarketInput> prices = new HashMap<>();
        if (activeOffers != null)
        {
            for (OfferEvent offer : activeOffers)
            {
                if (offer == null || !offer.isBuying() || !"BUYING".equals(offer.getEventType())) continue;
                JsonElement element = market.latest.get(String.valueOf(offer.getItemId()));
                if (element == null || !element.isJsonObject()) continue;
                JsonObject quote = element.getAsJsonObject();
                Integer low = positiveInt(quote, "low");
                Integer high = positiveInt(quote, "high");
                if (low != null && high != null)
                    prices.put(offer.getItemId(), new BuyMaintenanceMarketInput(
                        offer.getItemId(), low, high, market.observedAt));
            }
        }
        Map<Integer, Integer> remaining = buyLimits.remaining(market.mapping);
        return orchestrator.decide(activeOffers, prices, remaining, null, decidedAt);
    }
    private static Integer positiveInt(JsonObject object, String name)
    {
        try
        {
            if (!object.has(name) || object.get(name).isJsonNull()) return null;
            int value = object.get(name).getAsInt();
            return value > 0 ? value : null;
        }
        catch (RuntimeException invalid)
        {
            return null;
        }
    }
}
