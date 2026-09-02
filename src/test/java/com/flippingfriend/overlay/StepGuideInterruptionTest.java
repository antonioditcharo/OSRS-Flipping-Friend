package com.flippingfriend.overlay;

import com.flippingfriend.model.Suggestion;
import com.flippingfriend.model.SuggestionType;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * What happens when better advice arrives while the player is halfway through typing an offer.
 * <p>
 * Advice changes constantly and applying it immediately is right almost every time. The exception is
 * expensive: the offer editor is open, a quantity has been entered, and the price is next. Moving
 * every highlight to a different slot at that moment does not merely confuse — it invites an offer
 * placed at the wrong number for the wrong item, which is the exact mistake the walkthrough exists
 * to prevent. So the new advice waits, and the player is told it is waiting.
 */
public class StepGuideInterruptionTest
{
	private GeWidgetResolver resolver;
	private net.runelite.api.Client client;
	private StepGuide guide;

	private static Suggestion buy(int itemId, String name)
	{
		return Suggestion.builder(SuggestionType.BUY)
			.item(itemId, name)
			.price(100)
			.quantity(10)
			.headline("Buy 10 x " + name)
			.build();
	}

	private static Suggestion collect(String name)
	{
		return Suggestion.builder(SuggestionType.COLLECT)
			.item(999, name)
			.headline("Collect " + name)
			.build();
	}

	@Before
	public void setUp()
	{
		resolver = mock(GeWidgetResolver.class);
		client = mock(net.runelite.api.Client.class);
		// These cases all describe what happens on the client thread, which is where the walkthrough
		// runs. Leaving this unstubbed made every widget-reading branch silently unreachable, which
		// is how a client-thread violation in this exact path reached a live game.
		when(client.isClientThread()).thenReturn(true);
		guide = new StepGuide(client, resolver);
	}

	/** Simulates the player having typed the quantity into the open editor. */
	private void beginEntry()
	{
		when(resolver.isSetupOpen()).thenReturn(true);
		net.runelite.api.events.MenuOptionClicked click =
			mock(net.runelite.api.events.MenuOptionClicked.class);
		
		net.runelite.api.widgets.Widget quantityWidget = mock(net.runelite.api.widgets.Widget.class);
		when(resolver.getQuantityControl()).thenReturn(quantityWidget);
		when(click.getWidget()).thenReturn(quantityWidget);
		
		when(click.getMenuOption()).thenReturn("Set quantity");
		guide.onMenuOptionClicked(click);
	}

	@Test
	public void adviceArrivingOffTheClientThreadIsStillApplied()
	{
		// Widgets may only be read on the client thread, so off it the mid-entry question cannot be
		// answered. It must degrade to "not mid-entry" and apply the advice, never throw: an
		// AssertionError escaping here took the recommendation with it, and the panel went quiet
		// with nothing but a line in the client log to say why.
		when(client.isClientThread()).thenReturn(false);
		when(resolver.isSetupOpen()).thenReturn(false);
		guide.setSuggestion(buy(4151, "Abyssal whip"));
		beginEntry();

		guide.setSuggestion(collect("Nature rune"));

		assertEquals("advice must not be lost when the thread cannot answer",
			SuggestionType.COLLECT, guide.getSuggestion().getType());
		assertNull(guide.getPending());
	}

	@Test
	public void newAdviceAppliesImmediatelyWhenNothingIsInProgress()
	{
		when(resolver.isSetupOpen()).thenReturn(false);
		guide.setSuggestion(buy(4151, "Abyssal whip"));
		guide.setSuggestion(collect("Nature rune"));

		assertEquals("with no offer being typed there is nothing to protect",
			SuggestionType.COLLECT, guide.getSuggestion().getType());
		assertNull(guide.getPending());
	}

	@Test
	public void adviceArrivingMidEntryIsHeldBack()
	{
		when(resolver.isSetupOpen()).thenReturn(false);
		guide.setSuggestion(buy(4151, "Abyssal whip"));
		beginEntry();

		guide.setSuggestion(collect("Nature rune"));

		assertEquals("the walkthrough must not move mid-offer", 4151,
			guide.getSuggestion().getItemId());
		assertNotNull("but the player must be told something is waiting", guide.getPending());
		assertEquals(SuggestionType.COLLECT, guide.getPending().getType());
	}

