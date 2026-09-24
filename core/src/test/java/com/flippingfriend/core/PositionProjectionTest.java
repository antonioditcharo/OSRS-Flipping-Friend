package com.flippingfriend.core;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
public class PositionProjectionTest{
 @Test public void readyPositionExposesBlendedAverage(){PositionProjection p=PositionProjection.ready(4151,"Whip",6,6500,100,200,"e","o");assertEquals(1083,p.averageCost());assertEquals(PositionConsistencyState.READY,p.getConsistencyState());}
 @Test public void reconciliationCanPreserveUnknownOrEmptyFacts(){PositionProjection p=PositionProjection.reconcile(4151,"Whip",0,0,false,100,200,"e","o","missing position");assertEquals(0,p.averageCost());assertEquals(PositionConsistencyState.RECONCILE,p.getConsistencyState());}
 @Test(expected=IllegalArgumentException.class) public void readyRequiresPositiveKnownCost(){PositionProjection.ready(4151,"Whip",1,0,100,"e","o");}
}
