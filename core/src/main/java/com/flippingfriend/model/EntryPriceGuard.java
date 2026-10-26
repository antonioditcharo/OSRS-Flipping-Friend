package com.flippingfriend.model;

import com.flippingfriend.core.PortfolioCandidate;

/** A price forecast cannot buy a bigger/slower/more exposed position on a higher modeled score. */
public final class EntryPriceGuard {
    private EntryPriceGuard(){}
    public static boolean noWorse(PortfolioCandidate changed,PortfolioCandidate baseline) {
        return changed.getItemId()==baseline.getItemId() && changed.getQuantity()==baseline.getQuantity()
            && changed.getCapitalRequired()<=baseline.getCapitalRequired() && changed.getNetProfit()>=baseline.getNetProfit()
            && changed.getWorstLoss()<=baseline.getWorstLoss() && changed.getUnwindLoss()<=baseline.getUnwindLoss()
            && changed.getBuyFillProbability()>=baseline.getBuyFillProbability() && changed.getSellFillProbability()>=baseline.getSellFillProbability()
            && changed.getBuyHours()<=baseline.getBuyHours() && changed.getSellHours()<=baseline.getSellHours()
            && changed.expectedProfit()>=baseline.expectedProfit();
    }
    public static boolean noWorse(Candidate changed,Candidate baseline) {
        return changed.getItemId()==baseline.getItemId() && changed.getQuantity()==baseline.getQuantity()
            && changed.getBuyPrice()<=baseline.getBuyPrice() && changed.getNetProfit()>=baseline.getNetProfit()
            && changed.getBuyFill().getProbability()>=baseline.getBuyFill().getProbability()
            && changed.getSellFill().getProbability()>=baseline.getSellFill().getProbability()
            && changed.getBuyFill().getExpectedHours()<=baseline.getBuyFill().getExpectedHours()
            && changed.getSellFill().getExpectedHours()<=baseline.getSellFill().getExpectedHours()
            && changed.getScore()>=baseline.getScore();
    }
}
