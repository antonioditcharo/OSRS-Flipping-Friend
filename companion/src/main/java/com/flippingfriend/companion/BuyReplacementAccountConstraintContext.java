package com.flippingfriend.companion;
import com.flippingfriend.core.AccountSnapshot;
import com.flippingfriend.core.PortfolioConstraints;
/** Immutable account-derived replacement limits; evidence only, not a plan, decision, or authority. */
final class BuyReplacementAccountConstraintContext {
    private final BuyReplacementQuantityCandidateSet source;
    private final AccountSnapshot account;
    private final BuyReplacementAccountConstraintOutcome outcome;
    private final long lossBudget;
    private final String appetiteName;
    private final PortfolioConstraints constraints;
    private final long composedAt;
    BuyReplacementAccountConstraintContext(BuyReplacementQuantityCandidateSet source, AccountSnapshot account,
            BuyReplacementAccountConstraintOutcome outcome, long lossBudget, String appetiteName,
            PortfolioConstraints constraints, long composedAt) {
        this.source = source;
        this.account = account;
        this.outcome = outcome;
        this.lossBudget = lossBudget;
        this.appetiteName = appetiteName;
        this.constraints = constraints;
        this.composedAt = composedAt;
    }
    BuyReplacementQuantityCandidateSet getSource() { return source; }
    AccountSnapshot getAccount() { return account; }
    BuyReplacementAccountConstraintOutcome getOutcome() { return outcome; }
    long getLossBudget() { return lossBudget; }
    /** Risk appetite resolved from the account, or null before resolution. */
    String getAppetiteName() { return appetiteName; }
    /** Production constraints built by the planner's own helpers; null unless the outcome is READY. */
    PortfolioConstraints getConstraints() { return constraints; }
    long getAccountObservedAt() { return account.getObservedAt(); }
    long getExpiresAt() { return source.getExpiresAt(); }
    long getComposedAt() { return composedAt; }
}
