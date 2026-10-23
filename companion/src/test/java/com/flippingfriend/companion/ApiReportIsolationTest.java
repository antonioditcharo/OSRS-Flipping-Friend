package com.flippingfriend.companion;

import com.flippingfriend.core.AccountIdentity;
import com.flippingfriend.core.OfferEvent;
import com.google.gson.Gson;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.concurrent.*;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class ApiReportIsolationTest {
    @Rule public TemporaryFolder folder=new TemporaryFolder();
    private ThreadPoolExecutor reports(ApiServer api)throws Exception {
        var field=ApiServer.class.getDeclaredField("reportExecutor");field.setAccessible(true);return (ThreadPoolExecutor)field.get(api);
    }
    private int request(ApiServer api,String endpoint,String account,String token,String payload)throws Exception {
        var connection=(HttpURLConnection)new URL("http://127.0.0.1:"+api.port()+"/v1/"+endpoint).openConnection();
        connection.setConnectTimeout(2000);connection.setReadTimeout(4000);connection.setRequestProperty("X-Flipping-Friend-Token",token);
        if(account!=null)connection.setRequestProperty(AccountIdentity.HEADER,account);
        try {
            if(payload!=null){connection.setRequestMethod("POST");connection.setDoOutput(true);try(var output=connection.getOutputStream()){output.write(payload.getBytes(StandardCharsets.UTF_8));}}
            int status=connection.getResponseCode();assertEquals(AccountIdentity.PROTOCOL,connection.getHeaderField(AccountIdentity.PROTOCOL_HEADER));
            if(status==200 && endpoint.startsWith("accounts/"))assertEquals(account,connection.getHeaderField(AccountIdentity.HEADER));
            try(var input=status>=400?connection.getErrorStream():connection.getInputStream()){if(input!=null)input.readAllBytes();}
            return status;
        }finally{connection.disconnect();}
    }
    @Test public void blockedReportWorkerAndQueuedReportDoNotDelayRealOfferAcknowledgementOrEntryAuthority()throws Exception {
        var root=folder.newFolder().toPath();var json=new Gson();String account=AccountIdentity.fromProfileKey("report-isolation");
        var entered=new CountDownLatch(1);var release=new CountDownLatch(1);var client=Executors.newSingleThreadExecutor();
        try(var store=new SqliteStore(root.resolve("legacy.db"));var market=new CompanionService(json,store);
            var accounts=new CompanionAccounts(root.resolve("accounts"),market);
            var api=new ApiServer("secret",json,market,accounts,new InetSocketAddress("127.0.0.1",0))) {
            api.start();accounts.forAccount(account);
            reports(api).execute(()->{entered.countDown();try{release.await();}catch(InterruptedException interrupted){Thread.currentThread().interrupt();}});
            assertTrue(entered.await(5,TimeUnit.SECONDS));
            var read=client.submit(()->request(api,"accounts/"+account+"/performance/current",account,"secret",null));
            long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
            while(reports(api).getQueue().isEmpty() && System.nanoTime()<until)Thread.sleep(5);
            assertEquals(1,reports(api).getQueue().size());assertFalse(read.isDone());
            long at=Instant.now().getEpochSecond()-5;
            var offer=OfferEvent.builder("fixture",at,"BUYING").accountId(account).authenticatedObservation(true)
                .eventIdentity("open-once","session","offer").slot(0).item(561,"Nature rune").buying(true).price(100)
                .quantities(4,0).sequence(1).firstSeenAt(at).build();
            assertEquals(202,request(api,"accounts/"+account+"/events/ge-offer",account,"secret",json.toJson(offer)));
            assertEquals(200,request(api,"accounts/"+account+"/policy/entry?planId=none&rank=1",account,"secret",null));
            assertEquals(200,request(api,"health",null,"secret",null));assertFalse(read.isDone());
            release.countDown();assertEquals(200,read.get(10,TimeUnit.SECONDS).intValue());
            assertEquals(0,store.positionEventCount());
        }finally{release.countDown();client.shutdownNow();}
    }
    @Test public void reportBackpressureIsBoundedAndDoesNotAcknowledgeOrDivertMutations()throws Exception {
        var root=folder.newFolder().toPath();var json=new Gson();String account=AccountIdentity.fromProfileKey("report-overload");
        var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
        try(var store=new SqliteStore(root.resolve("legacy.db"));var market=new CompanionService(json,store);
            var accounts=new CompanionAccounts(root.resolve("accounts"),market);
            var api=new ApiServer("secret",json,market,accounts,new InetSocketAddress("127.0.0.1",0))) {
            api.start();accounts.forAccount(account);var reports=reports(api);
            reports.execute(()->{entered.countDown();try{release.await();}catch(InterruptedException interrupted){Thread.currentThread().interrupt();}});
            assertTrue(entered.await(5,TimeUnit.SECONDS));int capacity=reports.getQueue().remainingCapacity();
            assertTrue(capacity>0 && capacity<=4);for(int i=0;i<capacity;i++)reports.execute(()->{});
            for(String route:new String[]{"performance/current","trade-review/current","learning/current"})
                assertEquals(503,request(api,"accounts/"+account+"/"+route,account,"secret",null));
            assertEquals(401,request(api,"accounts/"+account+"/performance/current",account,"wrong",null));
            assertEquals(405,request(api,"accounts/"+account+"/performance/current",account,"secret","{}"));
            assertEquals(400,request(api,"accounts/"+account+"/trade-review",account,"secret","{}"));
            long at=Instant.now().getEpochSecond()-5;
            var open=OfferEvent.builder("fixture",at,"BUYING").accountId(account).authenticatedObservation(true)
                .eventIdentity("retry-once","session","offer").slot(0).item(561,"Nature rune").buying(true).price(100)
                .quantities(4,0).sequence(1).firstSeenAt(at).build();
            var filled=OfferEvent.builder("fixture",at+1,"BOUGHT").accountId(account).authenticatedObservation(true)
                .eventIdentity("fill-once","session","offer").slot(0).item(561,"Nature rune").buying(true).price(100)
                .quantities(4,4).spent(400).sequence(2).firstSeenAt(at).build();
            for(var event:new OfferEvent[]{open,open,filled,filled})
                assertEquals(202,request(api,"accounts/"+account+"/events/ge-offer",account,"secret",json.toJson(event)));
            var ledger=accounts.forAccount(account).ledger();assertEquals(4,ledger.getPositions().get(0).getQuantity());
            assertEquals(400,ledger.getPositions().get(0).getTotalCost());assertEquals(0,store.positionEventCount());
            release.countDown();
        }finally{release.countDown();}
    }
}
