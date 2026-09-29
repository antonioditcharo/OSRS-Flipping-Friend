package com.flippingfriend.companion;

/** Deterministic comparison outcome between the current and shadow entry presentations. */
public enum EntryDecisionParityStatus
{
    MATCH,
    ACTION_MISMATCH,
    ITEM_MISMATCH,
    ECONOMICS_MISMATCH,
    PRESENTATION_MISMATCH,
    LEGACY_RESULT_MISSING,
    SHADOW_RESULT_MISSING,
    INVALID_SHADOW_LINEAGE
}
