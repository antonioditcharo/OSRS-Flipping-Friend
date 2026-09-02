package com.flippingfriend.session;

import java.util.Collections;
import java.util.Map;

/**
 * What the plugin can see about the logged-in account: how much it has to spend, what it already
 * owns, and how many Grand Exchange slots are free.
 * <p>
 * Immutable, and rebuilt on the client thread whenever something relevant changes, so the scoring
 * threads can read a consistent picture without touching the game.
 */
public class AccountState
{
	private static final AccountState LOGGED_OUT = new AccountState(
		false, false, false, 0, 0, 0, 0, 0, 0, false, Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap());

	private final boolean loggedIn;
	private final boolean membersWorld;
	private final boolean ironman;
	private final long inventoryCoins;
	private final long bankCoins;
	private final long collectionBoxCoins;
	private final int freeSlots;
	private final int totalSlots;
	private final long committedCoins;
	private final boolean bankSeen;
	private final Map<Integer, Integer> holdings;
	private final Map<Integer, Integer> inventoryHoldings;
	private final Map<Integer, Integer> bankHoldings;

	public AccountState(boolean loggedIn, boolean membersWorld, boolean ironman, long inventoryCoins,
		long bankCoins, long collectionBoxCoins, int freeSlots, int totalSlots, long committedCoins,
		boolean bankSeen, Map<Integer, Integer> holdings, Map<Integer, Integer> inventoryHoldings, Map<Integer, Integer> bankHoldings)
	{
		this.committedCoins = committedCoins;
		this.bankSeen = bankSeen;
		this.loggedIn = loggedIn;
		this.membersWorld = membersWorld;
		this.ironman = ironman;
		this.inventoryCoins = inventoryCoins;
		this.bankCoins = bankCoins;
		this.collectionBoxCoins = collectionBoxCoins;
		this.freeSlots = freeSlots;
		this.totalSlots = totalSlots;
		this.holdings = Collections.unmodifiableMap(holdings);
		this.inventoryHoldings = Collections.unmodifiableMap(inventoryHoldings);
		this.bankHoldings = Collections.unmodifiableMap(bankHoldings);
	}

	public static AccountState loggedOut()
	{
		return LOGGED_OUT;
	}

	public boolean isLoggedIn()
	{
		return loggedIn;
	}

	/**
	 * Whether members' items may be <em>bought</em>. Selling is never restricted, so this must not
	 * be used to decide what can be sold — a lapsed member holding members' gear can still sell all
	 * of it, and the plugin should say so.
	 */
	public boolean canBuyMembersItems()
	{
		return membersWorld;
	}

	/** True when the account is being treated as free-to-play, whether detected or forced. */
	public boolean isFreeToPlay()
	{
		return !membersWorld;
	}

	/**
	 * Ironman accounts can only buy bonds on the Grand Exchange, so there is nothing for this
	 * plugin to suggest and it says so rather than showing an empty panel.
	 */
	public boolean isIronman()
	{
		return ironman;
	}

	public long getInventoryCoins()
	{
		return inventoryCoins;
	}

	/** Only meaningful once the bank has been opened at least once this session. */
	public long getBankCoins()
	{
		return bankCoins;
	}

	/** Coins sitting uncollected in the Grand Exchange collection boxes. */
	public long getCollectionBoxCoins()
	{
		return collectionBoxCoins;
	}

	public int getFreeSlots()
	{
		return freeSlots;
	}

	public int getTotalSlots()
	{
		return totalSlots;
	}

	/** Tradeable items held in the inventory, bank and collection boxes, by item id. */
	public Map<Integer, Integer> getHoldings()
	{
		return holdings;
	}

	public Map<Integer, Integer> getInventoryHoldings()
	{
		return inventoryHoldings;
	}

	public Map<Integer, Integer> getBankHoldings()
	{
		return bankHoldings;
	}

	public int quantityHeld(int itemId)
	{
		return holdings.getOrDefault(itemId, 0);
	}

	/**
	 * Whether the bank has been opened at least once this session.
	 * <p>
	 * Until it has, {@link #getHoldings()} is only the inventory and collection boxes, and an item
	 * sitting in the bank is indistinguishable from one that has been sold. Anything that deletes
	 * records on the strength of "not held" has to wait for this.
	 */
	public boolean isBankSeen()
	{
		return bankSeen;
	}

	/** Coins tied up in buy offers that are currently open. */
	public long getCommittedCoins()
	{
		return committedCoins;
	}

	/**
	 * Coins the plugin is willing to plan around.
	 * <p>
	 * Bank coins count because the Grand Exchange has drawn buy offers from the bank since 2023, so
	 * excluding them would understate what the account can trade with. But that same change is why
	 * open offers have to be deducted from the bank figure.
	 * <p>
	 * Coins in an open buy offer have already left the <em>inventory</em>, so the inventory number is
	 * self-correcting. The bank number is not: it is a snapshot from the last time the bank happened
	 * to be open, and an offer placed since then has quietly drawn from it. Counting that money twice
	 * is how an account ends up with eight offers it cannot actually fund. Deducting the full value of
	 * open offers slightly understates the bank when the offers predate the snapshot, and erring
	 * towards under-committing is the right direction to be wrong in.
	 *
	 * @param includeBank  user setting, in case they want the bank left alone
	 * @param reservedCoins  user spending cap, or 0 for no cap
	 */
	public long spendableCoins(boolean includeBank, long cap)
	{
		long bank = includeBank ? Math.max(0, bankCoins - committedCoins) : 0;
		long total = inventoryCoins + collectionBoxCoins + bank;
		if (cap > 0)
		{
			long remainingCap = Math.max(0, cap - committedCoins);
			total = Math.min(total, remainingCap);
		}
		return Math.max(0, total);
	}

	public boolean canTrade()
	{
		return loggedIn && !ironman;
	}
}
