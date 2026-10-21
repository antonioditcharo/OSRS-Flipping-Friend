package com.flippingfriend.core;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class InventoryEvidenceTest {
    @Test public void inventoryEvidenceSurvivesPlanningCopiesWithoutAdoptingOtherPhysicalUnits() {
        var inventory=new HashMap<>(Map.of(561,2));
        var snapshot=new AccountSnapshot("capture",100,100000,0,8,8,true,false,0)
            .withAccountId(AccountIdentity.fromProfileKey("inventory"))
            .withObservedHoldings(Map.of(561,7)).withObservedInventory(inventory).withAutomaticTradeScope();
        inventory.put(561,7000);
        var copy=snapshot.withPlanningExposure(Map.of()).withAccountId(snapshot.getAccountId());
        assertEquals(Map.of(561,2),copy.getObservedInventory());assertEquals(Map.of(561,7),copy.getObservedHoldings());
        assertNull(new AccountSnapshot("legacy",100,0,0,8,8,true,false,0).getObservedInventory());
        try{snapshot.withObservedInventory(Map.of(561,8));fail("Contradictory inventory accepted");}
        catch(IllegalArgumentException expected){}
    }
}
