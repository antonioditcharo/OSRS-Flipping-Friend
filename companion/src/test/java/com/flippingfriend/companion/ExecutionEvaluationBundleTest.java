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

public class ExecutionEvaluationBundleTest {
    @Rule public TemporaryFolder folder=new TemporaryFolder();
    final Gson gson=new Gson();static final String REVISION="0123456789012345678901234567890123456789";
    final String account=ExecutionHorizonEvaluationTest.ACCOUNT;static final long AT=ExecutionHorizonEvaluationTest.AT;
    List<OfferEvent> history(boolean terminal) {
        return ExecutionHorizonEvaluationTest.history(ExecutionHorizonEvaluationTest.forecast(true,300,.8),150,terminal?200:0,"BOUGHT",true);
    }
    ExecutionSourceSnapshot snapshot(long at,List<OfferEvent> events) {
        List<ExecutionSourceSnapshot.Row> rows=new ArrayList<>();long cursor=0;
        for(var event:events)rows.add(new ExecutionSourceSnapshot.Row(++cursor,event.getObservedAt(),event.getEventId(),event.getEventType(),gson.toJson(event)));
        return new ExecutionSourceSnapshot(account,AT+at,rows,List.of());
    }
    Path bundle(String name,ExecutionSourceSnapshot snapshot)throws Exception {
        Path target=folder.getRoot().toPath().resolve("local-data").resolve(name);
        ExecutionEvaluationMain.writeSnapshot(snapshot,target,REVISION);return target;
    }
    List<OfferEvent> appendEpisode(List<OfferEvent> prefix,long shift) {
        List<OfferEvent> all=new ArrayList<>(prefix);long sequence=prefix.size();
        for(var event:history(true)) {
            var raw=gson.toJsonTree(event).getAsJsonObject();raw.addProperty("eventId",event.getEventId()+"-new");
            raw.addProperty("sessionId","session-new");raw.addProperty("sequence",++sequence);
            raw.addProperty("observedAt",event.getObservedAt()+shift);
            if(event.getOfferIdentity()!=null) {
                raw.addProperty("offerIdentity","offer-new");raw.addProperty("firstSeenAt",event.getFirstSeenAt()+shift);
                raw.addProperty("adviceObservedAt",event.getAdviceObservedAt()+shift);raw.addProperty("predictionSourceEventId","birth-new");
            }
            all.add(gson.fromJson(raw,OfferEvent.class));
        }
        return all;
    }
    @Test public void aFrozenBundleReproducesWithoutADatabaseAndCannotBeOverwritten()throws Exception {
        var source=snapshot(250,history(true));Path target=bundle("train",source);
        var verified=ExecutionEvaluationMain.verify(target);
        assertEquals(4,verified.snapshot.offers.size());assertEquals(1,verified.evaluation.knownCompleted);
        byte[] before=Files.readAllBytes(target.resolve("manifest.json"));
        try{ExecutionEvaluationMain.writeSnapshot(source,target,REVISION);fail("overwrote bundle");}
        catch(FileAlreadyExistsException expected){ }
        assertArrayEquals(before,Files.readAllBytes(target.resolve("manifest.json")));
        ExecutionEvaluationMain.main(new String[]{"verify",target.toString()});
    }
    @Test public void modifiedOrIncompleteFilesCannotSupplyAnEvaluation()throws Exception {
        for(String name:List.of("manifest.json","sources.json","evaluation.json")) {
            Path target=bundle("tamper-"+name,snapshot(250,history(true)));
            Files.writeString(target.resolve(name)," ",StandardCharsets.UTF_8,StandardOpenOption.APPEND);
            if(name.equals("manifest.json")) {
                // A harmless whitespace change changes manifest identity but not its claims.
                assertNotNull(ExecutionEvaluationMain.verify(target));
                Files.writeString(target.resolve(name),"{}",StandardCharsets.UTF_8);
            }
            try{ExecutionEvaluationMain.verify(target);fail("accepted changed "+name);}
            catch(IllegalArgumentException expected){ }
        }
        Path incomplete=folder.newFolder().toPath();
        try{ExecutionEvaluationMain.verify(incomplete);fail("accepted incomplete bundle");}
        catch(IllegalArgumentException expected){ }
    }
    @Test public void chronologicalComparisonExcludesOldOffersThatSettleInValidation()throws Exception {
        var first=history(false);Path train=bundle("train",snapshot(250,first));
        List<OfferEvent> later=new ArrayList<>(first);
        later.add(ExecutionHorizonEvaluationTest.event("terminal",4,400,"BOUGHT",10,ExecutionHorizonEvaluationTest.forecast(true,300,.8)));
        later=appendEpisode(later,400);Path validation=bundle("validation",snapshot(700,later));
        var comparison=ExecutionEvaluationMain.compare(train,validation);
        assertEquals(1,comparison.training.ambiguous);assertEquals(1,comparison.validation.eligible);
        assertEquals(1,comparison.validation.cohortExcluded);assertEquals("birth-new",comparison.validation.scores.get(0).sourceEventId);
        assertEquals("HOLD_FOR_VALIDATION",comparison.promotionStatus);
    }
    @Test public void laterArrivalOfOldAdviceDoesNotBecomeAProspectiveValidationForecast()throws Exception {
        var first=history(true);Path train=bundle("train",snapshot(250,first));
        Path validation=bundle("validation",snapshot(700,appendEpisode(first,0)));
        assertEquals(0,ExecutionEvaluationMain.compare(train,validation).validation.eligible);
    }
    @Test public void changedPrefixesAndReversedOrSameCapturesCannotBeCompared()throws Exception {
        var first=history(true);Path train=bundle("train",snapshot(250,first));
        Path same=bundle("same",snapshot(250,appendEpisode(first,0)));
        try{ExecutionEvaluationMain.compare(train,same);fail("accepted same-second captures");}catch(IllegalArgumentException expected){ }
        var changed=appendEpisode(first,400);var raw=gson.toJsonTree(changed.get(1)).getAsJsonObject();raw.addProperty("itemName","changed");
        changed.set(1,gson.fromJson(raw,OfferEvent.class));Path other=bundle("changed",snapshot(700,changed));
        try{ExecutionEvaluationMain.compare(train,other);fail("accepted changed source prefix");}catch(IllegalArgumentException expected){ }
        Path valid=bundle("valid",snapshot(700,appendEpisode(first,400)));
        try{ExecutionEvaluationMain.compare(valid,train);fail("accepted reversed captures");}catch(IllegalArgumentException expected){ }
    }
    @Test public void foreignSourcesAndFutureEvidenceFailBeforeWritingAnArtifact()throws Exception {
        for(boolean foreign:new boolean[]{false,true}) {
            var events=history(true);
            if(foreign)events.set(1,events.get(1).withAccountId(AccountIdentity.fromProfileKey("other")));
            Path target=folder.getRoot().toPath().resolve("local-data").resolve(foreign?"foreign":"future");
            try{ExecutionEvaluationMain.writeSnapshot(snapshot(foreign?250:99,events),target,REVISION);fail("invalid snapshot exported");}
            catch(IllegalArgumentException expected){ }
            assertFalse(Files.exists(target));
        }
    }
    @Test public void freezeUsesAnExistingScopedDatabaseReadOnlyAndRejectsTheUnassignedLegacyPath()throws Exception {
        Path root=folder.newFolder().toPath();Path database=root.resolve("accounts").resolve(account).resolve("flipping-friend.db");
        try(var store=new SqliteStore(database)) {
            for(var event:history(true))store.recordAndProjectOffer(event,gson.toJson(event),true);
        }
        byte[] before=Files.readAllBytes(database);Path target=root.resolve("local-data/frozen");
        ExecutionEvaluationMain.freeze(database,account,target,REVISION);
        assertArrayEquals(before,Files.readAllBytes(database));assertEquals(1,ExecutionEvaluationMain.verify(target).evaluation.eligible);
        Path legacy=root.resolve("companion/flipping-friend.db");Files.createDirectories(legacy.getParent());Files.write(legacy,new byte[]{1,2,3});
        try{ExecutionEvaluationMain.freeze(legacy,account,root.resolve("local-data/no"),REVISION);fail("unassigned legacy database opened");}
        catch(IllegalArgumentException expected){ }
        assertArrayEquals(new byte[]{1,2,3},Files.readAllBytes(legacy));
    }
    @Test public void unknownReconciliationScopeCannotBeExportedAsCleanEvidence()throws Exception {
        var first=snapshot(250,history(true));
        var unknown=new ExecutionSourceSnapshot(account,AT+250,first.offers,List.of(new ExecutionSourceSnapshot.Gap(1,AT+200,"unknown",null)));
        Path target=folder.getRoot().toPath().resolve("local-data/unknown");
        try{ExecutionEvaluationMain.writeSnapshot(unknown,target,REVISION);fail("unknown gap exported");}
        catch(IllegalArgumentException expected){ }
        assertFalse(Files.exists(target));
    }
    @Test public void aNamedGapWithoutAnyRecordedOfferCannotBeSilentlyIgnored()throws Exception {
        var source=snapshot(250,history(true));
        var unknown=new ExecutionSourceSnapshot(account,AT+250,source.offers,
            List.of(new ExecutionSourceSnapshot.Gap(1,AT+200,"missing-source","never-observed-offer")));
        Path target=folder.getRoot().toPath().resolve("local-data/unassigned");
        try{ExecutionEvaluationMain.writeSnapshot(unknown,target,REVISION);fail("unassigned named gap was ignored");}
        catch(IllegalArgumentException expected){ }
        assertFalse(Files.exists(target));
    }
    @Test public void concurrentWalCommitCannotMixOfferAndReconciliationSnapshots()throws Exception {
        Path database=folder.newFolder().toPath().resolve("coherent.db");
        try(var store=new SqliteStore(database)) {
            for(var event:history(true))store.recordAndProjectOffer(event,gson.toJson(event),true);
        }
        var once=new java.util.concurrent.atomic.AtomicBoolean();
        try(var reader=new SqliteStore(database,point->{
            if(point.equals("EXECUTION_SOURCE_OFFERS_READ") && once.compareAndSet(false,true)) {
                try(var writer=java.sql.DriverManager.getConnection("jdbc:sqlite:"+database.toAbsolutePath());var s=writer.createStatement()) {
                    s.executeUpdate("INSERT INTO position_event(source_event_id,source_offer_identity,observed_at,effect_type,item_id,item_name,quantity,acquisition_cost,created_at) VALUES('birth','offer',1700000200,'RECONCILE',561,'Fixture',0,0,1700000200)");
                }
            }
        })) {
            var first=reader.freezeExecutionSources(account);assertEquals(0,first.gaps.size());
            assertEquals(1,new ExecutionHorizonEvaluation(first.observations(),null).eligible);
            var second=reader.freezeExecutionSources(account);assertEquals(1,second.gaps.size());
            assertEquals(0,new ExecutionHorizonEvaluation(second.observations(),null).eligible);
        }
    }
}
