package com.flippingfriend.core;

import java.util.*;

/** Display review only. Archiving never changes financial evidence, ownership or entry authority. */
public final class TradeReviewSnapshot {
    public static final String ELIGIBLE="ARCHIVE_ELIGIBLE", RETAINED="PURCHASE_RECORDS_RETAINED",
        OPEN="OPEN_OR_UNPROVEN", ARCHIVED="ARCHIVED";
    public final String accountId;
    public final long observedAt,positionCursor;
    public final Map<String,String> states;
    public final Set<String> archivedSources;
    public TradeReviewSnapshot(String account,long at,long cursor,Map<String,String> states,Set<String> archived) {
        accountId=AccountIdentity.require(account);observedAt=at;positionCursor=cursor;
        this.states=Collections.unmodifiableMap(new LinkedHashMap<>(states));
        archivedSources=Collections.unmodifiableSet(new HashSet<>(archived));
        if(!isValid())throw new IllegalArgumentException("Invalid trade review");
    }
    public boolean isValid() {
        return accountId!=null && accountId.matches("rs-[0-9a-f]{64}") && observedAt>=0 && positionCursor>=0
            && states!=null && archivedSources!=null && states.keySet().containsAll(archivedSources)
            && states.entrySet().stream().allMatch(e->e.getKey()!=null && !e.getKey().isBlank()
                && e.getValue()!=null && Set.of(ELIGIBLE,RETAINED,OPEN,ARCHIVED).contains(e.getValue())
                && (!ARCHIVED.equals(e.getValue()) || archivedSources.contains(e.getKey())));
    }
    public Set<String> effectiveArchived() {
        Set<String> result=new HashSet<>();states.forEach((source,state)->{if(ARCHIVED.equals(state))result.add(source);});return result;
    }
    public static final class Request {
        public String accountId,requestId;
        public long positionCursor;
        public Boolean archive;
        public List<String> sourceEventIds;
        public Request(String account,String id,long cursor,boolean archive,List<String> sources) {
            accountId=account;requestId=id;positionCursor=cursor;this.archive=archive;sourceEventIds=new ArrayList<>(sources);
        }
        public boolean isValid() {
            return accountId!=null && accountId.matches("rs-[0-9a-f]{64}") && requestId!=null
                && requestId.matches("[a-zA-Z0-9-]{1,80}") && positionCursor>0 && archive!=null
                && sourceEventIds!=null && !sourceEventIds.isEmpty() && sourceEventIds.size()<=1000
                && new HashSet<>(sourceEventIds).size()==sourceEventIds.size()
                && sourceEventIds.stream().allMatch(s->s!=null && !s.isBlank() && s.length()<=200);
        }
    }
}
