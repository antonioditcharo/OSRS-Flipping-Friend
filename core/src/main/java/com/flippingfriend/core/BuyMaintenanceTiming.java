package com.flippingfriend.core;

/** Original order timing for workflow patience; this never qualifies execution learning. */
public final class BuyMaintenanceTiming {
    private BuyMaintenanceTiming() { }

    public static double originalExpectedMinutes(OfferEvent event) {
        if(event==null || !event.isAuthenticatedObservation() || !event.isBuying()
            || !"BUYING".equals(event.getEventType()) || event.getPredictionSourceEventId()==null)return Double.NaN;
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
