package com.flippingfriend.companion;

import com.flippingfriend.core.*;
import com.flippingfriend.model.RiskAppetite;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/** Rebuild exact-size forecasts/economics using the production planner; never scale a larger order. */
final class ReplacementEntryPlanner {
    interface Plans {
        PortfolioPlan plan(MarketIngestionService.MarketState market,AccountSnapshot account,
            Map<Integer,Integer> limits,Collection<OfferEvent> offers,Instant at);
    }
    private final Plans plans;
    ReplacementEntryPlanner(SeriesSource series){this(new PortfolioPlanner(series)::plan);}
    ReplacementEntryPlanner(Plans plans){this.plans=plans;}
    ReplacementEntry plan(int item,int cap,MarketIngestionService.MarketState market,AccountSnapshot account,
            Map<Integer,Integer> limits,Collection<OfferEvent> offers,long at) {
        if(item<=0 || cap<=0 || market==null || account==null) throw new IllegalArgumentException("Invalid bounded entry request");
        var metadata=market.mapping.get(item);
        var focused=new MarketIngestionService.MarketState(metadata==null?Map.of():Map.of(item,metadata),market.latest,
            market.fiveMinute,market.hourly,market.observedAt,market.fiveMinuteStartedAt,market.hourlyStartedAt);
        int remaining=Math.min(cap,limits.getOrDefault(item,0));
        PortfolioPlan plan=plans.plan(focused,account,Map.of(item,Math.max(0,remaining)),offers,Instant.ofEpochSecond(at));
        if(account.getCanonicalRisk()!=null)plan=plan.withCanonicalRisk(account.getCanonicalRisk());
        var selected=plan.getAllocations().isEmpty()?null:plan.getAllocations().get(0);
        var decision=new CompanionEntryPolicy().decide(new EntryPolicyContext(plan,selected,at));
        if(decision.getAction()==OfferLifecycleAction.PLACE_BUY) {
            var c=selected.getCandidate();
            var constraints=PortfolioPlanner.constraintsFor(account,RiskAppetite.forName(account.getRiskAppetite()),
                PortfolioPlanner.lossBudgetFor(account,at),PortfolioPlanner.committedByGroupFor(account,market.mapping),at);
            if(c.getItemId()!=item || c.getQuantity()>remaining || c.getExpiresAt()<=at
                || PortfolioPlanner.rejected(c,account) || metadata==null || metadata.members && !account.isMembers()
                || new PortfolioOptimizer().optimize(List.of(c),constraints,plan.getCorrelationId(),at).getAllocations().isEmpty())
                decision=CompanionEntryPolicy.abstain(plan.getCorrelationId(),at,at,
                    PolicyAbstentionReason.ACCOUNT_STATE_INCONSISTENT,"REPLACEMENT_CONSTRAINTS_CHANGED");
        }
        return new ReplacementEntry(account.getAccountId(),item,cap,account.getObservedAt(),plan,decision);
    }
}
