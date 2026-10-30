package com.flippingfriend.companion;

import com.flippingfriend.data.Candle;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.zip.*;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

/** Existing public-cache format, ambiguity rules and cold-start fallback survive streaming IO. */
public class SeriesCacheStreamingTest {
    @Rule public final TemporaryFolder folder=new TemporaryFolder();

    @Test public void streamedWriterRetainsLegacyFieldsNullSidesAndExactIntegerValues()throws Exception {
        Path file=folder.newFolder().toPath().resolve("series-cache.json.gz");
        Instant fetched=Instant.now();var cache=new SeriesCache(file);
        cache.remember(1,"5m",List.of(new Candle(2147484000L,null,2147483647,0,2147483647)),fetched);
        cache.remember(2,"5m",List.of(new Candle(300,200,100,2,3)),fetched.minusSeconds(86400));
        cache.save();
        String expected="{\"entries\":[{\"key\":\"1@5m\",\"fetchedAt\":"+fetched.getEpochSecond()
            +",\"data\":[{\"timestamp\":2147484000,\"avgHighPrice\":null,\"avgLowPrice\":2147483647,\"highPriceVolume\":0,\"lowPriceVolume\":2147483647}]}]}";
        try(var in=new GZIPInputStream(Files.newInputStream(file))) {
            assertEquals("Old runtimes consume the same JSON fields and exact numbers",expected,new String(in.readAllBytes(),StandardCharsets.UTF_8));
        }
        byte[] before=Files.readAllBytes(file);var restored=new SeriesCache(file);
        assertEquals(1,restored.series(1,"5m").size());assertNull(restored.series(1,"5m").get(0).getAvgHighPrice());
        assertEquals(Integer.valueOf(Integer.MAX_VALUE),restored.series(1,"5m").get(0).getAvgLowPrice());
        assertArrayEquals(before,Files.readAllBytes(file));assertTrue(restored.series(2,"5m").isEmpty());
    }

    @Test public void repeatedRootMembersRetainTheLegacyLastMemberSemantics()throws Exception {
        String first=entry(1),last=entry(2);Path file=write("{\"entries\":["+first+"],\"extra\":{\"nested\":[1,2]},\"entries\":["+last+"]}");
        var cache=new SeriesCache(file);assertTrue(cache.series(1,"5m").isEmpty());assertEquals(1,cache.series(2,"5m").size());
        Path invalidLast=write("{\"entries\":["+first+"],\"entries\":false}");
        assertTrue(new SeriesCache(invalidLast).series(1,"5m").isEmpty());
    }

    @Test public void malformedContainersDiscardPreparedEntriesAndDuplicateKeysRemainUnavailable()throws Exception {
        for(String text:List.of("{\"entries\":["+entry(1)+"]", "{\"entries\":["+entry(1)+"]} trailing", "{\"entries\":["+entry(1)+",{")) {
            assertTrue(new SeriesCache(write(text)).series(1,"5m").isEmpty());
        }
        var duplicate=new SeriesCache(write("{\"entries\":["+entry(1)+","+entry(1)+","+entry(2)+"]}"));
        assertTrue(duplicate.series(1,"5m").isEmpty());assertEquals(1,duplicate.series(2,"5m").size());
    }

    @Test public void fullCapacityRoundTripPreservesEveryHistoryAndFetchClock()throws Exception {
        verifyFullCapacity(folder.newFolder().toPath());
    }

    static void verifyFullCapacity(Path directory)throws Exception {
        Path file=directory.resolve("series-cache.json.gz");var cache=new SeriesCache(file);Instant at=Instant.now();
        for(int item=1;item<=600;item++) {
            var bars=new ArrayList<Candle>();
            for(int i=0;i<288;i++)bars.add(new Candle(1600000200L+i*300,1000+item,900+item,i,item));
            cache.remember(item,"5m",bars,at);
        }
        cache.save();var restored=new SeriesCache(file);assertEquals(600,restored.warmCount("5m"));
        for(int item=1;item<=600;item++) {
            var bars=restored.series(item,"5m");assertEquals(288,bars.size());
            for(int i=0;i<bars.size();i++) {
                var bar=bars.get(i);assertEquals(1600000200L+i*300,bar.getTimestamp());
                assertEquals(Integer.valueOf(1000+item),bar.getAvgHighPrice());assertEquals(Integer.valueOf(900+item),bar.getAvgLowPrice());
                assertEquals(i,bar.getHighPriceVolume());assertEquals(item,bar.getLowPriceVolume());
            }
        }
        try(var in=new GZIPInputStream(Files.newInputStream(file));var reader=new java.io.InputStreamReader(in,StandardCharsets.UTF_8)) {
            var json=com.google.gson.JsonParser.parseReader(reader).getAsJsonObject();
            for(var entry:json.getAsJsonArray("entries"))assertEquals(at.getEpochSecond(),entry.getAsJsonObject().get("fetchedAt").getAsLong());
        }
    }

    private Path write(String text)throws Exception {
        Path file=folder.newFolder().toPath().resolve("cache.json.gz");
        try(var out=new GZIPOutputStream(Files.newOutputStream(file))){out.write(text.getBytes(StandardCharsets.UTF_8));}return file;
    }
    private static String entry(int item) {
        return "{\"key\":\""+item+"@5m\",\"fetchedAt\":"+Instant.now().getEpochSecond()
            +",\"data\":[{\"timestamp\":300,\"avgHighPrice\":200,\"avgLowPrice\":100,\"highPriceVolume\":2,\"lowPriceVolume\":3}]}";
    }
}
