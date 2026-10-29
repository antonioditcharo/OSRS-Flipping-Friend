package com.flippingfriend.companion;

import com.flippingfriend.core.*;
import com.google.gson.Gson;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.util.*;

/** Offline V2 envelope. Raw canonical payload strings are retained, never reserialized as events. */
final class ChunkedExecutionBundle {
    static final int CHUNK_BYTES=4*1024*1024,CHUNK_ROWS=1024,CACHE_CHUNKS=8;
    static final long BUNDLE_BYTES=5L*1024*1024*1024,FREE_RESERVE=768L*1024*1024;
    private static final Gson JSON=new Gson();
    private static final long MANIFEST_BYTES=16L*1024*1024;
    private ChunkedExecutionBundle(){ }
    static boolean isV2(Path dir)throws Exception {
        // Detection must not narrow the existing V1 256-MiB reader boundary.
        Path file=dir.resolve("manifest.json");
        if(!Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS) || Files.size(file)>256L*1024*1024)
            throw new IllegalArgumentException("Missing, linked or oversized evaluation manifest");
        try(var in=new com.google.gson.stream.JsonReader(Files.newBufferedReader(file,StandardCharsets.UTF_8))) {
            in.beginObject();
            while(in.hasNext()) {
                if("schemaVersion".equals(in.nextName()))return "2".equals(in.nextString());
                in.skipValue();
            }
            return false;
        }
    }
    static void freeze(Path db,String account,Path output,String revision)throws Exception {
        validateTarget(output,revision);AccountIdentity.require(account);
        Files.createDirectories(output.toAbsolutePath().getParent());Files.createDirectory(output);
        Budget budget=new Budget(output,BUNDLE_BYTES,FREE_RESERVE);
        Manifest manifest=new Manifest();manifest.accountId=account;manifest.sourceRevision=revision;
        try(var offers=new ChunkWriter<ExecutionSourceSnapshot.Row>(output,"offers",manifest.offers,budget);
            var gaps=new ChunkWriter<ExecutionSourceSnapshot.Gap>(output,"gaps",manifest.gaps,budget);
            var store=SqliteStore.openReadOnly(db)) {
            manifest.capturedAt=store.streamExecutionSources(account,row->offers.add(row,row.rowId),gap->gaps.add(gap,gap.rowId));
        }
        finish(output,manifest,budget);
    }
    /** Synthetic/equivalence fixture path; production exports always use the streaming transaction. */
    static void write(ExecutionSourceSnapshot source,Path output,String revision,BudgetLimits limits)throws Exception {
        validateTarget(output,revision);Files.createDirectories(output.toAbsolutePath().getParent());Files.createDirectory(output);
        Budget budget=new Budget(output,limits.bytes,limits.reserve);
        Manifest manifest=new Manifest();manifest.accountId=source.accountId;manifest.sourceRevision=revision;manifest.capturedAt=source.capturedAt;
        try(var offers=new ChunkWriter<ExecutionSourceSnapshot.Row>(output,"offers",manifest.offers,budget);
            var gaps=new ChunkWriter<ExecutionSourceSnapshot.Gap>(output,"gaps",manifest.gaps,budget)) {
            for(var row:source.offers)offers.add(row,row.rowId);
            for(var gap:source.gaps)gaps.add(gap,gap.rowId);
        }
        finish(output,manifest,budget);
    }
    static final class BudgetLimits {
        final long bytes,reserve;
        BudgetLimits(long bytes,long reserve){this.bytes=bytes;this.reserve=reserve;}
        static BudgetLimits defaults(){return new BudgetLimits(BUNDLE_BYTES,FREE_RESERVE);}
    }
    private static void finish(Path output,Manifest m,Budget budget)throws Exception {
        m.offerRows=rows(m.offers);m.gapRows=rows(m.gaps);m.sourceCursor=cursor(m.offers);m.gapCursor=cursor(m.gaps);
        var cache=chunkCache();
        var offers=new ChunkList<>(output,m.offers,ExecutionSourceSnapshot.Row.class,cache);
        var gaps=new ChunkList<>(output,m.gaps,ExecutionSourceSnapshot.Gap.class,cache);
        List<ExecutionTimingObservation> observations=validate(m,offers,gaps,output);
        var evaluation=new ExecutionHorizonEvaluation(observations,null);
        Path score=output.resolve("evaluation.json");
        writeJson(score,evaluation);budget.add(Files.size(score));m.evaluationSha256=hash(score);
        Path artifact=Path.of(ExecutionEvaluationMain.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        if(Files.isRegularFile(artifact)) {
            budget.check(Files.size(artifact));
            try(var in=Files.newInputStream(artifact);var out=Files.newOutputStream(output.resolve("evaluator.jar.bin"),StandardOpenOption.CREATE_NEW)) {in.transferTo(out);}
            budget.add(Files.size(artifact));m.artifactSha256=hash(output.resolve("evaluator.jar.bin"));m.codeIdentity="JAR_SHA256";
        }
        // Publication is last. Invalid, interrupted and over-budget directories have no manifest.
        Path pending=output.resolve("manifest.pending.json");writeJson(pending,m);budget.add(Files.size(pending));
        try(var channel=java.nio.channels.FileChannel.open(pending,StandardOpenOption.WRITE)){channel.force(true);}
        Files.move(pending,output.resolve("manifest.json"),StandardCopyOption.ATOMIC_MOVE);
    }
    static ExecutionEvaluationMain.Verified verify(Path output)throws Exception {
        Path file=regular(output,"manifest.json",MANIFEST_BYTES);String manifestHash=hash(file);Manifest m;
        try(var reader=Files.newBufferedReader(file,StandardCharsets.UTF_8)) {
            var raw=JSON.fromJson(reader,com.google.gson.JsonObject.class);requiredMetadata(raw);m=JSON.fromJson(raw,Manifest.class);
        }
        if(m==null || !"2".equals(m.schemaVersion) || !"HORIZON_BOUNDS_V1".equals(m.evaluatorVersion)
            || !ExecutionForecast.MODEL_VERSION.equals(m.baselineModelVersion)
            || m.sourceRevision==null || !m.sourceRevision.matches("[0-9a-f]{40}")
            || m.chunkBytes!=CHUNK_BYTES || m.chunkRows!=CHUNK_ROWS || m.cacheChunks!=CACHE_CHUNKS
            || m.bundleBudget!=BUNDLE_BYTES || m.freeSpaceReserve!=FREE_RESERVE
            || m.capturedAt<=0 || m.offers==null || m.gaps==null
            || m.offerRows!=rows(m.offers) || m.gapRows!=rows(m.gaps)
            || m.sourceCursor!=cursor(m.offers) || m.gapCursor!=cursor(m.gaps))
            throw new IllegalArgumentException("V2 manifest contract is invalid");
        AccountIdentity.require(m.accountId);
        long bytes=Files.size(file);Set<String> names=new HashSet<>();names.add("manifest.json");
        bytes=checkChunks(output,"offers",m.offers,names,bytes);bytes=checkChunks(output,"gaps",m.gaps,names,bytes);
        Path scores=regular(output,"evaluation.json",BUNDLE_BYTES);bytes=Math.addExact(bytes,Files.size(scores));names.add("evaluation.json");
        if(!hash(scores).equals(m.evaluationSha256))throw new IllegalArgumentException("Evaluation hash changed");
        if("JAR_SHA256".equals(m.codeIdentity) && validHash(m.artifactSha256)) {
            Path artifact=regular(output,"evaluator.jar.bin",BUNDLE_BYTES);bytes=Math.addExact(bytes,Files.size(artifact));names.add("evaluator.jar.bin");
            if(!hash(artifact).equals(m.artifactSha256))throw new IllegalArgumentException("Evaluator artifact hash changed");
        }else if(!"UNPACKAGED_CLASSES".equals(m.codeIdentity) || m.artifactSha256!=null)
            throw new IllegalArgumentException("Evaluator identity is invalid");
        if(bytes>BUNDLE_BYTES)throw new IllegalArgumentException("Bundle budget exceeded");
        try(var entries=Files.list(output)) {
            if(entries.anyMatch(p->!names.contains(p.getFileName().toString())))throw new IllegalArgumentException("Unmanifested bundle file");
        }
        var cache=chunkCache();
        var offers=new ChunkList<>(output,m.offers,ExecutionSourceSnapshot.Row.class,cache);
        var gaps=new ChunkList<>(output,m.gaps,ExecutionSourceSnapshot.Gap.class,cache);
        var observations=validate(m,offers,gaps,output.toAbsolutePath().getParent());
        var evaluation=new ExecutionHorizonEvaluation(observations,null);
        MessageDigest digest=MessageDigest.getInstance("SHA-256");
        try(var writer=new OutputStreamWriter(new DigestOutputStream(OutputStream.nullOutputStream(),digest),StandardCharsets.UTF_8)) {
            JSON.toJson(evaluation,writer);writer.write('\n');
        }
        if(!hex(digest.digest()).equals(m.evaluationSha256))throw new IllegalArgumentException("Evaluation does not reproduce from the complete prefix");
        Set<String> rechecked=new HashSet<>();checkChunks(output,"offers",m.offers,rechecked,0);checkChunks(output,"gaps",m.gaps,rechecked,0);
        if(!hash(file).equals(manifestHash) || !hash(scores).equals(m.evaluationSha256)
            || m.artifactSha256!=null && !hash(regular(output,"evaluator.jar.bin",BUNDLE_BYTES)).equals(m.artifactSha256))
            throw new IllegalArgumentException("Bundle changed during verification");
        return new ExecutionEvaluationMain.Verified(ExecutionSourceSnapshot.verifiedChunks(m.accountId,m.capturedAt,offers,gaps,m.sourceCursor,observations),evaluation,manifestHash);
    }
    private static void requiredMetadata(com.google.gson.JsonObject raw) {
        if(raw==null)throw new IllegalArgumentException("Missing manifest");
        for(String key:List.of("schemaVersion","evaluatorVersion","baselineModelVersion","accountId","sourceRevision","evaluationSha256","codeIdentity")) {
            var v=raw.get(key);
            if(v==null || !v.isJsonPrimitive() || !v.getAsJsonPrimitive().isString())throw new IllegalArgumentException("Missing or invalid manifest string: "+key);
        }
        for(String key:List.of("chunkBytes","chunkRows","cacheChunks","bundleBudget","freeSpaceReserve","capturedAt","sourceCursor","gapCursor","offerRows","gapRows")) {
            integer(raw,key,Set.of("chunkBytes","chunkRows","cacheChunks").contains(key));
        }
        for(String key:List.of("offers","gaps")) {
            if(!raw.has(key) || !raw.get(key).isJsonArray())throw new IllegalArgumentException("Missing chunk references: "+key);
            for(var row:raw.getAsJsonArray(key)) {
                if(!row.isJsonObject())throw new IllegalArgumentException("Invalid chunk reference");var c=row.getAsJsonObject();
                for(String field:List.of("ordinal","rows","bytes","firstCursor","lastCursor"))integer(c,field,field.equals("ordinal") || field.equals("rows"));
                for(String field:List.of("file","sha256"))if(!c.has(field) || !c.get(field).isJsonPrimitive() || !c.getAsJsonPrimitive(field).isString())
                    throw new IllegalArgumentException("Invalid chunk string: "+field);
            }
        }
    }
    private static void integer(com.google.gson.JsonObject raw,String key,boolean narrow) {
        var v=raw.get(key);
        if(v==null || !v.isJsonPrimitive() || !v.getAsJsonPrimitive().isNumber())throw new IllegalArgumentException("Missing manifest integer: "+key);
        try {
            var number=new java.math.BigDecimal(v.getAsString());
            if(narrow)number.intValueExact();else number.longValueExact();
        }catch(ArithmeticException | NumberFormatException invalid){throw new IllegalArgumentException("Inexact manifest integer: "+key,invalid);}
    }
    private static List<ExecutionTimingObservation> validate(Manifest m,List<ExecutionSourceSnapshot.Row> offers,
            List<ExecutionSourceSnapshot.Gap> gaps,Path temp)throws Exception {
        if(m.capturedAt<=0)throw new IllegalArgumentException("Invalid capture clock");AccountIdentity.require(m.accountId);
        try(var scratch=new OfflineScratch(temp)) {
            Set<String> ids=scratch.set("source-ids");long prior=0;
            for(var row:offers) {
                if(row==null || row.rowId<=prior)throw new IllegalArgumentException("Invalid recorded row order");
                OfferEvent e=event(row,m.accountId);
                if(!ids.add(e.getEventId()))throw new IllegalArgumentException("Duplicate canonical source identity");prior=row.rowId;
            }
            if(prior!=m.sourceCursor)throw new IllegalArgumentException("Incomplete offer prefix");
            Map<String,Integer> gapItems=scratch.map("gap-items",Integer.class);prior=0;
            for(var gap:gaps) {
                if(gap==null || gap.rowId<=prior || gap.sourceEventId==null || !ids.contains(gap.sourceEventId)
                    || gap.itemId==null || gap.itemId<0)throw new IllegalArgumentException("Invalid reconciliation source");
                Integer old=gapItems.put(gap.sourceEventId,gap.itemId);
                if(old!=null && !old.equals(gap.itemId))throw new IllegalArgumentException("Conflicting reconciliation source items");
                prior=gap.rowId;
            }
            if(prior!=m.gapCursor)throw new IllegalArgumentException("Incomplete reconciliation prefix");
            Iterable<OfferEvent> history=()->offers.stream().map(row->event(row,m.accountId)).iterator();
            Set<String> affected=scratch.set("affected-items");
            for(var scope:TradeGapEvidence.scopeDisk(history,gapItems,m.accountId,scratch)) {
                if(scope.isEmpty())throw new IllegalArgumentException("Recorded reconciliation scope is unknown");
                for(int item:scope)affected.add(Integer.toString(item));
            }
            Set<String> gapOffers=scratch.set("affected-offers");
            for(var e:history)if(e.getOfferIdentity()!=null && affected.contains(Integer.toString(e.getItemId())))gapOffers.add(e.getOfferIdentity());
            return ExecutionObservationLedger.compileDisk(m.accountId,m.capturedAt,history,gapOffers,scratch);
        }
    }
    private static OfferEvent event(ExecutionSourceSnapshot.Row row,String account) {
        if(row.payload==null)throw new IllegalArgumentException("Missing original payload");
        OfferEvent e=JSON.fromJson(row.payload,OfferEvent.class);
        if(!TradeGapEvidence.validFields(e) || !account.equals(e.getAccountId()) || !Objects.equals(row.eventId,e.getEventId())
            || row.observedAt!=e.getObservedAt() || !Objects.equals(row.eventType,e.getEventType()))
            throw new IllegalArgumentException("Foreign or inconsistent source row");
        return e;
    }
    private static long checkChunks(Path output,String kind,List<Chunk> chunks,Set<String> names,long bytes)throws Exception {
        long cursor=0;int ordinal=0;
        for(var c:chunks) {
            String expected=String.format(Locale.ROOT,"%s-%06d.jsonl",kind,++ordinal);
            if(c==null || !expected.equals(c.file) || c.ordinal!=ordinal || c.rows<=0 || c.rows>CHUNK_ROWS
                || c.bytes<=0 || c.bytes>CHUNK_BYTES || c.firstCursor<=cursor || c.lastCursor<c.firstCursor
                || !validHash(c.sha256) || !names.add(c.file))throw new IllegalArgumentException("Missing, reordered or invalid chunk");
            Path file=regular(output,c.file,CHUNK_BYTES);
            if(Files.size(file)!=c.bytes || !hash(file).equals(c.sha256))throw new IllegalArgumentException("Chunk hash or size changed");
            cursor=c.lastCursor;bytes=Math.addExact(bytes,c.bytes);
            if(bytes>BUNDLE_BYTES)throw new IllegalArgumentException("Bundle budget exceeded");
        }
        return bytes;
    }
    private static long rows(List<Chunk> chunks) {long n=0;for(var c:chunks){if(c==null)throw new IllegalArgumentException("Missing chunk");n=Math.addExact(n,c.rows);}return n;}
    private static long cursor(List<Chunk> chunks){return chunks.isEmpty()?0:chunks.get(chunks.size()-1).lastCursor;}
    private static boolean validHash(String s){return s!=null && s.matches("[0-9a-f]{64}");}
    private static Path regular(Path dir,String name,long limit)throws Exception {
        Path file=dir.resolve(name);
        if(Files.isSymbolicLink(dir) || !Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS) || Files.size(file)>limit)
            throw new IllegalArgumentException("Missing, linked or oversized bundle file: "+name);
        return file;
    }
    private static void validateTarget(Path target,String revision)throws Exception {
        if(revision==null || !revision.matches("[0-9a-f]{40}"))throw new IllegalArgumentException("Exact source revision required");
        boolean local=false;for(Path part:target.toAbsolutePath().normalize())if("local-data".equals(part.toString()))local=true;
        if(!local)throw new IllegalArgumentException("Output must stay under local-data");
        if(Files.exists(target,LinkOption.NOFOLLOW_LINKS))throw new FileAlreadyExistsException(target.toString());
    }
    private static void writeJson(Path file,Object object)throws Exception {
        try(var writer=Files.newBufferedWriter(file,StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW)) {JSON.toJson(object,writer);writer.write('\n');}
    }
    static String hash(Path file)throws Exception {
        MessageDigest digest=MessageDigest.getInstance("SHA-256");
        try(var in=new DigestInputStream(Files.newInputStream(file),digest)){in.transferTo(OutputStream.nullOutputStream());}
        return hex(digest.digest());
    }
    private static String hex(byte[] bytes){StringBuilder b=new StringBuilder();for(byte v:bytes)b.append(String.format(Locale.ROOT,"%02x",v&255));return b.toString();}
    private static final class Budget {
        final Path dir;final long limit,reserve;long used;
        Budget(Path dir,long limit,long reserve){this.dir=dir;this.limit=limit;this.reserve=reserve;}
        void check(long n)throws Exception {
            if(n<0 || used>limit-n || Files.getFileStore(dir).getUsableSpace()<Math.addExact(reserve,n))
                throw new IOException("Bundle budget or free-space reserve exceeded; no manifest published");
        }
        void add(long n)throws Exception{check(n);used=Math.addExact(used,n);}
    }
    private static final class Manifest {
        String schemaVersion="2",evaluatorVersion="HORIZON_BOUNDS_V1",baselineModelVersion=ExecutionForecast.MODEL_VERSION;
        String accountId,sourceRevision,evaluationSha256,artifactSha256,codeIdentity="UNPACKAGED_CLASSES";
        int chunkBytes=CHUNK_BYTES,chunkRows=CHUNK_ROWS,cacheChunks=CACHE_CHUNKS;
        long bundleBudget=BUNDLE_BYTES,freeSpaceReserve=FREE_RESERVE;
        long capturedAt,sourceCursor,gapCursor,offerRows,gapRows;
        final List<Chunk> offers=new ArrayList<>(),gaps=new ArrayList<>();
    }
    private static final class Chunk {
        final String file,sha256;final int ordinal,rows;final long bytes,firstCursor,lastCursor;
        Chunk(String file,int ordinal,int rows,long bytes,long first,long last,String hash) {
            this.file=file;this.ordinal=ordinal;this.rows=rows;this.bytes=bytes;firstCursor=first;lastCursor=last;sha256=hash;
        }
    }
    private static final class ChunkWriter<T> implements AutoCloseable {
        final Path output;final String kind;final List<Chunk> chunks;final Budget budget;
        OutputStream stream;Path path;int count;long bytes,first,last;
        ChunkWriter(Path output,String kind,List<Chunk> chunks,Budget budget){this.output=output;this.kind=kind;this.chunks=chunks;this.budget=budget;}
        void add(T row,long cursor)throws Exception {
            byte[] data=(JSON.toJson(row)+"\n").getBytes(StandardCharsets.UTF_8);
            if(data.length>CHUNK_BYTES || cursor<=last)throw new IllegalArgumentException("Oversized or unordered source row");
            if(stream!=null && (count==CHUNK_ROWS || bytes+data.length>CHUNK_BYTES))flush();
            budget.check(data.length);
            if(stream==null) {
                path=output.resolve(String.format(Locale.ROOT,"%s-%06d.jsonl",kind,chunks.size()+1));
                stream=new BufferedOutputStream(Files.newOutputStream(path,StandardOpenOption.CREATE_NEW));first=cursor;
            }
            stream.write(data);budget.add(data.length);count++;bytes+=data.length;last=cursor;
        }
        void flush()throws Exception {
            if(stream==null)return;stream.close();stream=null;
            chunks.add(new Chunk(path.getFileName().toString(),chunks.size()+1,count,bytes,first,last,hash(path)));count=0;bytes=0;
        }
        public void close()throws Exception{flush();}
    }
    private static Map<String,List<?>> chunkCache() {
        return new LinkedHashMap<String,List<?>>(16,.75f,true) {
            protected boolean removeEldestEntry(Map.Entry<String,List<?>> e){return size()>CACHE_CHUNKS;}
        };
    }
    /** At most eight 4-MiB chunks retained. File hashes/counts/cursors are rechecked on every load. */
    private static final class ChunkList<T> extends AbstractList<T> {
        final Path dir;final List<Chunk> chunks;final Class<T> type;final int[] starts;final int size;
        final Map<String,List<?>> cache;
        ChunkList(Path dir,List<Chunk> chunks,Class<T> type,Map<String,List<?>> cache) {
            this.cache=cache;
            this.dir=dir;this.chunks=List.copyOf(chunks);this.type=type;starts=new int[chunks.size()];int n=0;
            for(int i=0;i<chunks.size();i++){starts[i]=n;n=Math.addExact(n,chunks.get(i).rows);}size=n;
        }
        public int size(){return size;}
        @SuppressWarnings("unchecked")
        public T get(int index) {
            Objects.checkIndex(index,size);int which=Arrays.binarySearch(starts,index);if(which<0)which=-which-2;
            List<T> values=(List<T>)cache.get(chunks.get(which).file);
            if(values==null) {
                Chunk c=chunks.get(which);values=new ArrayList<>();
                try {
                    Path file=regular(dir,c.file,CHUNK_BYTES);
                    if(Files.size(file)!=c.bytes || !hash(file).equals(c.sha256))throw new IllegalArgumentException("Chunk changed during read");
                    long first=0,last=0;
                    try(var in=Files.newBufferedReader(file,StandardCharsets.UTF_8)) {
                        String line;while((line=in.readLine())!=null) {
                            T row=JSON.fromJson(line,type);
                            long cursor=row instanceof ExecutionSourceSnapshot.Row?((ExecutionSourceSnapshot.Row)row).rowId:((ExecutionSourceSnapshot.Gap)row).rowId;
                            if(cursor<=last || values.size()>=CHUNK_ROWS)throw new IllegalArgumentException("Invalid chunk row order");
                            if(values.isEmpty())first=cursor;last=cursor;values.add(row);
                        }
                    }
                    if(values.size()!=c.rows || first!=c.firstCursor || last!=c.lastCursor)throw new IllegalArgumentException("Chunk count or cursor mismatch");
                }catch(RuntimeException e){throw e;}catch(Exception e){throw new IllegalStateException("Chunk read failed",e);}
                cache.put(chunks.get(which).file,values);
            }
            return values.get(index-starts[which]);
        }
    }
}
