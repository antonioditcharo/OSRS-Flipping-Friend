package com.flippingfriend.model;

import com.flippingfriend.data.ItemMetadata;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public class ExactCostBreakEvenTest {
    @Test public void theReturnedPriceCoversThePoolAndOneCoinLessDoesNotAcrossTaxBoundaries() {
        var tax=new TaxCalculator();
        for(int quantity:new int[]{1,2,3,17,Integer.MAX_VALUE})
            for(long average:new long[]{1,40,49,50,980,249999999,250000000,1000000000}) {
                long cost=average*quantity+(quantity>1?quantity-1:0);
                int price=tax.breakEvenSellPriceForCost(561,cost,quantity);
                assertTrue(price>0);assertTrue(tax.netProceeds(561,price,quantity)>=cost);
                assertTrue(price==1 || tax.netProceeds(561,price-1,quantity)<cost);
            }
    }
    @Test public void exemptionsUseTheSameExactPoolCost() {
        var tax=new TaxCalculator();tax.resolveExemptions(List.of(new ItemMetadata(1755,"Chisel",false,40,14)));
        assertEquals(41,tax.breakEvenSellPriceForCost(1755,81,2));
        assertEquals(1001,tax.breakEvenSellPriceForCost(1755,2001,2));
    }
    @Test public void impossibleOrUnknownCostDoesNotClaimAClampedBreakEven() {
        var tax=new TaxCalculator();
        assertEquals(0,tax.breakEvenSellPriceForCost(561,2L*Integer.MAX_VALUE,2));
        assertEquals(0,tax.breakEvenSellPriceForCost(561,Long.MAX_VALUE,Integer.MAX_VALUE));
        assertEquals(0,tax.breakEvenSellPriceForCost(561,0,2));assertEquals(0,tax.breakEvenSellPriceForCost(561,10,0));
    }
}
