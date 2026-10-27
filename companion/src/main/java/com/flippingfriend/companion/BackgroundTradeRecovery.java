package com.flippingfriend.companion;

import com.flippingfriend.core.*;
import com.google.gson.Gson;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;

/** Optional historical work never owns the event/plan worker or the canonical read connection. */
final class BackgroundTradeRecovery implements AutoCloseable {
    static final String INVALID_SOURCE_JSON="event_id IS NOT NULL AND event_id<>'' AND NOT json_valid(payload)";
    // Equality/nonblank estimates can otherwise choose the much larger identity index and decode
    // every historical payload. SqliteStore creates this exact partial index before registration.
    // An unsupported old replica refuses optional support instead of falling back to that scan.
    static final String INVALID_SOURCE_LOOKUP_SQL="SELECT 1 FROM event_log INDEXED BY event_log_invalid_source_json WHERE "+INVALID_SOURCE_JSON+" LIMIT 1";
    static final String TERMINAL_SOURCES="event_id IS NOT NULL AND event_id<>'' AND CASE WHEN json_valid(payload) THEN "
        +"json_extract(payload,'$.filledQuantity')>0 AND (json_extract(payload,'$.filledQuantity')=json_extract(payload,'$.totalQuantity') "
        +"OR event_type IN ('CANCELLED_BUY','CANCELLED_SELL')) ELSE 0 END";
    private static final class Account {
        Path database;CompanionService service;int next;long nextScan;
        long rejectedRevision=-1,rejectedAmendments=-1;
        final Set<String> rejectedSources=new HashSet<>();
        String supportSnapshot;long supportRevision=-1,supportCursor=-1;
    }
    private final Path root,features;
    private final java.util.function.BooleanSupplier featureGate;
    private final Map<String,Account> accounts=new LinkedHashMap<>();
    private final Gson gson=new Gson();
    private final ScheduledExecutorService worker=Executors.newSingleThreadScheduledExecutor(r->{
        var t=new Thread(r,"flipping-friend-background-recovery");t.setDaemon(true);t.setPriority(Thread.MIN_PRIORITY);return t;});
    private int turn;
    BackgroundTradeRecovery(Path root) {
        this(root,()->true);
    }
    BackgroundTradeRecovery(Path root,java.util.function.BooleanSupplier featureGate) {
        this.root=root;this.featureGate=featureGate;features=root.resolve("features.properties");
        worker.scheduleWithFixedDelay(this::tick,30,5,TimeUnit.SECONDS);
    }
    synchronized void register(String id,Path database,CompanionService service) {
        if(accounts.containsKey(id))return;if(accounts.size()>=4096)return;
        var account=new Account();account.database=database;account.service=service;accounts.put(id,account);
    }
    String accept(GeHistorySnapshot snapshot)throws Exception {
        snapshot.rows();long now=System.currentTimeMillis()/1000;
        if(snapshot.observedAt>now || now-snapshot.observedAt>7*86400)throw new IllegalArgumentException("history capture age");
        String id=snapshot.evidenceId();Path directory=root.resolve("trade-evidence").resolve(AccountIdentity.require(snapshot.accountId));
        Files.createDirectories(directory);Path file=directory.resolve(id+".json");
        if(!Files.exists(file))atomic(file,gson.toJson(snapshot));return id;
    }
    private boolean enabled() {
        try {
            if(!featureGate.getAsBoolean())return false;
            if(!Files.isRegularFile(features))return true;
            var p=new Properties();try(var reader=Files.newBufferedReader(features)){p.load(reader);}
            return Boolean.parseBoolean(p.getProperty("automaticRecovery","true"));
        }catch(Exception invalid){return false;}
    }
    private void tick() {
        if(!enabled())return;
        String id;Account account;
        synchronized(this) {
            if(accounts.isEmpty())return;var ids=new ArrayList<>(accounts.keySet());
            id=ids.get(Math.floorMod(turn++,ids.size()));account=accounts.get(id);
        }
        long now=System.currentTimeMillis()/1000;if(now<account.nextScan)return;account.nextScan=now+120;
        try {
            long externalRevision=account.service.recoveryExternalRevision();
            var candidates=new ArrayList<String>();List<SaleCostAmendments.Prepared> prepared=List.of();
            try(var read=DriverManager.getConnection("jdbc:sqlite:"+account.database.toAbsolutePath().toUri().toASCIIString()+"?mode=ro")) {
                read.setAutoCommit(false);
                long amendments;
                try(var s=read.createStatement();var rows=s.executeQuery("SELECT COUNT(*) FROM sale_cost_amendment")){rows.next();amendments=rows.getLong(1);}
                if(account.rejectedRevision!=externalRevision || account.rejectedAmendments!=amendments) {
                    account.rejectedSources.clear();account.rejectedRevision=externalRevision;account.rejectedAmendments=amendments;
                }
                try(var s=read.createStatement();var rows=s.executeQuery("SELECT p.source_event_id FROM position_event p LEFT JOIN sale_cost_amendment a ON a.sale_source_event_id=p.source_event_id WHERE p.effect_type='DISPOSE' AND p.profit_lower IS NULL AND a.sale_source_event_id IS NULL AND p.transition_reason='Sale lacks complete observed acquisition coverage' ORDER BY p.id DESC LIMIT 100")) {
                    while(rows.next())candidates.add(rows.getString(1));
                }
                candidates.removeAll(account.rejectedSources);
                if(!candidates.isEmpty()) {
                    String source=candidates.get(Math.floorMod(account.next++,candidates.size()));
                    try{prepared=SaleCostAmendments.prepare(read,id,List.of(source),now);}
                    catch(IllegalArgumentException|IllegalStateException unresolved){
                        // Canonical appends cannot change a failed earlier immutable source/effect
                        // prefix. External edits or new amendments invalidate these bounded negatives.
                        if(account.rejectedSources.size()<100)account.rejectedSources.add(source);
                        writeStatus(id,now,"Investigating recorded trade evidence",source,unresolved.getMessage());
                    }
                }
                // A support report cannot establish missing executions, assignment or FIFO ownership.
                matchHistory(read,id,now,account,externalRevision);
                read.rollback();
            }
            for(var amendment:prepared){amendment.expectedExternalRevision=externalRevision;account.service.commitRecovery(amendment);}
            if(!prepared.isEmpty())writeStatus(id,now,"Source-proven cost recovered",prepared.get(0).id,"Original sources, quantities, tax rules and risk anchor retained.");
        }catch(Exception unavailable) {
            try{writeStatus(id,now,"Background evidence review will retry",null,unavailable.getClass().getSimpleName());}catch(Exception ignored){}
        }
    }
    private void matchHistory(Connection read,String id,long now,Account account,long externalRevision)throws Exception {
        Path directory=root.resolve("trade-evidence").resolve(id);if(!Files.isDirectory(directory))return;
        Path latest;
        try(var files=Files.list(directory)) {
            latest=files.filter(p->p.getFileName().toString().matches("[0-9a-f]{64}\\.json"))
                .max(Comparator.comparingLong(p->{try{return Files.getLastModifiedTime(p).toMillis();}catch(Exception error){return 0;}})).orElse(null);
        }
        if(latest==null)return;var snapshot=gson.fromJson(Files.readString(latest),GeHistorySnapshot.class);
        if(!id.equals(snapshot.accountId))throw new IllegalArgumentException("history account mismatch");
        String snapshotId=snapshot.evidenceId();long cursor;
        try(var s=read.createStatement();var rows=s.executeQuery(INVALID_SOURCE_LOOKUP_SQL)) {
            if(rows.next())throw new IllegalStateException("Invalid canonical source JSON; support remains unqualified");
        }
        try(var s=read.createStatement();var rows=s.executeQuery("SELECT COALESCE(MAX(id),0) FROM event_log WHERE "+TERMINAL_SOURCES)){rows.next();cursor=rows.getLong(1);}
        if(snapshotId.equals(account.supportSnapshot) && account.supportRevision==externalRevision && account.supportCursor==cursor)return;
        var terminals=new HashMap<String,OfferEvent>();
        try(var s=read.createStatement();var rows=s.executeQuery("SELECT payload FROM event_log WHERE "+TERMINAL_SOURCES+" ORDER BY id")) {
            while(rows.next()) {
                var e=gson.fromJson(rows.getString(1),OfferEvent.class);
                if(e!=null && id.equals(e.getAccountId()) && e.getOfferIdentity()!=null && e.getFilledQuantity()>0
                        && (e.getFilledQuantity()==e.getTotalQuantity() || e.getEventType().startsWith("CANCELLED")))
                    terminals.put(e.getSessionId()+"/"+e.getSlot()+"/"+e.getOfferIdentity()+"/"+e.getFirstSeenAt(),e);
            }
        }
        var report=new ArrayList<Map<String,Object>>();
        for(var row:snapshot.rows()) {
            var matches=new ArrayList<String>();
            for(var e:terminals.values())if(e.getItemId()==row.itemId && e.isBuying()==row.buying && e.getFilledQuantity()==row.quantity
                    && e.getSpent()>=row.grossLower() && e.getSpent()<=row.grossUpper()
                    && row.recordedAt>=e.getFirstSeenAt() && Math.abs(row.recordedAt-e.getObservedAt())<=2)matches.add(e.getEventId());
            var result=new LinkedHashMap<String,Object>();result.put("occurrence",row.occurrence);result.put("itemId",row.itemId);
            result.put("candidateSourceIds",matches);result.put("status",matches.size()==1?"UNIQUE_SUPPORT":matches.isEmpty()?"UNMATCHED":"AMBIGUOUS");
            result.put("authorizesAccounting",false);report.add(result);
        }
        atomic(directory.resolve("support-report.json"),gson.toJson(Map.of("accountId",id,"observedAt",now,"snapshotId",snapshot.evidenceId(),"rows",report)));
        account.supportSnapshot=snapshotId;account.supportRevision=externalRevision;account.supportCursor=cursor;
    }
    private void writeStatus(String id,long now,String status,String source,String detail)throws Exception {
        var result=new LinkedHashMap<String,Object>();result.put("accountId",id);result.put("observedAt",now);
        result.put("status",status);result.put("sourceEventId",source);result.put("detail",detail);
        atomic(root.resolve("trade-evidence").resolve(id).resolve("status.json"),gson.toJson(result));
    }
    String status(String id)throws Exception {
        Path file=root.resolve("trade-evidence").resolve(AccountIdentity.require(id)).resolve("status.json");
        return Files.isRegularFile(file)?Files.readString(file):gson.toJson(Map.of("accountId",id,"status","Background trade review is ready"));
    }
    private static void atomic(Path target,String value)throws Exception {
        Files.createDirectories(target.getParent());Path temp=Files.createTempFile(target.getParent(),"evidence-",".tmp");
        try {
            try(var channel=java.nio.channels.FileChannel.open(temp,StandardOpenOption.WRITE)) {
                var b=java.nio.ByteBuffer.wrap(value.getBytes(StandardCharsets.UTF_8));while(b.hasRemaining())channel.write(b);channel.force(true);
            }
            Files.move(temp,target,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
        }finally{Files.deleteIfExists(temp);}
    }
    @Override public void close(){worker.shutdownNow();}
}
