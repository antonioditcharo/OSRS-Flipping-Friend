package com.flippingfriend.core;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class StockPerformanceSnapshotTest {
    PerformanceSnapshot fixture() {
        var report=new PerformanceSnapshot(AccountIdentity.fromProfileKey("stock-display"),"ALL",null,1000,0,0,0,0,0,0,0,0,List.of());
        report.spendableCoins=10000L;report.managedAcquisitionCost=125L;
        report.stockValuations=List.of(new PerformanceSnapshot.Stock(561,"Nature rune",3,2,2,125,44,900,88,"One unit is not currently observed."));
        report.portfolioValue=88L;report.unrealizedProfit=5L;report.unpricedCost=42L;return report;
    }
    @Test public void exactPartialCostAndKnownProfitMustConserveTheFullPool() {
        var report=fixture();assertTrue(report.isValid());report.unpricedCost=41L;assertFalse(report.isValid());
        report=fixture();report.managedAcquisitionCost=124L;assertFalse(report.isValid());
        report=fixture();report.unrealizedProfit=6L;assertFalse(report.isValid());
        report=fixture();report.spendableCoins=null;assertFalse(report.isValid());
    }
    @Test public void duplicateFutureOrExcessPhysicalValuationsCannotPassDeserializationValidation()throws Exception {
        var report=fixture();report.stockValuations=List.of(report.stockValuations.get(0),report.stockValuations.get(0));assertFalse(report.isValid());
        report=fixture();var row=report.stockValuations.get(0);
        var field=PerformanceSnapshot.Stock.class.getDeclaredField("quotedAt");field.setAccessible(true);field.set(row,1001L);assertFalse(report.isValid());
        report=fixture();field=PerformanceSnapshot.Stock.class.getDeclaredField("pricedQuantity");field.setAccessible(true);
        field.set(report.stockValuations.get(0),3);assertFalse(report.isValid());
    }
}
