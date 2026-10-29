package com.flippingfriend.ui;

import com.flippingfriend.model.Suggestion;
import com.google.gson.Gson;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.LoggerFactory;

/** Bounded diagnostic telemetry. No financial identities, authority or delivery depend on it. */
public final class GuidanceTiming {
    private static final AtomicLong dropped = new AtomicLong();
    private static final ThreadPoolExecutor logger = new ThreadPoolExecutor(1, 1, 30, TimeUnit.SECONDS,
        new ArrayBlockingQueue<>(256), work -> { Thread t=new Thread(work,"flipping-guidance-telemetry"); t.setDaemon(true); return t; },
        (work, executor) -> dropped.incrementAndGet());
    static { logger.allowCoreThreadTimeOut(true); }
    private final Map<String,Object> values=new LinkedHashMap<>();
    private final Map<String,Long> stages=new LinkedHashMap<>();
    private final long started=System.nanoTime();
    private boolean delivered,painted;

    public GuidanceTiming(String id,String source) {
        values.put("schemaVersion",2); values.put("trace",id); values.put("source",source);
        stages.put("request",started);
    }
    public synchronized void mark(String name) { stages.putIfAbsent(name,System.nanoTime()); }
    public synchronized void acknowledged(long nanos) { if(nanos>0)stages.put("durableAck",nanos); }
    public synchronized void context(long generation,long revision) {
        values.put("generation",generation);values.put("revision",revision);
    }
    public synchronized void context(String account,long generation,long revision) {
        context(generation,revision);values.put("account",account);
    }
    public synchronized void delivery(String outcome,Suggestion action) {
        if(delivered)return;delivered=true;mark("delivery");values.put("deliveryOutcome",outcome);
        if(action!=null) {
            values.put("action",action.getType().name());values.put("item",action.getItemId());
            values.put("quantity",action.getQuantity());values.put("price",action.getPrice());values.put("slot",action.getSlot());
            // A repeated presentation is not necessarily a distinct decision. Keep source identity
            // available to the audit without changing the financial event or recommendation key.
            values.put("recommendation",action.getRecommendationId());values.put("offer",action.getMaintenanceOfferIdentity());
            values.put("saleSource",action.getSaleRepriceSource());values.put("origin",action.getContinuationOriginIdentity());
        }
        emit("DELIVERY");
    }
    public synchronized void paint(String outcome) {
        if(painted)return;painted=true;mark("paintEnd");values.put("paintOutcome",outcome);emit("PAINT");
    }
    synchronized Map<String,Object> diagnosticSnapshot() { return new LinkedHashMap<>(values); }
    private void emit(String event) {
        Map<String,Object> record=new LinkedHashMap<>(values);record.put("event",event);
        Map<String,Long> offsets=new LinkedHashMap<>();stages.forEach((key,value)->offsets.put(key,(value-started)/1000));
        record.put("stageMicros",offsets);
        // Serialization and log IO never run on the EDT or account worker. Rejection counts stay
        // visible; missing telemetry cannot qualify a release or make an order fail.
        logger.execute(()->{record.put("droppedTelemetryRecords",dropped.get());
            LoggerFactory.getLogger(GuidanceTiming.class).info("Guidance timing: {}",new Gson().toJson(record));});
    }
}
