package com.flippingfriend.core;

import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public class RecordedSaleTest {
    @Test public void missingProceedsAndCostsRemainUnknownAcrossTheReadout() {
        var sale=new RecordedSale(1,100,561,"Nature rune",10,0,"source","offer",null,null,null,null,null,null,"legacy");
        var ledger=new AccountLedgerSnapshot(AccountIdentity.fromProfileKey("test"),100,1,0,0,0,0,0,0,0,List.of()).withRecentSales(List.of(sale));
        assertTrue(ledger.isValid());assertNull(ledger.getRecentSales().get(0).getProfitLower());assertNull(sale.getGross());
    }
    @Test public void profitBoundsMustAgreeWithRecordedGrossTaxAndAppliedCost() {
        new RecordedSale(1,100,561,"Nature rune",2,200,"source","offer",299L,4L,5L,94L,95L,SaleAccounting.CURRENT_POLICY,"range");
        try{new RecordedSale(1,100,561,"Nature rune",2,200,"source","offer",299L,4L,5L,95L,95L,SaleAccounting.CURRENT_POLICY,"wrong");fail();}
        catch(IllegalArgumentException expected){}
    }
    @Test public void boundedHistoryCannotChangeTotalsOrSmuggleFutureAndDuplicateRows() {
        var sale=new RecordedSale(1,100,561,"Nature rune",1,100,"source","offer",120L,2L,2L,18L,18L,SaleAccounting.CURRENT_POLICY,"exact");
        var ledger=new AccountLedgerSnapshot(AccountIdentity.fromProfileKey("test"),100,2,2,0,2,36,36,4,4,List.of());
        try{ledger.withRecentSales(List.of(sale,sale));fail();}catch(IllegalArgumentException expected){}
        var future=new RecordedSale(2,101,561,"Nature rune",1,100,"source2","offer2",120L,2L,2L,18L,18L,SaleAccounting.CURRENT_POLICY,"exact");
        try{ledger.withRecentSales(List.of(future));fail();}catch(IllegalArgumentException expected){}
        assertEquals(36,ledger.withRecentSales(List.of(sale)).getProfitLower());
    }
}
