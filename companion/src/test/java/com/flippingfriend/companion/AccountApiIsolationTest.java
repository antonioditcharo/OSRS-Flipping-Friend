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
