package com.flippingfriend.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Account-scoped evidence and explicit promotion hold; public-price estimates are not proven returns. */
public final class ExecutionLearningReport {
    private final String schemaVersion, accountId, status, reason, activeModel, promotionStatus;
    private final long generatedAt, sourceCursor;
    private final int completedIntervals, cancelledCensored, openCensored, endedCensored, unknownStarts, inconsistentSources, comparableForecasts;
    private final int totalObservations;
    private final boolean observationsTruncated;
    private final List<ExecutionTimingObservation> observations;
    public ExecutionLearningReport(String accountId,long generatedAt,long cursor,String status,String reason,
            List<ExecutionTimingObservation> observations) {
        this.schemaVersion="1";this.accountId=accountId;this.generatedAt=generatedAt;this.sourceCursor=cursor;
        this.status=status;this.reason=reason;activeModel="PUBLIC_PRICE_BASELINE";promotionStatus="HOLD_FOR_VALIDATION";
        int complete=0,cancel=0,open=0,ended=0,unknown=0,inconsistent=0,comparable=0;
        for(var observation:observations) {
            if("COMPLETED_INTERVAL".equals(observation.getOutcome()))complete++;
            else if("CANCELLED_CENSORED".equals(observation.getOutcome()))cancel++;
            else if("OPEN_CENSORED".equals(observation.getOutcome()))open++;
            else if("OBSERVATION_ENDED_CENSORED".equals(observation.getOutcome()))ended++;
            else if("INCONSISTENT_SOURCE".equals(observation.getOutcome()))inconsistent++;
            else unknown++;
            if(observation.isPredictionComparable())comparable++;
        }
        completedIntervals=complete;cancelledCensored=cancel;openCensored=open;endedCensored=ended;
        unknownStarts=unknown;inconsistentSources=inconsistent;comparableForecasts=comparable;
        totalObservations=observations.size();observationsTruncated=totalObservations>200;
        // Bounded diagnostic response; counters cover the complete source prefix, not this tail.
        this.observations=Collections.unmodifiableList(new ArrayList<>(observations.subList(Math.max(0,totalObservations-200),totalObservations)));
    }
    public static ExecutionLearningReport unavailable(String account,long at,String reason) {
        return new ExecutionLearningReport(account,at,0,"UNAVAILABLE",reason,Collections.emptyList());
    }
    public String getSchemaVersion(){return schemaVersion;}
    public String getAccountId(){return accountId;}
    public String getStatus(){return status;}
    public String getReason(){return reason;}
    public String getActiveModel(){return activeModel;}
    public String getPromotionStatus(){return promotionStatus;}
    public long getGeneratedAt(){return generatedAt;}
    public long getSourceCursor(){return sourceCursor;}
    public int getCompletedIntervals(){return completedIntervals;}
    public int getCancelledCensored(){return cancelledCensored;}
    public int getOpenCensored(){return openCensored;}
    public int getEndedCensored(){return endedCensored;}
    public int getUnknownStarts(){return unknownStarts;}
    public int getInconsistentSources(){return inconsistentSources;}
    public int getComparableForecasts(){return comparableForecasts;}
    public int getTotalObservations(){return totalObservations;}
    public boolean isObservationsTruncated(){return observationsTruncated;}
    public List<ExecutionTimingObservation> getObservations(){return observations==null?Collections.emptyList():Collections.unmodifiableList(observations);}
}
