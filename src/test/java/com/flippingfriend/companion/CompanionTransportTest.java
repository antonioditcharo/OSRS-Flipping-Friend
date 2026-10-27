package com.flippingfriend.companion;

import com.flippingfriend.core.*;
import com.flippingfriend.data.*;
import com.google.gson.Gson;
import com.sun.net.httpserver.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

/** Real authenticated loopback requests in disposable fixtures; never connects to the live port. */
public class CompanionTransportTest {
    @Rule public TemporaryFolder folder=new TemporaryFolder();
    final Gson gson=new Gson();
    HttpServer server;ExecutorService executor;CompanionClient client;PluginStorage storage;
    OfferEventOutbox outbox;final java.util.concurrent.atomic.AtomicInteger writes=new java.util.concurrent.atomic.AtomicInteger();
    final List<Integer> peers=Collections.synchronizedList(new ArrayList<>());
    @Before public void start()throws Exception {
        storage=TestStorage.rootedAt(folder.newFolder().toPath(),"transport fixture");
        Path credentials=storage.root().resolve("companion/companion.properties");
        Files.createDirectories(credentials.getParent());Files.writeString(credentials,"token=fixture-token\n");
        server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);executor=Executors.newCachedThreadPool();server.setExecutor(executor);
        outbox=new OfferEventOutbox(storage,(f,t)->writes.incrementAndGet());
        client=new CompanionClient(storage,gson,new SuggestionLedger(),outbox,()->0L,
            "http://127.0.0.1:"+server.getAddress().getPort()+"/v1/");
        server.start();
    }
    @After public void stop(){if(server!=null)server.stop(0);if(executor!=null)executor.shutdownNow();}
    String request(String path,String method,String body)throws Exception {
        return request(path,method,body,1000);
    }
    String request(String path,String method,String body,int timeoutMillis)throws Exception {
        var call=CompanionClient.class.getDeclaredMethod("request",String.class,String.class,String.class,int.class,String.class);
        call.setAccessible(true);
        try{return (String)call.invoke(client,path,method,body,timeoutMillis,storage.accountId());}
        catch(java.lang.reflect.InvocationTargetException failure){throw (Exception)failure.getCause();}
    }
    CompanionTransportTotals.Report transportReport()throws Exception {
        var field=CompanionClient.class.getDeclaredField("transportTotals");field.setAccessible(true);
        var epoch=CompanionClient.class.getDeclaredField("transportAccountEpoch");epoch.setAccessible(true);
        return ((CompanionTransportTotals)field.get(client)).snapshot(storage.accountId(),
            ((java.util.concurrent.atomic.AtomicLong)epoch.get(client)).get(),System.nanoTime());
    }
    CompanionTransportTotals.Row transportRow(String group)throws Exception {
        return transportReport().rows.stream().filter(r->r.group.equals(group)).findFirst().orElseThrow();
    }
    void reply(HttpExchange exchange,int code,String body)throws Exception {
        assertEquals("fixture-token",exchange.getRequestHeaders().getFirst("X-Flipping-Friend-Token"));
        assertEquals(storage.accountId(),exchange.getRequestHeaders().getFirst(AccountIdentity.HEADER));
        peers.add(exchange.getRemoteAddress().getPort());
        exchange.getResponseHeaders().set(AccountIdentity.HEADER,storage.accountId());
        exchange.getResponseHeaders().set(AccountIdentity.PROTOCOL_HEADER,AccountIdentity.PROTOCOL);
        byte[] bytes=body.getBytes(StandardCharsets.UTF_8);exchange.sendResponseHeaders(code,bytes.length);
        exchange.getResponseBody().write(bytes);exchange.close();
    }
    @Test public void fullyConsumedScopedResponsesReuseOneConnectionAcrossAnEightSlotReadCycle()throws Exception {
        server.createContext("/v1/accounts/",exchange->{try{reply(exchange,200,"{\"ok\":true}");}catch(Exception e){throw new java.io.IOException(e);}});
        long started=System.nanoTime();
        for(int i=0;i<8;i++)assertEquals("{\"ok\":true}",request("fixture/"+i,"GET",null));
        System.out.println("Eight scoped pooled reads ms="+(System.nanoTime()-started)/1_000_000.0);
        assertEquals(8,peers.size());assertEquals("Successful reads should return their transport to the pool",1,new HashSet<>(peers).size());
    }
    @Test public void accountCapturePublishesInventorySeparatelyFromEquipmentCollectionAndCachedBank()throws Exception {
        var received=new java.util.concurrent.atomic.AtomicReference<AccountSnapshot>();
        server.createContext("/v1/accounts/",exchange->{try {
            received.set(gson.fromJson(new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8),AccountSnapshot.class));
            reply(exchange,200,"{\"ok\":true}");
        }catch(Exception failure){throw new java.io.IOException(failure);}});
        var state=new com.flippingfriend.session.AccountState(true,true,false,100000000,0,0,8,8,0,true,
            Map.of(561,12),Map.of(561,2),Map.of(561,5),0,Map.of(561,1));
        var config=org.mockito.Mockito.mock(com.flippingfriend.FlippingFriendConfig.class);
        client.publishAccount(state,config,0,Map.of(),Map.of(),Set.of(),Set.of(),Set.of(),false,List.of());
        assertNotNull(received.get());assertEquals(storage.accountId(),received.get().getAccountId());
        assertEquals(Map.of(561,7),received.get().getObservedHoldings());assertEquals(Map.of(561,2),received.get().getObservedInventory());
    }
    @Test public void serverConnectionCloseRetainsEveryScopedResponseWithoutReusingAClosedConnection()throws Exception {
        server.createContext("/v1/accounts/",exchange->{try{
            exchange.getResponseHeaders().set("Connection","close");reply(exchange,200,"{\"ok\":true}");
        }catch(Exception e){throw new java.io.IOException(e);}});
        long started=System.nanoTime();
        for(int i=0;i<8;i++)assertEquals("{\"ok\":true}",request("fixture/"+i,"GET",null));
        System.out.println("Eight scoped closed reads ms="+(System.nanoTime()-started)/1_000_000.0);
        assertEquals(8,peers.size());assertEquals(8,new HashSet<>(peers).size());
    }
    void publishEmptyFrame()throws Exception {
        var capture=emptyFrame();
        client.publishAccount(capture.getState(),org.mockito.Mockito.mock(com.flippingfriend.FlippingFriendConfig.class),0,Map.of(),Map.of(),Set.of(),Set.of(),Set.of(),false,List.of(),List.of(),storage.accountId(),client.capturePlanningObservation(capture));
    }
    private com.flippingfriend.session.PlanningAccountObservation emptyFrame() {
        var slots=new net.runelite.api.GrandExchangeOffer[8];
        for(int i=0;i<8;i++){slots[i]=org.mockito.Mockito.mock(net.runelite.api.GrandExchangeOffer.class);org.mockito.Mockito.when(slots[i].getState()).thenReturn(net.runelite.api.GrandExchangeOfferState.EMPTY);}
        var state=new com.flippingfriend.session.AccountState(true,true,false,100000000,0,0,8,8,0,false,Map.of(),Map.of(),Map.of());
        var capture=com.flippingfriend.session.PlanningAccountObservation.capture(storage.accountId(),state,slots,List.of(),java.time.Instant.now().getEpochSecond());
        return capture;
    }
    @Test public void aNewerUnchangedCallbackCannotBeOvertakenByAQueuedAccountFrame()throws Exception {
        var copied=emptyFrame();var captured=client.capturePlanningObservation(copied);
        assertNotNull(captured);assertEquals(8,outbox.pending().size());
        client.captureObservedOfferCleared(7,storage.accountId(),copied.getObservedAt()+1);
        assertEquals(9,outbox.pending().size());
        var last=outbox.pending().get(8);assertEquals(9,last.getSequence());
        client.publishAccount(copied.getState(),org.mockito.Mockito.mock(com.flippingfriend.FlippingFriendConfig.class),0,Map.of(),Map.of(),Set.of(),Set.of(),Set.of(),false,List.of(),List.of(),storage.accountId(),captured);
        assertEquals("Worker must never append a delayed frame",9,outbox.pending().size());
        assertEquals(last.getEventId(),outbox.pending().get(8).getEventId());assertTrue(peers.isEmpty());
        var restart=new OfferEventOutbox(storage);assertEquals(9,restart.pending().size());
        assertEquals(captured.getReceipt().get(7),restart.pending().get(7).getEventId());
    }
    @Test public void anUncommittedFrameCannotBeAppendedFromTheWorker()throws Exception {
        var copied=emptyFrame();
        client.publishAccount(copied.getState(),org.mockito.Mockito.mock(com.flippingfriend.FlippingFriendConfig.class),0,Map.of(),Map.of(),Set.of(),Set.of(),Set.of(),false,List.of(),List.of(),storage.accountId(),copied);
        assertTrue(outbox.pending().isEmpty());assertTrue(peers.isEmpty());
    }
    @Test public void unavailableHealthCanExplainItsRefusalWithoutAuthorizingAnEntry()throws Exception {
        long now=java.time.Instant.now().getEpochSecond();String reason="New buys paused: trade observation scope is unresolved.";
        server.createContext("/v1/health",exchange->{try{reply(exchange,200,gson.toJson(new CompanionHealth("DEGRADED","Recorded source issue",now,0,now,false)));}
            catch(Exception e){throw new java.io.IOException(e);}});
        server.createContext("/v1/accounts/",exchange->{try {
            String path=exchange.getRequestURI().getPath();Object body;
            if(path.endsWith("health"))body=new CompanionHealth("DEGRADED","Recorded source issue",now,0,now,false);
            else if(path.endsWith("ledger/current"))body=new AccountLedgerSnapshot(storage.accountId(),now,0,0,0,0,0,0,0,0,List.of());
            else body=PortfolioPlan.unavailable("refused",PortfolioPlanOutcome.COMPANION_STATE_UNAVAILABLE,reason,now);
            reply(exchange,200,gson.toJson(body));
        }catch(Exception e){throw new java.io.IOException(e);}});
        assertNull(client.refreshPlan());assertNull(client.lastPlan());
        var shown=client.entryUnavailableSuggestion();assertEquals(com.flippingfriend.model.SuggestionType.WAIT,shown.getType());assertEquals(reason,shown.getDetail());
    }
    @Test public void eightActualPostAcknowledgementsPrecedeTheAccountReadSetWithOnlyTwoOutboxReplacements()throws Exception {
        var accepted=new LinkedHashMap<Integer,String>();var snapshots=new java.util.concurrent.atomic.AtomicInteger();
        server.createContext("/v1/accounts/",exchange->{try {
            assertEquals("POST",exchange.getRequestMethod());String body=new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8);
            if(exchange.getRequestURI().getPath().endsWith("events/ge-offer")) {
                var event=gson.fromJson(body,OfferEvent.class);assertEquals(8,outbox.pending().size());
                assertEquals("EMPTY",event.getEventType());
                assertEquals(accepted.size()+1,event.getSequence());assertEquals(storage.accountId(),event.getAccountId());
                assertTrue(event.isAuthenticatedObservation());accepted.put(event.getSlot(),event.getEventId());
                reply(exchange,202,OfferReplayTailTest.ack(event));
            }else {
                var account=gson.fromJson(body,AccountSnapshot.class);assertTrue(outbox.pending().isEmpty());
                assertEquals(8,accepted.size());assertEquals(accepted,account.getOfferReadSet());snapshots.incrementAndGet();reply(exchange,202,"{\"accepted\":true}");
            }
        }catch(Exception e){throw new java.io.IOException(e);}});
        client.resumeOfferReplay();long start=System.nanoTime();publishEmptyFrame();
        System.out.println("Eight-slot durable publication ms="+(System.nanoTime()-start)/1_000_000.0);
        assertEquals(8,accepted.size());assertEquals(1,snapshots.get());assertEquals(2,writes.get());assertTrue(outbox.pending().isEmpty());
    }
    @Test public void aWrongWireAcknowledgementCannotPublishTheAccountOrDiscardUnacknowledgedSources()throws Exception {
        var requests=new java.util.concurrent.atomic.AtomicInteger();var snapshots=new java.util.concurrent.atomic.AtomicInteger();
        server.createContext("/v1/accounts/",exchange->{try {
            String body=new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8);
            if(exchange.getRequestURI().getPath().endsWith("events/ge-offer")) {
                var event=gson.fromJson(body,OfferEvent.class);int n=requests.incrementAndGet();reply(exchange,202,n==3?"{}":OfferReplayTailTest.ack(event));
            }else {snapshots.incrementAndGet();reply(exchange,202,"{}");}
        }catch(Exception e){throw new java.io.IOException(e);}});
        client.resumeOfferReplay();publishEmptyFrame();
        assertEquals(3,requests.get());assertEquals(0,snapshots.get());assertEquals(2,writes.get());
        assertEquals(6,new OfferEventOutbox(storage).pending().size());assertEquals(3,outbox.pending().get(0).getSequence());
        var transport=transportRow("OFFERS");assertEquals(3,transport.attempts);assertEquals(3,transport.bodyReadAndScoped2xx);
        assertTrue("Transport success explicitly grants no durable financial ACK",transportReport().semantics.contains("not durable ACK"));
    }
    @Test public void aColdCanonicalWriteLongerThanTheQuoteBudgetCanAcknowledgeTheOriginalFrame()throws Exception {
        var snapshots=new java.util.concurrent.atomic.AtomicInteger();
        server.createContext("/v1/accounts/",exchange->{try {
            String body=new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8);
            if(exchange.getRequestURI().getPath().endsWith("events/ge-offer")) {
                var event=gson.fromJson(body,OfferEvent.class);if(event.getSequence()==1)Thread.sleep(1100);
                reply(exchange,202,OfferReplayTailTest.ack(event));
            }else{snapshots.incrementAndGet();reply(exchange,202,"{\"accepted\":true}");}
        }catch(Exception e){throw new java.io.IOException(e);}});
        client.resumeOfferReplay();publishEmptyFrame();assertTrue(outbox.pending().isEmpty());assertEquals(1,snapshots.get());
    }
    @Test public void failedHttpResponseRetainsAndRetriesTheExactDurableSources()throws Exception {
        var unavailable=new java.util.concurrent.atomic.AtomicBoolean(true);var original=new ArrayList<String>();
        var received=new ArrayList<String>();var snapshots=new java.util.concurrent.atomic.AtomicInteger();
        server.createContext("/v1/accounts/",exchange->{try {
            String body=new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8);
            if(exchange.getRequestURI().getPath().endsWith("events/ge-offer")) {
                received.add(body);var event=gson.fromJson(body,OfferEvent.class);
                reply(exchange,unavailable.get()?503:202,unavailable.get()?"{\"error\":\"fixture unavailable\"}":OfferReplayTailTest.ack(event));
            }else{snapshots.incrementAndGet();reply(exchange,202,"{\"accepted\":true}");}
        }catch(Exception failure){throw new java.io.IOException(failure);}});
        var capture=client.capturePlanningObservation(emptyFrame());
        for(var event:outbox.pending())original.add(gson.toJson(event));
        client.resumeOfferReplay();
        client.publishAccount(capture.getState(),org.mockito.Mockito.mock(com.flippingfriend.FlippingFriendConfig.class),0,
            Map.of(),Map.of(),Set.of(),Set.of(),Set.of(),false,List.of(),List.of(),storage.accountId(),capture);
        assertEquals(8,outbox.pending().size());assertEquals(0,snapshots.get());assertEquals(original.get(0),received.get(0));
        unavailable.set(false);
        client.publishAccount(capture.getState(),org.mockito.Mockito.mock(com.flippingfriend.FlippingFriendConfig.class),0,
            Map.of(),Map.of(),Set.of(),Set.of(),Set.of(),false,List.of(),List.of(),storage.accountId(),capture);
        assertTrue(outbox.pending().isEmpty());assertEquals(1,snapshots.get());
        assertEquals("The failed request must retry its original financial identity and payload",original,received.subList(1,received.size()));
        var transport=transportRow("OFFERS");assertEquals(9,transport.attempts);assertEquals(8,transport.bodyReadAndScoped2xx);assertEquals(1,transport.httpFailures);
    }
    @Test public void transportTotalsIncludeScopedSuccessBadScopeHttpFailureAndConnectionFailure()throws Exception {
        var count=new java.util.concurrent.atomic.AtomicInteger();
        server.createContext("/v1/accounts/",exchange->{try {
            int current=count.incrementAndGet();
            if(current==2) {
                byte[] bytes="{}".getBytes(StandardCharsets.UTF_8);exchange.sendResponseHeaders(200,bytes.length);
                exchange.getResponseBody().write(bytes);exchange.close();
            }else reply(exchange,current==3?503:200,"{}");
        }catch(Exception error){throw new java.io.IOException(error);}});
        assertEquals("{}",request("policy/fixture","GET",null));
        try{request("policy/fixture","GET",null);fail("Missing scope must still reject");}catch(IllegalStateException expected){}
        try{request("policy/fixture","GET",null);fail("503 must still reject");}catch(IllegalStateException expected){}
        server.stop(0);server=null;
        try{request("policy/fixture","GET",null);fail("Stopped service must still fail");}catch(java.io.IOException expected){}
        var row=transportRow("POLICY");assertEquals(4,row.attempts);assertEquals(1,row.bodyReadAndScoped2xx);
        assertEquals(1,row.failed2xx);assertEquals(1,row.httpFailures);assertEquals(1,row.noResponse);
        long timings=0;for(long n:row.latencyHistogram)timings+=n;assertEquals(4,timings);assertEquals(0,row.invalidTiming);
    }
    @Test public void failedPreflightIsNotCountedAsAnAttemptThatEnteredHttpTransport()throws Exception {
        Files.delete(storage.root().resolve("companion/companion.properties"));
        try{request("policy/fixture","GET",null);fail("Missing credentials must reject");}catch(IllegalStateException expected){}
        assertNull("No account transport window starts before credential preflight passes",transportReport());
    }
    @Test public void lateScopedHttpReplyCannotPolluteTheNewAccountOrItsReturnWindow()throws Exception {
        Path root=storage.root();String one=storage.accountId(),two=AccountIdentity.fromProfileKey("second fixture");
        var account=new java.util.concurrent.atomic.AtomicReference<>(one);
        storage=org.mockito.Mockito.mock(PluginStorage.class);
        org.mockito.Mockito.when(storage.accountId()).thenAnswer(call->account.get());
        org.mockito.Mockito.when(storage.root()).thenReturn(root);org.mockito.Mockito.when(storage.gson()).thenReturn(gson);
        org.mockito.Mockito.when(storage.accountDir(org.mockito.Mockito.anyString())).thenAnswer(call->root.resolve(call.getArgument(0,String.class)));
        client=new CompanionClient(storage,gson,new SuggestionLedger(),outbox,()->0L,
            "http://127.0.0.1:"+server.getAddress().getPort()+"/v1/");
        var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
        server.createContext("/v1/accounts/",exchange->{try {
            String scope=exchange.getRequestHeaders().getFirst(AccountIdentity.HEADER);
            if(exchange.getRequestURI().getPath().endsWith("delayed")) {entered.countDown();if(!release.await(10,TimeUnit.SECONDS))throw new java.io.IOException("fixture timeout");}
            exchange.getResponseHeaders().set(AccountIdentity.HEADER,scope);
            exchange.getResponseHeaders().set(AccountIdentity.PROTOCOL_HEADER,AccountIdentity.PROTOCOL);
            byte[] bytes="{}".getBytes(StandardCharsets.UTF_8);exchange.sendResponseHeaders(200,bytes.length);
            exchange.getResponseBody().write(bytes);exchange.close();
        }catch(Exception error){throw new java.io.IOException(error);}});
        var worker=Executors.newSingleThreadExecutor();
        try {
            Future<?> late=worker.submit(()->{try{request("ledger/delayed","GET",null,5000);fail("Old account reply must reject");}
                catch(IllegalStateException expected){}catch(Exception unexpected){throw new RuntimeException(unexpected);}});
            assertTrue(entered.await(5,TimeUnit.SECONDS));account.set(two);assertEquals("{}",request("ledger/current","GET",null));
            release.countDown();late.get(5,TimeUnit.SECONDS);
            var second=transportReport();assertEquals(two,second.account);assertEquals(1,transportRow("LEDGER").attempts);
            assertEquals(0,transportRow("LEDGER").failed2xx);
            account.set(one);assertEquals("{}",request("ledger/current","GET",null));
            var returned=transportReport();assertEquals(one,returned.account);assertNotEquals(second.window,returned.window);
            assertEquals(2,returned.generation);assertEquals(1,transportRow("LEDGER").attempts);
        }finally{release.countDown();worker.shutdownNow();}
    }
}
