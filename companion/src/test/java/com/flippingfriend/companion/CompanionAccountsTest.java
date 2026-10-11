package com.flippingfriend.companion;

import com.flippingfriend.core.AccountIdentity;
import com.flippingfriend.core.AccountSnapshot;
import com.flippingfriend.core.PositionSnapshot;
import com.flippingfriend.core.OfferEvent;
import com.flippingfriend.core.CompanionActionType;
import com.google.gson.Gson;
import java.nio.file.Path;
import java.time.Instant;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class CompanionAccountsTest
{
    @Rule public final TemporaryFolder folder = new TemporaryFolder();
    private final String one = AccountIdentity.fromProfileKey("one");
    private final String two = AccountIdentity.fromProfileKey("two");

    private OfferEvent buy(String account, int quantity, int price, long time)
    {
        return OfferEvent.builder("same-correlation", time, "BOUGHT").accountId(account)
            .eventIdentity("same-event", "same-session", "same-offer").slot(0)
            .item(561, "Nature rune").buying(true).price(price).quantities(quantity, quantity)
            .spent((long) quantity * price).sequence(2).firstSeenAt(time).build();
    }

    private OfferEvent start(String account, int quantity, int price, long time)
    {
        return OfferEvent.builder("same-correlation", time, "BUYING").accountId(account)
            .eventIdentity("start-event", "same-session", "same-offer").slot(0)
            .item(561, "Nature rune").buying(true).price(price).quantities(quantity, 0)
            .spent(0).sequence(1).firstSeenAt(time).build();
    }

    @Test public void sameItemSlotAndEventIdsStaySeparateAcrossRestart() throws Exception
    {
        Path root = folder.newFolder().toPath();
        Path accountsRoot = root.resolve("accounts");
        try (SqliteStore legacy = new SqliteStore(root.resolve("legacy.db"));
             CompanionService market = new CompanionService(new Gson(), legacy))
        {
            long time = Instant.now().minusSeconds(9 * 3600).getEpochSecond();
            try (CompanionAccounts accounts = new CompanionAccounts(accountsRoot, market))
            {
                accounts.forAccount(one).offer(start(one, 100, 90, time));
                accounts.forAccount(two).offer(start(two, 50, 110, time));
                assertEquals(SqliteStore.EventAcceptance.NEW, accounts.forAccount(one).offer(buy(one, 100, 90, time)));
                assertEquals(SqliteStore.EventAcceptance.NEW, accounts.forAccount(two).offer(buy(two, 50, 110, time)));
                assertEquals(SqliteStore.EventAcceptance.DUPLICATE, accounts.forAccount(one).offer(buy(one, 100, 90, time)));
                assertEquals(100, accounts.forAccount(one).action().getQuantity());
                assertEquals(50, accounts.forAccount(two).action().getQuantity());
                try { accounts.forAccount(two).offer(buy(one, 7, 100, time)); fail("wrong account accepted"); }
                catch (IllegalArgumentException expected) { }
            }
            try (CompanionAccounts restarted = new CompanionAccounts(accountsRoot, market))
            {
                assertEquals(CompanionActionType.COLLECT, restarted.forAccount(one).action().getType());
                assertEquals(100, restarted.forAccount(one).action().getQuantity());
                assertEquals(50, restarted.forAccount(two).action().getQuantity());
                assertEquals(SqliteStore.EventAcceptance.DUPLICATE, restarted.forAccount(one).offer(buy(one, 100, 90, time)));
            }
        }
        try (SqliteStore first = new SqliteStore(accountsRoot.resolve(one).resolve("flipping-friend.db"));
             SqliteStore second = new SqliteStore(accountsRoot.resolve(two).resolve("flipping-friend.db")))
        {
            assertEquals(100, first.positionProjections().get(0).getQuantity());
            assertEquals(9000, first.positionProjections().get(0).getTotalCost());
            assertEquals(50, second.positionProjections().get(0).getQuantity());
            assertEquals(5500, second.positionProjections().get(0).getTotalCost());
            assertEquals(1, first.executionStats().get(561).observed);
            assertEquals(1, second.executionStats().get(561).observed);
        }
    }

    @Test public void legacyUnscopedRowsArePreservedAndNeverAssignedToAnAccount() throws Exception
    {
        Path root = folder.newFolder().toPath();
        try (SqliteStore legacy = new SqliteStore(root.resolve("legacy.db"));
             CompanionService market = new CompanionService(new Gson(), legacy))
        {
            market.offer(start(one, 7, 100, Instant.now().getEpochSecond()));
            market.offer(buy(one, 7, 100, Instant.now().getEpochSecond()));
            try (CompanionAccounts accounts = new CompanionAccounts(root.resolve("accounts"), market))
            {
                assertEquals(CompanionActionType.WAIT, accounts.forAccount(one).action().getType());
                assertEquals(7, legacy.positionProjections().get(0).getQuantity());
                try { accounts.forAccount("../legacy"); fail("invalid path accepted"); }
                catch (IllegalArgumentException expected) { }
            }
        }
    }

    @Test public void initialPartialOfferThenSnapshotDoesNotDoubleCountOwnedStock() throws Exception
    {
        Path root = folder.newFolder().toPath();
        long now = Instant.now().getEpochSecond();
        try (SqliteStore legacy = new SqliteStore(root.resolve("market.db"));
             CompanionService market = new CompanionService(new Gson(), legacy);
             CompanionAccounts accounts = new CompanionAccounts(root.resolve("accounts"), market))
        {
            CompanionService service = accounts.forAccount(one);
            service.offer(OfferEvent.builder("c", now, "BUYING").accountId(one)
                .eventIdentity("initial", "s", "o").slot(0).item(561, "Nature rune").buying(true)
                .price(90).quantities(200, 100).spent(9000).sequence(1).build());
            assertTrue(service.health().getReason().contains("Position projection item 561"));
            AccountSnapshot snapshot = new AccountSnapshot("snapshot", now, 100_000_000, 0,
                7, 8, true, true, 0, "MODERATE", java.util.Collections.emptyMap(), 0,
                java.util.Collections.emptyMap(), false, 5, java.util.Collections.emptySet(),
                java.util.Collections.emptySet(), java.util.Collections.emptySet(), false, 0,
                java.util.Collections.singletonList(new PositionSnapshot(561, "Nature rune", 100, 9000, true, now, 100, 80, 10)))
                .withAccountId(one);
            service.account(snapshot);
            assertFalse(service.health().getReason().contains("Position projection item 561"));
            service.offer(OfferEvent.builder("c2", now + 1, "BUYING").accountId(one)
                .eventIdentity("next", "s", "o").slot(0).item(561, "Nature rune").buying(true)
                .price(90).quantities(200, 110).spent(9900).sequence(2).build());
            try { accounts.forAccount(two).account(snapshot); fail("foreign snapshot accepted"); }
            catch (IllegalArgumentException expected) { }
        }
        try (SqliteStore store = new SqliteStore(root.resolve("accounts").resolve(one).resolve("flipping-friend.db")))
        {
            assertEquals(110, store.positionProjections().get(0).getQuantity());
            assertEquals(9900, store.positionProjections().get(0).getTotalCost());
        }
    }
}
