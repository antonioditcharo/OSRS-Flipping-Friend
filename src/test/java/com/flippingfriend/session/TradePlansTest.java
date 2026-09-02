package com.flippingfriend.session;

import com.flippingfriend.data.PluginStorage;
import org.junit.Test;
import org.mockito.Mockito;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * A plan is only worth making if it reaches the position it was made for. Every field written here
 * is read somewhere that changes what the player sees or is told to do, so a field the plan forgets
 * to carry does not fail loudly — it just quietly stops being part of the trade.
 */
public class TradePlansTest
{
	private static final int ITEM = 4151;

	private static Position bought(int quantity, long totalCost)
	{
		return new Position(ITEM, "Abyssal whip", quantity, totalCost, 0L, true);
	}

	@Test
	public void thePlanCarriesTheStopOntoThePosition()
	{
		// The stop was decided when the trade was chosen, and nothing was carrying it across. Every
		// position therefore sat on the profile-wide fallback, and the price graph's stop line — which
		// reads this field directly — could never draw, because the field was always zero.
		TradePlans plans = new TradePlans(Mockito.mock(PluginStorage.class));
		plans.plan(ITEM, 1_100, 950, 30.0, 5_000L);

		Position position = bought(10, 10_000);
		plans.applyTo(position);

		assertEquals("target must survive the trip", 1_100, position.getTargetSellPrice());
		assertEquals("and so must the stop", 950, position.getStopPrice());
	}

	@Test
	public void anExistingPlanIsNotOverwritten()
	{
		// Advice is re-issued constantly. Re-applying it must not move a stop the player is already
		// trading against.
		TradePlans plans = new TradePlans(Mockito.mock(PluginStorage.class));
		plans.plan(ITEM, 1_100, 950, 30.0, 5_000L);

		Position position = bought(10, 10_000);
		plans.applyTo(position);

		plans.plan(ITEM, 1_300, 800, 30.0, 5_000L);
		plans.applyTo(position);

		assertEquals("the original target stands", 1_100, position.getTargetSellPrice());
		assertEquals("and so does the original stop", 950, position.getStopPrice());
	}

	@Test
	public void aPositionWithNoPlanIsLeftAlone()
	{
		TradePlans plans = new TradePlans(Mockito.mock(PluginStorage.class));
		Position position = bought(10, 10_000);

		plans.applyTo(position);

		assertEquals(0, position.getTargetSellPrice());
		assertTrue("with no plan there is no stop to impose", position.getStopPrice() <= 0);
	}
}
