package com.flippingfriend.companion;

import com.flippingfriend.core.*;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.lang.reflect.*;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import java.time.Instant;
import java.util.Collections;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.TimeUnit;
import org.junit.Test;
import static org.junit.Assert.*;

public class StockReviewHealthTest {
    @Rule public TemporaryFolder folder = new TemporaryFolder();
    @Test public void freshScopeSeparatesUnresolvedHistoryFromOtherFaults() throws Exception {
        SqliteStore store = new SqliteStore(folder.newFolder().toPath().resolve("test.db"));
        CompanionService service = new CompanionService(new Gson(), store);
        try {
            AccountSnapshot raw = SnapshotReconciliationTest.account(100,
                new PositionSnapshot(100, "Synthetic supplies", 3, 0, false, 10, 0, 0, 0));
            store.recordAndReconcileAccount(raw, PositionStateView.from(raw), "raw");
            set(service, "account", raw);
            Field marketField = CompanionService.class.getDeclaredField("market"); marketField.setAccessible(true);
            Object market = marketField.get(service);
            Field stateField = MarketIngestionService.class.getDeclaredField("state"); stateField.setAccessible(true);
            long now = Instant.now().getEpochSecond();
            stateField.set(market, new MarketIngestionService.MarketState(Collections.emptyMap(), new JsonObject(),
                new JsonObject(), new JsonObject(), now));
            Method fault = CompanionService.class.getDeclaredMethod("fault", String.class, String.class, Throwable.class);
            fault.setAccessible(true); fault.invoke(service, "Position projection item 100", "unknown cost", null);
            assertFalse(service.health().canReadPlan(now));
            PositionProjection p = store.positionProjections().get(0);
            set(service, "account", current(store,raw).withStockDeclarations(Collections.singletonMap(100,
                new StockDeclaration(p, StockDeclaration.Use.PERSONAL))));
            CompanionHealth health = service.health();
            assertEquals("DEGRADED", health.getStatus()); assertTrue(health.getReason().contains("unknown cost"));
            assertTrue(health.canReadPlan(Instant.now().getEpochSecond()));
            fault.invoke(service, "Plan persistence", "disk failure", null);
            assertFalse(service.health().canReadPlan(Instant.now().getEpochSecond()));
        } finally { service.close(); }
    }
    @Test public void removingReviewImmediatelyWithdrawsCachedReadyPlanBeforeWorkerRuns() throws Exception {
        SqliteStore store = new SqliteStore(folder.newFolder().toPath().resolve("test.db"));
        CompanionService service = new CompanionService(new Gson(), store);
        try {
            AccountSnapshot raw = SnapshotReconciliationTest.account(100,
                new PositionSnapshot(100, "Synthetic supplies", 3, 0, false, 10, 0, 0, 0));
            store.recordAndReconcileAccount(raw, PositionStateView.from(raw), "raw");
            set(service, "account", current(store,raw).withStockDeclarations(Collections.singletonMap(100,
                new StockDeclaration(store.positionProjections().get(0), StockDeclaration.Use.PERSONAL))));
            PortfolioPlan ready = new PortfolioPlan("old", 0, Long.MAX_VALUE, "READY", "old review", 0, Collections.emptyList());
            set(service, "plan", ready); set(service, "planThread", new PausedExecutor());
            var existing=(AccountSnapshot)get(service,"account");
            service.account(existing.withStockDeclarations(Collections.emptyMap()));
            Field plan = CompanionService.class.getDeclaredField("plan"); plan.setAccessible(true);
            PortfolioPlan withdrawn = service.plan();
            assertNotSame(ready, withdrawn);
            assertEquals(PortfolioPlanOutcome.COMPANION_STATE_UNAVAILABLE, withdrawn.getOutcome());
            assertTrue(withdrawn.getAllocations().isEmpty());
        } finally { service.close(); }
    }
    private static Object get(Object target,String name)throws Exception {
        Field f=target.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(target);
    }
    private static AccountSnapshot current(SqliteStore store,AccountSnapshot raw)throws Exception {
        String id=AccountIdentity.fromProfileKey("current-health");long now=Instant.now().getEpochSecond();
        java.util.Map<Integer,String> receipt=new java.util.HashMap<>();Gson gson=new Gson();
        for(int slot=0;slot<8;slot++) {
            var e=OfferEvent.builder("fixture",now,"EMPTY").accountId(id).authenticatedObservation(true)
                .eventIdentity("current-"+slot,"health",null).slot(slot).sequence(slot+1).build();
            store.recordAndProjectOffer(e,gson.toJson(e),true);receipt.put(slot,e.getEventId());
        }
        var wire=gson.toJsonTree(raw).getAsJsonObject();wire.addProperty("observedAt",now);
        wire.addProperty("freeSlots",8);wire.addProperty("totalSlots",8);wire.addProperty("committedCoins",0);
        wire.addProperty("spendableCoins",1000000);
        var captured=gson.fromJson(wire,AccountSnapshot.class).withAccountId(id).withAutomaticTradeScope().withOfferReadSet(receipt);
        store.recordAndReconcileAccount(captured,PositionStateView.from(captured),gson.toJson(captured));return captured;
    }
    private static void set(Object target, String field, Object value) throws Exception {
        Field f = CompanionService.class.getDeclaredField(field); f.setAccessible(true); f.set(target, value);
    }
    private static class PausedExecutor extends AbstractExecutorService {
        private boolean stopped;
        public void execute(Runnable task) { /* Deliberately pause the worker until after assertions. */ }
        public void shutdown() { stopped = true; }
        public java.util.List<Runnable> shutdownNow() { stopped = true; return Collections.emptyList(); }
        public boolean isShutdown() { return stopped; }
        public boolean isTerminated() { return stopped; }
        public boolean awaitTermination(long timeout, TimeUnit unit) { return stopped; }
    }
}
