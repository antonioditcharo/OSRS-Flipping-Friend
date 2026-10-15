package com.flippingfriend.core;

/** Readout intervals for one observed episode. This is not an asset, execution or profit ledger. */
public final class ExecutionTimingObservation {
    private final String sourceEventId, sessionId, offerIdentity, terminalEventId, lastActiveEventId;
    private final int slot, itemId, quantity, price;
    private final boolean buying, predictionComparable, timingCoherent;
    private final double predictedMinutes;
    private final String outcome, reason;
    private final Long placementLowerAt, placementUpperAt, firstFillAfterAt, firstFillByAt;
    private final Long completionAfterAt, completionByAt, durationLowerSeconds, durationUpperSeconds;
    private final Long firstFillLowerSeconds, firstFillUpperSeconds;

    public ExecutionTimingObservation(OfferEvent original, OfferEvent firstActive, OfferEvent previousEmpty,
            OfferEvent lastActive, OfferEvent lastZeroBeforeFill, OfferEvent firstPositive,
            OfferEvent terminal, boolean endedWithoutTerminal, boolean timingCoherent, boolean comparable, String reason) {
        sourceEventId=original.getEventId(); sessionId=original.getSessionId(); offerIdentity=original.getOfferIdentity();
        slot=original.getSlot();itemId=original.getItemId();quantity=original.getTotalQuantity();price=original.getPrice();
        buying=original.isBuying();terminalEventId=terminal==null?null:terminal.getEventId();
        lastActiveEventId=lastActive==null?null:lastActive.getEventId();
        this.reason=reason;this.timingCoherent=timingCoherent;predictionComparable=comparable && timingCoherent;
        predictedMinutes=predictionComparable?original.getPredictedMinutes():0;
        placementLowerAt=!timingCoherent || previousEmpty==null?null:previousEmpty.getObservedAt();
        // Captures report whole seconds: t describes [t,t+1), not an exact instant.
        placementUpperAt=!timingCoherent || firstActive==null?null:afterSecond(firstActive.getObservedAt());
        firstFillAfterAt=!timingCoherent || firstPositive==null || lastZeroBeforeFill==null?null:lastZeroBeforeFill.getObservedAt();
        firstFillByAt=!timingCoherent || firstPositive==null?null:afterSecond(firstPositive.getObservedAt());
        boolean complete=timingCoherent && terminal!=null && terminal.isComplete();
        completionAfterAt=complete && lastActive!=null?lastActive.getObservedAt():null;
        completionByAt=complete?afterSecond(terminal.getObservedAt()):null;
        durationLowerSeconds=placementUpperAt==null || lastActive==null?null
            :Math.max(0,lastActive.getObservedAt()-placementUpperAt);
        durationUpperSeconds=!complete || placementLowerAt==null?null:completionByAt-placementLowerAt;
        firstFillLowerSeconds=firstFillAfterAt==null || placementUpperAt==null?null
            :Math.max(0,firstFillAfterAt-placementUpperAt);
        firstFillUpperSeconds=firstFillByAt==null || placementLowerAt==null?null:firstFillByAt-placementLowerAt;
        outcome=!timingCoherent?"INCONSISTENT_SOURCE":firstActive==null?"UNKNOWN_START":complete?"COMPLETED_INTERVAL"
            :terminal!=null?"CANCELLED_CENSORED":endedWithoutTerminal?"OBSERVATION_ENDED_CENSORED":"OPEN_CENSORED";
    }
    private static long afterSecond(long at) { return Math.addExact(at,1); }
    public String getSourceEventId(){return sourceEventId;}
    public String getSessionId(){return sessionId;}
    public String getOfferIdentity(){return offerIdentity;}
    public String getTerminalEventId(){return terminalEventId;}
    public String getLastActiveEventId(){return lastActiveEventId;}
    public int getSlot(){return slot;}
    public int getItemId(){return itemId;}
    public int getQuantity(){return quantity;}
    public int getPrice(){return price;}
    public boolean isBuying(){return buying;}
    public boolean isPredictionComparable(){return predictionComparable;}
    public boolean isTimingCoherent(){return timingCoherent;}
    public double getPredictedMinutes(){return predictedMinutes;}
    public String getOutcome(){return outcome;}
    public String getReason(){return reason;}
    public Long getPlacementLowerAt(){return placementLowerAt;}
    public Long getPlacementUpperAt(){return placementUpperAt;}
    public Long getFirstFillAfterAt(){return firstFillAfterAt;}
    public Long getFirstFillByAt(){return firstFillByAt;}
    public Long getCompletionAfterAt(){return completionAfterAt;}
    public Long getCompletionByAt(){return completionByAt;}
    public Long getDurationLowerSeconds(){return durationLowerSeconds;}
    public Long getDurationUpperSeconds(){return durationUpperSeconds;}
    public Long getFirstFillLowerSeconds(){return firstFillLowerSeconds;}
    public Long getFirstFillUpperSeconds(){return firstFillUpperSeconds;}
}
