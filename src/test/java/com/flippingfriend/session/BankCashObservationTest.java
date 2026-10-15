package com.flippingfriend.session;

import com.flippingfriend.*;
import java.util.EnumSet;
import net.runelite.api.*;
import net.runelite.api.gameval.InventoryID;
import net.runelite.client.game.ItemManager;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class BankCashObservationTest {
    private static final class Fixture {
        final Client client=mock(Client.class);
        final GrandExchangeOffer[] slots=new GrandExchangeOffer[8];
        final AccountMonitor monitor;
        Fixture() {
            when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
            when(client.getWorldType()).thenReturn(EnumSet.of(WorldType.MEMBERS));
            FlippingFriendConfig config=mock(FlippingFriendConfig.class);
            when(config.accountMode()).thenReturn(AccountMode.AUTOMATIC);
            for (int slot=0;slot<8;slot++) slots[slot]=offer(GrandExchangeOfferState.EMPTY,0);
            when(client.getGrandExchangeOffers()).thenReturn(slots);
            monitor=new AccountMonitor(client,mock(ItemManager.class),config);
        }
        void bank(long coins) {
            ItemContainer bank=mock(ItemContainer.class);
            when(bank.getItems()).thenReturn(new Item[]{new Item(995,(int)coins)});
            when(client.getItemContainer(InventoryID.BANK)).thenReturn(bank);
            monitor.refresh(); when(client.getItemContainer(InventoryID.BANK)).thenReturn(null);
        }
        void update(GrandExchangeOfferState state,int filled) { slots[0]=offer(state,filled); monitor.refresh(); }
        long spendable() { return monitor.getState().spendableCoins(true,0); }
    }
    private static GrandExchangeOffer offer(GrandExchangeOfferState state,int filled) {
        GrandExchangeOffer e=mock(GrandExchangeOffer.class); when(e.getState()).thenReturn(state);
        if (state!=GrandExchangeOfferState.EMPTY) {
            when(e.getItemId()).thenReturn(561); when(e.getPrice()).thenReturn(1_000_000L);
            when(e.getTotalQuantity()).thenReturn(10); when(e.getQuantitySold()).thenReturn(filled);
            when(e.getSpent()).thenReturn(filled*1_000_000L);
        }
        return e;
    }
    @Test public void fillsAndCollectionCannotRestoreSpentBankCoins() {
        Fixture f=new Fixture(); f.bank(100_000_000);
        f.update(GrandExchangeOfferState.BUYING,0); assertEquals(90_000_000,f.spendable());
        f.update(GrandExchangeOfferState.BUYING,4); assertEquals(90_000_000,f.spendable());
        f.update(GrandExchangeOfferState.BOUGHT,10); f.update(GrandExchangeOfferState.EMPTY,0);
        assertEquals(90_000_000,f.spendable());
        f.update(GrandExchangeOfferState.BUYING,0); assertEquals(80_000_000,f.spendable());
    }
    @Test public void aFreshBankReadDoesNotDeductAnAlreadyFundedOfferAgain() {
        Fixture f=new Fixture(); f.update(GrandExchangeOfferState.BUYING,0); f.bank(90_000_000);
        f.update(GrandExchangeOfferState.BUYING,4); assertEquals(90_000_000,f.spendable());
        f.update(GrandExchangeOfferState.BOUGHT,10); f.update(GrandExchangeOfferState.EMPTY,0);
        assertEquals(90_000_000,f.spendable());
        f.bank(91_000_000); assertEquals(91_000_000,f.spendable());
        f.monitor.reset(); assertEquals(0,f.spendable());
    }
}
