package com.flippingfriend.companion;

import com.flippingfriend.core.AccountSnapshot;
import com.flippingfriend.core.PortfolioPlan;
import com.flippingfriend.core.PortfolioPlanOutcome;
import com.google.gson.Gson;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import org.junit.Test;
import static org.junit.Assert.*;

public class PlanningExposureFailureTest {
    @Test public void failedDurableReadReplacesCachedPlan() throws Exception {
        SqliteStore store = new SqliteStore(
                Files.createTempDirectory("planning-failure").resolve("account.db"));
        CompanionService service = new CompanionService(new Gson(), store);
        try {
            AccountSnapshot account = new AccountSnapshot(
                    "failure-test", 123, 9000, 0, 3, 8, true, true, 0);
            Field accountField = CompanionService.class.getDeclaredField("account");
            accountField.setAccessible(true);
            accountField.set(service, account);
            Field planField = CompanionService.class.getDeclaredField("plan");
            planField.setAccessible(true);
            PortfolioPlan previous = PortfolioPlan.unavailable(
                    "previous-plan", PortfolioPlanOutcome.NO_FREE_SLOT,
                    "previous advice", 123);
            planField.set(service, previous);
            store.close();
            Method refresh = CompanionService.class.getDeclaredMethod("refreshPlan");
            refresh.setAccessible(true);
            refresh.invoke(service);
            PortfolioPlan result = (PortfolioPlan) planField.get(service);
            assertNotNull(result);
            assertNotSame(previous, result);
            assertEquals(PortfolioPlanOutcome.COMPANION_STATE_UNAVAILABLE,
                    result.getOutcome());
            assertEquals("failure-test", result.getCorrelationId());
            assertTrue(result.getAllocations().isEmpty());
        } finally {
            service.close();
        }
    }
}
