package com.flippingfriend.session;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Checks what the plugin believes it has to spend, and that free-to-play is reported in a way that
 * cannot accidentally be used to block selling.
 */
public class AccountStateTest
{
	private static AccountState state(boolean members, long inventory, long bank, long collection)
	{
		return new AccountState(true, members, false, inventory, bank, collection, 3, 8, 0, true, new HashMap<>(), new HashMap<>(), new HashMap<>());
	}

	@Test
	public void addsUpCoinsFromEverywhereItCanSee()
	{
		AccountState account = state(true, 1_000_000, 5_000_000, 250_000);
		assertEquals(6_250_000L, account.spendableCoins(true, 0));
	}

	@Test
	public void canLeaveTheBankOutOfIt()
	{
		AccountState account = state(true, 1_000_000, 5_000_000, 250_000);
		assertEquals(1_250_000L, account.spendableCoins(false, 0));
	}

	@Test
	public void respectsASpendingCap()
	{
		AccountState account = state(true, 1_000_000, 5_000_000, 0);
		assertEquals(2_000_000L, account.spendableCoins(true, 2_000_000));
		// A cap above what is actually held must not invent coins.
		assertEquals(6_000_000L, account.spendableCoins(true, 50_000_000));
	}

	@Test
	public void freeToPlayBlocksBuyingMembersItemsOnly()
	{
		AccountState free = state(false, 100_000, 0, 0);

		assertFalse("free accounts cannot buy members' items", free.canBuyMembersItems());
		assertTrue(free.isFreeToPlay());
		// Nothing on the state object restricts selling, which is the whole point: a lapsed member
		// holding members' gear must still be told to sell it.
		assertTrue(free.canTrade());
	}

	@Test
	public void openBuyOffersAreDeductedFromTheStaleBankFigure()
	{
		// The bank figure is a snapshot from whenever the bank was last open, and since 2023 buy
		// offers draw from it. Counting committed coins again is how an account ends up with more
		// offers than it can fund.
		AccountState account = new AccountState(true, true, false, 1_000_000, 50_000_000, 0, 4, 8,
			20_000_000, true, new HashMap<>(), new HashMap<>(), new HashMap<>());

		assertEquals(31_000_000L, account.spendableCoins(true, 0));
		assertEquals(20_000_000L, account.getCommittedCoins());
	}

	@Test
	public void commitmentsCannotDriveTheBankNegative()
	{
		AccountState account = new AccountState(true, true, false, 500_000, 1_000_000, 0, 0, 8,
			9_000_000, true, new HashMap<>(), new HashMap<>(), new HashMap<>());

		assertEquals("the inventory is still spendable", 500_000L, account.spendableCoins(true, 0));
	}

	@Test
	public void loggedOutHasNothingToTradeWith()
	{
		AccountState account = AccountState.loggedOut();
		assertFalse(account.isLoggedIn());
		assertFalse(account.canTrade());
		assertEquals(0L, account.spendableCoins(true, 0));
		assertEquals(Collections.<Integer, Integer>emptyMap(), account.getHoldings());
	}

	@Test
	public void reportsHeldQuantities()
	{
		Map<Integer, Integer> holdings = new HashMap<>();
		holdings.put(4151, 3);
		AccountState account = new AccountState(true, true, false, 0, 0, 0, 8, 8, 0, true, holdings, new HashMap<>(), new HashMap<>());

		assertEquals(3, account.quantityHeld(4151));
		assertEquals(0, account.quantityHeld(995));
	}
}
