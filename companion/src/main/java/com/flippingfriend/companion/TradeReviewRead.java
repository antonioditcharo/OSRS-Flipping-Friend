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
        return new Index().read(account,now,cursor,rows,history,rejectedSources,liveItems,archived);
    }
    /** Display evidence over a store-validated immutable prefix; never financial/risk authority. */
    static final class Index {
        private List<OfferEvent> history;
        private String account;
        private long at;private int consumed;private boolean future,valid=true;
        private long processed;
        private List<Row> rowKey;private Set<String> rejected;
        private final Set<String> neededSources=new HashSet<>(),neededOffers=new HashSet<>(),closed=new HashSet<>();
        private final Map<String,OfferEvent> soldSources=new HashMap<>();
        private final Map<Integer,OfferEvent> prior=new HashMap<>();
        void reset(){history=null;account=null;at=0;consumed=0;future=false;valid=true;rowKey=null;rejected=null;neededSources.clear();neededOffers.clear();closed.clear();soldSources.clear();prior.clear();}
        void appendCanonical(List<OfferEvent> previous,List<OfferEvent> appended) {
            if(history!=null && history==previous && appended!=null && appended.size()>=previous.size())history=appended;
        }
        long processedCount(){return processed;}
        TradeReviewSnapshot read(String account,long now,long cursor,List<Row> rows,List<OfferEvent> sources,
                Set<String> rejectedSources,Set<Integer> liveItems,Set<String> archived) {
            AccountIdentity.require(account);
            if(history!=sources || !Objects.equals(this.account,account) || now<at || future
                || !sameRows(rows,rowKey) || !rejectedSources.equals(rejected)) {
                reset();history=sources;this.account=account;rowKey=new ArrayList<>();rejected=Set.copyOf(rejectedSources);
                for(Row row:rows){neededSources.add(row.source);neededOffers.add(row.offer);rowKey.add(new Row(row.id,row.source,row.offer,row.item,row.purchaseRecorded));}
            }
            at=now;
            if(history!=null && valid)for(int i=consumed;i<history.size();i++) {
                var e=history.get(i);processed++;
                if(!TradeGapEvidence.validFields(e) || !account.equals(e.getAccountId())) {valid=false;closed.clear();soldSources.clear();break;}
                if(e.getObservedAt()>now || rejected.contains(e.getEventId())) {
                    if(e.getObservedAt()>now)future=true;prior.remove(e.getSlot());continue;
                }
                var before=prior.get(e.getSlot());
                if("EMPTY".equals(e.getEventType()) && before!=null && !before.isBuying()
                    && ("SOLD".equals(before.getEventType()) || "CANCELLED_SELL".equals(before.getEventType()))
                    && neededOffers.contains(before.getOfferIdentity()) && before.getSessionId().equals(e.getSessionId())
                    && e.getSequence()>before.getSequence() && e.getObservedAt()>=before.getObservedAt())closed.add(key(before));
                if(!e.isBuying() && e.getItemId()>0 && neededSources.contains(e.getEventId()))soldSources.put(e.getEventId(),e);
                prior.put(e.getSlot(),e);
            }
            consumed=history==null?0:history.size();
            return snapshot(account,now,cursor,rows,history!=null,liveItems,archived,closed,soldSources);
        }
        private static boolean sameRows(List<Row> a,List<Row> b) {
            if(b==null || a.size()!=b.size())return false;
            for(int i=0;i<a.size();i++){Row x=a.get(i),y=b.get(i);if(x.id!=y.id || x.item!=y.item || x.purchaseRecorded!=y.purchaseRecorded
                || !Objects.equals(x.source,y.source) || !Objects.equals(x.offer,y.offer))return false;}
            return true;
        }
    }
    private static TradeReviewSnapshot snapshot(String account,long now,long cursor,List<Row> rows,boolean hasHistory,
            Set<Integer> liveItems,Set<String> archived,Set<String> closed,Map<String,OfferEvent> soldSources) {
        Map<String,String> states=new LinkedHashMap<>();Set<String> retainedArchives=new HashSet<>();
        for(var row:rows) {
            var source=soldSources.get(row.source);
            boolean inactive=hasHistory && source!=null && row.item==source.getItemId()
                && Objects.equals(row.offer,source.getOfferIdentity()) && closed.contains(key(source)) && !liveItems.contains(row.item);
            String state=row.purchaseRecorded?TradeReviewSnapshot.RETAINED:!inactive?TradeReviewSnapshot.OPEN:
                archived.contains(row.source)?TradeReviewSnapshot.ARCHIVED:TradeReviewSnapshot.ELIGIBLE;
            states.put(row.source,state);if(archived.contains(row.source))retainedArchives.add(row.source);
        }
        return new TradeReviewSnapshot(account,now,cursor,states,retainedArchives);
    }
    private static String key(OfferEvent e){return e.getSessionId()+":"+e.getSlot()+":"+e.getOfferIdentity()+":"+e.getItemId();}
}
