package com.flippingfriend.core;

import java.util.Objects;

/** Prospective association to one accepted workflow, never financial ownership or placement authority. */
public final class ContinuationSuccessorProof {
    private final int formatVersion;
    private final String accountId,originIdentity,originSaleRepriceSource,acceptedAdviceId;
    private final int itemId,price,quantity;
    private final boolean buying;
    private final long acceptedAt;
    private final CaptureOrder acceptedOrder,captureOrder;
    private final String sourceEventId,sessionId,offerIdentity;
    private final int slot;
    private final long firstSeenAt,sequence;

    private ContinuationSuccessorProof(String account,String origin,String saleSource,String advice,int item,boolean buying,
            int price,int quantity,long at,CaptureOrder accepted,CaptureOrder capture,String event,String session,
            String episode,int slot,long firstSeen,long sequence) {
        formatVersion=1;accountId=account;originIdentity=origin;originSaleRepriceSource=saleSource;acceptedAdviceId=advice;
        itemId=item;this.buying=buying;this.price=price;this.quantity=quantity;acceptedAt=at;acceptedOrder=accepted;captureOrder=capture;
        sourceEventId=event;sessionId=session;offerIdentity=episode;this.slot=slot;firstSeenAt=firstSeen;this.sequence=sequence;
    }
    public static ContinuationSuccessorProof accepted(String account,String origin,String saleSource,String advice,
            int item,boolean buying,int price,int quantity,long at,CaptureOrder order) {
        var proof=new ContinuationSuccessorProof(account,origin,saleSource,advice,item,buying,price,quantity,at,order,null,null,null,null,-1,0,0);
        if(!proof.isWellFormed())throw new IllegalArgumentException("invalid continuation acceptance");return proof;
    }
    public static ContinuationSuccessorProof accepted(String account,String origin,String advice,int item,boolean buying,
            int price,int quantity,long at,CaptureOrder order) {
        return accepted(account,origin,null,advice,item,buying,price,quantity,at,order);
    }
    public ContinuationSuccessorProof captured(CaptureOrder capture) {
        if(isBound())return this;
        return new ContinuationSuccessorProof(accountId,originIdentity,originSaleRepriceSource,acceptedAdviceId,itemId,buying,
            price,quantity,acceptedAt,acceptedOrder,capture,null,null,null,-1,0,0);
    }
    public boolean isWellFormed() {
        if(formatVersion!=1 || blank(accountId) || !accountId.matches("rs-[0-9a-f]{64}") || blank(originIdentity)
            || blank(acceptedAdviceId) || itemId<=0 || price<=0 || quantity<=0 || acceptedAt<=0
            || acceptedOrder==null || blank(acceptedOrder.getProcess()) || acceptedOrder.getOrder()<=0
            || originSaleRepriceSource!=null && (buying || blank(originSaleRepriceSource)))return false;
        if(sourceEventId==null)return sessionId==null && offerIdentity==null && slot==-1 && firstSeenAt==0 && sequence==0;
        return !blank(sourceEventId) && !blank(sessionId) && !blank(offerIdentity) && slot>=0 && slot<8
            && firstSeenAt>=acceptedAt && firstSeenAt-acceptedAt<=600 && sequence>0 && acceptedOrder.precedes(captureOrder);
    }
    public boolean isValid(){return isWellFormed();}
    public boolean isBound(){return sourceEventId!=null && isWellFormed();}
    /** The outbox calls this only for the first durable source of an episode. */
    public ContinuationSuccessorProof bindTo(OfferEvent event) {
        if(event==null || !isWellFormed())return null;
        if(isBound())return qualifiesOriginal(event)?this:null;
        if(!acceptedOrder.precedes(captureOrder) || !matchesOrder(event) || !event.isAuthenticatedObservation()
            || event.getFilledQuantity()!=0 || event.getSpent()!=0
            || !(buying?"BUYING":"SELLING").equals(event.getEventType())
            || event.getFirstSeenAt()!=event.getObservedAt() || event.getFirstSeenAt()<acceptedAt
            || event.getFirstSeenAt()-acceptedAt>600 || blank(event.getEventId()) || blank(event.getSessionId())
            || blank(event.getOfferIdentity()) || event.getSlot()<0 || event.getSlot()>=8 || event.getSequence()<=0)return null;
        return new ContinuationSuccessorProof(accountId,originIdentity,originSaleRepriceSource,acceptedAdviceId,itemId,buying,
            price,quantity,acceptedAt,acceptedOrder,captureOrder,event.getEventId(),event.getSessionId(),event.getOfferIdentity(),
            event.getSlot(),event.getFirstSeenAt(),event.getSequence());
    }
    private boolean matchesOrder(OfferEvent event){return event!=null && accountId.equals(event.getAccountId())
        && event.getItemId()==itemId && event.isBuying()==buying && event.getPrice()==price && event.getTotalQuantity()==quantity;}
    public boolean matchesEpisode(OfferEvent event) {
        return isBound() && matchesOrder(event) && event.isAuthenticatedObservation() && sessionId.equals(event.getSessionId())
            && offerIdentity.equals(event.getOfferIdentity()) && slot==event.getSlot() && firstSeenAt==event.getFirstSeenAt()
            && sequence<=event.getSequence() && firstSeenAt<=event.getObservedAt()
            && event.getFilledQuantity()>=0 && event.getFilledQuantity()<=quantity
            && (buying?java.util.Set.of("BUYING","BOUGHT","CANCELLED_BUY"):java.util.Set.of("SELLING","SOLD","CANCELLED_SELL")).contains(event.getEventType());
    }
    public boolean qualifiesOriginal(OfferEvent event) {
        return matchesEpisode(event) && sourceEventId.equals(event.getEventId()) && sequence==event.getSequence()
            && event.getObservedAt()==firstSeenAt && event.getFilledQuantity()==0 && event.getSpent()==0
            && (buying?"BUYING":"SELLING").equals(event.getEventType());
    }
    public boolean matches(OfferContinuation.Observation observation){
        if(observation==null || !isBound() || slot!=observation.slot || itemId!=observation.item || buying!=observation.buying
            || price!=observation.price || quantity!=observation.total || firstSeenAt!=observation.firstSeen)return false;
        String legacy=slot+"@"+firstSeenAt+":"+itemId;
        String episode=legacy.equals(observation.episode)?null:observation.episode;
        return getSuccessorIdentity().equals(OfferContinuation.identity(observation.slot,observation.item,observation.buying,
            observation.price,observation.total,observation.firstSeen,episode));
    }
    public static String workflowIdentity(OfferEvent event) {
        if(event==null || event.getSlot()<0 || event.getSlot()>=8 || event.getItemId()<=0 || event.getPrice()<=0
            || event.getTotalQuantity()<=0 || event.getFirstSeenAt()<=0 || blank(event.getOfferIdentity()))return null;
        String legacy=event.getSlot()+"@"+event.getFirstSeenAt()+":"+event.getItemId();
        return OfferContinuation.identity(event.getSlot(),event.getItemId(),event.isBuying(),event.getPrice(),
            event.getTotalQuantity(),event.getFirstSeenAt(),legacy.equals(event.getOfferIdentity())?null:event.getOfferIdentity());
    }
    public String getSuccessorIdentity(){return isBound()?OfferContinuation.identity(slot,itemId,buying,price,quantity,firstSeenAt,
        (slot+"@"+firstSeenAt+":"+itemId).equals(offerIdentity)?null:offerIdentity):null;}
    public String getAccountId(){return accountId;}
    public String getOriginIdentity(){return originIdentity;}
    public String getOriginSaleRepriceSource(){return originSaleRepriceSource;}
    public String getOriginRepriceSource(){return originSaleRepriceSource;}
    public String getAcceptedAdviceId(){return acceptedAdviceId;}
    public int getItemId(){return itemId;}
    public boolean isBuying(){return buying;}
    public int getPrice(){return price;}
    public int getQuantity(){return quantity;}
    public long getAcceptedAt(){return acceptedAt;}
    public CaptureOrder getAcceptedOrder(){return acceptedOrder;}
    public CaptureOrder getCaptureOrder(){return captureOrder;}
    public String getSourceEventId(){return sourceEventId;}
    public String getSessionId(){return sessionId;}
    public String getOfferIdentity(){return offerIdentity;}
    public int getSlot(){return slot;}
    public long getFirstSeenAt(){return firstSeenAt;}
    public long getSequence(){return sequence;}
    /** Compare the immutable accepted display, independently of its later capture/source receipt. */
    public boolean hasSameAcceptance(ContinuationSuccessorProof other) {
        return other!=null && isWellFormed() && other.isWellFormed()
            && formatVersion==other.formatVersion && Objects.equals(accountId,other.accountId)
            && Objects.equals(originIdentity,other.originIdentity)
            && Objects.equals(originSaleRepriceSource,other.originSaleRepriceSource)
            && Objects.equals(acceptedAdviceId,other.acceptedAdviceId) && itemId==other.itemId && buying==other.buying
            && price==other.price && quantity==other.quantity && acceptedAt==other.acceptedAt
            && sameOrder(acceptedOrder,other.acceptedOrder);
    }
    private static boolean blank(String value){return value==null || value.isBlank();}
    private static boolean sameOrder(CaptureOrder a,CaptureOrder b){return a==b || a!=null && b!=null && a.getOrder()==b.getOrder() && Objects.equals(a.getProcess(),b.getProcess());}
    @Override public boolean equals(Object value){
        if(!(value instanceof ContinuationSuccessorProof))return false;var p=(ContinuationSuccessorProof)value;
        return formatVersion==p.formatVersion && Objects.equals(accountId,p.accountId) && Objects.equals(originIdentity,p.originIdentity)
            && Objects.equals(originSaleRepriceSource,p.originSaleRepriceSource) && Objects.equals(acceptedAdviceId,p.acceptedAdviceId)
            && itemId==p.itemId && buying==p.buying && price==p.price && quantity==p.quantity && acceptedAt==p.acceptedAt
            && sameOrder(acceptedOrder,p.acceptedOrder) && sameOrder(captureOrder,p.captureOrder)
            && Objects.equals(sourceEventId,p.sourceEventId) && Objects.equals(sessionId,p.sessionId)
            && Objects.equals(offerIdentity,p.offerIdentity) && slot==p.slot && firstSeenAt==p.firstSeenAt && sequence==p.sequence;
    }
    @Override public int hashCode(){return Objects.hash(formatVersion,accountId,originIdentity,acceptedAdviceId,sourceEventId,sessionId,offerIdentity,slot,firstSeenAt,sequence);}
}
