package com.flippingfriend.companion;
/** Account-constraint feasibility outcome for a selected replacement; evidence only, never authorization. */
enum BuyReplacementQuantityCandidateFeasibilityOutcome {
    FEASIBLE,
    NO_SELECTION,
    NO_FREE_SLOT,
    INSUFFICIENT_COINS,
    LOSS_BUDGET_EXCEEDED,
    ITEM_EXPOSURE_EXCEEDED,
    GROUP_EXPOSURE_EXCEEDED
}
