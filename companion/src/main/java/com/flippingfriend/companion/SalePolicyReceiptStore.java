package com.flippingfriend.companion;

import com.flippingfriend.core.*;
import com.google.gson.Gson;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.*;
import java.util.*;

/** Prospective authorization metadata only. Issuance never writes executions or accounting. */
final class SalePolicyReceiptStore {
    static final String TABLE="sale_policy_receipt";
    // Include the exact predicate of the durable partial identity index. Equality alone cannot use it.
    static final String SOURCE_LOOKUP_SQL="SELECT id,payload,event_id,event_type,observed_at,correlation_id FROM event_log "
        +"WHERE event_id=? AND event_id IS NOT NULL AND event_id<>''";
    static final String BINDING_ROW_SQL="SELECT id FROM event_log WHERE event_id=? AND event_id IS NOT NULL AND event_id<>''";
    static final String ACCOUNT_SOURCE_SQL="SELECT id,payload,observed_at FROM event_log WHERE event_type='ACCOUNT_STATE' AND correlation_id=? ORDER BY id";
    static final String ACCOUNT_CORRELATION_SQL="SELECT COUNT(*) FROM event_log WHERE event_type='ACCOUNT_STATE' AND correlation_id=?";
    private static final Gson JSON=new Gson();
    static void initialize(Statement sql)throws Exception {
        sql.execute("CREATE TABLE IF NOT EXISTS "+TABLE+" (receipt_id TEXT PRIMARY KEY,account_id TEXT NOT NULL,account_row_id INTEGER NOT NULL,source_event_id TEXT NOT NULL,issued_after_row INTEGER NOT NULL,payload TEXT NOT NULL)");
        sql.execute("CREATE INDEX IF NOT EXISTS event_log_account_correlation ON event_log(correlation_id) WHERE event_type='ACCOUNT_STATE'");
    }
    static final class Evidence {
        final Map<String,SalePolicyReceipt> receipts=new HashMap<>();
        final Map<String,Long> issuedAfter=new HashMap<>(),eventRows=new HashMap<>();
        boolean qualifies(AcceptedSalePolicyProof proof,OfferEvent binding) {
            if(proof==null || binding==null || !proof.qualifiesObservation(binding))return false;
            var receipt=proof.getReceipt();
            return receipt!=null && receipt.equals(receipts.get(receipt.getReceiptId()))
                && eventRows.getOrDefault(binding.getEventId(),0L)>issuedAfter.getOrDefault(receipt.getReceiptId(),Long.MAX_VALUE);
        }
    }
    static SalePolicyReceipt issue(Connection db,String request,AccountSnapshot settings,OfferEvent source,
            long progress,int price,int quantity,boolean cut,long now,long patience,SqliteStore.TransactionCheckpoint checkpoint)throws Exception {
        UUID.fromString(request);
        if(settings==null || source==null || !Objects.equals(settings.getAccountId(),source.getAccountId()))
            throw new IllegalArgumentException("Sale policy account is unavailable");
        boolean auto=db.getAutoCommit();if(!auto)throw new IllegalStateException("Receipt issuance requires its own transaction");
        db.setAutoCommit(false);
        try {
            try(var query=db.prepareStatement("SELECT account_id,account_row_id,source_event_id,issued_after_row,payload FROM "+TABLE+" WHERE receipt_id=?")) {
                query.setString(1,request);try(var rows=query.executeQuery()) {
                    if(rows.next()) {
                        var old=JSON.fromJson(rows.getString(5),SalePolicyReceipt.class);
                        if(old==null || !old.isWellFormed() || !old.matchesSettings(settings) || !old.matchesSource(source)
                            || !old.matchesOrder(source.getItemId(),price,quantity) || old.isLossCut()!=cut
                            || old.getProgressAt()!=progress || old.getPatienceSeconds()!=patience)
                            throw new IllegalArgumentException("Sale receipt request identity changed");
                        if(!retainedReceipt(db,request,settings.getAccountId(),rows.getString(1),rows.getLong(2),
                                rows.getString(3),rows.getLong(4),old,source) || !currentSource(db,source))
                            throw new IllegalStateException("Retained sale receipt source changed");
                        db.commit();return old;
                    }
                }
            }
            long rowId=0;String payload=null;
            try(var query=db.prepareStatement(ACCOUNT_SOURCE_SQL)) {
                query.setString(1,settings.getCorrelationId());try(var rows=query.executeQuery()) {
                    if(rows.next()){rowId=rows.getLong(1);payload=rows.getString(2);if(rows.getLong(3)!=settings.getObservedAt())throw new IllegalStateException("Account source clock changed");}
                    if(rows.next())throw new IllegalStateException("Ambiguous sale policy account source");
                }
            }
            if(rowId<=0 || payload==null)throw new IllegalStateException("Sale policy account source is missing");
            if(!exactSettings(payload))throw new IllegalStateException("Malformed sale policy settings");
            var recorded=JSON.fromJson(payload,AccountSnapshot.class);
            var receipt=SalePolicyReceipt.issue(request,rowId,hash(payload),settings,source,progress,price,quantity,cut,now,patience);
            if(!receipt.matchesSettings(recorded) || !validReadSet(db,recorded,source,rowId))
                throw new IllegalStateException("Sale policy account evidence changed");
            long sourceRow=sourceRow(db,source);
            if(!currentSource(db,source))throw new IllegalStateException("Sale policy offer changed");
            long tail=0;try(var query=db.createStatement();var rows=query.executeQuery("SELECT COALESCE(MAX(id),0) FROM event_log")){if(rows.next())tail=rows.getLong(1);}
            if(sourceRow<=0 || sourceRow>tail || rowId>tail)throw new IllegalStateException("Sale policy source ordering is unavailable");
            try(var insert=db.prepareStatement("INSERT INTO "+TABLE+"(receipt_id,account_id,account_row_id,source_event_id,issued_after_row,payload) VALUES(?,?,?,?,?,?)")) {
                insert.setString(1,request);insert.setString(2,settings.getAccountId());insert.setLong(3,rowId);
                insert.setString(4,source.getEventId());insert.setLong(5,tail);insert.setString(6,JSON.toJson(receipt));insert.executeUpdate();
            }
            checkpoint.reached("SALE_POLICY_RECEIPT_WRITTEN");
            db.commit();return receipt;
        }catch(Exception failure){db.rollback();throw failure;}finally{db.setAutoCommit(true);}
    }
    static Evidence read(Connection db,String account,List<OfferEvent> history)throws Exception {
        var input=new Inputs();for(var event:history)input.accept(event);
        var sources=new HashMap<String,OfferEvent>();
        for(var event:history)if(input.origins.contains(event.getEventId()))sources.put(event.getEventId(),event);
        return read(db,account,input,sources,Long.MAX_VALUE);
    }
    private static final class Inputs {
        final Set<String> needed=new HashSet<>(),origins=new HashSet<>(),bindings=new HashSet<>();
        void accept(OfferEvent event) {
            var proof=event.getAcceptedSalePolicyProof();
            if(proof!=null && proof.getReceipt()!=null) {
                needed.add(proof.getReceipt().getReceiptId());origins.add(proof.getReceipt().getSourceEventId());
                if(proof.qualifiesObservation(event))bindings.add(event.getEventId());
            }
        }
    }
    /** Prepares source identities, never receipt authority. Every retained row is checked afresh. */
    static final class InputIndex {
        private List<OfferEvent> canonical;private int authorizedSize,consumed;private String account;
        private Inputs input=new Inputs();private long processed;
        private final Map<Integer,Inputs> byItem=new HashMap<>();
        private RuntimeException failure;
        void reset(){canonical=null;authorizedSize=consumed=0;account=null;input=new Inputs();byItem.clear();failure=null;}
        void appendCanonical(String expectedAccount,List<OfferEvent> previous,List<OfferEvent> next) {
            if(previous!=canonical || !Objects.equals(account,expectedAccount) || next==null || next.size()<authorizedSize)reset();
            canonical=next;authorizedSize=next==null?0:next.size();account=expectedAccount;
            // Optional sale preparation must not turn a malformed historical policy into an
            // unrelated account/entry failure. Only the sale reader consumes this refusal.
            try{prepare(expectedAccount,next);}catch(RuntimeException unavailable){failure=unavailable;}
        }
        private Inputs prepare(String expectedAccount,List<OfferEvent> history) {
            if(history==null)throw new IllegalArgumentException("Canonical receipt sources unavailable");
            if(history!=canonical || history.size()!=authorizedSize || !Objects.equals(account,expectedAccount)) {
                reset();canonical=history;authorizedSize=history.size();account=expectedAccount;
            }
            if(failure!=null)throw new IllegalStateException("Sale policy sources require review",failure);
            while(consumed<history.size()) {
                var event=history.get(consumed);input.accept(event);
                var proof=event.getAcceptedSalePolicyProof();
                if(proof!=null && proof.getReceipt()!=null)
                    byItem.computeIfAbsent(proof.getReceipt().getItemId(),ignored->new Inputs()).accept(event);
                consumed++;processed++;
            }
            return input;
        }
        private Inputs prepareItem(String expectedAccount,List<OfferEvent> history,int item) {
            prepare(expectedAccount,history);
            return byItem.getOrDefault(item,new Inputs());
        }
        long processedCount(){return processed;}
    }
    static Evidence read(Connection db,String account,List<OfferEvent> history,InputIndex index,long sourceCursor)throws Exception {
        return readInputs(db,account,index.prepare(account,history),sourceCursor);
    }
    /** A relist can consume only receipts for its item. Each selected receipt still proves its full eight-slot read set. */
    static Evidence readForItem(Connection db,String account,List<OfferEvent> history,InputIndex index,long sourceCursor,int item)throws Exception {
        if(item<=0)throw new IllegalArgumentException("Sale receipt item is required");
        return readInputs(db,account,index.prepareItem(account,history,item),sourceCursor);
    }
    private static Evidence readInputs(Connection db,String account,Inputs input,long sourceCursor)throws Exception {
        var sources=new HashMap<String,OfferEvent>();
        // The cursor belongs to this transaction's complete validated source prefix. An origin
        // outside it cannot borrow authority from a later row, including under rollback/external edit.
        try(var query=db.prepareStatement(SOURCE_LOOKUP_SQL+" AND id<=?")) {
            for(String id:input.origins) {
                if(id==null)continue;query.setString(1,id);query.setLong(2,sourceCursor);
                try(var rows=query.executeQuery()) {
                    if(!rows.next())continue;
                    var source=JSON.fromJson(rows.getString(2),OfferEvent.class);
                    if(source!=null && TradeGapEvidence.validFields(source) && account.equals(source.getAccountId())
                        && id.equals(source.getEventId()) && !rows.next())sources.put(id,source);
                }
            }
        }
        return read(db,account,input,sources,sourceCursor);
    }
    private static Evidence read(Connection db,String account,Inputs input,Map<String,OfferEvent> sources,long sourceCursor)throws Exception {
        var evidence=new Evidence();if(input.needed.isEmpty())return evidence;
        try(var find=db.prepareStatement("SELECT account_id,account_row_id,source_event_id,issued_after_row,payload FROM "+TABLE+" WHERE receipt_id=?");
            var sourceOrder=db.prepareStatement(BINDING_ROW_SQL)) {
            for(var id:input.needed) {
                if(id==null)continue;find.setString(1,id);
                try {
                try(var row=find.executeQuery()) {
                    if(!row.next())continue;
                    var receipt=JSON.fromJson(row.getString(5),SalePolicyReceipt.class);long after=row.getLong(4);
                    if(!retainedReceipt(db,id,account,row.getString(1),row.getLong(2),row.getString(3),after,
                            receipt,receipt==null?null:sources.get(receipt.getSourceEventId())))continue;
                    evidence.receipts.put(id,receipt);evidence.issuedAfter.put(id,after);
                }
                }catch(RuntimeException malformed){ /* Unusable policy metadata grants no exemption. */ }
            }
            for(var binding:input.bindings) {
                sourceOrder.setString(1,binding);try(var rows=sourceOrder.executeQuery()) {
                    if(rows.next()){long value=rows.getLong(1);if(value<=sourceCursor && !rows.next())evidence.eventRows.put(binding,value);}
                }
            }
        }
        return evidence;
    }
    /** The idempotent POST path and later canonical use require the same retained proof. */
    private static boolean retainedReceipt(Connection db,String id,String account,String recordedAccount,long accountRow,
            String recordedSource,long issuedAfter,SalePolicyReceipt receipt,OfferEvent source)throws Exception {
        if(receipt==null || !receipt.isWellFormed() || !Objects.equals(id,receipt.getReceiptId())
            || !Objects.equals(account,recordedAccount) || !Objects.equals(account,receipt.getAccountId())
            || accountRow!=receipt.getAccountRowId() || !Objects.equals(recordedSource,receipt.getSourceEventId())
            || !receipt.matchesSource(source) || issuedAfter<accountRow)return false;
        try(var snapshot=db.prepareStatement("SELECT correlation_id,payload,observed_at FROM event_log WHERE id=? AND event_type='ACCOUNT_STATE'")) {
            snapshot.setLong(1,accountRow);
            try(var original=snapshot.executeQuery()) {
                if(!original.next() || original.getLong(3)!=receipt.getAccountObservedAt()
                    || !receipt.getAccountCorrelationId().equals(original.getString(1))
                    || !receipt.getPayloadHash().equals(hash(original.getString(2))) || !exactSettings(original.getString(2)))return false;
                var settings=JSON.fromJson(original.getString(2),AccountSnapshot.class);
                if(!receipt.matchesSettings(settings) || !validReadSet(db,settings,source,accountRow))return false;
            }
        }
        long sourcePosition=sourceRow(db,source);
        if(sourcePosition<=0 || sourcePosition>issuedAfter)return false;
        // Correlation ambiguity must never be resolved by choosing whichever row comes first.
        try(var duplicates=db.prepareStatement(ACCOUNT_CORRELATION_SQL)) {
            duplicates.setString(1,receipt.getAccountCorrelationId());
            try(var count=duplicates.executeQuery()){return count.next() && count.getInt(1)==1;}
        }
    }
    private static boolean currentSource(Connection db,OfferEvent source)throws Exception {
        if(source==null)return false;
        try(var query=db.prepareStatement("SELECT source_event_id FROM offer_projection WHERE slot=?")) {
            query.setInt(1,source.getSlot());try(var rows=query.executeQuery()){return rows.next() && source.getEventId().equals(rows.getString(1));}
        }
    }
    private static long sourceRow(Connection db,OfferEvent expected)throws Exception {
        if(expected==null)return 0;
        try(var query=db.prepareStatement(SOURCE_LOOKUP_SQL)) {
            query.setString(1,expected.getEventId());try(var rows=query.executeQuery()) {
                if(!rows.next())return 0;long row=rows.getLong(1);var actual=JSON.fromJson(rows.getString(2),OfferEvent.class);
                if(actual==null || !sourceMetadata(rows,actual) || !JSON.toJson(actual).equals(JSON.toJson(expected)) || rows.next())return 0;return row;
            }
        }
    }
    private static boolean validReadSet(Connection db,AccountSnapshot settings,OfferEvent source,long accountRow)throws Exception {
        if(settings==null || source==null || settings.getOfferReadSet()==null || settings.getOfferReadSet().size()!=8)return false;
        var ids=new HashSet<String>();String session=null;
        try(var query=db.prepareStatement(SOURCE_LOOKUP_SQL)) {
            for(int slot=0;slot<8;slot++) {
                var id=settings.getOfferReadSet().get(slot);if(id==null || !ids.add(id))return false;
                query.setString(1,id);try(var rows=query.executeQuery()) {
                    if(!rows.next() || rows.getLong(1)>=accountRow)return false;
                    var e=JSON.fromJson(rows.getString(2),OfferEvent.class);
                    if(e==null || !Objects.equals(id,e.getEventId()) || !sourceMetadata(rows,e) || !TradeGapEvidence.valid(e) || !e.isAuthenticatedObservation() || e.getSlot()!=slot
                        || !Objects.equals(settings.getAccountId(),e.getAccountId()) || e.getObservedAt()!=settings.getObservedAt() || rows.next())return false;
                    if(session==null)session=e.getSessionId();if(!Objects.equals(session,e.getSessionId()))return false;
                    if(slot==source.getSlot() && (!"SELLING".equals(e.getEventType()) || !Objects.equals(e.getOfferIdentity(),source.getOfferIdentity())
                        || e.isBuying() || e.getItemId()!=source.getItemId() || e.getPrice()!=source.getPrice()
                        || e.getTotalQuantity()!=source.getTotalQuantity() || e.getFirstSeenAt()!=source.getFirstSeenAt()
                        || e.getSequence()>source.getSequence() || e.getObservedAt()>source.getObservedAt()
                        || e.getFilledQuantity()>source.getFilledQuantity() || e.getSpent()>source.getSpent()))return false;
                }
            }
        }
        return Objects.equals(session,source.getSessionId());
    }
    private static String hash(String value)throws Exception {
        byte[] bytes=MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        var result=new StringBuilder();for(byte b:bytes)result.append(String.format("%02x",b&255));return result.toString();
    }
    private static boolean sourceMetadata(ResultSet row,OfferEvent event)throws Exception {
        return Objects.equals(row.getString(3),event.getEventId()) && Objects.equals(row.getString(4),event.getEventType())
            && row.getLong(5)==event.getObservedAt() && Objects.equals(row.getString(6),event.getCorrelationId());
    }
    private static boolean exactSettings(String payload) {
        try {
            var raw=JSON.fromJson(payload,com.google.gson.JsonObject.class);
            for(String name:new String[]{"checkIntervalMinutes","targetHoldMinutes"}) {
                var value=raw.getAsJsonPrimitive(name);
                if(value==null || !value.isNumber() || !value.getAsString().matches("0|[1-9][0-9]*")
                    || new java.math.BigInteger(value.getAsString()).compareTo(java.math.BigInteger.valueOf(Integer.MAX_VALUE))>0)return false;
            }
            var mode=raw.getAsJsonPrimitive("sellOnly");var risk=raw.getAsJsonPrimitive("riskAppetite");
            return mode!=null && mode.isBoolean() && risk!=null && risk.isString()
                && java.util.Set.of("LOW","MODERATE","HIGH").contains(risk.getAsString());
        }catch(RuntimeException invalid){return false;}
    }
}
