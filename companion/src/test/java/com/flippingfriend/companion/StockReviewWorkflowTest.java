package com.flippingfriend.companion;

import com.flippingfriend.core.*;
import com.google.gson.*;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import org.junit.Test;
import static org.junit.Assert.*;

/** Synthetic account fixtures only; never opens a live account or uses market HTTP. */
public class StockReviewWorkflowTest {
    @Rule public TemporaryFolder folder = new TemporaryFolder();
    private final Gson gson = new Gson();

    @Test public void personalSnapshotHoldingDoesNotBlockOtherBuysOrChangeLedger() throws Exception {
        checkReview("PERSONAL", false, true);
    }
    @Test public void playerRetiredOldHoldingDoesNotCreateDisposalOrProfit() throws Exception {
        checkReview("NO_LONGER_HELD", false, true);
    }
    @Test public void retiredUnpricedFullSaleRemainsUnresolvedWithoutBlockingPlanning() throws Exception {
        checkReview("NO_LONGER_HELD", true, true);
    }
    @Test public void unclassifiedUnknownHoldingStillBlocksPlanning() throws Exception {
        checkReview(null, false, false);
    }

    private void checkReview(String use, boolean sold, boolean allowed) throws Exception {
        SqliteStore store = new SqliteStore(folder.newFolder().toPath().resolve("test.db"));
        CompanionService service = new CompanionService(gson, store);
        try {
            AccountSnapshot seed = SnapshotReconciliationTest.account(100,
                new PositionSnapshot(4151, "Example trading stock", 2, 2000, true, 10, 0, 0, 0));
            store.recordAndReconcileAccount(seed, PositionStateView.from(seed), gson.toJson(seed));
            if (sold) {
                OfferEvent open = OfferEvent.builder("open", 109, "SELLING")
                    .eventIdentity("open-event", "session", "old-sale").sequence(1).slot(0)
                    .item(100, "Example old sale").buying(false).price(100).quantities(3, 0).spent(0).build();
                store.recordAndProjectOffer(open, gson.toJson(open));
                OfferEvent sale = OfferEvent.builder("sale", 110, "SOLD")
                    .eventIdentity("sale-event", "session", "old-sale").sequence(2).slot(0)
                    .item(100, "Example old sale").buying(false).price(100).quantities(3, 3).spent(300).build();
                store.recordAndProjectOffer(sale, gson.toJson(sale));
            } else {
                AccountSnapshot unknown = SnapshotReconciliationTest.account(110,
                    new PositionSnapshot(100, "Example unpriced holding", 3, 0, false, 10, 0, 0, 0));
                store.recordAndReconcileAccount(unknown, PositionStateView.from(unknown), gson.toJson(unknown));
            }
            var before = store.ledgerSnapshot(AccountIdentity.fromProfileKey("synthetic-review"));
            PositionProjection reviewed = before.getPositions().stream()
                .filter(p -> p.getItemId() == 100).findFirst().get();
            JsonObject payload = gson.toJsonTree(seed).getAsJsonObject();
            if (use != null) {
                JsonObject declaration = gson.toJsonTree(reviewed).getAsJsonObject();
                declaration.addProperty("use", use);
                JsonObject declarations = new JsonObject();
                declarations.add("100", declaration);
                payload.add("stockDeclarations", declarations);
            }
            AccountSnapshot snapshot = gson.fromJson(payload, AccountSnapshot.class);
            Field account = CompanionService.class.getDeclaredField("account");
            account.setAccessible(true); account.set(service, snapshot);
            Method refresh = CompanionService.class.getDeclaredMethod("refreshPlan");
            refresh.setAccessible(true); refresh.invoke(service);
            Field plan = CompanionService.class.getDeclaredField("plan");
            plan.setAccessible(true);
            PortfolioPlan result = (PortfolioPlan) plan.get(service);
            assertNotNull(result);
            if (allowed) assertNotEquals("explicit stock intent should permit exposure calculation",
                PortfolioPlanOutcome.COMPANION_STATE_UNAVAILABLE, result.getOutcome());
            else assertEquals(PortfolioPlanOutcome.COMPANION_STATE_UNAVAILABLE, result.getOutcome());
            var after = store.ledgerSnapshot(AccountIdentity.fromProfileKey("synthetic-review"));
            assertEquals(gson.toJson(before.getPositions()), gson.toJson(after.getPositions()));
            assertEquals(before.getSales(), after.getSales());
            assertEquals(before.getUnresolvedSales(), after.getUnresolvedSales());
            assertEquals(before.getProfitLower(), after.getProfitLower());
            assertEquals(before.getProfitUpper(), after.getProfitUpper());
            if (sold) assertEquals(1L, after.getUnresolvedSales());
        } finally { service.close(); }
    }
}
