package com.flippingfriend.core;

import com.flippingfriend.model.FillEstimate;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** Immutable model claim for one exact order, not execution evidence or an approved strategy. */
public final class ExecutionForecast {
    public static final String MODEL_VERSION="PUBLIC_PRICE_FILL_V1";
    private final String schemaVersion,modelVersion;
    private final Double captureRate,evidenceWeight,waitWeight,seasonalMultiplier,waitMultiplier,horizonSeconds;
    private final Boolean lumpyArrivals,buying;
    private final int itemId,price,quantity,scoredBuckets,reachableBuckets;
    private final long historyWindowSeconds,reachableVolume;
    private final Double rawWaitSeconds,workingSeconds,floorOverheadSeconds,expectedTotalSeconds,completionProbability;

    public ExecutionForecast(String modelVersion,double captureRate,double evidenceWeight,double waitWeight,boolean lumpy,
            int item,boolean buying,int price,int quantity,double horizonHours,double season,long window,
            int scored,int reachable,long volume,FillEstimate estimate,double correction,double totalHours) {
        schemaVersion="1";this.modelVersion=modelVersion;this.captureRate=captureRate;this.evidenceWeight=evidenceWeight;
        this.waitWeight=waitWeight;lumpyArrivals=lumpy;itemId=item;this.buying=buying;this.price=price;this.quantity=quantity;
        horizonSeconds=horizonHours*3600;seasonalMultiplier=season;historyWindowSeconds=window;scoredBuckets=scored;
        reachableBuckets=reachable;reachableVolume=volume;waitMultiplier=correction;
        rawWaitSeconds=estimate.getWaitHours()*3600;workingSeconds=quantity/estimate.getUnitsPerHour()*3600;
        expectedTotalSeconds=totalHours*3600;completionProbability=estimate.getProbability();
        double residual=expectedTotalSeconds-rawWaitSeconds*correction-workingSeconds;
        floorOverheadSeconds=residual<0 && residual>-1e-9*Math.max(1,expectedTotalSeconds)?0:residual;
    }
    public boolean isValid() {
        return "1".equals(schemaVersion) && MODEL_VERSION.equals(modelVersion) && lumpyArrivals!=null && buying!=null
            && itemId>0 && price>0 && quantity>0 && historyWindowSeconds>0 && scoredBuckets>0
            && reachableBuckets>0 && reachableBuckets<=scoredBuckets && reachableVolume>0
            && positive(captureRate) && captureRate>=.01 && captureRate<=1 && nonnegative(evidenceWeight)
            && nonnegative(waitWeight) && positive(seasonalMultiplier) && nonnegative(waitMultiplier) && positive(horizonSeconds)
            && nonnegative(rawWaitSeconds) && positive(workingSeconds) && nonnegative(floorOverheadSeconds)
            && positive(expectedTotalSeconds) && nonnegative(completionProbability) && completionProbability<=1
            && close(expectedTotalSeconds,rawWaitSeconds*waitMultiplier+workingSeconds+floorOverheadSeconds);
    }
    private static boolean positive(Double value){return value!=null && Double.isFinite(value) && value>0;}
    private static boolean nonnegative(Double value){return value!=null && Double.isFinite(value) && value>=0;}
    private static boolean close(double a,double b){return Math.abs(a-b)<=1e-9*Math.max(1,Math.max(Math.abs(a),Math.abs(b)));}
    public boolean matches(int item,boolean side,int p,int q) {
        return isValid() && itemId==item && buying==side && price==p && quantity==q;
    }
    public boolean matches(OfferEvent e) {
        return e!=null && matches(e.getItemId(),e.isBuying(),e.getPrice(),e.getTotalQuantity())
            && close(expectedTotalSeconds,e.getPredictedMinutes()*60)
            && Math.abs(completionProbability-e.getPredictedCompletion())<=1e-12;
    }
    public String getSchemaVersion(){return schemaVersion;}
    public String getModelVersion(){return modelVersion;}
    public int getItemId(){return itemId;}
    public boolean isBuying(){return Boolean.TRUE.equals(buying);}
    public int getPrice(){return price;}
    public int getQuantity(){return quantity;}
    public double getHorizonSeconds(){return horizonSeconds==null?Double.NaN:horizonSeconds;}
    public double getRawWaitSeconds(){return rawWaitSeconds==null?Double.NaN:rawWaitSeconds;}
    public double getWorkingSeconds(){return workingSeconds==null?Double.NaN:workingSeconds;}
    public double getFloorOverheadSeconds(){return floorOverheadSeconds==null?Double.NaN:floorOverheadSeconds;}
    public double getWaitMultiplier(){return waitMultiplier==null?Double.NaN:waitMultiplier;}
    public double getExpectedTotalSeconds(){return expectedTotalSeconds==null?Double.NaN:expectedTotalSeconds;}
    public double getCompletionProbability(){return completionProbability==null?Double.NaN:completionProbability;}
    /** Canonical content identity for later frozen manifests; no wall clock or offer identity is inferred. */
    public String getFingerprint() {
        if(!isValid())return null;
        String material=String.join("|",schemaVersion,modelVersion,itemId+"",buying+"",price+"",quantity+"",
            historyWindowSeconds+"",scoredBuckets+"",reachableBuckets+"",reachableVolume+"",lumpyArrivals+"",
            Double.toHexString(captureRate),Double.toHexString(evidenceWeight),Double.toHexString(waitWeight),
            Double.toHexString(seasonalMultiplier),Double.toHexString(waitMultiplier),Double.toHexString(horizonSeconds),
            Double.toHexString(rawWaitSeconds),Double.toHexString(workingSeconds),Double.toHexString(floorOverheadSeconds),
            Double.toHexString(expectedTotalSeconds),Double.toHexString(completionProbability));
        try {
            byte[] digest=MessageDigest.getInstance("SHA-256").digest(material.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex=new StringBuilder();for(byte b:digest)hex.append(String.format(java.util.Locale.ROOT,"%02x",b&255));
            return hex.toString();
        }catch(java.security.NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}
    }
}
