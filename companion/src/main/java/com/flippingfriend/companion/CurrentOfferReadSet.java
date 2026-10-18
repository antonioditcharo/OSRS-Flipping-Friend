package com.flippingfriend.companion;

import com.flippingfriend.core.*;
import java.util.*;

/** Checks that the reported cash/slots belong to the exact durable eight-slot observation. */
final class CurrentOfferReadSet {
    static boolean valid(SqliteStore.PlanningProjections read,AccountSnapshot account,long now) {
        if(read==null || account==null || account.getAccountId()==null || account.getObservedAt()<=0 || account.getObservedAt()>now
                || now-account.getObservedAt()>150 || account.getSpendableCoins()<0 || account.getCommittedCoins()<0
                || !Double.isFinite(account.getMarkedSessionDrawdown()) || account.getMarkedSessionDrawdown()<0
                || (account.getTotalSlots()!=3 && account.getTotalSlots()!=8)
                || account.getFreeSlots()<0 || account.getFreeSlots()>account.getTotalSlots()
                || read.offers.size()!=8 || read.currentEvents.size()!=8 || account.getOfferReadSet().size()!=8) return false;
        Set<Integer> slots=new HashSet<>();Set<String> ids=new HashSet<>();String session=null;int free=0;long reserved=0;
        for(var used:account.getBuyLimitUsed().entrySet())
            if(used.getKey()==null || used.getKey()<=0 || used.getValue()==null || used.getValue()<0)return false;
        var physical=account.getObservedHoldings();
        if(physical!=null && physical.entrySet().stream().anyMatch(e->e.getKey()==null || e.getKey()<=0
            || e.getValue()==null || e.getValue()<0))return false;
        try {
            for(var p:read.offers) {
                if(p==null || p.getState()==OfferLifecycleState.ERROR_RECONCILIATION
                    || p.getSlot()<0 || p.getSlot()>=8 || !slots.add(p.getSlot()))return false;
                var e=read.currentEvents.get(p.getSlot());
                if(e==null || !e.isAuthenticatedObservation() || !TradeGapEvidence.valid(e)
                        || !account.getAccountId().equals(e.getAccountId()) || !ids.add(e.getEventId())
                        || !e.getEventId().equals(account.getOfferReadSet().get(p.getSlot()))
                        || e.getObservedAt()!=account.getObservedAt() || p.getLastObservedAt()!=e.getObservedAt()
                        || p.getLastSequence()!=e.getSequence() || !Objects.equals(p.getSessionId(),e.getSessionId())
                        || p.getState()!=OfferLifecycleClassifier.classify(e)
                        || !Objects.equals(p.getOfferIdentity(),e.getOfferIdentity()) || p.getItemId()!=e.getItemId()
                        || p.isBuying()!=e.isBuying() || p.getPrice()!=e.getPrice()
                        || p.getTotalQuantity()!=e.getTotalQuantity() || p.getFilledQuantity()!=e.getFilledQuantity()
                        || p.getSpent()!=e.getSpent())return false;
                if(session==null)session=e.getSessionId();if(!session.equals(e.getSessionId()))return false;
                if(p.getState()==OfferLifecycleState.EMPTY && p.getSlot()<account.getTotalSlots())free++;
                if(p.getState()==OfferLifecycleState.BUY_OPEN || p.getState()==OfferLifecycleState.BUY_PARTIAL)
                    reserved=Math.addExact(reserved,Math.multiplyExact(p.getPrice(),(long)p.getTotalQuantity()-p.getFilledQuantity()));
            }
        }catch(ArithmeticException|IllegalArgumentException malformed){return false;}
        return free==account.getFreeSlots() && reserved==account.getCommittedCoins();
    }
}
