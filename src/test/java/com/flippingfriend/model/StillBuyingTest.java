package com.flippingfriend.model;

import com.flippingfriend.session.Position;
import com.flippingfriend.session.SellDecision;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * A holding whose buy is still running.
 * <p>
 * The slot reservation existed to stop a buy taking the last slot a sale needed. Applied to a position
 * that is still being bought it does the opposite of its purpose: it holds a slot open to sell part of
 * an order that is still growing, which cannot be listed until the order stops anyway. On three slots
 * that is a third of the account idle for the length of a 13,883-unit order.
 */
public class StillBuyingTest
{
	private static final int SAPPHIRE_NECKLACE = 6575;

	private static Position held(int itemId, int quantity)
	{
		return new Position(itemId, "Sapphire necklace", quantity, (long) quantity * 434,
			Instant.now().getEpochSecond(), true);
	}

	private static Set<Integer> buying(int... itemIds)
	{
		Set<Integer> set = new HashSet<>();
		for (int id : itemIds)
		{
			set.add(id);
		}
		return set;
	}

	@Test
	public void aPositionStillBeingBoughtReservesNothing()
	{
		// The live case: 6,318 of a 13,883 order filled, the rest still working.
		assertNull("the order is still growing; the slot is worth more than the option",
			SuggestionEngine.awaitingExit(
				Collections.singletonList(held(SAPPHIRE_NECKLACE, 6_318)),
				Collections.emptyMap(),
				buying(SAPPHIRE_NECKLACE)));
	}

	@Test
	public void theSameHoldingReservesASlotOnceTheBuyIsDone()
	{
		// Nothing else changes -- only that the buy has finished. The exit was deferred, not dropped.
		Position position = SuggestionEngine.awaitingExit(
			Collections.singletonList(held(SAPPHIRE_NECKLACE, 6_318)),
			Collections.emptyMap(),
			Collections.emptySet());

		assertNotNull("with no buy running it has to get out somehow", position);
		assertEquals(SAPPHIRE_NECKLACE, position.getItemId());
	}

	@Test
	public void anUnrelatedHoldingStillGetsItsSlot()
	{
		// Only the item being bought is deferred; a different holding is unaffected.
		Position other = held(1987, 5_000);
		other.setItemName("Grapes");

		Position position = SuggestionEngine.awaitingExit(
			java.util.Arrays.asList(held(SAPPHIRE_NECKLACE, 6_318), other),
			Collections.emptyMap(),
			buying(SAPPHIRE_NECKLACE));

		assertNotNull(position);
		assertEquals("the grapes still need an exit", 1987, position.getItemId());
	}

	@Test
	public void anAlreadyListedHoldingIsStillCoveredByItsOffer()
	{
		// The pre-existing rule has to survive: a holding entirely inside a sell offer needs nothing.
		Map<Integer, Integer> listed = new HashMap<>();
		listed.put(SAPPHIRE_NECKLACE, 6_318);

		assertNull(SuggestionEngine.awaitingExit(
			Collections.singletonList(held(SAPPHIRE_NECKLACE, 6_318)), listed, Collections.emptySet()));
	}

	@Test
	public void theCardAndTheSuggestionAgreeWhenTheBuyIsInTheWay()
	{
		// When the exit is wanted but our own buy keeps adding to the position, the suggestion is
		// "stop buying this". Showing the plain sell reason on the card left it saying it was time to
		// sell while the card beside it said to cancel a purchase -- two systems appearing to
		// disagree rather than one explaining itself.
		SellDecision wantsOut = new SellDecision(SellDecision.Action.CUT, 1200,
			"This has fallen past the point where holding is worth the risk.", 30, -5_000);

		PositionStatus status = PositionStatus.exitBlockedByOwnBuy(wantsOut);

		assertTrue("the card has to name the buy as the obstacle: " + status.summary(),
			status.summary().contains("cancel that first"));
		assertTrue("and still carry the reason for leaving: " + status.summary(),
			status.summary().contains("fallen past the point"));
	}

	@Test
	public void theCardSaysItIsWaitingRatherThanGoingQuiet()
	{
		PositionStatus status = PositionStatus.stillBuying(6_318, 13_883);

		assertTrue(status.isStillBuying());
		assertTrue("a plugin that is waiting must not look like one that has forgotten: "
			+ status.summary(), status.summary().contains("6318 of 13883"));
		assertTrue(status.summary().contains("once the buy finishes"));
	}
}
