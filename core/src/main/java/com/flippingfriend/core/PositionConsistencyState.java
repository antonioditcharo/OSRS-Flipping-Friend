package com.flippingfriend.core;

/** Whether a durable position is safe to use or requires snapshot reconciliation. */
public enum PositionConsistencyState
{
    READY,
    RECONCILE
}
