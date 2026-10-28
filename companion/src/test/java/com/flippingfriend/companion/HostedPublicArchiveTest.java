package com.flippingfriend.companion;

import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.Test;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class HostedPublicArchiveTest {
    @Rule public TemporaryFolder temp=new TemporaryFolder();
    private static final long START=1_800_000_000_000000000L;
    private PublicMarketArchive store(Path root,long at)throws Exception {return new PublicMarketArchive(root,at,3600,1024*1024,()->1024L*1024*1024);}
    private JsonObject row(long at) {
        var row=new JsonObject();row.addProperty("kind","FAILURE");row.addProperty("errorKind","TEST_ONLY");
        row.addProperty("requestAtNs",at-1);row.addProperty("recordedAtNs",at);return row;
    }
    private long seals(Path root)throws Exception {try(var dirs=Files.list(root.resolve("segments"))){return dirs.filter(p->Files.exists(p.resolve("seal.json"))).count();}}
    @Test public void interruptedTailIsRetainedAndRestartExtendsOnlySealedPrefix()throws Exception {
        Path root=temp.getRoot().toPath().resolve("archive");String reused;
        try(var a=store(root,START)) {
            var publicValue=new JsonObject();publicValue.addProperty("low",100);reused=a.blob(publicValue);
            assertEquals(reused,a.blob(publicValue));
            for(int i=0;i<7;i++)a.append(row(START+i*60_000000000L+1));
            assertEquals(1,seals(root));
        }
        Path tail;try(var paths=Files.list(root.resolve("segments"))){tail=paths.filter(p->!Files.exists(p.resolve("seal.json"))).findFirst().orElseThrow();}
        byte[] original=Files.readAllBytes(tail.resolve("00000000.json"));
        try(var a=store(root,START+700_000000000L)) {
            assertEquals(START+300_000000000L+1,a.lastAt());
            a.append(row(START+720_000000000L));a.seal();
        }
        assertEquals(2,seals(root));assertFalse(Files.exists(tail.resolve("seal.json")));
        assertArrayEquals(original,Files.readAllBytes(tail.resolve("00000000.json")));
        try(var a=store(root,START+800_000000000L)){assertEquals(START+720_000000000L,a.lastAt());}
        try(var blobs=Files.list(root.resolve("blobs"))){assertEquals(1,blobs.count());}
    }
    @Test public void conflictingWriterChangedSealedRecordClockRegressionAndDiskReserveAreRefused()throws Exception {
        Path root=temp.getRoot().toPath().resolve("archive");
        try(var a=store(root,START)) {
            try{store(root,START);fail();}catch(java.io.IOException expected){}
            try{a.write(root.resolve("over-budget.json"),new byte[1024*1024]);fail();}catch(java.io.IOException expected){}
            assertFalse(Files.exists(root.resolve("over-budget.json")));
            a.append(row(START+1));
            try{a.append(row(START));fail();}catch(java.io.IOException expected){}
            a.seal();
        }
        Path sealed;try(var dirs=Files.list(root.resolve("segments"))){sealed=dirs.findFirst().orElseThrow();}
        Files.writeString(sealed.resolve("00000000.json"),"{}");
        try{store(root,START+10);fail();}catch(java.io.IOException expected){}
        try{new PublicMarketArchive(temp.getRoot().toPath().resolve("disk"),START,3600,1024*1024,()->0);fail();}catch(java.io.IOException expected){}
    }
    @Test public void stopClockSurvivesRestartAndConfigurationCannotExtendIt()throws Exception {
        Path root=temp.getRoot().toPath().resolve("archive");
        try(var a=store(root,START)){assertFalse(a.expired(START+3599_000000000L));}
        try(var a=store(root,START+3600_000000000L)){assertTrue(a.expired(START+3600_000000000L));}
        try{new PublicMarketArchive(root,START+1,7200,1024*1024,()->1024L*1024*1024);fail();}catch(java.io.IOException expected){}
    }
    static MarketIngestionService.MarketState state(long now) {
        var latest=new JsonObject();var quote=new JsonObject();quote.addProperty("low",100);quote.addProperty("high",120);
        quote.addProperty("lowTime",now-1);quote.addProperty("highTime",now-2);quote.addProperty("accountId","MUST_NOT_CAPTURE");latest.add("1",quote);
        var bars=new JsonObject();bars.add("1",bar(now/300*300-300));
        return new MarketIngestionService.MarketState(Map.of(1,new MarketIngestionService.Item(1,"Synthetic public item",100,true)),latest,bars,bars,now,now/300*300-300,now/3600*3600-3600);
    }
    static JsonObject bar(long at) {
        var row=new JsonObject();row.addProperty("timestamp",at);row.addProperty("avgLowPrice",100);row.addProperty("avgHighPrice",120);
        row.addProperty("lowPriceVolume",1000);row.addProperty("highPriceVolume",2000);return row;
    }
    private HostedPublicArchive.Config config(){var c=new HostedPublicArchive.Config();c.enabled=true;c.seedItems=1;c.durationSeconds=3600;c.budgetBytes=1024*1024;return c;}
    private HostedPublicArchive worker(Path root,AtomicLong clock,HostedPublicArchive.History history) {
        return new HostedPublicArchive(root,config(),()->state(clock.get()/1000000000L),history,clock::incrementAndGet,()->1024L*1024*1024);
    }
    @Test public void seedsUseActualAvailabilityMissingSidesRemainMissingAndRestartDoesNotFetchThemAgain()throws Exception {
        Path root=temp.getRoot().toPath().resolve("archive");var clock=new AtomicLong(START);var calls=new AtomicInteger();
        HostedPublicArchive.History fetch=(item,step)-> {
            calls.incrementAndGet();if(step.equals("1h"))throw new java.io.IOException("fixture-only");
            var data=new JsonArray();for(int i=48;i>0;i--)data.add(bar(START/1000000000L-i*300));return data;
        };
        try(var worker=worker(root,clock,fetch)) {worker.tick();clock.addAndGet(60_000000000L);worker.tick();}
        assertEquals(2,calls.get());
        JsonObject seed=PublicMarketArchive.read(root.resolve("seeds.json"),65536);
        assertEquals("FAILURE",seed.getAsJsonArray("attempts").get(1).getAsJsonObject().get("outcome").getAsString());
        assertTrue(PublicMarketArchive.integer(seed.getAsJsonObject("seedFiles").getAsJsonObject("series-cache.json.gz"),"availableAtNs")>START);
        try(var worker=worker(root,clock,fetch)){clock.addAndGet(60_000000000L);worker.tick();}
        assertEquals(2,calls.get());
        var feed=HostedPublicArchive.publicFeed(state(START/1000000000L),START/1000000000L);
        assertFalse(feed.toString().contains("MUST_NOT_CAPTURE"));assertEquals(6,feed.size());
    }
    @Test public void queueHasOneEntryAndBlockedArchiveWorkDoesNotBlockCaller()throws Exception {
        var entered=new CountDownLatch(1);var finish=new CountDownLatch(1);var clock=new AtomicLong(START);
        try(var worker=worker(temp.getRoot().toPath().resolve("queue"),clock,(item,step)-> {
            assertEquals(Thread.MIN_PRIORITY,Thread.currentThread().getPriority());entered.countDown();finish.await(5,TimeUnit.SECONDS);return new JsonArray();
        })) {
            worker.request();assertTrue(entered.await(5,TimeUnit.SECONDS));
            for(int i=0;i<100;i++)worker.request();assertEquals(1,worker.queued());
            finish.countDown();
        } finally {finish.countDown();}
    }
    @Test public void restartDuringSeedingImmediatelyContinuesMissingRequestsWithoutRewritingPriorAttempts()throws Exception {
        Path root=temp.getRoot().toPath().resolve("partial-seed");var clock=new AtomicLong(START);var calls=new AtomicInteger();
        var settings=config();settings.seedItems=3;
        java.util.function.Supplier<MarketIngestionService.MarketState> source=()-> {
            var first=state(clock.get()/1000000000L);var mapping=new TreeMap<Integer,MarketIngestionService.Item>();
            for(int item=1;item<=3;item++) {
                mapping.put(item,new MarketIngestionService.Item(item,"Synthetic "+item,100,true));
                if(item>1){first.latest.add(""+item,first.latest.get("1").deepCopy());first.fiveMinute.add(""+item,first.fiveMinute.get("1").deepCopy());}
            }
            return new MarketIngestionService.MarketState(mapping,first.latest,first.fiveMinute,first.hourly,first.observedAt,first.fiveMinuteStartedAt,first.hourlyStartedAt);
        };
        HostedPublicArchive.History fetch=(item,step)->{calls.incrementAndGet();return new JsonArray();};
        byte[] original,manifest;
        try(var worker=new HostedPublicArchive(root,settings,source,fetch,clock::incrementAndGet,()->1024L*1024*1024)) {
            worker.tick();assertEquals(2,calls.get());assertFalse(Files.exists(root.resolve("seeds.json")));
            original=Files.readAllBytes(root.resolve("seed-000.json"));manifest=Files.readAllBytes(root.resolve("manifest.json"));
        }
        clock.addAndGet(60_000000000L);
        try(var worker=new HostedPublicArchive(root,settings,source,fetch,clock::incrementAndGet,()->1024L*1024*1024)) {
            worker.tick();assertEquals(4,calls.get());assertFalse(Files.exists(root.resolve("seeds.json")));
        }
        clock.addAndGet(60_000000000L);
        try(var worker=new HostedPublicArchive(root,settings,source,fetch,clock::incrementAndGet,()->1024L*1024*1024)) {
            worker.tick();assertEquals(6,calls.get());assertTrue(Files.exists(root.resolve("seeds.json")));
        }
        assertArrayEquals(original,Files.readAllBytes(root.resolve("seed-000.json")));
        assertArrayEquals(manifest,Files.readAllBytes(root.resolve("manifest.json")));
    }
    @Test public void malformedIntegerPricesVolumesAndIncompleteSeedBucketsCannotBecomeEvidence() {
        var invalid=state(START/1000000000L);invalid.latest.getAsJsonObject("1").addProperty("low",1.5);
        try{HostedPublicArchive.publicFeed(invalid,START/1000000000L);fail();}catch(RuntimeException expected){}
        var data=new JsonArray();data.add(bar(START/1000000000L));
        try{HostedPublicArchive.validateHistory(data,"5m",START);fail();}catch(RuntimeException expected){}
        var expensive=state(START/1000000000L);expensive.latest.getAsJsonObject("1").addProperty("low",8_770_000_000L);
        expensive.hourly.getAsJsonObject("1").addProperty("avgLowPrice",2_904_898_134L);
        var retained=HostedPublicArchive.publicFeed(expensive,START/1000000000L);
        assertEquals(8_770_000_000L,retained.getAsJsonObject("latest").getAsJsonObject("1").get("low").getAsLong());
        assertEquals(2_904_898_134L,retained.getAsJsonObject("hourly").getAsJsonObject("1").get("avgLowPrice").getAsLong());
        data=new JsonArray();var b=bar(START/1000000000L-300);b.addProperty("lowPriceVolume",-1);data.add(b);
        try{HostedPublicArchive.validateHistory(data,"5m",START);fail();}catch(RuntimeException expected){}
    }
    @Test public void corruptedRetainedCatalogueCannotExpandOrRepeatTheBoundedSeedWork()throws Exception {
        for(String mutation:List.of("large","duplicate","fractional")) {
            Path root=temp.getRoot().toPath().resolve("catalogue-"+mutation);var clock=new AtomicLong(START);var calls=new AtomicInteger();
            try(var worker=worker(root,clock,(item,step)->{calls.incrementAndGet();return new JsonArray();})){worker.tick();}
            assertEquals(2,calls.get());var catalogue=PublicMarketArchive.read(root.resolve("catalogue.json"),65536);var changed=new JsonArray();
            if(mutation.equals("large"))changed.add(9_000_000_000L);
            else if(mutation.equals("fractional"))changed.add(1.5);
            else {changed.add(1);changed.add(1);}
            catalogue.add("selectedItems",changed);Files.write(root.resolve("catalogue.json"),PublicMarketArchive.bytes(catalogue));
            try(var worker=worker(root,clock,(item,step)->{calls.incrementAndGet();return new JsonArray();})) {
                try{worker.tick();fail();}catch(Exception expected){}
            }
            assertEquals(2,calls.get());
        }
    }
    @Test public void disabledOrFailedArchiveLeavesNoForegroundDependency()throws Exception {
        Path root=temp.getRoot().toPath().resolve("disabled");var c=config();c.enabled=false;
        try(var worker=new HostedPublicArchive(root,c,()->{throw new AssertionError();},(i,s)->{throw new AssertionError();},()->START,()->0)) {
            worker.request();assertTrue(worker.disabled());assertEquals(0,worker.queued());assertFalse(Files.exists(root));
        }
        var entered=new CountDownLatch(1);
        try(var worker=new HostedPublicArchive(root,config(),()->state(START/1000000000L),(i,s)->new JsonArray(),()->START,()->{entered.countDown();return 0;})) {
            worker.request();assertTrue(entered.await(5,TimeUnit.SECONDS));
            long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);while(!worker.disabled() && System.nanoTime()<until)Thread.yield();
            assertTrue(worker.disabled());assertEquals(0,worker.queued());
        }
    }
}
