package com.flippingfriend.companion;

import com.flippingfriend.core.ExecutionForecast;
import java.util.*;

/** Offline bounded logit calibration. Censored outcomes contribute worst-case loss, never fake labels. */
final class ExecutionChallenger {
    static final String METHOD="BOUNDED_LOGIT_MINIMAX_BRIER_V1";
    final String schemaVersion="1",method=METHOD,baselineModel=ExecutionForecast.MODEL_VERSION;
    final String trainingManifestSha256,accountId;
    final String fitterClassSha256=codeIdentity();
    final String fitterArtifactSha256=artifactIdentity();
    final long capturedAt,sourceCursor;
    final int eligible,unqualified,ambiguous;
    final Map<String,Parameter> parameters;
    ExecutionChallenger(ExecutionEvaluationMain.Verified training) {
        trainingManifestSha256=training.manifestHash;accountId=training.snapshot.accountId;
        capturedAt=training.snapshot.capturedAt;sourceCursor=training.snapshot.sourceCursor;
        eligible=training.evaluation.eligible;unqualified=training.evaluation.unqualified;ambiguous=training.evaluation.ambiguous;
        Map<String,List<ExecutionHorizonEvaluation.Score>> groups=new TreeMap<>();
        Set<String> buys=new HashSet<>();
        for(var o:training.snapshot.observations())if(o.getExecutionForecast()!=null && o.getExecutionForecast().isBuying())buys.add(o.getSourceEventId());
        for(var score:training.evaluation.scores)groups.computeIfAbsent(key(buys.contains(score.sourceEventId),score.horizonSeconds),k->new ArrayList<>()).add(score);
        Map<String,Parameter> fitted=new TreeMap<>();groups.forEach((key,scores)->fitted.put(key,fit(scores)));parameters=Collections.unmodifiableMap(fitted);
    }
    static final class Parameter {
        final double logitOffset;final int count,knownCompleted,knownIncomplete,unknown;
        final double baselineWorstBrier,fittedWorstBrier;
        Parameter(double offset,List<ExecutionHorizonEvaluation.Score> scores) {
            logitOffset=offset;count=scores.size();int yes=0,no=0;
            for(var s:scores)if("COMPLETED_BY_HORIZON".equals(s.horizonOutcome))yes++;else if("INCOMPLETE_AT_HORIZON".equals(s.horizonOutcome))no++;
            knownCompleted=yes;knownIncomplete=no;unknown=count-yes-no;
            baselineWorstBrier=objective(scores,0,false);fittedWorstBrier=objective(scores,offset,false);
        }
    }
    private static Parameter fit(List<ExecutionHorizonEvaluation.Score> scores) {
        boolean informative=scores.stream().anyMatch(s->!"UNKNOWN_AT_HORIZON".equals(s.horizonOutcome));
        double best=objective(scores,0,true),offset=0;
        if(informative)for(int step=-20;step<=20;step++) {
            double candidate=step*.05,loss=objective(scores,candidate,true);
            if(loss<best-1e-12){best=loss;offset=candidate;}
        }
        return new Parameter(offset,scores);
    }
    private static double objective(List<ExecutionHorizonEvaluation.Score> scores,double shift,boolean regularize) {
        double loss=0;
        for(var s:scores) {
            double p=adjust(s.probability,shift),no=p*p,yes=(1-p)*(1-p);
            loss+="COMPLETED_BY_HORIZON".equals(s.horizonOutcome)?yes:"INCOMPLETE_AT_HORIZON".equals(s.horizonOutcome)?no:Math.max(no,yes);
        }
        return loss/scores.size()+(regularize?.01*shift*shift:0);
    }
    double probability(boolean buying,double horizon,double baseline) {
        Parameter p=parameters.get(key(buying,horizon));return p==null?baseline:adjust(baseline,p.logitOffset);
    }
    static double adjust(double p,double shift) {
        if(!Double.isFinite(p) || p<0 || p>1 || !Double.isFinite(shift) || Math.abs(shift)>1)throw new IllegalArgumentException("Invalid completion model parameter");
        if(p==0 || p==1 || shift==0)return p;
        return 1/(1+Math.exp(-(Math.log(p/(1-p))+shift)));
    }
    static String key(boolean buying,double horizon){return (buying?"BUY:":"SELL:")+Double.toHexString(horizon);}
    private static String codeIdentity() {
        try(var code=ExecutionChallenger.class.getResourceAsStream("ExecutionChallenger.class")) {
            if(code==null)throw new IllegalStateException("Missing fitter code identity");
            return ExecutionEvaluationMain.hash(code.readAllBytes());
        }catch(Exception failure){throw new IllegalStateException("Cannot identify fitting code",failure);}
    }
    static byte[] artifact() {
        try {
            var path=java.nio.file.Path.of(ExecutionChallenger.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            return java.nio.file.Files.isRegularFile(path)?AccountReplayMain.read(path):null;
        }catch(Exception failure){throw new IllegalStateException("Cannot retain fitting artifact",failure);}
    }
    private static String artifactIdentity() {
        try {byte[] code=artifact();return code==null?null:ExecutionEvaluationMain.hash(code);}
        catch(Exception failure){throw new IllegalStateException("Cannot identify fitting artifact",failure);}
    }
    static final class Validation {
        final String status="OUT_OF_TIME_OBSERVATIONAL_BOUNDS",promotionStatus;
        final int eligible,known,unknown;
        final Double pairedBrierChangeLower,pairedBrierChangeUpper;
        final Map<String,Slice> slices;
        final Map<String,Slice> temporalSlices;
        final Map<String,Integer> censoringOutcomes;
        final List<String> refusals;
        Validation(ExecutionChallenger model,ExecutionEvaluationMain.Verified valid,ExecutionHorizonEvaluation cohort) {
            eligible=cohort.eligible;known=cohort.knownCompleted+cohort.knownIncomplete;unknown=cohort.ambiguous;
            Map<String,Boolean> sides=new HashMap<>();
            Map<String,Long> days=new HashMap<>();Map<String,String> outcomes=new HashMap<>();
            for(var o:valid.snapshot.observations())if(o.getExecutionForecast()!=null) {
                sides.put(o.getSourceEventId(),o.getExecutionForecast().isBuying());
                if(o.getPlacementUpperAt()!=null)days.put(o.getSourceEventId(),Math.floorDiv(o.getPlacementUpperAt(),86400));
                outcomes.put(o.getSourceEventId(),o.getOutcome());
            }
            Map<String,double[]> totals=new TreeMap<>(),periods=new TreeMap<>();Map<String,Integer> censoring=new TreeMap<>();double lower=0,upper=0;
            for(var s:cohort.scores) {
                boolean buy=Boolean.TRUE.equals(sides.get(s.sourceEventId));double p=model.probability(buy,s.horizonSeconds,s.probability);
                double d0=p*p-s.probability*s.probability,d1=(1-p)*(1-p)-(1-s.probability)*(1-s.probability);
                double low="COMPLETED_BY_HORIZON".equals(s.horizonOutcome)?d1:"INCOMPLETE_AT_HORIZON".equals(s.horizonOutcome)?d0:Math.min(d0,d1);
                double high="COMPLETED_BY_HORIZON".equals(s.horizonOutcome)?d1:"INCOMPLETE_AT_HORIZON".equals(s.horizonOutcome)?d0:Math.max(d0,d1);
                lower+=low;upper+=high;
                String key=key(buy,s.horizonSeconds)+":confidence-"+Math.min(3,(int)(s.probability*4));
                double[] total=totals.computeIfAbsent(key,k->new double[4]);total[0]++;total[1]+=low;total[2]+=high;
                if("UNKNOWN_AT_HORIZON".equals(s.horizonOutcome))total[3]++;
                double[] period=periods.computeIfAbsent("UTC-day-"+days.get(s.sourceEventId)+":"+key(buy,s.horizonSeconds),k->new double[4]);
                period[0]++;period[1]+=low;period[2]+=high;if("UNKNOWN_AT_HORIZON".equals(s.horizonOutcome))period[3]++;
                censoring.merge(outcomes.get(s.sourceEventId),1,Integer::sum);
            }
            pairedBrierChangeLower=eligible==0?null:lower/eligible;pairedBrierChangeUpper=eligible==0?null:upper/eligible;
            Map<String,Slice> summary=new TreeMap<>();totals.forEach((key,total)->summary.put(key,new Slice(total)));slices=Collections.unmodifiableMap(summary);
            Map<String,Slice> temporal=new TreeMap<>();periods.forEach((key,total)->temporal.put(key,new Slice(total)));temporalSlices=Collections.unmodifiableMap(temporal);
            censoringOutcomes=Collections.unmodifiableMap(censoring);
            List<String> reasons=new ArrayList<>();
            if(known<20)reasons.add("Fewer than 20 informative out-of-time outcomes; counts alone never permit promotion");
            if(pairedBrierChangeUpper==null || pairedBrierChangeUpper>=-.005)reasons.add("Worst-case paired Brier improvement is below the fixed 0.005 gate");
            if(slices.values().stream().anyMatch(s->s.changeUpper>0))reasons.add("A side/horizon/confidence slice can degrade");
            if(temporalSlices.values().stream().anyMatch(s->s.changeUpper>0))reasons.add("A chronological period can degrade");
            reasons.add("Independent chronological whole-account return/loss evidence and explicit release approval are required");
            refusals=List.copyOf(reasons);promotionStatus="HOLD_FOR_VALIDATION";
        }
    }
    static final class Slice {
        final int count,unknown;final double changeLower,changeUpper;
        Slice(double[] total){count=(int)total[0];unknown=(int)total[3];changeLower=total[1]/count;changeUpper=total[2]/count;}
    }
}
