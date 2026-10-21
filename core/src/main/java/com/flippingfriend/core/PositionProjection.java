package com.flippingfriend.core;

/** Immutable durable current position with blended cost basis. */
public final class PositionProjection
{
    private final int itemId; private final String itemName; private final int quantity;
    private final long totalCost; private final boolean costKnown; private final long openedAt;
    private final long updatedAt; private final String sourceEventId; private final String sourceOfferIdentity;
    private final PositionConsistencyState consistencyState; private final String consistencyReason;
    private AcquisitionExitContext exitContext;
    private java.util.List<AcquisitionExitSlice> exitContexts;
    private PositionProjection(int itemId,String itemName,int quantity,long totalCost,boolean costKnown,long openedAt,long updatedAt,String sourceEventId,String sourceOfferIdentity,PositionConsistencyState state,String reason)
    {
        if(itemId<=0||quantity<0||totalCost<0||openedAt<0||updatedAt<0||state==null) throw new IllegalArgumentException("invalid position projection");
        if(state==PositionConsistencyState.READY&&(quantity<=0||totalCost<=0||!costKnown||reason!=null)) throw new IllegalArgumentException("ready position requires known positive cost and quantity");
        if(state==PositionConsistencyState.RECONCILE&&(reason==null||reason.trim().isEmpty())) throw new IllegalArgumentException("reconciliation requires a reason");
        this.itemId=itemId;this.itemName=itemName;this.quantity=quantity;this.totalCost=totalCost;this.costKnown=costKnown;this.openedAt=openedAt;this.updatedAt=updatedAt;this.sourceEventId=sourceEventId;this.sourceOfferIdentity=sourceOfferIdentity;this.consistencyState=state;this.consistencyReason=reason;
    }
    public static PositionProjection ready(int itemId,String itemName,int quantity,long totalCost,long openedAt,long updatedAt,String eventId,String offerIdentity){return new PositionProjection(itemId,itemName,quantity,totalCost,true,openedAt,updatedAt,eventId,offerIdentity,PositionConsistencyState.READY,null);}
    public static PositionProjection ready(int itemId,String itemName,int quantity,long totalCost,long observedAt,String eventId,String offerIdentity){return ready(itemId,itemName,quantity,totalCost,observedAt,observedAt,eventId,offerIdentity);}
    public static PositionProjection reconcile(int itemId,String itemName,int quantity,long totalCost,boolean costKnown,long openedAt,long updatedAt,String eventId,String offerIdentity,String reason){return new PositionProjection(itemId,itemName,quantity,totalCost,costKnown,openedAt,updatedAt,eventId,offerIdentity,PositionConsistencyState.RECONCILE,reason);}
    public static PositionProjection restore(int itemId,String itemName,int quantity,long totalCost,boolean costKnown,long openedAt,long updatedAt,String eventId,String offerIdentity,PositionConsistencyState state,String reason){return new PositionProjection(itemId,itemName,quantity,totalCost,costKnown,openedAt,updatedAt,eventId,offerIdentity,state,reason);}
    public PositionProjection withExitContext(AcquisitionExitContext context) {
        if(context!=null && !context.isBound())throw new IllegalArgumentException("unbound exit context");
        var copy=new PositionProjection(itemId,itemName,quantity,totalCost,costKnown,openedAt,updatedAt,sourceEventId,sourceOfferIdentity,consistencyState,consistencyReason);
        copy.exitContext=context;
        copy.exitContexts=context==null?java.util.List.of():java.util.List.of(new AcquisitionExitSlice(context.getSourceEventId(),context,quantity,quantity,openedAt));
        return copy;
    }
    public PositionProjection withExitContexts(java.util.List<AcquisitionExitSlice> contexts) {
        if(contexts==null)throw new IllegalArgumentException("missing exit origins");
        var copy=new PositionProjection(itemId,itemName,quantity,totalCost,costKnown,openedAt,updatedAt,sourceEventId,sourceOfferIdentity,consistencyState,consistencyReason);
        var retained=new java.util.ArrayList<AcquisitionExitSlice>();
        var identities=new java.util.HashSet<String>();long minimum=0,maximum=0;
        AcquisitionExitContext common=null;boolean first=true,same=true;
        for(var slice:contexts) {
            if(slice==null || !slice.isValid() || slice.getMaximumRemaining()>quantity
                || !identities.add(slice.getSourceEventId()))throw new IllegalArgumentException("invalid exit origin bounds");
            if(slice.getMaximumRemaining()==0)continue;
            minimum+=slice.getMinimumRemaining();maximum+=slice.getMaximumRemaining();
            retained.add(slice);
            if(first){common=slice.getContext();first=false;}
            else same&=java.util.Objects.equals(common,slice.getContext());
        }
        if(!retained.isEmpty() && (minimum>quantity || maximum<quantity))throw new IllegalArgumentException("incomplete exit origin bounds");
        copy.exitContexts=java.util.List.copyOf(retained);copy.exitContext=same?common:null;return copy;
    }
    public java.util.List<AcquisitionExitSlice> getExitContexts(){
        return exitContexts==null?(exitContext==null?java.util.List.of():java.util.List.of(new AcquisitionExitSlice(exitContext.getSourceEventId(),exitContext,quantity,quantity,openedAt)))
            :java.util.Collections.unmodifiableList(exitContexts);
    }
    public boolean hasValidExitContexts(){
        if(exitContext!=null && !exitContext.isBound())return false;
        if(exitContexts==null)return true;
        var identities=new java.util.HashSet<String>();long minimum=0,maximum=0;
        for(var slice:exitContexts){
            if(slice==null || !slice.isValid() || slice.getMaximumRemaining()>quantity
                || slice.getOpenedAt()>updatedAt || !identities.add(slice.getSourceEventId())
                || exitContext!=null && !exitContext.equals(slice.getContext()))return false;
            minimum+=slice.getMinimumRemaining();maximum+=slice.getMaximumRemaining();
        }
        return exitContexts.isEmpty()?exitContext==null:minimum<=quantity && maximum>=quantity;
    }
    public AcquisitionExitContext getExitContext(){return exitContext;}
    public int getItemId(){return itemId;} public String getItemName(){return itemName;} public int getQuantity(){return quantity;} public long getTotalCost(){return totalCost;} public boolean isCostKnown(){return costKnown;} public long getOpenedAt(){return openedAt;} public long getUpdatedAt(){return updatedAt;} public String getSourceEventId(){return sourceEventId;} public String getSourceOfferIdentity(){return sourceOfferIdentity;} public PositionConsistencyState getConsistencyState(){return consistencyState;} public String getConsistencyReason(){return consistencyReason;} public int averageCost(){return quantity<=0||!costKnown?0:(int)(totalCost/quantity);}
}
