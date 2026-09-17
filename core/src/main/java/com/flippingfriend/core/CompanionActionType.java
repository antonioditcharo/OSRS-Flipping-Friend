package com.flippingfriend.core;

/** Actions currently owned by the companion decision boundary. */
public enum CompanionActionType
{
        /** No companion-owned action is available, with a reason for the caller. */
        WAIT,

        /** Collect a completed or cancelled Grand Exchange offer to free its slot. */
        COLLECT
}
