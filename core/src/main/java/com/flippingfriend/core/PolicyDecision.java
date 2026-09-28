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

    private PolicyDecision(String schemaVersion, String policyVersion, String decisionId,
        PolicyDecisionType decisionType, OfferLifecycleAction action,
        PolicyAbstentionReason abstentionReason, String reasonCode, long decidedAt,
        long inputObservedAt, String candidateId, String recommendationId)
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
}
