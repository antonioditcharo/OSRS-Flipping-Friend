package com.flippingfriend.companion;

import com.flippingfriend.core.PortfolioAllocation;
import com.flippingfriend.data.PluginStorage;
import com.flippingfriend.core.PortfolioCandidate;
import com.flippingfriend.core.PortfolioPlan;
import com.flippingfriend.session.TrackedOffer;
import com.google.gson.Gson;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Which trade the player is shown, and — the harder half — whether it stays shown.
 * <p>
 * A new plan arrives every fifteen to thirty seconds and its ranking is not stable between runs, so
 * taking the top row each time meant the recommendation changed under the player several times a
 * minute. That is indistinguishable from a system that cannot make up its mind, and it is not what
 * exploration is supposed to buy.
 */
public class CompanionClientTest
{
	private static final int FIRE_RUNE = 554;
	private static final int NATURE_RUNE = 561;
	private static final int RUBY = 1603;

	private CompanionClient client;

	@Before
	public void setUp()
	{
		client = new CompanionClient(mock(PluginStorage.class), new Gson(), new SuggestionLedger());
	}

	private static PortfolioAllocation allocation(int rank, int itemId, String name)
	{
		PortfolioCandidate candidate = new PortfolioCandidate(itemId, name, "", 100, 100, 100, 110, 110, 110, 50, 1000,
			500, 10, 20, 0.9, 0.9, 0.3, 0.4, 4.0, Long.MAX_VALUE);
		return new PortfolioAllocation(rank, candidate, "PLACE_BUY");
	}

	private static PortfolioPlan planOf(PortfolioAllocation... rows)
	{
		return new PortfolioPlan("c", 0, Long.MAX_VALUE, "READY", "ok", 1000, Arrays.asList(rows));
	}

	private static PortfolioPlan unavailablePlan(String reason)
	{
		return new PortfolioPlan("c", 0, Long.MAX_VALUE, "UNAVAILABLE", reason, 0,
			Collections.emptyList());
	}

	private com.flippingfriend.model.Suggestion buyNow()
	{
		return client.nextBuySuggestion(new com.flippingfriend.model.Explainer(),
			Collections.emptySet(), Collections.emptySet(), Collections.emptySet());
	}

	@Test
	public void aMomentaryGapDoesNotReplaceTheTradeOnScreen()
	{
		// On three slots a single offer changing state moves the free-slot count between 0 and 1, and
		// the plan flips to "every slot is occupied" and back -- six plans in four seconds during one
		// transition. Each blip used to reach the player as a different answer.
		client.acceptPlan(planOf(allocation(1, FIRE_RUNE, "Fire rune")));
		assertEquals(FIRE_RUNE, buyNow().getItemId());

		client.acceptPlan(unavailablePlan("Every Grand Exchange slot is occupied."));

		assertEquals("the trade on screen must survive a plan built from a staler snapshot",
			FIRE_RUNE, buyNow().getItemId());
	}

	@Test
	public void aHeldTradeIsDroppedOnceItIsNoLongerActionable()
	{
		client.acceptPlan(planOf(allocation(1, FIRE_RUNE, "Fire rune")));
		buyNow();
		client.acceptPlan(unavailablePlan("Every Grand Exchange slot is occupied."));

		// Now it is sitting in a slot, so continuing to suggest buying it would be telling the player
		// to buy what they have just bought.
		com.flippingfriend.model.Suggestion after = client.nextBuySuggestion(
			new com.flippingfriend.model.Explainer(), Collections.emptySet(), Collections.emptySet(),
			new HashSet<>(Collections.singletonList(FIRE_RUNE)));

		assertEquals(com.flippingfriend.model.SuggestionType.WAIT, after.getType());
	}

