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
            for(var offer:read.offers)if(offer.getState()!=OfferLifecycleState.EMPTY
                && exposure.unavailableEntryItems.contains(offer.getItemId()))
                return AccountRiskSnapshot.unavailable(id,now,"The current offer for "
                    +(offer.getItemName()==null?"Item "+offer.getItemId():offer.getItemName())
                    +" has unresolved execution evidence. Finish or collect that offer before new entries. Historical records stay separate.");
            long marked=0, unpricedCost=0, unseenCost=0;
            Set<Integer> valuationPaused=new HashSet<>();
            List<String> valuationDetails=new ArrayList<>();
            long validUntil=Math.min(Math.addExact(market.observedAt,120),Math.addExact(account.getObservedAt(),150));
            for(var p:read.tradeScope.readyPositions()) {
                int quantity=physicalQuantity(read,account,p);
                long visibleCost=quantity==0?0:SaleAccounting.allocateCost(p.getTotalCost(),p.getQuantity(),quantity);
                unpricedCost=Math.addExact(unpricedCost,p.getTotalCost()-visibleCost);
                unseenCost=Math.addExact(unseenCost,p.getTotalCost()-visibleCost);
                if(quantity<p.getQuantity())valuationPaused.add(p.getItemId());
                describeReserve(valuationDetails,p.getItemName(),p.getItemId(),p.getQuantity()-quantity,
                    p.getTotalCost()-visibleCost,"not in the current inventory, collection boxes or unsold GE stock");
                if(quantity==0)continue;
                var quote=market.latest==null?null:market.latest.getAsJsonObject(Integer.toString(p.getItemId()));
                if(quote==null || !quote.has("low") || quote.get("low").isJsonNull()
                    || !quote.has("lowTime") || quote.get("lowTime").isJsonNull())
                    {unpricedCost=Math.addExact(unpricedCost,visibleCost);valuationPaused.add(p.getItemId());
                        describeReserve(valuationDetails,p.getItemName(),p.getItemId(),quantity,visibleCost,"no instant-sell quote available");continue;}
                long bid=exactInteger(quote.get("low")), quoteAt=exactInteger(quote.get("lowTime"));
                if(bid<=0 || bid>Integer.MAX_VALUE || quoteAt<=0 || quoteAt>now)
                    return AccountRiskSnapshot.unavailable(id,now,"A managed item's instant-sell quote is unsupported or in the future.");
                boolean quiet=now-quoteAt>120;
                int markedQuantity=quantity,markPrice=(int)bid;
                long markCost=visibleCost;
                if(quiet) {
                    var mark=StockRiskMark.quiet(market,p.getItemId(),quantity,bid,quoteAt,now);
                    markedQuantity=mark.quantity;markPrice=mark.price;
                    markCost=markedQuantity==0?0:SaleAccounting.allocateCost(visibleCost,quantity,markedQuantity);
                    unpricedCost=Math.addExact(unpricedCost,visibleCost-markCost);
                    if(markedQuantity<quantity)valuationPaused.add(p.getItemId());
                    describeReserve(valuationDetails,p.getItemName(),p.getItemId(),quantity-markedQuantity,visibleCost-markCost,
                        (markedQuantity==0?mark.detail:"completed public volume supports only "+markedQuantity+" of "+quantity+" physically observed units")
                            +"; a direct risk mark requires a trade within 120s, otherwise qualified support is required");
                    if(markedQuantity==0)continue;
                    valuationDetails.add(markedQuantity+" × "+(p.getItemName()==null?"Item "+p.getItemId():p.getItemName())
                        +": "+mark.detail+". This is a risk estimate, not an executable quote.");
                    validUntil=Math.min(validUntil,mark.validUntil);
                }else validUntil=Math.min(validUntil,Math.addExact(quoteAt,120));
                var item=market.mapping.get(p.getItemId());
                String name=item==null?null:item.name;
                long gross=Math.multiplyExact(markPrice,(long)markedQuantity);
                var sale=SaleAccounting.calculate(gross,markedQuantity,markPrice,name,SaleAccounting.CURRENT_POLICY,markCost);
                if(sale.getProfitLower()==null)
                    return AccountRiskSnapshot.unavailable(id,now,"A managed item cannot be marked under the current tax rule.");
                marked=Math.addExact(marked,quiet?Math.min(0,sale.getProfitLower()):sale.getProfitLower());
            }
            Map<Integer,Long> reserves=new HashMap<>(evidence.unresolvedCosts);
            var newGapBounds=ProspectiveGapExposure.calculate(read);
            if(newGapBounds==null)return AccountRiskSnapshot.unavailable(id,now,"A new trade gap has no proven prospective capital bound.");
            for(int item:evidence.newReconciliationItems) {
                Long bound=newGapBounds.get(item);
                if(bound==null)return AccountRiskSnapshot.unavailable(id,now,"A new trade gap has no finite capital bound.");
                reserves.merge(item,bound,Math::max);
            }
            long historicalReserve=0;for(long amount:reserves.values())historicalReserve=Math.addExact(historicalReserve,amount);
            return AccountRiskSnapshot.builder(id,window.windowId,window.sessionId,window.startedAt,window.positionCursor,now,market.observedAt)
                .capital(window.startingCapital,knownCapital(read,account,now))
                .realized(evidence.profitLower,evidence.profitUpper,evidence.boundedSales).marked(marked)
                .validUntil(validUntil)
                .uncertainty(unseenCost,evidence.unresolvedManagedSales,evidence.untrackedUnpricedSales)
                .valuationDetail(String.join(" ",valuationDetails)+(valuationDetails.isEmpty()?"":" "))
                .verifiedCashPolicy(historicalReserve,unpricedCost,unseenCost,valuationPaused).build();
        }catch(RuntimeException malformed) {
            return AccountRiskSnapshot.unavailable(id,now,"Account risk evidence cannot be evaluated: "+malformed.getClass().getSimpleName()+".");
        }
    }

    static int physicalQuantity(SqliteStore.PlanningProjections read,AccountSnapshot account,PositionProjection position) {
        long physical=account.getObservedHoldings()==null?0:account.getObservedHoldings().getOrDefault(position.getItemId(),0);
        // Filled buy units are in copied holdings; unsold sell units are only in their GE offer.
        for(var offer:read.offers)if(!offer.isBuying() && offer.getItemId()==position.getItemId()
            && offer.getState()!=OfferLifecycleState.EMPTY)
            physical=Math.addExact(physical,(long)offer.getTotalQuantity()-offer.getFilledQuantity());
        return (int)Math.min(position.getQuantity(),physical);
    }

    private static void describeReserve(List<String> details,String name,int item,int quantity,long cost,String reason) {
        if(quantity<=0 || cost<=0)return;
        details.add(quantity+" × "+(name==null || name.isBlank()?"Item "+item:name)+": "+reason+" ("
            +String.format(Locale.ROOT,"%,d GP",cost)+" reserved).");
    }

    private static long exactInteger(com.google.gson.JsonElement value) {
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber())
            throw new IllegalArgumentException("Risk quotes require integer numbers.");
        return value.getAsBigDecimal().longValueExact();
    }
}
