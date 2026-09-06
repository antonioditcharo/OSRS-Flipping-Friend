package com.flippingfriend.ui;

import static org.junit.Assert.assertEquals;

import com.flippingfriend.model.PositionStatus;
import com.flippingfriend.session.Position;
import java.time.Instant;
import org.junit.Test;

/**
 * Once an offer is on the market, the offer is the fact.
 *
 * <p>Every holding was valued at the plan's target and captioned "at target", including ones already
 * listed at a different price. The target is a forecast, and it only holds while the offer is still
 * priced the way the plan intended — which frequently it is not, because prices get adjusted, by the
 * player and by the plugin's own reprice advice alike.
 *
 * <p>A figure captioned "at target" beside an offer listed somewhere else is a number about a trade
 * nobody is making. While the item is still being bought there is nothing but the target to go on,
 * and there the caption is honest about being a projection.
 */
public class HoldingIsValuedAtWhatItIsListedForTest
{
	private static final int MAGUS = 28313;

	private static Position held(int target)
	{
		Position position = new Position(MAGUS, "Magus ring", 1, 22_658_211,
			Instant.now().getEpochSecond() - 600, true);
		position.setTargetSellPrice(target);
		return position;
	}

	@Test
	public void aListedHoldingIsValuedAtItsListedPrice()
	{
		// The reported case: listed at a price the plan did not choose, and still being valued at the
		// price the plan did choose.
		PositionsPanel.Basis basis = PositionsPanel.Basis.of(held(23_296_921),
			PositionStatus.selling(22_900_000, 0, 1), 22_700_000);

		assertEquals("the offer on the market is the fact", 22_900_000, basis.price);
		assertEquals("if it sells", basis.caption);
	}

	@Test
	public void aHoldingStillBeingBoughtIsValuedAtItsTarget()
	{
		// Nothing is listed yet, so the target is all there is -- and the caption says it is a
		// projection rather than money made.
		PositionsPanel.Basis basis = PositionsPanel.Basis.of(held(23_296_921), null, 22_700_000);

		assertEquals(23_296_921, basis.price);
		assertEquals("at target", basis.caption);
	}

	@Test
	public void aHoldingWaitingForASlotIsStillValuedAtItsTarget()
	{
		// Ready to sell but nothing placed. Not on the market, so not yet a fact.
		PositionsPanel.Basis basis = PositionsPanel.Basis.of(held(23_296_921),
			PositionStatus.stillBuying(500, 1_000), 22_700_000);

		assertEquals(23_296_921, basis.price);
		assertEquals("at target", basis.caption);
	}

	@Test
	public void aHoldingWithNoTargetFallsBackToTheMarket()
	{
		PositionsPanel.Basis basis = PositionsPanel.Basis.of(held(0), null, 22_700_000);

		assertEquals(22_700_000, basis.price);
		assertEquals("at market", basis.caption);
	}

	@Test
	public void aPartlyFilledListingStillValuesAtTheListedPrice()
	{
		// Half sold is still an offer on the market at a known price.
		PositionsPanel.Basis basis = PositionsPanel.Basis.of(held(23_296_921),
			PositionStatus.sellingWithRemainder(23_100_000, 5, 10, 3), 22_700_000);

		assertEquals(23_100_000, basis.price);
		assertEquals("if it sells", basis.caption);
	}
}