	@Test
	public void anExpiredPlanMeansTheCompanionHasGoneQuiet()
	{
		// The caller uses this to decide whether the built-in engine should be asked instead. A plan
		// that says "not now" is an answer; no plan at all is silence, and only silence justifies
		// putting a trade chosen on different rules in front of the player.
		client.acceptPlan(new PortfolioPlan("c", 0, 1, "READY", "ok", 1000,
			Arrays.asList(allocation(1, FIRE_RUNE, "Fire rune"))));
		org.junit.Assert.assertFalse("an expired plan is not an answer", client.hasFreshPlan());

		client.acceptPlan(unavailablePlan("Every Grand Exchange slot is occupied."));
		org.junit.Assert.assertTrue("a considered refusal is an answer", client.hasFreshPlan());
	}

	@Test
	public void theFirstPlanIsTakenFromTheTop()
	{
		PortfolioAllocation chosen = client.select(
			planOf(allocation(1, FIRE_RUNE, "Fire rune"), allocation(2, NATURE_RUNE, "Nature rune")),
			Collections.emptySet(), Collections.emptySet());

		assertEquals(FIRE_RUNE, chosen.getCandidate().getItemId());
	}

	@Test
	public void aReorderingReplanDoesNotMoveTheRecommendation()
	{
		// The whole point. The ranking objective carries a Thompson draw, so near-equal candidates
		// swap places between plans for reasons that have nothing to do with the market changing.
		client.select(planOf(allocation(1, FIRE_RUNE, "Fire rune"),
			allocation(2, NATURE_RUNE, "Nature rune")), Collections.emptySet(), Collections.emptySet());

		PortfolioAllocation after = client.select(
			planOf(allocation(1, NATURE_RUNE, "Nature rune"), allocation(2, FIRE_RUNE, "Fire rune")),
			Collections.emptySet(), Collections.emptySet());

		assertEquals("the trade on screen should still be the trade on screen",
			FIRE_RUNE, after.getCandidate().getItemId());
	}

	@Test
	public void anIncumbentThatLeavesThePlanReleasesItsHold()
	{
		client.select(planOf(allocation(1, FIRE_RUNE, "Fire rune")),
			Collections.emptySet(), Collections.emptySet());

		PortfolioAllocation after = client.select(
			planOf(allocation(1, NATURE_RUNE, "Nature rune"), allocation(2, RUBY, "Ruby")),
			Collections.emptySet(), Collections.emptySet());

		assertEquals("holding a trade the plan has dropped would be worse than switching",
			NATURE_RUNE, after.getCandidate().getItemId());
	}

	@Test
	public void skippingTheIncumbentMovesOn()
	{
		client.select(planOf(allocation(1, FIRE_RUNE, "Fire rune"),
			allocation(2, NATURE_RUNE, "Nature rune")), Collections.emptySet(), Collections.emptySet());

		PortfolioAllocation after = client.select(
			planOf(allocation(1, FIRE_RUNE, "Fire rune"), allocation(2, NATURE_RUNE, "Nature rune")),
			Collections.emptySet(), new HashSet<>(Collections.singletonList(FIRE_RUNE)));

		assertEquals(NATURE_RUNE, after.getCandidate().getItemId());
	}

	@Test
	public void blockingAnItemByNameKeepsItOffScreen()
	{
		// "Never trade this" writes a name into config. It was honoured only by the built-in engine,
		// whose suggestion the companion then replaced, so pressing it changed nothing.
		PortfolioAllocation after = client.select(
			planOf(allocation(1, FIRE_RUNE, "Fire rune"), allocation(2, NATURE_RUNE, "Nature rune")),
			new HashSet<>(Collections.singletonList("fire rune")), Collections.emptySet());

		assertEquals(NATURE_RUNE, after.getCandidate().getItemId());
	}

	@Test
	public void anItemYouAlreadyHaveAnOfferOnIsNotSuggestedAgain()
	{
		// The built-in engine has always excluded these, in its own words, because otherwise "the
		// engine would cheerfully tell you to buy the thing you just bought, over and over, until it
		// filled." The companion path -- which is the one that now produces the buy -- had no notion
		// of open offers at all.
		PortfolioAllocation after = client.select(
			planOf(allocation(1, FIRE_RUNE, "Fire rune"), allocation(2, NATURE_RUNE, "Nature rune")),
			Collections.emptySet(), Collections.emptySet(),
			new HashSet<>(Collections.singletonList(FIRE_RUNE)));

		assertEquals(NATURE_RUNE, after.getCandidate().getItemId());
	}

