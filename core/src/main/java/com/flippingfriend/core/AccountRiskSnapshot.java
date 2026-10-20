package com.flippingfriend.core;

/** Prospective ledger risk, not a session-profit estimate or historical repair. */
public final class AccountRiskSnapshot {
    private final String schemaVersion;
    private final String policyVersion;
    private final Long historicalUncertaintyReserve, unpricedManagedCostReserve;
    private final Long valuationUncertaintyExcluded;
    private final java.util.Set<Integer> valuationPausedItems;
    public static final String CURRENT_ACCOUNT_POLICY="CURRENT_ACCOUNT_V2";
    public static final String VERIFIED_CASH_POLICY="CURRENT_ACCOUNT_V3";
    private static final String LEGACY_CURRENT_ACCOUNT_POLICY="CURRENT_ACCOUNT_V1";
    private final String accountId, status, reason, windowId, sessionId;
    private final long evaluatedAt;
    private final Long validUntil;
    private final Long windowStartedAt, ledgerCursor, marketObservedAt, startingCapital, currentCapital;
    private final Long boundedRealizedProfitLower, boundedRealizedProfitUpper, markedUnrealizedProfit;
    private final Long uncertainLossReserve, riskCapital, drawdownUpper, remainingLossBudget;
    private final long boundedSaleUpdates, unresolvedManagedSaleUpdates, untrackedUnpricedSaleUpdates;

