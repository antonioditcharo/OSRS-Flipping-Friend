package com.flippingfriend.session;

import static org.junit.Assert.assertEquals;

import com.flippingfriend.AccountMode;
import com.flippingfriend.FlippingFriendConfig;
import java.util.EnumSet;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;
import net.runelite.api.gameval.InventoryID;
import net.runelite.client.game.ItemManager;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

/**
 * An item is in one place at a time.
 *
 * <p>The bank container is only legible while the bank is open, so between visits the plugin works
 * from a snapshot — and that snapshot was added to the holdings unconditionally. Withdrawing put the
 * item in two places at once: the inventory, where it now was, and the remembered bank, where it no
 * longer was. Take one piece of armour out to sell it and the plugin believed there were two, then
 * went on believing it until the bank was next opened, because that is the only moment the snapshot
 * is refreshed.
 *
 * <p>An inflated holding is not a cosmetic error. It is an instruction to sell stock that is not
 * there, which cannot be carried out and wastes the slot it was placed in.
 */
public class WithdrawnFromTheBankIsCountedOnceTest
{
	private static final int ARMOUR = 1163;
	private static final int RUNE = 561;

	private final Client client = Mockito.mock(Client.class);
	private final ItemManager itemManager = Mockito.mock(ItemManager.class);
	private final FlippingFriendConfig config = Mockito.mock(FlippingFriendConfig.class);

	private AccountMonitor monitor;

	@Before
	public void setUp()
	{
		Mockito.when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
		Mockito.when(client.getWorldType()).thenReturn(EnumSet.noneOf(net.runelite.api.WorldType.class));
		Mockito.when(client.getGrandExchangeOffers()).thenReturn(null);
		Mockito.when(config.accountMode()).thenReturn(AccountMode.AUTOMATIC);

		ItemComposition composition = Mockito.mock(ItemComposition.class);
		Mockito.when(composition.isGeTradeable()).thenReturn(true);
		Mockito.when(itemManager.getItemComposition(Mockito.anyInt())).thenReturn(composition);
		Mockito.when(itemManager.canonicalize(Mockito.anyInt()))
			.thenAnswer(call -> call.getArgument(0));

		monitor = new AccountMonitor(client, itemManager, config);
	}

	private void put(int containerId, Item... items)
	{
		ItemContainer container = Mockito.mock(ItemContainer.class);
		Mockito.when(container.getItems()).thenReturn(items);
		Mockito.when(client.getItemContainer(containerId)).thenReturn(container);
	}

	private void closeBank()
	{
		// What the client does once the interface is gone: the snapshot is all there is.
		Mockito.when(client.getItemContainer(InventoryID.BANK)).thenReturn(null);
	}

	@Test
	public void takingOneOutOfTheBankDoesNotMakeItTwo()
	{
		put(InventoryID.BANK, new Item(ARMOUR, 1));
		put(InventoryID.INV);
		assertEquals("premise: one, in the bank", 1, monitor.refresh().quantityHeld(ARMOUR));

		// Withdrawn, and the bank closed behind it.
		put(InventoryID.INV, new Item(ARMOUR, 1));
		closeBank();

		assertEquals("still one piece of armour", 1, monitor.refresh().quantityHeld(ARMOUR));
	}

	@Test
	public void aPartialWithdrawalLeavesTheRestInTheBank()
	{
		put(InventoryID.BANK, new Item(RUNE, 5_000));
		put(InventoryID.INV);
		assertEquals(5_000, monitor.refresh().quantityHeld(RUNE));

		put(InventoryID.INV, new Item(RUNE, 1_000));
		closeBank();

		assertEquals("a thousand moved, four thousand did not, five thousand owned",
			5_000, monitor.refresh().quantityHeld(RUNE));
	}

	@Test
	public void anItemNeverInTheBankIsUnaffected()
	{
		put(InventoryID.BANK, new Item(RUNE, 100));
		put(InventoryID.INV);
		monitor.refresh();

		put(InventoryID.INV, new Item(ARMOUR, 1));
		closeBank();

		AccountState state = monitor.refresh();
		assertEquals("the armour is counted once", 1, state.quantityHeld(ARMOUR));
		assertEquals("and the bank is untouched", 100, state.quantityHeld(RUNE));
	}

	@Test
	public void openingTheBankAgainRebaselines()
	{
		// The discount is only a stand-in for a reading we cannot take. Once we can take one, it is
		// the truth and the stand-in must not go on subtracting on top of it.
		put(InventoryID.BANK, new Item(RUNE, 5_000));
		put(InventoryID.INV);
		monitor.refresh();

		put(InventoryID.INV, new Item(RUNE, 1_000));
		closeBank();
		assertEquals(5_000, monitor.refresh().quantityHeld(RUNE));

		// Bank opened again: it really does hold 4,000, and the inventory really does hold 1,000.
		put(InventoryID.BANK, new Item(RUNE, 4_000));

		assertEquals("read afresh, not discounted twice", 5_000,
			monitor.refresh().quantityHeld(RUNE));
	}
}
