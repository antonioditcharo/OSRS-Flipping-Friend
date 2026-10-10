package com.flippingfriend.companion;
/** Per-candidate result of the production per-candidate gates and single-slot admission checks. */
enum BuyReplacementQuantityCandidateConstraintStatus {
    ADMISSIBLE,
    BELOW_MINIMUM,
    NO_CAPITAL,
    CANNOT_COMPLETE,
    NOT_WORTH_DOING,
    INSUFFICIENT_COINS,
    LOSS_BUDGET_EXCEEDED,
    ITEM_EXPOSURE_EXCEEDED,
    GROUP_EXPOSURE_EXCEEDED
}
