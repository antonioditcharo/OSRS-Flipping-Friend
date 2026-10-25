package com.flippingfriend.core;

import java.util.*;

/** Hosted suggestions are hints. No response here can acknowledge a canonical execution. */
public final class PilotProtocol {
    public static final String VERSION="hosted-pilot-v1";
    private PilotProtocol() { }
    public static final class Demand {
        public int itemId;
        public long buying, selling;
        public boolean uncertain;
        public Demand() { }
        public Demand(int item,long buying,long selling,boolean uncertain) {
            this.itemId=item;this.buying=buying;this.selling=selling;this.uncertain=uncertain;
        }
    }
    public static final class AllocationRequest {
        public String protocol=VERSION, accountId, installationId, context;
        public long generation, frameOrder, observedAt, preferredProfit, marketAt;
        public boolean coordinate;
        public List<PortfolioCandidate> candidates=new ArrayList<>();
        public List<Demand> demand=new ArrayList<>();
        public void validate(long now) {
            AccountIdentity.require(accountId);
            if(!VERSION.equals(protocol) || installationId==null || installationId.length()>100
                    || context==null || !context.matches("[0-9a-f]{64}") || generation<1 || frameOrder<1
                    || observedAt<=0 || observedAt>now || now-observedAt>150
                    || marketAt<=0 || marketAt>now || now-marketAt>180 || preferredProfit<0
                    || candidates==null || candidates.size()>2048 || demand==null || demand.size()>4096)
                throw new IllegalArgumentException("invalid allocation scope or freshness");
            Set<Integer> ids=new HashSet<>();
            for(Demand d:demand)if(d==null || d.itemId<=0 || d.buying<0 || d.selling<0 || !ids.add(d.itemId))
                throw new IllegalArgumentException("invalid demand");
            for(PortfolioCandidate c:candidates)if(c==null || c.getItemId()<=0 || c.getQuantity()<=0
                    || c.getTargetBuyPrice()<=0 || c.getTargetSellPrice()<=0 || c.getExpiresAt()<=now
                    || !Double.isFinite(c.expectedGpPerSlotHour()))throw new IllegalArgumentException("invalid candidate");
        }
    }
    public static final class AllocationHint {
        public String protocol=VERSION, accountId, installationId, context, adviceId, mode;
        public long generation, revision, expiresAt;
        public int itemId,buyPrice,sellPrice,quantity;
        public Set<Integer> crowdedItems=new HashSet<>();
        public boolean matches(AllocationRequest request,long now) {
            return VERSION.equals(protocol) && Objects.equals(accountId,request.accountId)
                && Objects.equals(installationId,request.installationId) && generation==request.generation
                && Objects.equals(context,request.context) && adviceId!=null && revision>0
                && expiresAt>now && expiresAt-now<=30 && crowdedItems!=null && crowdedItems.size()<=2048;
        }
    }
    public static final class EvidenceRow {
        public long cursor;
        public String eventId,payload,namespace="OFFER",sourceEventId;
    }
    public static final class EvidenceBatch {
        public String protocol=VERSION,accountId,installationId,streamId,previousHash,hash;
        public long generation,after;
        public List<EvidenceRow> rows=new ArrayList<>();
    }
    public static final class EvidenceAck {
        public String protocol=VERSION,accountId,installationId,streamId,hash;
        public long generation,cursor;
    }
}
