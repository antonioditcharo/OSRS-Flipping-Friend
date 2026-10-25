package com.flippingfriend.companion;

import com.flippingfriend.core.*;
import com.google.gson.*;
import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.sql.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class HostedPilotTest {
    @Rule public TemporaryFolder folder=new TemporaryFolder();
    private final Gson gson=new Gson();private final String account=AccountIdentity.fromProfileKey("pilot-test");
    private PortfolioCandidate candidate(int item,int quantity,long now) {
        return new PortfolioCandidate(item,"Item "+item,"",100,100,100,120,120,120,quantity,1000,
            quantity*18L,quantity*5L,1,.95,.95,.1,.1,1,now+90);
    }
    private PilotProtocol.AllocationRequest request(String account,long now) {
        var r=new PilotProtocol.AllocationRequest();r.accountId=account;r.installationId="fixture";r.generation=1;r.frameOrder=1;
        r.context="a".repeat(64);r.observedAt=now;r.marketAt=now;r.coordinate=true;r.candidates=List.of(candidate(561,30,now));return r;
    }
    @Test public void fairnessCountsUsersRatherThanRefreshesOrAccountCountAndHonorsBothLegs() {
        long now=20000;var coordinator=new PilotCoordinator();
        var first=request(account,now);coordinator.submit("one",first,now);
        coordinator.submit("one",request(AccountIdentity.fromProfileKey("alt"),now),now);
        String other=AccountIdentity.fromProfileKey("other");coordinator.submit("two",request(other,now),now);
        var hints=coordinator.round((item,buy)->100,now);assertEquals(2,hints.size());
        assertEquals(1,hints.values().stream().filter(h->h.itemId>0).count());
        for(int i=0;i<30;i++)coordinator.submit("one",first,now);
        var next=coordinator.round((item,buy)->100,now+16);
        assertEquals(561,next.get("two/"+other).itemId);
        var sellLimited=new PilotCoordinator();sellLimited.submit("owner",request(account,now),now);
        assertEquals(0,sellLimited.round((item,buy)->buy?10000:10,now).get("owner/"+account).itemId);
    }
    @Test public void realOutstandingDemandSurvivesAdviceExpiryAndOlderFramesCannotReplaceNewerOnes() {
        long now=20000;var coordinator=new PilotCoordinator();var first=request(account,now);
        first.demand=List.of(new PilotProtocol.Demand(561,0,100,false));first.frameOrder=2;
        coordinator.submit("owner",first,now);
        assertEquals(0,coordinator.round((item,buy)->100,now+16).get("owner/"+account).itemId);
        try{coordinator.submit("owner",request(account,now),now);fail("old frame accepted");}catch(IllegalArgumentException expected){}
    }
    @Test public void oneBlockedAccountCannotConsumeEveryForegroundWorker()throws Exception {
        var workers=Executors.newFixedThreadPool(2);var work=new FairAccountWork(workers);
        var entered=new CountDownLatch(1);var release=new CountDownLatch(1);var healthy=new CountDownLatch(1);
        try {
            work.execute("busy",()->{entered.countDown();try{release.await();}catch(InterruptedException ignored){}});
            assertTrue(entered.await(2,TimeUnit.SECONDS));
            for(int i=0;i<16;i++)work.execute("busy",()->{});
            try{work.execute("busy",()->{});fail();}catch(RejectedExecutionException expected){}
            work.execute("healthy",healthy::countDown);assertTrue(healthy.await(1,TimeUnit.SECONDS));
        }finally{release.countDown();work.close();workers.shutdownNow();}
    }
    @Test public void serverRestartPreservesDisconnectedDemandAndDoesNotRestoreAdviceLeases()throws Exception {
        long now=20000;Path database=folder.newFolder().toPath().resolve("replica.db");
        var input=request(account,now);input.demand=List.of(new PilotProtocol.Demand(561,0,100,false));
        try(var store=new PilotEvidenceStore(database)){store.saveDemand("offline",input);}
        var restored=new PilotCoordinator();
        try(var store=new PilotEvidenceStore(database)){store.restoreDemand(restored);}
        String other=AccountIdentity.fromProfileKey("new-user");restored.submit("active",request(other,now+200),now+200);
        var hints=restored.round((item,buy)->100,now+200);
        assertFalse(hints.containsKey("offline/"+account));assertEquals(0,hints.get("active/"+other).itemId);
    }
    @Test public void replicaCommitsOriginalIdsOnceAndWrongAccountOrChangedPayloadCannotAdvanceIt()throws Exception {
        Path file=folder.newFolder().toPath().resolve("replica.db");long now=20000;
        try(var store=new PilotEvidenceStore(file)) {
            var event=OfferEvent.builder("test",now,"EMPTY").accountId(account).eventIdentity("original-id","session",null).slot(0).sequence(1).build();
            var row=new PilotProtocol.EvidenceRow();row.cursor=7;row.eventId=event.getEventId();row.payload=gson.toJson(event);
            var batch=new PilotProtocol.EvidenceBatch();batch.accountId=account;batch.installationId="fixture";batch.streamId="offer-stream";batch.generation=1;
            batch.previousHash=PilotHashes.GENESIS;batch.rows=List.of(row);batch.hash=PilotHashes.sha(batch.previousHash+gson.toJson(batch.rows));
            assertEquals(7,store.accept("owner",batch).cursor);assertEquals(7,store.accept("owner",batch).cursor);
            batch.accountId=AccountIdentity.fromProfileKey("foreign");
            try{store.accept("owner",batch);fail();}catch(IllegalArgumentException expected){}
            batch.accountId=account;row.payload=row.payload.replace("test","changed");batch.after=7;row.cursor=8;batch.previousHash=batch.hash;
            batch.hash=PilotHashes.sha(batch.previousHash+gson.toJson(batch.rows));
            try{store.accept("owner",batch);fail();}catch(IllegalArgumentException expected){}
        }
        try(var c=DriverManager.getConnection("jdbc:sqlite:"+file);var s=c.createStatement();var r=s.executeQuery("SELECT COUNT(*),MIN(event_id) FROM evidence")) {
            r.next();assertEquals(1,r.getInt(1));assertEquals("original-id",r.getString(2));
        }
    }
    @Test public void privateApiRejectsForeignScopeAndReplicationNeverTouchesLocalOutbox()throws Exception {
        long now=System.currentTimeMillis()/1000;Path root=folder.newFolder().toPath();String token="fixture-token-"+"x".repeat(32);
        var config=new HostedPilotMain.Config();config.port=0;var device=new HostedPilotMain.Device();
        device.userId="owner";device.installationId="fixture";device.tokenSha256=PilotHashes.sha(token);device.accounts=Set.of(account);config.devices=List.of(device);
        try(var evidence=new PilotEvidenceStore(root.resolve("replica.db"));
            var server=new HostedPilotMain.Server(config,new MarketIngestionService(gson),evidence,gson);
            var local=new SqliteStore(root.resolve("local.db"))) {
            server.start();var settings=new PilotGateway.Config();settings.enabled=true;settings.installationId="fixture";settings.token=token;
            settings.endpoint="http://127.0.0.1:"+server.port();
            try(var gateway=new PilotGateway(settings);var replication=new PilotReplication(root,gateway)) {
                Path outbox=root.resolve("offer-event-outbox.json");Files.writeString(outbox,"player-owned-original-queue");
                var event=OfferEvent.builder("test",now,"EMPTY").accountId(account).eventIdentity("canonical-original","session",null)
                    .slot(0).sequence(1).authenticatedObservation(true).build();
                local.recordAndProjectOffer(event,gson.toJson(event),true);
                replication.replicate(account,root.resolve("local.db"),"OFFER");replication.replicate(account,root.resolve("local.db"),"OFFER");
                assertEquals("player-owned-original-queue",Files.readString(outbox));try(var c=DriverManager.getConnection("jdbc:sqlite:"+root.resolve("local.db"));var q=c.createStatement();var rows=q.executeQuery("SELECT COUNT(*) FROM event_log WHERE event_id='canonical-original'")){rows.next();assertEquals(1,rows.getInt(1));}
                var wrong=request(AccountIdentity.fromProfileKey("foreign"),now);
                assertEquals(400,gateway.post("allocations",gson.toJson(wrong)).statusCode());
            }
        }
    }
    @Test public void unassignedLegacyReplicaStaysUnassignedAndDoesNotPreventLaterScopedEvidence()throws Exception {
        Path file=folder.newFolder().toPath().resolve("replica.db");long now=20000;
        try(var store=new PilotEvidenceStore(file)) {
            var event=OfferEvent.builder("test",now,"EMPTY").eventIdentity("unassigned-original","session",null).slot(0).sequence(1).build();
            var row=new PilotProtocol.EvidenceRow();row.cursor=1;row.namespace="UNASSIGNED_OFFER";row.eventId=event.getEventId();row.payload=gson.toJson(event);
            var batch=new PilotProtocol.EvidenceBatch();batch.accountId=account;batch.installationId="fixture";batch.streamId="offer-stream";batch.generation=1;
            batch.previousHash=PilotHashes.GENESIS;batch.rows=List.of(row);batch.hash=PilotHashes.sha(batch.previousHash+gson.toJson(batch.rows));
            assertEquals(1,store.accept("owner",batch).cursor);
            var scoped=OfferEvent.builder("test",now,"EMPTY").accountId(account).eventIdentity("scoped-original","session",null).slot(1).sequence(2).build();
            var next=new PilotProtocol.EvidenceRow();next.cursor=2;next.eventId=scoped.getEventId();next.payload=gson.toJson(scoped);
            batch.after=1;batch.previousHash=batch.hash;batch.rows=List.of(next);batch.hash=PilotHashes.sha(batch.previousHash+gson.toJson(batch.rows));
            assertEquals(2,store.accept("owner",batch).cursor);
        }
        try(var read=DriverManager.getConnection("jdbc:sqlite:"+file);var statement=read.createStatement();var rows=statement.executeQuery("SELECT payload FROM evidence WHERE namespace='UNASSIGNED_OFFER'")) {
            assertTrue(rows.next());assertNull(gson.fromJson(rows.getString(1),OfferEvent.class).getAccountId());assertFalse(rows.next());
        }
    }
    @Test public void catalogueDiscoveryRotatesBeyondTheOriginalNinetyWithoutFetchingOnThePlannerThread() {
        long now=20000;var mapping=new HashMap<Integer,MarketIngestionService.Item>();var latest=new JsonObject();var bars=new JsonObject();
        for(int id=10000;id<10140;id++) {
            mapping.put(id,new MarketIngestionService.Item(id,"Item "+id,10000,false));
            var price=new JsonObject();price.addProperty("low",1000);price.addProperty("high",1200);price.addProperty("lowTime",now);price.addProperty("highTime",now);latest.add(""+id,price);
            var bar=new JsonObject();bar.addProperty("avgLowPrice",1000);bar.addProperty("avgHighPrice",1200);bar.addProperty("lowPriceVolume",600);bar.addProperty("highPriceVolume",600);bars.add(""+id,bar);
        }
        var factory=new CandidateFactory((item,step)->List.of());var market=new MarketIngestionService.MarketState(mapping,latest,bars,bars,now);
        var discovered=new HashSet<Integer>();
        for(int i=0;i<4;i++){factory.build(market,1,Map.of(),100000000,true,java.time.Instant.ofEpochSecond(now));discovered.addAll(factory.shortlistIds());}
        assertEquals(140,discovered.size());assertTrue(factory.shortlistIds().size()<=108);
    }
}
