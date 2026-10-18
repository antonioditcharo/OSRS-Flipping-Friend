package com.flippingfriend.companion;

import com.flippingfriend.core.*;
import com.google.gson.Gson;
import java.nio.file.*;
import java.util.*;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class ExecutionChallengerTest {
    @Rule public TemporaryFolder folder=new TemporaryFolder();
    final Gson json=new Gson();final ExecutionEvaluationBundleTest fixtures=new ExecutionEvaluationBundleTest();
    Path bundle(String name,long at,List<OfferEvent> history)throws Exception {
        Path out=folder.getRoot().toPath().resolve("local-data").resolve(name);
        ExecutionEvaluationMain.writeSnapshot(fixtures.snapshot(at,history),out,ExecutionEvaluationBundleTest.REVISION);return out;
    }
    Path fit(Path train)throws Exception {
        Path out=folder.getRoot().toPath().resolve("local-data/model");
        ExecutionChallengerMain.main(new String[]{"fit",train.toString(),out.toString()});return out;
    }
    @Test public void allUnknownTrainingRetainsEveryEpisodeAndDoesNotInventLabelsOrAParameter()throws Exception {
        Path train=bundle("train",250,fixtures.history(false));Path model=fit(train);
        var fitted=ExecutionChallengerMain.load(train,model);assertEquals(1,fitted.eligible);assertEquals(1,fitted.ambiguous);
        assertEquals(1,fitted.parameters.size());var p=fitted.parameters.values().iterator().next();
        assertEquals(1,p.unknown);assertEquals(0,p.logitOffset,0);
        assertEquals(.8,fitted.probability(true,300,.8),0);
        assertEquals(.7,fitted.probability(false,300,.7),0);
    }
    @Test public void knownTrainingFitsABoundedMonotoneCompletionCorrectionWithoutChangingTiming()throws Exception {
        Path train=bundle("train",250,fixtures.history(true));var fitted=ExecutionChallengerMain.load(train,fit(train));
        var p=fitted.parameters.values().iterator().next();assertEquals(1,p.knownCompleted);assertTrue(p.logitOffset>0 && p.logitOffset<=1);
        assertTrue(p.fittedWorstBrier<p.baselineWorstBrier);
        assertTrue(fitted.probability(true,300,.4)<fitted.probability(true,300,.8));
        assertEquals(.8,fitted.probability(true,600,.8),0);
    }
    @Test public void laterSettlementOfOldTrainingCannotBeUsedAsValidationOrRefitTheFrozenModel()throws Exception {
        var first=fixtures.history(false);Path train=bundle("train",250,first),model=fit(train);
        List<OfferEvent> later=new ArrayList<>(first);
        later.add(ExecutionHorizonEvaluationTest.event("terminal",4,400,"BOUGHT",10,ExecutionHorizonEvaluationTest.forecast(true,300,.8)));
        later=fixtures.appendEpisode(later,400);Path valid=bundle("valid",700,later);
        byte[] before=Files.readAllBytes(model.resolve("model.json"));
        var result=ExecutionChallengerMain.evaluate(train,model,valid);
        assertEquals(1,result.eligible);assertEquals("HOLD_FOR_VALIDATION",result.promotionStatus);
        assertEquals(1,result.temporalSlices.values().stream().mapToInt(s->s.count).sum());
        assertEquals(1,result.censoringOutcomes.values().stream().mapToInt(Integer::intValue).sum());
        assertArrayEquals(before,Files.readAllBytes(model.resolve("model.json")));
    }
    @Test public void rehashingEditedModelCannotBypassReproducibleFitting()throws Exception {
        Path train=bundle("train",250,fixtures.history(true)),model=fit(train);
        var raw=json.fromJson(Files.readString(model.resolve("model.json")),com.google.gson.JsonObject.class);
        raw.getAsJsonObject("parameters").entrySet().iterator().next().getValue().getAsJsonObject().addProperty("logitOffset",-1);
        byte[] changed=AccountReplayMain.bytes(raw);Files.write(model.resolve("model.json"),changed);
        Files.writeString(model.resolve("model.sha256"),ExecutionEvaluationMain.hash(changed));
        try{ExecutionChallengerMain.load(train,model);fail("edited parameters trusted");}catch(IllegalArgumentException expected){}
    }
    @Test public void unknownValidationUsesPairedWorstCaseDeltaAndCanBlockAnApparentlyHelpfulFit()throws Exception {
        Path train=bundle("train",250,fixtures.history(true)),model=fit(train);
        var later=fixtures.appendEpisode(fixtures.history(true),400);
        later.remove(later.size()-1); // Keep the new episode open before its horizon.
        Path valid=bundle("valid",590,later);
        var result=ExecutionChallengerMain.evaluate(train,model,valid);
        assertEquals(1,result.unknown);assertEquals(0,result.known);
        assertTrue(result.pairedBrierChangeLower<0);assertTrue(result.pairedBrierChangeUpper>0);
        assertEquals("HOLD_FOR_VALIDATION",result.promotionStatus);
    }
}