	@Test
	public void placingTheOfferReleasesTheIncumbentRatherThanPinningIt()
	{
		// The regression the lock introduced. Holding a recommendation is right until you act on it;
		// after that, continuing to hold it is the worst possible behaviour, because the plan will
		// keep listing the item until the offer fills.
		client.select(planOf(allocation(1, FIRE_RUNE, "Fire rune"),
			allocation(2, NATURE_RUNE, "Nature rune")), Collections.emptySet(), Collections.emptySet());

		PortfolioAllocation after = client.select(
			planOf(allocation(1, FIRE_RUNE, "Fire rune"), allocation(2, NATURE_RUNE, "Nature rune")),
			Collections.emptySet(), Collections.emptySet(),
			new HashSet<>(Collections.singletonList(FIRE_RUNE)));

		assertEquals("once it is on offer the hold must break",
			NATURE_RUNE, after.getCandidate().getItemId());
	}

	@Test
	public void clearingTheIncumbentLetsThePlanChooseAgain()
	{
		// Settings changed, or the session ended. A held trade belongs to the context it was chosen in.
		client.select(planOf(allocation(1, FIRE_RUNE, "Fire rune"),
			allocation(2, NATURE_RUNE, "Nature rune")), Collections.emptySet(), Collections.emptySet());

		client.clearIncumbent();

		PortfolioAllocation after = client.select(
			planOf(allocation(1, NATURE_RUNE, "Nature rune"), allocation(2, FIRE_RUNE, "Fire rune")),
			Collections.emptySet(), Collections.emptySet());

		assertEquals("with nothing held, the plan's own order wins",
			NATURE_RUNE, after.getCandidate().getItemId());
	}

	@Test
	public void offerPublicationIsPersistedBeforeDeliveryIsAttempted()
	{
		PluginStorage storage = mock(PluginStorage.class);
		CompanionEventOutbox outbox = mock(CompanionEventOutbox.class);
		when(outbox.enqueue(anyString(), anyString())).thenReturn(true);
		CompanionClient publishing = new CompanionClient(
			storage, new Gson(), new SuggestionLedger(), outbox);

		TrackedOffer offer = new TrackedOffer(2, RUBY, true, 800, 100, 1_000);
		offer.setState("BUYING");
		offer.setQuantityFilled(10);
		offer.setSpent(8_000);

		publishing.publishOffer(offer);

		org.mockito.InOrder ordered = inOrder(outbox);
		ordered.verify(outbox).enqueue(
			org.mockito.ArgumentMatchers.eq("events/ge-offer"), anyString());
		ordered.verify(outbox).drain(any(CompanionEventOutbox.Sender.class));
	}

	@Test
	public void anEventThatCannotBePersistedIsNotDelivered()
	{
		PluginStorage storage = mock(PluginStorage.class);
		CompanionEventOutbox outbox = mock(CompanionEventOutbox.class);
		when(outbox.enqueue(anyString(), anyString())).thenReturn(false);
		CompanionClient publishing = new CompanionClient(
			storage, new Gson(), new SuggestionLedger(), outbox);

		publishing.publishOffer(
			new TrackedOffer(1, FIRE_RUNE, true, 5, 100, 1_000));

		verify(outbox, never()).drain(any(CompanionEventOutbox.Sender.class));
		assertEquals(
			"Could not persist the companion event for later delivery.",
			publishing.lastError());
	}
	@Test
	public void rejectingEverythingIsAnAnswerRatherThanACrash()
	{
		List<String> both = Arrays.asList("fire rune", "nature rune");
		PortfolioAllocation after = client.select(
			planOf(allocation(1, FIRE_RUNE, "Fire rune"), allocation(2, NATURE_RUNE, "Nature rune")),
			new HashSet<>(both), Collections.emptySet());

		assertNull(after);
	}
}
