package com.flippingfriend.session;

import com.flippingfriend.model.FlipV2;
import com.flippingfriend.model.TaxCalculator;
import com.flippingfriend.model.Transaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Singleton
public class LocalFlipManager {

    private static final Logger log = LoggerFactory.getLogger(LocalFlipManager.class);
    private final TransactionManager transactionManager;
    private final TaxCalculator taxCalculator;
    private final int DEFAULT_ACCOUNT_ID = 1;

    private final Map<Integer, List<FlipV2>> cachedFlips = new ConcurrentHashMap<>();

    @Inject
    public LocalFlipManager(TransactionManager transactionManager, TaxCalculator taxCalculator) {
        this.transactionManager = transactionManager;
        this.taxCalculator = taxCalculator;
    }

    public void rebuildAllFlips() {
        log.info("Rebuilding all flips locally from TransactionManager...");
        List<Transaction> allTransactions = transactionManager.getHistory();
        
        Map<Integer, List<Transaction>> txsByItem = new java.util.HashMap<>();
        for (Transaction t : allTransactions) {
            txsByItem.computeIfAbsent(t.getItemId(), k -> new java.util.ArrayList<>()).add(t);
        }
        
        cachedFlips.clear();
        for (Map.Entry<Integer, List<Transaction>> entry : txsByItem.entrySet()) {
            cachedFlips.put(entry.getKey(), LocalFlipMatcher.matchTransactions(DEFAULT_ACCOUNT_ID, entry.getValue(), taxCalculator));
        }
        
        log.info("Successfully rebuilt flips for {} items.", cachedFlips.size());
    }

    public void onTransactionRecorded(Transaction newTransaction) {
        List<Transaction> itemTransactions = transactionManager.getTransactionsForItem(newTransaction.getItemId());
        List<FlipV2> updatedItemFlips = LocalFlipMatcher.matchTransactions(DEFAULT_ACCOUNT_ID, itemTransactions, taxCalculator);
        cachedFlips.put(newTransaction.getItemId(), updatedItemFlips);
    }
    
    public List<FlipV2> getFlipsForItem(int itemId) {
        return cachedFlips.getOrDefault(itemId, java.util.Collections.emptyList());
    }
    
    public List<FlipV2> getAllFlips() {
        List<FlipV2> all = new java.util.ArrayList<>();
        for (List<FlipV2> list : cachedFlips.values()) {
            all.addAll(list);
        }
        return all;
    }
}
