package com.flippingfriend.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import com.flippingfriend.session.AccountState;
import java.time.Instant;
import java.util.HashMap;
import net.runelite.api.GrandExchangeOffer;
import net.runelite.api.GrandExchangeOfferState;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.Mockito;

/**
 * The order of the decision chain, which is the design and had never been tested.
 *
 * <p>Collect, then reprice, then sell, then — only if asked — buy. The order is not incidental and
 * the code says so where it matters: abandoning a buy sits "above repricing, because there is no
 * point tuning the price of an offer that is about to be abandoned". Precedence bugs are silent.
 * Nothing fails, nothing throws; the plugin simply gives the second-best instruction, and the only
 * way anyone finds out is by playing.
 *
 * <p>These were unwritable until {@link EngineHarness} existed, because building a
 * {@link SuggestionEngine} needed a client, a network and a disk.
 */
public class DecisionChainOrderTest
{
	private static final int RUBY = 1603;
	private static final int WHIP = 4151;

	@Rule
	public final TemporaryFolder folder = new TemporaryFolder();

	private EngineHarness harness;

	@Before
	public void setUp()
	{
		harness = new EngineHarness(folder.getRoot().toPath());
		harness.loggedInWith(50_000_000, 8);
		harness.market.trading(RUBY, "Ruby", 780, 900, 25_000);
		harness.market.trading(WHIP, "Abyssal whip", 1_500_000, 1_600_000, 70);
	}

	private static GrandExchangeOffer offer(GrandExchangeOfferState state, int itemId, int price,
		int total, int done)
	{
		GrandExchangeOffer o = Mockito.mock(GrandExchangeOffer.class);
		Mockito.when(o.getState()).thenReturn(state);
		Mockito.when(o.getItemId()).thenReturn(itemId);
		Mockito.when(o.getPrice()).thenReturn(price);
		Mockito.when(o.getTotalQuantity()).thenReturn(total);
		Mockito.when(o.getQuantitySold()).thenReturn(done);
		Mockito.when(o.getSpent()).thenReturn(price * done);
		return o;
	}

	// ------------------------------------------------------------------ the gates at the top

	@Test
	public void loggedOutAsksYouToLogIn()
	{
		harness.account.set(AccountState.loggedOut());

		assertEquals(SuggestionType.WAIT, harness.engine.refresh().getType());
	}

	@Test
	public void anIronmanIsToldItCannotFlip()
	{
		// Ironman accounts can only buy bonds on the exchange, so there is nothing here for them.
		harness.account.set(new AccountState(true, true, true, 50_000_000, 0, 0, 8, 8, 0, true,
			new HashMap<>(), new HashMap<>(), new HashMap<>()));

		Suggestion shown = harness.engine.refresh();

		assertEquals(SuggestionType.WAIT, shown.getType());
		assertTrue(shown.getHeadline().toLowerCase().contains("ironman"));
	}

	@Test
	public void anEmptyMarketProducesNoAdviceRatherThanBadAdvice()
	{
		EngineHarness bare = new EngineHarness(folder.getRoot().toPath());
		bare.loggedInWith(50_000_000, 8);

		Suggestion shown = bare.engine.refresh();

		assertNotEquals("nothing is known, so nothing is recommended", SuggestionType.BUY,
			shown.getType());
	}

	// ------------------------------------------------------------------ collect goes first

	@Test
	public void collectingComesBeforeEverythingElse()
	{
		// A finished offer is holding both a slot and the coins or goods it produced. Nothing else in
		// the chain can proceed properly until it is emptied, which is why it is first.
		harness.offers.onOfferChanged(1, offer(GrandExchangeOfferState.BUYING, RUBY, 780, 1_000, 0));
		harness.offers.onOfferChanged(1, offer(GrandExchangeOfferState.BOUGHT, RUBY, 780, 1_000,
			1_000));

		Suggestion shown = harness.engine.refresh();

		assertEquals(SuggestionType.COLLECT, shown.getType());
		assertEquals(RUBY, shown.getItemId());
	}

