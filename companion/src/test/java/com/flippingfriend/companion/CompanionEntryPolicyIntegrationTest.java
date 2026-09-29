package com.flippingfriend.companion;

import com.flippingfriend.core.PolicyAbstentionReason;
import com.flippingfriend.core.PolicyDecision;
import com.flippingfriend.core.PortfolioPlan;
import com.flippingfriend.core.PortfolioPlanOutcome;
import com.google.gson.Gson;
import java.lang.reflect.Field;
import java.nio.file.Files;
import org.junit.Assert;
import org.junit.Test;

public class CompanionEntryPolicyIntegrationTest
{
    @Test public void serviceDelegatesCurrentStructuredPlanToEntryPolicy() throws Exception
    {
        java.nio.file.Path dir = Files.createTempDirectory("entry-policy-service");
        try (SqliteStore store = new SqliteStore(dir.resolve("state.db"));
             CompanionService service = new CompanionService(new Gson(), store))
        {
            PortfolioPlan plan = PortfolioPlan.unavailable("plan", PortfolioPlanOutcome.NO_FREE_SLOT,
                "display text is not policy", 950);
            Field field = CompanionService.class.getDeclaredField("plan");
            field.setAccessible(true);
            field.set(service, plan);
            PolicyDecision decision = service.entryDecision("plan", 1, 1_000);
            Assert.assertEquals(PolicyAbstentionReason.NO_FREE_SLOT,
                decision.getAbstentionReason());
            Assert.assertEquals("NO_FREE_SLOT", decision.getReasonCode());
        }
    }
}
