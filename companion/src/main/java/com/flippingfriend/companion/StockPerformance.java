package com.flippingfriend.companion;

import com.flippingfriend.core.*;
import com.google.gson.JsonElement;
import java.util.*;

/** Display-only last-quoted estimates. These values never authorize entries or book executions. */
final class StockPerformance {
    static void apply(PerformanceSnapshot report,SqliteStore.PlanningProjections read,AccountSnapshot account,
            MarketIngestionService.MarketState market,long now) {
        if(!CurrentOfferReadSet.valid(read,account,now))return;
        boolean feedCurrent=market!=null && market.observedAt>0 && market.observedAt<=now && now-market.observedAt<=120;
        List<PerformanceSnapshot.Stock> rows=new ArrayList<>();long cost=0,unpriced=0,net=0,profit=0;
        for(var position:read.tradeScope.readyPositions()) {
            int observed=DurableRisk.physicalQuantity(read,account,position),priced=0,price=0;long at=0,value=0;
            String reason=observed<position.getQuantity()?"Some recorded units are not in current inventory, collection boxes or unsold GE offers. ":"";
            if(feedCurrent && observed>0) {
                try {
                    var quote=market.latest.getAsJsonObject(Integer.toString(position.getItemId()));
                    long bid=integer(quote.get("low")),time=integer(quote.get("lowTime"));
                    if(bid>0 && bid<=Integer.MAX_VALUE && time>0 && time<=now) {
                        long applied=SaleAccounting.allocateCost(position.getTotalCost(),position.getQuantity(),observed);
                        var item=market.mapping.get(position.getItemId());
                        var sale=SaleAccounting.calculate(Math.multiplyExact(bid,observed),observed,(int)bid,
                            item==null?null:item.name,SaleAccounting.CURRENT_POLICY,applied);
                        if(sale.getProfitLower()!=null) {
                            priced=observed;price=(int)bid;at=time;value=Math.addExact(applied,sale.getProfitLower());
                        }
                    }
                }catch(RuntimeException unsupported){/* Unknown stays unknown; exact cash/cost remain reportable. */}
            }
            if(priced<observed)reason+=feedCurrent?"No supported last-quoted instant-sell estimate.":"Market feed is unavailable or stale.";
            var row=new PerformanceSnapshot.Stock(position.getItemId(),position.getItemName(),position.getQuantity(),observed,
                priced,position.getTotalCost(),price,at,value,reason);
            rows.add(row);cost=Math.addExact(cost,row.totalCost);net=Math.addExact(net,row.netValue);profit=Math.addExact(profit,row.pricedProfit);
            unpriced=Math.addExact(unpriced,row.totalCost-(priced==0?0:SaleAccounting.allocateCost(row.totalCost,row.quantity,priced)));
        }
        rows.sort(Comparator.comparingInt(row->row.itemId));
        report.spendableCoins=account.getSpendableCoins();report.managedAcquisitionCost=cost;
        report.stockValuations=Collections.unmodifiableList(rows);report.unpricedCost=unpriced;
        report.portfolioValue=net;report.unrealizedProfit=profit;
    }
    private static long integer(JsonElement value) {
        if(value==null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber())throw new IllegalArgumentException("Exact numeric price required");
        return value.getAsBigDecimal().longValueExact();
    }
}
