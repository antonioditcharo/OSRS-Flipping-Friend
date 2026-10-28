package com.flippingfriend.companion;

import com.google.gson.*;
import java.io.IOException;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;
import okhttp3.*;

/** Optional single-worker public capture. No device, account, advice or financial input. */
final class HostedPublicArchive implements AutoCloseable {
    static final class Config {
        boolean enabled=false;
        long durationSeconds=954000,budgetBytes=2L*1024*1024*1024;
        int seedItems=32;
    }
    interface History {JsonArray fetch(int item,String step)throws Exception;}
    private final Path root;
    private final Config config;
    private final Supplier<MarketIngestionService.MarketState> market;
    private final History history;
    private final LongSupplier clock,free;
    private final ThreadPoolExecutor worker=new ThreadPoolExecutor(1,1,0,TimeUnit.SECONDS,new ArrayBlockingQueue<>(1),r->{
        var thread=new Thread(r,"pilot-public-archive");thread.setDaemon(true);thread.setPriority(Thread.MIN_PRIORITY);return thread;});
    private volatile boolean disabled,closed;
    private PublicMarketArchive archive;
    private JsonObject catalogue;
    private int nextSeed;

    HostedPublicArchive(Path root,Config config,Supplier<MarketIngestionService.MarketState> market) {
        this(root,config,market,(item,step)-> {
            // Independent timeout/dispatcher; archive I/O never occupies financial/public foreground workers.
            Request request=new Request.Builder().url("https://prices.runescape.wiki/api/v1/osrs/timeseries?id="+item+"&timestep="+step)
                .header("User-Agent",CompanionHttp.USER_AGENT).build();
            OkHttpClient client=ArchiveHttp.CLIENT;
            try(Response response=client.newCall(request).execute()) {
                if(!response.isSuccessful() || response.body()==null)throw new IOException("Public history request failed");
                byte[] raw=response.body().byteStream().readNBytes(PublicMarketArchive.MAX_PAYLOAD+1);
                if(raw.length>PublicMarketArchive.MAX_PAYLOAD)throw new IOException("Oversized public history");
                return JsonParser.parseString(new String(raw,java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonArray("data");
            }
        },HostedPublicArchive::now,()-> {
            try{return Files.getFileStore(root.getParent()).getUsableSpace();}catch(IOException e){return 0;}
        });
    }
    private static final class ArchiveHttp {static final OkHttpClient CLIENT=new OkHttpClient.Builder().callTimeout(7,TimeUnit.SECONDS).build();}
    HostedPublicArchive(Path root,Config config,Supplier<MarketIngestionService.MarketState> market,History history,LongSupplier clock,LongSupplier free) {
        if(config==null || config.seedItems<1 || config.seedItems>32)throw new IllegalArgumentException("Bounded seed catalogue required");
        this.root=root;this.config=config;this.market=market;this.history=history;this.clock=clock;this.free=free;
        disabled=!config.enabled;
    }
    static long now(){Instant time=Instant.now();return Math.addExact(Math.multiplyExact(time.getEpochSecond(),1000000000L),time.getNano());}
    void request() {
        if(disabled || closed)return;
        try{worker.execute(this::run);}catch(RejectedExecutionException busy){/* One queued tick suffices; no unbounded catch-up. */}
    }
    boolean disabled(){return disabled;}
    int queued(){return worker.getQueue().size();}
    private void run() {
        try {
            tick();
        } catch(Exception failure) {
            disabled=true;System.err.println("Public archive paused: "+failure.getClass().getSimpleName()+"; guidance remains independent");
            release();
        } finally {if(closed)release();}
    }
    void tick()throws Exception {
        if(disabled || closed)return;
        if(archive==null)archive=new PublicMarketArchive(root,clock.getAsLong(),config.durationSeconds,config.budgetBytes,free);
        long requested=clock.getAsLong();
        if(archive.expired(requested)){archive.seal();disabled=true;release();return;}
        var state=market.get();if(state==null || state.mapping.isEmpty() || state.observedAt<=0)return;
        if(catalogue==null)loadCatalogue(state,requested);
        Path seeds=root.resolve("seeds.json");
        if(!Files.exists(seeds)) {
            JsonArray selected=catalogue.getAsJsonArray("selectedItems");
            for(int work=0;work<2 && nextSeed<selected.size()*2;work++,nextSeed++) {
                int item=selected.get(nextSeed/2).getAsInt();String step=nextSeed%2==0?"5m":"1h";
                Path file=root.resolve(String.format(Locale.ROOT,"seed-%03d.json",nextSeed));
                if(Files.exists(file)){validateSeed(PublicMarketArchive.read(file,65536),item,step);continue;}
                long request=clock.getAsLong();var result=new JsonObject();result.addProperty("itemId",item);result.addProperty("step",step);result.addProperty("requestAtNs",request);
                JsonArray captured=null;
                try {
                    captured=history.fetch(item,step);long available=clock.getAsLong();validateHistory(captured,step,available);
                    var allowed=new JsonArray();
                    for(JsonElement element:captured) {
                        var bar=new JsonObject();for(String name:List.of("timestamp","avgLowPrice","avgHighPrice","lowPriceVolume","highPriceVolume"))
                            if(element.getAsJsonObject().has(name))bar.add(name,element.getAsJsonObject().get(name));allowed.add(bar);
                    }
                    captured=allowed;
                    result.addProperty("availableAtNs",available);result.addProperty("outcome","CAPTURE");
                } catch(Exception failure) {
                    // Transport/malformed history stays missing, with its attempt recorded. Storage errors still fail below.
                    captured=null;result.addProperty("outcome","FAILURE");result.addProperty("errorKind",failure.getClass().getSimpleName());result.addProperty("availableAtNs",clock.getAsLong());
                }
                if(captured!=null)result.addProperty("sha256",archive.blob(captured));
                validateSeed(result,item,step);archive.write(file,PublicMarketArchive.bytes(result));
            }
            if(nextSeed<selected.size()*2)return;
            var entries=new JsonArray();var outcomes=new JsonArray();
            for(int i=0;i<nextSeed;i++) {
                var row=PublicMarketArchive.read(root.resolve(String.format(Locale.ROOT,"seed-%03d.json",i)),65536);outcomes.add(row);
                if("CAPTURE".equals(row.get("outcome").getAsString())) {
                    var entry=new JsonObject();entry.addProperty("key",row.get("itemId").getAsInt()+"@"+row.get("step").getAsString());
                    entry.addProperty("fetchedAt",PublicMarketArchive.integer(row,"availableAtNs")/1000000000L);
                    entry.add("data",readBlob(row.get("sha256").getAsString()));entries.add(entry);
                }
            }
            var series=new JsonObject();series.add("entries",entries);var metadata=new JsonObject();var files=new JsonObject();
            files.add("item-mapping.json",catalogue.getAsJsonObject("mapping"));
            var seriesRef=new JsonObject();seriesRef.addProperty("sha256",archive.blob(series));seriesRef.addProperty("availableAtNs",clock.getAsLong());
            files.add("series-cache.json.gz",seriesRef);metadata.add("seedFiles",files);metadata.add("attempts",outcomes);
            archive.write(seeds,PublicMarketArchive.bytes(metadata));
            return; // First observation is strictly after seed availability, including after restart.
        }
        requested=clock.getAsLong();var row=new JsonObject();row.addProperty("requestAtNs",requested);
        try {
            JsonObject feed=publicFeed(state,requested/1000000000L),parts=new JsonObject();
            for(String key:List.of("latest","fiveMinute","hourly"))parts.addProperty(key,archive.blob(feed.get(key)));
            row.addProperty("kind","CAPTURE");row.add("parts",parts);
            for(String key:List.of("observedAt","fiveMinuteStartedAt","hourlyStartedAt"))row.add(key,feed.get(key));
            row.addProperty("availableAtNs",clock.getAsLong());
        } catch(IllegalArgumentException malformed) {
            row.addProperty("kind","FAILURE");row.addProperty("recordedAtNs",clock.getAsLong());row.addProperty("errorKind","INVALID_PUBLIC_FRAME");
        }
        archive.append(row);
    }
    private void loadCatalogue(MarketIngestionService.MarketState state,long at)throws Exception {
        Path file=root.resolve("catalogue.json");
        if(Files.exists(file)){
            catalogue=PublicMarketArchive.read(file,65536);
            if(PublicMarketArchive.integer(catalogue,"requestedSeedItems")!=config.seedItems)throw new IOException("Seed catalogue configuration changed");
            return;
        }
        publicFeed(state,at/1000000000L); // Validate public clocks and numeric types before catalogue ranking.
        if(state.mapping.size()>20000 || state.mapping.values().stream().anyMatch(i->i.id<=0 || i.name==null || i.name.length()>256))
            throw new IOException("Invalid bounded public mapping");
        var ranked=new ArrayList<MarketIngestionService.Item>();
        for(var item:state.mapping.values())if(item.id>0 && item.buyLimitPublished && item.buyLimit>0
            && state.hourly.has(Integer.toString(item.id)) && replaySupported(state,item.id))ranked.add(item);
        ranked.sort(Comparator.<MarketIngestionService.Item,java.math.BigDecimal>comparing(i->turnover(state,i.id)).reversed().thenComparingInt(i->i.id));
        if(ranked.isEmpty())throw new IOException("No published-limit public catalogue");
        var mapping=new JsonArray();
        for(var item:ranked) {
            var value=new JsonObject();value.addProperty("id",item.id);value.addProperty("name",item.name);
            value.addProperty("limit",item.buyLimit);value.addProperty("members",item.members);mapping.add(value);
        }
        var ref=new JsonObject();ref.addProperty("sha256",archive.blob(mapping));ref.addProperty("availableAtNs",at);
        catalogue=new JsonObject();catalogue.add("mapping",ref);var selected=new JsonArray();
        for(int i=0;i<Math.min(config.seedItems,ranked.size());i++)selected.add(ranked.get(i).id);
        catalogue.add("selectedItems",selected);catalogue.addProperty("requestedSeedItems",config.seedItems);
        catalogue.addProperty("selectionRule","INITIAL_PUBLIC_HOURLY_TURNOVER_THEN_ITEM_ID");
        archive.write(file,PublicMarketArchive.bytes(catalogue));
    }
    private static java.math.BigDecimal turnover(MarketIngestionService.MarketState state,int item) {
        var row=state.hourly.getAsJsonObject(Integer.toString(item));
        var price=row.get("avgLowPrice");if(price==null || price.isJsonNull())return java.math.BigDecimal.ZERO;
        return price.getAsBigDecimal().multiply(row.get("lowPriceVolume").getAsBigDecimal().add(row.get("highPriceVolume").getAsBigDecimal()));
    }
    private static boolean replaySupported(MarketIngestionService.MarketState state,int item) {
        for(JsonObject source:List.of(state.latest,state.fiveMinute,state.hourly)) {
            JsonObject row=source.getAsJsonObject(Integer.toString(item));if(row==null)continue;
            for(String field:List.of("low","high","avgLowPrice","avgHighPrice","lowPriceVolume","highPriceVolume")) {
                JsonElement value=row.get(field);if(value!=null && !value.isJsonNull() && number(value)>Integer.MAX_VALUE)return false;
            }
            for(String[] fields:new String[][]{{"avgLowPrice","avgHighPrice"},{"lowPriceVolume","highPriceVolume"}}) {
                long sum=0;for(String field:fields)if(row.has(field) && !row.get(field).isJsonNull())sum+=number(row.get(field));
                if(sum>Integer.MAX_VALUE)return false;
            }
        }
        return true;
    }
    private JsonElement readBlob(String key)throws IOException {
        if(!key.matches("[0-9a-f]{64}"))throw new IOException("Invalid public blob key");
        byte[] packed=PublicMarketArchive.readBytes(root.resolve("blobs").resolve(key+".json.gz"),PublicMarketArchive.MAX_PAYLOAD);
        try(var in=new java.util.zip.GZIPInputStream(new java.io.ByteArrayInputStream(packed))) {
            byte[] raw=in.readNBytes(PublicMarketArchive.MAX_PAYLOAD+1);
            if(raw.length>PublicMarketArchive.MAX_PAYLOAD || !key.equals(PublicMarketArchive.hash(raw)))throw new IOException("Public seed blob changed");
            return JsonParser.parseString(new String(raw,java.nio.charset.StandardCharsets.UTF_8));
        }
    }
    private static void validateSeed(JsonObject row,int item,String step)throws IOException {
        if(PublicMarketArchive.integer(row,"itemId")!=item || !step.equals(row.get("step").getAsString())
            || PublicMarketArchive.integer(row,"requestAtNs")>=PublicMarketArchive.integer(row,"availableAtNs")
            || !Set.of("CAPTURE","FAILURE").contains(row.get("outcome").getAsString()))throw new IOException("Seed attempt changed");
    }
    static void validateHistory(JsonArray bars,String step,long availableAtNs) {
        if(bars==null || bars.size()>720)throw new IllegalArgumentException("Bounded public history required");
        int seconds=step.equals("5m")?300:3600;long previous=0;
        for(JsonElement value:bars) {
            JsonObject row=value.getAsJsonObject();long at=PublicMarketArchive.integer(row,"timestamp");
            if(at<=previous || at%seconds!=0 || Math.addExact(at,seconds)>availableAtNs/1000000000L)throw new IllegalArgumentException("Incomplete/out-of-order seed bucket");
            aggregate(row);previous=at;
        }
    }
    static JsonObject publicFeed(MarketIngestionService.MarketState state,long now) {
        if(state.observedAt<=0 || state.observedAt>now)throw new IllegalArgumentException("Invalid public snapshot clock");
        var feed=new JsonObject();feed.addProperty("observedAt",state.observedAt);
        for(String name:List.of("fiveMinuteStartedAt","hourlyStartedAt")) {
            long start=name.equals("fiveMinuteStartedAt")?state.fiveMinuteStartedAt:state.hourlyStartedAt,seconds=name.equals("fiveMinuteStartedAt")?300:3600;
            if(start<0 || start>0 && (start%seconds!=0 || Math.addExact(start,seconds)>state.observedAt))throw new IllegalArgumentException("Incomplete public aggregate");
            feed.addProperty(name,start);
        }
        for(String name:List.of("latest","fiveMinute","hourly")) {
            JsonObject source=name.equals("latest")?state.latest:name.equals("fiveMinute")?state.fiveMinute:state.hourly;
            if(source==null || source.size()>20000)throw new IllegalArgumentException("Bounded public map required");
            var rows=new JsonObject();
            for(var entry:source.entrySet()) {
                if(!entry.getKey().matches("[1-9][0-9]{0,9}") || Long.parseLong(entry.getKey())>Integer.MAX_VALUE)throw new IllegalArgumentException("Invalid public item");
                JsonObject row=entry.getValue().getAsJsonObject(),copy=new JsonObject();
                if(name.equals("latest")) {
                    for(String field:List.of("low","high","lowTime","highTime")) {
                        JsonElement v=row.get(field);if(v==null)continue;
                        if(!v.isJsonNull()){long n=number(v);if(n<=0 || field.endsWith("Time") && n>state.observedAt)throw new IllegalArgumentException("Invalid public quote");}
                        copy.add(field,v);
                    }
                } else {aggregate(row);for(String field:List.of("avgLowPrice","avgHighPrice","lowPriceVolume","highPriceVolume"))if(row.has(field))copy.add(field,row.get(field));}
                rows.add(entry.getKey(),copy);
            }
            feed.add(name,rows);
        }
        return feed;
    }
    private static long number(JsonElement v) {
        if(!v.isJsonPrimitive() || !v.getAsJsonPrimitive().isNumber())throw new IllegalArgumentException("Exact numeric public evidence required");
        return v.getAsBigDecimal().longValueExact();
    }
    private static void aggregate(JsonObject row) {
        for(String field:List.of("avgLowPrice","avgHighPrice","lowPriceVolume","highPriceVolume")) {
            JsonElement v=row.get(field);if(v==null || v.isJsonNull()){if(field.endsWith("Volume"))throw new IllegalArgumentException("Unknown public volume");continue;}
            long n=number(v);if(n<0 || field.startsWith("avg") && n==0)throw new IllegalArgumentException("Invalid public aggregate");
        }
    }
    private void release(){if(archive!=null)try{archive.close();}catch(IOException ignored){}finally{archive=null;}}
    @Override public void close(){
        closed=true;worker.shutdownNow();
        try{if(worker.awaitTermination(1,TimeUnit.SECONDS))release();}catch(InterruptedException e){Thread.currentThread().interrupt();}
    }
}
