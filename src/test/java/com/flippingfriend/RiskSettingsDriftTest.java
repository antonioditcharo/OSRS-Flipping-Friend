package com.flippingfriend;

import com.flippingfriend.model.RiskAppetite;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * The plugin and the companion each describe the player's risk setting in their own type, and only the
 * <em>name</em> crosses the wire between them. That means every tuned number exists twice, and the two
 * copies have drifted before — the companion charged a flat 5% stop at every setting while the plugin
 * cut at 2%, 5% and 12%, so the session loss budget was wrong at two settings out of three.
 * <p>
 * These are drift tests. They do not check that a value is correct; they check that the two halves of
 * the system still agree about it, which is the failure that keeps recurring and which no amount of
 * reading catches.
 */
public class RiskSettingsDriftTest
{
	private static RiskAppetite appetiteFor(RiskProfile profile)
	{
		return RiskAppetite.forName(profile.name());
	}

	@Test
	public void everyRiskProfileMapsToADistinctAppetite()
	{
		assertEquals("Cautious", appetiteFor(RiskProfile.LOW).getName());
		assertEquals("Balanced", appetiteFor(RiskProfile.MODERATE).getName());
		assertEquals("Aggressive", appetiteFor(RiskProfile.HIGH).getName());
	}

	@Test
	public void theLossCutMatchesOnBothSides()
	{
		// What the plugin cuts a position at, and what the companion budgets the session loss against,
		// have to be the same number or the drawdown breaker arms at the wrong depth.
		for (RiskProfile profile : RiskProfile.values())
		{
			assertEquals(profile.name() + ": the companion must budget the loss the plugin will take",
				profile.getLossCutPct(), appetiteFor(profile).getLossCutPct(), 1e-9);
		}
	}
}
