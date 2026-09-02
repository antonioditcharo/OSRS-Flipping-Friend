package com.flippingfriend.model;

import com.flippingfriend.session.Position;
import com.flippingfriend.session.SellDecision;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Keeping sight of what is already held.
 * <p>
 * Nine thousand Ruby necklaces sat through their sell target being reached because all three slots
 * held other trades — two of them buys this engine suggested *after* the necklaces were in hand. A
 * position needs a slot to leave by, and a buy takes one for hours, so spending the last slot on a
 * purchase means the holding cannot be sold at all until something else finishes.
 */
public class HeldPositionTest
{
	private static final int RUBY_NECKLACE = 1660;

	private static Position held(int itemId, int quantity)
	{
		return new Position(itemId, "Ruby necklace", quantity, (long) quantity * 1088,
			Instant.now().getEpochSecond(), true);
	}

	@Test
	public void aHoldingWithNoOfferCarryingItOutNeedsASlot()
	{
		Position waiting = SuggestionEngine.awaitingExit(
			Collections.singletonList(held(RUBY_NECKLACE, 9000)), Collections.emptyMap());

		assertNotNull("nine thousand held and nothing listed still has to get out", waiting);
		assertEquals(RUBY_NECKLACE, waiting.getItemId());
	}

	@Test
	public void aHoldingAlreadyBeingSoldDoesNotNeedAnother()
	{
		Map<Integer, Integer> listed = new HashMap<>();
		listed.put(RUBY_NECKLACE, 9000);

		assertNull("it already has the slot it needs",
			SuggestionEngine.awaitingExit(Collections.singletonList(held(RUBY_NECKLACE, 9000)), listed));
	}

	@Test
	public void aPartlyListedHoldingStillNeedsASlotForTheRest()
	{
		Map<Integer, Integer> listed = new HashMap<>();
		listed.put(RUBY_NECKLACE, 4000);

		assertNotNull("five thousand of it is still stuck in your inventory",
			SuggestionEngine.awaitingExit(Collections.singletonList(held(RUBY_NECKLACE, 9000)), listed));
	}

	@Test
	public void holdingNothingReservesNothing()
	{
		assertNull(SuggestionEngine.awaitingExit(Collections.emptyList(), Collections.emptyMap()));
		assertNull("a position already sold down to nothing does not need a slot",
			SuggestionEngine.awaitingExit(Arrays.asList(held(RUBY_NECKLACE, 0)), Collections.emptyMap()));
	}

	/**
	 * Grapes: a buy for 20,000 at 69 filling a few hundred at a time, with 8,310 already listed at 72.
	 * Every refresh leaves a fresh "sellable" remainder, and each one asked for its own offer.
	 */
	@Test
	public void aTrickleFromAPartFilledBuyDoesNotGetASlotOfItsOwn()
	{
		// 500 more grapes at +2 each is 1,000 gp, against a 5,000 gp minimum. Not worth a slot that
		// will be held for hours.
		assertTrue("a trickle waits for the offer already running",
			!SuggestionEngine.remainderWarrantsItsOwnOffer(8310, 1_000, 5_000));

		// The whole remaining 11,690 is 23,380 gp. That is a real trade and should not be held back.
		assertTrue("a genuine remainder still gets its offer",
			SuggestionEngine.remainderWarrantsItsOwnOffer(8310, 23_380, 5_000));
	}

	@Test
	public void theFirstOfferIsNeverHeldBack()
	{
		// Nothing listed means no second slot is being spent, so the profit bar must not apply --
		// otherwise a small holding could never be sold at all and would sit forever.
		assertTrue("with nothing on the market this is the first offer, not a second one",
			SuggestionEngine.remainderWarrantsItsOwnOffer(0, 1, 5_000));
	}

	@Test
	public void aWithheldRemainderIsReportedRatherThanHidden()
	{
		PositionStatus status = PositionStatus.sellingWithRemainder(72, 4000, 8310, 500);

		assertTrue("the card must say the rest is being held back deliberately",
			status.summary().contains("Holding 500 more back"));
		assertTrue("and still report the offer that is running",
			status.summary().contains("4000 of 8310 sold"));
		assertEquals(500, status.getWithheld());
	}

	@Test
	public void aHoldingAlreadyOnTheMarketSaysSo()
	{
		// The case that produced the wrong card: 1,049 Emerald necklaces entirely inside a sell offer,
		// 109 of 1,158 gone. It was reported as both "not seen in your inventory or bank" and "ready
		// to sell, but every slot is busy" -- while it was, in fact, selling.
		PositionStatus selling = PositionStatus.selling(645, 109, 1158);

		assertTrue(selling.isSelling());
		assertEquals("On the market at 645 gp — 109 of 1158 sold.", selling.summary());
		assertTrue("a listed holding is not waiting on a slot", !selling.isBlockedBySlots());
		assertTrue("and it is not missing", !selling.isUnconfirmed());
	}

	@Test
	public void aFreshlyListedHoldingDoesNotClaimSalesItHasNotMade()
	{
		PositionStatus selling = PositionStatus.selling(12, 0, 15000);

		assertEquals("On the market at 12 gp — none sold yet.", selling.summary());
	}

	@Test
	public void whatIsAlreadyListedIsNotStillWaitingToBeSold()
	{
		// The rule the sell loop now applies before anything else: an offer already carrying the
		// holding out means there is nothing left to arrange.
		Map<Integer, Integer> listed = new HashMap<>();
		listed.put(RUBY_NECKLACE, 9000);

		assertNull(SuggestionEngine.awaitingExit(
			Collections.singletonList(held(RUBY_NECKLACE, 9000)), listed));
	}

	@Test
	public void theCardSaysWhenASaleIsWantedButThereIsNowhereToPutIt()
	{
		PositionStatus blocked = new PositionStatus(
			SellDecision.hold("Waiting for 1120 gp. Currently 1122 gp.", 30), false, true);

		assertEquals("Ready to sell, but every Grand Exchange slot is busy.", blocked.summary());
	}

	@Test
	public void theCardOtherwiseSaysWhatTheEngineIsWaitingFor()
	{
		PositionStatus waiting = new PositionStatus(
			SellDecision.hold("Waiting for 1120 gp. Currently 1100 gp.", 30), false, false);

		assertEquals("Waiting for 1120 gp. Currently 1100 gp.", waiting.summary());
	}

	@Test
	public void anUnconfirmedHoldingSaysSoRatherThanPretending()
	{
		PositionStatus unconfirmed = new PositionStatus(
			SellDecision.hold("Waiting for 1120 gp. Currently 1100 gp.", 30), true, false);

		assertTrue(unconfirmed.summary().contains("not currently visible"));
	}
}
