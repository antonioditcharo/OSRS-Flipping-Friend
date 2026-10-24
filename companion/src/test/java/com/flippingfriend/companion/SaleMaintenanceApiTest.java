package com.flippingfriend.companion;

import com.flippingfriend.core.*;
import com.google.gson.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.time.Instant;
import java.util.*;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

/** Real loopback requests must not turn diagnostic GETs or malformed POSTs into acceptance. */
public class SaleMaintenanceApiTest {
    @Rule public TemporaryFolder folder=new TemporaryFolder();
    final Gson gson=new Gson();
    Object get(Object target,String name)throws Exception {
        var field=target.getClass().getDeclaredField(name);field.setAccessible(true);return field.get(target);
    }
    void set(Object target,String name,Object value)throws Exception {
        var field=target.getClass().getDeclaredField(name);field.setAccessible(true);field.set(target,value);
    }
    final class Response {
        final int status;final String body;
        Response(int status,String body){this.status=status;this.body=body;}
        PolicyDecision decision(){return gson.fromJson(body,PolicyDecision.class);}
    }
    final class Fixture implements AutoCloseable {
        final CompanionSalePolicyTest helper=new CompanionSalePolicyTest();
        final Path root=folder.newFolder().toPath();
        final SqliteStore legacy=new SqliteStore(root.resolve("legacy.db"));
        final CompanionService market=new CompanionService(gson,legacy);
        final CompanionAccounts accounts=new CompanionAccounts(root.resolve("accounts"),market);
        final ApiServer api=new ApiServer("fixture",gson,market,accounts,new InetSocketAddress("127.0.0.1",0));
        final String route="accounts/"+helper.id+"/policy/";
        final Path database=root.resolve("accounts").resolve(helper.id).resolve("flipping-friend.db");
        OfferEvent source;
        Fixture()throws Exception {api.start();}
        void seed()throws Exception {
            long now=Instant.now().getEpochSecond();
            set(get(market,"market"),"state",helper.market(now,120));
            ((SeriesCache)get(market,"series")).remember(561,"5m",helper.bars(now,120),Instant.now());
            var scoped=accounts.forAccount(helper.id);var store=(SqliteStore)get(scoped,"store");
            var account=helper.setup(store,new AcquisitionExitContext(140,90,120));
            for(long time:new long[]{now-10,now}) {
                Map<Integer,String> readSet=new HashMap<>();
                for(int slot=0;slot<8;slot++) {
                    var event=helper.event(slot,slot==0?"SELLING":"EMPTY",slot==0?"sale":null,
                        slot==0?3:0,0,0,slot==0?150:0,time);
                    if(slot==0){event=event.withSalePolicyEvidenceVersion("1");source=event;}
                    helper.accept(store,event);readSet.put(slot,event.getEventId());
                }
                var raw=gson.toJsonTree(account).getAsJsonObject();
                raw.addProperty("correlationId",UUID.randomUUID().toString());raw.addProperty("observedAt",time);
                raw.addProperty("freeSlots",7);raw.addProperty("targetHoldMinutes",360);
                account=gson.fromJson(raw,AccountSnapshot.class).withOfferReadSet(readSet)
                    .withObservedHoldings(Map.of(561,3)).withObservedInventory(Map.of(561,0));
                store.recordAndReconcileAccount(account,PositionStateView.from(account),gson.toJson(account),time);
            }
            set(scoped,"account",account);
        }
        JsonObject requestBody() {
            var body=new JsonObject();body.addProperty("requestId",UUID.randomUUID().toString());
            body.addProperty("item",561);body.addProperty("price",119);body.addProperty("quantity",3);
            body.addProperty("source",source==null?"unrecorded":source.getEventId());
            body.addProperty("cut",false);body.addProperty("slot",0);return body;
        }
        Response request(String endpoint,String method,String token,String account,String payload)throws Exception {
            var connection=(HttpURLConnection)new URL("http://127.0.0.1:"+api.port()+"/v1/"+endpoint).openConnection();
            connection.setConnectTimeout(2000);connection.setReadTimeout(5000);connection.setRequestMethod(method);
            if(token!=null)connection.setRequestProperty("X-Flipping-Friend-Token",token);
            if(account!=null)connection.setRequestProperty(AccountIdentity.HEADER,account);
            try {
                if(payload!=null){connection.setDoOutput(true);try(var out=connection.getOutputStream()){out.write(payload.getBytes(StandardCharsets.UTF_8));}}
                int status=connection.getResponseCode();var stream=status>=400?connection.getErrorStream():connection.getInputStream();
                String body=stream==null?"":new String(stream.readAllBytes(),StandardCharsets.UTF_8);
                assertEquals(AccountIdentity.PROTOCOL,connection.getHeaderField(AccountIdentity.PROTOCOL_HEADER));
                return new Response(status,body);
            }finally{connection.disconnect();}
        }
        Response post(JsonObject body)throws Exception{return request(route+"sale-maintenance","POST","fixture",helper.id,gson.toJson(body));}
        long count(String table)throws Exception {
            try(var db=DriverManager.getConnection("jdbc:sqlite:"+database.toAbsolutePath());var statement=db.createStatement();
                var rows=statement.executeQuery("SELECT COUNT(*) FROM "+table)){assertTrue(rows.next());return rows.getLong(1);}
        }
        @Override public void close()throws Exception{api.close();accounts.close();market.close();legacy.close();}
    }
    @Test public void maintenancePostRequiresAuthenticationExactAccountAndPostMethod()throws Exception {
        try(var f=new Fixture()) {
            String body=gson.toJson(f.requestBody());
            assertEquals(401,f.request(f.route+"sale-maintenance","POST",null,f.helper.id,body).status);
            assertEquals(401,f.request(f.route+"sale-maintenance","POST","wrong",f.helper.id,body).status);
            assertEquals(400,f.request(f.route+"sale-maintenance","POST","fixture",null,body).status);
            assertEquals(400,f.request(f.route+"sale-maintenance","POST","fixture",AccountIdentity.fromProfileKey("other"),body).status);
            assertEquals(405,f.request(f.route+"sale-maintenance","GET","fixture",f.helper.id,null).status);
            assertEquals(405,f.request(f.route+"sale-maintenance","PUT","fixture",f.helper.id,body).status);
            assertEquals(0,f.legacy.positionEventCount());
            assertFalse("Rejected scope must not create an account database",java.nio.file.Files.exists(f.database));
        }
    }
    @Test public void malformedAndClientInventedAuthorityFieldsNeverIssueAReceipt()throws Exception {
        try(var f=new Fixture()) {
            f.seed();long events=f.count("event_log"),effects=f.count("position_event");
            List<String> invalid=new ArrayList<>(List.of("{}","[]","null","{"," ".repeat(16385)));
            for(String field:List.of("requestId","item","price","quantity","source","cut","slot")) {
                var missing=f.requestBody();missing.remove(field);invalid.add(gson.toJson(missing));
                var nil=f.requestBody();nil.add(field,JsonNull.INSTANCE);invalid.add(gson.toJson(nil));
            }
            for(String value:List.of("\"119\"","119.0","1e2","-1","2147483648","9223372036854775808",
                    "18446744073709551616","99999999999999999999999999999999999999999999999999")) {
                var wrong=f.requestBody();wrong.add("price",JsonParser.parseString(value));invalid.add(gson.toJson(wrong));
            }
            for(String field:List.of("acceptedAt","patienceSeconds","salePolicyReceipt","accountId")) {
                var extra=f.requestBody();extra.addProperty(field,"client assertion");invalid.add(gson.toJson(extra));
            }
            var wrongCut=f.requestBody();wrongCut.addProperty("cut","false");invalid.add(gson.toJson(wrongCut));
            var wrongRequest=f.requestBody();wrongRequest.addProperty("requestId",true);invalid.add(gson.toJson(wrongRequest));
            var invalidId=f.requestBody();invalidId.addProperty("requestId","not-a-uuid");invalid.add(gson.toJson(invalidId));
            var invalidSlot=f.requestBody();invalidSlot.addProperty("slot",8);invalid.add(gson.toJson(invalidSlot));
            for(String body:invalid)assertEquals(body,400,f.request(f.route+"sale-maintenance","POST","fixture",f.helper.id,body).status);
            assertEquals(0,f.count("sale_policy_receipt"));assertEquals(events,f.count("event_log"));assertEquals(effects,f.count("position_event"));
        }
    }
    @Test public void authorizedGetIsReadOnlyAndOnlySuccessfulExactPostIssuesAnIdempotentReceipt()throws Exception {
        try(var f=new Fixture()) {
            f.seed();long events=f.count("event_log"),effects=f.count("position_event");
            String query="sale?item=561&price=119&quantity=3&source="+f.source.getEventId()+"&cut=false&slot=0";
            for(int retry=0;retry<2;retry++) {
                var read=f.request(f.route+query,"GET","fixture",f.helper.id,null);assertEquals(200,read.status);
                assertEquals(read.body,OfferLifecycleAction.CANCEL_SELL,read.decision().getAction());assertNull(read.decision().getSalePolicyReceipt());
                assertEquals(0,f.count("sale_policy_receipt"));
            }
            assertEquals(405,f.request(f.route+"sale","POST","fixture",f.helper.id,gson.toJson(f.requestBody())).status);
            var wrong=f.requestBody();wrong.addProperty("source","other-source");var refused=f.post(wrong);
            assertEquals(200,refused.status);assertEquals(OfferLifecycleAction.WAIT,refused.decision().getAction());assertNull(refused.decision().getSalePolicyReceipt());
            assertEquals(0,f.count("sale_policy_receipt"));
            var body=f.requestBody();var first=f.post(body);assertEquals(200,first.status);
            assertEquals(first.body,OfferLifecycleAction.CANCEL_SELL,first.decision().getAction());
            var receipt=first.decision().getSalePolicyReceipt();assertNotNull(first.body,receipt);assertTrue(receipt.isWellFormed());
            assertEquals(body.get("requestId").getAsString(),receipt.getReceiptId());assertEquals(f.helper.id,receipt.getAccountId());
            var retry=f.post(body);assertEquals(200,retry.status);assertEquals(retry.body,receipt,retry.decision().getSalePolicyReceipt());
            assertEquals(1,f.count("sale_policy_receipt"));
            f.request(f.route+query,"GET","fixture",f.helper.id,null);assertEquals(1,f.count("sale_policy_receipt"));
            var other=f.request("accounts/"+AccountIdentity.fromProfileKey("other")+"/policy/sale-maintenance","POST","fixture",
                AccountIdentity.fromProfileKey("other"),gson.toJson(body));
            assertEquals(200,other.status);assertEquals(OfferLifecycleAction.WAIT,other.decision().getAction());assertNull(other.decision().getSalePolicyReceipt());
            assertEquals(1,f.count("sale_policy_receipt"));assertEquals(events,f.count("event_log"));assertEquals(effects,f.count("position_event"));
            assertEquals(0,f.legacy.positionEventCount());
        }
    }
    @Test public void exactRetryAcrossSecondsKeepsItsReceiptButNeverRefreshesItsAcceptanceLifetime()throws Exception {
        try(var f=new Fixture()) {
            f.seed();long events=f.count("event_log"),effects=f.count("position_event");
            var body=f.requestBody();var first=f.post(body);var receipt=first.decision().getSalePolicyReceipt();assertNotNull(first.body,receipt);
            var service=f.accounts.forAccount(f.helper.id);String id=body.get("requestId").getAsString();
            for(long delay:new long[]{1,5}) {
                var retry=service.saleMaintenanceDecision(id,561,119,3,f.source.getEventId(),false,0,receipt.getDecidedAt()+delay);
                assertEquals(OfferLifecycleAction.CANCEL_SELL,retry.getAction());assertEquals(receipt,retry.getSalePolicyReceipt());
                assertEquals(receipt.getDecidedAt()+delay,retry.getDecidedAt());
            }
            var expired=service.saleMaintenanceDecision(id,561,119,3,f.source.getEventId(),false,0,receipt.getDecidedAt()+6);
            assertEquals(OfferLifecycleAction.WAIT,expired.getAction());assertEquals("SALE_POLICY_RECEIPT_EXPIRED",expired.getReasonCode());
            assertNull(expired.getSalePolicyReceipt());assertEquals(1,f.count("sale_policy_receipt"));
            assertEquals(events,f.count("event_log"));assertEquals(effects,f.count("position_event"));
        }
    }
}
