package com.flippingfriend.core;

import java.util.*;

/** A durable account publication receipt. Never acknowledges financial outbox events or an order. */
public final class AccountFrameAcknowledgement {
    private final int schemaVersion;
    private final boolean accepted,currentFrameVerified;
    private final String accountId,correlationId;
    private final long observedAt;
    private final Map<Integer,String> offerReadSet;
    private AccountFrameAcknowledgement(AccountSnapshot snapshot,boolean verified) {
        schemaVersion=1;accepted=true;
        accountId=snapshot.getAccountId();correlationId=snapshot.getCorrelationId();observedAt=snapshot.getObservedAt();
        var sources=snapshot.getOfferReadSet();offerReadSet=sources==null?Map.of():Collections.unmodifiableMap(new HashMap<>(sources));currentFrameVerified=verified;
    }
    public static AccountFrameAcknowledgement of(AccountSnapshot snapshot,boolean verified) {
        return new AccountFrameAcknowledgement(Objects.requireNonNull(snapshot),verified);
    }
    public boolean matches(AccountSnapshot snapshot) {
        if(schemaVersion!=1 || !accepted || snapshot==null || !Objects.equals(accountId,snapshot.getAccountId())
            || !Objects.equals(correlationId,snapshot.getCorrelationId()) || observedAt!=snapshot.getObservedAt()
            || !Objects.equals(offerReadSet,snapshot.getOfferReadSet()==null?Map.of():snapshot.getOfferReadSet()))return false;
        if(!currentFrameVerified)return true;
        if(!snapshot.isAutomaticTradeScope() || offerReadSet==null || offerReadSet.size()!=8)return false;
        var unique=new HashSet<String>();
        for(int slot=0;slot<8;slot++){String id=offerReadSet.get(slot);if(id==null || id.isBlank() || !unique.add(id))return false;}
        return true;
    }
    public boolean isCurrentFrameVerified(){return currentFrameVerified;}
}
