package com.flippingfriend.core;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;
import static org.junit.Assert.*;

public class PlanningExposureCopyTest {
    @Test public void copyPreservesEveryOtherFieldAndDefensivelyCopiesExposure() throws Exception {
        AccountSnapshot original = new AccountSnapshot(
                "copy-test", 123, 9000, 700, 3, 8, true, true, 12.5,
                "Balanced", java.util.Collections.singletonMap(4151, 999L), 50);
        Map<Integer, Long> exposure = new HashMap<>();
        exposure.put(4151, 400L);
        AccountSnapshot copy = original.withPlanningExposure(exposure);
        for (Field field : AccountSnapshot.class.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers())
                    || field.getName().equals("committedByItem")) continue;
            field.setAccessible(true);
            assertEquals(field.getName(), field.get(original), field.get(copy));
        }
        assertEquals(Long.valueOf(400), copy.getCommittedByItem().get(4151));
        assertEquals(Long.valueOf(999), original.getCommittedByItem().get(4151));
        exposure.put(4151, 800L);
        assertEquals(Long.valueOf(400), copy.getCommittedByItem().get(4151));
        try { copy.getCommittedByItem().clear(); fail("mutable exposure"); }
        catch (UnsupportedOperationException expected) { }
    }

    @Test public void invalidExposureIsRejected() {
        AccountSnapshot original = new AccountSnapshot(
                "copy-test", 123, 9000, 700, 3, 8, true, true, 0);
        try { original.withPlanningExposure(null); fail("null accepted"); }
        catch (IllegalArgumentException expected) { }
        for (Map<Integer, Long> bad : java.util.Arrays.asList(
                java.util.Collections.singletonMap(0, 1L),
                java.util.Collections.singletonMap(4151, -1L))) {
            try { original.withPlanningExposure(bad); fail("invalid exposure accepted"); }
            catch (IllegalArgumentException expected) { }
        }
    }
}
