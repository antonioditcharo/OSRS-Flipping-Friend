package com.flippingfriend.companion;

import com.flippingfriend.core.*;
import com.google.gson.Gson;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;

/** Committed evidence prefixes use independent checkpoints, never the player's delivery outbox. */
final class PilotReplication implements AutoCloseable {
    static final class Checkpoint {String streamId=UUID.randomUUID().toString(),hash=PilotHashes.GENESIS;long cursor;}
    private final Path root;private final PilotGateway gateway;private final Gson gson=new Gson();
    private final Map<String,Path> accounts=new LinkedHashMap<>();private int turn;
    private final ScheduledExecutorService worker=Executors.newSingleThreadScheduledExecutor(r->{var t=new Thread(r,"pilot-evidence-replica");t.setDaemon(true);return t;});
    PilotReplication(Path root,PilotGateway gateway) {
        this.root=root;this.gateway=gateway;worker.scheduleWithFixedDelay(this::tick,10,1,TimeUnit.SECONDS);
    }
    synchronized void register(String account,Path database){if(accounts.size()<4096)accounts.putIfAbsent(AccountIdentity.require(account),database);}
    private void tick() {
        if(!gateway.config().enabled)return;
        String account;Path database;String namespace;
        synchronized(this) {
            if(accounts.isEmpty())return;var ids=new ArrayList<>(accounts.keySet());
            int index=Math.floorMod(turn++,Math.multiplyExact(ids.size(),3));account=ids.get(index/3);database=accounts.get(account);
            namespace=new String[]{"OFFER","EFFECT","AMENDMENT"}[index%3];
        }
        try{replicate(account,database,namespace);}catch(Exception unavailable){/* A remote outage cannot withhold local acknowledgements or advice. */}
    }
    void replicate(String account,Path database,String namespace)throws Exception {
        Path file=root.resolve("hosted-sync").resolve(account).resolve(namespace+".json");
        Checkpoint checkpoint=Files.isRegularFile(file)?gson.fromJson(Files.readString(file),Checkpoint.class):new Checkpoint();
        if(checkpoint==null || checkpoint.cursor<0 || checkpoint.streamId==null || checkpoint.hash==null || !checkpoint.hash.matches("[0-9a-f]{64}"))
            throw new IllegalStateException("Invalid replica checkpoint; canonical outbox remains unchanged");
        var batch=new PilotProtocol.EvidenceBatch();batch.accountId=account;batch.installationId=gateway.config().installationId;
        batch.generation=gateway.config().generation;batch.streamId=namespace+"-"+checkpoint.streamId;
        batch.after=checkpoint.cursor;batch.previousHash=checkpoint.hash;
        String query="OFFER".equals(namespace)?"SELECT id,event_id,payload FROM event_log WHERE id>? AND event_id IS NOT NULL AND event_id<>'' ORDER BY id LIMIT 100"
            :"EFFECT".equals(namespace)?"SELECT * FROM position_event WHERE id>? ORDER BY id LIMIT 100"
            :"SELECT rowid AS replica_cursor,* FROM sale_cost_amendment WHERE rowid>? ORDER BY rowid LIMIT 100";
        int size=0;
        try(var read=DriverManager.getConnection("jdbc:sqlite:"+database.toAbsolutePath().toUri().toASCIIString()+"?mode=ro");var statement=read.prepareStatement(query)) {
            statement.setLong(1,checkpoint.cursor);
            try(var rows=statement.executeQuery()) {
                while(rows.next()) {
                    var row=new PilotProtocol.EvidenceRow();row.namespace=namespace;row.cursor=rows.getLong(1);
                    if("OFFER".equals(namespace)) {
                        row.eventId=rows.getString(2);row.sourceEventId=row.eventId;row.payload=rows.getString(3);
                        var source=gson.fromJson(row.payload,OfferEvent.class);
                        if(source.getAccountId()==null)row.namespace="UNASSIGNED_OFFER";
                        else if(!account.equals(source.getAccountId()))throw new IllegalStateException("Foreign retained source");
                    }
                    else {
                        var values=new LinkedHashMap<String,Object>();
                        for(int k=1;k<=rows.getMetaData().getColumnCount();k++)values.put(rows.getMetaData().getColumnName(k),rows.getObject(k));
                        row.sourceEventId=rows.getString("EFFECT".equals(namespace)?"source_event_id":"sale_source_event_id");
                        values.put("source_event_id",row.sourceEventId);row.eventId="EFFECT".equals(namespace)?Long.toString(row.cursor):row.sourceEventId;
                        row.payload=gson.toJson(values);
                        try(var scope=read.prepareStatement("SELECT payload FROM event_log WHERE event_id=? LIMIT 1")) {
                            scope.setString(1,row.sourceEventId);try(var source=scope.executeQuery()) {
                                if(!source.next())row.namespace="UNASSIGNED_"+namespace;
                                else {
                                    var original=gson.fromJson(source.getString(1),OfferEvent.class);
                                    if(original.getAccountId()==null)row.namespace="UNASSIGNED_"+namespace;
                                    else if(!account.equals(original.getAccountId()))throw new IllegalStateException("Foreign retained effect");
                                }
                            }
                        }
                    }
                    if(size+row.payload.length()>524288)break;size+=row.payload.length();batch.rows.add(row);
                }
            }
        }
        if(batch.rows.isEmpty())return;
        batch.hash=PilotHashes.sha(batch.previousHash+gson.toJson(batch.rows));
        var response=gateway.post("evidence",gson.toJson(batch));if(response.statusCode()!=200)return;
        var ack=gson.fromJson(response.body(),PilotProtocol.EvidenceAck.class);long end=batch.rows.get(batch.rows.size()-1).cursor;
        if(ack==null || !PilotProtocol.VERSION.equals(ack.protocol) || !account.equals(ack.accountId)
                || !batch.installationId.equals(ack.installationId) || batch.generation!=ack.generation
                || !batch.streamId.equals(ack.streamId) || end!=ack.cursor || !batch.hash.equals(ack.hash))return;
        checkpoint.cursor=end;checkpoint.hash=batch.hash;Files.createDirectories(file.getParent());
        Path temporary=Files.createTempFile(file.getParent(),"replica-",".tmp");
        try {
            byte[] bytes=gson.toJson(checkpoint).getBytes(java.nio.charset.StandardCharsets.UTF_8);
            try(var channel=java.nio.channels.FileChannel.open(temporary,StandardOpenOption.WRITE)) {
                var buffer=java.nio.ByteBuffer.wrap(bytes);while(buffer.hasRemaining())channel.write(buffer);channel.force(true);
            }
            Files.move(temporary,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
        }finally{Files.deleteIfExists(temporary);}
    }
    @Override public void close(){worker.shutdownNow();}
}
