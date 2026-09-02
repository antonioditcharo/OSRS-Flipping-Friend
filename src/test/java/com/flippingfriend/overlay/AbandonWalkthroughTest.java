package com.flippingfriend.overlay;

import com.flippingfriend.model.Suggestion;
import com.flippingfriend.model.SuggestionType;
import net.runelite.api.Client;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * What the on-screen walkthrough does with a CANCEL.
 * <p>
 * CANCEL used to mean "cut a loss", which the player performs by selling, so it shared the sell
 * walkthrough. It now means abandon the offer outright — and walking someone through placing a sell
 * offer would be the exact opposite of the instruction on the card.
 */
public class AbandonWalkthroughTest
{
	private static Suggestion cancel()
	{
		return Suggestion.builder(SuggestionType.CANCEL)
			.item(6575, "Sapphire necklace")
			.slot(0)
			.price(434)
			.quantity(9_176)
			.headline("Cancel your Sapphire necklace offer")
			.detail("Sell-only mode is on.")
			.build();
	}

	@Test
	public void aCancelIsNotWalkedThroughAsASell()
	{
		GeWidgetResolver resolver = mock(GeWidgetResolver.class);
		when(resolver.isGeOpen()).thenReturn(true);
		StepGuide guide = new StepGuide(mock(Client.class), resolver);
		guide.setSuggestion(cancel());

		StepGuide.GuideState state = guide.resolve(3, new boolean[]{true, false, false});

		assertEquals("abandoning an offer is its own step, not the sell flow",
			GuideStep.ABANDON_OFFER, state.getStep());
	}

	@Test
	public void theAbandonInstructionDoesNotPromiseAReplacement()
	{
		// CANCEL_OFFER exists for repricing, where something *is* placed afterwards. The two must not
		// share wording, or a cancel tells the player to put the offer back up.
		assertTrue("the reprice step is the one that replaces",
			GuideStep.CANCEL_OFFER.getInstruction().contains("replaced"));
		assertTrue("abandoning frees the slot and stops",
			!GuideStep.ABANDON_OFFER.getInstruction().contains("replace"));
	}
}
