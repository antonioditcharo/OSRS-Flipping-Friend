package com.flippingfriend.core;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class ScopedLedgerConstructionTest {
    @Test public void scopeIsAttachedBeforeValidatingUnrelatedHoldingsBesideAnItemZeroGap() {
        var ledger=new AccountLedgerSnapshot(AccountIdentity.fromProfileKey("scope"),100,0,0,0,0,0,0,0,0,List.of());
        var holding=PositionProjection.ready(561,"Nature rune",10,1000,10,"buy","episode");
        var missed=new MissedFlipEvidence("gap","old",0,null,"RECONCILE",0,1,null,null,null,null,null,null,"Incomplete old observation");
        var scoped=ledger.withTradeEvidence(List.of(holding),List.of(missed),Map.of("gap",Set.of(562)));
        assertTrue(scoped.isValid());assertTrue(scoped.hasUnassignedReconciliation());assertFalse(scoped.hasUnboundedReconciliation());
        assertEquals(10,scoped.getTradingPositions().get(0).getQuantity());
        try{ledger.withTradeEvidence(List.of(holding),List.of(missed),Map.of("gap",Set.of()));fail();}catch(IllegalArgumentException expected){}
        try{ledger.withTradeEvidence(List.of(holding),List.of(missed),Map.of("gap",Set.of(561)));fail();}catch(IllegalArgumentException expected){}
    }
}
