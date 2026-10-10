package com.flippingfriend.companion;
/** Constrained single-slot replacement selection outcome; names mirror the production plan outcomes. */
enum BuyReplacementQuantityConstrainedSelectionOutcome {
    SELECTED,
    NO_FREE_SLOT,
    NO_ELIGIBLE_CANDIDATE,
    PORTFOLIO_CONSTRAINT
}
