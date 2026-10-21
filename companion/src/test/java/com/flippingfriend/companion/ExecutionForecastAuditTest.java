package com.flippingfriend.companion;

import com.flippingfriend.core.*;
import com.google.gson.Gson;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class ExecutionForecastAuditTest {
    @Rule public TemporaryFolder folder=new TemporaryFolder();
    private final Gson gson=new Gson();
    private ExecutionTimingObservation observation(boolean buy,long active,long terminal,String state,String id) {
        var history=ExecutionHorizonEvaluationTest.history(ExecutionHorizonEvaluationTest.forecast(buy,300,.8),active,terminal,state,true);
        var value=ExecutionObservationLedger.compile(ExecutionHorizonEvaluationTest.ACCOUNT,
            ExecutionHorizonEvaluationTest.AT+10000,history,Set.of()).get(0);
        var raw=gson.toJsonTree(value).getAsJsonObject();raw.addProperty("sourceEventId",id);
        return gson.fromJson(raw,ExecutionTimingObservation.class);
    }
    @Test public void configurationsAndExactOrdersStaySeparateWithoutDroppingCensoredOutcomes() {
        var completed=observation(false,150,200,"SOLD","complete");
        var cancelled=observation(false,150,600,"CANCELLED_SELL","cancel");
        var beyond=observation(false,900,0,"OPEN","beyond");
        var different=gson.toJsonTree(completed).getAsJsonObject();different.addProperty("sourceEventId","different");
        different.addProperty("price",101);different.getAsJsonObject("executionForecast").addProperty("price",101);
        var anotherConfiguration=gson.toJsonTree(completed).getAsJsonObject();anotherConfiguration.addProperty("sourceEventId","config");
        anotherConfiguration.getAsJsonObject("executionForecast").addProperty("captureRate",.55);
        var buy=observation(true,150,200,"BOUGHT","buy");
        var audit=new ExecutionForecastAudit(1700010000,99,"manifest",List.of(completed,cancelled,beyond,
            gson.fromJson(different,ExecutionTimingObservation.class),
            gson.fromJson(anotherConfiguration,ExecutionTimingObservation.class),buy));
        assertEquals(6,audit.totalObservations);assertEquals(6,audit.eligible);assertEquals(0,audit.unqualified);
        assertEquals(3,audit.configurations.size());assertEquals(4,audit.orders.size());
        var group=audit.orders.get(0);assertEquals(3,group.count);
        assertEquals(1,group.knownCompleted);assertEquals(1,group.knownIncomplete);assertEquals(1,group.ambiguous);
        assertEquals(1.0/3,group.completionRateLower,1e-12);assertEquals(2.0/3,group.completionRateUpper,1e-12);
        assertEquals(.8,group.meanProbability,1e-12);assertEquals(1,group.confirmedBeyondEstimate);
        assertEquals(1,group.completedBeforeEstimate);assertEquals(1,group.estimateComparisonUnknown);
        var censored=audit.observations.get(1);assertNull(censored.durationUpperSeconds);
        assertNull("early censoring cannot prove a duration comparison",censored.confirmedBeyondEstimate);
        assertEquals("UNKNOWN_AT_HORIZON",censored.horizonOutcome);
        assertEquals("HOLD_FOR_VALIDATION",audit.promotionStatus);
        assertEquals(Boolean.TRUE,audit.observations.get(2).confirmedBeyondEstimate);
    }
    @Test public void unqualifiedOriginalsRemainVisibleWithoutBorrowingLaterForecasts() {
        var history=ExecutionHorizonEvaluationTest.history(ExecutionHorizonEvaluationTest.forecast(false,300,.8),150,200,"SOLD",true);
        var observations=ExecutionObservationLedger.compile(ExecutionHorizonEvaluationTest.ACCOUNT,
            ExecutionHorizonEvaluationTest.AT+10000,history,Set.of("offer"));
        var audit=new ExecutionForecastAudit(1700010000,99,"manifest",observations);
        assertEquals(1,audit.totalObservations);assertEquals(0,audit.eligible);assertEquals(1,audit.unqualified);
        assertTrue(audit.configurations.isEmpty());assertEquals(1,audit.observations.size());
        assertNull(audit.observations.get(0).forecast);assertNull(audit.observations.get(0).confirmedBeyondEstimate);
        assertEquals("UNQUALIFIED",audit.observations.get(0).horizonOutcome);
    }
    @Test public void supportedAuditVerifiesFrozenHashesAndCannotWriteTheBundle()throws Exception {
        var fixture=new ExecutionEvaluationBundleTest();
        Path target=folder.getRoot().toPath().resolve("local-data/capture");
        ExecutionEvaluationMain.writeSnapshot(fixture.snapshot(250,fixture.history(true)),target,ExecutionEvaluationBundleTest.REVISION);
        byte[] before=Files.readAllBytes(target.resolve("sources.json"));
        var audit=ExecutionEvaluationMain.audit(target);assertEquals(1,audit.eligible);assertEquals(4,audit.sourceCursor);
        assertNotNull(audit.manifestSha256);assertArrayEquals(before,Files.readAllBytes(target.resolve("sources.json")));
        Files.writeString(target.resolve("sources.json")," ",StandardCharsets.UTF_8,StandardOpenOption.APPEND);
        try{ExecutionEvaluationMain.audit(target);fail("accepted modified source file");}
        catch(IllegalArgumentException expected){ }
    }
}
