package com.flippingfriend.companion;

import com.flippingfriend.core.AccountIdentity;
import com.flippingfriend.core.OfferEvent;
import com.google.gson.Gson;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class AccountApiIsolationTest
{
    @Rule public final TemporaryFolder folder = new TemporaryFolder();
    @Test public void saleCostAmendmentRequiresAuthenticationExactScopeAndExplicitProvenSources()throws Exception {
        var fixture=new SaleCostAmendmentsTest();fixture.folder=folder;Path original=fixture.fixture(true),root=folder.newFolder().toPath();
        String one=fixture.account,two=AccountIdentity.fromProfileKey("other");Path scoped=root.resolve("accounts").resolve(one);
        java.nio.file.Files.createDirectories(scoped);java.nio.file.Files.copy(original,scoped.resolve("flipping-friend.db"));Gson gson=new Gson();
        try(var legacy=new SqliteStore(root.resolve("legacy.db"));var market=new CompanionService(gson,legacy);
                var accounts=new CompanionAccounts(root.resolve("accounts"),market);var api=new ApiServer("test-token",gson,market,accounts,new InetSocketAddress("127.0.0.1",0))) {
            api.start();String path="accounts/"+one+"/ledger/sale-cost-amendments";
            String body=gson.toJson(java.util.Map.of("accountId",one,"saleSourceEventIds",java.util.List.of("sold")));
            assertEquals(405,request(api,path,one,null));assertEquals(400,request(api,path,two,body));assertEquals(400,request(api,path,one,"{}"));
            var unauthorized=connection(api,path,one);unauthorized.setRequestProperty("X-Flipping-Friend-Token","wrong");
            try{assertEquals(401,unauthorized.getResponseCode());}finally{unauthorized.disconnect();}
            assertEquals(200,request(api,path,one,body));assertEquals(200,request(api,path,one,body));
            var ledger=gson.fromJson(body(api,"accounts/"+one+"/ledger/current",one),com.flippingfriend.core.AccountLedgerSnapshot.class);
            assertEquals(0,ledger.getUnresolvedSales());assertEquals(1797,ledger.getProfitLower());assertEquals(0,legacy.positionEventCount());
        }
    }
    @Test public void tradeReviewAnnotationsRequireAuthenticatedExactAccountScopeAndNeverPostAnOffer()throws Exception {
        Gson gson=new Gson();Path root=folder.newFolder().toPath();String one=AccountIdentity.fromProfileKey("review-api"),two=AccountIdentity.fromProfileKey("other");
        try(var legacy=new SqliteStore(root.resolve("legacy.db"));var market=new CompanionService(gson,legacy);
            var accounts=new CompanionAccounts(root.resolve("accounts"),market);var api=new ApiServer("test-token",gson,market,accounts,new InetSocketAddress("127.0.0.1",0))) {
            api.start();String read="accounts/"+one+"/trade-review/current",write="accounts/"+one+"/trade-review";
            assertEquals(400,request(api,read,two,null));assertEquals(405,request(api,read,one,"{}"));assertEquals(405,request(api,write,one,null));
            var unauthorized=connection(api,read,one);unauthorized.setRequestProperty("X-Flipping-Friend-Token","wrong");
            try{assertEquals(401,unauthorized.getResponseCode());}finally{unauthorized.disconnect();}
            var review=gson.fromJson(body(api,read,one),com.flippingfriend.core.TradeReviewSnapshot.class);assertTrue(review.isValid());assertTrue(review.states.isEmpty());
            String bad=gson.toJson(new com.flippingfriend.core.TradeReviewSnapshot.Request(two,"wrong-account",1,true,java.util.List.of("source")));
            assertEquals(400,request(api,write,one,bad));assertEquals(400,request(api,write,one,"{}"));
            long at=Instant.now().getEpochSecond()-10;
            var open=OfferEvent.builder("fixture",at,"SELLING").accountId(one).eventIdentity("open","session","sale")
                .slot(0).item(561,"Nature rune").price(120).quantities(3,0).sequence(1).build();
            var sold=OfferEvent.builder("fixture",at+1,"SOLD").accountId(one).eventIdentity("sold","session","sale")
                .slot(0).item(561,"Nature rune").price(120).quantities(3,3).spent(360).sequence(2).build();
            var empty=OfferEvent.builder("fixture",at+2,"EMPTY").accountId(one).eventIdentity("empty","session",null).slot(0).sequence(3).build();
            for(var event:java.util.List.of(open,sold,empty))assertEquals(202,request(api,"accounts/"+one+"/events/ge-offer",one,gson.toJson(event)));
            review=gson.fromJson(body(api,read,one),com.flippingfriend.core.TradeReviewSnapshot.class);
            assertEquals(com.flippingfriend.core.TradeReviewSnapshot.ELIGIBLE,review.states.get("sold"));
            String archive=gson.toJson(new com.flippingfriend.core.TradeReviewSnapshot.Request(one,"archive",review.positionCursor,true,java.util.List.of("sold")));
            assertEquals(200,request(api,write,one,archive));assertEquals(200,request(api,write,one,archive));
            review=gson.fromJson(body(api,read,one),com.flippingfriend.core.TradeReviewSnapshot.class);
            assertEquals(com.flippingfriend.core.TradeReviewSnapshot.ARCHIVED,review.states.get("sold"));
            var ledger=gson.fromJson(body(api,"accounts/"+one+"/ledger/current",one),com.flippingfriend.core.AccountLedgerSnapshot.class);
            assertEquals(1,ledger.getUnresolvedSales());assertEquals(360L,ledger.getRecentSales().get(0).getGross().longValue());assertNull(ledger.getRecentSales().get(0).getProfitLower());
            assertEquals(0,legacy.positionEventCount());assertEquals(0,legacy.eventCount(TradeReviewRead.EVENT_TYPE));
        }
    }

    @Test public void requestsRequireScopeAndCannotReadOrWriteAnotherAccountsOffer() throws Exception
    {
        Gson gson = new Gson();
        Path root = folder.newFolder().toPath();
        String one = AccountIdentity.fromProfileKey("one"), two = AccountIdentity.fromProfileKey("two");
        try (SqliteStore legacy = new SqliteStore(root.resolve("legacy.db"));
             CompanionService market = new CompanionService(gson, legacy);
             CompanionAccounts accounts = new CompanionAccounts(root.resolve("accounts"), market);
             ApiServer api = new ApiServer("test-token", gson, market, accounts, new InetSocketAddress("127.0.0.1", 0)))
        {
            api.start();
            OfferEvent event = OfferEvent.builder("c", Instant.now().getEpochSecond(), "BOUGHT")
                .accountId(one).eventIdentity("e", "s", "o").slot(0).item(561, "Nature rune")
                .buying(true).price(100).quantities(4, 4).spent(400).sequence(1).build();
            assertEquals(400, request(api, "action", null, null));
            assertEquals(400, request(api, "events/ge-offer", two, gson.toJson(event)));
            assertEquals(202, request(api, "events/ge-offer", one, gson.toJson(event)));
            assertEquals(202, request(api, "events/ge-offer", one, gson.toJson(event)));
            assertTrue(body(api, "action", one).contains("COLLECT"));
            assertFalse(body(api, "action", two).contains("COLLECT"));
            assertTrue(body(api, "accounts/" + one + "/action", one).contains("COLLECT"));
            assertEquals(400, request(api, "accounts/" + one + "/events/ge-offer", two, gson.toJson(event)));
            assertEquals(400, request(api, "events/ge-offer", one, "{}"));
            com.flippingfriend.core.AccountLedgerSnapshot first = gson.fromJson(
                body(api, "accounts/" + one + "/ledger/current", one), com.flippingfriend.core.AccountLedgerSnapshot.class);
            com.flippingfriend.core.AccountLedgerSnapshot second = gson.fromJson(
                body(api, "accounts/" + two + "/ledger/current", two), com.flippingfriend.core.AccountLedgerSnapshot.class);
            assertEquals(one, first.getAccountId()); assertTrue(first.isValid());
            assertEquals(1, first.getPositions().size()); assertTrue(second.getPositions().isEmpty());
            assertEquals(400, request(api, "accounts/" + one + "/ledger/current", two, null));
        }
    }

    @Test public void unidentifiedClearReturnsReadableScopedEvidenceInsteadOfHttp500() throws Exception
    {
        Gson gson = new Gson();
        Path root = folder.newFolder().toPath();
        String one = AccountIdentity.fromProfileKey("unidentified"), two = AccountIdentity.fromProfileKey("other");
        try (SqliteStore legacy = new SqliteStore(root.resolve("legacy.db"));
             CompanionService market = new CompanionService(gson, legacy);
             CompanionAccounts accounts = new CompanionAccounts(root.resolve("accounts"), market);
             ApiServer api = new ApiServer("test-token", gson, market, accounts, new InetSocketAddress("127.0.0.1", 0)))
        {
            api.start();
            long now = Instant.now().getEpochSecond();
            OfferEvent open = OfferEvent.builder("c",now,"BUYING").accountId(one)
                .eventIdentity("open","session","buy").slot(0).item(561,"Nature rune")
                .buying(true).price(100).quantities(10,0).spent(0).sequence(1).build();
            OfferEvent clear = OfferEvent.builder("c",now,"EMPTY").accountId(one)
                .eventIdentity("unidentified-clear","session",null).slot(0).sequence(2).build();
            assertEquals(202,request(api,"events/ge-offer",one,gson.toJson(open)));
            assertEquals(202,request(api,"events/ge-offer",one,gson.toJson(clear)));
            assertEquals(200,request(api,"accounts/"+one+"/ledger/current",one,null));
            var ledger = gson.fromJson(body(api,"accounts/"+one+"/ledger/current",one),
                com.flippingfriend.core.AccountLedgerSnapshot.class);
            assertTrue(ledger.isValid()); assertEquals(one,ledger.getAccountId());
            assertEquals(0,ledger.getSales()); assertTrue(ledger.getTradingPositions().isEmpty());
            assertEquals(1,ledger.getMissedFlips().size());
            assertEquals("Unidentified offer",ledger.getMissedFlips().get(0).getItemName());
            assertTrue(ledger.getMissedFlips().get(0).getReason().contains("before a terminal"));
            var other = gson.fromJson(body(api,"accounts/"+two+"/ledger/current",two),
                com.flippingfriend.core.AccountLedgerSnapshot.class);
            assertTrue(other.isValid()); assertTrue(other.getMissedFlips().isEmpty());
            assertEquals(400,request(api,"accounts/"+one+"/ledger/current",two,null));
        }
    }

    @Test public void executionLearningReadoutRequiresExactAccountScopeAndAllowsNoWrites()throws Exception {
        Gson gson=new Gson();Path root=folder.newFolder().toPath();
        String one=AccountIdentity.fromProfileKey("timing-one"),two=AccountIdentity.fromProfileKey("timing-two");
        try(var legacy=new SqliteStore(root.resolve("legacy.db"));var market=new CompanionService(gson,legacy);
            var accounts=new CompanionAccounts(root.resolve("accounts"),market);
            var api=new ApiServer("test-token",gson,market,accounts,new InetSocketAddress("127.0.0.1",0))) {
            api.start();long at=Instant.now().getEpochSecond()-100;
            var birth=OfferEvent.builder("fixture",at,"BUYING").accountId(one).eventIdentity("birth","s","o")
                .authenticatedObservation(true).sequence(1).slot(0).item(561,"Nature rune").buying(true).price(100)
                .quantities(10,0).firstSeenAt(at).recommendation("p",100,10,0,12,.75).predictionEvidence("birth",at-2,true).build();
            var done=OfferEvent.builder("fixture",at+50,"BOUGHT").accountId(one).eventIdentity("done","s","o")
                .authenticatedObservation(true).sequence(2).slot(0).item(561,"Nature rune").buying(true).price(100)
                .quantities(10,10).spent(1000).firstSeenAt(at).recommendation("p",100,10,0,12,.75).predictionEvidence("birth",at-2,true).build();
            assertEquals(202,request(api,"accounts/"+one+"/events/ge-offer",one,gson.toJson(birth)));
            assertEquals(202,request(api,"accounts/"+one+"/events/ge-offer",one,gson.toJson(done)));
            String endpoint="accounts/"+one+"/learning/current";
            assertEquals(400,request(api,endpoint,two,null));assertEquals(400,request(api,endpoint,null,null));
            var unauthorized=connection(api,endpoint,one);unauthorized.setRequestProperty("X-Flipping-Friend-Token","wrong");
            try {assertEquals(401,unauthorized.getResponseCode());}finally{unauthorized.disconnect();}
            assertEquals(405,request(api,endpoint,one,"{}"));
            var report=gson.fromJson(body(api,endpoint,one),com.flippingfriend.core.ExecutionLearningReport.class);
            assertEquals("1",report.getSchemaVersion());assertEquals(one,report.getAccountId());
            assertEquals("PUBLIC_PRICE_BASELINE",report.getActiveModel());assertEquals("HOLD_FOR_VALIDATION",report.getPromotionStatus());
            assertEquals(1,report.getCompletedIntervals());assertEquals(1,report.getComparableForecasts());
            assertNull(report.getObservations().get(0).getDurationUpperSeconds()); // No observed EMPTY placement bound.
            var other=gson.fromJson(body(api,"accounts/"+two+"/learning/current",two),com.flippingfriend.core.ExecutionLearningReport.class);
            assertEquals(two,other.getAccountId());assertTrue(other.getObservations().isEmpty());
            var ledger=gson.fromJson(body(api,"accounts/"+one+"/ledger/current",one),com.flippingfriend.core.AccountLedgerSnapshot.class);
            assertEquals(10,ledger.getPositions().get(0).getQuantity());assertEquals(0,ledger.getSales());
        }
    }

    @Test public void performanceReadsAndMeasurementResetRequireTheExactAuthenticatedAccount()throws Exception {
        Gson gson=new Gson();Path root=folder.newFolder().toPath();
        String one=AccountIdentity.fromProfileKey("performance-one"),two=AccountIdentity.fromProfileKey("performance-two");
        try(var legacy=new SqliteStore(root.resolve("legacy.db"));var market=new CompanionService(gson,legacy);
            var accounts=new CompanionAccounts(root.resolve("accounts"),market);
            var api=new ApiServer("test-token",gson,market,accounts,new InetSocketAddress("127.0.0.1",0))) {
            api.start();String endpoint="accounts/"+one+"/performance/current?scope=ALL";
            assertEquals(400,request(api,endpoint,two,null));assertEquals(400,request(api,endpoint,null,null));
            assertEquals(405,request(api,endpoint,one,"{}"));
            var unauthorized=connection(api,endpoint,one);unauthorized.setRequestProperty("X-Flipping-Friend-Token","wrong");
            try{assertEquals(401,unauthorized.getResponseCode());}finally{unauthorized.disconnect();}
            var report=gson.fromJson(body(api,endpoint,one),com.flippingfriend.core.PerformanceSnapshot.class);
            assertTrue(report.isValid());assertEquals(one,report.accountId);assertEquals(0,report.saleUpdates);
            assertEquals(400,request(api,"accounts/"+one+"/performance/current?scope=invalid",one,null));
            String reset="accounts/"+one+"/performance/session";
            assertEquals(405,request(api,reset,one,null));assertEquals(400,request(api,reset,two,"{\"requestId\":\"test\"}"));
            assertEquals("A reset cannot invent a current capture",400,request(api,reset,one,"{\"requestId\":\"test\"}"));
            assertEquals(0,legacy.eventCount(PerformanceRead.SESSION_EVENT));assertEquals(0,legacy.positionEventCount());
        }
    }
    private HttpURLConnection connection(ApiServer api, String endpoint, String account) throws Exception
    {
        HttpURLConnection connection = (HttpURLConnection) new URL("http://127.0.0.1:" + api.port() + "/v1/" + endpoint).openConnection();
        connection.setConnectTimeout(2000); connection.setReadTimeout(2000);
        connection.setRequestProperty("X-Flipping-Friend-Token", "test-token");
        if (account != null) connection.setRequestProperty(AccountIdentity.HEADER, account);
        return connection;
    }

    private int request(ApiServer api, String endpoint, String account, String body) throws Exception
    {
        HttpURLConnection connection = connection(api, endpoint, account);
        try
        {
            if (body != null)
            {
                connection.setRequestMethod("POST"); connection.setDoOutput(true);
                try (java.io.OutputStream output = connection.getOutputStream()) { output.write(body.getBytes(StandardCharsets.UTF_8)); }
            }
            return connection.getResponseCode();
        }
        finally { connection.disconnect(); }
    }

    private String body(ApiServer api, String endpoint, String account) throws Exception
    {
        HttpURLConnection connection = connection(api, endpoint, account);
        try (java.io.InputStream input = connection.getInputStream())
        {
            assertEquals(account, connection.getHeaderField(AccountIdentity.HEADER));
            assertEquals(AccountIdentity.PROTOCOL, connection.getHeaderField(AccountIdentity.PROTOCOL_HEADER));
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
        finally { connection.disconnect(); }
    }
}
