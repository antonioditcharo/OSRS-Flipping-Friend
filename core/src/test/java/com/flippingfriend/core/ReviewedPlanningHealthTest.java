package com.flippingfriend.core;
import org.junit.Test;
import static org.junit.Assert.*;

public class ReviewedPlanningHealthTest {
    @Test public void reviewedPlanningCanProceedWhileAccountingWarningsStayVisible() {
        CompanionHealth health = new CompanionHealth("DEGRADED", "unpriced historical sale", 990, 0, 1000, true);
        assertFalse(health.isReady()); assertTrue(health.canReadPlan(1000));
        assertFalse(new CompanionHealth("DEGRADED", "unknown current stock", 990, 0, 1000).canReadPlan(1000));
    }
    @Test public void staleFutureUnknownAndMissingMarketEvidenceFailClosed() {
        assertFalse(new CompanionHealth("DEGRADED", "reviewed", 879, 0, 1000, true).canReadPlan(1000));
        assertFalse(new CompanionHealth("DEGRADED", "reviewed", 1001, 0, 1000, true).canReadPlan(1000));
        assertFalse(new CompanionHealth("DEGRADED", "reviewed", 990, 0, 1001, true).canReadPlan(1000));
        assertFalse(new CompanionHealth("UNKNOWN", "reviewed", 990, 0, 1000, true).canReadPlan(1000));
        assertFalse(new CompanionHealth("READY", "missing feed", 0, 0, 1000).canReadPlan(1000));
    }
}
