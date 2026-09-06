package com.flippingfriend.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.flippingfriend.session.Position;
import java.time.Instant;
import net.runelite.api.GrandExchangeOffer;
import net.runelite.api.GrandExchangeOfferState;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.Mockito;

/**
 * The offer the player has open in the editor is always answered, with a current price.
 *
 * <p>This is the test that could not be written. Two rules cancelled each other out: opening the
 * editor pins the advice showing at that moment, so the player is not yanked onto another trade
 * mid-entry, and the reprice loop skips any offer younger than eight minutes so as not to nag about
 * one just placed. Between them the engine formed no opinion about the very offer being edited, found
 * nothing for that trade to compare against, fell back to the pin, and served the price from before
 * the market moved — for as long as the editor stayed open.
 *
 * <p>It was reported three times and I fixed the wrong thing twice, because every check available was
 * either reading the source or restarting the game. Both of those find what you go looking for.
 */
public class AdjustedOfferGetsAFreshPriceTest
{
	private static final int RUBY = 1603;

	@Rule
	public final TemporaryFolder folder = new TemporaryFolder();

	private EngineHarness harness;

	@Before
	public void setUp()
	{
		harness = new EngineHarness(folder.getRoot().toPath());
		harness.loggedInWith(50_000_000, 7);
	}

	private static GrandExchangeOffer sellOffer(int price, int total, int sold)
	{
		GrandExchangeOffer o = Mockito.mock(GrandExchangeOffer.class);
		Mockito.when(o.getState()).thenReturn(GrandExchangeOfferState.SELLING);
		Mockito.when(o.getItemId()).thenReturn(RUBY);
		Mockito.when(o.getPrice()).thenReturn(price);
		Mockito.when(o.getTotalQuantity()).thenReturn(total);
		Mockito.when(o.getQuantitySold()).thenReturn(sold);
		Mockito.when(o.getSpent()).thenReturn(price * sold);
		return o;
	}

	/**
	 * What the plugin pins when the player opens the editor on this offer.
	 *
	 * <p>Built directly rather than taken from a previous refresh, because the pin comes from the
	 * step guide -- the advice the player was following when they opened the box -- and it always
	 * names that offer's item and slot. Pinning whatever refresh() last returned would be pinning
	 * something about a different trade, which is a different situation entirely and not this one.
	 */
	private void editorOpenOn(int slot, int pinnedPrice)
	{
		harness.engine.setPendingAdjustment(Suggestion.builder(SuggestionType.MODIFY_SELL)
			.item(RUBY, "Ruby")
			.slot(slot)
			.price(pinnedPrice)
			.quantity(1_000)
			.headline("Reprice your Ruby offer")
			.build());
	}

	/** A holding of 1,000 rubies bought at 800, listed for sale at {@code listedAt}. */
	private void holdingListedAt(int listedAt)
	{
		Position position = harness.positions.recordBuy(RUBY, "Ruby", 1_000, 800_000,
			Instant.now());
		position.setTargetSellPrice(listedAt);
		harness.offers.onOfferChanged(1, sellOffer(listedAt, 1_000, 0));
	}

	@Test
	public void anOfferBeingEditedIsPricedAgainstTodayNotAgainstThePin()
	{
		// Listed at 850 while the market pays 900. The offer is seconds old, which is what an offer
		// being edited always is, and that is exactly what used to make it invisible.
		harness.market.trading(RUBY, "Ruby", 780, 900, 25_000);
		holdingListedAt(850);

		editorOpenOn(1, 850);

		Suggestion shown = harness.engine.refresh();

		assertNotNull(shown);
		assertEquals("the advice has to be about the offer being edited", RUBY, shown.getItemId());
		assertTrue("and priced against the market as it stands, not as it was: " + shown.getPrice(),
			shown.getPrice() >= 890);
	}

	@Test
	public void theAnswerFollowsTheMarketWhileTheEditorStaysOpen()
	{
		// The heart of the complaint: the number stopped moving once the box was open.
		harness.market.trading(RUBY, "Ruby", 780, 900, 25_000);
		holdingListedAt(850);
		editorOpenOn(1, 850);

		int first = harness.engine.refresh().getPrice();

		harness.market.trading(RUBY, "Ruby", 800, 950, 25_000);
		int second = harness.engine.refresh().getPrice();

		assertTrue("the price must track the market it is quoted against: " + first + " then "
			+ second, second > first);
	}

	@Test
	public void anOfferAlreadyPricedRightIsSaidToBePricedRight()
	{
		// The narrower hole behind the same symptom. When there is nothing to change the loop used to
		// return nothing, which dropped through to the pin and served a stale number by another
		// route. Declining to change has to be given as an answer.
		harness.market.trading(RUBY, "Ruby", 780, 900, 25_000);
		holdingListedAt(899);
		editorOpenOn(1, 899);

		Suggestion shown = harness.engine.refresh();

		assertEquals(RUBY, shown.getItemId());
		assertEquals("the price it already has, confirmed rather than left to a fallback",
			899, shown.getPrice());
	}

	@Test
	public void withNothingPinnedAFreshOfferIsStillLeftAlone()
	{
		// The rule that has to survive: an offer nobody is editing and nobody has had a chance to
		// look at is not worth interrupting for. Without the pin, the eight-minute gate still holds.
		harness.market.trading(RUBY, "Ruby", 780, 900, 25_000);
		holdingListedAt(850);

		Suggestion shown = harness.engine.refresh();

		assertTrue("a seconds-old offer must not be nagged about: " + shown.getType(),
			shown.getType() != SuggestionType.MODIFY_SELL || shown.getItemId() != RUBY);
	}
}
