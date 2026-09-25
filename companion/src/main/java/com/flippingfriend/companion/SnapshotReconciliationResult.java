package com.flippingfriend.companion;
import com.flippingfriend.core.PositionProjection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
/** Summary of one atomic account-snapshot reconciliation. */
final class SnapshotReconciliationResult
{
    private int adopted, confirmed, repaired, reconcile, unchanged, buyLimitRaised, buyLimitUnchanged;
    private final List<PositionProjection> resulting = new ArrayList<>();
    void adopted(PositionProjection p){adopted++;resulting.add(p);} void confirmed(PositionProjection p){confirmed++;resulting.add(p);} void repaired(PositionProjection p){repaired++;resulting.add(p);} void reconcile(PositionProjection p){reconcile++;resulting.add(p);} void unchanged(PositionProjection p){unchanged++;resulting.add(p);}
    int adopted(){return adopted;} int confirmed(){return confirmed;} int repaired(){return repaired;} int reconciliationRequired(){return reconcile;} int unchanged(){return unchanged;}
    void buyLimitRaised(){buyLimitRaised++;} void buyLimitUnchanged(){buyLimitUnchanged++;} int buyLimitsRaised(){return buyLimitRaised;} int buyLimitsUnchanged(){return buyLimitUnchanged;}
    List<PositionProjection> resultingPositions(){return Collections.unmodifiableList(resulting);}
}
