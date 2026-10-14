package com.flippingfriend.session;

import com.flippingfriend.core.StockDeclaration;
import com.flippingfriend.data.PluginStorage;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;

/** Separate account-scoped player intent. Reads/writes no position, journal or database files. */
@Singleton
public class StockDeclarations {
    private static final String FILE = "stock-declarations.json";
    private final PluginStorage storage;
    private static final class Saved {
        int schemaVersion = 1;
        String accountId;
        Map<Integer, StockDeclaration> declarations;
    }
    @Inject public StockDeclarations(PluginStorage storage) { this.storage = storage; }

    public synchronized Map<Integer, StockDeclaration> read() {
        String accountId = storage.accountId();
        if (accountId == null) return Collections.emptyMap();
        try { return Collections.unmodifiableMap(readFor(accountId)); }
        catch (Exception unavailable) { return Collections.emptyMap(); }
    }
    private Map<Integer, StockDeclaration> readFor(String expectedAccount) throws Exception {
        Path directory = storage.accountDir(expectedAccount);
        if (directory == null) return Collections.emptyMap();
        Path file = directory.resolve(FILE);
        if (!Files.isRegularFile(file)) return Collections.emptyMap();
        Saved saved = storage.gson().fromJson(Files.readString(file, StandardCharsets.UTF_8), Saved.class);
        if (saved == null || saved.schemaVersion != 1 || !expectedAccount.equals(saved.accountId)
                || saved.declarations == null || !expectedAccount.equals(storage.accountId()))
            throw new IllegalStateException("Stored stock reviews are unavailable; existing records were preserved.");
        for (var entry : saved.declarations.entrySet()) {
            StockDeclaration declaration = entry.getValue();
            if (entry.getKey() == null || declaration == null || !declaration.isValid()
                    || entry.getKey() != declaration.getItemId())
                throw new IllegalStateException("Invalid saved stock review; existing records were preserved.");
        }
        return new HashMap<>(saved.declarations);
    }

    public synchronized void record(String expectedAccount, int itemId, StockDeclaration declaration) throws Exception {
        if (expectedAccount == null || !expectedAccount.equals(storage.accountId()))
            throw new IllegalStateException("Account changed; refresh the review.");
        if (itemId <= 0 || (declaration != null && (!declaration.isValid() || declaration.getItemId() != itemId)))
            throw new IllegalArgumentException("Invalid stock declaration.");
        Saved saved = new Saved(); saved.accountId = expectedAccount;
        saved.declarations = new HashMap<>(readFor(expectedAccount));
        if (declaration == null) saved.declarations.remove(itemId);
        else saved.declarations.put(itemId, declaration);
        Path file = storage.accountDir(expectedAccount).resolve(FILE);
        Path temporary = file.resolveSibling(FILE + ".tmp");
        Files.writeString(temporary, storage.gson().toJson(saved), StandardCharsets.UTF_8);
        Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
    }

    public boolean excludesLocal(Position position) {
        if (position == null || position.isCostKnown()) return false;
        StockDeclaration declaration = read().get(position.getItemId());
        return declaration != null && declaration.isValid()
            && position.getQuantity() == declaration.getQuantity()
            && position.getTotalCost() == declaration.getTotalCost()
            && position.getOpenedAt() == declaration.getOpenedAt();
    }
}
