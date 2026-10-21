package com.flippingfriend.companion;

import com.flippingfriend.core.AccountIdentity;
import com.flippingfriend.core.OfferEvent;
import com.google.gson.Gson;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class ApiHealthIsolationTest
{
    @Rule public final TemporaryFolder folder = new TemporaryFolder();

    @Test public void authenticatedGlobalHealthSurvivesTwoBlockedAccountRequests() throws Exception
    {
        Gson gson = new Gson(); Path root = folder.newFolder().toPath();
        String account = AccountIdentity.fromProfileKey("busy-health");
        ExecutorService clients = Executors.newFixedThreadPool(2);
        try (var store = new SqliteStore(root.resolve("legacy.db"));
             var market = new CompanionService(gson, store);
             var accounts = new CompanionAccounts(root.resolve("accounts"), market);
             var api = new ApiServer("secret", gson, market, accounts, new InetSocketAddress("127.0.0.1", 0)))
        {
            api.start(); Future<Integer> first, second;
            synchronized (accounts)
            {
                first = clients.submit(() -> request(api, "action", account, "secret", null));
                second = clients.submit(() -> request(api, "health", account, "secret", null));
                awaitBusy(api);
                assertEquals(200, request(api, "health", null, "secret", null));
                assertEquals(401, request(api, "health", null, "wrong", null));
                assertEquals(401, request(api, "events/ge-offer", account, "wrong", "{}"));
                assertFalse("Scoped health still requires its account initialization", second.isDone());
                assertEquals(0, store.positionEventCount());
            }
            assertEquals(200, first.get(10, TimeUnit.SECONDS).intValue());
            assertEquals(200, second.get(10, TimeUnit.SECONDS).intValue());
        }
        finally { clients.shutdownNow(); }
    }

    @Test public void overloadDoesNotAcknowledgeOrLoseAnOfferAndSameIdentityRetryCommitsOnce() throws Exception
    {
        Gson gson = new Gson(); Path root = folder.newFolder().toPath();
        String account = AccountIdentity.fromProfileKey("busy-retry");
        ExecutorService clients = Executors.newFixedThreadPool(2);
        try (var store = new SqliteStore(root.resolve("legacy.db"));
             var market = new CompanionService(gson, store);
             var accounts = new CompanionAccounts(root.resolve("accounts"), market);
             var api = new ApiServer("secret", gson, market, accounts, new InetSocketAddress("127.0.0.1", 0)))
        {
            api.start(); Future<Integer> first, second;
            long at = java.time.Instant.now().getEpochSecond() - 10;
            var event = OfferEvent.builder("fixture", at, "BUYING")
                .accountId(account).eventIdentity("retry-once", "session", "offer").slot(0)
                .item(561, "Nature rune").buying(true).price(100).quantities(4, 0).firstSeenAt(at)
                .authenticatedObservation(true).sequence(1).build();
            String payload = gson.toJson(event);
            synchronized (accounts)
            {
                first = clients.submit(() -> request(api, "action", account, "secret", null));
                second = clients.submit(() -> request(api, "action", account, "secret", null));
                ThreadPoolExecutor workers = awaitBusy(api);
                // Hold both real account requests at initialization, then fill the bounded pending queue.
                for (int i = 0; i < 64; i++) workers.execute(() -> { });
                assertEquals(0, workers.getQueue().remainingCapacity());
                assertEquals(503, request(api, "events/ge-offer", account, "secret", payload));
                assertEquals(200, request(api, "health", null, "secret", null));
            }
            assertEquals(200, first.get(10, TimeUnit.SECONDS).intValue());
            assertEquals(200, second.get(10, TimeUnit.SECONDS).intValue());
            assertEquals(202, request(api, "events/ge-offer", account, "secret", payload));
            assertEquals(202, request(api, "events/ge-offer", account, "secret", payload));
            var filled = OfferEvent.builder("fixture", at + 1, "BOUGHT")
                .accountId(account).eventIdentity("filled-once", "session", "offer").slot(0)
                .item(561, "Nature rune").buying(true).price(100).quantities(4, 4).spent(400).firstSeenAt(at)
                .authenticatedObservation(true).sequence(2).build();
            assertEquals(202, request(api, "events/ge-offer", account, "secret", gson.toJson(filled)));
            assertEquals(202, request(api, "events/ge-offer", account, "secret", gson.toJson(filled)));
            var ledger = accounts.forAccount(account).ledger();
            assertEquals(1, ledger.getPositions().size());
            assertEquals(4, ledger.getPositions().get(0).getQuantity());
            assertEquals(400L, ledger.getPositions().get(0).getTotalCost());
            assertEquals(0, store.positionEventCount());
        }
        finally { clients.shutdownNow(); }
    }

    @Test public void refreshingAnAccountDoesNotScanDiagnosticHistoryButExplicitReadStillDoes() throws Exception
    {
        Gson gson = new Gson(); Path root = folder.newFolder().toPath(); AtomicInteger scans = new AtomicInteger();
        String account = AccountIdentity.fromProfileKey("on-demand-diagnostics");
        try (var store = new SqliteStore(root.resolve("account.db"), checkpoint -> {
                if ("EXECUTION_SOURCE_OFFERS_READ".equals(checkpoint)) scans.incrementAndGet();
             });
             var market = new CompanionService(gson, store);
             var scoped = market.forAccountStore(store, account))
        {
            scoped.rehydrate(); scoped.refreshAccountPlan(); scoped.refreshAccountPlan();
            assertEquals(0, scans.get());
            assertEquals("OBSERVING", scoped.learningEvidence().getStatus());
            assertEquals(1, scans.get());
            scoped.refreshAccountPlan();
            assertEquals(1, scans.get());
        }
    }

    private ThreadPoolExecutor awaitBusy(ApiServer api) throws Exception
    {
        var field = ApiServer.class.getDeclaredField("accountExecutor"); field.setAccessible(true);
        ThreadPoolExecutor workers = (ThreadPoolExecutor) field.get(api);
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (workers.getActiveCount() < 2 && System.nanoTime() < deadline) Thread.sleep(5);
        assertEquals("Both requests must enter account work before the health probe", 2, workers.getActiveCount());
        return workers;
    }

    private int request(ApiServer api, String endpoint, String account, String token, String payload) throws Exception
    {
        var connection = (HttpURLConnection) new URL("http://127.0.0.1:" + api.port() + "/v1/" + endpoint).openConnection();
        connection.setConnectTimeout(2000); connection.setReadTimeout(5000);
        connection.setRequestProperty("X-Flipping-Friend-Token", token);
        if (account != null) connection.setRequestProperty(AccountIdentity.HEADER, account);
        try
        {
            if (payload != null)
            {
                connection.setRequestMethod("POST"); connection.setDoOutput(true);
                try (var output = connection.getOutputStream()) { output.write(payload.getBytes(StandardCharsets.UTF_8)); }
            }
            int code = connection.getResponseCode();
            assertEquals(AccountIdentity.PROTOCOL, connection.getHeaderField(AccountIdentity.PROTOCOL_HEADER));
            return code;
        }
        finally { connection.disconnect(); }
    }
}
