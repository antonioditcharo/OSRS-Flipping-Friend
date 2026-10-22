package com.flippingfriend.companion;

import com.flippingfriend.core.*;
import com.flippingfriend.data.*;
import com.flippingfriend.model.*;
import com.flippingfriend.session.TrackedOffer;
import com.google.gson.Gson;
import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

/** Exercises the actual HTTP, copied offer identity and foreground sale boundary together. */
public class SaleGuidanceDeliveryTest {
    @Rule public TemporaryFolder folder=new TemporaryFolder();
    final Gson json=new Gson();
    HttpServer server;
    CompanionClient client;
    OfferEventOutbox outbox;
    PluginStorage storage;
    final AtomicReference<Map<String,String>> requested=new AtomicReference<>();
    long delay;
    Runnable duringRequest=()->{};
    String replySource;
    String refusal;

    @Before public void setup()throws Exception {
        storage=TestStorage.rootedAt(folder.newFolder().toPath(),"sale-delivery");
        Files.createDirectories(storage.root().resolve("companion"));
        Files.writeString(storage.root().resolve("companion/companion.properties"),"token=fixture-only\n");
        outbox=new OfferEventOutbox(storage);
        server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/v1/accounts/",exchange->{
            try {
                assertEquals("GET",exchange.getRequestMethod());
                assertEquals(storage.accountId(),exchange.getRequestHeaders().getFirst(AccountIdentity.HEADER));
                Map<String,String> q=new HashMap<>();
                for(String part:exchange.getRequestURI().getRawQuery().split("&")) {
                    String[] pair=part.split("=",2);q.put(pair[0],URLDecoder.decode(pair[1],StandardCharsets.UTF_8));
                }
                requested.set(q);duringRequest.run();
                Thread.sleep(delay);
                long now=Instant.now().getEpochSecond();
                boolean entry=exchange.getRequestURI().getPath().endsWith("policy/entry");
                int slot=entry?-1:Integer.parseInt(q.get("slot"));
                String source=replySource==null?q.get("source"):replySource;
                String key=q.get("item")+":"+q.get("price")+":"+q.get("quantity")+":"+source+":"+q.get("cut")+":"+slot
                    +":"+(slot<0?q.getOrDefault("repriceSource",""):"");
                var decision=entry?PolicyDecision.action("1","companion-entry-1","entry",PolicyDecisionType.ENTRY,OfferLifecycleAction.PLACE_BUY,
                    "ENTRY_CANDIDATE_SELECTED",now,now-1,"561:100:120:3",q.get("planId"))
                    :PolicyDecision.action("1","companion-sale-6","decision",slot<0?PolicyDecisionType.EXIT:PolicyDecisionType.SELL_MAINTENANCE,
                    slot<0?OfferLifecycleAction.PLACE_SELL:OfferLifecycleAction.CANCEL_SELL,"SALE_AUTHORIZED",now,now-1,key,source);
                if(refusal!=null)decision=PolicyDecision.abstain("1","companion-sale-6","refused",
                    slot<0?PolicyDecisionType.EXIT:PolicyDecisionType.SELL_MAINTENANCE,
                    PolicyAbstentionReason.ACCOUNT_STATE_INCONSISTENT,refusal,now,now-1,key,source);
                byte[] body=json.toJson(decision).getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set(AccountIdentity.HEADER,storage.accountId());
                exchange.getResponseHeaders().set(AccountIdentity.PROTOCOL_HEADER,AccountIdentity.PROTOCOL);
                exchange.sendResponseHeaders(200,body.length);exchange.getResponseBody().write(body);
            }catch(Exception ignored){ /* A timed-out or withdrawn client may close the socket. */ }
            finally{exchange.close();}
        });
        server.start();
        client=new CompanionClient(storage,json,new SuggestionLedger(),outbox,System::currentTimeMillis,
            "http://127.0.0.1:"+server.getAddress().getPort()+"/v1/");
        client.resumeOfferReplay();
    }
    @After public void stop(){if(server!=null)server.stop(0);}
    void ledger(boolean acquired) {
        long now=Instant.now().getEpochSecond();
        var p=PositionProjection.ready(561,"Nature rune",3,301,now-60,"acquisition","buy-episode");
        var snapshot=new AccountLedgerSnapshot(storage.accountId(),now,0,0,0,0,0,0,0,0,List.of())
            .withTradeEvidence(acquired?List.of(p):List.of(),List.of(),Map.of());
        assertTrue(client.acceptLedger(snapshot,storage.accountId()));
    }
    Suggestion sale(boolean maintenance) {
        return Suggestion.builder(maintenance?SuggestionType.MODIFY_SELL:SuggestionType.SELL)
            .item(561,"Nature rune").quantity(3).price(120).slot(maintenance?2:-1).profitKnown(true).build();
    }
    OfferEvent publishSale(int filled)throws Exception {
        long now=Instant.now().getEpochSecond();
        var offer=new TrackedOffer(2,561,false,150,3,now-60);offer.setState("SELLING");offer.setQuantityFilled(filled);
        var method=CompanionClient.class.getDeclaredMethod("publishOffer",TrackedOffer.class,String.class,boolean.class,long.class,boolean.class);
        method.setAccessible(true);
        return (OfferEvent)method.invoke(client,offer,storage.accountId(),true,now,false);
    }
    void acknowledge()throws Exception {
        for(var event:outbox.pending())assertTrue(outbox.acknowledge(event.getEventId(),event.getSessionId(),event.getSequence()));
    }
    @Test public void acquiredSaleWaitsForRealisticLedgerReadWithoutLosingExactAuthorization()throws Exception {
        ledger(true);delay=1100;
        Suggestion proposed=sale(false);
        assertSame("A valid response beyond the old 750ms budget must reach the player",proposed,client.authorizeSale(proposed));
        assertEquals("acquisition",requested.get().get("source"));
    }
    @Test public void entryReadAlsoWaitsForLedgerAuthorityAndKeepsItsPlanIdentity()throws Exception {
        delay=1100;
        var decision=client.fetchEntryDecision("current-plan",1);
        assertNotNull("A slow valid entry read must not be reported as an unavailable companion",decision);
        assertEquals(OfferLifecycleAction.PLACE_BUY,decision.getAction());assertEquals("current-plan",decision.getRecommendationId());
        assertEquals("1",requested.get().get("rank"));
    }
    @Test public void aVerifiedReductionBindsItsExactSourceAndTheRelistSendsThatIdentity()throws Exception {
        var offer=publishSale(0);acknowledge();ledger(true);
        var modify=sale(true);assertSame(modify,client.authorizeSale(modify));
        assertEquals(offer.getEventId(),modify.getSaleRepriceSource());
        client.publishOfferCleared(2);acknowledge();ledger(true);
        var relist=Suggestion.builder(SuggestionType.SELL).item(561,"Nature rune").quantity(3).price(120)
            .profitKnown(true).saleRepriceSource(modify.getSaleRepriceSource()).build();
        assertSame(relist,client.authorizeSale(relist));
        assertEquals(offer.getEventId(),requested.get().get("repriceSource"));
        assertEquals("acquisition",requested.get().get("source"));
    }
    @Test public void authenticatedPersonalSellOfferRetainsSourceForMaintenance()throws Exception {
        var offer=publishSale(0);acknowledge();ledger(false);
        Suggestion proposed=sale(true);
        assertSame("An observed sell needs its own canonical identity, even without a known acquisition",proposed,client.authorizeSale(proposed));
        assertEquals(offer.getEventId(),requested.get().get("source"));
        assertNull(client.currentCanonicalOpenBuy());
        client.publishOfferCleared(2);acknowledge();ledger(false);requested.set(null);
        assertNull(client.authorizeSale(proposed));assertNull(requested.get());
    }
    @Test public void newPartialFillWhileVerifyingWithdrawsTheOldInstruction()throws Exception {
        publishSale(0);acknowledge();ledger(false);
        duringRequest=()->{try{publishSale(1);}catch(Exception error){throw new AssertionError(error);}};
        assertNull(client.authorizeSale(sale(true)));
        assertNotNull("The source was sent before the partial fill changed it",requested.get());
    }
    @Test public void acknowledgedPartialFillAlsoWithdrawsTheOldSource()throws Exception {
        publishSale(0);acknowledge();ledger(false);
        duringRequest=()->{try{publishSale(1);acknowledge();ledger(false);}catch(Exception error){throw new AssertionError(error);}};
        assertNull("Drained delivery is not permission to reuse the previous sell frame",client.authorizeSale(sale(true)));
        assertNotNull(requested.get());
    }
    @Test public void wrongSourceAndMissingFinancialDeliveryNeverBecomeAuthority()throws Exception {
        ledger(true);replySource="different-acquisition";
        assertEquals(SuggestionType.WAIT,client.authorizeSale(sale(false)).getType());
        requested.set(null);publishSale(0);
        assertEquals(SuggestionType.WAIT,client.authorizeSale(sale(false)).getType());assertNull(requested.get());
    }
    @Test public void refusalExplainsTheActualBlockedActionAndRecoveryClearsTheDiagnostic()throws Exception {
        publishSale(0);acknowledge();ledger(false);refusal="SALE_QUOTE_STALE";
        assertNull("Routine maintenance is withheld without blocking the separate buy path",client.authorizeSale(sale(true)));
        assertTrue(client.lastError().contains("SALE_QUOTE_STALE"));
        refusal=null;assertEquals(SuggestionType.MODIFY_SELL,client.authorizeSale(sale(true)).getType());
        assertEquals("",client.lastError());
    }
    @Test public void refusedUrgentCutsStillRetainPriorityAndNeverBecomeAnEntryFallback()throws Exception {
        publishSale(0);acknowledge();ledger(false);refusal="SALE_QUOTE_STALE";
        var cut=Suggestion.builder(SuggestionType.MODIFY_SELL).item(561,"Nature rune").quantity(3).price(120).slot(2).lossCut(true).build();
        assertEquals(SuggestionType.WAIT,client.authorizeSale(cut).getType());
        ledger(true);assertEquals(SuggestionType.WAIT,client.authorizeSale(sale(false)).getType());
    }
}
