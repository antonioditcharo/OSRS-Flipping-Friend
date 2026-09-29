package com.flippingfriend.core;

import java.util.Collections;
import org.junit.Assert;
import org.junit.Test;

public class PortfolioPlanOutcomeTest
{
    @Test public void copiesPreserveStructuredOutcome()
    {
        PortfolioPlan plan = PortfolioPlan.unavailable("c", PortfolioPlanOutcome.NO_FREE_SLOT,
            "different display prose", 100);
        Assert.assertEquals(PortfolioPlanOutcome.NO_FREE_SLOT, plan.getOutcome());
        Assert.assertEquals(PortfolioPlanOutcome.NO_FREE_SLOT,
            plan.withDiagnostics(null).getOutcome());
        Assert.assertEquals(PortfolioPlanOutcome.NO_FREE_SLOT,
            plan.withBoard(0, 0).getOutcome());
    }

    @Test public void legacyPlansRemainConservative()
    {
        PortfolioPlan refusal = PortfolioPlan.unavailable("c", "legacy reason", 100);
        Assert.assertEquals(PortfolioPlanOutcome.UNKNOWN, refusal.getOutcome());
        PortfolioPlan ready = new PortfolioPlan("c", 100, 190, "READY", "ok", 1,
            Collections.singletonList(new PortfolioAllocation(1, null, "PLACE_BUY")));
        Assert.assertEquals(PortfolioPlanOutcome.READY, ready.getOutcome());
    }
}
