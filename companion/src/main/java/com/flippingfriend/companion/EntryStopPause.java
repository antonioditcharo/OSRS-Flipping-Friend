package com.flippingfriend.companion;

import com.flippingfriend.data.LatestPrice;
import com.flippingfriend.session.Position;
import com.flippingfriend.session.StopLossEvidence;
import java.time.Instant;
import java.util.*;

/** Item-local, reversible entry restriction from original still-possible acquisition stops. */
final class EntryStopPause {
    static Set<Integer> items(SqliteStore.PlanningProjections read,MarketIngestionService.MarketState market,long now) {
        var paused=new HashSet<Integer>();
        if(read==null || read.tradeScope==null || market==null)return paused;
        for(var stock:read.tradeScope.readyPositions()) {
            var raw=market.latest.get(Integer.toString(stock.getItemId()));
            if(raw==null)continue;
            var position=new Position(stock.getItemId(),stock.getItemName(),stock.getQuantity(),stock.getTotalCost(),stock.getOpenedAt(),true);
            position.applyExitContexts(stock.getExitContexts());
            if(StopLossEvidence.blocksAdditionalBuy(position,com.flippingfriend.data.PublicMarketNumbers.quote(raw),Instant.ofEpochSecond(now)))paused.add(stock.getItemId());
        }
        return paused;
    }
    static Set<Integer> restrictions(DurablePlanningExposure exposure,SqliteStore.PlanningProjections read,
        MarketIngestionService.MarketState market,long now) {
        var paused=items(read,market,now);paused.addAll(exposure.unavailableEntryItems);return paused;
    }
    static final String REASON="A fresh trade crossed an original acquisition stop. New purchases of this item wait for a fresh recovery; its stock and cancelled remainder are retained. This does not authorize a loss sale.";
    private EntryStopPause(){}
}
