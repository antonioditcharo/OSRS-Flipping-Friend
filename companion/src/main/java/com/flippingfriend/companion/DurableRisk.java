package com.flippingfriend.companion;

import com.flippingfriend.core.*;
import java.util.*;

/** One account's prospective loss allowance derived from its durable effects and fresh marks. */
final class DurableRisk {
    static long knownCapital(SqliteStore.PlanningProjections read, AccountSnapshot account, long now) {
        long capital=Math.addExact(account.getSpendableCoins(),account.getCommittedCoins());
        for(var p:read.tradeScope.readyPositions()) {
            if(p.getUpdatedAt()>now)throw new IllegalStateException("Managed acquisition is in the future.");
            capital=Math.addExact(capital,p.getTotalCost());
        }
        return capital;
    }

    static AccountRiskSnapshot evaluate(SqliteStore.PlanningProjections read, AccountSnapshot account,
            DurablePlanningExposure exposure, MarketIngestionService.MarketState market, long now) {
        String id=account.getAccountId();
        try {
            if(exposure==null || !CurrentOfferReadSet.valid(read,account,now))
                return AccountRiskSnapshot.unavailable(id,now,"Waiting for consistent current account evidence.");
            var evidence=read.riskEvidence;
            if(evidence==null || evidence.window==null)
                return AccountRiskSnapshot.unavailable(id,now,"Waiting for a proven prospective risk window.");
            var window=evidence.window;
            if(!window.isValid() || !id.equals(window.accountId) || window.startedAt>now
                || evidence.unbounded || read.currentEvents.values().stream().anyMatch(e->!window.sessionId.equals(e.getSessionId())))
                return AccountRiskSnapshot.unavailable(id,now,"Risk window or current loss scope is inconsistent.");
            if(market==null || market.observedAt<=0 || market.observedAt>now || now-market.observedAt>120)
                return AccountRiskSnapshot.unavailable(id,now,"Waiting for fresh prices to evaluate account risk.");
            long marked=0, validUntil=Math.min(Math.addExact(market.observedAt,120),Math.addExact(account.getObservedAt(),150));
            for(var p:read.tradeScope.readyPositions()) {
                var quote=market.latest==null?null:market.latest.getAsJsonObject(Integer.toString(p.getItemId()));
                if(quote==null || !quote.has("low") || quote.get("low").isJsonNull()
                    || !quote.has("lowTime") || quote.get("lowTime").isJsonNull())
                    return AccountRiskSnapshot.unavailable(id,now,"A managed item has no current instant-sell quote.");
                long bid=exactInteger(quote.get("low")), quoteAt=exactInteger(quote.get("lowTime"));
                if(bid<=0 || bid>Integer.MAX_VALUE || quoteAt<=0 || quoteAt>now || now-quoteAt>120)
                    return AccountRiskSnapshot.unavailable(id,now,"A managed item's instant-sell quote is stale or unsupported.");
                validUntil=Math.min(validUntil,Math.addExact(quoteAt,120));
                var item=market.mapping.get(p.getItemId());
                String name=item==null?null:item.name;
                long gross=Math.multiplyExact(bid,(long)p.getQuantity());
                var sale=SaleAccounting.calculate(gross,p.getQuantity(),(int)bid,name,SaleAccounting.CURRENT_POLICY,p.getTotalCost());
                if(sale.getProfitLower()==null)
                    return AccountRiskSnapshot.unavailable(id,now,"A managed item cannot be marked under the current tax rule.");
                marked=Math.addExact(marked,sale.getProfitLower());
            }
            Map<Integer,Long> reserves=new HashMap<>(evidence.unresolvedCosts);
            var newGapBounds=ProspectiveGapExposure.calculate(read);
            if(newGapBounds==null)return AccountRiskSnapshot.unavailable(id,now,"A new trade gap has no proven prospective capital bound.");
            for(int item:evidence.newReconciliationItems) {
                Long bound=newGapBounds.get(item);
                if(bound==null)return AccountRiskSnapshot.unavailable(id,now,"A new trade gap has no finite capital bound.");
                reserves.merge(item,bound,Math::max);
            }
            long reserve=0;for(long amount:reserves.values())reserve=Math.addExact(reserve,amount);
            return AccountRiskSnapshot.builder(id,window.windowId,window.sessionId,window.startedAt,window.positionCursor,now,market.observedAt)
                .capital(window.startingCapital,knownCapital(read,account,now))
                .realized(evidence.profitLower,evidence.profitUpper,evidence.boundedSales).marked(marked)
                .validUntil(validUntil)
                .uncertainty(reserve,evidence.unresolvedManagedSales,evidence.untrackedUnpricedSales).build();
        }catch(RuntimeException malformed) {
            return AccountRiskSnapshot.unavailable(id,now,"Account risk evidence cannot be evaluated: "+malformed.getClass().getSimpleName()+".");
        }
    }

    private static long exactInteger(com.google.gson.JsonElement value) {
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber())
            throw new IllegalArgumentException("Risk quotes require integer numbers.");
        return value.getAsBigDecimal().longValueExact();
    }
}
