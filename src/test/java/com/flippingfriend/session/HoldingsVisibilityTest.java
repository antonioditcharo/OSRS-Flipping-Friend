package com.flippingfriend.session;

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

import static org.junit.Assert.assertEquals;

/**
 * Every place an item can be, the plugin has to look.
 * <p>
 * The equipment container was never read. Only the inventory, the bank and the eight collection boxes
 * were, so a bought item that was then worn was in none of them — and because an unseen holding used
 * to be deleted as sold, equipping something you had just bought destroyed the position and the cost
 * basis behind it. A Mystic smoke staff went missing exactly that way.
 */
public class HoldingsVisibilityTest
{
	private static final int MYSTIC_SMOKE_STAFF = 21_006;

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

		// Tradeable, so the holdings map keeps it.
		ItemComposition composition = Mockito.mock(ItemComposition.class);
		Mockito.when(composition.isGeTradeable()).thenReturn(true);
		Mockito.when(itemManager.getItemComposition(Mockito.anyInt())).thenReturn(composition);
		Mockito.when(itemManager.canonicalize(Mockito.anyInt()))
			.thenAnswer(call -> call.getArgument(0));

		monitor = new AccountMonitor(client, itemManager, config);
	}

	/**
	 * Stubs one container. Built and stubbed in one call on purpose: doing it inside the argument of
	 * another {@code when(...)} is nested stubbing, which Mockito rejects.
	 */
	private void put(int containerId, int itemId, int quantity)
	{
		// Item is final, so it is constructed rather than mocked.
		ItemContainer container = Mockito.mock(ItemContainer.class);
		Mockito.when(container.getItems()).thenReturn(new Item[]{new Item(itemId, quantity)});
		Mockito.when(client.getItemContainer(containerId)).thenReturn(container);
	}

	@Test
	public void anEquippedItemIsStillHeld()
	{
		put(InventoryID.WORN, MYSTIC_SMOKE_STAFF, 1);

		AccountState state = monitor.refresh();

		assertEquals("what you are wearing is what you own", 1,
			state.quantityHeld(MYSTIC_SMOKE_STAFF));
	}

	@Test
	public void anItemInTheInventoryIsStillHeld()
	{
		put(InventoryID.INV, MYSTIC_SMOKE_STAFF, 1);

		assertEquals(1, monitor.refresh().quantityHeld(MYSTIC_SMOKE_STAFF));
	}

	@Test
	public void anItemInACollectionBoxIsStillHeld()
	{
		// A finished buy waiting to be collected. Not in any inventory, but very much owned.
		put(InventoryID.GE_OFFER_3, MYSTIC_SMOKE_STAFF, 1);

		assertEquals(1, monitor.refresh().quantityHeld(MYSTIC_SMOKE_STAFF));
	}

	@Test
	public void wornAndCarriedAddUpRatherThanReplacingEachOther()
	{
		put(InventoryID.INV, MYSTIC_SMOKE_STAFF, 2);
		put(InventoryID.WORN, MYSTIC_SMOKE_STAFF, 1);

		assertEquals(3, monitor.refresh().quantityHeld(MYSTIC_SMOKE_STAFF));
	}

	@Test
	public void nothingAnywhereReadsAsNothingHeld()
	{
		assertEquals(0, monitor.refresh().quantityHeld(MYSTIC_SMOKE_STAFF));
	}
}
