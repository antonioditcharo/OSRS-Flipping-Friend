package com.flippingfriend.session;

import com.flippingfriend.CheckInterval;
import com.flippingfriend.RiskProfile;
import com.flippingfriend.TradingHorizon;
import com.flippingfriend.data.Candle;
import com.flippingfriend.data.LatestPrice;
import com.flippingfriend.model.FeatureEngine;
import com.flippingfriend.model.FillModel;
import com.flippingfriend.model.ItemFeatures;
import com.flippingfriend.model.TaxCalculator;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * A sale that has not been forced is never priced below what the position cost.
 * <p>
 * The exit grid runs from one percent under the instant-buy price to a fifth of a percent over it,
 * and break-even is the purchase price plus two percent of tax. Those two facts together mean that
 * unless the market had already carried the item upwards since the buy, <b>no price in the search
 * made money</b> — so the engine picked the smallest loss available and presented it as the
 * recommendation.
 * <p>
 * Live, and the numbers below are that session's: 696 Anti-venom(4) bought at 10,767 with the market
 * at 10,871. Break-even 10,986. The highest price the grid could reach was 10,893, which is what the
 * card said, at an expected <b>minus 63,300 gp</b> — on a position eighteen minutes old whose stop
 * loss sat at 9,475, more than a thousand gp below. Nothing had gone wrong with the trade. The search
 * simply had no profitable answer available to give.
 * <p>
 * Listing is not the same as marking down. The never-hold rule asks that a holding be on the market,
 * and it is satisfied by an offer at break-even; it has never had anything to say about selling below
 * cost. Only the stop, or the player's own decision to wind the session down, authorises that.
 */
public class ExitAtALossTest
{
	private static final int ANTIVENOM = 12_913;
	private static final int QUANTITY = 696;
	private static final int PAID = 10_767;
	private static final int MARKET = 10_871;
	private static final int TARGET = 11_108;
	private static final int BUCKET_SECONDS = 300;

	private final TaxCalculator tax = new TaxCalculator();
	private final SellTimingEngine engine = new SellTimingEngine(new FillModel(), tax);
	private final FeatureEngine featureEngine = new FeatureEngine();
	private final TradingHorizon horizon =
		TradingHorizon.of(RiskProfile.HIGH, CheckInterval.FIFTEEN_MINUTES);

	/** A busy book sitting at the market price, which is where this item actually was. */
	private static List<Candle> around(int level, int count)
	{
		List<Candle> series = new ArrayList<>(count);
		long now = Instant.now().getEpochSecond();
		for (int i = 0; i < count; i++)
		{
			int drift = (int) Math.round(level * 0.004 * Math.sin(i * 0.37));
			series.add(new Candle(now - (long) (count - i) * BUCKET_SECONDS,
				level + drift + 60, level + drift - 60, 4_000, 4_000));
		}
		return series;
	}

	private static Position held(long minutesHeld)
	{
		Position position = new Position(ANTIVENOM, "Anti-venom(4)", QUANTITY,
			(long) QUANTITY * PAID, Instant.now().getEpochSecond() - minutesHeld * 60, true);
		position.setTargetSellPrice(TARGET);
		return position;
	}

	private SellDecision decide(Position position, boolean sellOnly)
	{
		List<Candle> series = around(MARKET, 300);
		ItemFeatures features = featureEngine.compute(ANTIVENOM, series, BUCKET_SECONDS);
		LatestPrice latest = new LatestPrice(MARKET, Instant.now().getEpochSecond() - 30,
			MARKET - 120, Instant.now().getEpochSecond() - 30);
		return engine.evaluate(position, latest, features, series, horizon, Instant.now(),
			50_000, true, sellOnly, false);
	}

	@Test
	public void aHealthyPositionIsNotListedBelowWhatItCost()
	{
		SellDecision decision = decide(held(18), false);
		int breakEven = tax.breakEvenSellPrice(ANTIVENOM, PAID);

		assertTrue("it is a sale either way -- the holding goes on the market", decision.isSell());
		assertTrue("priced at " + decision.getPrice() + " against a break-even of " + breakEven
				+ ", which is a guaranteed loss of "
				+ tax.netProfit(ANTIVENOM, PAID, decision.getPrice(), QUANTITY) + " gp",
			decision.getPrice() >= breakEven);
	}

	@Test
	public void theExpectedProfitOnTheCardIsNotNegative()
	{
		// The figure the player reads. It said minus 63,300 gp, in red, on a trade that had done
		// nothing wrong -- which is the whole of the complaint.
		SellDecision decision = decide(held(18), false);

		assertTrue("the card would have shown " + decision.getExpectedProfit() + " gp",
			decision.getExpectedProfit() >= 0);
	}

	@Test
	public void theSearchCanSeeAProfitablePriceAtAll()
	{
		// The mechanism, isolated. The offsets run to a fifth of a percent over the market and
		// break-even is two percent above cost, so the grid could not reach a profitable price even
		// in principle -- whatever the fill model thought of any of them.
		int breakEven = tax.breakEvenSellPrice(ANTIVENOM, PAID);
		List<Integer> offsetsOnly = SellTimingEngine.exitPrices(MARKET, MARKET - 120, false);
		List<Integer> withPlan = SellTimingEngine.exitPrices(MARKET, MARKET - 120, false, breakEven,
			TARGET);

		boolean anyProfitable = false;
		for (int price : offsetsOnly)
		{
			anyProfitable |= price >= breakEven;
		}
		assertTrue("this is the fault: the plain grid tops out at "
			+ offsetsOnly.stream().max(Integer::compareTo).orElse(0) + " against a break-even of "
			+ breakEven, !anyProfitable);

		assertTrue("break-even has to be a price the search can choose", withPlan.contains(breakEven));
		assertTrue("and so does the exit the trade was opened for", withPlan.contains(TARGET));
	}

	@Test
	public void aPositionThroughItsStopStillTakesTheLoss()
	{
		// The floor must not become a new way to hold a losing position. Below the stop, realising
		// the loss is the decision, and the card says so.
		Position position = held(18);
		position.setStopPrice(MARKET + 500);

		SellDecision decision = decide(position, false);

		assertEquals("through the stop is the one case that is meant to lose money",
			SellDecision.Action.CUT, decision.getAction());
		assertTrue("and it prices to get out, not to wait: " + decision.getPrice(),
			decision.getPrice() <= tax.breakEvenSellPrice(ANTIVENOM, PAID));
	}

	@Test
	public void windingTheSessionDownStillCrossesTheSpread()
	{
		// The player's own decision to close out. They have asked to be finished, and holding out
		// for break-even is not what they asked for.
		SellDecision decision = decide(held(18), true);

		assertTrue("sell-only has to be able to price under cost", decision.isSell());
		assertTrue("it should be reaching for the bid, not the target: " + decision.getPrice(),
			decision.getPrice() < TARGET);
	}

	@Test
	public void aHoldingWithNoKnownCostIsStillListed()
	{
		// Nothing to floor against. An adopted position -- seen in the bank, never bought through
		// this plugin -- must still be sold rather than left because the floor cannot be computed.
		Position adopted = Position.preExisting(ANTIVENOM, "Anti-venom(4)", QUANTITY,
			Instant.now().getEpochSecond() - 18 * 60);

		SellDecision decision = decide(adopted, false);

		assertTrue("no cost basis is not a reason to hold", decision.isSell());
		assertTrue("and it still gets a real price: " + decision.getPrice(), decision.getPrice() > 0);
	}
}
