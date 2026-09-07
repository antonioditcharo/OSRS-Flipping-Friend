package com.flippingfriend.session;

import com.flippingfriend.data.PluginStorage;
import com.flippingfriend.model.Transaction;
import com.google.gson.reflect.TypeToken;
import lombok.extern.slf4j.Slf4j;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;

private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(DataMigrator.class);
public class DataMigrator {
@Singleton
private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(DataMigrator.class);
public class DataMigrator {

    private final PluginStorage storage;

    @Inject
    public DataMigrator(PluginStorage storage) {
        this.storage = storage;
    }

    /**
     * Checks if migration is needed for the current account.
     * If legacy files exist and have not been migrated, it converts them into transactions.
     * 
     * @return true if a migration occurred, false otherwise.
     */
    public boolean migrateIfNeeded() {
        if (!storage.hasAccount()) {
            return false;
        }

        Path markerFile = storage.accountDir().resolve("migration_v2.marker");
        if (Files.exists(markerFile)) {
            return false; // Already migrated
        }

        Path journalFile = storage.accountDir().resolve("journal.jsonl");
        Path positionsFile = storage.accountDir().resolve("positions.json");
        Path transactionsFile = storage.accountDir().resolve("transactions.jsonl");

        if (!Files.exists(journalFile) && !Files.exists(positionsFile)) {
            // Nothing to migrate
            try {
                Files.createFile(markerFile);
            } catch (IOException e) {
                log.error("Failed to create migration marker", e);
            }
            return false;
        }

        log.info("Legacy data found. Starting migration to transaction-based ledger...");
        List<Transaction> allTransactions = new ArrayList<>();

        // 1. Migrate Positions (Ghost Buys)
        if (Files.exists(positionsFile)) {
            log.info("Migrating positions...");
            Map<Integer, LegacyPosition> positions = storage.readJson(
                positionsFile, 
                new TypeToken<Map<Integer, LegacyPosition>>(){}.getType(), 
                new HashMap<>()
            );

            for (Map.Entry<Integer, LegacyPosition> entry : positions.entrySet()) {
                int itemId = entry.getKey();
                LegacyPosition pos = entry.getValue();
                
                if (pos.quantity > 0) {
                    Transaction ghostBuy = new Transaction(
                        UUID.randomUUID(),
                        itemId,
                        pos.quantity,
                        (int) (pos.cost / Math.max(1, pos.quantity)),
                        pos.cost,
                        true,
                        0, // Assign timestamp 0 so it appears earliest in LIFO matching
                        -1, // No slot
                        UUID.randomUUID()
                    );
                    allTransactions.add(ghostBuy);
                }
            }
            log.info("Migrated {} positions.", positions.size());
        }

        // 2. Migrate Trade Journal
        if (Files.exists(journalFile)) {
            log.info("Migrating journal...");
            List<String> lines = storage.readLines(journalFile);
            for (String line : lines) {
                if (line.trim().isEmpty()) continue;
                try {
                    LegacyJournalEntry entry = storage.gson().fromJson(line, LegacyJournalEntry.class);
                    if (entry != null) {
                        Transaction tx = new Transaction(
                            UUID.randomUUID(),
                            entry.itemId,
                            entry.quantity,
                            entry.price,
                            (long) entry.price * entry.quantity, // amount spent/received
                            entry.buy,
                            entry.timestamp,
                            -1, // slot unknown
                            UUID.randomUUID() // new offer ID
                        );
                        allTransactions.add(tx);
                    }
                } catch (Exception e) {
                    log.error("Failed to parse journal entry: " + line, e);
                }
            }
            log.info("Migrated {} journal entries.", lines.size());
        }

        // 3. Sort transactions by timestamp just to be safe
        allTransactions.sort(Comparator.comparingLong(Transaction::getTimestamp));

        // 4. Write all to new transactions.jsonl
        log.info("Writing {} total transactions to new storage...", allTransactions.size());
        try {
            if (Files.exists(transactionsFile)) {
                // If there's already some new data, we append to it or just merge? 
                // Since this is a one-time migration, if transactions.jsonl exists but no marker, 
                // something is weird. We'll just overwrite or start fresh.
                Files.delete(transactionsFile);
            }
            Files.createFile(transactionsFile);
            for (Transaction tx : allTransactions) {
                storage.appendLine(transactionsFile, storage.gson().toJson(tx));
            }

            // Mark complete
            Files.createFile(markerFile);

            // Backup old files just in case
            Files.move(journalFile, storage.accountDir().resolve("journal.jsonl.bak"), StandardCopyOption.REPLACE_EXISTING);
            Files.move(positionsFile, storage.accountDir().resolve("positions.json.bak"), StandardCopyOption.REPLACE_EXISTING);
            
            log.info("Migration complete successfully.");
            return true;

        } catch (IOException e) {
            log.error("Failed during migration writing phase", e);
            return false;
        }
    }

    // Legacy classes for deserialization
    private static class LegacyPosition {
        int quantity;
        long cost;
    }

    private static class LegacyJournalEntry {
        int itemId;
        boolean buy;
        int price;
        int quantity;
        long timestamp;
    }
}
