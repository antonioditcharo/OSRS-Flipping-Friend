package com.flippingfriend.companion;

import com.flippingfriend.core.*;
import com.google.gson.Gson;
import java.util.*;

/** Frozen recorded prefixes, not a reconstruction of what was known at an old wall-clock time. */
final class ExecutionSourceSnapshot {
    final String schemaVersion,accountId;
    final Long capturedAt,sourceCursor;
    final List<Row> offers;
    final List<Gap> gaps;
    ExecutionSourceSnapshot(String account,long capturedAt,List<Row> offers,List<Gap> gaps) {
        schemaVersion="1";accountId=AccountIdentity.require(account);this.capturedAt=capturedAt;
        this.offers=Collections.unmodifiableList(new ArrayList<>(offers));this.gaps=Collections.unmodifiableList(new ArrayList<>(gaps));
        sourceCursor=offers.isEmpty()?0:offers.get(offers.size()-1).rowId;
    }
    List<ExecutionTimingObservation> observations() {
        return observations(capturedAt==null?0:capturedAt);
    }
    List<ExecutionTimingObservation> observations(long evaluatedAt) {
        if(!"1".equals(schemaVersion) || capturedAt==null || capturedAt<=0 || sourceCursor==null || sourceCursor<0
            || offers==null || gaps==null)throw new IllegalArgumentException("Incomplete source snapshot");
        AccountIdentity.require(accountId);
        List<OfferEvent> history=new ArrayList<>();long previous=0;Gson parser=new Gson();
        for(Row row:offers) {
            if(row==null || row.rowId<=previous || row.payload==null)throw new IllegalArgumentException("Invalid recorded row order");
            OfferEvent event=parser.fromJson(row.payload,OfferEvent.class);
            if(!TradeGapEvidence.valid(event) || !Objects.equals(row.eventId,event.getEventId())
                || row.observedAt!=event.getObservedAt() || !Objects.equals(row.eventType,event.getEventType())
                || !accountId.equals(event.getAccountId()))throw new IllegalArgumentException("Foreign or inconsistent source row");
            previous=row.rowId;history.add(event);
        }
        if(previous!=sourceCursor)throw new IllegalArgumentException("Source cursor does not match the complete prefix");
        Set<String> identities=new HashSet<>(),sourceIds=new HashSet<>();Map<String,Integer> gapItems=new LinkedHashMap<>();previous=0;
        for(var event:history)sourceIds.add(event.getEventId());
        for(Gap gap:gaps) {
            if(gap==null || gap.rowId<=previous)throw new IllegalArgumentException("Invalid reconciliation row order");
            if(gap.sourceEventId==null || !sourceIds.contains(gap.sourceEventId))
                throw new IllegalArgumentException("Reconciliation has no recorded canonical source in this account");
            if(gap.itemId==null || gap.itemId<0)throw new IllegalArgumentException("Reconciliation item is unknown");
            Integer old=gapItems.put(gap.sourceEventId,gap.itemId);
            if(old!=null && !old.equals(gap.itemId))throw new IllegalArgumentException("Conflicting reconciliation source items");
            previous=gap.rowId;
        }
        Set<Integer> affected=new HashSet<>();
        for(var scope:TradeGapEvidence.scope(history,gapItems,accountId).values()) {
            if(scope.isEmpty())throw new IllegalArgumentException("Recorded reconciliation scope is unknown");
            affected.addAll(scope);
        }
        // Quarantine affected items conservatively; a hint/name is never the source of item scope.
        for(var event:history)if(affected.contains(event.getItemId()) && event.getOfferIdentity()!=null)identities.add(event.getOfferIdentity());
        return ExecutionObservationLedger.compile(accountId,evaluatedAt,history,identities);
    }
    static final class Row {
        final long rowId,observedAt;final String eventId,eventType,payload;
        Row(long id,long at,String eventId,String type,String payload) {
            rowId=id;observedAt=at;this.eventId=eventId;eventType=type;this.payload=payload;
        }
    }
    static final class Gap {
        final long rowId,observedAt;final String sourceEventId,offerIdentity;final Integer itemId;
        Gap(long id,long at,String source,String offer){this(id,at,source,offer,0);}
        Gap(long id,long at,String source,String offer,int item){rowId=id;observedAt=at;sourceEventId=source;offerIdentity=offer;itemId=item;}
    }
}
