package com.flippingfriend.core;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;
public class PortfolioExposureOverflowTest {
    @Test public void addingACandidateCannotWrapAnExposureBoundNegative() {
        var buy=new PortfolioCandidate(562,"Item","group",100,100,100,120,120,120,10,100000,200L,
            1,1,.95,.95,.4,.4,1.5,1000);
        var limits=new PortfolioConstraints(1,10000,10000,Long.MAX_VALUE,Long.MAX_VALUE,
            Map.of(),Map.of("group",Long.MAX_VALUE-500),0);
        var plan=new PortfolioOptimizer().optimize(List.of(buy),limits,"overflow",100);
        assertTrue("An unrepresentable exposure addition must not permit a buy",plan.getAllocations().isEmpty());
    }
}
