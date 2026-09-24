package com.flippingfriend.core;

/** Result of applying one source observation to an offer lifecycle projection. */
public final class OfferLifecycleTransition
{
    private final OfferLifecycleState previousState;
    private final OfferLifecycleState newState;
    private final OfferLifecycleAction action;
    private final OfferLifecycleProjection projection;
    private final OfferEvent sourceEvent;
    private final boolean accepted;
    private final boolean idempotent;
    private final String reason;
    private final PositionAccountingEffect accountingEffect;

    OfferLifecycleTransition(OfferLifecycleState previousState, OfferLifecycleState newState,
        OfferLifecycleAction action, OfferLifecycleProjection projection, OfferEvent sourceEvent,
        boolean accepted, boolean idempotent, String reason,
        PositionAccountingEffect accountingEffect)
    {
        this.previousState = previousState;
        this.newState = newState;
        this.action = action;
        this.projection = projection;
        this.sourceEvent = sourceEvent;
        this.accepted = accepted;
        this.idempotent = idempotent;
        this.reason = reason;
        this.accountingEffect = accountingEffect;
    }

    public OfferLifecycleState getPreviousState() { return previousState; }
    public OfferLifecycleState getNewState() { return newState; }
    public OfferLifecycleAction getAction() { return action; }
    public OfferLifecycleProjection getProjection() { return projection; }
    public OfferEvent getSourceEvent() { return sourceEvent; }
    public boolean isAccepted() { return accepted; }
    public boolean isIdempotent() { return idempotent; }
    public String getReason() { return reason; }
    public PositionAccountingEffect getAccountingEffect() { return accountingEffect; }
}
