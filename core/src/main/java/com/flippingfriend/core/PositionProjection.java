package com.flippingfriend.core;

/** Immutable durable current position with blended cost basis. */
public final class PositionProjection
{
    private final int itemId; private final String itemName; private final int quantity;
    private final long totalCost; private final boolean costKnown; private final long openedAt;
    private final long updatedAt; private final String sourceEventId; private final String sourceOfferIdentity;
    private final PositionConsistencyState consistencyState; private final String consistencyReason;
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
    public int getItemId(){return itemId;} public String getItemName(){return itemName;} public int getQuantity(){return quantity;} public long getTotalCost(){return totalCost;} public boolean isCostKnown(){return costKnown;} public long getOpenedAt(){return openedAt;} public long getUpdatedAt(){return updatedAt;} public String getSourceEventId(){return sourceEventId;} public String getSourceOfferIdentity(){return sourceOfferIdentity;} public PositionConsistencyState getConsistencyState(){return consistencyState;} public String getConsistencyReason(){return consistencyReason;} public int averageCost(){return quantity<=0||!costKnown?0:(int)(totalCost/quantity);}
}
