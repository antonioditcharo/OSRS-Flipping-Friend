package com.flippingfriend.model;

import com.flippingfriend.model.SuggestionEngine.Reprice;
import com.flippingfriend.model.SuggestionEngine.RepriceVerdict;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Finishing a reprice instead of abandoning it halfway.
 * <p>
 * The Grand Exchange has no way to change the price of an offer that is already running, so
 * "reprice this" is three separate actions: cancel, collect, place again. The engine issued the
 * first, and the cancel and the collect between them destroyed every trace of why — the offer left
 * the book, the reprice card went with it, and what the player saw next was whatever the planner
 * ranked top that cycle, at whatever size it liked. Telling somebody to abandon a working offer and
 * then not replacing it is worse than never having mentioned it.
 * <p>
 * These are the branches that decide whether the instruction is finished, held, or dropped. The
 * distinction between holding and dropping is the whole point: dropping because a slot was
 * momentarily busy would leave the player having cancelled an offer for nothing.
 */
public class RepriceContinuationTest
{
	private static final int ITEM = 2361;
	private static final long NOW = 1_700_000_000L;

	private static Reprice pending()
	{
		return new Reprice(ITEM, 500, NOW);
	}

	@Test
	public void theReplacementIsPlacedOnceTheOldOfferIsGone()
	{
		assertEquals(RepriceVerdict.PLACE, SuggestionEngine.repriceVerdict(
			pending(), NOW + 30, false, false, false, 3));
	}

	@Test
	public void nothingIsSaidWhileTheOfferIsStillStanding()
	{
		// The player has not cancelled yet, so the reprice card itself is still the right thing to
		// show. Saying "place it again" over the top of that would be two instructions at once.
		assertEquals(RepriceVerdict.WAIT, SuggestionEngine.repriceVerdict(
			pending(), NOW + 30, true, false, false, 3));
	}

	@Test
	public void aFullBoardIsAReasonToWaitAndNeverToGiveUp()
	{
		// The cancelled offer is still sitting in its slot waiting to be collected, and collecting
		// ranks above this in the chain. Dropping the intent here would be the original bug with an
		// extra step: an offer cancelled on the plugin's instruction and never replaced.
		assertEquals(RepriceVerdict.WAIT, SuggestionEngine.repriceVerdict(
			pending(), NOW + 30, false, false, false, 0));
	}

	@Test
	public void anIntentIsRetiredOnceTheReplacementIsOnTheBoard()
	{
		Reprice done = pending();
		done.markOffered();

		assertEquals("shown, then an offer appeared: that is the replacement, and this is finished",
			RepriceVerdict.DROP,
			SuggestionEngine.repriceVerdict(done, NOW + 60, true, false, false, 3));
	}

	@Test
	public void aStalePriceIsNotWorthPlacing()
	{
		// The quote that justified the reprice is a cancel and a collect old by the time the
		// replacement can go in. Past a few minutes it is simply a different market.
		assertEquals(RepriceVerdict.DROP, SuggestionEngine.repriceVerdict(
			pending(), NOW + 3_600, false, false, false, 3));
	}

	@Test
	public void theTwoRejectionControlsStillWin()
	{
		assertEquals("skipping the item has to end this too", RepriceVerdict.DROP,
			SuggestionEngine.repriceVerdict(pending(), NOW + 30, false, true, false, 3));
		assertEquals("and so does deciding to stop buying altogether", RepriceVerdict.DROP,
			SuggestionEngine.repriceVerdict(pending(), NOW + 30, false, false, true, 3));
	}

	@Test
	public void theReplacementIsSizedToWhatIsStillPossible()
	{
		// Straightforward when nothing has changed.
		assertEquals(500, SuggestionEngine.replacementQuantity(500, 10_000, 100_000_000L, 2_000));

		// The buy limit moved while the offer was being cancelled.
		assertEquals(120, SuggestionEngine.replacementQuantity(500, 120, 100_000_000L, 2_000));

		// The coins did. The unfilled part of the old offer had its gold reserved by the exchange and
		// the cancel hands it back, so affordability is a fresh question rather than a given.
		assertEquals(50, SuggestionEngine.replacementQuantity(500, 10_000, 100_000L, 2_000));

		assertEquals("nothing left to place is not an offer", 0,
			SuggestionEngine.replacementQuantity(500, 0, 100_000_000L, 2_000));
		assertEquals(0, SuggestionEngine.replacementQuantity(500, 10_000, 0, 2_000));
	}
}
