package com.flippingfriend.companion;

import com.flippingfriend.core.*;
import com.flippingfriend.data.Candle;
import com.google.gson.*;
import java.time.Instant;
import java.util.*;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

/** Real candidate factory, source proof, durable risk and exact bounded forecasts; disposable stores. */
public class ReplacementEntryIntegrationTest {
    @Rule public TemporaryFolder folder=new TemporaryFolder();
    final DurableRiskWindowTest f=new DurableRiskWindowTest();
    final int item=2361;
    List<Candle> bars(String step) {
        int seconds=step.equals("5m")?300:3600,count=step.equals("5m")?300:400;
        var bars=new ArrayList<Candle>();
        for(int i=0;i<count;i++){int drift=(int)Math.round(40*Math.sin(i*.37));
            bars.add(new Candle(f.at-(long)(count-i)*seconds,2120+drift,2000+drift,4000,4000));}
        return bars;
    }
    MarketIngestionService.MarketState market() {
        JsonObject latest=new JsonObject(),five=new JsonObject(),hourly=new JsonObject(),q=new JsonObject();
        q.addProperty("low",2000);q.addProperty("high",2120);q.addProperty("lowTime",f.at-60);q.addProperty("highTime",f.at-60);latest.add(""+item,q);
        JsonObject bar=new JsonObject();bar.addProperty("avgLowPrice",2000);bar.addProperty("avgHighPrice",2120);
        bar.addProperty("lowPriceVolume",4000);bar.addProperty("highPriceVolume",4000);five.add(""+item,bar);
        hourly.add(""+item,bar.deepCopy());
        return new MarketIngestionService.MarketState(Map.of(item,new MarketIngestionService.Item(item,"Adamant bar",30000)),latest,five,hourly,f.at);
    }
    @Test public void aSmallerRemainderGetsNewExactForecastsThroughEveryCurrentEntryGuard()throws Exception {
        try(var store=new SqliteStore(folder.newFolder().toPath().resolve("bounded.db"));var host=new CompanionService(f.gson,store);
                var service=host.forAccountStore(store,f.account)) {
            var account=f.capture(store,100000000);var helper=new BoundedPlanningStateTest();helper.set(service,"account",account);
            helper.set(helper.get(service,"market"),"state",market());
            helper.set(service,"replacementPlanner",new ReplacementEntryPlanner((id,step)->bars(step)));
            var reply=service.replacementEntry(item,7,f.at);
            assertEquals(f.gson.toJson(reply),OfferLifecycleAction.PLACE_BUY,reply.getDecision().getAction());
            var c=reply.getPlan().getAllocations().get(0).getCandidate();assertTrue(c.getQuantity()>0 && c.getQuantity()<=7);
            assertNotNull(c.getBuyForecast());assertTrue(c.getBuyForecast().matches(item,true,c.getBuyPrice(),c.getQuantity()));
            assertNotNull(c.getSellForecast());assertTrue(c.getSellForecast().matches(item,false,c.getSellPrice(),c.getQuantity()));
            assertEquals(c.getBuyForecast().getExpectedTotalSeconds(),c.getBuyHours()*3600,1e-9);
            assertEquals(0,store.positionEventCount());assertEquals(1,store.eventCount(RiskWindow.EVENT_TYPE));
            // A requested profit is a preference; smaller exact replacements retain every financial guard.
            var json=f.gson.toJsonTree(account).getAsJsonObject();json.addProperty("minProfitPerFlip",1000000);
            helper.set(service,"account",f.gson.fromJson(json,AccountSnapshot.class));
            assertEquals(OfferLifecycleAction.PLACE_BUY,service.replacementEntry(item,3,f.at).getDecision().getAction());
            // Nor can the cap rescue a stale/mismatched cash/eight-slot capture.
            json=f.gson.toJsonTree(account).getAsJsonObject();json.addProperty("observedAt",f.at-151);
            helper.set(service,"account",f.gson.fromJson(json,AccountSnapshot.class));
            assertEquals(OfferLifecycleAction.WAIT,service.replacementEntry(item,7,f.at).getDecision().getAction());
            assertEquals(0,store.positionEventCount());assertEquals(1,store.eventCount(RiskWindow.EVENT_TYPE));
        }
    }
}
