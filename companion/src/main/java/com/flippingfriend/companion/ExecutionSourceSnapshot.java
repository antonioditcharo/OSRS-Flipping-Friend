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
        Set<String> identities=new HashSet<>();previous=0;
        for(Gap gap:gaps) {
            if(gap==null || gap.rowId<=previous)throw new IllegalArgumentException("Invalid reconciliation row order");
            previous=gap.rowId;identities.add(gap.offerIdentity);
        }
        return ExecutionObservationLedger.compile(accountId,capturedAt,history,identities);
    }
    static final class Row {
        final long rowId,observedAt;final String eventId,eventType,payload;
        Row(long id,long at,String eventId,String type,String payload) {
            rowId=id;observedAt=at;this.eventId=eventId;eventType=type;this.payload=payload;
        }
    }
    static final class Gap {
        final long rowId,observedAt;final String sourceEventId,offerIdentity;
        Gap(long id,long at,String source,String offer){rowId=id;observedAt=at;sourceEventId=source;offerIdentity=offer;}
    }
}
