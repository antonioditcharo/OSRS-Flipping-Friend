package com.flippingfriend.companion;
import com.flippingfriend.core.AccountSnapshot;
import com.flippingfriend.core.PortfolioCandidate;
import com.flippingfriend.core.PortfolioConstraints;
import com.flippingfriend.model.RiskAppetite;
import com.flippingfriend.model.TaxCalculator;
import java.util.Map;
/**
 * Pure fail-closed derivation of replacement limits from a fresh account snapshot, using the
 * planner's own package-private helpers so the limits cannot drift from what plan() enforces.
 * Applies the planner's refusals in its order: drawdown limit, sell-only mode, then the player's
 * rejected items. Never authorizes, persists, presents, or activates.
 */
final class BuyReplacementAccountConstraintComposer {
    private final TaxCalculator tax;
    BuyReplacementAccountConstraintComposer(TaxCalculator tax) { this.tax = tax; }
    BuyReplacementAccountConstraintContext compose(BuyReplacementQuantityCandidateSet source, AccountSnapshot account,
            Map<Integer, MarketIngestionService.Item> mapping, long at) {
        if (source == null || account == null || mapping == null || tax == null) return null;
        try {
            return composeValid(source, account, mapping, at);
        } catch (NullPointerException | IllegalArgumentException | ArithmeticException
                | IndexOutOfBoundsException | ClassCastException malformed) {
            return null;
        }
    }
    private BuyReplacementAccountConstraintContext composeValid(BuyReplacementQuantityCandidateSet source,
            AccountSnapshot account, Map<Integer, MarketIngestionService.Item> mapping, long at) {
        long observedAt = account.getObservedAt();
        if (observedAt < 0 || observedAt > at
                || at - observedAt > CompanionBuyReplacementReadinessPolicy.MAX_INPUT_AGE_SECONDS) return null;
        // The Package 3.82 selector is the revalidation, timing, and freshness gate for the candidate set.
        if (new BuyReplacementQuantityCandidateSelector(tax)
                .select(source, account.getMinProfitPerFlip(), at) == null) return null;
        for (Long value : account.getCommittedByItem().values()) if (value == null || value < 0) return null;
        long lossBudget = PortfolioPlanner.lossBudgetFor(account);
        if (lossBudget <= 0) return refused(source, account, BuyReplacementAccountConstraintOutcome.DRAWDOWN_LIMIT_REACHED, lossBudget, at);
        if (account.isSellOnly()) return refused(source, account, BuyReplacementAccountConstraintOutcome.SELL_ONLY_MODE, lossBudget, at);
        for (PortfolioCandidate candidate : source.getCandidates())
            if (PortfolioPlanner.rejected(candidate, account))
                return refused(source, account, BuyReplacementAccountConstraintOutcome.PLAYER_REJECTED, lossBudget, at);
        RiskAppetite appetite = RiskAppetite.forName(account.getRiskAppetite());
        Map<String, Long> committedByGroup = PortfolioPlanner.committedByGroupFor(account, mapping);
        PortfolioConstraints constraints = PortfolioPlanner.constraintsFor(account, appetite, lossBudget, committedByGroup);
        return new BuyReplacementAccountConstraintContext(source, account, BuyReplacementAccountConstraintOutcome.READY,
                lossBudget, appetite.getName(), constraints, at);
    }
    private static BuyReplacementAccountConstraintContext refused(BuyReplacementQuantityCandidateSet source,
            AccountSnapshot account, BuyReplacementAccountConstraintOutcome outcome, long lossBudget, long at) {
        return new BuyReplacementAccountConstraintContext(source, account, outcome, lossBudget, null, null, at);
    }
}