    private AccountRiskSnapshot(String accountId, long at, String reason) {
        schemaVersion = "1";
        policyVersion=null;historicalUncertaintyReserve=unpricedManagedCostReserve=null;
        valuationUncertaintyExcluded=null;valuationPausedItems=null;
        this.accountId = AccountIdentity.require(accountId); evaluatedAt = at;
        status = "UNAVAILABLE"; this.reason = reason; windowId = null; sessionId = null;
        validUntil = null;
        windowStartedAt = ledgerCursor = marketObservedAt = startingCapital = currentCapital = null;
        boundedRealizedProfitLower = boundedRealizedProfitUpper = markedUnrealizedProfit = null;
        uncertainLossReserve = riskCapital = drawdownUpper = remainingLossBudget = null;
        boundedSaleUpdates = unresolvedManagedSaleUpdates = untrackedUnpricedSaleUpdates = 0;
        if (!isValid()) throw new IllegalArgumentException("invalid unavailable risk state");
    }
    private AccountRiskSnapshot(Builder b) {
        schemaVersion = b.currentAccount ? "2" : "1";
        policyVersion=b.verifiedCash?VERIFIED_CASH_POLICY:b.currentAccount?CURRENT_ACCOUNT_POLICY:null;
        historicalUncertaintyReserve=b.currentAccount?b.historical:null;
        unpricedManagedCostReserve=b.currentAccount?b.unpriced:null;
        valuationUncertaintyExcluded=b.verifiedCash?b.excluded:null;
        valuationPausedItems=b.verifiedCash?java.util.Collections.unmodifiableSet(new java.util.HashSet<>(b.paused)):null;
        accountId = AccountIdentity.require(b.accountId); windowId = b.windowId; sessionId = b.sessionId;
        windowStartedAt = b.startedAt; ledgerCursor = b.cursor; evaluatedAt = b.at; marketObservedAt = b.marketAt;
        validUntil = b.validUntil;
        startingCapital = b.startingCapital; currentCapital = b.currentCapital;
        boundedRealizedProfitLower = b.lower; boundedRealizedProfitUpper = b.upper;
        markedUnrealizedProfit = b.marked; uncertainLossReserve = b.reserve;
        boundedSaleUpdates = b.bounded; unresolvedManagedSaleUpdates = b.unresolved; untrackedUnpricedSaleUpdates = b.untracked;
        riskCapital = Math.min(b.startingCapital, Math.max(0,Math.subtractExact(b.currentCapital,b.upper)));
        long conservativeNet = Math.subtractExact(Math.addExact(b.lower, b.marked), b.reserve);
        drawdownUpper = conservativeNet < 0 ? Math.negateExact(conservativeNet) : 0;
        remainingLossBudget = Math.subtractExact(fifteenPercent(riskCapital), drawdownUpper);
        status = "AVAILABLE";
        String summary = remainingLossBudget > 0 ? b.verifiedCash
                ? "Verified current cash may fund unrelated buys. Unpriced stock remains recorded and affected items are paused; its unknown resale value is not a confirmed loss."
            : b.currentAccount && b.historical>0
                ? "Current-account risk available. Incomplete historical trades remain paused."
                : "Durable account risk available."
            : riskCapital == 0 ? "No observed trading capital is available."
            : uncertainLossReserve > 0
                ? b.verifiedCash ? "Tracked stock missing from current physical evidence remains reserved at full cost. Quiet-price uncertainty does not consume the loss allowance."
                : b.currentAccount ? "Unseen or unpriced tracked stock is reserved at full cost against the current loss allowance. This reserve is not a confirmed loss."
                : "Unresolved trade evidence contributes an uncertainty reserve to the account's 15% risk allowance. "
                    + "This reserve is not a confirmed loss."
                : b.currentAccount?"The recorded and currently reserved loss guard has reached its 15% allowance."
                    : "The conservative account loss bound has reached its 15% risk allowance.";
        reason=summary+(b.verifiedCash
            ? " Unpriced acquisition cost retained: "+String.format(java.util.Locale.ROOT,"%,d GP",b.unpriced)
                +"; valuation-only uncertainty excluded from the loss charge: "+String.format(java.util.Locale.ROOT,"%,d GP",b.excluded)+". "+b.valuationDetail
            : b.currentAccount && b.unpriced>0 && remainingLossBudget<=0
            ? " Current stock reserve: "+String.format(java.util.Locale.ROOT,"%,d GP",b.unpriced)+". "
                +b.valuationDetail+"Buy eligibility is rechecked automatically with new prices and current holdings. Historical uncertainty stays separate."
            : b.currentAccount && !b.valuationDetail.isBlank()?" "+b.valuationDetail:"");
        if (!isValid()) throw new IllegalArgumentException("invalid available risk state");
    }
    public static AccountRiskSnapshot unavailable(String account, long at, String reason) {
        return new AccountRiskSnapshot(account, at, reason);
    }
    public static Builder builder(String account, String window, String session, long startedAt, long cursor, long at, long marketAt) {
        return new Builder(account, window, session, startedAt, cursor, at, marketAt);
    }
    public static final class Builder {
        private final String accountId, windowId, sessionId;
        private final long startedAt, cursor, at, marketAt;
        private long startingCapital, currentCapital, lower, upper, marked, reserve, bounded, unresolved, untracked, validUntil;
        private boolean currentAccount;
        private boolean verifiedCash;
        private long historical,unpriced;
        private long excluded;
        private java.util.Set<Integer> paused=java.util.Collections.emptySet();
        private String valuationDetail="";
        private Builder(String account, String window, String session, long start, long cursor, long at, long marketAt) {
            accountId = account; windowId = window; sessionId = session; startedAt = start;
            this.cursor = cursor; this.at = at; this.marketAt = marketAt;
            validUntil = Math.addExact(marketAt,120);
        }
        public Builder capital(long starting, long current) { startingCapital = starting; currentCapital = current; return this; }
        public Builder realized(long lower, long upper, long updates) { this.lower = lower; this.upper = upper; bounded = updates; return this; }
        public Builder uncertainty(long possibleLostCost, long managedUpdates, long untrackedUpdates) {
            reserve = possibleLostCost; unresolved = managedUpdates; untracked = untrackedUpdates; return this;
        }
        public Builder marked(long value) { marked = value; return this; }
        /** Historical unknown money remains reported, not subtracted as a confirmed current loss. */
        public Builder currentAccountPolicy(long historicalReserve,long unpricedOpenCost) {
            currentAccount=true;historical=historicalReserve;unpriced=unpricedOpenCost;return this;
        }
        /** Owner-approved V3: only physically proven stock's price uncertainty is excluded. */
        public Builder verifiedCashPolicy(long historicalReserve,long unpricedOpenCost,long unseenCost,java.util.Set<Integer> pausedItems) {
            currentAccount=true;verifiedCash=true;historical=historicalReserve;unpriced=unpricedOpenCost;
            excluded=Math.subtractExact(unpricedOpenCost,unseenCost);paused=new java.util.HashSet<>(pausedItems);return this;
        }
        public Builder validUntil(long value) { validUntil = value; return this; }
        /** Display explanation only; this text never changes reserve arithmetic or entry authority. */
        public Builder valuationDetail(String value) { valuationDetail=value==null?"":value;return this; }
        public AccountRiskSnapshot build() { return new AccountRiskSnapshot(this); }
    }
    /** Exact integer floor without multiplying a full account value by fifteen. */
    private static long fifteenPercent(long capital) {
        return Math.addExact(Math.multiplyExact(capital / 100, 15), (capital % 100) * 15 / 100);
    }
    public boolean isValid() {
        if ((!"1".equals(schemaVersion) && !"2".equals(schemaVersion)) || accountId == null || !accountId.matches("rs-[0-9a-f]{64}")
            || evaluatedAt < 0 || reason == null || reason.isBlank()) return false;
        if("2".equals(schemaVersion)) {
            if(!currentPolicy(policyVersion) || historicalUncertaintyReserve==null || historicalUncertaintyReserve<0
                || unpricedManagedCostReserve==null || unpricedManagedCostReserve<0 || uncertainLossReserve==null
                || (!VERIFIED_CASH_POLICY.equals(policyVersion) && unpricedManagedCostReserve>uncertainLossReserve))return false;
        }else if(policyVersion!=null || historicalUncertaintyReserve!=null || unpricedManagedCostReserve!=null)return false;
        if(VERIFIED_CASH_POLICY.equals(policyVersion)) {
            if(valuationUncertaintyExcluded==null || valuationUncertaintyExcluded<0
                || valuationUncertaintyExcluded>unpricedManagedCostReserve || valuationPausedItems==null
                || valuationPausedItems.stream().anyMatch(item->item==null || item<=0)
                || unpricedManagedCostReserve-valuationUncertaintyExcluded>uncertainLossReserve
                || valuationUncertaintyExcluded>0 && valuationPausedItems.isEmpty())return false;
        }else if(valuationUncertaintyExcluded!=null || valuationPausedItems!=null)return false;
        if ("UNAVAILABLE".equals(status)) return validUntil == null && windowId == null && sessionId == null && windowStartedAt == null
            && ledgerCursor == null && marketObservedAt == null && startingCapital == null && currentCapital == null
            && boundedRealizedProfitLower == null && boundedRealizedProfitUpper == null && markedUnrealizedProfit == null
            && uncertainLossReserve == null && riskCapital == null && drawdownUpper == null && remainingLossBudget == null
            && boundedSaleUpdates == 0 && unresolvedManagedSaleUpdates == 0 && untrackedUnpricedSaleUpdates == 0;
        if (!"AVAILABLE".equals(status) || validUntil == null || validUntil < evaluatedAt
            || validUntil - evaluatedAt > 150
            || windowId == null || windowId.isBlank() || sessionId == null || sessionId.isBlank()
            || windowStartedAt == null || ledgerCursor == null || marketObservedAt == null || startingCapital == null
            || currentCapital == null || boundedRealizedProfitLower == null || boundedRealizedProfitUpper == null
            || markedUnrealizedProfit == null || uncertainLossReserve == null || riskCapital == null
            || drawdownUpper == null || remainingLossBudget == null || windowStartedAt <= 0 || windowStartedAt > evaluatedAt
            || ledgerCursor < 0 || marketObservedAt <= 0 || marketObservedAt > evaluatedAt || startingCapital <= 0
            || currentCapital < 0 || boundedRealizedProfitLower > boundedRealizedProfitUpper || uncertainLossReserve < 0
            || (boundedSaleUpdates == 0 && (boundedRealizedProfitLower != 0 || boundedRealizedProfitUpper != 0))
            || boundedSaleUpdates < 0 || unresolvedManagedSaleUpdates < 0 || untrackedUnpricedSaleUpdates < 0) return false;
        if(validUntil-marketObservedAt>120)return false;
        try {
            long capital = Math.min(startingCapital, Math.max(0,Math.subtractExact(currentCapital,boundedRealizedProfitUpper)));
            long net = Math.subtractExact(Math.addExact(boundedRealizedProfitLower, markedUnrealizedProfit), uncertainLossReserve);
            long drawdown = net < 0 ? Math.negateExact(net) : 0;
            return riskCapital == capital && drawdownUpper == drawdown
                && remainingLossBudget == Math.subtractExact(fifteenPercent(capital), drawdown);
        } catch (ArithmeticException overflow) { return false; }
    }
    public boolean isAvailable() { return "AVAILABLE".equals(status) && isValid(); }
    private static boolean currentPolicy(String value) {
        return VERIFIED_CASH_POLICY.equals(value) || CURRENT_ACCOUNT_POLICY.equals(value) || LEGACY_CURRENT_ACCOUNT_POLICY.equals(value);
    }
    public boolean isCurrentAccountPolicy() { return "2".equals(schemaVersion) && currentPolicy(policyVersion); }
    public Long getHistoricalUncertaintyReserve() { return historicalUncertaintyReserve; }
    public Long getUnpricedManagedCostReserve() { return unpricedManagedCostReserve; }
    public boolean isVerifiedCashPolicy() { return VERIFIED_CASH_POLICY.equals(policyVersion); }
    public long getValuationUncertaintyExcluded() { return valuationUncertaintyExcluded==null?0:valuationUncertaintyExcluded; }
    public java.util.Set<Integer> getValuationPausedItems() {
        return valuationPausedItems==null?java.util.Collections.emptySet():java.util.Collections.unmodifiableSet(valuationPausedItems);
    }
    public String getAccountId() { return accountId; }
    public String getWindowId() { return windowId; }
    public String getSessionId() { return sessionId; }
    public String getReason() { return reason; }
    public long getEvaluatedAt() { return evaluatedAt; }
    public Long getValidUntil() { return validUntil; }
    public Long getWindowStartedAt() { return windowStartedAt; }
    public Long getLedgerCursor() { return ledgerCursor; }
    public Long getMarketObservedAt() { return marketObservedAt; }
    public Long getStartingCapital() { return startingCapital; }
    public Long getCurrentCapital() { return currentCapital; }
    public Long getRiskCapital() { return riskCapital; }
    public Long getBoundedRealizedProfitLower() { return boundedRealizedProfitLower; }
    public Long getBoundedRealizedProfitUpper() { return boundedRealizedProfitUpper; }
    public Long getMarkedUnrealizedProfit() { return markedUnrealizedProfit; }
    public Long getUncertainLossReserve() { return uncertainLossReserve; }
    public Long getDrawdownUpper() { return drawdownUpper; }
    public Long getRemainingLossBudget() { return remainingLossBudget; }
    public long getBoundedSaleUpdates() { return boundedSaleUpdates; }
    public long getUnresolvedManagedSaleUpdates() { return unresolvedManagedSaleUpdates; }
    public long getUntrackedUnpricedSaleUpdates() { return untrackedUnpricedSaleUpdates; }
}
