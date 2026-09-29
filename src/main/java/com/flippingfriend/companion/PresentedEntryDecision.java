package com.flippingfriend.companion;

import com.flippingfriend.core.PolicyDecision;
import com.flippingfriend.model.Suggestion;

/** A validated entry decision paired with the manual presentation derived from it. */
public final class PresentedEntryDecision
{
    private final PolicyDecision decision;
    private final Suggestion suggestion;

    PresentedEntryDecision(PolicyDecision decision, Suggestion suggestion)
    {
        if (decision == null) throw new IllegalArgumentException("decision is required");
        if (suggestion == null) throw new IllegalArgumentException("suggestion is required");
        this.decision = decision;
        this.suggestion = suggestion;
    }

    public PolicyDecision getDecision() { return decision; }
    public Suggestion getSuggestion() { return suggestion; }
}
