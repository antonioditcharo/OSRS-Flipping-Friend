package com.flippingfriend.session;

import com.flippingfriend.data.LatestPrice;
import java.time.Instant;

/** Immutable worker result tied to one exact observed sale. It cannot authorize an action. */
public final class SaleMaintenanceDiagnostic {
    private final TrackedOffer offer;
    public final SaleMaintenancePricing.Evaluation evaluation;
    public final String unavailable;
    public final long evaluatedAt, validUntil, highAge, lowAge;
    public final double originalExpectedMinutes;
    public final Integer highTradePrice,lowTradePrice;
    public final boolean personal;

    public SaleMaintenanceDiagnostic(TrackedOffer offer,SaleMaintenancePricing.Evaluation evaluation,
            String unavailable,LatestPrice price,long now,long ledgerExpiresAt,double originalExpectedMinutes,
            boolean personal) {
        this.offer=offer.copyObservation();this.evaluation=evaluation;this.unavailable=unavailable;
        this.evaluatedAt=now;this.originalExpectedMinutes=originalExpectedMinutes;this.personal=personal;
        highTradePrice=price==null?null:price.getHigh();lowTradePrice=price==null?null:price.getLow();
        highAge=price==null || price.getHighTime()==null?-1:price.secondsSinceHigh(Instant.ofEpochSecond(now));
        lowAge=price==null || price.getLowTime()==null?-1:price.secondsSinceLow(Instant.ofEpochSecond(now));
        long expiry=Math.min(Math.min(now+30,ledgerExpiresAt),(now/300+1)*300);
        // Expire at a policy boundary, even before the next scheduled worker pass.
        if(highAge>=0 && highAge<=300)expiry=Math.min(expiry,now+301-highAge);
        if(lowAge>=0 && lowAge<=300)expiry=Math.min(expiry,now+301-lowAge);
        if(evaluation!=null && evaluation.reassessAt>now)expiry=Math.min(expiry,evaluation.reassessAt);
        validUntil=expiry;
    }
    public TrackedOffer offer(){return offer.copyObservation();}
    public boolean matches(TrackedOffer current,long now) {
        return now>=evaluatedAt && now<validUntil && current!=null
            && "SELLING".equals(current.getState()) && !current.isBuying()
            && offer.getContinuationIdentity().equals(current.getContinuationIdentity())
            && offer.getQuantityFilled()==current.getQuantityFilled() && offer.getSpent()==current.getSpent()
            && offer.getLastFillObservedAt()==current.getLastFillObservedAt();
    }
    public static SaleMaintenanceDiagnostic unavailable(TrackedOffer offer,long now) {
        return new SaleMaintenanceDiagnostic(offer,null,
            "Current evaluation unavailable; waiting for confirmed account, offer and pricing evidence.",
            null,now,now+30,Double.NaN,false);
    }
}
