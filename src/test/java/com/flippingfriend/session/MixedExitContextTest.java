package com.flippingfriend.session;

import com.flippingfriend.core.*;
import com.google.gson.Gson;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public class MixedExitContextTest {
    private final long at=1800000000;
    private final AcquisitionExitContext original=new AcquisitionExitContext(1889,1685,66.55).bind("original");
    private final AcquisitionExitContext replacement=new AcquisitionExitContext(1899,1695,40.17).bind("replacement");
    @Test public void twoOriginalsKeepTargetsAndOnlyTheCommonProvenStopWithoutChangingCost(){
        var p=new Position(21948,"Bolts",1157,2058588,at,true);
        p.applyExitContexts(List.of(new AcquisitionExitSlice("original",original,550,550,at),
            new AcquisitionExitSlice("replacement",replacement,607,607,at+600)));
        assertEquals(1899,p.getTargetSellPrice());assertEquals(1685,p.getStopPrice());
        assertEquals(66.55,p.getPredictedSellMinutes(),0);assertEquals(2058588,p.getTotalCost());
        assertEquals(1899,p.protectedOriginalTarget(at+3600,60));
        assertEquals(0,p.protectedOriginalTarget(at+4200,60));
    }
    @Test public void unknownAcquisitionPreventsAnInventedCommonStop(){
        var p=new Position(21948,"Bolts",1157,2058588,at,true);
        p.applyExitContexts(List.of(new AcquisitionExitSlice("original",original,550,550,at),
            new AcquisitionExitSlice("unknown",null,607,607,at+600)));
        assertEquals(1889,p.getTargetSellPrice());assertEquals(0,p.getStopPrice());
        assertEquals(2,p.getExitContexts().size());
    }
    @Test public void canonicalSlicesSurviveWireRoundTripIncludingUnknownCoverage(){
        var projection=PositionProjection.ready(21948,"Bolts",807,1435853,at,at+900,"sale","offer")
            .withExitContexts(List.of(new AcquisitionExitSlice("original",original,200,550,at),
                new AcquisitionExitSlice("replacement",replacement,257,607,at+600)));
        var gson=new Gson();var restored=gson.fromJson(gson.toJson(projection),PositionProjection.class);
        assertNull(restored.getExitContext());assertEquals(1435853,restored.getTotalCost());
        assertEquals(200,restored.getExitContexts().get(0).getMinimumRemaining());
        assertEquals(607,restored.getExitContexts().get(1).getMaximumRemaining());
        assertEquals(at+600,restored.getExitContexts().get(1).getOpenedAt());
        var p=new Position(21948,"Bolts",807,1435853,at,true);p.applyExitContexts(restored.getExitContexts());
        assertEquals(1685,p.getStopPrice());assertEquals(1899,p.getTargetSellPrice());
    }
    @Test public void removalRangesNeverClaimWhichOriginalWasSold(){
        var a=new AcquisitionExitSlice("original",original,550,550,at).afterDisposal(350,807);
        var b=new AcquisitionExitSlice("replacement",replacement,607,607,at+600).afterDisposal(350,807);
        assertEquals(200,a.getMinimumRemaining());assertEquals(550,a.getMaximumRemaining());
        assertEquals(257,b.getMinimumRemaining());assertEquals(607,b.getMaximumRemaining());
        assertEquals(0,a.afterDisposal(807,0).getMaximumRemaining());
    }
    @Test public void corruptedWireRangesOrOriginIdentityCannotSupplyExitPolicy(){
        var gson=new Gson();
        var originalProjection=PositionProjection.ready(21948,"Bolts",10,17740,at,at,"fill","offer")
            .withExitContexts(List.of(new AcquisitionExitSlice("original",original,10,10,at)));
        for(int variant=0;variant<4;variant++) {
            var wire=gson.toJsonTree(originalProjection).getAsJsonObject();
            var slices=wire.getAsJsonArray("exitContexts");var first=slices.get(0).getAsJsonObject();
            if(variant==0)first.addProperty("minimumRemaining",11);
            if(variant==1)first.addProperty("maximumRemaining",9);
            if(variant==2)first.addProperty("sourceEventId","unrelated");
            if(variant==3)slices.add(first.deepCopy());
            assertFalse(gson.fromJson(wire,PositionProjection.class).hasValidExitContexts());
        }
    }
}
