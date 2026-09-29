package com.flippingfriend.core;

/** Machine-readable result of companion portfolio planning. */
public enum PortfolioPlanOutcome
{
    READY,
    MARKET_DATA_STALE,
    DRAWDOWN_LIMIT_REACHED,
    SELL_ONLY_MODE,
    NO_FREE_SLOT,
    NO_ELIGIBLE_CANDIDATE,
    PORTFOLIO_CONSTRAINT,
    COMPANION_STATE_UNAVAILABLE,
    UNKNOWN
}
