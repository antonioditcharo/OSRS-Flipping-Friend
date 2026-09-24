package com.flippingfriend.core;

/** Canonical manual actions, deliberately separate from lifecycle states. */
public enum OfferLifecycleAction
{
    WAIT,
    COLLECT,
    PLACE_BUY,
    CANCEL_BUY,
    PLACE_SELL,
    CANCEL_SELL,
    REPLACE_BUY,
    REPLACE_SELL,
    CUT_LOSS,
    HOLD,
    RECONCILE
}
