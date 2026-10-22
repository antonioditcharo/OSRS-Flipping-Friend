package com.flippingfriend.core;

import java.util.Objects;

/** Prospective exact accepted BUY plan; separate from attribution and execution learning. */
public final class AcceptedBuyPlanProof {
    private final int formatVersion;
    private final String accountId,acceptedAdviceId,sourceEventId,sessionId,offerIdentity;
    private final int itemId,price,quantity,slot;
    private final long acceptedAt,firstSeenAt,capturedAt,sequence;
    private final CaptureOrder acceptedOrder,captureOrder;
    private final AcquisitionExitContext exitContext;
    private final ExecutionForecast forecast;
    private AcceptedBuyPlanProof(String account,String id,int item,int price,int quantity,long at,CaptureOrder accepted,
            AcquisitionExitContext context,ExecutionForecast forecast,CaptureOrder capture,String source,String session,
            String episode,int slot,long first,long capturedAt,long sequence) {
        formatVersion=1;accountId=account;acceptedAdviceId=id;itemId=item;this.price=price;this.quantity=quantity;acceptedAt=at;
        acceptedOrder=accepted;exitContext=context;this.forecast=forecast;captureOrder=capture;sourceEventId=source;
        sessionId=session;offerIdentity=episode;this.slot=slot;firstSeenAt=first;this.capturedAt=capturedAt;this.sequence=sequence;
    }
    public static AcceptedBuyPlanProof accepted(String account,String id,int item,int price,int quantity,long at,
            CaptureOrder order,AcquisitionExitContext context,ExecutionForecast forecast) {
        var p=new AcceptedBuyPlanProof(account,id,item,price,quantity,at,order,context,forecast,null,null,null,null,-1,0,0,0);
        if(!p.isWellFormed())throw new IllegalArgumentException("invalid accepted buy plan");return p;
    }
    public AcceptedBuyPlanProof captured(CaptureOrder capture) {
        if(isBound())return this;
        return new AcceptedBuyPlanProof(accountId,acceptedAdviceId,itemId,price,quantity,acceptedAt,acceptedOrder,exitContext,
            forecast,capture,null,null,null,-1,0,0,0);
    }
    public boolean isWellFormed() {
        if(formatVersion!=1 || accountId==null || !accountId.matches("rs-[0-9a-f]{64}") || blank(acceptedAdviceId)
            || itemId<=0 || price<=0 || quantity<=0 || acceptedAt<=0 || acceptedOrder==null
            || blank(acceptedOrder.getProcess()) || acceptedOrder.getOrder()<=0 || exitContext==null || !exitContext.isValid()
            || forecast!=null && !forecast.matches(itemId,true,price,quantity))return false;
        if(sourceEventId==null)return !exitContext.isBound() && sessionId==null && offerIdentity==null && slot==-1
            && firstSeenAt==0 && capturedAt==0 && sequence==0;
        return !blank(sourceEventId) && !blank(sessionId) && !blank(offerIdentity) && slot>=0 && slot<8 && sequence>0
            && firstSeenAt>=acceptedAt && capturedAt>=firstSeenAt && capturedAt-acceptedAt<=600
            && acceptedOrder.precedes(captureOrder) && sourceEventId.equals(exitContext.getSourceEventId());
    }
    public boolean isBound(){return sourceEventId!=null && isWellFormed();}
    public AcceptedBuyPlanProof bindTo(OfferEvent e) {
        if(e==null || !isWellFormed())return null;
        if(isBound())return qualifiesOriginal(e)?this:null;
        if(!acceptedOrder.precedes(captureOrder) || !matchesOrder(e) || !e.isAuthenticatedObservation()
            || !"BUYING".equals(e.getEventType()) || e.getFilledQuantity()!=0 || e.getSpent()!=0
            || e.getFirstSeenAt()<acceptedAt || e.getObservedAt()<e.getFirstSeenAt() || e.getObservedAt()-acceptedAt>600
            || blank(e.getEventId()) || blank(e.getSessionId()) || blank(e.getOfferIdentity()) || e.getSlot()<0
            || e.getSlot()>=8 || e.getSequence()<=0)return null;
        return new AcceptedBuyPlanProof(accountId,acceptedAdviceId,itemId,price,quantity,acceptedAt,acceptedOrder,
            exitContext.bind(e.getEventId()),forecast,captureOrder,e.getEventId(),e.getSessionId(),e.getOfferIdentity(),
            e.getSlot(),e.getFirstSeenAt(),e.getObservedAt(),e.getSequence());
    }
    private boolean matchesOrder(OfferEvent e){return e!=null && accountId.equals(e.getAccountId()) && e.isBuying()
        && itemId==e.getItemId() && price==e.getPrice() && quantity==e.getTotalQuantity();}
    public boolean matchesEpisode(OfferEvent e) {
        return isBound() && matchesOrder(e) && e.isAuthenticatedObservation() && sessionId.equals(e.getSessionId())
            && offerIdentity.equals(e.getOfferIdentity()) && slot==e.getSlot() && firstSeenAt==e.getFirstSeenAt()
            && sequence<=e.getSequence() && capturedAt<=e.getObservedAt() && e.getFilledQuantity()>=0
            && e.getFilledQuantity()<=quantity && e.getSpent()>=0
            && java.util.Set.of("BUYING","BOUGHT","CANCELLED_BUY").contains(e.getEventType());
    }
    public boolean qualifiesOriginal(OfferEvent e){return matchesEpisode(e) && sourceEventId.equals(e.getEventId())
        && sequence==e.getSequence() && capturedAt==e.getObservedAt() && e.getFilledQuantity()==0 && e.getSpent()==0
        && "BUYING".equals(e.getEventType());}
    public boolean hasSameAcceptance(AcceptedBuyPlanProof p) {
        return p!=null && isWellFormed() && p.isWellFormed() && formatVersion==p.formatVersion
            && Objects.equals(accountId,p.accountId) && Objects.equals(acceptedAdviceId,p.acceptedAdviceId)
            && itemId==p.itemId && price==p.price && quantity==p.quantity && acceptedAt==p.acceptedAt
            && sameOrder(acceptedOrder,p.acceptedOrder) && sameContext(exitContext,p.exitContext)
            && Objects.equals(fingerprint(forecast),fingerprint(p.forecast));
    }
    private static boolean sameContext(AcquisitionExitContext a,AcquisitionExitContext b){return a!=null && b!=null
        && a.getTargetSellPrice()==b.getTargetSellPrice() && a.getStopPrice()==b.getStopPrice()
        && Double.compare(a.getPredictedSellMinutes(),b.getPredictedSellMinutes())==0;}
    private static String fingerprint(ExecutionForecast f){return f==null?null:f.getFingerprint();}
    private static boolean sameOrder(CaptureOrder a,CaptureOrder b){return a==b || a!=null && b!=null
        && a.getOrder()==b.getOrder() && Objects.equals(a.getProcess(),b.getProcess());}
    private static boolean blank(String s){return s==null || s.isBlank();}
    public String getAccountId(){return accountId;}
    public String getAcceptedAdviceId(){return acceptedAdviceId;}
    public int getItemId(){return itemId;}
    public int getPrice(){return price;}
    public int getQuantity(){return quantity;}
    public long getAcceptedAt(){return acceptedAt;}
    public CaptureOrder getAcceptedOrder(){return acceptedOrder;}
    public CaptureOrder getCaptureOrder(){return captureOrder;}
    public AcquisitionExitContext getExitContext(){return exitContext;}
    public ExecutionForecast getForecast(){return forecast;}
    public String getSourceEventId(){return sourceEventId;}
    public String getSessionId(){return sessionId;}
    public String getOfferIdentity(){return offerIdentity;}
    public int getSlot(){return slot;}
    public long getFirstSeenAt(){return firstSeenAt;}
    public long getCapturedAt(){return capturedAt;}
    public long getSequence(){return sequence;}
    @Override public boolean equals(Object o){if(!(o instanceof AcceptedBuyPlanProof))return false;var p=(AcceptedBuyPlanProof)o;
        return hasSameAcceptance(p) && sameOrder(captureOrder,p.captureOrder) && Objects.equals(exitContext,p.exitContext)
            && Objects.equals(sourceEventId,p.sourceEventId) && Objects.equals(sessionId,p.sessionId)
            && Objects.equals(offerIdentity,p.offerIdentity) && slot==p.slot && firstSeenAt==p.firstSeenAt
            && capturedAt==p.capturedAt && sequence==p.sequence;}
    @Override public int hashCode(){return Objects.hash(accountId,acceptedAdviceId,itemId,price,quantity,acceptedAt,
        sourceEventId,sessionId,offerIdentity,slot,firstSeenAt,capturedAt,sequence);}
}
