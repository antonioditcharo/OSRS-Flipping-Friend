package com.flippingfriend.session;

import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.gameval.InventoryID;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class AdviceContainerChangesTest {
    ItemContainer contents(int item,int quantity) {
        var container=mock(ItemContainer.class);
        when(container.getItems()).thenReturn(new Item[]{new Item(item,quantity)});return container;
    }
    @Test public void exactInventoryEquipmentAndCollectionDuplicatesAreTheOnlySuppressedEvidence() {
        var changes=new AdviceContainerChanges();
        for(int id:new int[]{InventoryID.INV,InventoryID.WORN,InventoryID.GE_OFFER_0,InventoryID.GE_OFFER_7}) {
            assertTrue(changes.changed(id,contents(561,10)));
            assertFalse(changes.changed(id,contents(561,10)));
            assertTrue(changes.changed(id,contents(561,9)));
            assertTrue(changes.changed(id,contents(562,9)));
            assertTrue(changes.changed(id,null));assertTrue(changes.changed(id,contents(562,9)));
        }
        assertFalse(changes.changed(123456,contents(995,1000000)));
    }
    @Test public void everyBankEventCanRenewCashAndLoginResetCannotBorrowAnotherSessionsSnapshot() {
        var changes=new AdviceContainerChanges();var container=contents(995,1000);
        assertTrue(changes.changed(InventoryID.BANK,container));assertTrue(changes.changed(InventoryID.BANK,container));
        assertTrue(changes.changed(InventoryID.INV,container));assertFalse(changes.changed(InventoryID.INV,container));
        changes.reset();assertTrue(changes.changed(InventoryID.INV,container));
    }
}
