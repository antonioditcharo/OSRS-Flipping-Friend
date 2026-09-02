package com.flippingfriend.companion;

import java.time.Instant;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Attribution decides which outcomes the model is allowed to learn from, so both kinds of mistake
 * matter. Crediting the plugin with a trade the player made on their own would let it congratulate
 * itself for someone else's decision; refusing to credit a trade that was plainly taken from the
 * panel would throw away the only evidence there is.
 */
public class SuggestionLedgerTest
{
	private SuggestionLedger ledger;

	@Before
	public void setUp()
	{
		ledger = new SuggestionLedger();
	}

	private static long now()
	{
		return Instant.now().getEpochSecond();
	}

	@Test
	public void attributesAnOfferToTheAdviceThatPromptedIt()
	{
		ledger.recorded("plan-7", 4151, true, 1_000_000, 5, 12, 12.0);

		SuggestionLedger.Advice advice = ledger.attribute(4151, true, now());
		assertNotNull(advice);
		assertEquals("plan-7", advice.getRecommendationId());
		assertEquals(1_000_000, advice.getPrice());
		assertEquals(5, advice.getQuantity());
		assertEquals(12, advice.getQuoteAgeSeconds());
		assertEquals("the prediction must survive, or the outcome cannot be scored against it",
			12.0, advice.getPredictedMinutes(), 1e-9);
	}

	@Test
	public void doesNotCreditAdviceForATradeOnTheOtherSide()
	{
		// We suggested buying; selling the same item is a different decision entirely, usually the
		// exit from a position we advised hours ago.
		ledger.recorded("plan-7", 4151, true, 1_000_000, 5, 0, 12.0);

		assertNull(ledger.attribute(4151, false, now()));
	}

	@Test
	public void doesNotCreditAdviceForADifferentItem()
	{
		ledger.recorded("plan-7", 4151, true, 1_000_000, 5, 0, 12.0);

		assertNull(ledger.attribute(561, true, now()));
	}

	@Test
	public void repeatingTheSameTradeUnderANewPlanIdDoesNotRestartTheClock()
	{
		// The panel re-records its recommendation on every refresh, and each refresh carries a new
		// plan correlation id, because the plugin generates one with UUID.randomUUID() on every
		// account-state POST. Treating the id as identity therefore made every repeat look like new
		// advice and restamped the clock, so the attribution window could never elapse for anything
		// still on screen -- and an unprompted trade in a top-ranked item was credited to the plan.
		//
		// Sameness has to be the trade itself.
		ledger.recorded("plan-7", 4151, true, 1_000_000, 5, 0, 12.0);
		long first = ledger.attribute(4151, true, Instant.now().getEpochSecond()).getSuggestedAt();

		ledger.recorded("plan-8", 4151, true, 1_000_000, 5, 0, 12.0);
		ledger.recorded("plan-9", 4151, true, 1_000_000, 5, 0, 12.0);

		assertEquals("the same trade repeated keeps the moment it was first shown",
			first, ledger.attribute(4151, true, Instant.now().getEpochSecond()).getSuggestedAt());
	}

	@Test
	public void aGenuinelyDifferentTradeDoesRestartTheClock()
	{
		ledger.recorded("plan-7", 4151, true, 1_000_000, 5, 0, 12.0);
		long first = ledger.attribute(4151, true, Instant.now().getEpochSecond()).getSuggestedAt();

		// A new price is new advice, and the player has only just been shown it.
		ledger.recorded("plan-7", 4151, true, 1_050_000, 5, 0, 12.0);

		assertEquals(1_050_000,
			ledger.attribute(4151, true, Instant.now().getEpochSecond()).getPrice());
		assertTrue("a changed price is new advice",
			ledger.attribute(4151, true, Instant.now().getEpochSecond()).getSuggestedAt() >= first);
	}

	@Test
	public void staleAdviceIsNotCreditedWithACoincidence()
	{
		ledger.recorded("plan-7", 4151, true, 1_000_000, 5, 0, 12.0);

		long muchLater = now() + SuggestionLedger.ATTRIBUTION_WINDOW_SECONDS + 1;
		assertNull("an unrelated trade in the same item an hour later is not our doing",
			ledger.attribute(4151, true, muchLater));
	}

	@Test
	public void adviceFollowedImperfectlyIsStillAttributed()
	{
		// The player rounds the price and buys half as many. That is the advice being followed
		// imperfectly, not a different decision, and the divergence is exactly what we want recorded.
		ledger.recorded("plan-7", 4151, true, 1_000_000, 10, 0, 12.0);

		SuggestionLedger.Advice advice = ledger.attribute(4151, true, now());
		assertNotNull(advice);
		assertEquals("what we suggested is kept, so it can be compared with what was done",
			1_000_000, advice.getPrice());
		assertEquals(10, advice.getQuantity());
	}

	@Test
	public void theLatestAdviceForAnItemSupersedesTheEarlier()
	{
		ledger.recorded("plan-1", 4151, true, 1_000_000, 5, 0, 12.0);
		ledger.recorded("plan-2", 4151, true, 1_010_000, 6, 0, 12.0);

		assertEquals("plan-2", ledger.attribute(4151, true, now()).getRecommendationId());
	}

	@Test
	public void sequenceNumbersStrictlyIncrease()
	{
		long first = ledger.nextSequence();
		long second = ledger.nextSequence();
		long third = ledger.nextSequence();

		assertTrue(first < second && second < third);
	}

	@Test
	public void ignoresIncompleteAdvice()
	{
		ledger.recorded(null, 4151, true, 100, 1, 0, 12.0);
		ledger.recorded("", 4151, true, 100, 1, 0, 12.0);
		ledger.recorded("plan-7", 0, true, 100, 1, 0, 12.0);

		assertNull(ledger.attribute(4151, true, now()));
	}
}
