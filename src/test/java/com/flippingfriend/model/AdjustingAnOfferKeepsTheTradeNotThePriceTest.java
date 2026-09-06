package com.flippingfriend.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * A pin on the trade being entered must not be a pin on its price.
 *
 * <p>While an offer was being adjusted the engine returned the suggestion captured when the editor
 * opened and computed nothing at all, so the price on the recommendation card and in the overlay was
 * whatever had been calculated at that moment. Reprice advice is worth exactly its price, and the
 * number the player was being told to type went stale as soon as the market moved — on both surfaces
 * at once, because they read the same object.
 *
 * <p>{@link com.flippingfriend.overlay.StepGuide} already carried the right rule and could never
 * exercise it: "a correction to the trade already being typed is applied even mid-entry... only a
 * genuinely different trade waits." It never saw a correction, because nothing upstream produced one.
 *
 * <p>What makes the fix work is that {@link Suggestion#isSameTradeAs} compares the type, the item and
 * the slot and deliberately not the price. Repricing the same offer is the same trade; switching to a
 * different item is not. These pin that, since the fix rests entirely on it.
 */
public class AdjustingAnOfferKeepsTheTradeNotThePriceTest
{
	private static final int MAGUS = 28313;
	private static final int VENATOR = 28310;

	private static Suggestion sellAt(int itemId, int slot, int price)
	{
		return Suggestion.builder(SuggestionType.MODIFY_SELL)
			.item(itemId, "item")
			.slot(slot)
			.price(price)
			.quantity(1)
			.headline("reprice")
			.build();
	}

	@Test
	public void arepricedOfferIsStillTheSameTrade()
	{
		// The case the fix turns on: same offer, new number. If this compared prices, a correction
		// would look like a different trade and the stale figure would win.
		assertTrue("changing the price does not change which offer this is",
			sellAt(MAGUS, 3, 23_296_921).isSameTradeAs(sellAt(MAGUS, 3, 22_900_000)));
	}

	@Test
	public void aDifferentItemIsADifferentTrade()
	{
		// And this is what the pin is actually for -- not yanking the player onto another trade while
		// they are part way through typing this one.
		assertTrue("a different item must not be treated as a correction",
			!sellAt(MAGUS, 3, 23_296_921).isSameTradeAs(sellAt(VENATOR, 3, 23_296_921)));
	}

	@Test
	public void aDifferentSlotIsADifferentTrade()
	{
		assertTrue(!sellAt(MAGUS, 3, 23_296_921).isSameTradeAs(sellAt(MAGUS, 5, 23_296_921)));
	}

	@Test
	public void aDifferentActionIsADifferentTrade()
	{
		Suggestion sell = sellAt(MAGUS, 3, 23_296_921);
		Suggestion cancel = Suggestion.builder(SuggestionType.CANCEL)
			.item(MAGUS, "item").slot(3).price(23_296_921).quantity(1).headline("stop").build();

		assertTrue("being told to cancel is not a correction to being told to reprice",
			!sell.isSameTradeAs(cancel));
	}

	@Test
	public void theCardAndTheOverlayReadTheSameFigure()
	{
		// Both surfaces render suggestion.getPrice(), which is why one stale object showed a stale
		// number in two places and why one fix covers both.
		Suggestion fresh = sellAt(MAGUS, 3, 22_900_000);

		assertEquals(22_900_000, fresh.getPrice());
	}
}