	@Test
	public void collectingOutranksRepricing()
	{
		// One offer needs collecting, another is badly priced. Tuning a price while goods sit
		// uncollected spends the player's attention on the lesser of the two.
		//
		// The competitor has to be genuinely live or this proves nothing. The first version placed a
		// mispriced sell offer that was seconds old, so the eight-minute gate skipped it, no reprice
		// was ever produced, and collect won by default -- moving collect BELOW repricing left the
		// test passing, which is how that was caught. An offer standing for half an hour is what makes
		// the reprice path fire.
		harness.positions.recordBuy(WHIP, "Abyssal whip", 1, 1_400_000, Instant.now());
		harness.standingOffer(2, WHIP, "Abyssal whip", false, 2_000_000, 1, 30);

		assertEquals("the premise: with nothing to collect, repricing is what comes back",
			SuggestionType.MODIFY_SELL, harness.engine.refresh().getType());

		harness.offers.onOfferChanged(1, offer(GrandExchangeOfferState.BUYING, RUBY, 780, 1_000, 0));
		harness.offers.onOfferChanged(1, offer(GrandExchangeOfferState.BOUGHT, RUBY, 780, 1_000,
			1_000));

		assertEquals("and collecting takes precedence over it", SuggestionType.COLLECT,
			harness.engine.refresh().getType());
	}

	@Test
	public void aPinnedTradeOutranksTheWholeChain()
	{
		// Found while writing the test above, and worth keeping: pinning an offer short-circuits
		// everything, including collecting. That is what stops the player being yanked off a trade
		// they are part way through typing, and it is bounded -- the pin is dropped the moment the
		// editor closes. Recorded because it is surprising, and because a future change that made
		// collect jump the pin would be a regression nothing else would notice.
		harness.positions.recordBuy(WHIP, "Abyssal whip", 1, 1_400_000, Instant.now());
		harness.standingOffer(2, WHIP, "Abyssal whip", false, 2_000_000, 1, 30);
		harness.engine.setPendingAdjustment(Suggestion.builder(SuggestionType.MODIFY_SELL)
			.item(WHIP, "Abyssal whip").slot(2).price(2_000_000).quantity(1)
			.headline("Reprice").build());

		harness.offers.onOfferChanged(1, offer(GrandExchangeOfferState.BUYING, RUBY, 780, 1_000, 0));
		harness.offers.onOfferChanged(1, offer(GrandExchangeOfferState.BOUGHT, RUBY, 780, 1_000,
			1_000));

		assertEquals("the trade being typed keeps the card", WHIP,
			harness.engine.refresh().getItemId());
	}

	// ------------------------------------------------------------------ selling before buying

	@Test
	public void aHoldingIsSoldBeforeANewTradeIsOpened()
	{
		// Capital already committed is worth more attention than capital not yet spent. A plugin that
		// suggests a new purchase while a position waits to be sold is one that fills every slot and
		// then cannot move.
		//
		// Asserted as "it is a sale" rather than "it is not a purchase". The first version took the
		// negative and passed against an engine with the sell stage deleted entirely -- with two items
		// in the market and nothing worth buying, the answer was WAIT, which is not a purchase either.
		// A negative assertion is satisfied by every kind of nothing.
		harness.positions.recordBuy(WHIP, "Abyssal whip", 1, 1_400_000, Instant.now());

		Suggestion shown = harness.engine.refresh();

		assertNotNull(shown);
		assertEquals("the holding is what gets acted on", SuggestionType.SELL, shown.getType());
		assertEquals(WHIP, shown.getItemId());
	}

