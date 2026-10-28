package com.flippingfriend.companion;

import com.flippingfriend.core.OfferEvent;
import com.flippingfriend.core.PolicyDecision;
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
                var quote = com.flippingfriend.data.PublicMarketNumbers.quote(
                    market.latest.get(String.valueOf(offer.getItemId())));
                if (quote != null && quote.isComplete())
                    prices.put(offer.getItemId(), new BuyMaintenanceMarketInput(
                        offer.getItemId(), quote.getLow(), quote.getHigh(), market.observedAt));
            }
        }
        Map<Integer, Integer> remaining = buyLimits.remaining(market.mapping, java.time.Instant.ofEpochSecond(decidedAt));
        return orchestrator.decide(activeOffers, prices, remaining, null, decidedAt);
    }
}
