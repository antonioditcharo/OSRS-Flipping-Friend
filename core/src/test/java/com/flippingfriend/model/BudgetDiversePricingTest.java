package com.flippingfriend.model;

import com.flippingfriend.core.*;
import com.flippingfriend.data.LatestPrice;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class BudgetDiversePricingTest {
    final long now=1_800_000_000L;
    final TaxCalculator tax=new TaxCalculator();
    PortfolioCandidate candidate(int item,int quantity,int buy,int sell,double sellProbability) {
        var c=new PortfolioCandidate(item,"Synthetic "+item,"synthetic",buy,buy,buy,sell,sell,sell,quantity,1000,
            tax.netProceeds(item,sell,quantity)-(long)buy*quantity,quantity*5L,quantity*4L,1,sellProbability,.2,.2,2,now+60);
        var b=new FillEstimate(1,.2,quantity*1000.0,.1);var s=new FillEstimate(sellProbability,.2,quantity*1000.0,.1);
        c=c.withForecasts(forecast(item,true,buy,quantity,b),forecast(item,false,sell,quantity,s));
        return c.withAccountPriceScenarios(AccountPriceScenarios.entry(c,5,2,new LatestPrice(sell,now,100,now),now,tax));
    }
    ExecutionForecast forecast(int item,boolean buy,int price,int quantity,FillEstimate estimate) {
        return new ExecutionForecast(ExecutionForecast.MODEL_VERSION,.1,1,1,false,item,buy,price,quantity,2,1,
            14400,48,40,100000,estimate,1,.2);
    }
    @Test public void affordableAlternativeEnablesTheBetterJointAccountInsideUnchangedBudgets() {
        var raw=List.of(candidate(1,80,100,130,1),candidate(1,40,100,130,1),
            candidate(1,20,100,130,1),candidate(1,10,100,130,1));
        var retained=AccountHorizonPricing.entryAlternatives(raw,3);
        assertEquals(3,retained.size());assertTrue(retained.contains(raw.get(3)));assertEquals(4,raw.size());
        var other=candidate(2,10,100,150,1);var old=new ArrayList<>(raw.subList(0,3));old.add(other);
        var diverse=new ArrayList<>(retained);diverse.add(other);
        var limits=new PortfolioConstraints(2,2000,500,10_000,10_000);var optimizer=new PortfolioOptimizer();
        var before=optimizer.optimizePreferred(old,limits,"same-account",now,c->c.getAccountPriceScenarios().score());
        var after=optimizer.optimizePreferred(diverse,limits,"same-account",now,c->c.getAccountPriceScenarios().score());
        assertEquals(1,before.getAllocations().size());assertEquals(2,after.getAllocations().size());
        assertEquals(2000,after.getAllocations().stream().mapToLong(a->a.getCandidate().getCapitalRequired()).sum());
        assertEquals(750,after.getAllocations().stream().mapToLong(a->a.getCandidate().getNetProfit()).sum());
        assertTrue(after.getAllocations().stream().mapToLong(a->a.getCandidate().getWorstLoss()).sum()<=500);
        assertEquals(2,after.getAllocations().stream().map(a->a.getCandidate().getItemId()).distinct().count());
        try{retained.clear();fail();}catch(UnsupportedOperationException expected){}
    }
    @Test public void anUnfillableHighTerminalMarkCannotDominateAValidAlternative() {
        var unfillable=candidate(1,10,50,150,0);var valid=candidate(1,10,100,130,1);
        assertTrue(unfillable.getAccountPriceScenarios().score()>valid.getAccountPriceScenarios().score());
        assertEquals(List.of(valid),AccountHorizonPricing.entryAlternatives(List.of(unfillable,valid),3));
    }
    @Test public void boundedHomogeneousInputsAndOneChoiceBudgetRetainTheirMeaning() {
        var large=candidate(1,80,100,130,1);var small=candidate(1,10,100,130,1);
        assertEquals(List.of(large),AccountHorizonPricing.entryAlternatives(List.of(large,small),1));
        assertTrue(AccountHorizonPricing.entryAlternatives(List.of(),3).isEmpty());
        try{AccountHorizonPricing.entryAlternatives(List.of(large,candidate(2,10,100,130,1)),3);fail();}
        catch(IllegalArgumentException expected){}
        try{AccountHorizonPricing.entryAlternatives(List.of(large),0);fail();}catch(IllegalArgumentException expected){}
    }
}
