package com.flippingfriend.companion;

import com.flippingfriend.core.AccountSnapshot;
import com.flippingfriend.model.RiskAppetite;
import com.google.gson.Gson;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Whether "how long a flip should take" reaches the thing that chooses the trade.
 * <p>
 * The plugin has two timing settings and they answer different questions, which the setting text
 * says in as many words: "This is separate from how often you check — you can want slow flips while
 * standing at the Exchange." {@code TradingHorizon} on the plugin side has honoured both since they
 * were separated. Only the checking habit was ever put on the wire, and the companion is the path
 * that actually picks which trade to recommend — so a player asking for twenty-minute flips was
 * planned for at the risk appetite's two and a half hours a leg and handed orders sized for it.
 */
public class PlanningHorizonTest
{
	private final Gson gson = new Gson();

	private static AccountSnapshot account(int checkIntervalMinutes, int targetHoldMinutes)
	{
		return new AccountSnapshot("c", 0, 100_000_000L, 0, 8, 8, true, true, 0,
			"MODERATE", null, 0, null, false, checkIntervalMinutes, null, null, null, false,
			targetHoldMinutes);
	}

	@Test
	public void anUnstatedHoldLeavesTheRiskAppetiteInCharge()
	{
		assertEquals(RiskAppetite.BALANCED.getHorizonHours(),
			PortfolioPlanner.horizonFor(account(15, 0), RiskAppetite.BALANCED), 1e-9);
	}

	@Test
	public void askingForFastFlipsShortensTheLeg()
	{
		// Twenty minutes start to finish is ten minutes a leg. The checking habit is a floor, so a
		// player at the Exchange every five minutes gets the ten, not the appetite's two and a half
		// hours.
		double horizon = PortfolioPlanner.horizonFor(account(5, 20), RiskAppetite.BALANCED);

		assertEquals(10 / 60.0, horizon, 1e-9);
		assertTrue("and it is far shorter than the appetite would have chosen alone",
			horizon < RiskAppetite.BALANCED.getHorizonHours());
	}

	@Test
	public void askingForSlowFlipsLengthensIt()
	{
		double horizon = PortfolioPlanner.horizonFor(account(15, 600), RiskAppetite.BALANCED);

		assertEquals(300 / 60.0, horizon, 1e-9);
		assertTrue(horizon > RiskAppetite.BALANCED.getHorizonHours());
	}

	@Test
	public void theCheckingHabitIsAFloorAndNeverACeiling()
	{
		// Someone who only returns every three hours cannot be given a ten-minute leg, however fast
		// a flip they say they want: the order would sit completed, holding its slot, until they
		// came back. Being present, though, never shortens anything.
		double patient = PortfolioPlanner.horizonFor(account(180, 20), RiskAppetite.BALANCED);
		assertEquals(180 * 0.8 / 60.0, patient, 1e-9);

		double attentive = PortfolioPlanner.horizonFor(account(5, 600), RiskAppetite.BALANCED);
		assertEquals("standing at the Exchange does not make the market fill faster",
			300 / 60.0, attentive, 1e-9);
	}

	@Test
	public void theSettingSurvivesTheWire()
	{
		// Gson writes the fields directly, so what matters is the name on the wire and what a
		// snapshot from an older plugin does when the field is absent.
		String json = gson.toJson(account(15, 45));
		assertTrue("the field has to actually be serialised: " + json,
			json.contains("targetHoldMinutes"));

		assertEquals(45, gson.fromJson(json, AccountSnapshot.class).getTargetHoldMinutes());
		assertEquals("an older plugin that never sends it must mean 'unstated', not 'instant'",
			0, gson.fromJson("{\"correlationId\":\"c\"}", AccountSnapshot.class)
				.getTargetHoldMinutes());
	}
}
