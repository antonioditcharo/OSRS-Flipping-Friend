package com.flippingfriend.companion;

import com.google.gson.Gson;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Offline verified-capture audit. No database opener, service, network or financial writer. */
public final class TradeEvidenceAuditMain {
    private static final Gson JSON=new Gson();
    private static final long MAX_FILE_BYTES=256L*1024*1024;
    private TradeEvidenceAuditMain(){}
    public static void main(String[] args)throws Exception {
        if(args.length!=3 || !(args[0].equals("audit") || args[0].equals("verify")))
            throw new IllegalArgumentException("Usage: audit <verified-capture> <new-local-data-report-directory>; verify <verified-capture> <report-directory>");
        Path capture=Path.of(args[1]),report=Path.of(args[2]);
        TradeEvidenceAudit audit=args[0].equals("audit")?create(capture,report):verify(capture,report);
        Map<String,Object> summary=new LinkedHashMap<>();summary.put("status",audit.status);
        summary.put("sourceManifestSha256",audit.sourceManifestSha256);summary.put("sourceRows",audit.sourceRows);
        summary.put("identities",audit.identities.size());
        summary.put("identitiesWithFlags",audit.identities.stream().filter(row->!row.flags.isEmpty()).count());
        summary.put("unassignedProblems",audit.unassignedProblems.size());
        Map<String,Integer> flags=new TreeMap<>();for(var row:audit.identities)for(String flag:row.flags)flags.merge(flag,1,Integer::sum);
        summary.put("identityFlagCounts",flags);summary.put("authorizesAccounting",false);summary.put("authorizesTrading",false);
        summary.put("auditManifestSha256",ExecutionEvaluationMain.hash(read(report,"manifest.json")));
        System.out.println(JSON.toJson(summary));
    }
    static TradeEvidenceAudit create(Path capture,Path report)throws Exception {
        var verified=ExecutionEvaluationMain.verify(capture);
        var audit=TradeEvidenceAudit.analyze(verified.snapshot,verified.manifestHash);
        byte[] output=bytes(audit),artifact=artifactBytes();
        if(output.length>MAX_FILE_BYTES || artifact!=null && artifact.length>MAX_FILE_BYTES)
            throw new IllegalArgumentException("Audit exceeds the explicit file limit; no source rows were truncated");
        Path target=report.toAbsolutePath().normalize();boolean privateOutput=false;
        for(Path part:target)if("local-data".equals(part.toString()))privateOutput=true;
        if(!privateOutput)throw new IllegalArgumentException("Audit output must stay under local-data");
        if(Files.exists(target,LinkOption.NOFOLLOW_LINKS))throw new FileAlreadyExistsException(target.toString());
        var manifest=new Manifest(audit,ExecutionEvaluationMain.hash(output),artifact==null?null:ExecutionEvaluationMain.hash(artifact));
        Files.createDirectories(target);
        Files.write(target.resolve("audit.json"),output,StandardOpenOption.CREATE_NEW);
        if(artifact!=null)Files.write(target.resolve("auditor.jar.bin"),artifact,StandardOpenOption.CREATE_NEW);
        Files.write(target.resolve("manifest.json"),bytes(manifest),StandardOpenOption.CREATE_NEW);
        return verify(capture,target);
    }
    static TradeEvidenceAudit verify(Path capture,Path report)throws Exception {
        var parent=ExecutionEvaluationMain.verify(capture);
        var manifest=JSON.fromJson(new String(read(report,"manifest.json"),StandardCharsets.UTF_8),Manifest.class);
        byte[] original=read(report,"audit.json");
        if(manifest==null || !"1".equals(manifest.schemaVersion)
                || !TradeEvidenceAudit.VERSION.equals(manifest.auditorVersion)
                || !parent.manifestHash.equals(manifest.sourceManifestSha256)
                || !parent.snapshot.accountId.equals(manifest.accountId)
                || !Objects.equals(parent.snapshot.capturedAt,manifest.capturedAt)
                || !Objects.equals(parent.snapshot.sourceCursor,manifest.sourceCursor)
                || !Objects.equals((long)parent.snapshot.offers.size(),manifest.sourceRows)
                || !ExecutionEvaluationMain.hash(original).equals(manifest.auditSha256)
                || !("UNPACKAGED_CLASSES".equals(manifest.codeIdentity) && manifest.auditorArtifactSha256==null
                    || "JAR_SHA256".equals(manifest.codeIdentity) && manifest.auditorArtifactSha256!=null
                        && manifest.auditorArtifactSha256.matches("[0-9a-f]{64}")))
            throw new IllegalArgumentException("Audit manifest, source prefix or file hashes do not match");
        if("JAR_SHA256".equals(manifest.codeIdentity)
                && !ExecutionEvaluationMain.hash(read(report,"auditor.jar.bin")).equals(manifest.auditorArtifactSha256))
            throw new IllegalArgumentException("Retained auditor artifact does not match its manifest");
        var audit=TradeEvidenceAudit.analyze(parent.snapshot,parent.manifestHash);
        if(!Arrays.equals(original,bytes(audit)))throw new IllegalArgumentException("Audit does not reproduce from the verified sources");
        return audit;
    }
    private static byte[] bytes(Object value){return (JSON.toJson(value)+"\n").getBytes(StandardCharsets.UTF_8);}
    private static byte[] artifactBytes()throws Exception {
        Path source=Path.of(TradeEvidenceAuditMain.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        if(!Files.isRegularFile(source))return null;
        if(Files.size(source)>MAX_FILE_BYTES)throw new IllegalArgumentException("Auditor artifact exceeds the explicit file limit");
        return Files.readAllBytes(source);
    }
    private static byte[] read(Path directory,String name)throws Exception {
        Path file=directory.resolve(name);
        if(!Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS) || Files.size(file)>MAX_FILE_BYTES)
            throw new IllegalArgumentException("Missing, linked or oversized audit file: "+name);
        return Files.readAllBytes(file);
    }
    private static final class Manifest {
        final String schemaVersion,auditorVersion;
        final String sourceManifestSha256,accountId,auditSha256,codeIdentity,auditorArtifactSha256;
        final Long capturedAt,sourceCursor,sourceRows;
        Manifest(TradeEvidenceAudit audit,String hash,String artifact) {
            schemaVersion="1";auditorVersion=TradeEvidenceAudit.VERSION;
            sourceManifestSha256=audit.sourceManifestSha256;accountId=audit.accountId;
            capturedAt=audit.capturedAt;sourceCursor=audit.sourceCursor;sourceRows=audit.sourceRows;
            auditSha256=hash;auditorArtifactSha256=artifact;codeIdentity=artifact==null?"UNPACKAGED_CLASSES":"JAR_SHA256";
        }
    }
}