	@Test
	public void noFreeSlotsSaysSoRatherThanLookingForATrade()
	{
		// Asserted on WHICH wait it is, not that it is one.
		//
		// Deleting the slot guard entirely left this passing when it only checked the type: the engine
		// fell through to buy selection, found nothing worth trading, and returned a WAIT of a
		// different kind. Two dead ends that look identical through a type check, and the difference
		// between them is the whole point -- one is "there is nowhere to put a trade", the other is
		// "there is nothing worth trading".
		harness.loggedInWith(50_000_000, 0);

		Suggestion shown = harness.engine.refresh();

		assertEquals(SuggestionType.WAIT, shown.getType());
		assertTrue("the player is told the slots are full, not that the market is dull: "
			+ shown.getHeadline(), shown.getHeadline().toLowerCase().contains("slots"));
	}

	// ------------------------------------------------------------------ who owns the buy decision

	@Test
	public void theEngineDeclinesToPickABuyWhenItIsNotAskedTo()
	{
		// The companion owns which new position to open. This engine's own selection used to run
		// every cycle and be thrown away, which is where every divergence between the two came from:
		// the buy limit rule, the exposure ceilings and the volume screen all had a second
		// implementation that ran, was discarded, and could rot without anything failing.
		//
		// Returning null is how it says "nothing local to do, a buy is what is needed" and leaves the
		// answer to whoever is authoritative.
		assertNull(harness.engine.refresh(false));
	}

	@Test
	public void andStillHandlesLocalWorkWhenNotAskedToBuy()
	{
		// The other half of that contract: declining to choose a purchase must not mean declining to
		// notice a finished offer.
		harness.offers.onOfferChanged(1, offer(GrandExchangeOfferState.BUYING, RUBY, 780, 1_000, 0));
		harness.offers.onOfferChanged(1, offer(GrandExchangeOfferState.BOUGHT, RUBY, 780, 1_000,
			1_000));

		Suggestion shown = harness.engine.refresh(false);

		assertNotNull("collecting is local work and still gets done", shown);
		assertEquals(SuggestionType.COLLECT, shown.getType());
	}

	// ------------------------------------------------------------------ winding down

	@Test
	public void sellOnlyNeverSuggestsBuyingAnything()
	{
		harness.engine.setSellOnly(true);

		Suggestion shown = harness.engine.refresh();

		assertNotEquals(SuggestionType.BUY, shown.getType());
		assertNotEquals(SuggestionType.MODIFY_BUY, shown.getType());
	}

	@Test
	public void sellOnlyAbandonsAnOpenBuyBeforeTuningItsPrice()
	{
		// The precedence the code calls out by name: "above repricing, because there is no point
		// tuning the price of an offer that is about to be abandoned".
		harness.offers.onOfferChanged(1, offer(GrandExchangeOfferState.BUYING, RUBY, 700, 1_000, 0));
		harness.engine.setSellOnly(true);

		Suggestion shown = harness.engine.refresh();

		assertEquals("stop buying it, do not reprice it", SuggestionType.CANCEL, shown.getType());
		assertEquals(RUBY, shown.getItemId());
	}

	@Test
	public void leavingSellOnlyStopsAbandoningBuys()
	{
		// Sell-only is a way to end a session, not a state to get stuck in.
		//
		// Asserted on the behaviour that sell-only actually changes, not on the type of whatever
		// comes back. The first version of this checked that the answer was no longer WAIT, which
		// failed for a reason that had nothing to do with sell-only: a two-item market with no
		// holdings has nothing to suggest, so WAIT is the correct answer either way. A test that can
		// fail for a reason it is not about is a test that will be argued with rather than believed.
		harness.offers.onOfferChanged(1, offer(GrandExchangeOfferState.BUYING, RUBY, 700, 1_000, 0));

		harness.engine.setSellOnly(true);
		assertEquals(SuggestionType.CANCEL, harness.engine.refresh().getType());

		harness.engine.setSellOnly(false);
		assertNotEquals("the buy is left alone once the wind-down is over",
			SuggestionType.CANCEL, harness.engine.refresh().getType());
	}
}
