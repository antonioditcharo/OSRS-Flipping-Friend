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
    private final Map<String,java.util.concurrent.CompletableFuture<CompanionService>> opening=new LinkedHashMap<>();
    private PilotReplication replication;
    void useReplication(PilotReplication value){replication=value;}
    private BackgroundTradeRecovery recovery;
    void useRecovery(BackgroundTradeRecovery value){recovery=value;}
    String acceptHistory(com.flippingfriend.core.GeHistorySnapshot snapshot)throws Exception {
        com.flippingfriend.core.AccountIdentity.require(snapshot.accountId);
        if(recovery==null)throw new IllegalStateException("History capture is unavailable");
        return recovery.accept(snapshot);
    }
    String recoveryStatus(String id)throws Exception{return recovery==null?"{}":recovery.status(id);}

    CompanionAccounts(Path root, CompanionService marketSource)
    {
        this.root = root.toAbsolutePath().normalize();
        this.marketSource = marketSource;
        marketSource.useWarmShortlist(this::shortlist);
        marketSource.useWarmMaintenance(this::maintenance);
    }

    CompanionService forAccount(String accountId) throws Exception {
        AccountIdentity.require(accountId);
        java.util.concurrent.CompletableFuture<CompanionService> future;boolean owner=false;
        synchronized(this) {
            if(closed)throw new IllegalStateException("account services are closed");
            CompanionService current=services.get(accountId);if(current!=null)return current;
            future=opening.get(accountId);
            if(future==null) {
                if(services.size()+opening.size()>=128)throw new IllegalStateException("local account capacity reached");
                future=new java.util.concurrent.CompletableFuture<>();opening.put(accountId,future);owner=true;
            }
        }
        if(!owner)return future.get();
        Path database=root.resolve(accountId).resolve("flipping-friend.db").normalize();
        SqliteStore store=null;CompanionService service=null;
        try {
            if(!database.startsWith(root))throw new IllegalArgumentException("invalid account path");
            store=new SqliteStore(database);service=marketSource.forAccountStore(store,accountId);
            service.rehydrate();service.refreshAccountPlan();
            synchronized(this) {
                if(closed)throw new IllegalStateException("account services are closed");
                stores.put(accountId,store);services.put(accountId,service);opening.remove(accountId);
                future.complete(service);
            }
            if(recovery!=null)recovery.register(accountId,database,service);
            if(replication!=null)replication.register(accountId,database);
            return service;
        }catch(Exception error) {
            if(service!=null)service.close();if(store!=null)store.close();
            synchronized(this){opening.remove(accountId);future.completeExceptionally(error);}
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
