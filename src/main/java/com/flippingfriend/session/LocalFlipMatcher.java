package com.flippingfriend.session;

import com.flippingfriend.model.FlipV2;
import com.flippingfriend.model.FlipV2.FlipStatus;
import com.flippingfriend.model.TaxCalculator;
import com.flippingfriend.model.Transaction;

import java.util.*;

public class LocalFlipMatcher {

    public static List<FlipV2> matchTransactions(int accountId, List<Transaction> transactions, TaxCalculator taxCalculator) {
        List<FlipV2> completedFlips = new ArrayList<>();
        Map<Integer, LinkedList<FlipV2>> openBuysByItem = new HashMap<>();

        for (Transaction t : transactions) {
            int itemId = t.getItemId();
            LinkedList<FlipV2> openBuys = openBuysByItem.computeIfAbsent(itemId, k -> new LinkedList<>());

            if (t.isBuy()) {
                FlipV2 flip = new FlipV2();
                flip.setId(UUID.randomUUID());
                flip.setAccountId(accountId);
                flip.setItemId(itemId);
                flip.setOpenedTime((int) (t.getTimestamp() / 1000));
                flip.setOpenedQuantity(t.getQuantity());
                flip.setSpent(t.getAmountSpent());
                flip.setClosedQuantity(0);
                flip.setReceivedPostTax(0);
                flip.setTaxPaid(0);
                flip.setProfit(0);
                flip.setStatus(FlipStatus.BUYING);
                flip.setUpdatedTime((int) (System.currentTimeMillis() / 1000));
                flip.setDeleted(false);
                flip.setPortfolioId(1);
                
                openBuys.addLast(flip);
            } else {
                int sellRemaining = t.getQuantity();
                long totalGrossReceived = t.getAmountSpent();
                int receivedPerItem = sellRemaining > 0 ? (int)(totalGrossReceived / sellRemaining) : 0;

                while (sellRemaining > 0 && !openBuys.isEmpty()) {
                    FlipV2 newestBuy = openBuys.getLast();
                    int buyRemaining = newestBuy.getOpenedQuantity() - newestBuy.getClosedQuantity();

                    int matchedQty = Math.min(sellRemaining, buyRemaining);
                    
                    long allocatedGross = (long) matchedQty * receivedPerItem;
                    long allocatedTax = taxCalculator.taxFor(itemId, receivedPerItem, matchedQty);
                    long allocatedNet = allocatedGross - allocatedTax;

                    newestBuy.setClosedQuantity(newestBuy.getClosedQuantity() + matchedQty);
                    newestBuy.setReceivedPostTax(newestBuy.getReceivedPostTax() + allocatedNet);
                    newestBuy.setTaxPaid(newestBuy.getTaxPaid() + allocatedTax);
                    newestBuy.setClosedTime((int) (t.getTimestamp() / 1000));
                    
                    long avgCost = newestBuy.getAvgBuyPrice();
                    long costBasis = avgCost * newestBuy.getClosedQuantity();
                    newestBuy.setProfit(newestBuy.getReceivedPostTax() - costBasis);

                    sellRemaining -= matchedQty;
                    
                    if (newestBuy.getOpenedQuantity() == newestBuy.getClosedQuantity()) {
                        newestBuy.setStatus(FlipStatus.FINISHED);
                        completedFlips.add(newestBuy);
                        openBuys.removeLast();
                    } else {
                        newestBuy.setStatus(FlipStatus.BUYING);
                    }
                }

                if (sellRemaining > 0) {
                    FlipV2 unbacked = new FlipV2();
                    unbacked.setId(UUID.randomUUID());
                    unbacked.setAccountId(accountId);
                    unbacked.setItemId(itemId);
                    unbacked.setOpenedTime((int) (t.getTimestamp() / 1000));
                    unbacked.setOpenedQuantity(sellRemaining);
                    unbacked.setSpent(0);
                    unbacked.setClosedQuantity(sellRemaining);
                    
                    long allocatedGross = (long) sellRemaining * receivedPerItem;
                    long allocatedTax = taxCalculator.taxFor(itemId, receivedPerItem, sellRemaining);
                    long allocatedNet = allocatedGross - allocatedTax;
                    
                    unbacked.setReceivedPostTax(allocatedNet);
                    unbacked.setTaxPaid(allocatedTax);
                    unbacked.setProfit(allocatedNet);
                    unbacked.setStatus(FlipStatus.FINISHED);
                    unbacked.setUpdatedTime((int) (System.currentTimeMillis() / 1000));
                    unbacked.setDeleted(false);
                    unbacked.setPortfolioId(1);
                    unbacked.setClosedTime((int) (t.getTimestamp() / 1000));
                    
                    completedFlips.add(unbacked);
                }
            }
        }
        
        for (LinkedList<FlipV2> openList : openBuysByItem.values()) {
            completedFlips.addAll(openList);
        }

        return completedFlips;
    }
}
