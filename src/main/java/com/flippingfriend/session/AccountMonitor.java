package com.flippingfriend.session;

import com.flippingfriend.AccountMode;
import com.flippingfriend.FlippingFriendConfig;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.GrandExchangeOffer;
import net.runelite.api.GrandExchangeOfferState;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;
import net.runelite.api.WorldType;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.game.ItemManager;

/**
 * Works out what the account has to trade with, without asking the user a single question.
 * <p>
 * Everything here runs on the client thread and publishes an immutable {@link AccountState}, which
 * is the only thing the rest of the plugin reads. That keeps game access in one place and means the
 * scoring code can never accidentally touch the client off-thread.
 */
@Singleton
public class AccountMonitor
{
	/** Collection box containers, one per Grand Exchange slot. */
	private static final int[] COLLECTION_CONTAINERS = {
		InventoryID.GE_OFFER_0, InventoryID.GE_OFFER_1, InventoryID.GE_OFFER_2, InventoryID.GE_OFFER_3,
		InventoryID.GE_OFFER_4, InventoryID.GE_OFFER_5, InventoryID.GE_OFFER_6, InventoryID.GE_OFFER_7
	};

	private final Client client;
	private final ItemManager itemManager;
	private final FlippingFriendConfig config;

	private final AtomicReference<AccountState> state = new AtomicReference<>(AccountState.loggedOut());

	/**
	 * The bank container only exists while the bank is open, so the last seen contents are remembered
	 * for the session.
	 * <p>
	 * Remembered as <b>evidence of presence, never of absence</b>. The game stops syncing this
	 * container the moment the interface closes, so what is held here is a photograph: it can prove an
	 * item is owned, and it can never prove one is not. Reading it the other way is what deleted real
	 * positions -- collect an item to the bank without reopening it and the plugin concluded the item
	 * had been sold. Nothing destructive is decided from this map any more; see
	 * {@link PositionBook#reconcile}.
	 */
	private long lastSeenBankCoins;
	private Map<Integer, Integer> lastSeenBankItems = new HashMap<>();
	/** The inventory at the moment the bank was last readable, as the baseline for withdrawals. */
	private Map<Integer, Integer> inventoryWhenBankSeen = new HashMap<>();

	/**
	 * The remembered bank, less whatever has been withdrawn from it since.
	 *
	 * <p>The bank container is only legible while the bank is open, so between visits this class works
	 * from a snapshot. That snapshot was merged into holdings unconditionally, and a withdrawal put
	 * the item in TWO places at once: in the inventory, where it now is, and in the remembered bank,
	 * where it no longer is. Take one piece of armour out to sell it and the plugin believed there
	 * were two -- and went on believing it until the bank was opened again, because that is the only
	 * moment the snapshot is refreshed.
	 *
	 * <p>What gives it away is the inventory. Anything above what the inventory held when the bank was
	 * last read has arrived since, and for an item that was sitting in the bank the overwhelmingly
	 * likely route is that it came out of it. So the remembered bank is reduced by that difference,
	 * floored at zero.
	 *
	 * <p>The other route in is a Grand Exchange collection, and for an item that is ALSO in the bank
	 * this will discount it once too often and understate the total. That is the direction to be wrong
	 * in: a holding counted short is an opportunity missed, while one counted long is an instruction
	 * to sell something that is not there. It corrects itself the moment the bank is opened.
	 */
	private Map<Integer, Integer> stillInTheBank(Map<Integer, Integer> inventoryNow)
	{
		Map<Integer, Integer> remaining = new HashMap<>();
		for (Map.Entry<Integer, Integer> banked : lastSeenBankItems.entrySet())
		{
			int id = banked.getKey();
			int since = inventoryNow.getOrDefault(id, 0) - inventoryWhenBankSeen.getOrDefault(id, 0);
			int left = banked.getValue() - Math.max(0, since);
			if (left > 0)
			{
				remaining.put(id, left);
			}
		}
		return remaining;
	}
	private boolean bankSeen;

	@Inject
	public AccountMonitor(Client client, ItemManager itemManager, FlippingFriendConfig config)
	{
		this.client = client;
		this.itemManager = itemManager;
		this.config = config;
	}

	public AccountState getState()
	{
		return state.get();
	}

	public void reset()
	{
		lastSeenBankCoins = 0;
		lastSeenBankItems = new HashMap<>();
		inventoryWhenBankSeen = new HashMap<>();
		bankSeen = false;
		state.set(AccountState.loggedOut());
	}

