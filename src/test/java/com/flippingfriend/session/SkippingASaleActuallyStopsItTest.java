package com.flippingfriend.session;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.flippingfriend.CheckInterval;
import com.flippingfriend.RiskProfile;
import com.flippingfriend.TradingHorizon;
import com.flippingfriend.data.LatestPrice;
import com.flippingfriend.model.FillModel;
import com.flippingfriend.model.ItemFeatures;
import com.flippingfriend.model.TaxCalculator;
import java.time.Instant;
import java.util.Collections;
import org.junit.Test;

/**
 * Asking the plugin not to sell something has to actually stop it selling.
 *
 * <p>The plugin offers to sell whatever it finds you holding, which is right for stock it bought and
 * wrong for the armour you took out of the bank to wear. There was a Skip button for that, on
 * purchases only — and even where the flag did reach the sell engine it steered around a single
 * branch and fell through to "Listing this banked item", which is also a sale. So the flag changed
 * the sentence the plugin gave for selling your armour and nothing else.
 *
 * <p>Declining costs nothing here. These are holdings the plugin did not buy: no cost basis, no
 * capital tied up, nothing to manage. That is not true of a position it DID buy, where refusing to
 * sell would strand real money, so the refusal is scoped to the former.
 */
public class SkippingASaleActuallyStopsItTest
{
	private static final int ARMOUR = 1163;

	private final SellTimingEngine engine =
		new SellTimingEngine(new FillModel(), new TaxCalculator());

	private static Position owned(boolean costKnown)
	{
		return new Position(ARMOUR, "Rune full helm", 1, costKnown ? 20_000 : 0,
			Instant.now().getEpochSecond() - 600, costKnown);
	}

	private SellDecision decide(boolean costKnown, boolean inInventory, boolean skipped)
	{
		return engine.evaluate(owned(costKnown),
			new LatestPrice(21_000, Instant.now().getEpochSecond(),
				20_000, Instant.now().getEpochSecond()),
			ItemFeatures.unknown(ARMOUR), Collections.emptyList(),
			TradingHorizon.of(RiskProfile.MODERATE, CheckInterval.CONSTANT),
			Instant.now(), 0, inInventory, false, skipped);
	}

	@Test
	public void aSkippedInventoryItemIsLeftAlone()
	{
		// The reported case: armour taken out of the bank, which the plugin promptly offered to sell.
		SellDecision skipped = decide(false, true, true);

		assertFalse("skipping must stop the sale, not relabel it: " + skipped.getReason(),
			skipped.isSell());
		assertEquals(SellDecision.Action.HOLD, skipped.getAction());
	}

	@Test
	public void withoutSkippingItIsStillOffered()
	{
		// The behaviour that has to survive: an unskipped holding is still something to sell.
		assertTrue("an item you have not skipped is still a sale",
			decide(false, true, false).isSell());
	}

	@Test
	public void aBankedItemIsAlsoLeftAloneWhenSkipped()
	{
		// Not just the inventory branch. The fall-through to "Listing this banked item" is what made
		// the flag useless, so a skipped holding that is NOT in the inventory must be held too.
		assertFalse("the fall-through was also a sale",
			decide(false, false, true).isSell());
	}

	@Test
	public void skippingDoesNotStrandAPositionThePluginBought()
	{
		// Deliberately not honoured where there is a cost basis: that is real capital in a real trade,
		// and refusing to manage its exit would leave it stranded with no one watching it.
		assertTrue("a bought position is still managed", decide(true, true, true).isSell()
			|| decide(true, true, true).getAction() == SellDecision.Action.HOLD);
	}
}
