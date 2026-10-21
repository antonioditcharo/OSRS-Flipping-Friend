package com.flippingfriend.companion;

import com.flippingfriend.core.ExecutionForecast;
import com.flippingfriend.core.ExecutionTimingObservation;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.util.*;

/** Complete frozen-source diagnostics. No fitting, financial writes or independent-censoring claim. */
final class ExecutionForecastAudit {
    final String schemaVersion="1",status="UNVALIDATED_ESTIMATOR_DIAGNOSTICS",promotionStatus="HOLD_FOR_VALIDATION";
    final String interpretation="Completion bounds retain every ambiguous/censored episode. Beyond estimate is diagnostic only; it does not authorize repricing. Source-history freshness cannot be recovered from aggregate forecast inputs.";
    final String manifestSha256;
    final long capturedAt,sourceCursor;
    final int totalObservations,eligible,unqualified;
    final List<Group> configurations,orders;
    final List<Row> observations;

    ExecutionForecastAudit(ExecutionEvaluationMain.Verified verified) {
        this(verified.snapshot.capturedAt,verified.snapshot.sourceCursor,verified.manifestHash,verified.snapshot.observations());
    }
    ExecutionForecastAudit(long capturedAt,long cursor,String manifest,List<ExecutionTimingObservation> source) {
        this.capturedAt=capturedAt;sourceCursor=cursor;manifestSha256=manifest;totalObservations=source.size();
        ExecutionHorizonEvaluation evaluation=new ExecutionHorizonEvaluation(source,null);
        eligible=evaluation.eligible;unqualified=evaluation.unqualified;
        Map<String,ExecutionHorizonEvaluation.Score> scores=new HashMap<>();
        for(var score:evaluation.scores)scores.put(score.sourceEventId,score);
        Map<String,Group> byConfiguration=new LinkedHashMap<>(),byOrder=new LinkedHashMap<>();
        List<Row> rows=new ArrayList<>();
        for(var observation:source) {
            var score=scores.get(observation.getSourceEventId());
            rows.add(new Row(observation,score));
            if(score==null)continue;
            JsonObject configuration=configuration(observation.getExecutionForecast());
            String key=configuration.toString();
            byConfiguration.computeIfAbsent(key,k->new Group(configuration,null)).add(observation,score);
            String order=key+":"+observation.getItemId()+":"+observation.getPrice()+":"+observation.getQuantity();
            byOrder.computeIfAbsent(order,k->new Group(configuration,observation)).add(observation,score);
        }
        configurations=finish(byConfiguration);orders=finish(byOrder);observations=Collections.unmodifiableList(rows);
    }
    private static List<Group> finish(Map<String,Group> groups) {
        for(var group:groups.values())group.finish();
        return Collections.unmodifiableList(new ArrayList<>(groups.values()));
    }
    private static JsonObject configuration(ExecutionForecast forecast) {
        JsonObject raw=new Gson().toJsonTree(forecast).getAsJsonObject(),config=new JsonObject();
        for(String name:List.of("modelVersion","buying","captureRate","evidenceWeight","waitWeight",
                "lumpyArrivals","seasonalMultiplier","waitMultiplier","horizonSeconds"))config.add(name,raw.get(name));
        return config;
    }
    static final class Group {
        final JsonObject configuration;
        final Integer itemId,price,quantity;
        final Map<String,Integer> outcomes=new LinkedHashMap<>();
        int count,knownCompleted,knownIncomplete,ambiguous,completedBeforeEstimate,confirmedBeyondEstimate,estimateComparisonUnknown;
        double meanProbability,completionRateLower,completionRateUpper;
        Group(JsonObject configuration,ExecutionTimingObservation order) {
            this.configuration=configuration;itemId=order==null?null:order.getItemId();
            price=order==null?null:order.getPrice();quantity=order==null?null:order.getQuantity();
        }
        void add(ExecutionTimingObservation observation,ExecutionHorizonEvaluation.Score score) {
            count++;meanProbability+=score.probability;outcomes.merge(observation.getOutcome(),1,Integer::sum);
            if("COMPLETED_BY_HORIZON".equals(score.horizonOutcome))knownCompleted++;
            else if("INCOMPLETE_AT_HORIZON".equals(score.horizonOutcome))knownIncomplete++;
            else ambiguous++;
            double expected=observation.getExecutionForecast().getExpectedTotalSeconds();
            if(observation.getDurationLowerSeconds()!=null && observation.getDurationLowerSeconds()>expected)confirmedBeyondEstimate++;
            else if(observation.getDurationUpperSeconds()!=null && observation.getDurationUpperSeconds()<expected)completedBeforeEstimate++;
            else estimateComparisonUnknown++;
        }
        void finish() {
            meanProbability/=count;completionRateLower=(double)knownCompleted/count;
            completionRateUpper=(double)(knownCompleted+ambiguous)/count;
        }
    }
    static final class Row {
        final String sourceEventId,outcome,reason,horizonOutcome;
        final int itemId,price,quantity;
        final boolean buying,eligible;
        final ExecutionForecast forecast;
        final Long durationLowerSeconds,durationUpperSeconds,firstFillLowerSeconds,firstFillUpperSeconds;
        final Boolean confirmedBeyondEstimate;
        Row(ExecutionTimingObservation observation,ExecutionHorizonEvaluation.Score score) {
            sourceEventId=observation.getSourceEventId();outcome=observation.getOutcome();reason=observation.getReason();
            itemId=observation.getItemId();price=observation.getPrice();quantity=observation.getQuantity();buying=observation.isBuying();
            eligible=score!=null;horizonOutcome=score==null?"UNQUALIFIED":score.horizonOutcome;
            forecast=observation.getExecutionForecast();durationLowerSeconds=observation.getDurationLowerSeconds();
            durationUpperSeconds=observation.getDurationUpperSeconds();firstFillLowerSeconds=observation.getFirstFillLowerSeconds();
            firstFillUpperSeconds=observation.getFirstFillUpperSeconds();
            confirmedBeyondEstimate=!eligible?null
                :durationLowerSeconds!=null && durationLowerSeconds>forecast.getExpectedTotalSeconds()?Boolean.TRUE
                :durationUpperSeconds!=null && durationUpperSeconds<=forecast.getExpectedTotalSeconds()?Boolean.FALSE:null;
        }
    }
}
