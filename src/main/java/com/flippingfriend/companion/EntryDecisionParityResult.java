package com.flippingfriend.companion;

/** Immutable parity evidence carrying the shadow decision lineage when available. */
public final class EntryDecisionParityResult
{
    private final EntryDecisionParityStatus status;
    private final String reasonCode;
    private final String decisionId;
    private final String policyVersion;
    private final String recommendationId;
    private final String candidateId;

    EntryDecisionParityResult(EntryDecisionParityStatus status, String reasonCode,
        String decisionId, String policyVersion, String recommendationId, String candidateId)
    {
        if (status == null) throw new IllegalArgumentException("status is required");
        if (reasonCode == null || reasonCode.trim().isEmpty())
            throw new IllegalArgumentException("reasonCode is required");
        this.status = status;
        this.reasonCode = reasonCode;
        this.decisionId = decisionId;
        this.policyVersion = policyVersion;
        this.recommendationId = recommendationId;
        this.candidateId = candidateId;
    }

    public EntryDecisionParityStatus getStatus() { return status; }
    public String getReasonCode() { return reasonCode; }
    public String getDecisionId() { return decisionId; }
    public String getPolicyVersion() { return policyVersion; }
    public String getRecommendationId() { return recommendationId; }
    public String getCandidateId() { return candidateId; }
    public boolean isMatch() { return status == EntryDecisionParityStatus.MATCH; }
}
