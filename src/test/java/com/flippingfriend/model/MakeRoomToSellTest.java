package com.flippingfriend.model;

import com.flippingfriend.session.SellDecision;
import com.flippingfriend.session.TrackedOffer;
import java.util.Arrays;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The guarantee that lets the slot reservation be relaxed.
 * <p>
 * A slot used to be held back for any holding at all, for as long as it was held — 2,197 Grimy irit
 * leaf kept one idle while the engine waited on a price hours away. Reserving only near the exit
 * gives that slot back, but it means the exit can now arrive with all eight slots busy, and the old
 * answer to that was "collect or cancel an offer to free one up" and nothing else: a shrug at the
 * exact moment the player needs an answer.
 * <p>
 * So the two halves are tested together. Reserving late is only safe because room is taken back on
 * demand, and if either half regresses the holding is stranded — which is the failure that started
 * all of this.
 */
public class MakeRoomToSellTest
{
	private static final int SAPPHIRE_NECKLACE = 6575;
	private static final int NATURE_RUNE = 561;

	private final Explainer explainer = new Explainer();

	private static TrackedOffer offer(int slot, int itemId, boolean buying, int price, int total,
		int filled, String state)
	{
		TrackedOffer offer = new TrackedOffer(slot, itemId, buying, price, total, 0L);
		offer.setQuantityFilled(filled);
		offer.setState(state);
		return offer;
	}

	@Test
	public void aWantedSaleWithEverySlotBusyCancelsTheBuyFurthestFromFinishing()
	{
		TrackedOffer sapphire = offer(0, SAPPHIRE_NECKLACE, true, 434, 13_883, 4_707, "BUYING");
		TrackedOffer nature = offer(2, NATURE_RUNE, true, 139, 17_937, 0, "BUYING");

		TrackedOffer give = SuggestionEngine.largestOpenBuy(Arrays.asList(nature, sapphire));
		Suggestion suggestion = SuggestionEngine.makeRoomToSell(give, "Sapphire necklace",
			"Grimy irit leaf", explainer);

		assertEquals("room must be made, not asked for", SuggestionType.CANCEL, suggestion.getType());
		assertEquals("and it must name the buy holding the most", SAPPHIRE_NECKLACE,
			suggestion.getItemId());
		assertEquals("in the slot that buy occupies", 0, suggestion.getSlot());
		assertEquals("for what is still unfilled", 13_883 - 4_707, suggestion.getQuantity());
		assertTrue("and say what it is for", suggestion.getDetail().contains("Grimy irit leaf"));
	}

	@Test
	public void aSaleIsNeverCancelledToMakeRoomForAnother()
	{
		// Every slot already holds a sale. Cancelling one to place another trades nothing for
		// nothing, and the item would come back to the inventory to be listed all over again.
		TrackedOffer give = SuggestionEngine.largestOpenBuy(Arrays.asList(
			offer(0, NATURE_RUNE, false, 139, 17_937, 200, "SELLING"),
			offer(1, SAPPHIRE_NECKLACE, false, 440, 10_000, 0, "SELLING")));

		Suggestion suggestion = SuggestionEngine.makeRoomToSell(give, "", "Grimy irit leaf", explainer);

		assertFalse("nothing here is worth cancelling", suggestion.isActionable());
		assertTrue("and the player is told why the wait is short",
			suggestion.getHeadline().contains("every slot is busy"));
	}

	/**
	 * The other half: which holdings hold a slot back at all. The reservation reads the decision the
	 * sell pass has just published, so the card and the reservation cannot disagree.
	 */
	@Test
	public void aHoldingFarFromItsExitDoesNotReserveASlot()
	{
		SellDecision waiting = SellDecision.hold("Waiting for 1,449 gp.", 90);

		assertFalse("a price hours away must not hold a slot idle",
			SuggestionEngine.exitIsNear(new PositionStatus(waiting, false, false)));
	}

	@Test
	public void aHoldingNearItsExitStillReservesOne()
	{
		SellDecision arriving = SellDecision.hold("Almost there.", 5).withExitNear(true);

		assertTrue("a slot must be there when the price arrives",
			SuggestionEngine.exitIsNear(new PositionStatus(arriving, false, false)));
	}

	@Test
	public void aHoldingWithNoDecisionYetReservesOne()
	{
		// A position seen before the first sell evaluation. On no information the safe direction is
		// to reserve: refusing to is the one that can strand a holding with nowhere to sell it.
		assertTrue(SuggestionEngine.exitIsNear(null));
	}
}
