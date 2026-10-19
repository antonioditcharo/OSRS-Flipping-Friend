package com.flippingfriend.companion;

import com.google.gson.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.*;
import okhttp3.*;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

/** Publication clocks belong to the exact cached aggregate, not to a new poll of /latest. */
public class MarketPublicationClockTest {
    @Rule public TemporaryFolder folder=new TemporaryFolder();
    final long now=Instant.now().getEpochSecond();
    final long shortAt=(now/300-1)*300,hourAt=(now/3600-1)*3600;
    final Map<String,Integer> requests=new HashMap<>();
    final AtomicBoolean failHour=new AtomicBoolean();
    MarketIngestionService service() {
        var client=new OkHttpClient.Builder().addInterceptor(chain->{
            var request=chain.request();String path=request.url().encodedPath();String route=path.substring(path.lastIndexOf('/')+1);
            requests.merge(route,1,Integer::sum);
            String body=route.equals("mapping")?"[{\"id\":561,\"name\":\"Nature rune\",\"limit\":10000}]":
                route.equals("latest")?"{\"data\":{\"561\":{\"low\":100,\"lowTime\":"+now+"}}}":
                "{\"timestamp\":"+(route.equals("5m")?shortAt:hourAt)+",\"data\":{\"561\":{\"avgLowPrice\":100,\"lowPriceVolume\":40}}}";
            return new Response.Builder().request(request).protocol(Protocol.HTTP_1_1)
                .code(route.equals("1h") && failHour.get()?500:200).message("fixture")
                .body(ResponseBody.create(body,MediaType.parse("application/json"))).build();
        }).build();
        return new MarketIngestionService(new Gson(),client);
    }
    @Test public void cachedAggregateKeepsItsOriginalPublicationClock()throws Exception {
        var service=service();service.refresh();var first=service.state();
        assertEquals(shortAt,first.fiveMinuteStartedAt);assertEquals(hourAt,first.hourlyStartedAt);
        service.refresh();var next=service.state();
        assertSame(first.fiveMinute,next.fiveMinute);assertSame(first.hourly,next.hourly);
        assertEquals(first.fiveMinuteStartedAt,next.fiveMinuteStartedAt);assertEquals(first.hourlyStartedAt,next.hourlyStartedAt);
        assertEquals(Integer.valueOf(1),requests.get("5m"));assertEquals(Integer.valueOf(1),requests.get("1h"));
        assertEquals(Integer.valueOf(2),requests.get("latest"));
    }
    @Test public void failedRefreshCannotAttachANewClockToOldDataOrSuppressTheRetry()throws Exception {
        var service=service();service.refresh();var first=service.state();
        var fields=new BoundedPlanningStateTest();fields.set(service,"lastFiveMinute",0L);fields.set(service,"lastHourly",0L);
        failHour.set(true);
        try{service.refresh();fail("failed hourly response expected");}catch(IllegalStateException expected){}
        assertSame(first,service.state());assertEquals(0L,fields.get(service,"lastFiveMinute"));
        failHour.set(false);service.refresh();
        assertEquals(Integer.valueOf(3),requests.get("5m"));assertEquals(Integer.valueOf(3),requests.get("1h"));
        assertEquals(shortAt,service.state().fiveMinuteStartedAt);
    }
    @Test public void clocksMustBeExactAlignedAndCompletedBeforeCapture() {
        assertEquals(0,MarketIngestionService.bucketStart(new JsonObject(),300,now));
        for(JsonElement value:List.of(JsonNull.INSTANCE,new JsonPrimitive("300"),new JsonPrimitive(300.5),
                new JsonPrimitive(shortAt+1),new JsonPrimitive((now/300)*300),new JsonPrimitive(Long.MAX_VALUE))) {
            var root=new JsonObject();root.add("timestamp",value);
            try{MarketIngestionService.bucketStart(root,300,now);fail(value.toString());}
            catch(IllegalArgumentException|ArithmeticException expected){}
        }
    }
    @Test public void marketHttpSnapshotKeepsPublicationEvidence()throws Exception {
        var service=service();service.refresh();
        try(var store=new SqliteStore(folder.newFolder().toPath().resolve("fixture.db"));
                var companion=new CompanionService(new Gson(),store)) {
            var fields=new BoundedPlanningStateTest();
            fields.set(companion,"market",service);
            var feed=companion.marketFeed();assertEquals(shortAt,feed.fiveMinuteStartedAt);assertEquals(hourAt,feed.hourlyStartedAt);
        }
    }
}
