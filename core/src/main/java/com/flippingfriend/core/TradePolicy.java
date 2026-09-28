package com.flippingfriend.core;

/** Pure decision boundary. Implementations receive an immutable context and return one decision. */
public interface TradePolicy<C>
{
    PolicyDecision decide(C context);
}
