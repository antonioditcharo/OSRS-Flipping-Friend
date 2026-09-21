package com.flippingfriend.companion;

import com.flippingfriend.core.PortfolioCandidate;
import com.flippingfriend.data.Candle;
import com.flippingfriend.data.LatestPrice;
import com.flippingfriend.model.RiskAppetite;
import com.flippingfriend.model.TaxCalculator;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class PreTrimDiagnosticSeamTest
{
    private static final int ITEM = 2361;
    private static final int BID = 2_000;
    private static final int ASK = 2_120;
    private static final int BUY_LIMIT = 30_000;
    private static final long NOW = 1_700_000_000L + 300L * 300L;
    private static final double HORIZON = 2.5;

    private static List<Candle> bars(int count, int step, int volumePerSide)
    {
        List<Candle> series = new ArrayList<>(count);
        for (int i = 0; i < count; i++)
        {
            int drift = (int) Math.round(40 * Math.sin(i * 0.37));
            series.add(new Candle(NOW - (long) (count - i) * step,
                    ASK + drift, BID + drift, volumePerSide, volumePerSide));
        }
        return series;
    }

    private static CandidateFactory factory()
    {
        CandidateFactory factory = new CandidateFactory(
                (id, step) -> "5m".equals(step) ? bars(300, 300, 1_200)
                        : bars(400, 3_600, 14_400),
                new TaxCalculator(), "5m", "1h", 300);
        factory.setRiskAppetite(RiskAppetite.BALANCED);
        return factory;
    }

    private static CandidateFactory.QuotedItem quoted()
    {
        MarketIngestionService.Item item =
                new MarketIngestionService.Item(ITEM, "Adamant bar", BUY_LIMIT);
        return new CandidateFactory.QuotedItem(item,
                new LatestPrice(ASK, NOW - 60, BID, NOW - 60),
                new Candle(NOW, ASK, BID, 1_200, 1_200),
                new Candle(NOW, ASK, BID, 14_400, 14_400));
    }

    private static Map<Integer, Integer> limits()
    {
        Map<Integer, Integer> limits = new HashMap<>();
        limits.put(ITEM, BUY_LIMIT);
        return limits;
    }

    @Test
    public void diagnosticsExposeTheSameRankedUniverseBeforeProductionTrimming()
    {
        CandidateFactory productionFactory = factory();
        List<PortfolioCandidate> production = productionFactory.build(
                Collections.singletonList(quoted()), HORIZON, limits(), 1_000_000_000L,
                true, Instant.ofEpochSecond(NOW));

        CandidateFactory diagnosticFactory = factory();
        List<PortfolioCandidate> diagnostic = diagnosticFactory.buildForDiagnostics(
                Collections.singletonList(quoted()), HORIZON, limits(), 1_000_000_000L,
                true, Instant.ofEpochSecond(NOW));

        assertEquals("production must retain exactly its established per-item limit", 3,
                production.size());
        assertTrue("diagnostics must expose tactics removed by the production trim: "
                + diagnostic.size(), diagnostic.size() > production.size());

        for (int i = 0; i < production.size(); i++)
        {
            assertSameTactic(production.get(i), diagnostic.get(i));
        }
    }

    private static void assertSameTactic(PortfolioCandidate expected, PortfolioCandidate actual)
    {
        assertEquals(expected.getItemId(), actual.getItemId());
        assertEquals(expected.getBuyPrice(), actual.getBuyPrice());
        assertEquals(expected.getSellPrice(), actual.getSellPrice());
        assertEquals(expected.getQuantity(), actual.getQuantity());
        assertEquals(expected.expectedGpPerSlotHour(), actual.expectedGpPerSlotHour(), 1e-9);
    }
}
