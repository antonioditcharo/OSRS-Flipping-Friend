package com.flippingfriend.companion;

import com.flippingfriend.core.*;
import com.google.gson.Gson;
import java.util.*;

/** Observed source facts and unresolved ambiguities only. Never reconstructs holdings or money. */
final class TradeEvidenceAudit {
    static final String VERSION="TRADE_SOURCE_AUDIT_V1";
    final String schemaVersion="1",auditorVersion=VERSION,status="OBSERVATION_AUDIT_ONLY";
    final boolean authorizesAccounting=false,authorizesTrading=false;
    final String accountId,sourceManifestSha256;
    final long capturedAt,sourceCursor,sourceRows;
    final List<IdentityEvidence> identities;
    final List<Reference> unassignedProblems;

    private TradeEvidenceAudit(ExecutionSourceSnapshot source,String manifest,
            Collection<MutableIdentity> groups,List<Reference> unassigned) {
        accountId=source.accountId;sourceManifestSha256=manifest;capturedAt=source.capturedAt;
        sourceCursor=source.sourceCursor;sourceRows=source.offers.size();
        List<IdentityEvidence> rows=new ArrayList<>();for(var group:groups)rows.add(new IdentityEvidence(group));
        identities=Collections.unmodifiableList(rows);unassignedProblems=List.copyOf(unassigned);
    }
    static TradeEvidenceAudit analyze(ExecutionSourceSnapshot source,String manifest) {
        if(source==null || manifest==null || !manifest.matches("[0-9a-f]{64}"))
            throw new IllegalArgumentException("Verified source manifest is required");
        source.observations(); // Existing complete-prefix/account/source/gap validation; no database.
        Gson json=new Gson();Map<String,MutableIdentity> groups=new LinkedHashMap<>();
        Map<Integer,Slot> slots=new HashMap<>();Set<String> seen=new HashSet<>(),gaps=new HashSet<>();
        for(var gap:source.gaps)gaps.add(gap.sourceEventId);
        List<Reference> unassigned=new ArrayList<>();
        for(var row:source.offers) {
            OfferEvent e=json.fromJson(row.payload,OfferEvent.class);
            if(!seen.add(e.getEventId()))throw new IllegalArgumentException("Duplicate canonical source identity");
            var reference=new Reference(row,e);var slot=slots.computeIfAbsent(e.getSlot(),key->new Slot());
            boolean empty="EMPTY".equals(e.getEventType());
            boolean unordered=slot.last!=null && (!Objects.equals(slot.last.getSessionId(),e.getSessionId())
                || e.getSequence()<=slot.last.getSequence() || e.getObservedAt()<slot.last.getObservedAt());
            boolean future=e.getObservedAt()>source.capturedAt;
            if(empty) {
                if(slot.possible.isEmpty() && (unordered || future || gaps.contains(e.getEventId())))unassigned.add(reference);
                for(var group:slot.possible) {
                    group.clears.add(reference);
                    if(group.last==null || !terminal(group.last))group.problem("EMPTY_WITHOUT_TERMINAL",reference);
                    if(unordered)group.problem("SOURCE_ORDER_CONFLICT",reference);
                    if(future)group.problem("SOURCE_CLOCK_CONFLICT",reference);
                    if(gaps.contains(e.getEventId()))group.gap(reference);
                }
                if(slot.last!=null && terminal(slot.last) && !gaps.contains(e.getEventId()))slot.possible.clear();
                slot.current=null;
            }else {
                var group=groups.computeIfAbsent(e.getOfferIdentity(),key->new MutableIdentity(e,reference));
                if(!group.clears.isEmpty() && slot.current!=group)group.problem("IDENTITY_REAPPEARED_AFTER_EMPTY",reference);
                if(slot.current!=null && slot.current!=group) {
                    group.problem("IDENTITY_CHANGED_WITHOUT_EMPTY",reference);
                    slot.current.problem("IDENTITY_CHANGED_WITHOUT_EMPTY",reference);
                }
                if(unordered) {
                    group.problem("SOURCE_ORDER_CONFLICT",reference);
                    for(var prior:slot.possible)prior.problem("SOURCE_ORDER_CONFLICT",reference);
                }
                if(future)group.problem("SOURCE_CLOCK_CONFLICT",reference);
                if(gaps.contains(e.getEventId())) {
                    group.gap(reference);for(var prior:slot.possible)prior.gap(reference);
                }
                group.observe(e,reference);slot.current=group;slot.possible.add(group);
            }
            slot.last=e;
        }
        return new TradeEvidenceAudit(source,manifest,groups.values(),unassigned);
    }
    private static boolean terminal(OfferEvent e) {
        String state=e.getEventType();return "BOUGHT".equals(state) || "SOLD".equals(state)
            || "CANCELLED_BUY".equals(state) || "CANCELLED_SELL".equals(state);
    }
    static final class Reference {
        final long rowId,observedAt,spent;
        final String eventId,eventType;
        final int itemId,filledQuantity;
        Reference(ExecutionSourceSnapshot.Row row,OfferEvent e) {
            rowId=row.rowId;observedAt=e.getObservedAt();eventId=e.getEventId();eventType=e.getEventType();
            itemId=e.getItemId();filledQuantity=e.getFilledQuantity();spent=e.getSpent();
        }
    }
    static final class IdentityEvidence {
        final String offerIdentity;
        final Set<Integer> itemIds;
        final Set<String> flags;
        final boolean observedZeroStart;
        final Integer maxObservedCumulativeFilled;
        final Long maxObservedCumulativeSpent;
        final Reference first,last;
        final List<Reference> clears,problems;
        final List<String> recordedGapSourceIds;
        IdentityEvidence(MutableIdentity group) {
            offerIdentity=group.firstEvent.getOfferIdentity();itemIds=Collections.unmodifiableSet(new TreeSet<>(group.items));
            flags=Collections.unmodifiableSet(new TreeSet<>(group.flags));observedZeroStart=group.zeroStart;
            boolean coherent=!flags.contains("CONFLICTING_METADATA");
            maxObservedCumulativeFilled=coherent?group.maxFilled:null;maxObservedCumulativeSpent=coherent?group.maxSpent:null;
            first=group.first;last=group.lastReference;clears=List.copyOf(group.clears);
            problems=List.copyOf(group.problems.values());recordedGapSourceIds=List.copyOf(group.gapIds);
        }
    }
    private static final class MutableIdentity {
        final OfferEvent firstEvent;final Reference first;final boolean zeroStart;
        final Set<Integer> items=new TreeSet<>();final Set<String> flags=new TreeSet<>();
        final List<Reference> clears=new ArrayList<>();final Map<String,Reference> problems=new LinkedHashMap<>();
        final Set<String> gapIds=new LinkedHashSet<>();
        int maxFilled;long maxSpent;OfferEvent last;Reference lastReference;
        MutableIdentity(OfferEvent e,Reference reference) {
            firstEvent=e;first=reference;zeroStart=e.getFilledQuantity()==0 && e.getSpent()==0 && !terminal(e);
            if(e.getFilledQuantity()>0)problem("INITIAL_FILLED_BASELINE",reference);
            if(terminal(e))problem("INITIAL_TERMINAL_BASELINE",reference);
        }
        void problem(String flag,Reference reference) {flags.add(flag);problems.putIfAbsent(reference.eventId,reference);}
        void gap(Reference reference) {problem("RECORDED_RECONCILIATION",reference);gapIds.add(reference.eventId);}
        void observe(OfferEvent e,Reference reference) {
            items.add(e.getItemId());
            if(e.getItemId()!=firstEvent.getItemId() || e.isBuying()!=firstEvent.isBuying()
                    || e.getPrice()!=firstEvent.getPrice() || e.getTotalQuantity()!=firstEvent.getTotalQuantity()
                    || e.getSlot()!=firstEvent.getSlot() || e.getFirstSeenAt()!=firstEvent.getFirstSeenAt())
                problem("CONFLICTING_METADATA",reference);
            if(last!=null) {
                if(e.getFilledQuantity()<last.getFilledQuantity() || e.getSpent()<last.getSpent())problem("COUNTER_REGRESSION",reference);
                if(terminal(last) && !terminal(e))problem("TERMINAL_TO_ACTIVE",reference);
                if(!Objects.equals(e.getSessionId(),last.getSessionId()))problem("SESSION_CHANGED",reference);
            }
            if(OfferLifecycleClassifier.classify(e)==OfferLifecycleState.ERROR_RECONCILIATION)problem("INVALID_LIFECYCLE_OBSERVATION",reference);
            try {
                if(e.isBuying() && e.getSpent()>Math.multiplyExact(e.getPrice(),(long)e.getTotalQuantity()))problem("CONTRADICTORY_FUNDING",reference);
            }catch(ArithmeticException overflow){problem("FUNDING_BOUND_OVERFLOW",reference);}
            maxFilled=Math.max(maxFilled,e.getFilledQuantity());maxSpent=Math.max(maxSpent,e.getSpent());
            last=e;lastReference=reference;
        }
    }
    private static final class Slot {
        OfferEvent last;MutableIdentity current;final Set<MutableIdentity> possible=new LinkedHashSet<>();
    }
}
