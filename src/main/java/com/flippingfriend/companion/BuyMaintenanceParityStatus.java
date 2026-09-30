package com.flippingfriend.companion;

/** Deterministic comparison outcome between local and shadow buy-maintenance presentations. */
public enum BuyMaintenanceParityStatus
{
    MATCH,
    ACTION_MISMATCH,
    ITEM_MISMATCH,
    SOURCE_OFFER_MISMATCH,
    PRESENTATION_MISMATCH,
    LEGACY_RESULT_MISSING,
    SHADOW_RESULT_MISSING,
    INVALID_SHADOW_LINEAGE
}
