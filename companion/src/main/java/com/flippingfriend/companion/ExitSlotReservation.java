package com.flippingfriend.companion;

import com.flippingfriend.core.*;

/** One current slot for physically observed, proven acquired stock awaiting its sale. */
final class ExitSlotReservation {
    static PositionProjection awaiting(SqliteStore.PlanningProjections read,AccountSnapshot account,int replacementItem) {
        if(account==null || account.getFreeSlots()>1 || read==null || read.tradeScope==null
            || account.getObservedInventory()==null)return null;
        for(var stock:read.tradeScope.readyPositions()) {
            if(stock.getItemId()==replacementItem || account.getObservedInventory().getOrDefault(stock.getItemId(),0)<=0)continue;
            long listed=0;boolean accumulating=false;
            for(var offer:read.currentEvents.values())if(offer.getItemId()==stock.getItemId()) {
                if(offer.isBuying() && "BUYING".equals(offer.getEventType()) && offer.getTotalQuantity()>offer.getFilledQuantity())accumulating=true;
                if(!offer.isBuying() && "SELLING".equals(offer.getEventType()))listed+=(long)offer.getTotalQuantity()-offer.getFilledQuantity();
            }
            if(!accumulating && stock.getQuantity()>listed)return stock;
        }
        return null;
    }
    static String reason(PositionProjection stock){return "Keeping the last Grand Exchange slot free to sell your "+stock.getItemName()+". Existing offers stay working.";}
    private ExitSlotReservation(){}
}
