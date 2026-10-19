package com.flippingfriend.core;

import java.util.Objects;

/** Original prospective exit advice for one exact observed buy. Never quantity, cost or a risk mark. */
public final class AcquisitionExitContext {
    private final String sourceEventId;
    private final int targetSellPrice, stopPrice;
    private final double predictedSellMinutes;

    public AcquisitionExitContext(int targetSellPrice, int stopPrice, double predictedSellMinutes) {
        this(null, targetSellPrice, stopPrice, predictedSellMinutes);
    }
    private AcquisitionExitContext(String source, int target, int stop, double minutes) {
        sourceEventId=source;targetSellPrice=target;stopPrice=stop;predictedSellMinutes=minutes;
        if(!isValid())throw new IllegalArgumentException("invalid acquisition exit context");
    }
    public boolean isValid() {
        return targetSellPrice>0 && stopPrice>=0 && stopPrice<targetSellPrice
            && Double.isFinite(predictedSellMinutes) && predictedSellMinutes>0
            && (sourceEventId==null || !sourceEventId.isBlank());
    }
    public boolean isBound() { return isValid() && sourceEventId!=null; }
    public AcquisitionExitContext bind(String source) {
        if(source==null || source.isBlank() || sourceEventId!=null && !sourceEventId.equals(source))
            throw new IllegalArgumentException("exit context source changed");
        return new AcquisitionExitContext(source,targetSellPrice,stopPrice,predictedSellMinutes);
    }
    public String getSourceEventId(){return sourceEventId;}
    public int getTargetSellPrice(){return targetSellPrice;}
    public int getStopPrice(){return stopPrice;}
    public double getPredictedSellMinutes(){return predictedSellMinutes;}
    @Override public boolean equals(Object other) {
        if(!(other instanceof AcquisitionExitContext))return false;
        var p=(AcquisitionExitContext)other;
        return Objects.equals(sourceEventId,p.sourceEventId) && targetSellPrice==p.targetSellPrice
            && stopPrice==p.stopPrice && Double.compare(predictedSellMinutes,p.predictedSellMinutes)==0;
    }
    @Override public int hashCode(){return Objects.hash(sourceEventId,targetSellPrice,stopPrice,predictedSellMinutes);}
}
