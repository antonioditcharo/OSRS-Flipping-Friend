package com.flippingfriend.companion;

import com.flippingfriend.core.*;
import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

/** Complete freeze -> audit -> recomputation, using private disposable JSON bundles only. */
public class TradeEvidenceAuditBundleTest {
    @Rule public TemporaryFolder folder=new TemporaryFolder();
    final TradeEvidenceAuditTest fixture=new TradeEvidenceAuditTest();final Gson json=new Gson();
    Path capture(Path root,String name,String id)throws Exception {
        var source=fixture.source(List.of(fixture.event(id,"BUYING",0,0,1)),List.of());
        Path target=root.resolve(name);ExecutionEvaluationMain.writeSnapshot(source,target,"a".repeat(40));return target;
    }
    @Test public void reportReproducesWithoutChangingTheFrozenCapture()throws Exception {
        Path root=folder.newFolder("local-data").toPath(),capture=capture(root,"capture","open"),report=root.resolve("audit");
        byte[] before=Files.readAllBytes(capture.resolve("manifest.json"));
        var created=TradeEvidenceAuditMain.create(capture,report);
        var verified=TradeEvidenceAuditMain.verify(capture,report);
        assertEquals(created.sourceManifestSha256,verified.sourceManifestSha256);
        assertFalse(verified.authorizesAccounting);assertFalse(verified.authorizesTrading);
        assertArrayEquals(before,Files.readAllBytes(capture.resolve("manifest.json")));
        assertEquals(1,verified.identities.size());assertTrue(Files.isRegularFile(report.resolve("manifest.json")));
    }
    @Test public void rehashingAnEditedAccountingClaimCannotBypassRecomputation()throws Exception {
        Path root=folder.newFolder("local-data").toPath(),capture=capture(root,"capture","open"),report=root.resolve("audit");
        TradeEvidenceAuditMain.create(capture,report);
        var body=json.fromJson(Files.readString(report.resolve("audit.json")),JsonObject.class);
        body.addProperty("authorizesAccounting",true);byte[] edited=(json.toJson(body)+"\n").getBytes(java.nio.charset.StandardCharsets.UTF_8);
        Files.write(report.resolve("audit.json"),edited);
        var manifest=json.fromJson(Files.readString(report.resolve("manifest.json")),JsonObject.class);
        manifest.addProperty("auditSha256",ExecutionEvaluationMain.hash(edited));
        Files.writeString(report.resolve("manifest.json"),json.toJson(manifest));
        try{TradeEvidenceAuditMain.verify(capture,report);fail("recomputed observation report must reject invented authorization");}
        catch(IllegalArgumentException expected){}
    }
    @Test public void foreignOrChangedFrozenPrefixesCannotBorrowAnAudit()throws Exception {
        Path root=folder.newFolder("local-data").toPath(),capture=capture(root,"capture","open"),report=root.resolve("audit");
        TradeEvidenceAuditMain.create(capture,report);
        Path changed=capture(root,"changed","different-open");
        try{TradeEvidenceAuditMain.verify(changed,report);fail("different recorded prefix");}catch(IllegalArgumentException expected){}
        Path foreign=root.resolve("foreign");
        ExecutionEvaluationMain.writeSnapshot(new ExecutionSourceSnapshot(AccountIdentity.fromProfileKey("other"),
            fixture.at+100,List.of(),List.of()),foreign,"a".repeat(40));
        try{TradeEvidenceAuditMain.verify(foreign,report);fail("foreign account");}catch(IllegalArgumentException expected){}
    }
    @Test public void outputsStayPrivateAndNeverOverwriteExistingFiles()throws Exception {
        Path root=folder.newFolder("local-data").toPath(),capture=capture(root,"capture","open");
        Path outside=folder.getRoot().toPath().resolve("public-output");
        try{TradeEvidenceAuditMain.create(capture,outside);fail("outside private output");}catch(IllegalArgumentException expected){}
        assertFalse(Files.exists(outside));Path existing=root.resolve("existing");Files.createDirectory(existing);
        Files.writeString(existing.resolve("keep.txt"),"keep");
        try{TradeEvidenceAuditMain.create(capture,existing);fail("overwrite");}catch(FileAlreadyExistsException expected){}
        assertEquals("keep",Files.readString(existing.resolve("keep.txt")));
    }
    @Test public void brokenParentOrIncompleteAuditCannotProduceAVerifiedReport()throws Exception {
        Path root=folder.newFolder("local-data").toPath(),capture=capture(root,"capture","open"),report=root.resolve("audit");
        Files.writeString(capture.resolve("sources.json"),"{}");
        try{TradeEvidenceAuditMain.create(capture,report);fail("broken source hashes");}catch(IllegalArgumentException expected){}
        assertFalse(Files.exists(report));Path fresh=capture(root,"fresh","open");Files.createDirectory(report);
        Files.writeString(report.resolve("audit.json"),"{}");
        try{TradeEvidenceAuditMain.verify(fresh,report);fail("missing final manifest");}catch(IllegalArgumentException expected){}
    }
    @Test public void unsupportedManifestVersionsCannotMasqueradeAsTheCurrentAuditor()throws Exception {
        Path root=folder.newFolder("local-data").toPath(),capture=capture(root,"capture","open"),report=root.resolve("audit");
        TradeEvidenceAuditMain.create(capture,report);
        var manifest=json.fromJson(Files.readString(report.resolve("manifest.json")),JsonObject.class);
        for(String field:List.of("schemaVersion","auditorVersion")) {
            var bad=manifest.deepCopy();bad.addProperty(field,"unsupported");
            Files.writeString(report.resolve("manifest.json"),json.toJson(bad));
            try{TradeEvidenceAuditMain.verify(capture,report);fail("unsupported "+field);}catch(IllegalArgumentException expected){}
        }
    }
}
