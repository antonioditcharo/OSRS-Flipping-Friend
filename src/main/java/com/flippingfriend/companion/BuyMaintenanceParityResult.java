package com.flippingfriend.companion;

/** Immutable parity evidence carrying companion buy-maintenance lineage when available. */
public final class BuyMaintenanceParityResult
{
    private final BuyMaintenanceParityStatus status;
    private final String reasonCode;
    private final String decisionId;
    private final String policyVersion;
    private final String offerIdentity;
    private final String recommendationId;

    BuyMaintenanceParityResult(BuyMaintenanceParityStatus status, String reasonCode,
        String decisionId, String policyVersion, String offerIdentity, String recommendationId)
    {
        if (status == null) throw new IllegalArgumentException("status is required");
        if (reasonCode == null || reasonCode.trim().isEmpty())
            throw new IllegalArgumentException("reasonCode is required");
        this.status = status;
        this.reasonCode = reasonCode;
        this.decisionId = decisionId;
        this.policyVersion = policyVersion;
        this.offerIdentity = offerIdentity;
        this.recommendationId = recommendationId;
    }

    public BuyMaintenanceParityStatus getStatus() { return status; }
    public String getReasonCode() { return reasonCode; }
    public String getDecisionId() { return decisionId; }
    public String getPolicyVersion() { return policyVersion; }
    public String getOfferIdentity() { return offerIdentity; }
    public String getRecommendationId() { return recommendationId; }
    public boolean isMatch() { return status == BuyMaintenanceParityStatus.MATCH; }
}
