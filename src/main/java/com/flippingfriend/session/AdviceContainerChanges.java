package com.flippingfriend.session;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.gameval.InventoryID;

/** Client-thread duplicate filter for advice invalidation, never financial event deduplication. */
public final class AdviceContainerChanges {
    private final Map<Integer,int[]> observations=new HashMap<>();

    public void reset(){observations.clear();}

    public boolean changed(int id,ItemContainer container) {
        // Every real bank event can renew a session-local cash lower bound, even with equal items.
        if(id==InventoryID.BANK)return true;
        if(id!=InventoryID.INV && id!=InventoryID.WORN && id!=InventoryID.GE_OFFER_0
            && id!=InventoryID.GE_OFFER_1 && id!=InventoryID.GE_OFFER_2 && id!=InventoryID.GE_OFFER_3
            && id!=InventoryID.GE_OFFER_4 && id!=InventoryID.GE_OFFER_5 && id!=InventoryID.GE_OFFER_6
            && id!=InventoryID.GE_OFFER_7)return false;
        Item[] items=container==null?null:container.getItems();
        if(items==null){observations.remove(id);return true;}
        int[] copy=new int[items.length*2];
        for(int i=0;i<items.length;i++) {
            if(items[i]==null){observations.remove(id);return true;}
            copy[2*i]=items[i].getId();copy[2*i+1]=items[i].getQuantity();
        }
        return !Arrays.equals(observations.put(id,copy),copy);
    }
}
