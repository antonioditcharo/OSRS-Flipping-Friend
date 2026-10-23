package com.flippingfriend.core;

import java.util.Objects;
import java.util.UUID;

/** Client acceptance and subsequent copied observation; never a replacement for canonical issuance. */
public final class AcceptedSalePolicyProof {
    private final int formatVersion;
    private final SalePolicyReceipt receipt;
    private final String acceptanceId;
    private final long acceptedAt;
    private final CaptureOrder acceptedOrder,captureOrder;
    private final String sourceEventId;
    private final long sequence,capturedAt;
    private final int filledQuantity;
    private final long spent;
    private final Boolean prospectiveCaptureRequired;
    private AcceptedSalePolicyProof(SalePolicyReceipt receipt,String id,long at,CaptureOrder accepted,
            CaptureOrder capture,String source,long sequence,long observed,int filled,long spent) {
        this(receipt,id,at,accepted,capture,source,sequence,observed,filled,spent,false);
    }
    private AcceptedSalePolicyProof(SalePolicyReceipt receipt,String id,long at,CaptureOrder accepted,
            CaptureOrder capture,String source,long sequence,long observed,int filled,long spent,boolean prospective) {
        prospectiveCaptureRequired=prospective?Boolean.TRUE:null;
        formatVersion=prospective?2:1;this.receipt=receipt;acceptanceId=id;acceptedAt=at;acceptedOrder=accepted;
        captureOrder=capture;sourceEventId=source;this.sequence=sequence;capturedAt=observed;
        filledQuantity=filled;this.spent=spent;
    }
    public static AcceptedSalePolicyProof accepted(SalePolicyReceipt receipt,long at,CaptureOrder order) {
        return accepted(receipt,UUID.randomUUID().toString(),at,order);
    }
    public static AcceptedSalePolicyProof accepted(SalePolicyReceipt receipt,String id,long at,CaptureOrder order) {
        var proof=new AcceptedSalePolicyProof(receipt,id,at,order,null,null,0,0,0,0);
        if(!proof.isWellFormed())throw new IllegalArgumentException("invalid sale policy acceptance");return proof;
    }
    public AcceptedSalePolicyProof captured(CaptureOrder order) {
        if(!isWellFormed() || isBound())return this;
        return new AcceptedSalePolicyProof(receipt,acceptanceId,acceptedAt,acceptedOrder,order,null,0,0,0,0,isProspectiveCaptureRequired());
    }
    /** New runtime acceptance must be independently committed before capture can bind it. */
    public AcceptedSalePolicyProof requiringProspectiveCapture() {
        if(!isWellFormed() || isBound())throw new IllegalStateException("Only prospective acceptance can be staged");
        return new AcceptedSalePolicyProof(receipt,acceptanceId,acceptedAt,acceptedOrder,captureOrder,null,0,0,0,0,true);
    }
    public boolean isProspectiveCaptureRequired(){return Boolean.TRUE.equals(prospectiveCaptureRequired);}
    public boolean isWellFormed() {
        if((formatVersion!=1 && formatVersion!=2) || isProspectiveCaptureRequired()!=(formatVersion==2) || receipt==null || !receipt.isWellFormed() || acceptanceId==null || acceptanceId.isBlank()
            || acceptedAt<receipt.getDecidedAt() || acceptedAt-receipt.getDecidedAt()>10 || !validOrder(acceptedOrder)
            || captureOrder!=null && !validOrder(captureOrder))return false;
        if(sourceEventId==null)return sequence==0 && capturedAt==0 && filledQuantity==0 && spent==0;
        return !sourceEventId.isBlank() && sequence>receipt.getSourceSequence() && capturedAt>=acceptedAt && ordered(capturedAt)
            && filledQuantity>=receipt.getFilledQuantity() && filledQuantity<=receipt.getTotalQuantity() && spent>=receipt.getSpent();
    }
    private boolean ordered(long observedAt) {
        if(!validOrder(captureOrder))return false;
        return acceptedOrder.getProcess().equals(captureOrder.getProcess())?acceptedOrder.precedes(captureOrder):acceptedAt<observedAt;
    }
    public AcceptedSalePolicyProof bindTo(OfferEvent event) {
        if(!isWellFormed() || event==null)return null;
        if(isBound())return qualifiesObservation(event)?this:null;
        if(!receipt.matchesEpisode(event) || !ordered(event.getObservedAt()) || event.getObservedAt()<acceptedAt
            || event.getSequence()<=receipt.getSourceSequence() || Objects.equals(receipt.getSourceEventId(),event.getEventId())
            || event.getEventId()==null || event.getEventId().isBlank())return null;
        return new AcceptedSalePolicyProof(receipt,acceptanceId,acceptedAt,acceptedOrder,captureOrder,
            event.getEventId(),event.getSequence(),event.getObservedAt(),event.getFilledQuantity(),event.getSpent(),isProspectiveCaptureRequired());
    }
    public boolean matchesEpisode(OfferEvent event) {
        return isBound() && receipt.matchesEpisode(event) && event.getSequence()>=sequence && event.getObservedAt()>=capturedAt
            && event.getFilledQuantity()>=filledQuantity && event.getSpent()>=spent;
    }
    public boolean qualifiesObservation(OfferEvent event) {
        return matchesEpisode(event) && sourceEventId.equals(event.getEventId()) && sequence==event.getSequence() && capturedAt==event.getObservedAt()
            && filledQuantity==event.getFilledQuantity() && spent==event.getSpent();
    }
    public boolean qualifiesFirst(OfferEvent event){return qualifiesObservation(event);}
    public boolean isBound(){return sourceEventId!=null && isWellFormed();}
    public boolean isValid(){return isWellFormed();}
    public SalePolicyReceipt getReceipt(){return receipt;}
    public String getAcceptanceId(){return acceptanceId;}
    public String getAccountId(){return receipt==null?null:receipt.getAccountId();}
    public long getAcceptedAt(){return acceptedAt;}
    public CaptureOrder getAcceptedOrder(){return acceptedOrder;}
    public CaptureOrder getCaptureOrder(){return captureOrder;}
    public String getSourceEventId(){return sourceEventId;}
    public long getSequence(){return sequence;}
    public long getCapturedAt(){return capturedAt;}
    public int getFilledQuantity(){return filledQuantity;}
    public long getSpent(){return spent;}
    public boolean hasSameAcceptance(AcceptedSalePolicyProof other) {
        return other!=null && isWellFormed() && other.isWellFormed() && formatVersion==other.formatVersion
            && Objects.equals(receipt,other.receipt) && Objects.equals(acceptanceId,other.acceptanceId)
            && isProspectiveCaptureRequired()==other.isProspectiveCaptureRequired()
            && acceptedAt==other.acceptedAt && sameOrder(acceptedOrder,other.acceptedOrder);
    }
    private static boolean validOrder(CaptureOrder order){return order!=null && order.getProcess()!=null && !order.getProcess().isBlank() && order.getOrder()>0;}
    private static boolean sameOrder(CaptureOrder a,CaptureOrder b){return a==b || a!=null && b!=null && a.getOrder()==b.getOrder() && Objects.equals(a.getProcess(),b.getProcess());}
    @Override public boolean equals(Object value) {
        if(!(value instanceof AcceptedSalePolicyProof))return false;var p=(AcceptedSalePolicyProof)value;
        return formatVersion==p.formatVersion && isProspectiveCaptureRequired()==p.isProspectiveCaptureRequired() && Objects.equals(receipt,p.receipt) && Objects.equals(acceptanceId,p.acceptanceId)
            && acceptedAt==p.acceptedAt && sameOrder(acceptedOrder,p.acceptedOrder) && sameOrder(captureOrder,p.captureOrder)
            && Objects.equals(sourceEventId,p.sourceEventId) && sequence==p.sequence && capturedAt==p.capturedAt
            && filledQuantity==p.filledQuantity && spent==p.spent;
    }
    @Override public int hashCode(){return Objects.hash(receipt,acceptanceId,acceptedAt,sourceEventId,sequence,capturedAt,isProspectiveCaptureRequired());}
}
