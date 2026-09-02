package com.flippingfriend.model;

import com.flippingfriend.session.Position;
import java.time.Instant;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Repricing a sell offer that is not filling.
 * <p>
 * This step had no floor but 1 gp, and because it ranks above the sell engine in the decision chain it
 * walked past the stop-loss logic entirely. Every losing flip in the journal came from here and
 * nowhere else -- three of them, together -40,540 against 488,450 of gross profit.
 */
public class RepriceFloorTest
{
	private static final int MAPLE_LOGS = 1517;
	private static final int SOFT_CLAY = 1761;

	private final TaxCalculator tax = new TaxCalculator();

	private static Position bought(int itemId, int quantity, int unitCost)
	{
		return new Position(itemId, "item", quantity, (long) quantity * unitCost,
			Instant.now().getEpochSecond(), true);
	}

	@Test
	public void aRepriceIsNeverTakenBelowWhatThePositionCost()
	{
		// The real case: 15,000 Maple logs bought at 11. The market bid fell to 11, this step offered
		// 10, and the sale went through at a loss of exactly one gp a unit.
		Position maple = bought(MAPLE_LOGS, 15_000, 11);
		int breakEven = tax.breakEvenSellPrice(MAPLE_LOGS, 11);

		assertTrue("selling a log for less than it cost is not a repricing decision",
			!SuggestionEngine.repriceAllowed(10, maple, breakEven));
		assertEquals("and the loss it would have realised", -15_000L,
			tax.netProfit(MAPLE_LOGS, 11, 10, 15_000));

		// Soft clay, twice: bought at 119, repriced to 116. At a 12% loss cut the stop was 104, so the
		// sell engine would have held -- this step sold it twelve gp above the price it would have cut
		// at, without ever asking.
		Position clay = bought(SOFT_CLAY, 2_664, 119);
		assertTrue("nor is giving up three gp a unit on clay",
			!SuggestionEngine.repriceAllowed(116, clay, tax.breakEvenSellPrice(SOFT_CLAY, 119)));
	}

	@Test
	public void arepriceThatStillClearsCostIsAllowed()
	{
		// The point of the step survives: an offer priced above the market should still come down, as
		// long as coming down does not mean selling at a loss.
		Position clay = bought(SOFT_CLAY, 2_664, 119);
		int breakEven = tax.breakEvenSellPrice(SOFT_CLAY, 119);

		assertTrue("break-even itself is not a loss", SuggestionEngine.repriceAllowed(breakEven, clay,
			breakEven));
		assertTrue("and anything above it is a real improvement",
			SuggestionEngine.repriceAllowed(breakEven + 5, clay, breakEven));
	}

	@Test
	public void anItemWithNoCostBasisRepricesFreely()
	{
		// Something that was already in the bank cost nothing to acquire, so there is no loss to
		// protect against and the old behaviour is the right one.
		Position adopted = Position.preExisting(SOFT_CLAY, "Soft clay", 500,
			Instant.now().getEpochSecond());

		assertTrue("an adopted holding has no cost to fall below",
			SuggestionEngine.repriceAllowed(1, adopted, 0));
		assertTrue("and neither does a position the book has no record of",
			SuggestionEngine.repriceAllowed(1, null, 0));
	}
}
