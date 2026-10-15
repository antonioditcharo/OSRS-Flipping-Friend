package com.flippingfriend.companion;

import com.flippingfriend.core.*;
import java.util.*;

/** Partial-identification bounds. No independent-censoring assumption, fitted model or promotion. */
final class ExecutionHorizonEvaluation {
    final String schemaVersion,algorithmVersion,status,promotionStatus;
    final int totalObservations,cohortExcluded,unqualified,eligible,knownCompleted,knownIncomplete,ambiguous;
    final List<Group> groups;
    final List<Score> scores;
    ExecutionHorizonEvaluation(List<ExecutionTimingObservation> observations,Set<String> cohort) {
        schemaVersion="1";algorithmVersion="HORIZON_BOUNDS_V1";status="OBSERVATIONAL_BOUNDS";promotionStatus="HOLD_FOR_VALIDATION";
        totalObservations=observations.size();int outside=0,unknown=0,yes=0,no=0,maybe=0;
        List<Score> scored=new ArrayList<>();Map<String,Accumulator> grouped=new TreeMap<>();
        for(var observation:observations) {
            if(cohort!=null && !cohort.contains(observation.getSourceEventId())){outside++;continue;}
            ExecutionForecast f=observation.getExecutionForecast();
            if(!observation.isPredictionComparable() || !observation.isTimingCoherent() || f==null || !f.isValid()){unknown++;continue;}
            Long lower=observation.getDurationLowerSeconds(),upper=observation.getDurationUpperSeconds();
            double horizon=f.getHorizonSeconds(),p=f.getCompletionProbability();
            // Bounds are inclusive conservative intervals: equality at the lower bound is ambiguous.
            String label=upper!=null && upper<=horizon?"COMPLETED_BY_HORIZON"
                :lower!=null && lower>horizon?"INCOMPLETE_AT_HORIZON":"UNKNOWN_AT_HORIZON";
            if("COMPLETED_BY_HORIZON".equals(label))yes++;else if("INCOMPLETE_AT_HORIZON".equals(label))no++;else maybe++;
            double failureLoss=p*p,successLoss=(1-p)*(1-p);
            double low="COMPLETED_BY_HORIZON".equals(label)?successLoss:"INCOMPLETE_AT_HORIZON".equals(label)?failureLoss:Math.min(failureLoss,successLoss);
            double high="COMPLETED_BY_HORIZON".equals(label)?successLoss:"INCOMPLETE_AT_HORIZON".equals(label)?failureLoss:Math.max(failureLoss,successLoss);
            Score score=new Score(observation.getSourceEventId(),f.getFingerprint(),observation.getOutcome(),label,p,horizon,low,high);
            scored.add(score);String key=(f.isBuying()?"BUY":"SELL")+":"+Double.toHexString(horizon);
            grouped.computeIfAbsent(key,k->new Accumulator(f.isBuying(),horizon)).add(score);
        }
        cohortExcluded=outside;unqualified=unknown;knownCompleted=yes;knownIncomplete=no;ambiguous=maybe;eligible=scored.size();
        scores=Collections.unmodifiableList(scored);List<Group> summaries=new ArrayList<>();
        for(var value:grouped.values())summaries.add(value.finish());groups=Collections.unmodifiableList(summaries);
    }
    static final class Score {
        final String sourceEventId,forecastFingerprint,observationOutcome,horizonOutcome;
        final double probability,horizonSeconds,brierLower,brierUpper;
        Score(String id,String fingerprint,String outcome,String label,double p,double h,double low,double high) {
            sourceEventId=id;forecastFingerprint=fingerprint;observationOutcome=outcome;horizonOutcome=label;
            probability=p;horizonSeconds=h;brierLower=low;brierUpper=high;
        }
    }
    static final class Group {
        final boolean buying;final double horizonSeconds,meanProbability,completionRateLower,completionRateUpper,meanBrierLower,meanBrierUpper;
        final int count,knownCompleted,knownIncomplete,ambiguous;
        Group(Accumulator a){buying=a.buying;horizonSeconds=a.horizon;count=a.count;knownCompleted=a.yes;knownIncomplete=a.no;
            ambiguous=count-knownCompleted-knownIncomplete;meanProbability=a.probability/count;
            completionRateLower=(double)knownCompleted/count;completionRateUpper=(double)(knownCompleted+ambiguous)/count;
            meanBrierLower=a.low/count;meanBrierUpper=a.high/count;}
    }
    private static final class Accumulator {
        final boolean buying;final double horizon;int count,yes,no;double probability,low,high;
        Accumulator(boolean side,double h){buying=side;horizon=h;}
        void add(Score s){count++;probability+=s.probability;low+=s.brierLower;high+=s.brierUpper;
            if("COMPLETED_BY_HORIZON".equals(s.horizonOutcome))yes++;else if("INCOMPLETE_AT_HORIZON".equals(s.horizonOutcome))no++;}
        Group finish(){return new Group(this);}
    }
}
