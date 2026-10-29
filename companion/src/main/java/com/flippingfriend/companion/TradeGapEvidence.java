package com.flippingfriend.companion;

import com.flippingfriend.core.*;
import java.util.*;

/** Read-only causal scope of recorded gaps. It makes no execution, ownership or disposal inference. */
final class TradeGapEvidence {
    private static final Set<String> NONEMPTY = Set.of("BUYING","BOUGHT","CANCELLED_BUY",
        "SELLING","SOLD","CANCELLED_SELL");
    private TradeGapEvidence() { }

    static Map<String,Set<Integer>> unknown(Map<String,Integer> gaps) {
        Map<String,Set<Integer>> result=new HashMap<>();
        gaps.keySet().forEach(id->result.put(id,Collections.emptySet()));
        return result;
    }
    static Map<String,Set<Integer>> scope(List<OfferEvent> history,Map<String,Integer> gaps,String account) {
        try { AccountIdentity.require(account); } catch (IllegalArgumentException unavailable) { return unknown(gaps); }
        Scan scan=new Scan(gaps,account);
        for(OfferEvent event:history)if(!scan.accept(event))return unknown(gaps);
        return scan.snapshot();
    }
    /** Receives only immutable prefixes whose append chain SqliteStore has validated. */
    static final class Index {
        private List<OfferEvent> canonical;
        private int authorizedSize,consumed;
        private String account;
        private Scan scan;
        private long processed;
        void reset(){canonical=null;authorizedSize=consumed=0;account=null;scan=null;}
        void appendCanonical(List<OfferEvent> previous,List<OfferEvent> next) {
            if(previous!=canonical || next==null || next.size()<authorizedSize)reset();
            canonical=next;authorizedSize=next==null?0:next.size();
        }
        Map<String,Set<Integer>> read(List<OfferEvent> history,Map<String,Integer> gaps,String expectedAccount) {
            try{AccountIdentity.require(expectedAccount);}catch(IllegalArgumentException unavailable){reset();return unknown(gaps);}
            if(history==null){reset();return unknown(gaps);}
            if(history!=canonical || history.size()!=authorizedSize) {
                reset();canonical=history;authorizedSize=history.size();
            }
            boolean rebuild=scan==null || !Objects.equals(account,expectedAccount);
            if(!rebuild) {
                for(var old:scan.gaps.entrySet())if(!gaps.containsKey(old.getKey()) || !Objects.equals(old.getValue(),gaps.get(old.getKey()))) {rebuild=true;break;}
                // A newly discovered gap at an already processed EMPTY changes that episode's
                // clear semantics. Rebuild; do not apply today's gap map to yesterday's state.
                if(!rebuild)for(String id:gaps.keySet())if(!scan.gaps.containsKey(id) && scan.eventIds.contains(id)){rebuild=true;break;}
            }
            if(rebuild){scan=new Scan(gaps,expectedAccount);consumed=0;account=expectedAccount;}
            else scan.addGaps(gaps);
            if(!scan.valid)return unknown(gaps);
            while(consumed<history.size()) {
                OfferEvent event=history.get(consumed++);processed++;
                if(!scan.accept(event))return unknown(gaps);
            }
            return scan.snapshot();
        }
        long processedCount(){return processed;}
    }
    static Collection<Set<Integer>> scopeDisk(Iterable<OfferEvent> history,Map<String,Integer> gaps,String account,OfflineScratch disk) {
        Scan scan=new Scan(gaps,account,disk);
        for(OfferEvent event:history)if(!scan.accept(event))throw new IllegalArgumentException("Recorded gap lineage is invalid");
        if(scan.result.size()!=gaps.size())throw new IllegalArgumentException("Recorded reconciliation scope is unknown");
        return scan.result.values();
    }
    private static final class Scan {
        final String account;
        Map<String,Integer> gaps;
        final Map<String,Set<Integer>> result;
        final Map<Integer,Slot> slots;
        final Map<String,Set<Integer>> episodeItems;
        final Set<String> eventIds;
        boolean valid=true;
        Scan(Map<String,Integer> gaps,String account){this.account=account;result=new HashMap<>();slots=new HashMap<>();episodeItems=new HashMap<>();eventIds=new HashSet<>();addGaps(gaps);}
        @SuppressWarnings({"unchecked","rawtypes"})
        Scan(Map<String,Integer> gaps,String account,OfflineScratch disk) {
            this.account=account;this.gaps=gaps;result=(Map)disk.map("gap-result",ItemSet.class);
            slots=disk.map("gap-slots",Slot.class);episodeItems=(Map)disk.map("gap-episodes",ItemSet.class);eventIds=disk.set("gap-source-ids");
        }
        void addGaps(Map<String,Integer> next) {
            gaps=new HashMap<>(next);
            for(String id:gaps.keySet())result.putIfAbsent(id,Collections.emptySet());
        }
        Map<String,Set<Integer>> snapshot(){return new HashMap<>(result);}
        boolean accept(OfferEvent e) {
            // Equality to the already validated account proves the identical account alphabet.
            // Keep every event/duplicate check without scanning that 64-digit hash per row.
            if (!validFields(e) || !account.equals(e.getAccountId()) || !eventIds.add(e.getEventId())) {valid=false;return false;}
            Slot prior=slots.get(e.getSlot());
            boolean ordered=prior==null || (prior.trusted && prior.session.equals(e.getSessionId())
                && e.getSequence()>prior.sequence && (e.getObservedAt()>=prior.observedAt
                    || unchangedAuthenticatedEpisode(prior.source,e)));
            if (gaps.containsKey(e.getEventId()) && gaps.get(e.getEventId())==e.getItemId() && ordered) {
                Set<Integer> possible=new HashSet<>();
                if (prior!=null) possible.addAll(prior.items);
                if (e.getItemId()>0) possible.add(e.getItemId());
                if (e.getOfferIdentity()!=null) possible.addAll(episodeItems.getOrDefault(e.getOfferIdentity(),Set.of()));
                if (!possible.isEmpty()) result.put(e.getEventId(),new ItemSet(possible));
            }
            Slot next=prior==null?new Slot(e.getSessionId()):prior;
            if (!ordered) next.trusted=false;
            // A terminal followed by an unambiguous EMPTY separates later offer episodes. A clear
            // recorded as RECONCILE retains its context for subsequent rejected observations.
            // Neither operation disposes stock or removes the already retained gap mappings.
            if ("EMPTY".equals(e.getEventType())) {
                if (ordered && next.terminal && !gaps.containsKey(e.getEventId())) next.items.clear();
                next.terminal=false;
            } else {
                next.items.addAll(episodeItems.getOrDefault(e.getOfferIdentity(),Set.of()));
                next.items.add(e.getItemId());
                Set<Integer> items=episodeItems.get(e.getOfferIdentity());if(items==null)items=new ItemSet();
                items.add(e.getItemId());OfflineScratch.save(episodeItems,e.getOfferIdentity(),items);
                OfferLifecycleState state=OfferLifecycleClassifier.classify(e);
                next.terminal=state==OfferLifecycleState.BUY_FILLED_UNCOLLECTED
                    || state==OfferLifecycleState.BUY_CANCELLED_UNCOLLECTED
                    || state==OfferLifecycleState.SELL_FILLED_UNCOLLECTED
                    || state==OfferLifecycleState.SELL_CANCELLED_UNCOLLECTED;
            }
            next.sequence=Math.max(next.sequence,e.getSequence());
            next.observedAt=Math.max(next.observedAt,e.getObservedAt());
            next.source=e;
            OfflineScratch.save(slots,e.getSlot(),next);
            return true;
        }
    }
    // A delayed unchanged frame cannot widen the possible item set. This is scope evidence only:
    // the reducer still retains the rejection/baseline and never books recovered counters.
    static boolean unchangedAuthenticatedEpisode(OfferEvent a,OfferEvent b) {
        return a!=null && a.isAuthenticatedObservation() && b.isAuthenticatedObservation()
            && a.getItemId()>0 && a.getItemId()==b.getItemId()
            && Objects.equals(a.getOfferIdentity(),b.getOfferIdentity())
            && Objects.equals(a.getEventType(),b.getEventType()) && a.isBuying()==b.isBuying()
            && a.getPrice()==b.getPrice() && a.getTotalQuantity()==b.getTotalQuantity()
            && a.getFilledQuantity()==b.getFilledQuantity() && a.getSpent()==b.getSpent();
    }
    static boolean valid(OfferEvent e) {
        if(e==null)return false;
        try { AccountIdentity.require(e.getAccountId()); } catch (IllegalArgumentException invalid) { return false; }
        return validFields(e);
    }
    /** Callers omitting the account regex must separately require and match one validated account. */
    static boolean validFields(OfferEvent e) {
        if (e==null || !"1".equals(e.getSchemaVersion()) || blank(e.getEventId()) || blank(e.getSessionId())
                || e.getSequence()<=0 || e.getObservedAt()<0 || e.getSlot()<0 || e.getSlot()>=8) return false;
        if ("EMPTY".equals(e.getEventType())) return e.getItemId()==0 && !e.isBuying()
            && e.getPrice()==0 && e.getTotalQuantity()==0 && e.getFilledQuantity()==0 && e.getSpent()==0
            && blank(e.getOfferIdentity());
        if (blank(e.getEventType()) || !NONEMPTY.contains(e.getEventType()) || e.getItemId()<=0 || blank(e.getOfferIdentity())
                || e.getPrice()<=0 || e.getTotalQuantity()<=0 || e.getFilledQuantity()<0
                || e.getFilledQuantity()>e.getTotalQuantity() || e.getSpent()<0) return false;
        boolean buy=e.getEventType().equals("BUYING") || e.getEventType().equals("BOUGHT")
            || e.getEventType().equals("CANCELLED_BUY");
        return buy==e.isBuying() && (e.getFilledQuantity()==0 ? e.getSpent()==0 : e.getSpent()>0);
    }
    private static boolean blank(String value) { return value==null || value.isBlank(); }
    private static final class ItemSet extends HashSet<Integer> {
        ItemSet(){ } ItemSet(Collection<Integer> values){super(values);}
    }
    private static final class Slot {
        final String session;
        final Set<Integer> items=new HashSet<>();
        long sequence,observedAt;
        boolean trusted=true,terminal;
        OfferEvent source;
        Slot(String session) { this.session=session; }
    }
}