	@Test
	public void heldAdviceTakesOverOnceTheOfferIsDone()
	{
		when(resolver.isSetupOpen()).thenReturn(false);
		guide.setSuggestion(buy(4151, "Abyssal whip"));
		beginEntry();
		guide.setSuggestion(collect("Nature rune"));

		guide.onSetupClosed();

		assertEquals("closing the editor is the safe moment to switch",
			SuggestionType.COLLECT, guide.getSuggestion().getType());
		assertNull(guide.getPending());
	}

	@Test
	public void mostRecentAdviceWinsWhileWaiting()
	{
		// Several changes can arrive during one offer. Only the latest is worth showing; the ones in
		// between were superseded before the player ever saw them.
		when(resolver.isSetupOpen()).thenReturn(false);
		guide.setSuggestion(buy(4151, "Abyssal whip"));
		beginEntry();

		guide.setSuggestion(collect("Nature rune"));
		guide.setSuggestion(collect("Dragon bones"));

		assertEquals("Collect Dragon bones", guide.getPending().getHeadline());
	}

	@Test
	public void anOpenEditorAloneDoesNotHoldAnything()
	{
		// Opening the editor and typing nothing is not being mid-entry. Holding advice then would
		// leave someone staring at stale guidance simply for having the window open.
		when(resolver.isSetupOpen()).thenReturn(true);
		guide.setSuggestion(buy(4151, "Abyssal whip"));
		guide.setSuggestion(collect("Nature rune"));

		assertEquals(SuggestionType.COLLECT, guide.getSuggestion().getType());
		assertNull(guide.getPending());
	}

	@Test
	public void repeatingTheSameAdviceDoesNotResetProgress()
	{
		// The engine re-issues the same advice every few seconds. Treating each as a change would
		// wipe the record of what has been typed and restart the walkthrough continuously.
		when(resolver.isSetupOpen()).thenReturn(false);
		Suggestion advice = buy(4151, "Abyssal whip");
		guide.setSuggestion(advice);
		beginEntry();

		guide.setSuggestion(buy(4151, "Abyssal whip"));

		assertNull("identical advice is not an interruption", guide.getPending());
		assertEquals(4151, guide.getSuggestion().getItemId());
	}

	@Test
	public void aPriceCorrectionOnTheSameTradeAppliesImmediately()
	{
		// The hole that holding advice would otherwise open. If the market moves while the player is
		// typing, the price on screen must move with it — deferring that would mean protecting them
		// from the wrong offer by handing them a stale one instead.
		when(resolver.isSetupOpen()).thenReturn(false);
		guide.setSuggestion(buy(4151, "Abyssal whip"));
		beginEntry();

		Suggestion repriced = Suggestion.builder(SuggestionType.BUY)
			.item(4151, "Abyssal whip")
			.price(1_250)
			.quantity(10)
			.headline("Buy 10 x Abyssal whip")
			.build();
		guide.setSuggestion(repriced);

		assertEquals("a new price for the same trade is a correction, not an interruption",
			1_250, guide.getSuggestion().getPrice());
		assertNull(guide.getPending());
	}

	@Test
	public void aCorrectionDoesNotRestartTheWalkthrough()
	{
		// Progress must survive a reprice. Wiping it would send the player back to re-enter a
		// quantity they had already set, every time the market ticked.
		when(resolver.isSetupOpen()).thenReturn(true);
		guide.setSuggestion(buy(4151, "Abyssal whip"));
		beginEntry();

		guide.setSuggestion(Suggestion.builder(SuggestionType.BUY)
			.item(4151, "Abyssal whip").price(1_250).quantity(10)
			.headline("Buy 10 x Abyssal whip").build());

		// Still mid-entry, so a genuinely different trade must still be held back.
		guide.setSuggestion(collect("Nature rune"));
		assertNotNull("progress should have survived the reprice", guide.getPending());
	}

	@Test
	public void abandoningAnOfferStillReleasesTheQueue()
	{
		// The player may close the editor without placing anything. The queued advice must still be
		// applied, or they would be left following guidance the plugin had already superseded.
		when(resolver.isSetupOpen()).thenReturn(false);
		guide.setSuggestion(buy(4151, "Abyssal whip"));
		beginEntry();
		guide.setSuggestion(collect("Nature rune"));

		guide.onSetupClosed();

		assertTrue(guide.getSuggestion().getType() == SuggestionType.COLLECT);
	}
}
