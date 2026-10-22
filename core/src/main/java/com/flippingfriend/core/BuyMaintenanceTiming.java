package com.flippingfriend.core;

/** Original order timing for workflow patience; this never qualifies execution learning. */
public final class BuyMaintenanceTiming {
    private BuyMaintenanceTiming() { }

    public static double originalExpectedMinutes(OfferEvent event) {
        if(event==null || !event.isAuthenticatedObservation() || !event.isBuying()
            || !"BUYING".equals(event.getEventType()))return Double.NaN;
        var plan=event.getAcceptedBuyPlanProof();
        if(plan!=null || event.getAcceptedBuyPlanEvidenceVersion()!=null) {
            if(!"1".equals(event.getAcceptedBuyPlanEvidenceVersion()))return Double.NaN;
            if(plan!=null)return plan.matchesEpisode(event) && plan.getForecast()!=null
                ?plan.getForecast().getExpectedTotalSeconds()/60:Double.NaN;
            // An unknown exit plan cannot erase an independently qualified original prediction.
            // This strict learning association gives timing only, never target or stop authority.
            return event.getPredictionSourceEventId()!=null && event.isPredictionComparable()
                && Double.isFinite(event.getPredictedMinutes()) && event.getPredictedMinutes()>0
                ?event.getPredictedMinutes():Double.NaN;
        }
        if(event.getPredictionSourceEventId()==null)return Double.NaN;
        var forecast=event.getExecutionForecast();
        // Accepted callback ordering can retain an immutable claim even when second-resolution
        // learning attribution remains unqualified. Later fills inherit the original binding.
        if(event.hasAcceptedExitProof() && forecast!=null
            && forecast.matches(event.getItemId(),true,event.getPrice(),event.getTotalQuantity()))
            return forecast.getExpectedTotalSeconds()/60;
        return event.isPredictionComparable() && Double.isFinite(event.getPredictedMinutes()) && event.getPredictedMinutes()>0
            ?event.getPredictedMinutes():Double.NaN;
    }
}
