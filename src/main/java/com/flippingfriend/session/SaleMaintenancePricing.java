package com.flippingfriend.session;

import com.flippingfriend.TradingHorizon;
import com.flippingfriend.core.SaleAccounting;
import com.flippingfriend.data.*;
import com.flippingfriend.model.*;
import java.time.Instant;
import java.util.List;

/** Pure sale-maintenance authority shared by the client, companion and chronological replay. */
public final class SaleMaintenancePricing {
    public static final String VERSION="exact-sale-maintenance-1";
    public static final class Quote {
        public final int price, floor;
        public final long allocatedCost;
        public final boolean costKnown, lossCut;
        Quote(int price,int floor,long cost,boolean known,boolean cut) {
            this.price=price;this.floor=floor;allocatedCost=cost;costKnown=known;lossCut=cut;
        }
    }
    public Quote quote(int item,int offerPrice,int remaining,Position position,LatestPrice latest,
        List<Candle> series,TradingHorizon horizon,Instant now,boolean sellOnly,
        SellTimingEngine exits,TaxCalculator tax) {
        if(remaining<=0 || latest==null || !latest.isComplete() || latest.secondsSinceHigh(now)<0
            || latest.secondsSinceLow(now)<0 || latest.stalestSideSeconds(now)>300
            || offerPrice-latest.getHigh()<horizon.outbidDeadband(offerPrice)
            || position!=null && remaining>position.getQuantity()) return null;
        boolean known=position!=null && position.isCostKnown();
        long cost=known?SaleAccounting.allocateCost(position.getTotalCost(),position.getQuantity(),remaining):0;
        int floor=known?tax.breakEvenSellPriceForCost(item,cost,remaining):0;
        int price=Math.max(1,latest.getHigh()-1);boolean cut=false;
        if(known && (floor<=0 || price<floor)) {
            SellDecision decision=exits.evaluate(position,latest,null,series,horizon,now,0,false,sellOnly,false);
            if(decision.getAction()==SellDecision.Action.CUT){price=decision.getPrice();cut=true;}
            else if(floor>0 && floor<offerPrice)price=floor;
            else return null;
        }
        return new Quote(price,floor,cost,known,cut);
    }
}
