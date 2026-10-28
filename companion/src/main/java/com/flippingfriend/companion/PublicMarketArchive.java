package com.flippingfriend.companion;

import com.google.gson.*;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.function.LongSupplier;
import java.util.zip.*;

/** Public evidence only. Seals are immutable; a crash never completes an interrupted segment. */
final class PublicMarketArchive implements AutoCloseable {
    static final String ZERO="0".repeat(64);
    static final int MAX_PAYLOAD=16*1024*1024, RESERVE=256*1024*1024;
    static final Gson JSON=new Gson();
    final Path root;
    final JsonObject manifest;
    private final long budget;
    private final LongSupplier free;
    private final FileChannel lockChannel;
    private final FileLock lock;
    private long used,lastAt;
    private int sequence;
    private String previousSeal=ZERO,head=ZERO;
    private Path segment;
    private int count;
    private long firstAt;

    PublicMarketArchive(Path path,long now,long durationSeconds,long budget,LongSupplier free)throws IOException {
        if(now<=0 || durationSeconds<1 || durationSeconds>31*86400L || budget<1024*1024 || budget>2L*1024*1024*1024)
            throw new IllegalArgumentException("Bounded public archive required");
        root=path.toAbsolutePath().normalize();this.budget=budget;this.free=free;
        for(Path p=root;p!=null;p=p.getParent())if(Files.isSymbolicLink(p))throw new IOException("Linked public archive");
        Files.createDirectories(root);
        if(Files.isSymbolicLink(root.resolve("writer.lock")))throw new IOException("Linked public writer lock");
        lockChannel=FileChannel.open(root.resolve("writer.lock"),StandardOpenOption.CREATE,StandardOpenOption.WRITE);
        FileLock acquired=null;
        try {
            acquired=lockChannel.tryLock();if(acquired==null)throw new IOException("Public archive already owned");
            lock=acquired;
            try(var paths=Files.walk(root)) {
                var iterator=paths.iterator();int files=0;
                while(iterator.hasNext()) {
                    Path p=iterator.next();if(++files>150000 || Files.isSymbolicLink(p))throw new IOException("Invalid public archive tree");
                    if(Files.isRegularFile(p))used=Math.addExact(used,Files.size(p));
                }
            }
            if(Files.exists(root.resolve("manifest.json"))) {
                manifest=read(root.resolve("manifest.json"),65536);
                if(integer(manifest,"schemaVersion")!=3 || !"PUBLIC_MARKET_NOT_EXECUTIONS".equals(manifest.get("evidence").getAsString())
                    || !"RECORDED_PUBLIC_AVAILABILITY".equals(manifest.get("availabilityEvidence").getAsString())
                    || integer(manifest,"segmentSeconds")!=300 || integer(manifest,"budgetBytes")!=budget || integer(manifest,"intervalSeconds")!=60
                    || integer(manifest,"durationSeconds")!=durationSeconds || integer(manifest,"startedAtNs")>now
                    || integer(manifest,"stopAtNs")!=Math.addExact(integer(manifest,"startedAtNs"),Math.multiplyExact(durationSeconds,1000000000L)))
                    throw new IOException("Incompatible public archive; retain it and choose a new directory");
            } else {
                try(var paths=Files.list(root)) {if(paths.anyMatch(p->!p.getFileName().toString().equals("writer.lock")))throw new IOException("Unassigned archive contents");}
                manifest=new JsonObject();manifest.addProperty("schemaVersion",3);manifest.addProperty("evidence","PUBLIC_MARKET_NOT_EXECUTIONS");
                manifest.addProperty("availabilityEvidence","RECORDED_PUBLIC_AVAILABILITY");manifest.addProperty("startedAtNs",now);
                manifest.addProperty("stopAtNs",Math.addExact(now,Math.multiplyExact(durationSeconds,1000000000L)));
                manifest.addProperty("durationSeconds",durationSeconds);manifest.addProperty("intervalSeconds",60);
                manifest.addProperty("segmentSeconds",300);manifest.addProperty("budgetBytes",budget);write(root.resolve("manifest.json"),bytes(manifest));
            }
            Files.createDirectories(root.resolve("blobs"));Files.createDirectories(root.resolve("segments"));
            var seals=new TreeMap<Integer,Path>();
            try(var dirs=Files.list(root.resolve("segments"))) {
                for(Path dir:(Iterable<Path>)dirs::iterator) {
                    if(!dir.getFileName().toString().matches("[0-9]{8}-[0-9a-f-]{36}") || !Files.isDirectory(dir))throw new IOException("Invalid segment directory");
                    if(Files.exists(dir.resolve("seal.json"))) {
                        JsonObject s=read(dir.resolve("seal.json"),65536);int index=Math.toIntExact(integer(s,"sequence"));
                        if(seals.put(index,dir)!=null)throw new IOException("Conflicting public seals");
                    }
                }
            }
            for(var entry:seals.entrySet()) {
                Path dir=entry.getValue();JsonObject seal=read(dir.resolve("seal.json"),65536);
                if(entry.getKey()!=sequence || !previousSeal.equals(seal.get("previousSeal").getAsString()) || !seal.get("complete").getAsBoolean())
                    throw new IOException("Broken public seal chain");
                String prior=ZERO;long first=0;int n=Math.toIntExact(integer(seal,"recordCount"));
                if(n<1 || n>16)throw new IOException("Invalid public segment size");
                for(int i=0;i<n;i++) {
                    byte[] raw=readBytes(dir.resolve(String.format(Locale.ROOT,"%08d.json",i)),65536);
                    JsonObject row=JsonParser.parseString(new String(raw,StandardCharsets.UTF_8)).getAsJsonObject();
                    long at=at(row);
                    if(integer(row,"index")!=i || !prior.equals(row.get("previous").getAsString()) || at<=lastAt
                        || integer(row,"requestAtNs")<=0 || integer(row,"requestAtNs")>=at)throw new IOException("Broken public record chain");
                    if(i==0)first=at;lastAt=at;prior=hash(raw);
                }
                if(!prior.equals(seal.get("head").getAsString()) || first!=integer(seal,"firstAtNs") || lastAt!=integer(seal,"lastAtNs"))throw new IOException("Public seal changed");
                previousSeal=hash(readBytes(dir.resolve("seal.json"),65536));sequence++;
            }
            checkSpace(0);
        } catch(Exception failure) {
            if(acquired!=null)acquired.close();lockChannel.close();
            if(failure instanceof IOException)throw (IOException)failure;
            throw new IOException("Public archive refused",failure);
        }
    }
    boolean expired(long now){return now>=integer(manifest,"stopAtNs");}
    long lastAt(){return lastAt;}
    private void checkSpace(long bytes)throws IOException {
        if(bytes<0 || bytes>budget-65536 || used>budget-65536-bytes || free.getAsLong()<RESERVE+bytes)throw new IOException("Public archive byte/disk reserve reached");
    }
    void write(Path path,byte[] raw)throws IOException {
        if(raw.length>MAX_PAYLOAD || Files.exists(path))throw new IOException("Immutable bounded public file required");
        checkSpace(raw.length);Path temp=path.resolveSibling(path.getFileName()+"."+UUID.randomUUID()+".pending");
        try {
            try(var out=FileChannel.open(temp,StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE)) {
                ByteBuffer bytes=ByteBuffer.wrap(raw);while(bytes.hasRemaining())out.write(bytes);out.force(true);
            }
            Files.move(temp,path,StandardCopyOption.ATOMIC_MOVE);used+=raw.length;
        } catch(IOException e) {throw e;} // Keep interrupted files as evidence; never rewrite originals.
    }
    String blob(JsonElement value)throws IOException {
        byte[] raw=bytes(value);if(raw.length>MAX_PAYLOAD)throw new IOException("Oversized public component");
        String key=hash(raw);Path path=root.resolve("blobs").resolve(key+".json.gz");
        if(Files.exists(path)) {
            try(var in=new GZIPInputStream(new ByteArrayInputStream(readBytes(path,MAX_PAYLOAD)))) {
                byte[] prior=in.readNBytes(MAX_PAYLOAD+1);if(!Arrays.equals(raw,prior))throw new IOException("Public blob changed");
            }
        } else {
            var packed=new ByteArrayOutputStream();try(var gzip=new GZIPOutputStream(packed)){gzip.write(raw);}write(path,packed.toByteArray());
        }
        return key;
    }
    void append(JsonObject row)throws IOException {
        long at=at(row);
        if(at<=lastAt || integer(row,"requestAtNs")<=0 || integer(row,"requestAtNs")>=at)throw new IOException("Public availability clock regressed");
        if(segment==null) {
            segment=root.resolve("segments").resolve(String.format(Locale.ROOT,"%08d",sequence)+"-"+UUID.randomUUID());
            Files.createDirectory(segment);count=0;firstAt=at;head=ZERO;
        }
        row=row.deepCopy();row.addProperty("index",count);row.addProperty("previous",head);
        byte[] raw=bytes(row);if(raw.length>65536)throw new IOException("Oversized public record");
        write(segment.resolve(String.format(Locale.ROOT,"%08d.json",count)),raw);head=hash(raw);count++;lastAt=at;
        if(at-firstAt>=300_000000000L || count>=16)seal();
    }
    void seal()throws IOException {
        if(segment==null)return;
        var seal=new JsonObject();seal.addProperty("schemaVersion",3);seal.addProperty("complete",true);
        seal.addProperty("sequence",sequence);seal.addProperty("previousSeal",previousSeal);seal.addProperty("recordCount",count);
        seal.addProperty("head",head);seal.addProperty("firstAtNs",firstAt);seal.addProperty("lastAtNs",lastAt);
        byte[] raw=bytes(seal);write(segment.resolve("seal.json"),raw);previousSeal=hash(raw);sequence++;segment=null;
    }
    static long at(JsonObject row)throws IOException {
        String kind=row.has("kind")?row.get("kind").getAsString():"";
        if(!Set.of("CAPTURE","FAILURE").contains(kind))throw new IOException("Invalid public record kind");
        return integer(row,kind.equals("CAPTURE")?"availableAtNs":"recordedAtNs");
    }
    static long integer(JsonObject o,String name){
        JsonElement v=o.get(name);if(v==null || !v.isJsonPrimitive() || !v.getAsJsonPrimitive().isNumber())throw new IllegalArgumentException("Exact integer public metadata required");
        return v.getAsBigDecimal().longValueExact();
    }
    static byte[] bytes(JsonElement value){return JSON.toJson(value).getBytes(StandardCharsets.UTF_8);}
    static String hash(byte[] bytes){
        try {
            byte[] digest=java.security.MessageDigest.getInstance("SHA-256").digest(bytes);var out=new StringBuilder(64);
            for(byte b:digest)out.append(Character.forDigit((b>>>4)&15,16)).append(Character.forDigit(b&15,16));return out.toString();
        } catch(java.security.NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}
    }
    static byte[] readBytes(Path path,int limit)throws IOException {
        if(Files.isSymbolicLink(path) || !Files.isRegularFile(path) || Files.size(path)>limit)throw new IOException("Invalid public file");
        try(var in=Files.newInputStream(path)){byte[] bytes=in.readNBytes(limit+1);if(bytes.length>limit)throw new IOException("Oversized public file");return bytes;}
    }
    static JsonObject read(Path path,int limit)throws IOException {return JsonParser.parseString(new String(readBytes(path,limit),StandardCharsets.UTF_8)).getAsJsonObject();}
    @Override public void close()throws IOException {lock.close();lockChannel.close();} // Do not seal on a crash/restart.
}
