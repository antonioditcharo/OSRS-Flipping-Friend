package com.flippingfriend.companion;

import com.flippingfriend.core.*;
import com.flippingfriend.data.Candle;
import com.google.gson.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

/** Actual loopback handlers, planning, partial accounting and final entry; disposable synthetic data. */
public class PromptGuidanceLoopbackTest {
    @Rule public TemporaryFolder folder=new TemporaryFolder();
    final Gson gson=new Gson();final String account=AccountIdentity.fromProfileKey("prompt-loopback");
    Object get(Object target,String name)throws Exception{var f=target.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(target);}
    void set(Object target,String name,Object value)throws Exception{var f=target.getClass().getDeclaredField(name);f.setAccessible(true);f.set(target,value);}
    String request(ApiServer api,String endpoint,Object body)throws Exception {
        var c=(HttpURLConnection)new URL("http://127.0.0.1:"+api.port()+"/v1/accounts/"+account+"/"+endpoint).openConnection();
        c.setConnectTimeout(2000);c.setReadTimeout(5000);c.setRequestProperty("X-Flipping-Friend-Token","fixture");c.setRequestProperty(AccountIdentity.HEADER,account);
        if(body!=null){c.setRequestMethod("POST");c.setDoOutput(true);try(var out=c.getOutputStream()){out.write(gson.toJson(body).getBytes(StandardCharsets.UTF_8));}}
        try {assertTrue("HTTP "+endpoint,c.getResponseCode()<300);return new String(c.getInputStream().readAllBytes(),StandardCharsets.UTF_8);}
        finally {c.disconnect();}
    }
    @Test public void coherentPartialFillsCanReceiveAnEligibleSuccessorOnTheImmediatePlanRead()throws Exception {
        var root=folder.newFolder().toPath();long at=Instant.now().getEpochSecond();
        try(var legacy=new SqliteStore(root.resolve("legacy.db"));var marketService=new CompanionService(gson,legacy);
            var accounts=new CompanionAccounts(root.resolve("accounts"),marketService);
            var api=new ApiServer("fixture",gson,marketService,accounts,new InetSocketAddress("127.0.0.1",0))) {
            JsonObject latest=new JsonObject(),shortBars=new JsonObject(),hour=new JsonObject();
            Map<Integer,MarketIngestionService.Item> mapping=new HashMap<>();
            var series=(SeriesCache)get(marketService,"series");
            for(int item:new int[]{2361,2362}) {
                mapping.put(item,new MarketIngestionService.Item(item,"Fixture item "+item,30000));
                latest.add(Integer.toString(item),JsonParser.parseString("{\"low\":2000,\"high\":2120,\"lowTime\":"+at+",\"highTime\":"+at+"}"));
                shortBars.add(Integer.toString(item),JsonParser.parseString("{\"avgLowPrice\":2000,\"avgHighPrice\":2120,\"lowPriceVolume\":4000,\"highPriceVolume\":4000}"));
                hour.add(Integer.toString(item),JsonParser.parseString("{\"avgLowPrice\":2000,\"avgHighPrice\":2120,\"lowPriceVolume\":48000,\"highPriceVolume\":48000}"));
                for(String step:List.of("5m","1h")) {
                    List<Candle> bars=new ArrayList<>();int seconds=step.equals("5m")?300:3600,n=step.equals("5m")?300:400,volume=step.equals("5m")?4000:48000;
                    for(int i=0;i<n;i++){int drift=(int)Math.round(40*Math.sin(i*.37));bars.add(new Candle(at-(long)(n-i)*seconds,2120+drift,2000+drift,volume,volume));}
                    series.remember(item,step,bars,Instant.now());
                }
            }
            set(get(marketService,"market"),"state",new MarketIngestionService.MarketState(mapping,latest,shortBars,hour,at));
            api.start();List<Double> durations=new ArrayList<>();
            for(int cycle=0;cycle<12;cycle++) {
                int filled=Math.max(0,cycle-1);long started=System.nanoTime();Map<Integer,String> receipt=new HashMap<>();
                for(int slot=0;slot<8;slot++) {
                    boolean buying=cycle>0 && slot==0;
                    String id="frame-"+cycle+"-"+slot;receipt.put(slot,id);
                    var e=OfferEvent.builder("fixture",at,buying?(filled==10?"BOUGHT":"BUYING"):"EMPTY")
                        .accountId(account).authenticatedObservation(true).eventIdentity(id,"session",buying?"buy":null).slot(slot).sequence(cycle*8+slot+1);
                    if(buying)e.item(2361,"Fixture item 2361").buying(true).price(2000).quantities(10,filled).spent(filled*2000L);
                    request(api,"events/ge-offer",e.build());
                }
                var a=new AccountSnapshot("frame-"+cycle,at,cycle==0?100000000:99980000,cycle==0?0:(10-filled)*2000L,
                    cycle==0?8:7,8,true,false,0,"MODERATE",Map.of(),0,Map.of(),true,30,Set.of(),Set.of(),Set.of(),false,60,List.of())
                    .withAccountId(account).withAutomaticTradeScope().withOfferReadSet(receipt).withObservedHoldings(filled==0?Map.of():Map.of(2361,filled));
                request(api,"events/account-state",a);
                var p=gson.fromJson(request(api,"portfolio/current",null),PortfolioPlan.class);
                assertEquals("Immediate read must return completed work, not require a later heartbeat: "+p.getReason(),PortfolioPlanOutcome.READY,p.getOutcome());
                assertFalse(p.getAllocations().isEmpty());
                var d=gson.fromJson(request(api,"policy/entry?planId="+p.getCorrelationId()+"&rank=1",null),PolicyDecision.class);
                assertEquals(d.getReasonCode(),OfferLifecycleAction.PLACE_BUY,d.getAction());
                durations.add((System.nanoTime()-started)/1e6);
            }
            var ledger=gson.fromJson(request(api,"ledger/current",null),AccountLedgerSnapshot.class);
            assertEquals(10,ledger.getTradingPositions().get(0).getQuantity());assertEquals(20000,ledger.getTradingPositions().get(0).getTotalCost());
            assertEquals(0,ledger.getSales());
            assertEquals(0,legacy.positionEventCount());
            System.out.println("Synthetic authenticated 8-offer POST -> account -> immediate plan -> final PLACE_BUY milliseconds: "+durations);
        }
    }
}
