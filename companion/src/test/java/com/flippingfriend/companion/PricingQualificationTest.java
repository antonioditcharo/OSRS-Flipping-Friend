package com.flippingfriend.companion;

import com.google.gson.Gson;
import java.nio.file.*;
import java.util.*;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class PricingQualificationTest {
    @Rule public TemporaryFolder folder=new TemporaryFolder();
    private Path root()throws Exception{return folder.newFolder("local-data").toPath();}
    private Path freeze(Path root,AccountReplayInput input)throws Exception {
        Path source=root.resolve("new-input.json"),excluded=root.resolve("excluded.json");
        Files.writeString(source,new Gson().toJson(input));
        Files.writeString(excluded,new Gson().toJson(Map.of("schemaVersion","1","tunedThrough",input.startAt-1,
            "inputSha256",List.of("a".repeat(64)),"availabilityEvidence","DEVELOPMENT_ONLY")));
        Path cohort=root.resolve("cohort");PricingQualificationMain.freeze(source,excluded,cohort);return cohort;
    }
    @Test public void chronologicalSlicesKeepPastWarmupButNeverFutureFramesAndPreserveVisitAssumptions()throws Exception {
        var input=new AccountReplayTest().input(1,30);input.restartAt=List.of(input.startAt+60,input.startAt+1500);
        input.missedVisitIndices=List.of(1,3);var root=root();Path cohort=freeze(root,input);
        var m=PricingQualificationMain.verify(cohort);
        assertEquals(input.startAt+1080,m.fitEnd);assertEquals(input.startAt+1440,m.validationEnd);
        var validation=PricingQualificationMain.slice(input,m.fitEnd,m.validationEnd);
        assertEquals(input.frames.get(0).availableAt,validation.frames.get(0).availableAt);
        assertTrue(validation.frames.stream().allMatch(f->f.availableAt<=m.validationEnd));
        assertEquals(input.missedVisitIndices,validation.missedVisitIndices);assertTrue(validation.restartAt.isEmpty());
        assertEquals(100_000_000,validation.initialCoins);assertEquals(8,validation.slots);
    }
    @Test public void failedOrUnsupportedValidationLeavesFinalTestUnopenedAndCannotBeOverwritten()throws Exception {
        var root=root();Path cohort=freeze(root,new AccountReplayTest().input(1,30));
        PricingQualificationMain.evaluate(cohort,"fit");PricingQualificationMain.evaluate(cohort,"validation");
        String report=Files.readString(cohort.resolve("validation/results.json"));assertTrue(report.contains("UNSUPPORTED_AVAILABILITY_CLOCKS"));
        for(int cadence:new int[]{5,30,120,480})assertTrue(report.contains("\"cadenceMinutes\":"+cadence));
        try{PricingQualificationMain.evaluate(cohort,"test");fail();}catch(IllegalStateException expected){}
        assertFalse(Files.exists(cohort.resolve("test-consumed.json")));assertFalse(Files.exists(cohort.resolve("test")));
        try{PricingQualificationMain.evaluate(cohort,"validation");fail();}catch(FileAlreadyExistsException expected){}
        Files.writeString(cohort.resolve("validation/results.json"),report+" ");
        try{PricingQualificationMain.evaluate(cohort,"test");fail();}catch(IllegalArgumentException expected){}
    }
    @Test public void tunedInputsOrChangedManifestsCannotBeCalledANewCohort()throws Exception {
        var input=new AccountReplayTest().input(1,30);var root=root();Path cohort=freeze(root,input);
        byte[] bytes=Files.readAllBytes(root.resolve("new-input.json"));
        Files.writeString(root.resolve("excluded-again.json"),new Gson().toJson(Map.of("schemaVersion","1","tunedThrough",input.startAt-1,
            "inputSha256",List.of(ExecutionEvaluationMain.hash(bytes)),"availabilityEvidence","DEVELOPMENT_ONLY")));
        try{PricingQualificationMain.freeze(root.resolve("new-input.json"),root.resolve("excluded-again.json"),root.resolve("again"));fail();}
        catch(IllegalArgumentException expected){}
        Files.writeString(cohort.resolve("input.json"),new String(bytes,java.nio.charset.StandardCharsets.UTF_8)+" ");
        try{PricingQualificationMain.verify(cohort);fail();}catch(IllegalArgumentException expected){}
    }
}
