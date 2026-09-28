package com.flippingfriend.companion;

import com.flippingfriend.core.PortfolioAllocation;
import com.flippingfriend.core.PortfolioPlan;

/** Immutable input to the companion entry policy. */
final class EntryPolicyContext
{
    private final PortfolioPlan plan;
    private final PortfolioAllocation selectedAllocation;
    private final long decidedAt;

    EntryPolicyContext(PortfolioPlan plan, PortfolioAllocation selectedAllocation, long decidedAt)
    {
        if (decidedAt < 0) throw new IllegalArgumentException("decidedAt is invalid");
        this.plan = plan;
        this.selectedAllocation = selectedAllocation;
        this.decidedAt = decidedAt;
    }

    PortfolioPlan getPlan() { return plan; }
    PortfolioAllocation getSelectedAllocation() { return selectedAllocation; }
    long getDecidedAt() { return decidedAt; }
}
