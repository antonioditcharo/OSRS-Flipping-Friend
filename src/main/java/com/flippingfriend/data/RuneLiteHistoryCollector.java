package com.flippingfriend.data;

import com.flippingfriend.core.*;
import com.flippingfriend.companion.CompanionClient;
import com.google.gson.Gson;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import javax.inject.*;
import net.runelite.api.*;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetUtil;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.grandexchange.GrandExchangeConfig;

/** Client-thread copies only; optional history work owns its own bounded executor and files. */
@Singleton public final class RuneLiteHistoryCollector {
    private final PluginStorage storage;private final ConfigManager configuration;private final Client client;
    private final CompanionClient companion;private final Gson gson;
    private final Map<String,String> captured=new HashMap<>();
    private ThreadPoolExecutor worker;
    private long nextCapture;
    @Inject public RuneLiteHistoryCollector(PluginStorage storage,ConfigManager configuration,Client client,CompanionClient companion,Gson gson) {
        this.storage=storage;this.configuration=configuration;this.client=client;this.companion=companion;this.gson=gson;
    }
    public synchronized void start() {
        if(worker==null || worker.isShutdown())worker=new ThreadPoolExecutor(1,1,0,TimeUnit.SECONDS,new ArrayBlockingQueue<>(2),
            r->{var t=new Thread(r,"flipping-friend-history-evidence");t.setDaemon(true);return t;});
        nextCapture=0;captured.clear();
    }
    public synchronized void stop(){if(worker!=null)worker.shutdownNow();}
    public void capture(boolean force) {
        long monotonic=System.nanoTime();if(!force && nextCapture!=0 && monotonic-nextCapture<0)return;
        nextCapture=monotonic+TimeUnit.SECONDS.toNanos(30);
        if(worker==null || worker.isShutdown() || client.getGameState()!=GameState.LOGGED_IN)return;
        String profile=configuration.getRSProfileKey(),account=storage.accountId();if(account==null || profile==null)return;
        if(!account.equals(AccountIdentity.fromProfileKey(profile)))return;
        String raw=configuration.getRSProfileConfiguration(GrandExchangeConfig.CONFIG_GROUP,"tradeHistory");
        if(raw==null)raw="[]";
        if(raw.length()>1048576)return;
        var snapshot=new GeHistorySnapshot();snapshot.accountId=account;snapshot.historyJson=raw;snapshot.observedAt=System.currentTimeMillis()/1000;
        var seen=Collections.newSetFromMap(new IdentityHashMap<Widget,Boolean>());var queue=new ArrayDeque<Widget>();
        Widget[] roots=client.getWidgetRoots();
        if(roots!=null)for(var widget:roots)if(widget!=null && WidgetUtil.componentToInterface(widget.getId())==InterfaceID.GE_HISTORY)queue.add(widget);
        while(!queue.isEmpty() && seen.size()<2048) {
            Widget widget=queue.remove();if(!seen.add(widget))continue;
            snapshot.visibleHistory.add(widget.getId()+"|"+widget.getIndex()+"|"+widget.getItemId()+"|"+widget.getItemQuantity()+"|"+Objects.toString(widget.getText(),""));
            for(Widget[] children:new Widget[][]{widget.getChildren(),widget.getDynamicChildren(),widget.getNestedChildren()})if(children!=null)
                for(var child:children)if(child!=null)queue.add(child);
        }
        if(!profile.equals(configuration.getRSProfileKey()) || !account.equals(storage.accountId()))return;
        Path directory=storage.accountDir(account).resolve("history-evidence");
        String signature=sha(account+raw+gson.toJson(snapshot.visibleHistory));
        synchronized(this) {
            snapshot.previousSnapshot=captured.get(account);
            if(signature.equals(snapshot.previousSnapshot) && !force) {
                try{worker.execute(()->deliver(directory,account));}catch(RejectedExecutionException ignored){}return;
            }
            try{worker.execute(()->save(directory,snapshot,signature));captured.put(account,signature);}
            catch(RejectedExecutionException busy){nextCapture=0;}
        }
    }
    private void save(Path directory,GeHistorySnapshot snapshot,String signature) {
        try {
            snapshot.rows();Files.createDirectories(directory);
            Path target=directory.resolve(signature+".json");
            if(!Files.exists(target) && !storage.writeDurably(target,gson.toJson(snapshot).getBytes(StandardCharsets.UTF_8)))return;
            deliver(directory,snapshot.accountId);
        }catch(Exception malformed){synchronized(this){captured.remove(snapshot.accountId,signature);} }
    }
    private void deliver(Path directory,String account) {
        try(var files=Files.list(directory)) {
            for(Path file:files.filter(p->p.getFileName().toString().matches("[0-9a-f]{64}\\.json")).filter(p->!Files.exists(p.resolveSibling(p.getFileName()+".ack"))).limit(8).toArray(Path[]::new)) {
                Path receipt=file.resolveSibling(file.getFileName()+".ack");if(Files.exists(receipt))continue;
                GeHistorySnapshot snapshot=gson.fromJson(Files.readString(file),GeHistorySnapshot.class);
                if(!account.equals(snapshot.accountId) || !companion.publishHistory(snapshot))continue;
                storage.writeDurably(receipt,sha(Files.readString(file)).getBytes(StandardCharsets.US_ASCII));
            }
        }catch(Exception unavailable){/* Supporting evidence cannot interrupt canonical delivery. */}
    }
    private static String sha(String value) {
        try{var b=new StringBuilder();for(byte v:java.security.MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)))b.append(String.format(Locale.ROOT,"%02x",v&255));return b.toString();}
        catch(Exception impossible){throw new IllegalStateException(impossible);}
    }
}
