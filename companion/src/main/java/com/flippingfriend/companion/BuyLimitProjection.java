package com.flippingfriend.companion;
import java.time.Duration;
/** Immutable durable anchored four-hour buy-limit window. */
final class BuyLimitProjection
{
 private final int itemId; private final long startedAt; private final int usedQuantity; private final long updatedAt; private final String sourceEventId; private final String sourceSnapshotCorrelationId;
 BuyLimitProjection(int itemId,long startedAt,int usedQuantity,long updatedAt,String sourceEventId,String sourceSnapshotCorrelationId){if(itemId<=0||startedAt<0||usedQuantity<0||updatedAt<0)throw new IllegalArgumentException("invalid buy-limit projection");this.itemId=itemId;this.startedAt=startedAt;this.usedQuantity=usedQuantity;this.updatedAt=updatedAt;this.sourceEventId=sourceEventId;this.sourceSnapshotCorrelationId=sourceSnapshotCorrelationId;}
 int getItemId(){return itemId;} long getStartedAt(){return startedAt;} int getUsedQuantity(){return usedQuantity;} long getUpdatedAt(){return updatedAt;} String getSourceEventId(){return sourceEventId;} String getSourceSnapshotCorrelationId(){return sourceSnapshotCorrelationId;} boolean hasExpired(long observedAt){return startedAt+Duration.ofHours(4).getSeconds()<observedAt;}
}
