package com.flippingfriend.companion;

import com.flippingfriend.core.PortfolioCandidate;
import com.flippingfriend.data.Candle;
import com.flippingfriend.data.LatestPrice;
import com.flippingfriend.model.FillCurve;
import com.flippingfriend.model.FillModel;
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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The fill probability a candidate carries has to be the measured one.
 * <p>
 * This is the fault that produced no buy recommendations at all on a live 128m account with eight
 * free slots. A second ONNX override sat below the sizing step, ungated, and replaced the measured
 * {@code buyFill} and {@code sellFill} with the placeholder classifier's output — the one
 * {@code ml-forecaster/train_models.py} fits to {@code np.random.rand(100, 4)} against random
 * labels. Those are the estimates stored on the candidate, so they set
 * {@code getCompletionProbability()}, {@code expectedProfit()} and {@code expectedGpPerSlotHour()},
 * and the optimizer will not select anything whose expected value is not positive.
 * <p>
 * Measured on the item below — a six percent spread, four thousand units crossing each side every
 * five minutes — the fill model says 0.999 and the override said 0.397. At 0.397 a leg the expected
 * profit of a 234,000 gp flip came out at <b>minus 1,222</b>, so the plan was empty at every risk
 * level and every horizon, and reported it as a safety-constraint failure.
 */
public class FillModelDecidesTest
{
	private static final int ITEM = 2361;
	private static final int BID = 2_000;
	private static final int ASK = 2_120;
	private static final long NOW = 1_700_000_000L + 300L * 300L;

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

	private static List<PortfolioCandidate> plan()
	{
		CandidateFactory factory = new CandidateFactory(
			(id, step) -> "5m".equals(step) ? bars(300, 300, 4_000) : bars(400, 3_600, 48_000),
			new TaxCalculator(), "5m", "1h", 300);
		factory.setRiskAppetite(RiskAppetite.BALANCED);
		assertFalse("the placeholder classifier must not be in the path",
			factory.isOnnxOverridingFillModel());

		MarketIngestionService.Item item =
			new MarketIngestionService.Item(ITEM, "Adamant bar", 30_000);
		CandidateFactory.QuotedItem quoted = new CandidateFactory.QuotedItem(item,
			new LatestPrice(ASK, NOW - 60, BID, NOW - 60),
			new Candle(NOW, ASK, BID, 4_000, 4_000),
			new Candle(NOW, ASK, BID, 48_000, 48_000));
		Map<Integer, Integer> limits = new HashMap<>();
		limits.put(ITEM, 30_000);

		return factory.build(Collections.singletonList(quoted), 2.5, limits, 1_000_000_000L, true,
			Instant.ofEpochSecond(NOW));
	}

	@Test
	public void aCandidateCarriesTheFillModelsOwnAnswerForTheSizeItOrders()
	{
		List<PortfolioCandidate> tactics = plan();
		assertFalse(tactics.isEmpty());

		FillCurve curve = FillCurve.from(bars(300, 300, 4_000));
		FillModel model = new FillModel().withCaptureRate(RiskAppetite.BALANCED.getCaptureShare());

		for (PortfolioCandidate candidate : tactics)
		{
			double measured = model
				.estimateBuy(curve, candidate.getBuyPrice(), candidate.getQuantity(), 2.5, 1.0)
				.getProbability();
			assertEquals("the stored probability has to be the measured one for the size actually "
					+ "ordered, not a number from somewhere else",
				measured, candidate.getBuyFillProbability(), 0.02);
		}
	}

	@Test
	public void anOrderThatIsATinyShareOfTheFlowIsNotRatedACoinFlip()
	{
		for (PortfolioCandidate candidate : plan())
		{
			// The order is a few thousand units against a hundred and twenty thousand crossing inside
			// the horizon. Anything near a half here means something other than the market is
			// answering.
			assertTrue("buy leg rated " + candidate.getBuyFillProbability(),
				candidate.getBuyFillProbability() > 0.9);
			assertTrue("sell leg rated " + candidate.getSellFillProbability(),
				candidate.getSellFillProbability() > 0.9);
		}
	}

	@Test
	public void aGoodFlipIsWorthDoingOnceTheUnwindCostIsCounted()
	{
		// The property the optimizer actually requires: it keeps only a set that scores above zero,
		// so a candidate whose expected value is negative can never be recommended however good its
		// margin looks. Every tactic on an item this healthy has to clear it.
		for (PortfolioCandidate candidate : plan())
		{
			assertTrue(candidate.getItemName() + " expected " + candidate.expectedProfit()
					+ " on a net margin of " + candidate.getNetProfit()
					+ " against an unwind cost of " + candidate.getUnwindLoss(),
				candidate.expectedProfit() > 0);
			assertTrue(candidate.expectedGpPerSlotHour() > 0);
		}
	}
}
