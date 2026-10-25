package com.flippingfriend.companion;

import com.flippingfriend.core.*;
import com.google.gson.Gson;
import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class PilotGatewayAvailabilityTest {
    @Test public void demandOnlyCaptureUsesCanonicalStockAndWorkingOrdersWithoutEnrollingPersonalInventory()throws Exception {
        long now=System.currentTimeMillis()/1000;Gson gson=new Gson();String id=AccountIdentity.fromProfileKey("stock");
        var snapshot=new AccountSnapshot("full-board",now,0,10000,0,8,true,false,0).withAccountId(id)
            .withObservedHoldings(Map.of(562,20000));var read=new HashMap<Integer,String>();for(int i=0;i<8;i++)read.put(i,"source-"+i);snapshot=snapshot.withOfferReadSet(read);
        var stock=List.of(PositionProjection.ready(561,"Canonical",30,3000,now-100,now,"buy","episode"));
        var buy=OfferEvent.builder("test",now,"BUYING").item(561,"Canonical").buying(true).slot(0).quantities(10,6).build();
        var sell=OfferEvent.builder("test",now,"SELLING").item(561,"Canonical").buying(false).slot(1).quantities(20,0).build();
        var captured=new CompletableFuture<PilotProtocol.AllocationRequest>();var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),8);
        server.createContext("/service/v1/allocations",exchange->{
            var input=gson.fromJson(new String(exchange.getRequestBody().readAllBytes(),java.nio.charset.StandardCharsets.UTF_8),PilotProtocol.AllocationRequest.class);
            captured.complete(input);exchange.getResponseHeaders().set("X-Flipping-Friend-Pilot-Protocol",PilotProtocol.VERSION);
            byte[] bytes="{}".getBytes(java.nio.charset.StandardCharsets.UTF_8);exchange.sendResponseHeaders(503,bytes.length);exchange.getResponseBody().write(bytes);exchange.close();
        });server.start();var config=new PilotGateway.Config();config.enabled=true;config.installationId="fixture";config.token="x".repeat(40);config.endpoint="http://127.0.0.1:"+server.getAddress().getPort();
        try(var gateway=new PilotGateway(config)) {
            gateway.observeDemand(snapshot,List.of(buy,sell),0,now,stock);var input=captured.get(3,TimeUnit.SECONDS);
            input.validate(now);assertTrue(input.candidates.isEmpty());assertEquals(1,input.demand.size());
            assertEquals(561,input.demand.get(0).itemId);assertEquals(4,input.demand.get(0).buying);assertEquals(34,input.demand.get(0).selling);
        }finally{server.stop(0);}
    }
    @Test public void slowMalformedAndUnaffordableRemoteAdviceCannotHideLocalCandidate()throws Exception {
        Gson gson=new Gson();long now=System.currentTimeMillis()/1000;String id=AccountIdentity.fromProfileKey("availability");
        var account=new AccountSnapshot("test",now,10000,0,1,8,true,false,0).withAccountId(id);
        var frames=new HashMap<Integer,String>();for(int i=0;i<8;i++)frames.put(i,"source-"+i);account=account.withOfferReadSet(frames);
        var safe=new PortfolioCandidate(561,"Safe","",100,100,100,120,120,120,10,1000,180,50,1,.95,.95,.1,.1,1,now+90);
        var tooLarge=new PortfolioCandidate(562,"Too large","",100,100,100,120,120,120,200,1000,3600,1000,1,.95,.95,.1,.1,1,now+90);
        var limits=new PortfolioConstraints(1,10000,2000,10000,10000);var candidates=List.of(safe,tooLarge);
        var baseline=new PortfolioOptimizer().optimizePreferred(candidates,limits,"test",now);assertEquals(561,baseline.getAllocations().get(0).getCandidate().getItemId());
        var received=new AtomicReference<PilotProtocol.AllocationRequest>();var allow=new CountDownLatch(1);var refreshed=new CountDownLatch(1);
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),8);
        server.createContext("/service/v1/allocations",exchange->{
            var input=gson.fromJson(new String(exchange.getRequestBody().readAllBytes(),java.nio.charset.StandardCharsets.UTF_8),PilotProtocol.AllocationRequest.class);received.set(input);
            try{allow.await(2,TimeUnit.SECONDS);}catch(InterruptedException ignored){}
            var hint=new PilotProtocol.AllocationHint();hint.accountId=input.accountId;hint.installationId=input.installationId;
            hint.context=input.context;hint.generation=input.generation;hint.adviceId="bad-but-scoped";hint.revision=1;hint.expiresAt=now+15;
            hint.itemId=562;hint.buyPrice=100;hint.sellPrice=120;hint.quantity=200;
            byte[] bytes=gson.toJson(hint).getBytes(java.nio.charset.StandardCharsets.UTF_8);exchange.getResponseHeaders().set("X-Flipping-Friend-Pilot-Protocol",PilotProtocol.VERSION);
            exchange.sendResponseHeaders(200,bytes.length);exchange.getResponseBody().write(bytes);exchange.close();
        });server.start();
        var config=new PilotGateway.Config();config.enabled=true;config.shadow=false;config.recommendations=true;config.token="x".repeat(40);config.installationId="fixture";config.endpoint="http://127.0.0.1:"+server.getAddress().getPort();
        try(var gateway=new PilotGateway(config)) {
            long began=System.nanoTime();assertSame(candidates,gateway.choose(account,candidates,List.of(),now,now,refreshed::countDown,limits,baseline,List.of()));
            assertTrue("network blocked foreground",System.nanoTime()-began<TimeUnit.MILLISECONDS.toNanos(100));
            allow.countDown();assertTrue(refreshed.await(3,TimeUnit.SECONDS));assertNotNull(received.get());
            assertEquals(1,received.get().candidates.size());assertEquals(561,received.get().candidates.get(0).getItemId());
            assertSame(candidates,gateway.choose(account,candidates,List.of(),now,now,()->{},limits,baseline,List.of()));
            assertEquals(561,baseline.getAllocations().get(0).getCandidate().getItemId());
            assertEquals("HOSTED_COMPATIBLE",gateway.status(id).get("mode"));
        }finally{allow.countDown();server.stop(0);}
        config.endpoint="http://127.0.0.1:"+server.getAddress().getPort();var unavailable=new CountDownLatch(1);
        try(var gateway=new PilotGateway(config)) {
            assertSame(candidates,gateway.choose(account,candidates,List.of(),now,now,unavailable::countDown,limits,baseline,List.of()));
            assertTrue(unavailable.await(3,TimeUnit.SECONDS));assertEquals("LOCAL_FALLBACK",gateway.status(id).get("mode"));
            assertSame(candidates,gateway.choose(account,candidates,List.of(),now,now,()->{},limits,baseline,List.of()));
        }
    }
}
