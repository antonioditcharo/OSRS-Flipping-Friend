package com.flippingfriend.core;

/** Canonical lifecycle states shared by offer projections and policies. */
public enum OfferLifecycleState
{
    EMPTY,
    BUY_OPEN,
    BUY_PARTIAL,
    BUY_FILLED_UNCOLLECTED,
    BUY_CANCELLED_UNCOLLECTED,
    POSITION_AVAILABLE,
    SELL_OPEN,
    SELL_PARTIAL,
    SELL_FILLED_UNCOLLECTED,
    SELL_CANCELLED_UNCOLLECTED,
    REPRICE_CANCEL_PENDING,
    REPRICE_COLLECT_PENDING,
    REPRICE_READY,
    ERROR_RECONCILIATION
}
