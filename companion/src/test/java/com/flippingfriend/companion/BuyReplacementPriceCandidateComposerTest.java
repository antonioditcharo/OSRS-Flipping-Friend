package com.flippingfriend.companion;

import java.util.List;
import org.junit.Test;

import static org.junit.Assert.*;

public class BuyReplacementPriceCandidateComposerTest
{
    private final BuyReplacementPriceCandidateComposer composer =
        new BuyReplacementPriceCandidateComposer();

    @Test public void balancedOffsetsProduceOrderedLowAnchoredCandidates()
    {
        BuyReplacementPriceCandidateSet result = composer.compose(strategy(1000,
            new double[]{0.0, 0.002, 0.006, 0.012}));
        assertNotNull(result);
        assertEquals(400, result.getExactRemainderQuantity());
        assertEquals(1000, result.getHistoricalOriginalPrice());
        assertEquals(990, result.getCurrentLowPrice());
        assertEquals("Balanced", result.getAppetiteName());
        assertCandidates(result.getCandidates(), new int[]{990, 992, 996, 1002},
            new double[]{0.0, 0.002, 0.006, 0.012});
    }

    @Test public void historicalPriceIsNotTheCandidateAnchor()
    {
        BuyReplacementPriceCandidateSet result = composer.compose(strategy(5000,
            new double[]{0.0, 0.01}));
        assertEquals(990, result.getCandidates().get(0).getBuyPrice());
        assertEquals(1000, result.getCandidates().get(1).getBuyPrice());
    }

    @Test public void duplicateLowPriceResultsAndProvenanceArePreserved()
    {
        BuyReplacementPriceCandidateSet result = composer.compose(strategyAtLow(5,
            new double[]{0.0, 0.002, 0.006, 0.012}));
        assertCandidates(result.getCandidates(), new int[]{5, 6, 6, 6},
            new double[]{0.0, 0.002, 0.006, 0.012});
    }

    @Test public void candidateCollectionIsUnmodifiableAndDeterministic()
    {
        BuyReplacementPriceCandidateSet first = composer.compose(strategy(1000,
            new double[]{0.0, 0.004, 0.01}));
        BuyReplacementPriceCandidateSet second = composer.compose(strategy(1000,
            new double[]{0.0, 0.004, 0.01}));
        try
        {
            first.getCandidates().add(new BuyReplacementPriceCandidate(3, 0.02, 1010));
            fail("candidate collection must be unmodifiable");
        }
        catch (UnsupportedOperationException expected)
        {
        }
        assertCandidates(first.getCandidates(), prices(second), offsets(second));
    }

    @Test public void malformedStaleOrExpiredStrategyFailsClosed()
    {
        assertNull(composer.compose(null));
        assertNull(composer.compose(strategyWith(400, 399, 990, 1010, 900, 900, 1000, 1100,
            "Balanced", 2.5, new double[]{0.0})));
        assertNull(composer.compose(strategyWith(400, 400, 1010, 1010, 900, 900, 1000, 1100,
            "Balanced", 2.5, new double[]{0.0})));
        assertNull(composer.compose(strategyWith(400, 400, 990, 1010, 879, 900, 1000, 1100,
            "Balanced", 2.5, new double[]{0.0})));
        assertNull(composer.compose(strategyWith(400, 400, 990, 1010, 900, 900, 1001, 1000,
            "Balanced", 2.5, new double[]{0.0})));
        assertNull(composer.compose(strategyWith(400, 400, 990, 1010, 900, 900, 1000, 1100,
            "Balanced", 2.5, new double[]{})));
        assertNull(composer.compose(strategyWith(400, 400, 990, 1010, 900, 900, 1000, 1100,
            "Balanced", 2.5, new double[]{Double.NaN})));
    }

    @Test public void candidateContractsCarryNoSelectionEvaluationOrAuthorization() throws Exception
    {
        String source = java.nio.file.Files.readString(java.nio.file.Path.of(
            "src/main/java/com/flippingfriend/companion/BuyReplacementPriceCandidateSet.java"));
        assertFalse(source.contains("selected"));
        assertFalse(source.contains("preferred"));
        assertFalse(source.contains("FillModel"));
        assertFalse(source.contains("PortfolioCandidate"));
        assertFalse(source.contains("PolicyDecision"));
        assertFalse(source.contains("OfferLifecycleAction"));
        assertFalse(source.contains("REPLACEMENT_AUTHORIZED"));
    }

    private static BuyReplacementPricingStrategyContext strategy(int historical, double[] offsets)
    {
        return strategyWith(400, 400, 990, 1010, 900, 900, 1000, 1100,
            "Balanced", 2.5, offsets, historical);
    }

    private static BuyReplacementPricingStrategyContext strategyAtLow(int low, double[] offsets)
    {
        return strategyWith(400, 400, low, low + 1, 900, 900, 1000, 1100,
            "Balanced", 2.5, offsets, 1000);
    }

    private static BuyReplacementPricingStrategyContext strategyWith(int remainder, int limit,
        int low, int high, long accountAt, long marketAt, long evaluatedAt, long expiresAt,
        String appetite, double horizon, double[] offsets)
    {
        return strategyWith(remainder, limit, low, high, accountAt, marketAt, evaluatedAt,
            expiresAt, appetite, horizon, offsets, 1000);
    }

    private static BuyReplacementPricingStrategyContext strategyWith(int remainder, int limit,
        int low, int high, long accountAt, long marketAt, long evaluatedAt, long expiresAt,
        String appetite, double horizon, double[] offsets, int historical)
    {
        BuyReplacementPricingContext pricing = new BuyReplacementPricingContext("assessment-1",
            "readiness-1", "intent-1", "offer-1", "plan-1", 2, 4151,
            "Abyssal whip", remainder, historical, low, high, limit, 2_000_000, true,
            100, expiresAt, accountAt, marketAt, Math.min(accountAt, marketAt), evaluatedAt);
        return new BuyReplacementPricingStrategyContext(pricing, appetite, offsets, horizon);
    }

    private static void assertCandidates(List<BuyReplacementPriceCandidate> candidates,
        int[] prices, double[] offsets)
    {
        assertEquals(prices.length, candidates.size());
        for (int i = 0; i < prices.length; i++)
        {
            assertEquals(i, candidates.get(i).getOffsetIndex());
            assertEquals(offsets[i], candidates.get(i).getOffset(), 0.0);
            assertEquals(prices[i], candidates.get(i).getBuyPrice());
        }
    }

    private static int[] prices(BuyReplacementPriceCandidateSet set)
    {
        int[] values = new int[set.getCandidates().size()];
        for (int i = 0; i < values.length; i++) values[i] = set.getCandidates().get(i).getBuyPrice();
        return values;
    }

    private static double[] offsets(BuyReplacementPriceCandidateSet set)
    {
        double[] values = new double[set.getCandidates().size()];
        for (int i = 0; i < values.length; i++) values[i] = set.getCandidates().get(i).getOffset();
        return values;
    }
}
