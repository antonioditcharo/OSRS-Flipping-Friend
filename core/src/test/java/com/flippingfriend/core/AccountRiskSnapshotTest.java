package com.flippingfriend.core;

import org.junit.Test;
import static org.junit.Assert.*;

public class AccountRiskSnapshotTest {
    final String account=AccountIdentity.fromProfileKey("risk");
    AccountRiskSnapshot.Builder risk() {
        return AccountRiskSnapshot.builder(account,"window","session",100,0,200,190).capital(100000000,100000000);
    }
    @Test public void lowerProfitBoundAndUnresolvedCostReserveDriveTheAllowance() {
        var risk=risk().realized(-8,-7,2).marked(-1000).uncertainty(2000,1,0).build();
        assertTrue(risk.isAvailable());assertEquals(Long.valueOf(3008),risk.getDrawdownUpper());
        assertEquals(Long.valueOf(14996992),risk.getRemainingLossBudget());
        assertEquals(Long.valueOf(-8),risk.getBoundedRealizedProfitLower());assertEquals(Long.valueOf(-7),risk.getBoundedRealizedProfitUpper());
    }
    @Test public void capitalCannotExceedTheStartingOrCurrentlyKnownAmount() {
        assertEquals(Long.valueOf(15000000),risk().capital(100000000,200000000).build().getRemainingLossBudget());
        assertEquals(Long.valueOf(1500000),risk().capital(100000000,10000000).build().getRemainingLossBudget());
        assertEquals(Long.valueOf(0),risk().capital(100000000,0).build().getRemainingLossBudget());
    }
    @Test public void unavailableHasNoInventedMoneyValues() {
        var risk=AccountRiskSnapshot.unavailable(account,200,"Missing authoritative window");
        assertTrue(risk.isValid());assertFalse(risk.isAvailable());assertNull(risk.getDrawdownUpper());
        assertNull(risk.getBoundedRealizedProfitLower());assertNull(risk.getRemainingLossBudget());
    }
    @Test public void anAlreadyCountedKnownLossDoesNotShrinkTheCapitalBaseAgain() {
        var risk=risk().capital(100000000,99000000).realized(-1000000,-1000000,1).build();
        assertEquals(Long.valueOf(100000000),risk.getRiskCapital());
        assertEquals(Long.valueOf(1000000),risk.getDrawdownUpper());
        assertEquals(Long.valueOf(14000000),risk.getRemainingLossBudget());
    }
    @Test public void fullLongCapitalUsesExactArithmeticAndUnrepresentableLossRefuses() {
        var risk=risk().capital(Long.MAX_VALUE,Long.MAX_VALUE).build();
        assertEquals(Long.valueOf(1383505805528216371L),risk.getRemainingLossBudget());
        try {risk().realized(Long.MIN_VALUE,Long.MIN_VALUE,1).build();fail("unrepresentable loss");}
        catch(ArithmeticException expected) { }
    }
    @Test public void aReserveOnlyPauseMustNotDescribeTheReserveAsConfirmedLoss() {
        var risk=risk().uncertainty(20000000,1,0).build();
        assertTrue(risk.isAvailable());
        assertEquals(Long.valueOf(0),risk.getBoundedRealizedProfitLower());
        assertEquals(Long.valueOf(20000000),risk.getUncertainLossReserve());
        assertEquals(Long.valueOf(-5000000),risk.getRemainingLossBudget());
        assertTrue(risk.getReason(),risk.getReason().contains("uncertainty reserve"));
        assertTrue(risk.getReason(),risk.getReason().contains("not a confirmed loss"));
    }
    @Test public void riskWordingSeparatesLossBoundsAvailableCapitalAndUnspentAllowance() {
        var loss=risk().realized(-16000000,-16000000,1).build();
        assertTrue(loss.getReason(),loss.getReason().contains("loss bound"));
        assertFalse(loss.getReason(),loss.getReason().contains("uncertainty reserve"));
        assertTrue(risk().capital(100000000,0).build().getReason().contains("No observed trading capital"));
        assertFalse(risk().uncertainty(100,1,0).build().getReason().contains("paused"));
    }
}
