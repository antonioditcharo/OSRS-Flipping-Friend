package com.flippingfriend.companion;

import com.flippingfriend.core.*;
import com.flippingfriend.data.*;
import com.google.gson.Gson;
import java.time.Instant;
import java.util.Collections;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class StockReviewClientTest {
    @Rule public TemporaryFolder folder = new TemporaryFolder();
    @Test public void staleAccountOrPositionCannotBeDeclaredAndAcceptedReviewWithdrawsCachedBuyPlan() throws Exception {
        PluginStorage storage = TestStorage.rootedAt(folder.newFolder().toPath(), "one");
        CompanionClient client = new CompanionClient(storage, new Gson(), new SuggestionLedger());
        PositionProjection p = PositionProjection.reconcile(100, "Example", 3, 0, false, 10, 20, null, null, "unknown");
        AccountLedgerSnapshot ledger = new AccountLedgerSnapshot(storage.accountId(), Instant.now().getEpochSecond(),
            0, 0, 0, 0, 0, 0, 0, 0, Collections.singletonList(p));
        assertTrue(client.acceptLedger(ledger, storage.accountId()));
        assertFalse(client.reviewStock(AccountIdentity.fromProfileKey("other"), p, StockDeclaration.Use.PERSONAL));
        PositionProjection old = PositionProjection.reconcile(100, "Example", 2, 0, false, 10, 20, null, null, "unknown");
        assertFalse(client.reviewStock(storage.accountId(), old, StockDeclaration.Use.PERSONAL));
        client.acceptPlan(new PortfolioPlan("old", 0, Long.MAX_VALUE, "READY", "ok", 0, Collections.emptyList()));
        assertTrue(client.hasFreshPlan());
        long version = client.stockReviewVersion();
        assertTrue(client.reviewStock(storage.accountId(), p, StockDeclaration.Use.PERSONAL));
        assertFalse(client.hasFreshPlan()); assertEquals(version + 1, client.stockReviewVersion());
        assertFalse(client.acceptFetchedPlan(new PortfolioPlan("late", 0, Long.MAX_VALUE, "READY", "old review", 0,
            Collections.emptyList()), storage.accountId(), version));
        assertFalse(client.hasFreshPlan());
        assertTrue(client.stockReviews().get(100).matches(p));
        assertTrue(client.reviewStock(storage.accountId(), p, null)); assertTrue(client.stockReviews().isEmpty());
        client.acceptLedger(new AccountLedgerSnapshot(storage.accountId(), Instant.now().minusSeconds(151).getEpochSecond(),
            0, 0, 0, 0, 0, 0, 0, 0, Collections.singletonList(p)), storage.accountId());
        assertFalse(client.reviewStock(storage.accountId(), p, StockDeclaration.Use.PERSONAL));
    }
}
