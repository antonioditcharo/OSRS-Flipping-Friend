package com.flippingfriend.companion;

import com.flippingfriend.core.*;
import com.google.gson.Gson;
import java.nio.file.*;
import java.sql.*;

/** A replica of evidence, deliberately separate from every authoritative account database. */
final class PilotEvidenceStore implements AutoCloseable {
    private final Connection db;
    private final Gson gson=new Gson();
    PilotEvidenceStore(Path path)throws Exception {
        Files.createDirectories(path.toAbsolutePath().getParent());
        db=DriverManager.getConnection("jdbc:sqlite:"+path.toAbsolutePath());
        try(var s=db.createStatement()) {
            s.execute("PRAGMA journal_mode=WAL");s.execute("PRAGMA busy_timeout=2000");s.execute("PRAGMA synchronous=FULL");
            s.execute("CREATE TABLE IF NOT EXISTS streams (user_id TEXT,account_id TEXT,installation_id TEXT,stream_id TEXT,generation INTEGER,cursor INTEGER,hash TEXT,PRIMARY KEY(user_id,account_id,installation_id,stream_id))");
            s.execute("CREATE TABLE IF NOT EXISTS evidence (user_id TEXT,account_id TEXT,namespace TEXT,event_id TEXT,payload TEXT NOT NULL,PRIMARY KEY(user_id,account_id,namespace,event_id))");
            s.execute("CREATE TABLE IF NOT EXISTS capacity_demand (user_id TEXT,account_id TEXT,payload TEXT NOT NULL,PRIMARY KEY(user_id,account_id))");
        }
    }
    synchronized void saveDemand(String user,PilotProtocol.AllocationRequest input) {
        saveDemands(java.util.List.of(new PilotCoordinator.Request(user,input)));
    }
    synchronized void saveDemands(java.util.List<PilotCoordinator.Request> batch) {
        try {
            // Only the last authenticated frame and its outstanding quantities survive restart.
            // Candidate advice leases expire; this record never becomes financial exposure.
            db.setAutoCommit(false);
            try(var statement=db.prepareStatement("INSERT OR REPLACE INTO capacity_demand VALUES(?,?,?)")) {
                for(var request:batch) {
                    var input=request.input;var retained=new PilotProtocol.AllocationRequest();
                    retained.protocol=input.protocol;retained.accountId=input.accountId;retained.installationId=input.installationId;
                    retained.context=input.context;retained.generation=input.generation;retained.frameOrder=input.frameOrder;
                    retained.observedAt=input.observedAt;retained.marketAt=input.marketAt;retained.coordinate=input.coordinate;
                    retained.preferredProfit=input.preferredProfit;retained.demand=input.demand;
                    statement.setString(1,request.user);statement.setString(2,input.accountId);statement.setString(3,gson.toJson(retained));statement.addBatch();
                }
                statement.executeBatch();db.commit();
            }
        }catch(Exception unavailable){try{db.rollback();}catch(Exception rollback){unavailable.addSuppressed(rollback);}throw new IllegalStateException("Outstanding demand persistence unavailable",unavailable);}
        finally{try{db.setAutoCommit(true);}catch(Exception failure){throw new IllegalStateException(failure);}}
    }
    synchronized void restoreDemand(PilotCoordinator coordinator)throws Exception {
        try(var statement=db.createStatement();var rows=statement.executeQuery("SELECT user_id,payload FROM capacity_demand LIMIT 4097")) {
            int count=0;while(rows.next()) {
                if(++count>4096)throw new IllegalStateException("Retained coordination capacity exceeded");
                var input=gson.fromJson(rows.getString(2),PilotProtocol.AllocationRequest.class);
                input.candidates=java.util.List.of();input.validate(Math.max(input.observedAt,input.marketAt));
                coordinator.restore(rows.getString(1),input);
            }
        }
    }
    synchronized PilotProtocol.EvidenceAck accept(String user,PilotProtocol.EvidenceBatch batch)throws Exception {
        AccountIdentity.require(batch.accountId);
        if(!PilotProtocol.VERSION.equals(batch.protocol) || batch.installationId==null || batch.streamId==null
                || !batch.streamId.matches("[a-zA-Z0-9._-]{1,100}") || batch.generation<1 || batch.after<0
                || batch.rows==null || batch.rows.isEmpty() || batch.rows.size()>100
                || batch.previousHash==null || !batch.previousHash.matches("[0-9a-f]{64}")
                || !PilotHashes.sha(batch.previousHash+gson.toJson(batch.rows)).equals(batch.hash))
            throw new IllegalArgumentException("invalid evidence envelope");
        long end=batch.after;
        for(var row:batch.rows) {
            if(row==null || row.cursor<=end || row.eventId==null || row.eventId.length()>200
                    || row.payload==null || row.payload.length()>262144)throw new IllegalArgumentException("invalid evidence ordering");
            if("OFFER".equals(row.namespace)) {
                OfferEvent source=gson.fromJson(row.payload,OfferEvent.class);
                if(source==null || !batch.accountId.equals(source.getAccountId()) || !row.eventId.equals(source.getEventId()))
                    throw new IllegalArgumentException("foreign source evidence");
            }else if("UNASSIGNED_OFFER".equals(row.namespace)) {
                var source=gson.fromJson(row.payload,OfferEvent.class);
                if(source==null || source.getAccountId()!=null || !row.eventId.equals(source.getEventId()))
                    throw new IllegalArgumentException("Unassigned evidence must retain its missing ownership");
            }else if(!java.util.Set.of("EFFECT","AMENDMENT","UNASSIGNED_EFFECT","UNASSIGNED_AMENDMENT").contains(row.namespace)
                    || !gson.fromJson(row.payload,com.google.gson.JsonObject.class).has("source_event_id"))
                throw new IllegalArgumentException("unknown evidence namespace");
            end=row.cursor;
        }
        db.setAutoCommit(false);
        try {
            long generation=0,cursor=0;String hash=PilotHashes.GENESIS;
            try(var s=db.prepareStatement("SELECT generation,cursor,hash FROM streams WHERE user_id=? AND account_id=? AND installation_id=? AND stream_id=?")) {
                bindStream(s,user,batch);try(var r=s.executeQuery()){if(r.next()){generation=r.getLong(1);cursor=r.getLong(2);hash=r.getString(3);}}
            }
            if(batch.generation<generation)throw new IllegalArgumentException("stale replica generation");
            if(cursor!=end || !hash.equals(batch.hash)) {
                if(cursor!=batch.after || !hash.equals(batch.previousHash))throw new IllegalArgumentException("replica prefix mismatch");
                for(var row:batch.rows) {
                    try(var s=db.prepareStatement("SELECT payload FROM evidence WHERE user_id=? AND account_id=? AND namespace=? AND event_id=?")) {
                        s.setString(1,user);s.setString(2,batch.accountId);s.setString(3,row.namespace);s.setString(4,row.eventId);
                        try(var r=s.executeQuery()){if(r.next() && !row.payload.equals(r.getString(1)))throw new IllegalArgumentException("source identity changed");}
                    }
                    try(var s=db.prepareStatement("INSERT OR IGNORE INTO evidence VALUES(?,?,?,?,?)")) {
                        s.setString(1,user);s.setString(2,batch.accountId);s.setString(3,row.namespace);s.setString(4,row.eventId);s.setString(5,row.payload);s.executeUpdate();
                    }
                }
            }
            try(var s=db.prepareStatement("INSERT OR REPLACE INTO streams VALUES(?,?,?,?,?,?,?)")) {
                bindStream(s,user,batch);s.setLong(5,batch.generation);s.setLong(6,end);s.setString(7,batch.hash);s.executeUpdate();
            }
            db.commit();
        }catch(Exception failure){db.rollback();throw failure;}finally{db.setAutoCommit(true);}
        var ack=new PilotProtocol.EvidenceAck();ack.accountId=batch.accountId;ack.installationId=batch.installationId;
        ack.streamId=batch.streamId;ack.generation=batch.generation;ack.cursor=end;ack.hash=batch.hash;return ack;
    }
    private static void bindStream(PreparedStatement s,String user,PilotProtocol.EvidenceBatch b)throws SQLException {
        s.setString(1,user);s.setString(2,b.accountId);s.setString(3,b.installationId);s.setString(4,b.streamId);
    }
    @Override public void close()throws SQLException{db.close();}
}