	/** Must be called on the client thread. */
	public AccountState refresh()
	{
		if (client.getGameState() != GameState.LOGGED_IN)
		{
			AccountState loggedOut = AccountState.loggedOut();
			state.set(loggedOut);
			return loggedOut;
		}

		// The world tells us what is available, but the user can override it — someone without
		// membership may still be sat on a members world briefly, and someone planning around a
		// lapsing subscription may want free-to-play suggestions before it actually runs out.
		boolean membersWorld = client.getWorldType().contains(WorldType.MEMBERS);
		boolean members = config.accountMode().resolveMembers(membersWorld);
		boolean ironman = client.getVarbitValue(VarbitID.IRONMAN) != 0;

		Map<Integer, Integer> holdings = new HashMap<>();
		Map<Integer, Integer> inventoryHoldings = new HashMap<>();

		long inventoryCoins = readContainer(client.getItemContainer(InventoryID.INV), inventoryHoldings);

		// What you are wearing is still what you own.
		//
		// The equipment container was never read at all, so equipping something you had just bought
		// put it in no container this method looks at -- and a Mystic smoke staff, which is a weapon,
		// went missing exactly that way. Coins cannot be equipped, so the return value is discarded.
		readContainer(client.getItemContainer(InventoryID.WORN), inventoryHoldings);
		java.util.Set<String> blockedNames = com.flippingfriend.model.SuggestionEngine.parseBlocked(config.blockedItems());
		
		inventoryHoldings.keySet().removeIf(id -> {
			ItemComposition comp = itemManager.getItemComposition(id);
			return comp != null && comp.getName() != null && blockedNames.contains(comp.getName().toLowerCase(java.util.Locale.ROOT));
		});
		
		merge(holdings, inventoryHoldings);

		ItemContainer bank = client.getItemContainer(InventoryID.BANK);
		if (bank != null)
		{
			Map<Integer, Integer> bankItems = new HashMap<>();
			lastSeenBankCoins = readContainer(bank, bankItems);
			
			bankItems.keySet().removeIf(id -> {
				ItemComposition comp = itemManager.getItemComposition(id);
				return comp != null && comp.getName() != null && blockedNames.contains(comp.getName().toLowerCase(java.util.Locale.ROOT));
			});
			
			lastSeenBankItems = bankItems;
			// The inventory as it stood when the bank was last legible, so a withdrawal can be told
			// apart from something that was always in the inventory. See stillInTheBank().
			inventoryWhenBankSeen = new HashMap<>(inventoryHoldings);
			bankSeen = true;
		}
		merge(holdings, stillInTheBank(inventoryHoldings));

		long collectionCoins = 0;
		for (int containerId : COLLECTION_CONTAINERS)
		{
			collectionCoins += readContainer(client.getItemContainer(containerId), holdings);
		}

		int totalSlots = AccountMode.slotsFor(members);
		int freeSlots = countFreeSlots(totalSlots);
		long committed = committedToBuyOffers();

		AccountState updated = new AccountState(true, members, ironman, inventoryCoins, lastSeenBankCoins,
			collectionCoins, freeSlots, totalSlots, committed, bankSeen, holdings, inventoryHoldings,
			stillInTheBank(inventoryHoldings));
		state.set(updated);
		return updated;
	}

	/**
	 * Adds a container's tradeable contents to {@code holdings} and returns the coins it held.
	 * Coins are pulled out rather than treated as a holding because they are the thing we spend,
	 * not something we would ever be told to sell.
	 */
	private long readContainer(ItemContainer container, Map<Integer, Integer> holdings)
	{
		if (container == null)
		{
			return 0;
		}

		long coins = 0;
		for (Item item : container.getItems())
		{
			int id = item.getId();
			int quantity = item.getQuantity();
			if (id <= 0 || quantity <= 0)
			{
				continue;
			}

			if (id == ItemID.COINS)
			{
				coins += quantity;
				continue;
			}

			// Noted items trade as their unnoted form, so fold them together.
			int canonical = itemManager.canonicalize(id);
			ItemComposition composition = itemManager.getItemComposition(canonical);
			if (composition == null || !composition.isGeTradeable())
			{
				continue;
			}

			holdings.merge(canonical, quantity, Integer::sum);
		}
		return coins;
	}

	/**
	 * Coins currently locked up in open buy offers.
	 * <p>
	 * The exchange takes the full asking price when an offer is placed and refunds the difference as
	 * it fills, so the outstanding commitment is the price times whatever has not yet been bought.
	 */
	private long committedToBuyOffers()
	{
		GrandExchangeOffer[] offers = client.getGrandExchangeOffers();
		if (offers == null)
		{
			return 0;
		}

		long committed = 0;
		for (GrandExchangeOffer offer : offers)
		{
			if (offer == null)
			{
				continue;
			}
			GrandExchangeOfferState state = offer.getState();
			if (state != GrandExchangeOfferState.BUYING)
			{
				continue;
			}
			int outstanding = Math.max(0, offer.getTotalQuantity() - offer.getQuantitySold());
			committed += (long) offer.getPrice() * outstanding;
		}
		return committed;
	}

	private int countFreeSlots(int totalSlots)
	{
		GrandExchangeOffer[] offers = client.getGrandExchangeOffers();
		if (offers == null)
		{
			return totalSlots;
		}

		int used = 0;
		int checked = Math.min(totalSlots, offers.length);
		for (int i = 0; i < checked; i++)
		{
			GrandExchangeOffer offer = offers[i];
			if (offer != null && offer.getState() != GrandExchangeOfferState.EMPTY)
			{
				used++;
			}
		}
		return Math.max(0, totalSlots - used);
	}

	private static void merge(Map<Integer, Integer> target, Map<Integer, Integer> source)
	{
		for (Map.Entry<Integer, Integer> entry : source.entrySet())
		{
			target.merge(entry.getKey(), entry.getValue(), Integer::sum);
		}
	}
}
