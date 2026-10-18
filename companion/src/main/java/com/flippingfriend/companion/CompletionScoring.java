package com.flippingfriend.companion;

/** A completion-only challenger never changes wait/work timing or financial authority. */
interface CompletionScoring {
    CompletionScoring BASELINE=(buy,horizon,p)->p;
    double probability(boolean buying,double horizonSeconds,double baselineProbability);
}
