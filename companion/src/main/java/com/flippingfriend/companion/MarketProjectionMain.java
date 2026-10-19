package com.flippingfriend.companion;

import com.flippingfriend.data.Candle;
import com.flippingfriend.data.LatestPrice;
import com.flippingfriend.model.MarketPriceProjection;
import com.google.gson.Gson;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Reproducible display-projection evaluation. No database, account, service or trading writes. */
public final class MarketProjectionMain {
    private static final Gson JSON=new Gson();
    private MarketProjectionMain(){}
    static final class Input {
        String sourceUrl;int itemId,bucketSeconds;long observedAt;List<Candle> history;
        LatestPrice quote;String quoteSourceUrl;Long quoteSnapshotAt;
        void validate() {
            if(itemId<=0 || bucketSeconds!=3600 || observedAt<=0 || history==null || history.isEmpty() || history.size()>10000
                || !source(sourceUrl,"timeseries",Map.of("id",Integer.toString(itemId),"timestep","1h"))
                || quote!=null && (quoteSnapshotAt==null || !source(quoteSourceUrl,"latest",Map.of("id",Integer.toString(itemId)))))
                throw new IllegalArgumentException("Explicit frozen hourly public-market input is required");
        }
    }
    private static boolean source(String value,String endpoint,Map<String,String> expected) {
        try {
            var uri=java.net.URI.create(value);
            if(!"https".equals(uri.getScheme()) || !"prices.runescape.wiki".equals(uri.getHost()) || uri.getPort()!=-1
                || uri.getUserInfo()!=null || uri.getFragment()!=null || !("/api/v1/osrs/"+endpoint).equals(uri.getPath()))return false;
            var query=new HashMap<String,String>();
            for(String part:uri.getRawQuery().split("&")) {
                String[] pair=part.split("=",2);if(pair.length!=2 || query.put(java.net.URLDecoder.decode(pair[0],StandardCharsets.UTF_8),
                    java.net.URLDecoder.decode(pair[1],StandardCharsets.UTF_8))!=null)return false;
            }
            return query.equals(expected);
        }catch(RuntimeException invalid){return false;}
    }
    public static void main(String[] args)throws Exception {
        if(args.length==3 && "run".equals(args[0])){run(Path.of(args[1]),Path.of(args[2]));verify(Path.of(args[2]));System.out.println("Frozen public-price projection and rolling-origin evaluation reproduce; display only.");return;}
        if(args.length==2 && "verify".equals(args[0])){verify(Path.of(args[1]));System.out.println("Display projection bundle verified.");return;}
        throw new IllegalArgumentException("Usage: run <frozen-input.json> <new-local-data-directory>; verify <bundle-directory>");
    }
    static byte[] result(byte[] bytes) {
        Input input=JSON.fromJson(new String(bytes,StandardCharsets.UTF_8),Input.class);input.validate();
        var model=new MarketPriceProjection();Map<String,Object> result=new LinkedHashMap<>();
        result.put("modelVersion",MarketPriceProjection.VERSION);result.put("itemId",input.itemId);result.put("sourceUrl",input.sourceUrl);
        result.put("evidence","PUBLIC_BUCKET_PRICE_SCENARIOS; NOT EXECUTIONS, VALIDATED PROBABILITIES OR ENTRY AUTHORITY");
        var projection=model.project(input.history,input.bucketSeconds,input.observedAt,24);
        if(input.quote!=null)projection=model.anchor(projection,input.quote,input.quoteSnapshotAt,input.observedAt);
        result.put("projection",projection);
        var evaluations=new ArrayList<MarketPriceProjection.Evaluation>();
        for(int h:new int[]{1,3,8,24})evaluations.add(model.evaluate(input.history,input.bucketSeconds,input.observedAt,h));
        result.put("rollingOriginEvaluations",evaluations);return AccountReplayMain.bytes(result);
    }
    static void run(Path source,Path target)throws Exception {
        byte[] input=AccountReplayMain.read(source),output=result(input);Path out=AccountReplayMain.privateNewDirectory(target);Files.createDirectory(out);
        Files.write(out.resolve("input.json"),input,StandardOpenOption.CREATE_NEW);Files.write(out.resolve("results.json"),output,StandardOpenOption.CREATE_NEW);
        Path code=Path.of(MarketProjectionMain.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        byte[] artifact=Files.isRegularFile(code)?AccountReplayMain.read(code):null;Map<String,Object> manifest=new LinkedHashMap<>();
        manifest.put("schemaVersion","1");manifest.put("modelVersion",MarketPriceProjection.VERSION);
        manifest.put("codeIdentity",artifact==null?"UNPACKAGED_CLASSES":"JAR_SHA256");
        manifest.put("inputSha256",ExecutionEvaluationMain.hash(input));manifest.put("resultsSha256",ExecutionEvaluationMain.hash(output));
        manifest.put("artifactSha256",artifact==null?null:ExecutionEvaluationMain.hash(artifact));
        if(artifact!=null)Files.write(out.resolve("evaluator.jar.bin"),artifact,StandardOpenOption.CREATE_NEW);
        Files.write(out.resolve("manifest.json"),AccountReplayMain.bytes(manifest),StandardOpenOption.CREATE_NEW);
    }
    static void verify(Path directory)throws Exception {
        var manifest=JSON.fromJson(new String(AccountReplayMain.read(directory.resolve("manifest.json")),StandardCharsets.UTF_8),com.google.gson.JsonObject.class);
        byte[] input=AccountReplayMain.read(directory.resolve("input.json")),result=AccountReplayMain.read(directory.resolve("results.json"));
        if(manifest==null || !manifest.has("schemaVersion") || !manifest.has("modelVersion") || !manifest.has("codeIdentity")
            || !manifest.has("inputSha256") || !manifest.has("resultsSha256")
            || !"1".equals(manifest.get("schemaVersion").getAsString()) || !MarketPriceProjection.VERSION.equals(manifest.get("modelVersion").getAsString())
            || !ExecutionEvaluationMain.hash(input).equals(manifest.get("inputSha256").getAsString())
            || !ExecutionEvaluationMain.hash(result).equals(manifest.get("resultsSha256").getAsString()) || !Arrays.equals(result,result(input)))
            throw new IllegalArgumentException("Projection input, result or method changed");
        String identity=manifest.get("codeIdentity").getAsString();
        boolean hasArtifact=manifest.has("artifactSha256") && !manifest.get("artifactSha256").isJsonNull();
        if(!("UNPACKAGED_CLASSES".equals(identity) && !hasArtifact || "JAR_SHA256".equals(identity) && hasArtifact))
            throw new IllegalArgumentException("Projection code identity is invalid");
        if(hasArtifact && !ExecutionEvaluationMain.hash(AccountReplayMain.read(directory.resolve("evaluator.jar.bin")))
            .equals(manifest.get("artifactSha256").getAsString()))throw new IllegalArgumentException("Retained projection evaluator mismatch");
    }
}
