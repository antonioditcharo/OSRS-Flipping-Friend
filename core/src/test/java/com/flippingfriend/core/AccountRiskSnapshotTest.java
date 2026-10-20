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
    @Test public void currentPolicyKeepsHistoricalUnknownMoneySeparateFromTheLossAllowance()throws Exception {
        var current=risk().realized(-8,-7,2).marked(-1000).uncertainty(2000,1,0)
            .currentAccountPolicy(500000000,2000).build();
        assertTrue(current.isCurrentAccountPolicy());assertTrue(current.isAvailable());
        assertEquals(Long.valueOf(500000000),current.getHistoricalUncertaintyReserve());
        assertEquals(Long.valueOf(3008),current.getDrawdownUpper());
        assertEquals(Long.valueOf(14996992),current.getRemainingLossBudget());
        // Constructor-bypassing readers must not accept malformed metadata or a policy downgrade.
        var field=AccountRiskSnapshot.class.getDeclaredField("historicalUncertaintyReserve");field.setAccessible(true);
        field.set(current,-1L);assertFalse(current.isValid());field.set(current,500000000L);
        field=AccountRiskSnapshot.class.getDeclaredField("unpricedManagedCostReserve");field.setAccessible(true);
        field.set(current,2001L);assertFalse(current.isValid());field.set(current,2000L);
        field=AccountRiskSnapshot.class.getDeclaredField("policyVersion");field.setAccessible(true);
        field.set(current,"unknown");assertFalse(current.isValid());field.set(current,AccountRiskSnapshot.CURRENT_ACCOUNT_POLICY);
        field=AccountRiskSnapshot.class.getDeclaredField("schemaVersion");field.setAccessible(true);
        field.set(current,"1");assertFalse(current.isValid());
    }
    @Test public void priorCurrentAccountRiskRemainsReadableWithoutRewritingItsPolicy()throws Exception {
        var current=risk().currentAccountPolicy(500000000,0).build();
        var field=AccountRiskSnapshot.class.getDeclaredField("policyVersion");field.setAccessible(true);
        assertEquals("CURRENT_ACCOUNT_V2",field.get(current));
        field.set(current,"CURRENT_ACCOUNT_V1");
        assertTrue(current.isValid());assertTrue(current.isCurrentAccountPolicy());
        assertEquals("CURRENT_ACCOUNT_V1",field.get(current));
    }
    @Test public void verifiedCashPolicySeparatesPriceUncertaintyFromUnseenStockAndRetainsItemPauses()throws Exception {
        var current=risk().uncertainty(2000,0,0).verifiedCashPolicy(500000000,20000000,2000,java.util.Set.of(561)).build();
        assertTrue(current.isAvailable());assertTrue(current.isVerifiedCashPolicy());
        assertEquals(19998000,current.getValuationUncertaintyExcluded());assertEquals(Long.valueOf(14998000),current.getRemainingLossBudget());
        var snapshot=new AccountSnapshot("copied",190,1000000,0,8,8,true,false,0).withAccountId(account)
            .withPlanningRestrictions(java.util.Map.of(561,20000000L),java.util.Set.of(562)).withCanonicalRisk(current);
        assertEquals(java.util.Set.of(561,562),snapshot.getUnavailableEntryItems());
        var excluded=AccountRiskSnapshot.class.getDeclaredField("valuationUncertaintyExcluded");excluded.setAccessible(true);
        excluded.set(current,20000001L);assertFalse(current.isValid());excluded.set(current,19998000L);
        var paused=AccountRiskSnapshot.class.getDeclaredField("valuationPausedItems");paused.setAccessible(true);
        paused.set(current,java.util.Set.of());assertFalse(current.isValid());
    }
}
