package com.flippingfriend.core;

import org.junit.Test;
import static org.junit.Assert.*;

public class MissedFlipEvidenceTest {
    @Test public void unidentifiedNonEconomicReconciliationIsExplicitEvidenceNotAnItem() {
        MissedFlipEvidence evidence = new MissedFlipEvidence("clear", null, 0, null,
            "RECONCILE", 0, 100, null, null, null, null, null, null,
            "offer cleared before a terminal observation");
        assertTrue(evidence.isValid());
        assertEquals(0, evidence.getItemId());
        assertEquals("Unidentified offer", evidence.getItemName());
        assertNull(evidence.getAcquisitionCost()); assertNull(evidence.getGrossProceeds());
        assertNull(evidence.getProfitLower()); assertNull(evidence.getProfitUpper());
    }

    @Test public void unidentifiedEvidenceCannotCarryEconomicEffectsOrInventMoney() {
        for (String type : new String[] {"ACQUIRE", "DISPOSE", "UNKNOWN"}) {
            reject(type, 0, null, null, null);
        }
        reject("RECONCILE", 1, null, null, null);
        reject("RECONCILE", 0, 100L, null, null);
        reject("RECONCILE", 0, null, 100L, null);
        reject("RECONCILE", 0, null, null, 10L);
    }

    private void reject(String type, int quantity, Long cost, Long gross, Long profit) {
        try {
            new MissedFlipEvidence("event", null, 0, null, type, quantity, 100,
                cost, gross, null, null, profit, profit, "missing item identity");
            fail("unidentified evidence must not assert ownership or money");
        } catch (IllegalArgumentException expected) { }
    }
}
