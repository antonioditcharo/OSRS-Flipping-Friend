package com.flippingfriend.companion;

import com.flippingfriend.core.*;
import com.google.gson.Gson;
import java.net.*;
import java.util.concurrent.*;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class StartupPublicPreparationTest {
    @Rule public final TemporaryFolder folder=new TemporaryFolder();

    @Test public void slowInitialMarketWorkLeavesHealthAndOrderedDurableOffersAvailable()throws Exception {
        var entered=new CountDownLatch(1);var release=new CountDownLatch(1);var finished=new CountDownLatch(1);
        var refreshFailed=new java.util.concurrent.atomic.AtomicBoolean();
        var scheduler=Executors.newScheduledThreadPool(2);var gson=new Gson();
        var root=folder.newFolder().toPath();String account=AccountIdentity.fromProfileKey("startup-with-slow-public-feed");
        try(var store=new SqliteStore(root.resolve("public.db"));var market=new CompanionService(gson,store);
                var accounts=new CompanionAccounts(root.resolve("accounts"),market);
                var api=new ApiServer("secret",gson,market,accounts,new InetSocketAddress("127.0.0.1",0))) {
            api.start();
            var refresh=CompanionMain.scheduleMarketRefresh(scheduler,()->{
                entered.countDown();try{if(!release.await(10,TimeUnit.SECONDS))refreshFailed.set(true);}
                catch(InterruptedException e){Thread.currentThread().interrupt();refreshFailed.set(true);}
                finally{finished.countDown();}
            });
            assertTrue("First refresh starts immediately in the background",entered.await(3,TimeUnit.SECONDS));
            var health=gson.fromJson(request(api,"health",null,null,200),com.google.gson.JsonObject.class);
            assertEquals("DEGRADED",health.get("status").getAsString());
            long at=java.time.Instant.now().getEpochSecond();
            var offer=OfferEvent.builder("startup",at,"BUYING").accountId(account)
                .eventIdentity("original-startup-observation","session","episode").slot(0).sequence(1)
                .item(561,"Synthetic rune").buying(true).price(100).quantities(4,0)
                .firstSeenAt(at).authenticatedObservation(true).build();
            String payload=gson.toJson(offer);
            request(api,"accounts/"+account+"/events/ge-offer",account,payload,202);
            request(api,"accounts/"+account+"/events/ge-offer",account,payload,202);
            var plan=gson.fromJson(request(api,"accounts/"+account+"/portfolio/current",account,null,200),PortfolioPlan.class);
            assertTrue("No current cash/frame/market proof may be bypassed",plan.getAllocations().isEmpty());
            try(var read=SqliteStore.openReadOnly(root.resolve("accounts").resolve(account).resolve("flipping-friend.db"))) {
                assertEquals(1,read.eventCount("BUYING"));
                assertEquals("episode",read.offerProjections().get(0).getOfferIdentity());
                assertEquals(1,read.offerProjections().get(0).getLastSequence());
            }
            assertEquals(0,store.positionEventCount());
            assertEquals("The public refresh is still blocked after durable acceptance",1,finished.getCount());
            assertFalse(refresh.isDone());release.countDown();
            assertTrue(finished.await(3,TimeUnit.SECONDS));assertFalse(refreshFailed.get());refresh.cancel(false);
        } finally {release.countDown();scheduler.shutdownNow();assertTrue(scheduler.awaitTermination(5,TimeUnit.SECONDS));}
    }

    private String request(ApiServer api,String path,String account,String payload,int expected)throws Exception {
        var headers=new java.util.HashMap<String,String>();headers.put("X-Flipping-Friend-Token","secret");
        if(account!=null)headers.put(AccountIdentity.HEADER,account);
        var response=LoopbackFixtureClient.request("http://127.0.0.1:"+api.port()+"/v1/"+path,headers,payload);
        assertEquals(response.body(),expected,response.statusCode());
        assertEquals(AccountIdentity.PROTOCOL,response.headers().firstValue(AccountIdentity.PROTOCOL_HEADER).orElse(null));
        if(account!=null)assertEquals(account,response.headers().firstValue(AccountIdentity.HEADER).orElse(null));
        return response.body();
    }
}
