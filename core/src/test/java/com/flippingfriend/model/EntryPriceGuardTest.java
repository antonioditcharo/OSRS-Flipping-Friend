package com.flippingfriend.model;

import com.flippingfriend.core.PortfolioCandidate;
import org.junit.Test;
import static org.junit.Assert.*;

public class EntryPriceGuardTest {
    PortfolioCandidate tactic(int buy,int quantity,long profit,double buyHours,double sellHours,double buyProbability,double sellProbability,long unwind) {
        return new PortfolioCandidate(4151,"Abyssal whip","weapons",buy,buy,buy,120,120,120,quantity,70,profit,
            5L*quantity,unwind,buyProbability,sellProbability,buyHours,sellHours,2,1800000100);
    }
    @Test public void higherModeledProfitCannotJustifyMoreCapitalQuantityWaitingOrDownside() {
        var old=tactic(100,1,18,1,1,.8,.8,3);
        assertFalse(EntryPriceGuard.noWorse(tactic(100,2,100,.5,.5,.9,.9,3),old));
        assertFalse(EntryPriceGuard.noWorse(tactic(101,1,100,.5,.5,.9,.9,3),old));
        assertFalse(EntryPriceGuard.noWorse(tactic(99,1,100,1,1.01,.8,.8,3),old));
        assertFalse(EntryPriceGuard.noWorse(tactic(99,1,100,1,1,.8,.79,3),old));
        assertFalse(EntryPriceGuard.noWorse(tactic(99,1,100,1,1,.8,.8,4),old));
        assertTrue(EntryPriceGuard.noWorse(tactic(99,1,19,1,1,.8,.8,2),old));
        assertTrue(EntryPriceGuard.noWorse(old,old));
    }
}
