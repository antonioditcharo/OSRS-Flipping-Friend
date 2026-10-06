package com.flippingfriend.companion;
import java.util.ArrayList; import java.util.Collections; import java.util.List;
/** Immutable ordered expected slot-occupancy evidence. */
final class BuyReplacementExpectedSlotOccupancyEvaluationSet {
 private final BuyReplacementCalibratedDurationEvaluationSet source; private final List<BuyReplacementExpectedSlotOccupancyEvaluation> evaluations; private final long evaluatedAt;
 BuyReplacementExpectedSlotOccupancyEvaluationSet(BuyReplacementCalibratedDurationEvaluationSet s,List<BuyReplacementExpectedSlotOccupancyEvaluation> e,long at){source=s;evaluations=Collections.unmodifiableList(new ArrayList<>(e));evaluatedAt=at;}
 String getIntentId(){return source.getIntentId();} String getOriginalOfferIdentity(){return source.getOriginalOfferIdentity();} String getRecommendationId(){return source.getRecommendationId();} int getSlot(){return source.getSlot();} int getItemId(){return source.getItemId();} String getItemName(){return source.getItemName();} int getExactRemainderQuantity(){return source.getExactRemainderQuantity();} String getAppetiteName(){return source.getAppetiteName();} double getEffectiveHorizonHours(){return source.getEffectiveHorizonHours();} long getCalibratedDurationEvaluatedAt(){return source.getEvaluatedAt();} List<BuyReplacementCalibratedDurationEvaluation> getCalibratedDurationEvaluations(){return source.getEvaluations();} List<BuyReplacementExpectedSlotOccupancyEvaluation> getEvaluations(){return evaluations;} long getEvaluatedAt(){return evaluatedAt;}
}
