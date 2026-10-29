package com.flippingfriend.companion;

import com.flippingfriend.core.*;
import com.google.gson.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class ChunkedExecutionBundleTest {
    @Rule public TemporaryFolder folder=new TemporaryFolder();
    final ExecutionEvaluationBundleTest fixture=new ExecutionEvaluationBundleTest();final Gson json=new Gson();
    Path target(String name){return folder.getRoot().toPath().resolve("local-data/"+name);}
    ExecutionSourceSnapshot source(){return fixture.snapshot(250,fixture.history(true));}
    Path write(String name,ExecutionSourceSnapshot source)throws Exception {
        Path path=target(name);ChunkedExecutionBundle.write(source,path,ExecutionEvaluationBundleTest.REVISION,ChunkedExecutionBundle.BudgetLimits.defaults());return path;
    }
    void refused(Path path)throws Exception {
        try{ExecutionEvaluationMain.verify(path);fail("Accepted invalid bundle");}catch(IllegalArgumentException | IllegalStateException expected){ }
    }
    JsonObject manifest(Path p)throws Exception{return json.fromJson(Files.readString(p.resolve("manifest.json"),StandardCharsets.UTF_8),JsonObject.class);}
    void manifest(Path p,JsonObject m)throws Exception{Files.writeString(p.resolve("manifest.json"),json.toJson(m),StandardCharsets.UTF_8);}
    @Test public void versionsHaveIdenticalSourcesObservationsCensoringScoresAndAudits()throws Exception {
        for(boolean terminal:new boolean[]{false,true}) {
            var s=fixture.snapshot(250,fixture.history(terminal));Path v1=target("v1-"+terminal),v2=write("v2-"+terminal,s);
            ExecutionEvaluationMain.writeSnapshot(s,v1,ExecutionEvaluationBundleTest.REVISION);
            var a=ExecutionEvaluationMain.verify(v1);var b=ExecutionEvaluationMain.verify(v2);
            assertEquals(json.toJson(a.snapshot.offers),json.toJson(b.snapshot.offers));
            assertEquals(json.toJson(a.snapshot.observations()),json.toJson(b.snapshot.observations()));
            assertEquals(json.toJson(a.evaluation),json.toJson(b.evaluation));
            assertEquals(json.toJson(new ExecutionForecastAudit(1,1,"same",a.snapshot.observations())),
                json.toJson(new ExecutionForecastAudit(1,1,"same",b.snapshot.observations())));
            assertEquals(json.toJson(TradeEvidenceAudit.analyze(a.snapshot,"a".repeat(64))),
                json.toJson(TradeEvidenceAudit.analyze(b.snapshot,"a".repeat(64))));
            try(var files=Files.list(v2)){assertEquals(0,files.filter(p->p.toString().endsWith(".sqlite")).count());}
        }
    }
    @Test public void multipleEpisodesAndReconciliationMatchTheFullReader()throws Exception {
        var original=source();List<ExecutionSourceSnapshot.Gap> gaps=List.of(new ExecutionSourceSnapshot.Gap(3,ExecutionEvaluationBundleTest.AT+200,"terminal","offer",561));
        var scoped=new ExecutionSourceSnapshot(original.accountId,original.capturedAt,original.offers,gaps);
        var b=ExecutionEvaluationMain.verify(write("scoped",scoped));
        assertEquals(json.toJson(scoped.observations()),json.toJson(b.snapshot.observations()));
        assertEquals(0,b.evaluation.eligible);assertEquals(1,b.evaluation.unqualified);
    }
    @Test public void chunksRespectBothByteAndRowBoundsAndRetainOpaquePayloads()throws Exception {
        List<ExecutionSourceSnapshot.Row> rows=new ArrayList<>();
        for(int i=0;i<2100;i++) {
            var e=OfferEvent.builder("fixture",1700000000+i,"EMPTY").accountId(fixture.account)
                .eventIdentity("row-"+i,"session",null).slot(0).sequence(i+1).build();
            var raw=json.toJsonTree(e).getAsJsonObject();raw.addProperty("opaque","x".repeat(i<1000?4500:1));
            rows.add(new ExecutionSourceSnapshot.Row(i+1,e.getObservedAt(),e.getEventId(),e.getEventType(),raw.toString()));
        }
        var s=new ExecutionSourceSnapshot(fixture.account,1700005000,rows,List.of());Path p=write("bounds",s);
        var chunks=manifest(p).getAsJsonArray("offers");assertTrue(chunks.size()>=3);
        for(var raw:chunks){var c=raw.getAsJsonObject();assertTrue(c.get("rows").getAsInt()<=1024);assertTrue(c.get("bytes").getAsLong()<=4*1024*1024);}
        var b=ExecutionEvaluationMain.verify(p);assertEquals(2100,b.snapshot.offers.size());
        assertEquals(rows.get(930).payload,b.snapshot.offers.get(930).payload);
    }
    @Test public void missingCorruptDuplicateReorderedAndUnmanifestedChunksFail()throws Exception {
        for(String mode:List.of("missing","corrupt","duplicate","order","contract","extra","cursor","count")) {
            Path p=write(mode,source());var m=manifest(p);var chunks=m.getAsJsonArray("offers");var chunk=chunks.get(0).getAsJsonObject();
            String name=chunk.get("file").getAsString();
            switch(mode) {
                case "missing":Files.delete(p.resolve(name));break;
                case "corrupt":Files.writeString(p.resolve(name),"x",StandardCharsets.UTF_8,StandardOpenOption.APPEND);break;
                case "duplicate":chunks.add(chunk.deepCopy());break;
                case "order":chunk.addProperty("ordinal",2);break;
                case "contract":m.addProperty("chunkBytes",1);break;
                case "extra":Files.writeString(p.resolve("unlisted.jsonl"),"{}",StandardCharsets.UTF_8);break;
                case "cursor":chunk.addProperty("firstCursor",99);break;
                case "count":chunk.addProperty("rows",3);m.addProperty("offerRows",3);break;
                default:throw new AssertionError(mode);
            }
            manifest(p,m);refused(p);
        }
    }
    @Test public void invalidSourcesAndInterruptedOrOverBudgetWritesNeverPublishAManifest()throws Exception {
        for(String mode:List.of("foreign","duplicate","future","unknown-gap","byte-budget","disk-reserve","oversized")) {
            var s=source();List<ExecutionSourceSnapshot.Row> rows=new ArrayList<>(s.offers);List<ExecutionSourceSnapshot.Gap> gaps=List.of();
            var limits=ChunkedExecutionBundle.BudgetLimits.defaults();
            if(mode.equals("foreign") || mode.equals("oversized")) {
                var raw=json.fromJson(rows.get(1).payload,JsonObject.class);
                if(mode.equals("foreign"))raw.addProperty("accountId",AccountIdentity.fromProfileKey("foreign"));
                else raw.addProperty("opaque","x".repeat(ChunkedExecutionBundle.CHUNK_BYTES));
                var old=rows.get(1);rows.set(1,new ExecutionSourceSnapshot.Row(old.rowId,old.observedAt,old.eventId,old.eventType,raw.toString()));
            }
            if(mode.equals("duplicate")){var r=rows.get(1);rows.add(new ExecutionSourceSnapshot.Row(5,r.observedAt,r.eventId,r.eventType,r.payload));}
            if(mode.equals("unknown-gap"))gaps=List.of(new ExecutionSourceSnapshot.Gap(1,1,"missing",null,0));
            if(mode.equals("byte-budget"))limits=new ChunkedExecutionBundle.BudgetLimits(1,0);
            if(mode.equals("disk-reserve"))limits=new ChunkedExecutionBundle.BudgetLimits(ChunkedExecutionBundle.BUNDLE_BYTES,Long.MAX_VALUE-ChunkedExecutionBundle.CHUNK_BYTES);
            s=new ExecutionSourceSnapshot(s.accountId,mode.equals("future")?1:s.capturedAt,rows,gaps);Path p=target(mode);
            try{ChunkedExecutionBundle.write(s,p,ExecutionEvaluationBundleTest.REVISION,limits);fail("Accepted "+mode);}
            catch(IllegalArgumentException | java.io.IOException expected){ }
            assertFalse(mode,Files.exists(p.resolve("manifest.json")));refused(p);
        }
    }
    @Test public void omittedContractFieldsAndRoundedMetadataCannotInheritWriterDefaults()throws Exception {
        for(String key:List.of("evaluatorVersion","chunkBytes","gapRows","gaps","codeIdentity")) {
            Path p=write("missing-"+key,source());var m=manifest(p);m.remove(key);manifest(p,m);refused(p);
        }
        for(String key:List.of("offerRows","sourceCursor","chunkBytes")) {
            Path p=write("fraction-"+key,source());var m=manifest(p);
            m.addProperty(key,new java.math.BigDecimal(m.get(key).getAsString()).add(new java.math.BigDecimal("0.1")));
            manifest(p,m);refused(p);
        }
        Path p=write("overflow-count",source());var m=manifest(p);
        m.getAsJsonArray("offers").get(0).getAsJsonObject().addProperty("rows",4294967300L);
        manifest(p,m);refused(p);
    }
    @Test public void walCommitsBetweenQueriesCannotMixTheStreamedSnapshot()throws Exception {
        Path db=folder.newFolder().toPath().resolve("wal.db");
        try(var s=new SqliteStore(db)){for(var e:fixture.history(true))s.recordAndProjectOffer(e,json.toJson(e),true);}
        try(var s=new SqliteStore(db,point->{if(point.equals("EXECUTION_SOURCE_OFFERS_READ")) {
            try(var c=java.sql.DriverManager.getConnection("jdbc:sqlite:"+db);var statement=c.createStatement()) {
                statement.executeUpdate("INSERT INTO position_event(source_event_id,source_offer_identity,observed_at,effect_type,item_id,item_name,quantity,acquisition_cost,created_at) VALUES('birth','offer',1700000200,'RECONCILE',561,'fixture',0,0,1700000200)");
            }
        }})) {
            List<ExecutionSourceSnapshot.Row> rows=new ArrayList<>();List<ExecutionSourceSnapshot.Gap> gaps=new ArrayList<>();
            long at=s.streamExecutionSources(fixture.account,rows::add,gaps::add);assertTrue(gaps.isEmpty());
            assertEquals(1,ExecutionEvaluationMain.verify(write("wal",new ExecutionSourceSnapshot(fixture.account,at,rows,gaps))).evaluation.eligible);
        }
    }
    @Test public void interleavedEpisodesRetainOriginalOrderFirstTerminalAndCensoring()throws Exception {
        List<OfferEvent> events=new ArrayList<>();
        for(int episode=0;episode<48;episode++) {
            int row=0;
            for(var original:fixture.history(episode%3!=0)) {
                var raw=json.toJsonTree(original).getAsJsonObject();row++;
                raw.addProperty("eventId",original.getEventId()+"-"+episode);
                raw.addProperty("sessionId","session-"+(episode/8));raw.addProperty("slot",episode%8);
                raw.addProperty("sequence",episode*10L+row);raw.addProperty("observedAt",original.getObservedAt()+episode*1000L);
                if(original.getOfferIdentity()!=null) {
                    raw.addProperty("offerIdentity","offer-"+episode);raw.addProperty("firstSeenAt",original.getFirstSeenAt()+episode*1000L);
                    raw.addProperty("adviceObservedAt",original.getAdviceObservedAt()+episode*1000L);
                    raw.addProperty("predictionSourceEventId","birth-"+episode);
                    if(episode%7==0 && row==3)raw.addProperty("sequence",1);
                }
                events.add(json.fromJson(raw,OfferEvent.class));
            }
        }
        var s=fixture.snapshot(100000,events);var verified=ExecutionEvaluationMain.verify(write("episodes",s));
        assertEquals(48,verified.snapshot.observations().size());
        assertEquals(json.toJson(s.observations()),json.toJson(verified.snapshot.observations()));
        assertEquals(json.toJson(new ExecutionHorizonEvaluation(s.observations(),null)),json.toJson(verified.evaluation));
    }
    @Test public void streamFailureClosesItsSnapshotAndKeepsOriginalDatabaseBytes()throws Exception {
        Path db=folder.newFolder().toPath().resolve("interrupted.db");
        try(var s=new SqliteStore(db)){for(var e:fixture.history(true))s.recordAndProjectOffer(e,json.toJson(e),true);}
        byte[] before=Files.readAllBytes(db);
        try(var reader=SqliteStore.openReadOnly(db)) {
            try{reader.streamExecutionSources(fixture.account,row->{throw new java.io.IOException("deliberate fixture interruption");},gap->{});fail("ignored interruption");}
            catch(java.io.IOException expected){ }
            List<ExecutionSourceSnapshot.Row> rows=new ArrayList<>();reader.streamExecutionSources(fixture.account,rows::add,gap->{});
            assertEquals(4,rows.size());
        }
        assertArrayEquals(before,Files.readAllBytes(db));
    }
}
