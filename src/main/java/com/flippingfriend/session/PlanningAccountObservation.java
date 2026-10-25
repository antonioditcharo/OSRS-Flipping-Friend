package com.flippingfriend.session;

import com.flippingfriend.core.*;
import java.util.*;
import net.runelite.api.GrandExchangeOffer;
import net.runelite.api.GrandExchangeOfferState;

/** Immutable client-thread cash and eight-slot capture. Contains no live client objects. */
public final class PlanningAccountObservation {
    private final String accountId;
    private final long observedAt;
    private final AccountState state;
    private final List<TrackedOffer> slots;
    private final Map<Integer,String> receipt;
    private PlanningAccountObservation(String accountId,long observedAt,AccountState state,List<TrackedOffer> slots) {
        this(accountId,observedAt,state,slots,Map.of());
    }
    private PlanningAccountObservation(String accountId,long observedAt,AccountState state,List<TrackedOffer> slots,Map<Integer,String> receipt) {
        this.accountId=accountId;this.observedAt=observedAt;this.state=state;this.slots=slots;
        this.receipt=Map.copyOf(receipt);
    }
    public String getAccountId(){return accountId;}
    public long getObservedAt(){return observedAt;}
    public AccountState getState(){return state;}
    public Map<Integer,String> getReceipt(){return receipt;}
    public PlanningAccountObservation withReceipt(Map<Integer,String> receipt) {
        if(receipt.size()!=8)throw new IllegalArgumentException("Eight durable sources required");
        for(int slot=0;slot<8;slot++)if(receipt.get(slot)==null || receipt.get(slot).isBlank())
            throw new IllegalArgumentException("Missing durable slot source");
        return new PlanningAccountObservation(accountId,observedAt,state,slots,receipt);
    }
    public List<TrackedOffer> getSlots(){
        List<TrackedOffer> copy=new ArrayList<>();slots.forEach(o->copy.add(o==null?null:o.copyObservation()));
        return Collections.unmodifiableList(copy);
    }
    public static PlanningAccountObservation capture(String account,AccountState state,GrandExchangeOffer[] raw,
            Collection<TrackedOffer> known,long at) {
        try { AccountIdentity.require(account); } catch(IllegalArgumentException unavailable){return null;}
        if (state==null || !state.canTrade() || at<=0 || raw==null || raw.length!=8 || known==null
                || (state.getTotalSlots()!=3 && state.getTotalSlots()!=8)) return null;
        Map<Integer,TrackedOffer> bySlot=new HashMap<>();
        for(TrackedOffer offer:known)
            if(offer==null || offer.getSlot()<0 || offer.getSlot()>=8 || bySlot.put(offer.getSlot(),offer)!=null)return null;
        List<TrackedOffer> slots=new ArrayList<>();int free=0;long reserved=0;
        try {
            for(int slot=0;slot<8;slot++) {
                if(raw[slot]==null || raw[slot].getState()==null) return null;
                OfferObservation observed=OfferObservation.copyOf(raw[slot]);
                if(observed.getState()==GrandExchangeOfferState.EMPTY) {
                    // Only a processed clear may remove an offer. A raw array can still contain
                    // login defaults or lag behind the callback that established this identity.
                    if(bySlot.containsKey(slot))return null;
                    slots.add(null);if(slot<state.getTotalSlots())free++;continue;
                }
                if(observed.getPrice()<=0 || observed.getPrice()>Integer.MAX_VALUE) return null;
                boolean buy=observed.getState()==GrandExchangeOfferState.BUYING
                    || observed.getState()==GrandExchangeOfferState.BOUGHT || observed.getState()==GrandExchangeOfferState.CANCELLED_BUY;
                TrackedOffer previous=bySlot.get(slot);
                if(previous==null || !previous.matches(observed.getItemId(),buy,(int)observed.getPrice(),observed.getTotalQuantity())) return null;
                // A heartbeat confirms processed callbacks; it must never introduce a transition
                // or fill ahead of (or behind) their ordered canonical publication.
                if(!observed.getState().name().equals(previous.getState())
                        || observed.getQuantitySold()!=previous.getQuantityFilled()
                        || observed.getSpent()!=previous.getSpent())return null;
                TrackedOffer next=previous.copyObservation();
                var event=OfferEvent.builder("capture",at,next.getState()).slot(slot).item(next.getItemId(),next.getItemName())
                    .buying(buy).price(next.getPrice()).quantities(next.getTotalQuantity(),next.getQuantityFilled()).spent(next.getSpent()).build();
                OfferLifecycleState classified=OfferLifecycleClassifier.classify(event);
                if(classified==OfferLifecycleState.ERROR_RECONCILIATION || next.getSpent()<0
                        || (next.getQuantityFilled()==0 ? next.getSpent()!=0 : next.getSpent()<=0)) return null;
                if(classified==OfferLifecycleState.BUY_OPEN || classified==OfferLifecycleState.BUY_PARTIAL)
                    reserved=Math.addExact(reserved,Math.multiplyExact(next.getPrice(),(long)next.getRemaining()));
                slots.add(next);
            }
        } catch(ArithmeticException malformed){return null;}
        if(free!=state.getFreeSlots() || reserved!=state.getCommittedCoins())return null;
        return new PlanningAccountObservation(account,at,state,Collections.unmodifiableList(slots));
    }
}
