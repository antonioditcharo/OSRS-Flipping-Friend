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
        var call=CompanionClient.class.getDeclaredMethod("request",String.class,String.class,String.class,int.class,String.class);
        call.setAccessible(true);
        try{return (String)call.invoke(client,path,method,body,1000,storage.accountId());}
        catch(java.lang.reflect.InvocationTargetException failure){throw (Exception)failure.getCause();}
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
        var slots=new net.runelite.api.GrandExchangeOffer[8];
        for(int i=0;i<8;i++){slots[i]=org.mockito.Mockito.mock(net.runelite.api.GrandExchangeOffer.class);org.mockito.Mockito.when(slots[i].getState()).thenReturn(net.runelite.api.GrandExchangeOfferState.EMPTY);}
        var state=new com.flippingfriend.session.AccountState(true,true,false,100000000,0,0,8,8,0,false,Map.of(),Map.of(),Map.of());
        var capture=com.flippingfriend.session.PlanningAccountObservation.capture(storage.accountId(),state,slots,List.of(),java.time.Instant.now().getEpochSecond());
        client.publishAccount(state,org.mockito.Mockito.mock(com.flippingfriend.FlippingFriendConfig.class),0,Map.of(),Map.of(),Set.of(),Set.of(),Set.of(),false,List.of(),List.of(),storage.accountId(),capture);
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
    }
}
