package com.flippingfriend.companion;

import com.google.gson.Gson;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Explicit offline benchmark. Inputs and outputs never share a live account directory. */
public final class AccountReplayMain {
    private static final Gson JSON=new Gson();
    private static final long MAX_BYTES=256L*1024*1024;
    private AccountReplayMain(){}
    public static void main(String[] args)throws Exception {
        if(args.length==3 && "run".equals(args[0])) {
            Path out=Path.of(args[2]);run(Path.of(args[1]),out);System.out.println("Replay written and verified: "+out.toAbsolutePath());verify(out);return;
        }
        if(args.length==2 && "verify".equals(args[0])) {verify(Path.of(args[1]));System.out.println("Replay reproduces for 5m, 30m, 2h, 8h and all three fill scenarios.");return;}
        throw new IllegalArgumentException("Usage: run <frozen-input.json> <new-local-data-directory>; verify <bundle-directory>");
    }
    static void run(Path source,Path output)throws Exception {
        byte[] inputBytes=read(source);var input=JSON.fromJson(new String(inputBytes,StandardCharsets.UTF_8),AccountReplayInput.class);input.validate();
        Path out=privateNewDirectory(output);Files.createDirectory(out);
        Files.write(out.resolve("input.json"),inputBytes,StandardOpenOption.CREATE_NEW);
        byte[] report=bytes(benchmark(input,out.resolve("synthetic-ledgers")));
        Files.write(out.resolve("results.json"),report,StandardOpenOption.CREATE_NEW);
        Map<String,Object> manifest=new LinkedHashMap<>();manifest.put("schemaVersion","1");manifest.put("policyVersion",AccountReplay.VERSION);
        manifest.put("inputSha256",ExecutionEvaluationMain.hash(inputBytes));manifest.put("resultsSha256",ExecutionEvaluationMain.hash(report));
        manifest.put("evidence","SIMULATION; public volume-share assumptions do not establish actual fills or comparative performance");
        byte[] code=artifact();manifest.put("artifactSha256",code==null?null:ExecutionEvaluationMain.hash(code));
        if(code!=null)Files.write(out.resolve("evaluator.jar.bin"),code,StandardOpenOption.CREATE_NEW);
        Files.write(out.resolve("manifest.json"),bytes(manifest),StandardOpenOption.CREATE_NEW);
    }
    static List<AccountReplay.Result> benchmark(AccountReplayInput input,Path scratch)throws Exception {
        Files.createDirectories(scratch);List<AccountReplay.Result> results=new ArrayList<>();
        for(int cadence:new int[]{5,30,120,480})for(int share:new int[]{10,50,100})
            try(var replay=new AccountReplay(input,cadence,share,scratch.resolve("synthetic-"+cadence+"-"+share+".db"))){results.add(replay.run());}
        return results;
    }
    static void verify(Path bundle)throws Exception {
        var manifest=JSON.fromJson(new String(read(bundle.resolve("manifest.json")),StandardCharsets.UTF_8),com.google.gson.JsonObject.class);
        byte[] inputBytes=read(bundle.resolve("input.json")),report=read(bundle.resolve("results.json"));
        if(!"1".equals(manifest.get("schemaVersion").getAsString()) || !AccountReplay.VERSION.equals(manifest.get("policyVersion").getAsString())
            || !ExecutionEvaluationMain.hash(inputBytes).equals(manifest.get("inputSha256").getAsString())
            || !ExecutionEvaluationMain.hash(report).equals(manifest.get("resultsSha256").getAsString()))throw new IllegalArgumentException("Replay manifest mismatch");
        if(manifest.has("artifactSha256") && !manifest.get("artifactSha256").isJsonNull()
            && !ExecutionEvaluationMain.hash(read(bundle.resolve("evaluator.jar.bin"))).equals(manifest.get("artifactSha256").getAsString()))
            throw new IllegalArgumentException("Evaluator artifact hash mismatch");
        Path scratch=Files.createTempDirectory(privateParent(bundle),"replay-verification-");
        try {
            var input=JSON.fromJson(new String(inputBytes,StandardCharsets.UTF_8),AccountReplayInput.class);
            if(!Arrays.equals(report,bytes(benchmark(input,scratch))))throw new IllegalArgumentException("Replay does not reproduce from frozen input");
        } finally {
            // Only the exact newly-created scratch directory is removed; input/output evidence stays intact.
            Path resolved=scratch.toRealPath();
            if(!resolved.getParent().equals(privateParent(bundle).toRealPath()))throw new IllegalStateException("Verification cleanup escaped its private directory");
            try(var paths=Files.walk(resolved)){for(Path path:paths.sorted(Comparator.reverseOrder()).toArray(Path[]::new)) {
                if(!path.toAbsolutePath().normalize().startsWith(resolved))throw new IllegalStateException("Invalid cleanup target");
                Files.delete(path);
            }}
        }
    }
    static Path privateNewDirectory(Path output)throws Exception {
        Path path=output.toAbsolutePath().normalize();privateParent(path);
        if(Files.exists(path,LinkOption.NOFOLLOW_LINKS))throw new FileAlreadyExistsException(path.toString());
        Files.createDirectories(path.getParent());return path;
    }
    private static Path privateParent(Path path) {
        Path absolute=path.toAbsolutePath().normalize();
        for(Path part:absolute)if("local-data".equals(part.toString()))return absolute.getParent();
        throw new IllegalArgumentException("Evaluation output must remain under local-data");
    }
    static byte[] read(Path file)throws Exception {
        if(!Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS) || Files.size(file)>MAX_BYTES)throw new IllegalArgumentException("Missing, linked or oversized frozen file");
        return Files.readAllBytes(file);
    }
    static byte[] bytes(Object value){return (JSON.toJson(value)+"\n").getBytes(StandardCharsets.UTF_8);}
    private static byte[] artifact()throws Exception {
        Path code=Path.of(AccountReplayMain.class.getProtectionDomain().getCodeSource().getLocation().toURI());return Files.isRegularFile(code)?read(code):null;
    }
}
