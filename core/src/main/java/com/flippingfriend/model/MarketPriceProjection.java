package com.flippingfriend.model;

import com.flippingfriend.data.Candle;
import com.flippingfriend.data.LatestPrice;
import java.util.*;

/** Descriptive public-price analogue projection. Never entry authority or execution evidence. */
public final class MarketPriceProjection {
    public static final String VERSION="HISTORICAL_RETURN_QUANTILES_V1";
    public static final int MIN_ANALOGUES=24;
    public static final class Side {
        public final double lower,median,upper;
        Side(double lower,double median,double upper){this.lower=lower;this.median=median;this.upper=upper;}
    }
    public static final class Point {
        public final long at;
        public final Side low,high;
        public final int analogues;
        Point(long at,Side low,Side high,int analogues){this.at=at;this.low=low;this.high=high;this.analogues=analogues;}
    }
    public static final class Projection {
        public final String modelVersion,reason;
        public final long observedAt,originAt;
        public final int bucketSeconds;
        public final List<Point> points;
        Projection(long now,long origin,int bucket,List<Point> points,String reason) {
            this(VERSION,now,origin,bucket,points,reason);
        }
        Projection(String modelVersion,long now,long origin,int bucket,List<Point> points,String reason) {
            this.modelVersion=modelVersion;observedAt=now;originAt=origin;bucketSeconds=bucket;this.points=List.copyOf(points);this.reason=reason;
        }
        public boolean isAvailable(){return points.size()>1;}
    }
    /** Same-length, complete past return windows; no forward filling or extrapolated volume. */
    public Projection project(List<Candle> history,int bucket,long now,int steps) {
        if(bucket<=0 || now<0 || steps<1 || steps>48)throw new IllegalArgumentException("Invalid projection horizon");
        TreeMap<Long,Candle> rows=new TreeMap<>();
        for(Candle c:history==null?List.<Candle>of():history) {
            if(c==null || c.getTimestamp()<0 || c.getTimestamp()>now-bucket)continue;
            Candle previous=rows.putIfAbsent(c.getTimestamp(),c);
            if(previous!=null && !same(previous,c))return absent(now,bucket,"Conflicting history buckets");
        }
        var data=new ArrayList<>(rows.values());int anchor=data.size()-1;
        while(anchor>=0 && !complete(data.get(anchor)))anchor--;
        if(anchor<0)return absent(now,bucket,"Waiting for completed two-sided history");
        Candle last=data.get(anchor);long origin=last.getTimestamp()+bucket;
        if(now-origin>2L*bucket)return absent(now,bucket,"The last completed two-sided bucket is too old for a projection");
        List<Point> points=new ArrayList<>();
        points.add(new Point(origin,new Side(last.getAvgLowPrice(),last.getAvgLowPrice(),last.getAvgLowPrice()),
            new Side(last.getAvgHighPrice(),last.getAvgHighPrice(),last.getAvgHighPrice()),0));
        int[] bad=new int[anchor+2],gaps=new int[anchor+2];
        for(int i=0;i<=anchor;i++) {
            bad[i+1]=bad[i]+(complete(data.get(i))?0:1);
            gaps[i+1]=gaps[i]+(i>0 && data.get(i).getTimestamp()-data.get(i-1).getTimestamp()!=bucket?1:0);
        }
        String unavailable="Fewer than 24 complete historical comparisons";
        for(int h=1;h<=steps;h++) {
            List<Double> lows=new ArrayList<>(),highs=new ArrayList<>();
            for(int end=h;end<=anchor;end++) {
                int start=end-h;
                if(bad[end+1]!=bad[start] || gaps[end+1]!=gaps[start+1])continue;
                Candle a=data.get(start),b=data.get(end);
                lows.add(Math.log(b.getAvgLowPrice()/(double)a.getAvgLowPrice()));
                highs.add(Math.log(b.getAvgHighPrice()/(double)a.getAvgHighPrice()));
            }
            if(lows.size()<MIN_ANALOGUES)break;
            Side low=side(last.getAvgLowPrice(),lows),high=side(last.getAvgHighPrice(),highs);
            if(low==null || high==null){unavailable="Historical return range exceeds supported Grand Exchange prices";break;}
            points.add(new Point(origin+(long)h*bucket,low,high,lows.size()));
        }
        return new Projection(now,origin,bucket,points,points.size()>1
            ?"Experimental median and central 50% of historical returns; coverage is not guaranteed"
            :unavailable);
    }
    private static Projection absent(long now,int bucket,String reason){return new Projection(now,0,bucket,List.of(),reason);}
    /** Rebase return scenarios on an actual, timestamped public quote, never a cost or order target. */
    public Projection anchor(Projection p,LatestPrice quote,long snapshotAt,long now) {
        if(!p.isAvailable())return p;
        if(quote==null || !quote.isComplete() || quote.getLowTime()==null || quote.getHighTime()==null
            || quote.getLowTime()<=0 || quote.getHighTime()<=0 || snapshotAt<=0 || snapshotAt>now || now-snapshotAt>150
            || quote.getLowTime()>snapshotAt || quote.getHighTime()>snapshotAt
            || snapshotAt-quote.getLowTime()>7200 || snapshotAt-quote.getHighTime()>7200)
            return absent(now,p.bucketSeconds,"Waiting for recent timestamped market quotes to anchor the projection");
        Point first=p.points.get(0);List<Point> points=new ArrayList<>();
        for(Point point:p.points) {
            Side low=scale(point.low,quote.getLow()/first.low.median),high=scale(point.high,quote.getHigh()/first.high.median);
            if(low==null || high==null)break;
            points.add(new Point(snapshotAt+point.at-p.originAt,low,high,point.analogues));
        }
        return new Projection(now,snapshotAt,p.bucketSeconds,points,p.reason);
    }
    private static Side scale(Side side,double factor) {
        double low=side.lower*factor,high=side.upper*factor;
        return !Double.isFinite(high) || low<1 || high>Integer.MAX_VALUE?null:new Side(low,side.median*factor,high);
    }
    private static boolean complete(Candle c){return c.hasBothSides() && c.getHighPriceVolume()>0 && c.getLowPriceVolume()>0;}
    private static boolean same(Candle a,Candle b){return Objects.equals(a.getAvgHighPrice(),b.getAvgHighPrice())
        && Objects.equals(a.getAvgLowPrice(),b.getAvgLowPrice()) && a.getHighPriceVolume()==b.getHighPriceVolume()
        && a.getLowPriceVolume()==b.getLowPriceVolume();}
    private static Side side(int anchor,List<Double> returns) {
        Collections.sort(returns);
        double low=anchor*Math.exp(quantile(returns,.25)),median=anchor*Math.exp(quantile(returns,.5)),high=anchor*Math.exp(quantile(returns,.75));
        if(!Double.isFinite(low) || !Double.isFinite(high) || low<1 || high>Integer.MAX_VALUE)return null;
        return new Side(low,median,high);
    }
    private static double quantile(List<Double> sorted,double q) {
        double index=(sorted.size()-1)*q;int lo=(int)index,hi=(int)Math.ceil(index);
        return sorted.get(lo)+(sorted.get(hi)-sorted.get(lo))*(index-lo);
    }

