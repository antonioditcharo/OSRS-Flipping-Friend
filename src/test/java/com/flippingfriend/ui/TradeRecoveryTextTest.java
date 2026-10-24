package com.flippingfriend.ui;

import com.flippingfriend.core.*;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public class TradeRecoveryTextTest {
    @Test public void expiredCoverageKeepsTheObligationButWithdrawsOldEligibility() {
        var row=new TradeRecoveryItem("exact-episode",TradeRecoveryItem.Kind.OFFER,TradeRecoveryItem.State.ACTIONABLE,
            561,"Rune",0,3,100,false,100,230,"Supported change","Reauthorize the exact sale");
        var snapshot=new TradeRecoverySnapshot("account",200,200,true,List.of(row));
        String fresh=TradeRecoveryText.render(snapshot,201);assertTrue(fresh.contains("ACTIONABLE"));assertTrue(fresh.contains("Slot 1"));
        String stale=TradeRecoveryText.render(snapshot,231);assertTrue(stale.contains("AWAITING_CURRENT_EVALUATION"));
        assertFalse(stale.contains("ACTIONABLE"));assertTrue(stale.contains("exact-episode"));assertTrue(stale.contains("do not add their quantities"));
        assertTrue(TradeRecoveryText.render(null,231).contains("unavailable"));
    }
}
