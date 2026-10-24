package com.flippingfriend.companion;

import com.flippingfriend.core.AccountIdentity;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Account databases and policy instances share only the public market source. */
final class CompanionAccounts implements AutoCloseable
{
    private final Path root;
    private final CompanionService marketSource;
    private final Map<String, CompanionService> services = new LinkedHashMap<>();
    private final Map<String, SqliteStore> stores = new LinkedHashMap<>();
    private boolean closed;

    CompanionAccounts(Path root, CompanionService marketSource)
    {
        this.root = root.toAbsolutePath().normalize();
        this.marketSource = marketSource;
        marketSource.useWarmShortlist(this::shortlist);
        marketSource.useWarmMaintenance(this::maintenance);
    }

    synchronized CompanionService forAccount(String accountId) throws Exception
    {
        AccountIdentity.require(accountId);
        if (closed) throw new IllegalStateException("account services are closed");
        CompanionService existing = services.get(accountId);
        if (existing != null) return existing;
        Path database = root.resolve(accountId).resolve("flipping-friend.db").normalize();
        if (!database.startsWith(root)) throw new IllegalArgumentException("invalid account path");
        SqliteStore store = new SqliteStore(database);
        CompanionService service = marketSource.forAccountStore(store, accountId);
        try
        {
            service.rehydrate();
            service.refreshAccountPlan();
            stores.put(accountId, store);
            services.put(accountId, service);
            return service;
        }
        catch (Exception error)
        {
            service.close();
            store.close();
            throw error;
        }
    }

    void refreshMarket()
    {
        marketSource.refreshMarket();
        List<CompanionService> current;
        synchronized (this) { current = new ArrayList<>(services.values()); }
        for (CompanionService service : current) service.refreshAccountPlan();
    }

    private synchronized List<Integer> shortlist()
    {
        java.util.Set<Integer> ids = new java.util.LinkedHashSet<>();
        for (CompanionService service : services.values()) ids.addAll(service.shortlistIds());
        return new ArrayList<>(ids);
    }
    private synchronized List<Integer> maintenance() {
        java.util.Set<Integer> ids=new java.util.LinkedHashSet<>();
        for(var service:services.values())ids.addAll(service.maintenanceIds());
        return new ArrayList<>(ids);
    }

    @Override public synchronized void close() throws Exception
    {
        closed = true;
        Exception failure = null;
        for (CompanionService service : services.values()) service.close();
        for (SqliteStore store : stores.values())
        {
            try { store.close(); }
            catch (Exception error) { if (failure == null) failure = error; else failure.addSuppressed(error); }
        }
        services.clear(); stores.clear();
        if (failure != null) throw failure;
    }
}
