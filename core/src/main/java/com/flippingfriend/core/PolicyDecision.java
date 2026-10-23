package com.flippingfriend.core;

/** Immutable, versioned output of a trade policy. */
public final class PolicyDecision
{
    private final String schemaVersion;
    private final String policyVersion;
    private final String decisionId;
    private final PolicyDecisionType decisionType;
    private final OfferLifecycleAction action;
    private final PolicyAbstentionReason abstentionReason;
    private final String reasonCode;
    private final long decidedAt;
    private final long inputObservedAt;
    private final String candidateId;
    private final String recommendationId;
    private final SalePolicyReceipt salePolicyReceipt;
    /** A refused quote may suggest one replacement price; it is never order authority. */
    private SalePriceRevision salePriceRevision;
    public static final class SalePriceRevision {
        private final String version;
        private final int price;
        private final long expectedProfit;
        private final boolean costKnown;
        private SalePriceRevision(int price,long profit,boolean known){version="1";this.price=price;expectedProfit=profit;costKnown=known;}
        public boolean isValid(){return "1".equals(version) && price>0;}
        public int getPrice(){return price;}
        public long getExpectedProfit(){return expectedProfit;}
        public boolean isCostKnown(){return costKnown;}
    }
    public SalePriceRevision getSalePriceRevision(){return salePriceRevision;}
    public PolicyDecision withSalePriceRevision(int price,long profit,boolean known) {
        if(price<=0 || action!=OfferLifecycleAction.WAIT || abstentionReason==PolicyAbstentionReason.NONE
            || !("SALE_PRICE_NOT_AUTHORIZED".equals(reasonCode) || "SALE_EXIT_CHANGED".equals(reasonCode))
            || decisionType!=PolicyDecisionType.EXIT && decisionType!=PolicyDecisionType.SELL_MAINTENANCE)
            throw new IllegalArgumentException("A sale price refusal is required");
        var copy=new PolicyDecision(schemaVersion,policyVersion,decisionId,decisionType,action,abstentionReason,
            reasonCode,decidedAt,inputObservedAt,candidateId,recommendationId);
        copy.salePriceRevision=new SalePriceRevision(price,profit,known);return copy;
    }

    private PolicyDecision(String schemaVersion, String policyVersion, String decisionId,
        PolicyDecisionType decisionType, OfferLifecycleAction action,
        PolicyAbstentionReason abstentionReason, String reasonCode, long decidedAt,
        long inputObservedAt, String candidateId, String recommendationId)
    {
        this(schemaVersion,policyVersion,decisionId,decisionType,action,abstentionReason,reasonCode,
            decidedAt,inputObservedAt,candidateId,recommendationId,null);
    }
    private PolicyDecision(String schemaVersion,String policyVersion,String decisionId,
        PolicyDecisionType decisionType,OfferLifecycleAction action,PolicyAbstentionReason abstentionReason,
        String reasonCode,long decidedAt,long inputObservedAt,String candidateId,String recommendationId,
        SalePolicyReceipt receipt)
    {
        this.schemaVersion = required(schemaVersion, "schemaVersion");
        this.policyVersion = required(policyVersion, "policyVersion");
        this.decisionId = required(decisionId, "decisionId");
        if (decisionType == null) throw new IllegalArgumentException("decisionType is required");
        if (action == null) throw new IllegalArgumentException("action is required");
        if (abstentionReason == null) throw new IllegalArgumentException("abstentionReason is required");
        if (decidedAt < 0 || inputObservedAt < 0 || inputObservedAt > decidedAt)
            throw new IllegalArgumentException("decision timestamps are invalid");
        boolean abstaining = abstentionReason != PolicyAbstentionReason.NONE;
        if (abstaining && action != OfferLifecycleAction.WAIT)
            throw new IllegalArgumentException("abstention requires WAIT");
        if (!abstaining && action == OfferLifecycleAction.WAIT)
            throw new IllegalArgumentException("WAIT requires an abstention reason");
        this.decisionType = decisionType;
        this.action = action;
        this.abstentionReason = abstentionReason;
        this.reasonCode = required(reasonCode, "reasonCode");
        this.decidedAt = decidedAt;
        this.inputObservedAt = inputObservedAt;
        this.candidateId = candidateId;
        this.recommendationId = recommendationId;
        this.salePolicyReceipt=receipt;
    }

    public static PolicyDecision action(String schemaVersion, String policyVersion, String decisionId,
        PolicyDecisionType decisionType, OfferLifecycleAction action, String reasonCode,
        long decidedAt, long inputObservedAt, String candidateId, String recommendationId)
    {
        return new PolicyDecision(schemaVersion, policyVersion, decisionId, decisionType, action,
            PolicyAbstentionReason.NONE, reasonCode, decidedAt, inputObservedAt, candidateId,
            recommendationId);
    }

    public static PolicyDecision abstain(String schemaVersion, String policyVersion, String decisionId,
        PolicyDecisionType decisionType, PolicyAbstentionReason reason, String reasonCode,
        long decidedAt, long inputObservedAt, String candidateId, String recommendationId)
    {
        if (reason == null || reason == PolicyAbstentionReason.NONE)
            throw new IllegalArgumentException("abstention reason is required");
        return new PolicyDecision(schemaVersion, policyVersion, decisionId, decisionType,
            OfferLifecycleAction.WAIT, reason, reasonCode, decidedAt, inputObservedAt,
            candidateId, recommendationId);
    }

    private static String required(String value, String name)
    {
        if (value == null || value.trim().isEmpty())
            throw new IllegalArgumentException(name + " is required");
        return value;
    }

    public String getSchemaVersion() { return schemaVersion; }
    public String getPolicyVersion() { return policyVersion; }
    public String getDecisionId() { return decisionId; }
    public PolicyDecisionType getDecisionType() { return decisionType; }
    public OfferLifecycleAction getAction() { return action; }
    public PolicyAbstentionReason getAbstentionReason() { return abstentionReason; }
    public String getReasonCode() { return reasonCode; }
    public long getDecidedAt() { return decidedAt; }
    public long getInputObservedAt() { return inputObservedAt; }
    public String getCandidateId() { return candidateId; }
    public String getRecommendationId() { return recommendationId; }
    public SalePolicyReceipt getSalePolicyReceipt(){return salePolicyReceipt;}
    public PolicyDecision withSalePolicyReceipt(SalePolicyReceipt receipt) {
        if(receipt==null || !receipt.isWellFormed() || !policyVersion.equals(receipt.getPolicyVersion())
            || decisionType!=PolicyDecisionType.SELL_MAINTENANCE || action!=OfferLifecycleAction.CANCEL_SELL
            || abstentionReason!=PolicyAbstentionReason.NONE || decidedAt!=receipt.getDecidedAt()
            || !java.util.Objects.equals(recommendationId,receipt.getSourceEventId()))
            throw new IllegalArgumentException("Receipt must match the authorized sale decision");
        return new PolicyDecision(schemaVersion,policyVersion,decisionId,decisionType,action,abstentionReason,
            reasonCode,decidedAt,inputObservedAt,candidateId,recommendationId,receipt);
    }
}