    public static final class Evaluation {
        public final String modelVersion=VERSION,evidence="ROLLING_ORIGIN_PUBLIC_BUCKETS",promotionStatus="EXPERIMENTAL_DISPLAY_ONLY";
        public final String availabilityAssumption="RETROSPECTIVE_BUCKET_CLOSE; ORIGINAL PUBLICATION AND REVISION CLOCKS UNKNOWN";
        public final int horizonSteps,known,unknown,abstained;
        public final Double knownOutcomeProjectionMaePercent,knownOutcomePersistenceMaePercent,knownOutcomeRangeCoverage,coverageLower,coverageUpper;
        Evaluation(int h,int known,int unknown,int abstained,double error,double baseline,int covered) {
            horizonSteps=h;this.known=known;this.unknown=unknown;this.abstained=abstained;
            knownOutcomeProjectionMaePercent=known==0?null:100*error/(known*2);
            knownOutcomePersistenceMaePercent=known==0?null:100*baseline/(known*2);
            knownOutcomeRangeCoverage=known==0?null:covered/(known*2.0);
            coverageLower=known+unknown==0?null:covered/(2.0*(known+unknown));
            coverageUpper=known+unknown==0?null:(covered+2.0*unknown)/(2.0*(known+unknown));
        }
    }
    /** Non-overlapping test horizons. Each forecast sees only its completed training prefix. */
    public Evaluation evaluate(List<Candle> history,int bucket,long observedAt,int horizonSteps) {
        if(bucket<=0 || observedAt<0 || horizonSteps<1 || horizonSteps>48)throw new IllegalArgumentException("Invalid evaluation horizon");
        TreeMap<Long,Candle> rows=new TreeMap<>();
        for(Candle c:history==null?List.<Candle>of():history)if(c!=null && c.getTimestamp()>=0 && c.getTimestamp()<=observedAt-bucket) {
            Candle previous=rows.putIfAbsent(c.getTimestamp(),c);
            if(previous!=null && !same(previous,c))throw new IllegalArgumentException("Conflicting evaluation buckets");
        }
        var data=new ArrayList<>(rows.values());int known=0,unknown=0,abstained=0,covered=0;double error=0,baseline=0;
        for(int i=Math.max(48,MIN_ANALOGUES+horizonSteps);i<data.size();i+=horizonSteps) {
            Candle anchor=data.get(i);long origin=anchor.getTimestamp()+bucket;
            // A not-yet-observed target is right-censored, not a failed forecast.
            long targetAt=anchor.getTimestamp()+(long)horizonSteps*bucket;
            Projection p=project(data.subList(0,i+1),bucket,origin,horizonSteps);
            Candle target=rows.get(targetAt);
            if(!p.isAvailable() || p.points.size()<=horizonSteps || !complete(anchor)) {abstained++;continue;}
            if(target==null || !complete(target)) {unknown++;continue;}
            Point point=p.points.get(horizonSteps);known++;
            double[] actual={target.getAvgLowPrice(),target.getAvgHighPrice()},last={anchor.getAvgLowPrice(),anchor.getAvgHighPrice()};
            Side[] sides={point.low,point.high};
            for(int side=0;side<2;side++) {
                error+=Math.abs(sides[side].median-actual[side])/actual[side];
                baseline+=Math.abs(last[side]-actual[side])/actual[side];
                if(actual[side]>=sides[side].lower && actual[side]<=sides[side].upper)covered++;
            }
        }
        return new Evaluation(horizonSteps,known,unknown,abstained,error,baseline,covered);
    }
}
