package com.flippingfriend.core;

import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class PositionAccountingEffectTest
{
    @Test public void buyProgressProducesExactIncrementalAcquisition()
    {
        OfferLifecycleProjection p = apply(null, event("BUYING", true, 10, 0, 0, 1)).getProjection();
        OfferLifecycleTransition partial = apply(p, event("BUYING", true, 10, 4, 4000, 2));
        assertEffect(partial, PositionAccountingEffectType.ACQUIRE, 4, 4000);
        OfferLifecycleTransition more = apply(partial.getProjection(), event("BUYING", true, 10, 6, 6500, 3));
        assertEffect(more, PositionAccountingEffectType.ACQUIRE, 2, 2500);
        OfferLifecycleTransition filled = apply(more.getProjection(), event("BOUGHT", true, 10, 10, 10900, 4));
        assertEffect(filled, PositionAccountingEffectType.ACQUIRE, 4, 4400);
    }

    @Test public void sellProgressProducesQuantityOnlyDisposal()
    {
        OfferLifecycleProjection p = apply(null, event("SELLING", false, 10, 0, 0, 1)).getProjection();
        OfferLifecycleTransition partial = apply(p, event("SELLING", false, 10, 4, 5000, 2));
        assertEffect(partial, PositionAccountingEffectType.DISPOSE, 4, 0);
        OfferLifecycleTransition sold = apply(partial.getProjection(), event("SOLD", false, 10, 10, 13000, 3));
        assertEffect(sold, PositionAccountingEffectType.DISPOSE, 6, 0);
    }

    @Test public void replayCollectionAndNoNewFillProduceNone()
    {
        OfferEvent open = event("BUYING", true, 10, 0, 0, 1);
        OfferLifecycleProjection p = apply(null, open).getProjection();
        assertEffect(apply(p, open), PositionAccountingEffectType.NONE, 0, 0);
        OfferLifecycleTransition cancelled = apply(p, event("CANCELLED_BUY", true, 10, 0, 0, 2));
        assertEffect(cancelled, PositionAccountingEffectType.NONE, 0, 0);
        assertEffect(apply(cancelled.getProjection(), event("EMPTY", false, 0, 0, 0, 3)),
            PositionAccountingEffectType.NONE, 0, 0);
    }

    @Test public void ambiguousReconstructionAndContradictoryMoneyReconcile()
    {
        assertEffect(apply(null, event("BUYING", true, 10, 4, 4000, 1)),
            PositionAccountingEffectType.RECONCILE, 0, 0);
        OfferLifecycleProjection p = apply(null, event("BUYING", true, 10, 0, 0, 1)).getProjection();
        assertEffect(apply(p, event("BUYING", true, 10, 2, 0, 2)),
            PositionAccountingEffectType.RECONCILE, 0, 0);
        assertEffect(apply(p, event("BUYING", true, 10, 0, 100, 2)),
            PositionAccountingEffectType.RECONCILE, 0, 0);
    }

    @Test public void rejectedLifecycleAlwaysReconciles()
    {
        OfferLifecycleProjection p = apply(null, event("BUYING", true, 10, 4, 4000, 2)).getProjection();
        OfferLifecycleTransition rejected = apply(p, event("BUYING", true, 10, 3, 3000, 1));
        assertEffect(rejected, PositionAccountingEffectType.RECONCILE, 0, 0);
    }

    private static void assertEffect(OfferLifecycleTransition transition,
        PositionAccountingEffectType type, int quantity, long cost)
    {
        PositionAccountingEffect effect = transition.getAccountingEffect();
        assertNotNull(effect); assertEquals(type, effect.getType());
        assertEquals(quantity, effect.getQuantity()); assertEquals(cost, effect.getAcquisitionCost());
    }
    private static OfferLifecycleTransition apply(OfferLifecycleProjection p, OfferEvent e)
    { return OfferLifecycleReducer.apply(p, e); }
    private static OfferEvent event(String state, boolean buying, int total, int filled, long spent, long sequence)
    {
        OfferEvent.Builder b = OfferEvent.builder("trace", 100 + sequence, state)
            .eventIdentity("event-" + sequence, "session", "offer").slot(2)
            .buying(buying).sequence(sequence);
        if (!"EMPTY".equals(state)) b.item(4151, "Abyssal whip").price(1000)
            .quantities(total, filled).spent(spent);
        return b.build();
    }
}
