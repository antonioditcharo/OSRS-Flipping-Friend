package com.flippingfriend.core;

/** Possible remaining units from one acquisition origin, never a FIFO or cost allocation claim. */
public final class AcquisitionExitSlice {
    private final String sourceEventId;
    private final AcquisitionExitContext context;
    private final int minimumRemaining,maximumRemaining;
    private final long openedAt;
    public AcquisitionExitSlice(String source,AcquisitionExitContext context,int minimum,int maximum){
        this(source,context,minimum,maximum,0);
    }
    public AcquisitionExitSlice(String source,AcquisitionExitContext context,int minimum,int maximum,long openedAt){
        sourceEventId=source;this.context=context;minimumRemaining=minimum;maximumRemaining=maximum;
        this.openedAt=openedAt;
        if(!isValid())throw new IllegalArgumentException("invalid exit origin range");
    }
    public String getSourceEventId(){return sourceEventId;}
    public AcquisitionExitContext getContext(){return context;}
    public int getMinimumRemaining(){return minimumRemaining;}
    public int getMaximumRemaining(){return maximumRemaining;}
    public long getOpenedAt(){return openedAt;}
    public boolean isValid(){return sourceEventId!=null && !sourceEventId.isBlank()
        && minimumRemaining>=0 && maximumRemaining>=minimumRemaining && openedAt>=0
        && (context==null || context.isBound() && sourceEventId.equals(context.getSourceEventId()));}
    public AcquisitionExitSlice add(int quantity){return new AcquisitionExitSlice(sourceEventId,context,
        Math.addExact(minimumRemaining,quantity),Math.addExact(maximumRemaining,quantity),openedAt);}
    public AcquisitionExitSlice afterDisposal(int quantity,int poolRemaining){return new AcquisitionExitSlice(sourceEventId,context,
        Math.max(0,minimumRemaining-quantity),Math.min(maximumRemaining,poolRemaining),openedAt);}
}
