package com.flippingfriend.companion;

import com.flippingfriend.core.*;
import java.util.*;

/** Conservative display classification, independent of accounting and risk. */
final class TradeReviewRead {
    static final String EVENT_TYPE="TRADE_REVIEW_V1";
    static final class Row {
        long id;String source,offer;int item;boolean purchaseRecorded;
        Row(long id,String source,String offer,int item,boolean purchase) {
            this.id=id;this.source=source;this.offer=offer;this.item=item;purchaseRecorded=purchase;
        }
    }
    static TradeReviewSnapshot classify(String account,long now,long cursor,List<Row> rows,List<OfferEvent> history,
        Set<String> rejectedSources,Set<Integer> liveItems,Set<String> archived) {
        Set<String> closed=new HashSet<>();Map<String,OfferEvent> soldSources=new HashMap<>();
        Map<Integer,OfferEvent> prior=new HashMap<>();
        if(history!=null)for(var e:history) {
            if(!account.equals(e.getAccountId()) || !TradeGapEvidence.valid(e)) {closed.clear();soldSources.clear();break;}
            if(e.getObservedAt()>now || rejectedSources.contains(e.getEventId())) {prior.remove(e.getSlot());continue;}
            var before=prior.get(e.getSlot());
            if("EMPTY".equals(e.getEventType()) && before!=null && !before.isBuying()
                && Set.of("SOLD","CANCELLED_SELL").contains(before.getEventType())
                && before.getSessionId().equals(e.getSessionId()) && e.getSequence()>before.getSequence()
                && e.getObservedAt()>=before.getObservedAt())closed.add(key(before));
            if(!e.isBuying() && e.getItemId()>0)soldSources.put(e.getEventId(),e);
            prior.put(e.getSlot(),e);
        }
        Map<String,String> states=new LinkedHashMap<>();Set<String> retainedArchives=new HashSet<>();
        for(var row:rows) {
            var source=soldSources.get(row.source);
            boolean inactive=history!=null && source!=null && row.item==source.getItemId()
                && Objects.equals(row.offer,source.getOfferIdentity()) && closed.contains(key(source)) && !liveItems.contains(row.item);
            String state=row.purchaseRecorded?TradeReviewSnapshot.RETAINED:!inactive?TradeReviewSnapshot.OPEN:
                archived.contains(row.source)?TradeReviewSnapshot.ARCHIVED:TradeReviewSnapshot.ELIGIBLE;
            states.put(row.source,state);if(archived.contains(row.source))retainedArchives.add(row.source);
        }
        return new TradeReviewSnapshot(account,now,cursor,states,retainedArchives);
    }
    private static String key(OfferEvent e){return e.getSessionId()+":"+e.getSlot()+":"+e.getOfferIdentity()+":"+e.getItemId();}
}
