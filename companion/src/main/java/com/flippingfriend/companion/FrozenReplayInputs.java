package com.flippingfriend.companion;

import com.flippingfriend.data.Candle;
import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.GZIPInputStream;

/** Offline referenced public inputs. Bounded caches never materialize all per-frame histories. */
final class FrozenReplayInputs {
    private static final Gson JSON=new Gson();
    static final String VERSION="CHUNKED_PUBLIC_REPLAY_V1";
    private static final int MAX_BLOB=2*1024*1024,MAX_EXPANDED=4*1024*1024,MAX_FRAMES=50000;
    private static final long MAX_ARCHIVE=5L*1024*1024*1024;
    static final class Ref { long availableAt;String sha256; }
    static final class Storage {String version;List<Ref> frames;}
    static final class StoredFrame {
        long availableAt,observedAt,fiveMinuteStartedAt,hourlyStartedAt;
        JsonObject latest,fiveMinute,hourly;
        Map<Integer,List<String>> shortHistory,longHistory;
        List<AccountReplayInput.Flow> flows;
    }
    private FrozenReplayInputs(){}
    static AccountReplayInput load(Path source)throws Exception {
        JsonObject object=JSON.fromJson(new String(AccountReplayMain.read(source),StandardCharsets.UTF_8),JsonObject.class);
        if(object==null)throw new IllegalArgumentException("Missing frozen public input");
        JsonElement value=object.remove("storage");
        if(value==null)return JSON.fromJson(object,AccountReplayInput.class);
        if(object.has("frames"))throw new IllegalArgumentException("Conflicting public frame formats");
        Storage storage=JSON.fromJson(value,Storage.class);
        if(storage==null || !VERSION.equals(storage.version) || storage.frames==null
            || storage.frames.isEmpty() || storage.frames.size()>MAX_FRAMES)
            throw new IllegalArgumentException("Invalid referenced public input");
        long previous=0;
        for(Ref ref:storage.frames) {
            if(ref==null || ref.availableAt<=previous || !key(ref.sha256))throw new IllegalArgumentException("Invalid public frame index");
            previous=ref.availableAt;
        }
        Path directory=source.toAbsolutePath().normalize().getParent().resolve("blobs");
        // A Windows short-name spelling is not a link. Inspect ancestors before canonicalizing;
        // comparing the lexical path with toRealPath() would reject ordinary CI/user temp roots.
        for(Path parent=directory;parent!=null;parent=parent.getParent()) {
            var attributes=Files.readAttributes(parent,java.nio.file.attribute.BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
            if(!attributes.isDirectory() || attributes.isSymbolicLink() || attributes.isOther())
                throw new IllegalArgumentException("Linked or missing public blob root");
        }
        directory=directory.toRealPath();
        var input=JSON.fromJson(object,AccountReplayInput.class);
        input.frames=new Frames(directory,List.copyOf(storage.frames));return input;
    }
    static void copy(Path source,Path output)throws Exception {
        var input=load(source);input.validate();
        if(input.frames instanceof Frames) {
            var frames=(Frames)input.frames;
            Path target=output.getParent().resolve("blobs");Files.createDirectory(target);
            Set<String> copied=new HashSet<>();long bytes=0;
            for(Ref ref:frames.refs) {
                var stored=JSON.fromJson(new String(frames.blob(ref.sha256),StandardCharsets.UTF_8),StoredFrame.class);
                List<String> keys=new ArrayList<>();keys.add(ref.sha256);
                for(var histories:List.of(stored.shortHistory,stored.longHistory))for(var chunks:histories.values())keys.addAll(chunks);
                for(String key:keys)if(copied.add(key)) {
                    frames.blob(key); // Revalidate exact contents before retaining the original compressed bytes.
                    byte[] raw=compressed(frames.root,key);bytes=Math.addExact(bytes,raw.length);
                    if(bytes>MAX_ARCHIVE)throw new IllegalArgumentException("Referenced archive exceeds budget");
                    Files.write(target.resolve(key+".json.gz"),raw,StandardOpenOption.CREATE_NEW);
                    // A concurrent file replacement cannot copy unchecked bytes into a frozen cohort.
                    expanded(raw,key);
                }
            }
        }
        Files.write(output,AccountReplayMain.read(source),StandardOpenOption.CREATE_NEW);
    }
    private static boolean key(String value){return value!=null && value.matches("[0-9a-f]{64}");}
    private static String hash(byte[] bytes) {
        try {
            var result=new StringBuilder(64);
            for(byte b:java.security.MessageDigest.getInstance("SHA-256").digest(bytes)) {
                result.append(Character.forDigit((b>>>4)&15,16));result.append(Character.forDigit(b&15,16));
            }
            return result.toString();
        }catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}
    }
    private static byte[] compressed(Path root,String key)throws IOException {
        if(!key(key))throw new IllegalArgumentException("Invalid public blob identity");
        Path path=root.resolve(key+".json.gz");
        if(!Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS) || Files.size(path)>MAX_BLOB)
            throw new IllegalArgumentException("Missing, linked or oversized public blob");
        try(InputStream in=Files.newInputStream(path)) {
            byte[] bytes=in.readNBytes(MAX_BLOB+1);
            if(bytes.length>MAX_BLOB)throw new IllegalArgumentException("Public blob exceeds budget");
            return bytes;
        }
    }
    private static byte[] expanded(byte[] packed,String key)throws IOException {
        try(var in=new GZIPInputStream(new ByteArrayInputStream(packed))) {
            byte[] raw=in.readNBytes(MAX_EXPANDED+1);
            if(raw.length>MAX_EXPANDED || !hash(raw).equals(key))
                throw new IllegalArgumentException("Expanded public blob changed or exceeds budget");
            return raw;
        }
    }
    static final class Frames extends AbstractList<AccountReplayInput.Frame> implements RandomAccess {
        final Path root;final List<Ref> refs;
        private int lastIndex=-1;
        private AccountReplayInput.Frame last;
        private final Map<String,List<Candle>> chunks=new LinkedHashMap<String,List<Candle>>(1024,.75f,true) {
            @Override protected boolean removeEldestEntry(Map.Entry<String,List<Candle>> entry){return size()>1024;}
        };
        private final Map<List<String>,List<Candle>> preparedHistory=new LinkedHashMap<List<String>,List<Candle>>(256,.75f,true) {
            @Override protected boolean removeEldestEntry(Map.Entry<List<String>,List<Candle>> entry){return size()>256;}
        };
        Frames(Path root,List<Ref> refs){this.root=root;this.refs=refs;}
        byte[] blob(String key)throws IOException{return expanded(compressed(root,key),key);}
        @Override public int size(){return refs.size();}
        @Override public AccountReplayInput.Frame get(int index) {
            Objects.checkIndex(index,size());
            if(index==lastIndex)return last;
            try {
                Ref ref=refs.get(index);
                var f=JSON.fromJson(new String(blob(ref.sha256),StandardCharsets.UTF_8),StoredFrame.class);
                if(f==null || f.availableAt!=ref.availableAt)throw new IllegalArgumentException("Public frame index disagrees");
                var result=new AccountReplayInput.Frame();result.availableAt=f.availableAt;result.observedAt=f.observedAt;
                result.fiveMinuteStartedAt=f.fiveMinuteStartedAt;result.hourlyStartedAt=f.hourlyStartedAt;
                result.latest=f.latest;result.fiveMinute=f.fiveMinute;result.hourly=f.hourly;result.flows=f.flows;
                result.shortHistory=histories(f.shortHistory);result.longHistory=histories(f.longHistory);
                lastIndex=index;last=result;return result;
            }catch(IOException e){throw new UncheckedIOException("Cannot read frozen public evidence",e);}
        }
        private Map<Integer,List<Candle>> histories(Map<Integer,List<String>> source)throws IOException {
            if(source==null || source.size()>32)throw new IllegalArgumentException("Invalid bounded public histories");
            Map<Integer,List<Candle>> result=new TreeMap<>();
            for(var entry:source.entrySet()) {
                if(entry.getValue()==null || entry.getValue().size()>13)throw new IllegalArgumentException("Invalid public history chunks");
                List<String> identity=List.copyOf(entry.getValue());
                var prepared=preparedHistory.get(identity);
                if(prepared!=null){result.put(entry.getKey(),prepared);continue;}
                List<Candle> bars=new ArrayList<>();
                for(String key:entry.getValue()) {
                    List<Candle> chunk=chunks.get(key);
                    if(chunk==null) {
                        chunk=JSON.fromJson(new String(blob(key),StandardCharsets.UTF_8),new TypeToken<List<Candle>>(){}.getType());
                        if(chunk==null || chunk.isEmpty() || chunk.size()>64 || chunk.contains(null))
                            throw new IllegalArgumentException("Invalid public history chunk");
                        List<Candle> immutable=new ArrayList<>();for(Candle bar:chunk)immutable.add(new FrozenCandle(bar));
                        chunk=List.copyOf(immutable);chunks.put(key,chunk);
                    }
                    bars.addAll(chunk);
                }
                if(bars.size()>720)throw new IllegalArgumentException("Public history exceeds bounded window");
                var frozen=List.copyOf(bars);preparedHistory.put(identity,frozen);result.put(entry.getKey(),frozen);
            }
            return Collections.unmodifiableMap(result);
        }
        int cachedChunkCount(){return chunks.size();}
        int cachedHistoryCount(){return preparedHistory.size();}
    }
    private static final class FrozenCandle extends Candle {
        FrozenCandle(Candle source){super(source.getTimestamp(),source.getAvgHighPrice(),source.getAvgLowPrice(),source.getHighPriceVolume(),source.getLowPriceVolume());}
        @Override public void setTimestamp(long timestamp){throw new UnsupportedOperationException("Frozen public history");}
    }
}
