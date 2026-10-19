package com.flippingfriend.data;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.LongSupplier;

/** Focused chart reads cannot block trading history work or replace its cached inputs. */
final class GraphHistoryCache implements AutoCloseable {
    interface Fetcher {List<Candle> fetch(int item,String step)throws Exception;}
    private final Fetcher warm,upstream;
    private final LongSupplier clock;
    private final Map<String,Entry> entries=new LinkedHashMap<String,Entry>(16,.75f,true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String,GraphHistoryCache.Entry> e){return size()>32;}
    };
    private ExecutorService worker;
    private long generation;
    private int focusedItem;
    private boolean active=true;
    GraphHistoryCache(Fetcher warm,Fetcher upstream){this(warm,upstream,System::currentTimeMillis);}
    GraphHistoryCache(Fetcher warm,Fetcher upstream,LongSupplier clock){this.warm=warm;this.upstream=upstream;this.clock=clock;}
    synchronized void start(){active=true;}
    synchronized List<Candle> get(int item,String step) {
        int seconds=seconds(step);if(item<=0)throw new IllegalArgumentException("Invalid graph item");
        if(!active)return List.of();
        if(focusedItem!=item) {
            focusedItem=item;String prefix=item+"@";
            entries.entrySet().removeIf(e->e.getValue().pending && !e.getKey().startsWith(prefix));
        }
        String key=item+"@"+step;Entry old=entries.get(key);long now=clock.getAsLong();
        if(old!=null && (old.pending || now<old.retryAt))return old.bars;
        Entry pending=new Entry(old==null?List.of():old.bars,true,Long.MAX_VALUE,null);entries.put(key,pending);
        if(worker==null)worker=Executors.newSingleThreadExecutor(task->{Thread t=new Thread(task,"flipping-friend-graph-history");t.setDaemon(true);return t;});
        long requestGeneration=generation;
        worker.execute(()->{
            synchronized(GraphHistoryCache.this) {
                if(!active || generation!=requestGeneration || entries.get(key)!=pending)return;
            }
            List<Candle> result=null;String problem=null;boolean succeeded=false;
            try {
                try {result=warm.fetch(item,step);}catch(Exception unavailable){result=null;}
                if(!fresh(result,seconds,clock.getAsLong()/1000))result=upstream.fetch(item,step);
                if(result==null || result.isEmpty())problem="No market history is available yet. Retrying shortly.";
                else {result=List.copyOf(result);succeeded=true;}
            }catch(Exception unavailable){problem="Market history is temporarily unavailable. Retrying shortly.";}
            finally {
                synchronized(GraphHistoryCache.this) {
                    if(active && generation==requestGeneration && entries.get(key)==pending) {
                        boolean failed=!succeeded;
                        List<Candle> bars=failed?pending.bars:result;
                        long ttl=failed?120000:Math.max(60000,Math.min(3600000,seconds*1000L/12));
                        entries.put(key,new Entry(bars,false,clock.getAsLong()+ttl,failed?
                            Objects.toString(problem,"Market history is temporarily unavailable. Retrying shortly."):null));
                    }
                }
            }
        });
        return pending.bars;
    }
    synchronized String problem(int item,String step){Entry e=entries.get(item+"@"+step);return e==null?null:e.problem;}
    @Override public synchronized void close(){active=false;generation++;entries.clear();if(worker!=null){worker.shutdownNow();worker=null;}}
    private static boolean fresh(List<Candle> rows,int bucket,long now) {
        if(rows==null || rows.isEmpty())return false;
        long newest=0;
        for(Candle c:rows) {
            if(c==null || c.getTimestamp()<0 || c.getTimestamp()>now)return false;
            if(c.getTimestamp()<=now-bucket)newest=Math.max(newest,c.getTimestamp()+bucket);
        }
        return newest>0 && now-newest<=bucket;
    }
    static int seconds(String step) {
        switch(step){case "5m":return 300;case "1h":return 3600;case "6h":return 21600;case "24h":return 86400;
            default:throw new IllegalArgumentException("Unsupported graph resolution");}
    }
    private static final class Entry {
        final List<Candle> bars;final boolean pending;final long retryAt;final String problem;
        Entry(List<Candle> bars,boolean pending,long retryAt,String problem){this.bars=bars;this.pending=pending;this.retryAt=retryAt;this.problem=problem;}
    }
}
