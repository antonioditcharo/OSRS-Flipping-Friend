package com.flippingfriend.companion;

import com.flippingfriend.core.*;
import com.google.gson.Gson;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;

/** Explicit offline command. Never starts the service, writes a database, trains or promotes. */
public final class ExecutionEvaluationMain {
    private static final Gson JSON=new Gson();
    private static final long MAX_FILE_BYTES=256L*1024*1024;
    private ExecutionEvaluationMain(){ }
    public static void main(String[] args)throws Exception {
        if(args.length==5 && "freeze".equals(args[0])) {
            Path output=Path.of(args[3]);freeze(Path.of(args[1]),args[2],output,args[4]);
            var verified=verify(output);System.out.println(summary(verified));return;
        }
        if(args.length==2 && "verify".equals(args[0])) {
            System.out.println(summary(verify(Path.of(args[1]))));return;
        }
        if(args.length==2 && "audit".equals(args[0])) {
            System.out.println(JSON.toJson(audit(Path.of(args[1]))));return;
        }
        if(args.length==3 && "compare".equals(args[0])) {
            System.out.println(JSON.toJson(compare(Path.of(args[1]),Path.of(args[2]))));return;
        }
        throw new IllegalArgumentException("Usage: freeze <scoped-db> <account-id> <new-local-data-directory> <source-revision>; verify <directory>; audit <directory>; compare <training-directory> <validation-directory>");
    }
    static ExecutionForecastAudit audit(Path directory)throws Exception {
        return new ExecutionForecastAudit(verify(directory));
    }
    static void freeze(Path database,String account,Path output,String revision)throws Exception {
        AccountIdentity.require(account);
        if(revision==null || !revision.matches("[0-9a-f]{40}"))throw new IllegalArgumentException("40-character source revision is required");
        Path real=database.toRealPath();
        if(!real.getFileName().toString().equals("flipping-friend.db") || real.getParent()==null
            || !real.getParent().getFileName().toString().equals(account) || real.getParent().getParent()==null
            || !real.getParent().getParent().getFileName().toString().equals("accounts"))
            throw new IllegalArgumentException("Only the explicitly selected scoped accounts/<account>/flipping-friend.db is allowed");
        ChunkedExecutionBundle.freeze(real,account,output,revision);
    }
    static void writeSnapshot(ExecutionSourceSnapshot snapshot,Path output,String revision)throws Exception {
        if(revision==null || !revision.matches("[0-9a-f]{40}"))throw new IllegalArgumentException("40-character source revision is required");
        Path target=output.toAbsolutePath().normalize();boolean privateOutput=false;
        for(Path part:target)if("local-data".equals(part.toString()))privateOutput=true;
        if(!privateOutput)throw new IllegalArgumentException("Evaluation output must stay under local-data");
        if(Files.exists(target,LinkOption.NOFOLLOW_LINKS))throw new FileAlreadyExistsException(target.toString());
        var evaluation=new ExecutionHorizonEvaluation(snapshot.observations(),null);
        byte[] sources=bytes(snapshot),scores=bytes(evaluation);
        if(sources.length>MAX_FILE_BYTES || scores.length>MAX_FILE_BYTES)throw new IllegalArgumentException("Evaluation exceeds the explicit 256 MiB file limit; no prefix was truncated");
        byte[] artifact=artifactBytes();
        if(artifact!=null && artifact.length>MAX_FILE_BYTES)throw new IllegalArgumentException("Evaluator artifact exceeds the explicit file limit");
        Manifest manifest=new Manifest(snapshot,revision,hash(sources),hash(scores),artifact==null?null:hash(artifact));
        Files.createDirectories(target.getParent());Files.createDirectory(target);
        // A manifest appears last; interrupted/incomplete directories are never accepted by verify.
        Files.write(target.resolve("sources.json"),sources,StandardOpenOption.CREATE_NEW);
        Files.write(target.resolve("evaluation.json"),scores,StandardOpenOption.CREATE_NEW);
        if(artifact!=null)Files.write(target.resolve("evaluator.jar.bin"),artifact,StandardOpenOption.CREATE_NEW);
        Files.write(target.resolve("manifest.json"),bytes(manifest),StandardOpenOption.CREATE_NEW);
    }
    static Verified verify(Path directory)throws Exception {
        if(ChunkedExecutionBundle.isV2(directory))return ChunkedExecutionBundle.verify(directory);
        byte[] manifestBytes=read(directory,"manifest.json"),sourceBytes=read(directory,"sources.json"),scoreBytes=read(directory,"evaluation.json");
        Manifest manifest=JSON.fromJson(new String(manifestBytes,StandardCharsets.UTF_8),Manifest.class);
        if(manifest==null || !"1".equals(manifest.schemaVersion) || !"HORIZON_BOUNDS_V1".equals(manifest.evaluatorVersion)
            || !ExecutionForecast.MODEL_VERSION.equals(manifest.baselineModelVersion)
            || manifest.sourceRevision==null || !manifest.sourceRevision.matches("[0-9a-f]{40}")
            || !("UNPACKAGED_CLASSES".equals(manifest.codeIdentity) && manifest.artifactSha256==null
                || "JAR_SHA256".equals(manifest.codeIdentity) && manifest.artifactSha256!=null && manifest.artifactSha256.matches("[0-9a-f]{64}"))
            || !hash(sourceBytes).equals(manifest.sourceSha256) || !hash(scoreBytes).equals(manifest.evaluationSha256))
            throw new IllegalArgumentException("Manifest version or file hashes do not match");
        if("JAR_SHA256".equals(manifest.codeIdentity)
            && !hash(read(directory,"evaluator.jar.bin")).equals(manifest.artifactSha256))
            throw new IllegalArgumentException("Retained evaluator artifact does not match its manifest");
        var snapshot=JSON.fromJson(new String(sourceBytes,StandardCharsets.UTF_8),ExecutionSourceSnapshot.class);
        if(snapshot==null || !Objects.equals(manifest.accountId,snapshot.accountId)
            || !Objects.equals(manifest.capturedAt,snapshot.capturedAt) || !Objects.equals(manifest.sourceCursor,snapshot.sourceCursor)
            || manifest.offerRows==null || manifest.gapRows==null || snapshot.offers==null || snapshot.gaps==null
            || manifest.offerRows!=snapshot.offers.size() || manifest.gapRows!=snapshot.gaps.size())
            throw new IllegalArgumentException("Manifest and source prefix disagree");
        var evaluation=new ExecutionHorizonEvaluation(snapshot.observations(),null);
        if(!Arrays.equals(scoreBytes,bytes(evaluation)))throw new IllegalArgumentException("Evaluation does not reproduce from the frozen sources");
        return new Verified(snapshot,evaluation,hash(manifestBytes));
    }
    static Comparison compare(Path training,Path validation)throws Exception {
        Verified train=verify(training),valid=verify(validation);
        var a=train.snapshot;var b=valid.snapshot;
        if(!a.accountId.equals(b.accountId) || b.capturedAt<=a.capturedAt || b.sourceCursor<=a.sourceCursor
            || b.offers.size()<a.offers.size() || b.gaps.size()<a.gaps.size())
            throw new IllegalArgumentException("Validation must extend the same account's frozen source prefix");
        for(int i=0;i<a.offers.size();i++)if(!JSON.toJson(a.offers.get(i)).equals(JSON.toJson(b.offers.get(i))))
            throw new IllegalArgumentException("The validation offer prefix changed");
        for(int i=0;i<a.gaps.size();i++)if(!JSON.toJson(a.gaps.get(i)).equals(JSON.toJson(b.gaps.get(i))))
            throw new IllegalArgumentException("The validation reconciliation prefix changed");
        Set<String> cohort=new HashSet<>();long afterCapture=Math.addExact(a.capturedAt,1);
        for(var row:b.offers)if(row.rowId>a.sourceCursor) {
            OfferEvent event=JSON.fromJson(row.payload,OfferEvent.class);
            if(event.getAdviceObservedAt()>afterCapture && event.getFirstSeenAt()>afterCapture)cohort.add(event.getEventId());
        }
        return new Comparison(train,valid,new ExecutionHorizonEvaluation(b.observations(),cohort));
    }
    private static String summary(Verified verified) {
        Map<String,Object> summary=new LinkedHashMap<>();summary.put("status","VERIFIED");summary.put("manifestSha256",verified.manifestHash);
        summary.put("sourceCursor",verified.snapshot.sourceCursor);summary.put("sourceRows",verified.snapshot.offers.size());
        summary.put("totalObservations",verified.evaluation.totalObservations);summary.put("eligible",verified.evaluation.eligible);
        summary.put("knownCompleted",verified.evaluation.knownCompleted);summary.put("knownIncomplete",verified.evaluation.knownIncomplete);
        summary.put("ambiguous",verified.evaluation.ambiguous);summary.put("promotionStatus",verified.evaluation.promotionStatus);
        return JSON.toJson(summary);
    }
    private static byte[] bytes(Object value){return (JSON.toJson(value)+"\n").getBytes(StandardCharsets.UTF_8);}
    static String hash(byte[] bytes)throws Exception {
        byte[] digest=MessageDigest.getInstance("SHA-256").digest(bytes);StringBuilder hex=new StringBuilder();
        for(byte value:digest)hex.append(String.format(Locale.ROOT,"%02x",value&255));return hex.toString();
    }
    private static byte[] artifactBytes()throws Exception {
        Path source=Path.of(ExecutionEvaluationMain.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        if(!Files.isRegularFile(source))return null;
        if(Files.size(source)>MAX_FILE_BYTES)throw new IllegalArgumentException("Evaluator artifact exceeds the explicit file limit");
        return Files.readAllBytes(source);
    }
    private static byte[] read(Path directory,String name)throws Exception {
        Path file=directory.resolve(name);
        if(!Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS) || Files.size(file)>MAX_FILE_BYTES)
            throw new IllegalArgumentException("Missing, linked or oversized evaluation file: "+name);
        return Files.readAllBytes(file);
    }
    private static final class Manifest {
        final String schemaVersion,evaluatorVersion,baselineModelVersion,accountId,sourceRevision,sourceSha256,evaluationSha256,codeIdentity,artifactSha256;
        final Long capturedAt,sourceCursor;final Integer offerRows,gapRows;
        Manifest(ExecutionSourceSnapshot s,String revision,String sourceHash,String scoreHash,String artifactHash) {
            schemaVersion="1";evaluatorVersion="HORIZON_BOUNDS_V1";baselineModelVersion=ExecutionForecast.MODEL_VERSION;
            accountId=s.accountId;capturedAt=s.capturedAt;sourceCursor=s.sourceCursor;offerRows=s.offers.size();gapRows=s.gaps.size();
            sourceRevision=revision;sourceSha256=sourceHash;evaluationSha256=scoreHash;
            artifactSha256=artifactHash;codeIdentity=artifactHash==null?"UNPACKAGED_CLASSES":"JAR_SHA256";
        }
    }
    static final class Verified {
        final ExecutionSourceSnapshot snapshot;final ExecutionHorizonEvaluation evaluation;final String manifestHash;
        Verified(ExecutionSourceSnapshot s,ExecutionHorizonEvaluation e,String hash){snapshot=s;evaluation=e;manifestHash=hash;}
    }
    static final class Comparison {
        final String schemaVersion,status,trainingManifestSha256,validationManifestSha256,promotionStatus;
        final ExecutionHorizonEvaluation training,validation;
        Comparison(Verified a,Verified b,ExecutionHorizonEvaluation evaluation) {
            schemaVersion="1";status="RECORDED_PREFIX_COMPARISON";trainingManifestSha256=a.manifestHash;validationManifestSha256=b.manifestHash;
            promotionStatus="HOLD_FOR_VALIDATION";training=a.evaluation;validation=evaluation;
        }
    }
}
