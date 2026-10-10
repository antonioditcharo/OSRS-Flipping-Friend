package com.flippingfriend.companion;
/** Account readiness for a replacement, in the planner's refusal order; evidence only, never authorization. */
enum BuyReplacementAccountConstraintOutcome {
    READY,
    DRAWDOWN_LIMIT_REACHED,
    SELL_ONLY_MODE,
    PLAYER_REJECTED
}
