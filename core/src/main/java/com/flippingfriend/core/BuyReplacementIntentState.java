package com.flippingfriend.core;

/** Workflow state for a proposed buy-remainder replacement. States do not perform game input. */
public enum BuyReplacementIntentState
{
    CANCEL_AUTHORIZED,
    CANCEL_OBSERVED,
    COLLECTION_REQUIRED,
    ASSETS_RETURNED,
    REPLACEMENT_ELIGIBILITY_PENDING,
    REPLACEMENT_AUTHORIZED,
    ABANDONED,
    RECONCILIATION_REQUIRED
}
