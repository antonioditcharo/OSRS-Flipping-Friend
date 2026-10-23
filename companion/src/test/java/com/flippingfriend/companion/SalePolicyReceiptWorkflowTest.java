package com.flippingfriend.companion;

import com.flippingfriend.core.*;
import com.google.gson.Gson;
import java.nio.file.Path;
import java.sql.*;
import java.util.*;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

/** Disposable canonical sources: accepted policy settings never rewrite financial history. */
public class SalePolicyReceiptWorkflowTest {
    @Rule public TemporaryFolder folder=new TemporaryFolder();
    final Gson json=new Gson();
    final class Fixture implements AutoCloseable {
        final CompanionSalePolicyTest helper=new CompanionSalePolicyTest();
        final Path path;final SqliteStore store;final long now=helper.at+1800;
        AccountSnapshot account;OfferEvent source;boolean listed=true;final boolean modernSource;
        Fixture()throws Exception {this(true,null);}
        Fixture(boolean modernSource)throws Exception {this(modernSource,null);}
        Fixture(boolean modernSource,SqliteStore.TransactionCheckpoint checkpoint)throws Exception {
            this.modernSource=modernSource;
            path=folder.newFolder().toPath().resolve("account.db");store=checkpoint==null?new SqliteStore(path):new SqliteStore(path,checkpoint);
            account=helper.setup(store,new AcquisitionExitContext(140,90,120));
            accept(helper.event(0,"SELLING","sale",3,0,0,150,now-20));
            captureSettings(5,now-10,3);captureSettings(5,now,3);
        }
        void accept(OfferEvent event)throws Exception {helper.accept(store,event);}
        AccountSnapshot captureSettings(int cadence,long time,int stock)throws Exception {
            var readSet=new HashMap<Integer,String>();
            for(int slot=0;slot<8;slot++) {
                var event=helper.event(slot,slot==0 && listed?"SELLING":"EMPTY",slot==0 && listed?"sale":null,
                    slot==0 && listed?3:0,0,0,slot==0 && listed?150:0,time);
                if(slot==0 && listed && modernSource)event=event.withSalePolicyEvidenceVersion("1");
                accept(event);readSet.put(slot,event.getEventId());if(slot==0 && listed)source=event;
            }
            var raw=json.toJsonTree(account).getAsJsonObject();raw.addProperty("correlationId",UUID.randomUUID().toString());
            raw.addProperty("observedAt",time);raw.addProperty("checkIntervalMinutes",cadence);
            raw.addProperty("targetHoldMinutes",360);raw.addProperty("freeSlots",listed?7:8);
            account=json.fromJson(raw,AccountSnapshot.class).withOfferReadSet(readSet)
                .withObservedHoldings(Map.of(561,stock)).withObservedInventory(Map.of(561,listed?0:stock));
            store.recordAndReconcileAccount(account,PositionStateView.from(account),json.toJson(account),time);
            return account;
        }
        SalePolicyReceipt issue(String request)throws Exception {
            var read=store.saleProjections(helper.id,now);
            var decision=new CompanionSalePolicy().decide(read.planning,read.ledger,account,helper.market(now,120),
                (i,s)->helper.bars(now,120),561,119,3,source.getEventId(),false,0,now);
            assertEquals("Only a successful final policy decision may reach POST issuance",OfferLifecycleAction.CANCEL_SELL,decision.getAction());
            return store.issueSalePolicyReceipt(request,account,source,source.getFirstSeenAt(),119,3,false,now,480);
        }
        void collect(SalePolicyReceipt receipt)throws Exception {
            AcceptedSalePolicyProof proof=null;
            if(receipt!=null)proof=AcceptedSalePolicyProof.accepted(receipt,now,CaptureOrder.next()).captured(CaptureOrder.next());
            var fill=helper.event(0,"SELLING","sale",3,1,150,150,now+1);
            if(proof!=null){proof=proof.bindTo(fill);assertNotNull(proof);fill=fill.withAcceptedSalePolicyProof(proof);}
            accept(fill);
            var cancel=helper.event(0,"CANCELLED_SELL","sale",3,1,150,150,now+2);
            accept(proof==null?cancel:cancel.withAcceptedSalePolicyProof(proof));
            accept(helper.event(0,"EMPTY",null,0,0,0,0,now+3));listed=false;
            captureSettings(480,now+4,2);
        }
        PolicyDecision relist(SqliteStore db,int price,int quantity)throws Exception {
            var read=db.saleProjections(helper.id,now+4);var position=read.ledger.getTradingPositions().get(0);
            return new CompanionSalePolicy().decide(read.planning,read.ledger,account,helper.market(now+4,120),
                (i,s)->helper.bars(now+4,120),561,price,quantity,position.getSourceEventId(),false,-1,now+4,source.getEventId());
        }
        @Override public void close()throws Exception{store.close();}
    }
    private List<String> rows(Path path,String sql)throws Exception {
        var result=new ArrayList<String>();
        try(var db=DriverManager.getConnection("jdbc:sqlite:"+path.toAbsolutePath());var statement=db.createStatement();var rows=statement.executeQuery(sql)) {
            int columns=rows.getMetaData().getColumnCount();
            while(rows.next()){var row=new ArrayList<String>();for(int c=1;c<=columns;c++)row.add(rows.getString(c));result.add(row.toString());}
        }
        return result;
    }
    private List<String> financialPrefix(Path path)throws Exception {
        var result=new ArrayList<>(rows(path,"SELECT * FROM position_event ORDER BY id"));
        result.addAll(rows(path,"SELECT * FROM event_log WHERE event_type<>'ACCOUNT_STATE' ORDER BY id"));return result;
    }
    private void mutate(Path path,String sql)throws Exception {
        try(var db=DriverManager.getConnection("jdbc:sqlite:"+path.toAbsolutePath());var statement=db.createStatement()){statement.executeUpdate(sql);}
    }
    private void assertMaintenanceAuthorized(Fixture f,OfferEvent source,long time)throws Exception {
        var read=f.store.saleProjections(f.helper.id,time);
        assertEquals(OfferLifecycleAction.CANCEL_SELL,new CompanionSalePolicy().decide(read.planning,read.ledger,f.account,f.helper.market(time,120),
            (i,s)->f.helper.bars(time,120),561,119,source.getTotalQuantity()-source.getFilledQuantity(),source.getEventId(),false,source.getSlot(),time).getAction());
    }
    private Map<Integer,OfferEvent> boardAccount(Fixture f,Map<Integer,OfferEvent> board,int cadence,long time,
            Map<Integer,AcceptedSalePolicyProof> policies)throws Exception {
        var captured=new HashMap<Integer,OfferEvent>();var readSet=new HashMap<Integer,String>();int listed=0;
        for(int slot=0;slot<8;slot++) {
            var template=board.get(slot);OfferEvent event;
            if(template==null)event=f.helper.event(slot,"EMPTY",null,0,0,0,0,time);
            else {
                event=OfferEvent.builder("board",time,template.getEventType()).accountId(f.helper.id).authenticatedObservation(true)
                    .eventIdentity("event-"+(++f.helper.seq),"session",template.getOfferIdentity()).sequence(f.helper.seq)
                    .slot(slot).item(561,"Nature rune").price(template.getPrice()).quantities(template.getTotalQuantity(),template.getFilledQuantity())
                    .spent(template.getSpent()).firstSeenAt(template.getFirstSeenAt()).salePolicyEvidenceVersion("1")
                    .continuationSuccessorProof(template.getContinuationSuccessorProof()).build();
                var policy=policies.get(slot);
                if(policy!=null) {
                    if(!policy.isBound()){policy=policy.captured(CaptureOrder.next()).bindTo(event);assertNotNull(policy);policies.put(slot,policy);}
                    event=event.withAcceptedSalePolicyProof(policy);
                }
                listed+=event.getTotalQuantity()-event.getFilledQuantity();
            }
            f.accept(event);captured.put(slot,event);readSet.put(slot,event.getEventId());
        }
        var raw=json.toJsonTree(f.account).getAsJsonObject();raw.addProperty("correlationId",UUID.randomUUID().toString());
        raw.addProperty("observedAt",time);raw.addProperty("checkIntervalMinutes",cadence);raw.addProperty("freeSlots",8-board.size());
        f.account=json.fromJson(raw,AccountSnapshot.class).withOfferReadSet(readSet).withObservedHoldings(Map.of(561,3))
            .withObservedInventory(Map.of(561,3-listed));
        f.store.recordAndReconcileAccount(f.account,PositionStateView.from(f.account),json.toJson(f.account),time);return captured;
    }
    @Test public void acceptedFiveMinutePolicySurvivesEightHourCadenceLateFillCollectionAndCanonicalRestart()throws Exception {
        try(var f=new Fixture()) {
            var receipt=f.issue(UUID.randomUUID().toString());f.collect(receipt);
            var position=f.store.ledgerSnapshot(f.helper.id,f.now+4).getTradingPositions().get(0);
            assertEquals(2,position.getQuantity());assertEquals(201,position.getTotalCost());
            var prefix=financialPrefix(f.path);
            try(var restarted=new SqliteStore(f.path)) {
                assertEquals(OfferLifecycleAction.PLACE_SELL,f.relist(restarted,119,2).getAction());
                assertEquals(OfferLifecycleAction.WAIT,f.relist(restarted,100,2).getAction());
                assertEquals(OfferLifecycleAction.WAIT,f.relist(restarted,119,3).getAction());
            }
            assertEquals(prefix,financialPrefix(f.path));
        }
    }
    @Test public void legacyCancellationCannotBorrowTodaysSettingsToProveItsHistoricalEligibility()throws Exception {
        try(var f=new Fixture(false)) {
            f.collect(null);var prefix=financialPrefix(f.path);
            assertEquals("The old current-cadence gate is the reproduced blocked workflow", "SALE_REPRICE_CHAIN_UNAVAILABLE",
                f.relist(f.store,119,2).getReasonCode());
            assertEquals(prefix,financialPrefix(f.path));
        }
    }
    @Test public void newMissingAcceptanceCannotDowngradeToLegacyWhenCurrentCadenceWouldQualify()throws Exception {
        for(boolean modern:List.of(false,true))try(var f=new Fixture(modern)) {
            f.collect(null);f.captureSettings(5,f.now+4,2);
            assertEquals("Only old unmarked sources keep their existing conservative compatibility path",modern?OfferLifecycleAction.WAIT:OfferLifecycleAction.PLACE_SELL,
                f.relist(f.store,119,2).getAction());
        }
    }
    @Test public void unissuedForgedReceiptMissingIssuanceAndMissingPinnedAccountGrantNoAuthority()throws Exception {
        for(String missing:List.of("unissued","issuance","account"))try(var f=new Fixture()) {
            var receipt=f.issue(UUID.randomUUID().toString());
            if("unissued".equals(missing)) {
                var raw=json.toJsonTree(receipt).getAsJsonObject();raw.addProperty("receiptId",UUID.randomUUID().toString());
                receipt=json.fromJson(raw,SalePolicyReceipt.class);
            }
            f.collect(receipt);var prefix=financialPrefix(f.path);
            if("issuance".equals(missing))mutate(f.path,"DELETE FROM sale_policy_receipt");
            if("account".equals(missing))mutate(f.path,"DELETE FROM event_log WHERE id="+receipt.getAccountRowId());
            assertEquals(missing,"SALE_REPRICE_CHAIN_UNAVAILABLE",f.relist(f.store,119,2).getReasonCode());
            assertEquals(missing,prefix,financialPrefix(f.path));
        }
    }
    @Test public void pruningKeepsOnlyPinnedPolicyAccountSourceAndPreservesFinancialAndOfferPrefix()throws Exception {
        try(var f=new Fixture()) {
            var receipt=f.issue(UUID.randomUUID().toString());f.collect(receipt);var prefix=financialPrefix(f.path);
            assertTrue(rows(f.path,"SELECT id FROM event_log WHERE event_type='ACCOUNT_STATE'").size()>1);
            assertTrue(f.store.pruneEvents(0)>0);
            assertEquals(List.of("["+receipt.getAccountRowId()+"]"),rows(f.path,"SELECT id FROM event_log WHERE event_type='ACCOUNT_STATE'"));
            assertEquals(OfferLifecycleAction.PLACE_SELL,f.relist(f.store,119,2).getAction());
            assertEquals(prefix,financialPrefix(f.path));
        }
    }
    @Test public void issuanceRetryIsIdempotentButChangedRequestCannotReplaceTheOriginalReceipt()throws Exception {
        try(var f=new Fixture()) {
            var prefix=financialPrefix(f.path);String request=UUID.randomUUID().toString();
            var receipt=f.issue(request);assertEquals(receipt,f.issue(request));
            assertEquals(1,rows(f.path,"SELECT receipt_id FROM sale_policy_receipt").size());
            assertThrows(IllegalArgumentException.class,()->f.store.issueSalePolicyReceipt(request,f.account,f.source,
                f.source.getFirstSeenAt(),118,3,false,f.now,480));
            assertEquals(List.of("["+request+"]"),rows(f.path,"SELECT receipt_id FROM sale_policy_receipt"));
            assertEquals(prefix,financialPrefix(f.path));
        }
    }
    @Test public void contradictoryReadSetAndOriginalSourceMetadataRefusesIssuanceWithoutAnyFurtherWrites()throws Exception {
        for(int slot:List.of(0,7))for(String corruption:List.of("observed_at=observed_at+1","event_type='WRONG_SOURCE_TYPE'",
                "correlation_id='different-capture'","payload=json_set(payload,'$.eventId','different-payload-event')"))try(var f=new Fixture()) {
            String target=f.account.getOfferReadSet().get(slot);
            mutate(f.path,"UPDATE event_log SET "+corruption+" WHERE event_id='"+target+"'");
            var prefix=financialPrefix(f.path);
            assertThrows("slot "+slot+" / "+corruption,Exception.class,()->f.store.issueSalePolicyReceipt(UUID.randomUUID().toString(),
                f.account,f.source,f.source.getFirstSeenAt(),119,3,false,f.now,480));
            assertTrue(rows(f.path,"SELECT receipt_id FROM sale_policy_receipt").isEmpty());
            assertEquals(prefix,financialPrefix(f.path));
        }
    }
    @Test public void malformedRawAccountSettingsCannotBeNormalizedIntoAValidReceipt()throws Exception {
        for(String corruption:List.of("observed_at=observed_at+1","payload=json_set(payload,'$.targetHoldMinutes','360')",
                "payload=json_set(payload,'$.checkIntervalMinutes','5')","payload=json_set(payload,'$.sellOnly','false')"))try(var f=new Fixture()) {
            mutate(f.path,"UPDATE event_log SET "+corruption+" WHERE event_type='ACCOUNT_STATE' AND correlation_id='"+f.account.getCorrelationId()+"'");
            var prefix=financialPrefix(f.path);
            assertThrows(corruption,Exception.class,()->f.store.issueSalePolicyReceipt(UUID.randomUUID().toString(),f.account,f.source,
                f.source.getFirstSeenAt(),119,3,false,f.now,480));
            assertTrue(rows(f.path,"SELECT receipt_id FROM sale_policy_receipt").isEmpty());assertEquals(prefix,financialPrefix(f.path));
        }
    }
    @Test public void failedReceiptCommitRollsBackItsPinAndSameRequestCanRetryWithoutFinancialReplay()throws Exception {
        var fail=new java.util.concurrent.atomic.AtomicBoolean();
        try(var f=new Fixture(true,checkpoint->{if("SALE_POLICY_RECEIPT_WRITTEN".equals(checkpoint) && fail.get())throw new java.io.IOException("injected receipt outage");})) {
            String request=UUID.randomUUID().toString();var prefix=financialPrefix(f.path);fail.set(true);
            assertThrows(Exception.class,()->f.issue(request));
            assertTrue(rows(f.path,"SELECT receipt_id FROM sale_policy_receipt").isEmpty());assertEquals(prefix,financialPrefix(f.path));
            fail.set(false);var receipt=f.issue(request);assertEquals(request,receipt.getReceiptId());
            assertEquals(List.of("["+request+"]"),rows(f.path,"SELECT receipt_id FROM sale_policy_receipt"));
            assertEquals(prefix,financialPrefix(f.path));
        }
    }
    @Test public void idempotentRetryRevalidatesThePinnedSourceAndIssuanceColumnsWithoutRewriting()throws Exception {
        for(String damage:List.of("missing-account","changed-hash","duplicate-correlation","account-column","source-column","ordering-column"))try(var f=new Fixture()) {
            String request=UUID.randomUUID().toString();var receipt=f.issue(request);
            if("missing-account".equals(damage))mutate(f.path,"DELETE FROM event_log WHERE id="+receipt.getAccountRowId());
            if("changed-hash".equals(damage))mutate(f.path,"UPDATE event_log SET payload=json_set(payload,'$.targetHoldMinutes','360') WHERE id="+receipt.getAccountRowId());
            if("duplicate-correlation".equals(damage))mutate(f.path,"INSERT INTO event_log(observed_at,correlation_id,event_id,event_type,payload) "
                +"SELECT observed_at,correlation_id,NULL,event_type,payload FROM event_log WHERE id="+receipt.getAccountRowId());
            if("account-column".equals(damage))mutate(f.path,"UPDATE sale_policy_receipt SET account_id='different-account'");
            if("source-column".equals(damage))mutate(f.path,"UPDATE sale_policy_receipt SET source_event_id='different-source'");
            if("ordering-column".equals(damage))mutate(f.path,"UPDATE sale_policy_receipt SET issued_after_row=0");
            var prefix=financialPrefix(f.path);var retained=rows(f.path,"SELECT * FROM sale_policy_receipt");
            assertThrows(damage,Exception.class,()->f.store.issueSalePolicyReceipt(request,f.account,f.source,f.source.getFirstSeenAt(),119,3,false,f.now,480));
            assertEquals(damage,retained,rows(f.path,"SELECT * FROM sale_policy_receipt"));assertEquals(damage,prefix,financialPrefix(f.path));
        }
    }
    @Test public void separatelyAcceptedFiveAndThirtyMinuteOriginsKeepTheirOwnCapsBesideAProvenSuccessorAtEightHours()throws Exception {
        try(var f=new Fixture()) {
            f.accept(f.helper.event(0,"CANCELLED_SELL","sale",3,0,0,150,f.now+1));
            f.accept(f.helper.event(0,"EMPTY",null,0,0,0,0,f.now+2));
            var a=f.helper.event(0,"SELLING","sale-a",2,0,0,150,f.now+3);
            var b=f.helper.event(1,"SELLING","sale-b",1,0,0,160,f.now+3);f.accept(a);f.accept(b);
            var accepted=new HashMap<Integer,AcceptedSalePolicyProof>();
            var board=boardAccount(f,Map.of(0,a,1,b),5,f.now+5,accepted);a=board.get(0);
            assertMaintenanceAuthorized(f,a,f.now+5);
            var receiptA=f.store.issueSalePolicyReceipt(UUID.randomUUID().toString(),f.account,a,a.getFirstSeenAt(),119,2,false,f.now+5,480);
            accepted.put(0,AcceptedSalePolicyProof.accepted(receiptA,f.now+5,CaptureOrder.next()));
            board=boardAccount(f,Map.of(0,a,1,b),30,f.now+6,accepted);b=board.get(1);
            assertMaintenanceAuthorized(f,b,f.now+6);
            var receiptB=f.store.issueSalePolicyReceipt(UUID.randomUUID().toString(),f.account,b,b.getFirstSeenAt(),119,1,false,f.now+6,1800);
            var proofB=AcceptedSalePolicyProof.accepted(receiptB,f.now+6,CaptureOrder.next()).captured(CaptureOrder.next());
            f.accept(f.helper.event(0,"CANCELLED_SELL","sale-a",2,0,0,150,f.now+7).withAcceptedSalePolicyProof(accepted.get(0)));
            var cancelB=f.helper.event(1,"CANCELLED_SELL","sale-b",1,0,0,160,f.now+8);proofB=proofB.bindTo(cancelB);assertNotNull(proofB);
            f.accept(cancelB.withAcceptedSalePolicyProof(proofB));
            f.accept(f.helper.event(0,"EMPTY",null,0,0,0,0,f.now+9));f.accept(f.helper.event(1,"EMPTY",null,0,0,0,0,f.now+10));
            var child=OfferEvent.builder("child",f.now+11,"SELLING").accountId(f.helper.id).authenticatedObservation(true)
                .eventIdentity("event-"+(++f.helper.seq),"session","successor-a").sequence(f.helper.seq).slot(0).item(561,"Nature rune")
                .price(119).quantities(1,0).firstSeenAt(f.now+11).salePolicyEvidenceVersion("1").build();
            var successor=ContinuationSuccessorProof.accepted(f.helper.id,ContinuationSuccessorProof.workflowIdentity(a),a.getEventId(),
                "accepted-successor-a",561,false,119,1,f.now+10,CaptureOrder.next()).captured(CaptureOrder.next()).bindTo(child);
            assertNotNull(successor);child=child.withContinuationSuccessorProof(successor);f.accept(child);
            boardAccount(f,Map.of(0,child),480,f.now+12,new HashMap<>());
            var read=f.store.saleProjections(f.helper.id,f.now+12);var position=read.ledger.getTradingPositions().get(0);
            assertEquals(3,position.getQuantity());assertEquals(301,position.getTotalCost());var prefix=financialPrefix(f.path);
            for(var origin:List.of(a,b)) {
                var decision=new CompanionSalePolicy().decide(read.planning,read.ledger,f.account,f.helper.market(f.now+12,120),
                    (i,s)->f.helper.bars(f.now+12,120),561,119,1,position.getSourceEventId(),false,-1,f.now+12,origin.getEventId());
                assertEquals(origin.getOfferIdentity()+": "+decision.getReasonCode(),OfferLifecycleAction.PLACE_SELL,decision.getAction());
            }
            assertEquals(OfferLifecycleAction.WAIT,new CompanionSalePolicy().decide(read.planning,read.ledger,f.account,f.helper.market(f.now+12,120),
                (i,s)->f.helper.bars(f.now+12,120),561,119,2,position.getSourceEventId(),false,-1,f.now+12,b.getEventId()).getAction());
            assertEquals(prefix,financialPrefix(f.path));
        }
    }
}
