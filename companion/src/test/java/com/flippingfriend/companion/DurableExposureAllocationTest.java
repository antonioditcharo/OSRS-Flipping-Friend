package com.flippingfriend.companion;

import com.flippingfriend.core.AccountSnapshot;
import com.flippingfriend.core.PortfolioCandidate;
import com.flippingfriend.core.PortfolioConstraints;
import com.flippingfriend.core.PortfolioOptimizer;
import com.flippingfriend.core.PortfolioPlan;
import com.flippingfriend.core.PortfolioPlanOutcome;
import com.flippingfriend.model.RiskAppetite;
import java.util.Collections;
import java.util.Map;
import org.junit.Test;
import static org.junit.Assert.*;

/** Durable held exposure, not the client snapshot, decides whether an item cap blocks a buy. */
public class DurableExposureAllocationTest {
    private static final int ITEM = 4151;
    private static final long NOW = 10_000;

    private static PortfolioConstraints limits(AccountSnapshot account) {
        RiskAppetite appetite = RiskAppetite.forName(account.getRiskAppetite());
        Map<Integer, MarketIngestionService.Item> mapping = Collections.singletonMap(ITEM,
                new MarketIngestionService.Item(ITEM, "Abyssal whip", 70));
        return PortfolioPlanner.constraintsFor(account, appetite,
                PortfolioPlanner.lossBudgetFor(account),
                PortfolioPlanner.committedByGroupFor(account, mapping));
    }

    @Test public void durableHeldExposureBlocksABuyTheSnapshotWouldAllow() {
        AccountSnapshot client = new AccountSnapshot("allocation", NOW, 1_000_000, 0, 1, 8,
                true, true, 0, "Balanced", Collections.emptyMap(), 0);
        PortfolioConstraints clientLimits = limits(client);
        long cap = clientLimits.getPerItemCapitalCap();
        assertTrue("balanced appetite must impose an item cap for this test: " + cap,
                cap > 2_000 && cap <= 1_000_000);

        int quantity = (int) (cap / 2_000);
        long net = 200L * quantity;
        PortfolioCandidate buy = new PortfolioCandidate(ITEM, "Abyssal whip", "",
                1_000, 1_000, 1_000, 1_200, 1_200, 1_200, quantity, 100_000, net,
                1, 1, 0.95, 0.95, 0.4, 0.4, 1.5, NOW + 100);
        assertTrue(buy.getCapitalRequired() > 0 && buy.getCapitalRequired() < cap);
        assertTrue(buy.expectedGpPerSlotHour() > 0);

        PortfolioOptimizer optimizer = new PortfolioOptimizer();
        PortfolioPlan fromSnapshot = optimizer.optimize(
                Collections.singletonList(buy), clientLimits, "snapshot", NOW);
        assertEquals("the snapshot alone permits the buy",
                PortfolioPlanOutcome.READY, fromSnapshot.getOutcome());
        assertSame(buy, fromSnapshot.getAllocations().get(0).getCandidate());

        AccountSnapshot durable = client.withPlanningExposure(Collections.singletonMap(ITEM, cap));
        PortfolioConstraints durableLimits = limits(durable);
        assertEquals("only exposure changes; the cap itself does not",
                cap, durableLimits.getPerItemCapitalCap());
        assertEquals(Long.valueOf(cap), durableLimits.getCommittedByItem().get(ITEM));
        PortfolioPlan fromDurable = optimizer.optimize(
                Collections.singletonList(buy), durableLimits, "durable", NOW);
        assertEquals("durable holdings at the cap must block the same buy",
                PortfolioPlanOutcome.PORTFOLIO_CONSTRAINT, fromDurable.getOutcome());
        assertTrue(fromDurable.getAllocations().isEmpty());
    }
}
