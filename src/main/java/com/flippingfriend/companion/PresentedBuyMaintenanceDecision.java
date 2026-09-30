package com.flippingfriend.companion;

import com.flippingfriend.core.PolicyDecision;
import com.flippingfriend.model.Suggestion;

/** A validated buy-maintenance decision paired with its manual presentation. */
public final class PresentedBuyMaintenanceDecision
{
    private final PolicyDecision decision;
    private final Suggestion suggestion;
    PresentedBuyMaintenanceDecision(PolicyDecision decision, Suggestion suggestion)
    {
        if (decision == null || suggestion == null) throw new IllegalArgumentException("decision and suggestion are required");
        this.decision = decision;
        this.suggestion = suggestion;
    }
    public PolicyDecision getDecision() { return decision; }
    public Suggestion getSuggestion() { return suggestion; }
}
