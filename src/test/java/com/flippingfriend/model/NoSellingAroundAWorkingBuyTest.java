package com.flippingfriend.model;

import static org.junit.Assert.assertEquals;

import com.flippingfriend.session.SellDecision;
import org.junit.Test;

/**
 * A part-filled order is a position being built, not a position.
 *
 * <p>The engine used to sell the part that had arrived while the rest of the order was still filling
 * behind it, on the strength of a roadmap note about turning part-filled slots into sell instructions
 * -- which is right the moment a buy stops and wrong while it is running. The player was told to sell
 * stock they were in the middle of buying: the buy keeps filling behind the sale, so the round ends
 * holding items reported as sold, taxed on a flip that never closed, having spent a second Grand
 * Exchange slot to do it.
 *
 * <p>Two ways out of a holding, and no third: the buy finishes, or the buy is cancelled and whatever
 * arrived before it stopped is sold.
 *
 * <p>Tested here rather than through the engine because nothing constructs a {@code SuggestionEngine}
 * in a test -- which is exactly how this shipped. {@link StillBuyingTest} checks the two cards that
 * report these states, and both passed throughout, because they were only ever exercised in
 * isolation from the branch that was supposed to produce them.
 */
public class NoSellingAroundAWorkingBuyTest
{
	private static SellDecision decision(SellDecision.Action action)
	{
		return new SellDecision(action, 1_200, "reason", 30, 5_000);
	}

	@Test
	public void aFillingBuyIsNotInterruptedToTakeAProfit()
	{
		// The reported bug, exactly: the buy is working and getting items in, and the sell target has
		// been reached. Reaching a target mid-order is not a reason to interfere with an order that
		// is filling -- the target will still be there when it stops.
		assertEquals(SuggestionEngine.BuyInTheWay.WAIT_FOR_THE_BUY,
			SuggestionEngine.buyInTheWay(true, decision(SellDecision.Action.SELL)));
	}

	@Test
	public void aHoldWaitsTheSameWay()
	{
		assertEquals(SuggestionEngine.BuyInTheWay.WAIT_FOR_THE_BUY,
			SuggestionEngine.buyInTheWay(true, decision(SellDecision.Action.HOLD)));
	}

	@Test
	public void aCutStopsTheBuyRatherThanSellingAroundIt()
	{
		// The one case that cannot wait -- an order that keeps buying more of a collapsing item makes
		// the hole deeper every minute. Even here the action is to stop the buy, not to place a sale
		// beside it.
		assertEquals(SuggestionEngine.BuyInTheWay.STOP_THE_BUY,
			SuggestionEngine.buyInTheWay(true, decision(SellDecision.Action.CUT)));
	}

	@Test
	public void withNoBuyRunningTheOrdinarySellPathApplies()
	{
		// Including for a cut: there is nothing in the way, so it is an ordinary sale.
		assertEquals(SuggestionEngine.BuyInTheWay.NOTHING_IN_THE_WAY,
			SuggestionEngine.buyInTheWay(false, decision(SellDecision.Action.SELL)));
		assertEquals(SuggestionEngine.BuyInTheWay.NOTHING_IN_THE_WAY,
			SuggestionEngine.buyInTheWay(false, decision(SellDecision.Action.CUT)));
	}

	@Test
	public void aMissingDecisionWaitsRatherThanSelling()
	{
		// Nothing should ever read "no opinion" as permission to sell into a working buy.
		assertEquals(SuggestionEngine.BuyInTheWay.WAIT_FOR_THE_BUY,
			SuggestionEngine.buyInTheWay(true, null));
	}
}
