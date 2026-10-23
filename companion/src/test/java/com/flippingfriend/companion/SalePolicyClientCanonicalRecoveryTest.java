package com.flippingfriend.companion;

import com.flippingfriend.core.*;
import com.flippingfriend.data.PluginStorage;
import com.flippingfriend.session.*;
import com.google.gson.Gson;
import java.nio.file.*;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import net.runelite.client.config.ConfigManager;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/** Real forced client promises/outbox must remain canonical authority during later fact-save outages. */
public class SalePolicyClientCanonicalRecoveryTest {
    @Rule public TemporaryFolder folder=new TemporaryFolder();
    final Gson json=new Gson();
    final CompanionSalePolicyTest helper=new CompanionSalePolicyTest();
    final String account=helper.id;
    PluginStorage storage(Path root)throws Exception {
        var config=mock(ConfigManager.class);when(config.getRSProfileKey()).thenReturn("sale-policy-fixture");
        var constructor=PluginStorage.class.getDeclaredConstructor(ConfigManager.class,Gson.class,Path.class);
        constructor.setAccessible(true);return constructor.newInstance(config,json,root);
    }
    final class Client {
        final PluginStorage storage;final SellRelistIntent intents;final OfferEventOutbox outbox;
        final CompanionClient client;final OfferTracker tracker=mock(OfferTracker.class);
        Client(Path root)throws Exception {
            storage=storage(root);intents=new SellRelistIntent(storage);outbox=new OfferEventOutbox(storage);
            client=new CompanionClient(storage,json,new SuggestionLedger(),outbox);
            client.setSalePolicyAcceptanceReader(intents::acceptedPolicies);
        }
        void offer(TrackedOffer offer,long at)throws Exception{client.captureObservedOffer(offer.copyCurrentObservation(),account,at);}
        void empty(int slot,long at)throws Exception{client.captureObservedOfferCleared(slot,account,at);}
        List<OfferEvent> deliver(SqliteStore store)throws Exception {
            var pending=new ArrayList<>(outbox.pending());
            for(var event:pending) {
                var accepted=store.recordAndProjectOffer(event,json.toJson(event),true);
                assertTrue("Fixture source must be accepted: "+event.getEventType()+" / "+accepted.transition.getReason(),accepted.transition.isAccepted());
                assertTrue(outbox.acknowledge(event.getEventId(),event.getSessionId(),event.getSequence()));
            }
            return pending;
        }
    }
    AccountSnapshot capture(Client client,SqliteStore store,AccountSnapshot previous,TrackedOffer offer,int cadence,long at,int quantity)throws Exception {
        if(offer!=null)client.offer(offer,at);else client.empty(0,at);
        for(int slot=1;slot<8;slot++)client.empty(slot,at);
        var frames=client.deliver(store);var readSet=new HashMap<Integer,String>();
        for(var event:frames)readSet.put(event.getSlot(),event.getEventId());
        assertEquals(8,readSet.size());
        var raw=json.toJsonTree(previous).getAsJsonObject();raw.addProperty("correlationId",UUID.randomUUID().toString());
        raw.addProperty("observedAt",at);raw.addProperty("checkIntervalMinutes",cadence);raw.addProperty("targetHoldMinutes",360);
        raw.addProperty("freeSlots",offer==null?8:7);
        var snapshot=json.fromJson(raw,AccountSnapshot.class).withOfferReadSet(readSet).withObservedHoldings(Map.of(561,quantity))
            .withObservedInventory(Map.of(561,offer==null?quantity:0));
        store.recordAndReconcileAccount(snapshot,PositionStateView.from(snapshot),json.toJson(snapshot),at);return snapshot;
    }
    List<String> financialRows(Path path)throws Exception {
        var result=new ArrayList<String>();
        try(var db=DriverManager.getConnection("jdbc:sqlite:"+path.toAbsolutePath());var statement=db.createStatement();
            var rows=statement.executeQuery("SELECT * FROM position_event ORDER BY id")) {
            int columns=rows.getMetaData().getColumnCount();
            while(rows.next()){var fields=new ArrayList<String>();for(int c=1;c<=columns;c++)fields.add(rows.getString(c));result.add(fields.toString());}
        }
        return result;
    }
    @Test public void committedReceiptSurvivesFailedLaterProgressSaveThroughFirstCancellationCanonicalRestartAndExactRelist()throws Exception {
        var root=folder.newFolder().toPath();var path=root.resolve("canonical.db");var clientRoot=root.resolve("client");
        var client=new Client(clientRoot);long now=Instant.now().getEpochSecond();
        var working=new TrackedOffer(0,561,false,150,3,helper.at+60);working.setState("SELLING");
        when(client.tracker.getOffer(0)).thenReturn(working);when(client.tracker.getOffers()).thenReturn(List.of(working));
        AccountSnapshot snapshot;OfferEvent source;AcceptedSalePolicyProof firstProof;
        try(var store=new SqliteStore(path)) {
            // One actual durable client session owns both acquisition and sale observations.
            // Mixing helper session IDs with the real outbox correctly fails canonical authority.
            for(int slot=0;slot<8;slot++)client.empty(slot,helper.at);
            client.deliver(store);
            var buy=new TrackedOffer(0,561,true,101,3,helper.at+1);buy.setState("BUYING");
            client.offer(buy,helper.at+1);client.deliver(store);
            buy.setState("BOUGHT");buy.setQuantityFilled(3);buy.setSpent(302);
            client.offer(buy,helper.at+2);client.deliver(store);
            client.empty(0,helper.at+3);client.deliver(store);
            snapshot=new AccountSnapshot("initial",now,100000000,0,7,8,true,false,0,"MODERATE",Map.of(),0,Map.of(),true,5,
                Set.of(),Set.of(),Set.of(),false,360).withAccountId(account).withAutomaticTradeScope().withObservedHoldings(Map.of(561,3));
            snapshot=capture(client,store,snapshot,working,5,now-10,3);
            snapshot=capture(client,store,snapshot,working,5,now,3);
            var read=store.saleProjections(account,now);source=read.planning.currentEvents.get(0);
            var decision=new CompanionSalePolicy().decide(read.planning,read.ledger,snapshot,helper.market(now,120),
                (i,s)->helper.bars(now,120),561,119,3,source.getEventId(),false,0,now);
            assertEquals(decision.getReasonCode(),OfferLifecycleAction.CANCEL_SELL,decision.getAction());
            var receipt=store.issueSalePolicyReceipt(UUID.randomUUID().toString(),snapshot,source,source.getFirstSeenAt(),119,3,false,now,480);
            assertTrue(client.intents.advise(working,account,true,client.tracker,source.getEventId(),receipt,now));
            var accepted=client.intents.acceptedPolicies(account);assertEquals(1,accepted.size());assertFalse(accepted.get(0).isBound());
            var file=client.storage.accountDir().resolve("sell-relist-intent.json");var originalBytes=Files.readAllBytes(file);
            var blocked=file.resolveSibling(file.getFileName()+".tmp");Files.createDirectory(blocked);
            working.setQuantityFilled(1);working.setSpent(150);working.setState("CANCELLED_SELL");
            client.intents.observe(client.tracker,account); // The real callback checkpoints facts before asking the outbox for receipt evidence.
            assertArrayEquals("The later fact save failed; original accepted promise is still forced",originalBytes,Files.readAllBytes(file));
            client.offer(working,now+1);var cancellation=client.outbox.pending();assertEquals(1,cancellation.size());
            var first=cancellation.get(0);assertEquals("CANCELLED_SELL",first.getEventType());
            assertEquals(1,first.getFilledQuantity());assertEquals(150,first.getSpent());
            firstProof=first.getAcceptedSalePolicyProof();assertNotNull("First cancellation must retain the separately committed acceptance",firstProof);
            assertTrue(firstProof.qualifiesFirst(first));assertEquals(receipt,firstProof.getReceipt());
            client.deliver(store);Files.delete(blocked);
            var position=store.ledgerSnapshot(account,now+1).getTradingPositions().get(0);
            assertEquals(2,position.getQuantity());assertEquals(302-SaleAccounting.allocateCost(302,3,1),position.getTotalCost());
        }
        var prefix=financialRows(path);
        var restarted=new Client(clientRoot);when(restarted.tracker.getOffers()).thenReturn(List.of());
        when(restarted.tracker.getOffer(0)).thenReturn(null);working.setCollected(true);when(restarted.tracker.getSettledOffer(0)).thenReturn(working);
        var remaining=restarted.intents.collectedRequest(restarted.tracker,account);assertNotNull(remaining);
        assertEquals(2,remaining.getQuantity());assertEquals(source.getEventId(),remaining.getSaleRepriceSource());assertEquals(480,remaining.getAcceptedPatienceSeconds());
        try(var store=new SqliteStore(path)) {
            // Retry the canonical cancellation without creating another financial execution.
            working.setCollected(false);restarted.offer(working,now+2);
            var retry=restarted.outbox.pending().get(0);assertEquals(firstProof,retry.getAcceptedSalePolicyProof());
            restarted.deliver(store);working.setCollected(true);
            snapshot=capture(restarted,store,snapshot,null,480,now+3,2);
            var read=store.saleProjections(account,now+3);var position=read.ledger.getTradingPositions().get(0);
            assertEquals(2,position.getQuantity());assertEquals(302-SaleAccounting.allocateCost(302,3,1),position.getTotalCost());
            var relist=new CompanionSalePolicy().decide(read.planning,read.ledger,snapshot,helper.market(now+3,120),
                (i,s)->helper.bars(now+3,120),561,119,2,position.getSourceEventId(),false,-1,now+3,source.getEventId());
            assertEquals(relist.getReasonCode(),OfferLifecycleAction.PLACE_SELL,relist.getAction());
            assertEquals(OfferLifecycleAction.WAIT,new CompanionSalePolicy().decide(read.planning,read.ledger,snapshot,helper.market(now+3,120),
                (i,s)->helper.bars(now+3,120),561,100,2,position.getSourceEventId(),false,-1,now+3,source.getEventId()).getAction());
        }
        assertEquals("ACK, canonical restart and collection must not replay accounting",prefix,financialRows(path));
    }
}
