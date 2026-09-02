package com.flippingfriend.model;

import com.flippingfriend.session.TrackedOffer;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Winding a session down.
 * <p>
 * The point of the mode is to stop opening new positions and release what is committed. The coins
 * tied up in an open buy are the largest single thing standing between the player and a finished
 * session, so those come back first.
 */
public class SellOnlyTest
{
	private static TrackedOffer offer(int slot, int itemId, boolean buying, int price, int total,
		int filled, String state)
	{
		TrackedOffer offer = new TrackedOffer(slot, itemId, buying, price, total, 0L);
		offer.setQuantityFilled(filled);
		offer.setState(state);
		return offer;
	}

	@Test
	public void theBuyHoldingTheMostCoinsIsAbandonedFirst()
	{
		// The live shape when this was written: a Sapphire necklace buy with most of it unfilled, and
		// a smaller Nature rune buy alongside it.
		TrackedOffer sapphire = offer(0, 6575, true, 434, 13_883, 4_707, "BUYING");
		TrackedOffer nature = offer(2, 561, true, 139, 17_937, 0, "BUYING");

		TrackedOffer chosen = SuggestionEngine.largestOpenBuy(Arrays.asList(nature, sapphire));

		// Sapphire holds 434 x 9,176 = 3.98M against Nature's 139 x 17,937 = 2.49M.
		assertEquals("the larger reservation comes back first", 6575, chosen.getItemId());
	}

	@Test
	public void aSellIsNeverCancelled()
	{
		// Cancelling a sell would put the item back in the inventory to be listed all over again --
		// the exact opposite of winding down.
		TrackedOffer selling = offer(1, 1987, false, 72, 8_310, 4_000, "SELLING");

		assertNull("a sell offer is already doing what sell-only wants",
			SuggestionEngine.largestOpenBuy(Collections.singletonList(selling)));
	}

	@Test
	public void aFinishedOrCancelledBuyIsLeftAlone()
	{
		List<TrackedOffer> offers = Arrays.asList(
			// Fully bought: nothing is reserved any more, it is a position now.
			offer(0, 1987, true, 69, 20_000, 20_000, "BOUGHT"),
			// Already cancelled by the player.
			offer(1, 561, true, 139, 17_937, 0, "CANCELLED_BUY"));

		assertNull("neither has coins left to release",
			SuggestionEngine.largestOpenBuy(offers));
	}

	@Test
	public void nothingOpenMeansNothingToCancel()
	{
		assertNull(SuggestionEngine.largestOpenBuy(Collections.emptyList()));
	}

	@Test
	public void aPartlyFilledBuyIsMeasuredOnWhatIsStillReserved()
	{
		// The exchange refunds as an order fills, so what is left to release is the unfilled part --
		// not the whole order. A nearly complete large order should lose to a smaller untouched one.
		TrackedOffer nearlyDone = offer(0, 1987, true, 100, 10_000, 9_900, "BUYING");
		TrackedOffer untouched = offer(1, 561, true, 100, 1_000, 0, "BUYING");

		assertTrue("100 x 100 reserved beats 100 x 10 still outstanding",
			SuggestionEngine.largestOpenBuy(Arrays.asList(nearlyDone, untouched)) == untouched);
	}
}
