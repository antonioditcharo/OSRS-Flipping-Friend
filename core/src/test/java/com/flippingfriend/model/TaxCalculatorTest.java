package com.flippingfriend.model;

import com.flippingfriend.data.ItemMetadata;
import java.util.Arrays;
import java.util.List;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The tax rules are simple to state and easy to get subtly wrong, and a 2% error is larger than the
 * entire margin on most high-volume flips. These cases pin down every boundary the game actually
 * has.
 */
public class TaxCalculatorTest
{
	private static final int ITEM = 4151;
	private static final int BOND = 13190;

	private TaxCalculator taxCalculator;

	@Before
	public void setUp()
	{
		taxCalculator = new TaxCalculator();

		List<ItemMetadata> items = Arrays.asList(
			new ItemMetadata(ITEM, "Abyssal whip", true, 70, 120001),
			new ItemMetadata(BOND, "Old school bond", false, 100, 0),
			new ItemMetadata(1755, "Chisel", false, 40, 14),
			new ItemMetadata(379, "Lobster", false, 12000, 150),
			new ItemMetadata(3024, "Energy potion(4)", true, 2000, 200),
			new ItemMetadata(2, "Cannonball", true, 11000, 5));

		taxCalculator.resolveExemptions(items);
	}

	@Test
	public void chargesTwoPercentRoundedDown()
	{
		// 2% of 1000 is exactly 20.
		assertEquals(20, taxCalculator.taxPerItem(ITEM, 1000));
		// 2% of 1049 is 20.98, which floors to 20 rather than rounding to 21.
		assertEquals(20, taxCalculator.taxPerItem(ITEM, 1049));
		assertEquals(21, taxCalculator.taxPerItem(ITEM, 1050));
	}

	@Test
	public void chargesNothingBelowFiftyCoins()
	{
		for (int price = 1; price < 50; price++)
		{
			assertEquals("no tax below 50gp, failed at " + price, 0, taxCalculator.taxPerItem(ITEM, price));
		}
		assertEquals(1, taxCalculator.taxPerItem(ITEM, 50));
	}

	@Test
	public void capsAtFiveMillionPerItem()
	{
		// The cap binds from 250m upwards.
		assertEquals(4_999_999, taxCalculator.taxPerItem(ITEM, 249_999_999));
		assertEquals(TaxCalculator.MAX_TAX_PER_ITEM, taxCalculator.taxPerItem(ITEM, 250_000_000));
		assertEquals(TaxCalculator.MAX_TAX_PER_ITEM, taxCalculator.taxPerItem(ITEM, 2_000_000_000));
	}

	@Test
	public void exemptsBondsToolsAndLowLevelFood()
	{
		assertTrue(taxCalculator.isExempt(BOND));
		assertTrue(taxCalculator.isExempt(1755));
		assertTrue(taxCalculator.isExempt(379));
		assertFalse(taxCalculator.isExempt(ITEM));
		assertFalse(taxCalculator.isExempt(2));

		assertEquals(0, taxCalculator.taxPerItem(BOND, 10_000_000));
	}

	@Test
	public void exemptsDosedPotionsRegardlessOfDose()
	{
		assertTrue(TaxCalculator.isExemptName("Energy potion(4)"));
		assertTrue(TaxCalculator.isExemptName("Energy potion(1)"));
		assertTrue(TaxCalculator.isExemptName("energy potion"));
		assertFalse(TaxCalculator.isExemptName("Super energy potion(4)"));
	}

	@Test
	public void multipliesTaxByQuantity()
	{
		assertEquals(2000L, taxCalculator.taxFor(ITEM, 1000, 100));
		assertEquals(98_000L, taxCalculator.netProceeds(ITEM, 1000, 100));
	}

	@Test
	public void netMarginAccountsForTheTaxOnTheSaleSide()
	{
		// Buy at 1000, sell at 1010: gross margin 10, tax 20, so this actually loses money.
		assertEquals(-10L, taxCalculator.netMarginPerItem(ITEM, 1000, 1010));
		// Buy at 1000, sell at 1050: gross 50, tax 21, net 29.
		assertEquals(29L, taxCalculator.netMarginPerItem(ITEM, 1000, 1050));
	}

	@Test
	public void breakEvenPriceCoversTheTax()
	{
		int breakEven = taxCalculator.breakEvenSellPrice(ITEM, 1000);

		assertTrue("break-even must not lose money",
			taxCalculator.netMarginPerItem(ITEM, 1000, breakEven) >= 0);
		assertTrue("break-even must be the lowest such price",
			taxCalculator.netMarginPerItem(ITEM, 1000, breakEven - 1) < 0);
	}

	@Test
	public void breakEvenOnExemptItemsIsTheBuyPrice()
	{
		assertEquals(5000, taxCalculator.breakEvenSellPrice(BOND, 5000));
	}

	@Test
	public void breakEvenHoldsAcrossAWideRangeOfPrices()
	{
		for (int buyPrice : new int[]{50, 137, 1000, 9999, 250_000, 1_000_000, 40_000_000, 300_000_000})
		{
			int breakEven = taxCalculator.breakEvenSellPrice(ITEM, buyPrice);
			assertTrue("must not lose money at " + buyPrice,
				taxCalculator.netMarginPerItem(ITEM, buyPrice, breakEven) >= 0);
			assertTrue("must be minimal at " + buyPrice,
				taxCalculator.netMarginPerItem(ITEM, buyPrice, breakEven - 1) < 0);
		}
	}
}
