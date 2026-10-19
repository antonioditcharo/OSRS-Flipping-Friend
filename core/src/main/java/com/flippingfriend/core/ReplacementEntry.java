package com.flippingfriend.core;

/** A freshly planned bounded entry and its matching final decision. The cap is a preference only. */
public final class ReplacementEntry {
    public static final String VERSION="replacement-entry-1";
    private final String version;
    private final String accountId;
    private final int itemId,quantityCap;
    private final long accountObservedAt;
    private final PortfolioPlan plan;
    private final PolicyDecision decision;
    public ReplacementEntry(String accountId,int itemId,int quantityCap,long accountObservedAt,
            PortfolioPlan plan,PolicyDecision decision) {
        this.version=VERSION;this.accountId=AccountIdentity.require(accountId);this.itemId=itemId;this.quantityCap=quantityCap;
        this.accountObservedAt=accountObservedAt;this.plan=plan;this.decision=decision;
    }
    public String getVersion(){return version;}
    public String getAccountId(){return accountId;}
    public int getItemId(){return itemId;}
    public int getQuantityCap(){return quantityCap;}
    public long getAccountObservedAt(){return accountObservedAt;}
    public PortfolioPlan getPlan(){return plan;}
    public PolicyDecision getDecision(){return decision;}
}
