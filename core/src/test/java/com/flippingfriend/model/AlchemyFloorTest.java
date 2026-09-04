package com.flippingfriend.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * The one price in this system the market does not set.
 *
 * <p>Everything else here is inferred from what traded. High Level Alchemy turns an item into a
 * fixed number of coins that Jagex decides and that does not move between updates, which is what
 * lets a downside estimate become a downside bound. The tests below are mostly about the three ways
 * a naive subtraction gets that bound wrong.
 */
public class AlchemyFloorTest
{
	/** Yew longbow: 768 high alch, and priced near enough its floor that the floor matters. */
	private static final int YEW_LONGBOW = 855;
	private static final int YEW_ALCH = 768;
	private static final int RUNE_PRICE = 100;

	private final TaxCalculator tax = new TaxCalculator();
	private final AlchemyFloor floor = new AlchemyFloor(tax, RUNE_PRICE);

	@Test
	public void theRuneIsPaidForOutOfTheProceeds()
	{
		assertEquals("768 in coins less the rune that made them", 668,
			floor.netAlchValue(YEW_ALCH));
	}

	@Test
	public void anItemThatCannotBeAlchedHasNoFloor()
	{
		// The wiki leaves the field off, and zero here means "no floor" rather than "unknown floor".
		// Treating an absent value as a floor of zero is right; treating it as unknown and guessing
		// would put a fictional bound under exactly the items that have none.
		assertEquals(0, floor.netAlchValue(0));
		assertEquals(0, floor.supportedPrice(YEW_LONGBOW, 0));
		assertFalse(floor.isBelowFloor(YEW_LONGBOW, 0, 1));
	}

	@Test
	public void anItemWorthLessThanItsRuneHasNoFloorEither()
	{
		// A 40 gp alch value against a 100 gp rune is a loss, not a floor.
		assertEquals(0, floor.netAlchValue(40));
	}

	@Test
	public void theFloorSupportsAHigherPriceThanItsOwnValue()
	{
		// The one that a subtraction gets wrong, and the reason this class exists rather than a
		// one-line helper. Alching is not a sale and pays no tax; a Grand Exchange sale pays 2%. So a
		// holder with a 668 gp alch in their inventory should refuse a bid of 668 -- after tax that
		// pays 655 -- and the price at which they are genuinely indifferent is higher.
		int supported = floor.supportedPrice(YEW_LONGBOW, YEW_ALCH);
		int net = floor.netAlchValue(YEW_ALCH);

		assertTrue("the supported price must exceed the alch value: " + supported + " vs " + net,
			supported > net);
		long proceeds = supported - tax.taxPerItem(YEW_LONGBOW, supported);
		assertTrue("and must be the first price whose net proceeds match the alch: "
			+ proceeds + " vs " + net, proceeds >= net);
		long justBelow = (supported - 1) - tax.taxPerItem(YEW_LONGBOW, supported - 1);
		assertTrue("one coin lower must not clear it: " + justBelow, justBelow < net);
	}

	@Test
	public void aBidUnderThatPriceIsUnderTheFloor()
	{
		int supported = floor.supportedPrice(YEW_LONGBOW, YEW_ALCH);

		assertTrue("a coin under is under", floor.isBelowFloor(YEW_LONGBOW, YEW_ALCH, supported - 1));
		assertFalse("and at it is not", floor.isBelowFloor(YEW_LONGBOW, YEW_ALCH, supported));
		assertFalse("nor well above it",
			floor.isBelowFloor(YEW_LONGBOW, YEW_ALCH, supported * 3));
	}

	@Test
	public void aDearerRuneLiftsTheCostAndLowersTheFloor()
	{
		AlchemyFloor dear = new AlchemyFloor(tax, 300);

		assertEquals(468, dear.netAlchValue(YEW_ALCH));
		assertTrue("a rune that costs more leaves a lower floor",
			dear.supportedPrice(YEW_LONGBOW, YEW_ALCH)
				< floor.supportedPrice(YEW_LONGBOW, YEW_ALCH));
	}

	@Test
	public void aMissingRunePriceFallsBackRatherThanDividingByNothing()
	{
		AlchemyFloor unknown = new AlchemyFloor(tax, 0);

		assertEquals(AlchemyFloor.DEFAULT_NATURE_RUNE_PRICE, unknown.natureRunePrice());
		assertTrue("a zero rune price must not make the floor infinite",
			unknown.netAlchValue(YEW_ALCH) < YEW_ALCH);
	}

	// --- throughput: the floor is a rate, not a guarantee ---

	@Test
	public void onlyAsManyAsThereIsTimeToCastAreProtected()
	{
		assertEquals("an hour of casting", AlchemyFloor.CASTS_PER_HOUR,
			floor.alchableWithin(100_000, 1.0));
		assertEquals("a small holding is protected entirely", 200,
			floor.alchableWithin(200, 1.0));
		assertEquals("and no time protects nothing", 0, floor.alchableWithin(200, 0));
	}

	@Test
	public void aHoldingLargerThanTheHorizonIsOnlyPartlyFloored()
	{
		// The failure this prevents: a bounded downside becoming a fictional one on precisely the
		// large positions where being wrong costs the most. Forty thousand yew longbows are floored
		// in principle and not inside any horizon a flip lives in.
		int quantity = 40_000;
		int bid = 300;
		long recovered = floor.unwindValue(YEW_LONGBOW, YEW_ALCH, bid, quantity, 2.0);

		long ifAllAlched = (long) floor.netAlchValue(YEW_ALCH) * quantity;
		long ifNoneAlched = (long) (bid - tax.taxPerItem(YEW_LONGBOW, bid)) * quantity;

		assertTrue("far short of alching the lot: " + recovered, recovered < ifAllAlched / 2);
		assertTrue("but better than dumping the lot: " + recovered, recovered > ifNoneAlched);
	}

	@Test
	public void theFurnaceIsIgnoredWhenTheMarketPaysBetter()
	{
		// The ordinary case, and the reason this is a floor rather than a plan. An item trading well
		// above its alch value is sold, not burned, and the floor contributes nothing.
		int bid = 5_000;
		long recovered = floor.unwindValue(YEW_LONGBOW, YEW_ALCH, bid, 500, 4.0);

		assertEquals((long) (bid - tax.taxPerItem(YEW_LONGBOW, bid)) * 500, recovered);
	}

	@Test
	public void theFloorIsWhatMakesTheWorstCaseBounded()
	{
		// The whole point, stated as the arithmetic a sizing decision actually does. The market can
		// fall as far as it likes and the recovery cannot follow it all the way down.
		int quantity = 500;
		long atCollapse = floor.unwindValue(YEW_LONGBOW, YEW_ALCH, 5, quantity, 4.0);
		long atRuin = floor.unwindValue(YEW_LONGBOW, YEW_ALCH, 1, quantity, 4.0);

		assertEquals("a price of five and a price of one recover the same, because neither is used",
			atCollapse, atRuin);
		assertEquals((long) floor.netAlchValue(YEW_ALCH) * quantity, atRuin);
	}

	@Test
	public void anUnalchableItemHasNothingUnderIt()
	{
		// The contrast that makes the floor worth carrying: the same collapse, on an item the game
		// will not buy back, recovers almost nothing.
		long recovered = floor.unwindValue(YEW_LONGBOW, 0, 1, 500, 4.0);

		assertTrue("no floor means the market's price is the only price: " + recovered,
			recovered <= 500);
	}
}
