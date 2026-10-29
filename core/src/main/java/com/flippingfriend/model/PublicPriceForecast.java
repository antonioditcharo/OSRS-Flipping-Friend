package com.flippingfriend.model;

import com.flippingfriend.data.Candle;
import com.flippingfriend.data.LatestPrice;
import java.util.*;

/** Bounded, public-only rolling-origin price analysis. Not an execution or disposal authority. */
public final class PublicPriceForecast {
    public static final String VERSION="PUBLIC_PRICE_PATH_V1";
    public static final String CHART_VERSION="PUBLIC_PRICE_CHART_PATH_V2";
    private static final int[] HORIZONS={300,1800,7200,28800};
    private static final int[] CHART_HORIZONS={300,1800,3600,7200,14400,28800,43200,86400};
    private static final int MODELS=5,TRAIN=24,VALIDATE=12;
    private static final Map<Key,PublicPriceForecast> CACHE=Collections.synchronizedMap(
        new LinkedHashMap<Key,PublicPriceForecast>(64,.75f,true) {
            @Override protected boolean removeEldestEntry(Map.Entry<Key,PublicPriceForecast> e){return size()>128;}
        });
    private static final Map<Key,PublicPriceForecast> CHART_CACHE=Collections.synchronizedMap(
        new LinkedHashMap<Key,PublicPriceForecast>(16,.75f,true) {
            @Override protected boolean removeEldestEntry(Map.Entry<Key,PublicPriceForecast> e){return size()>32;}
        });
    private static final Set<Key> PENDING=Collections.synchronizedSet(new HashSet<>());
    private static final java.util.concurrent.Executor WORKER=new java.util.concurrent.ThreadPoolExecutor(1,1,30,
        java.util.concurrent.TimeUnit.SECONDS,new java.util.concurrent.ArrayBlockingQueue<>(64),r->{Thread t=new Thread(r,"public-price-analysis");t.setDaemon(true);t.setPriority(Thread.MIN_PRIORITY);return t;});
    public static final class Estimate {
        public final double lower,median,upper;
        public final String model;
        public final int comparisons;
        public final boolean directional;
        Estimate(double lower,double median,double upper,String model,int comparisons,boolean directional) {
            this.lower=lower;this.median=median;this.upper=upper;this.model=model;
            this.comparisons=comparisons;this.directional=directional;
        }
        public boolean available(){return comparisons>=12 && Double.isFinite(upper) && lower>=1 && upper<=Integer.MAX_VALUE;}
    }
    private static final class Fit {
        final double shift,lower,upper;final int model,count;
        Fit(double shift,double lower,double upper,int model,int count) {
            this.shift=shift;this.lower=lower;this.upper=upper;this.model=model;this.count=count;
        }
    }
    private static final class Series {
        final NavigableMap<Long,Candle> rows=new TreeMap<>();
        final int bucket;final boolean valid;final long origin;
        Series(List<Candle> input,int bucket,long now) {
            this.bucket=bucket;boolean ok=input==null || input.size()<=2048;
            if(input!=null && ok)for(Candle c:input)if(c!=null && c.getTimestamp()>=0 && c.getTimestamp()<=now-bucket) {
                if(c.getTimestamp()%bucket!=0 || c.getHighPriceVolume()<0 || c.getLowPriceVolume()<0){ok=false;break;}
                Candle previous=rows.putIfAbsent(c.getTimestamp(),c);
                if(previous!=null && !same(previous,c)){ok=false;break;}
            }
            while(rows.size()>720)rows.pollFirstEntry();
            origin=rows.isEmpty()?0:rows.lastKey()+bucket;
            valid=ok && origin>0 && now-origin<2L*bucket;
        }
        double price(long at,boolean high) {
            Candle c=rows.get(at);if(c==null)return Double.NaN;
            Integer price=high?c.getAvgHighPrice():c.getAvgLowPrice();
            int volume=high?c.getHighPriceVolume():c.getLowPriceVolume();
            return price==null || price<=0 || volume<=0?Double.NaN:Math.log(price);
        }
    }
    private final Fit[][] fits;
    private final int[] horizons;
    private final boolean chart;
    public final long observedAt;
    public final String historyFingerprint;
    private PublicPriceForecast(List<Candle> shortRows,List<Candle> hourly,long now,String fingerprint) {
        this(shortRows,hourly,null,now,fingerprint,false);
    }
    private PublicPriceForecast(List<Candle> shortRows,List<Candle> hourly,List<Candle> sixHourly,long now,String fingerprint,boolean chart) {
        this.chart=chart;horizons=chart?CHART_HORIZONS:HORIZONS;fits=new Fit[2][horizons.length];
        observedAt=now;historyFingerprint=fingerprint;Series shortSeries=new Series(shortRows,300,now),longSeries=new Series(hourly,3600,now);
        Series sixHourSeries=chart?new Series(sixHourly,21600,now):null;
        for(int side=0;side<2;side++)for(int h=0;h<horizons.length;h++) {
            Series series=h<2?shortSeries:longSeries;
            Fit result=chart?chartFit(series,side==1,horizons[h]/series.bucket):fit(series,side==1,horizons[h]/series.bucket);
            // Longer public history supplies disjoint day-ahead comparisons. Never shorten a
            // strategy horizon or use chart fits in live order selection.
            if(chart && horizons[h]>=43200 && result.count<TRAIN+VALIDATE) {
                Fit longer=chartFit(sixHourSeries,side==1,horizons[h]/sixHourSeries.bucket);
                if(longer.count>result.count)result=longer;
            }
            fits[side][h]=result;
        }
    }
    /** Display-only day-ahead fits, cached separately so browsing cannot evict strategy analysis. */
    public static PublicPriceForecast fitChart(List<Candle> shortRows,List<Candle> hourly,List<Candle> sixHourly,long now) {
        shortRows=copy(shortRows);hourly=copy(hourly);sixHourly=copy(sixHourly);
        Key key=new Key(shortRows,hourly,sixHourly,now);PublicPriceForecast existing=CHART_CACHE.get(key);
        if(existing!=null)return existing;
        PublicPriceForecast result=new PublicPriceForecast(shortRows,hourly,sixHourly,now,key.fingerprint(),true);
        CHART_CACHE.put(key,result);return result;
    }
    public static PublicPriceForecast cachedChart(List<Candle> shortRows,List<Candle> hourly,List<Candle> sixHourly,long now) {
        return CHART_CACHE.get(new Key(shortRows,hourly,sixHourly,now));
    }
    /** Keys copy every retained public field; mutation, conflicting duplicates and time advance cannot reuse a fit. */
    public static PublicPriceForecast fit(List<Candle> shortRows,List<Candle> hourly,long now) {
        shortRows=copy(shortRows);hourly=copy(hourly);
        Key key=new Key(shortRows,hourly,now);PublicPriceForecast existing=CACHE.get(key);
        if(existing!=null)return existing;
        PublicPriceForecast result=new PublicPriceForecast(shortRows,hourly,now,key.fingerprint());CACHE.put(key,result);return result;
    }
    /** Lookup only: chart/UI callers schedule a miss on their bounded background worker. */
    public static PublicPriceForecast cached(List<Candle> shortRows,List<Candle> hourly,long now){return CACHE.get(new Key(shortRows,hourly,now));}
    /** Live guidance never fits inline. A miss returns immediately to the existing price policy. */
    public static PublicPriceForecast ready(List<Candle> shortRows,List<Candle> hourly,long now) {
        if(shortRows!=null && shortRows.size()>2048 || hourly!=null && hourly.size()>2048)return null;
        Key key=new Key(shortRows,hourly,now);var result=CACHE.get(key);if(result!=null)return result;
        if(PENDING.size()>=65 || !PENDING.add(key))return null;
        try {
            List<Candle> shortCopy=copy(shortRows),longCopy=copy(hourly);
            if(!key.equals(new Key(shortCopy,longCopy,now))){PENDING.remove(key);return null;}
            WORKER.execute(()->{try{if(!CACHE.containsKey(key))CACHE.put(key,new PublicPriceForecast(shortCopy,longCopy,now,key.fingerprint()));}
                catch(RuntimeException unsupported){ /* Optional analysis never withholds the verified price path. */ }
                finally{PENDING.remove(key);}});
        }catch(java.util.concurrent.RejectedExecutionException full){PENDING.remove(key);}
        return null;
    }
    private static List<Candle> copy(List<Candle> rows) {
        if(rows==null)return null;
        if(rows.size()>2048)return Collections.nCopies(2049,null);
        List<Candle> result=new ArrayList<>();for(Candle c:rows)result.add(c==null?null:new Candle(c.getTimestamp(),c.getAvgHighPrice(),c.getAvgLowPrice(),c.getHighPriceVolume(),c.getLowPriceVolume()));return result;
    }
    public Estimate at(boolean high,int seconds,int anchor) {
        if(seconds<=0 || seconds>horizons[horizons.length-1] || anchor<=0)return missing();
        int upper=0;while(upper<horizons.length-1 && horizons[upper]<seconds)upper++;
        int lower=upper==0 || horizons[upper]==seconds?upper:upper-1;
        Fit b=fits[high?1:0][upper],a=fits[high?1:0][lower];
        if(a.count<12 || b.count<12)return missing();
        double weight=upper==lower?seconds/(double)horizons[upper]
            :(seconds-horizons[lower])/(double)(horizons[upper]-horizons[lower]);
        double shift=upper==lower?b.shift*weight:a.shift+(b.shift-a.shift)*weight;
        double lo=upper==lower?b.lower*Math.sqrt(weight):a.lower+(b.lower-a.lower)*weight;
        double hi=upper==lower?b.upper*Math.sqrt(weight):a.upper+(b.upper-a.upper)*weight;
        double median=anchor*Math.exp(shift),bottom=anchor*Math.exp(shift+Math.min(0,lo)),top=anchor*Math.exp(shift+Math.max(0,hi));
        if(!Double.isFinite(top) || bottom<1 || top>Integer.MAX_VALUE)return missing();
        return new Estimate(bottom,median,top,modelName(b.model),Math.min(a.count,b.count),a.model!=0 || b.model!=0);
    }
    public boolean directional(boolean high,int seconds,int anchor){Estimate e=at(high,seconds,anchor);return e.available() && e.directional;}
    public MarketPriceProjection.Projection projection(LatestPrice quote,long snapshotAt,long now,int hours) {
        if(quote==null || !quote.isComplete() || snapshotAt<=0 || snapshotAt>now || now-snapshotAt>150
            || quote.getLowTime()==null || quote.getHighTime()==null || quote.getLowTime()<=0 || quote.getHighTime()<=0
            || quote.getLowTime()>snapshotAt || quote.getHighTime()>snapshotAt
            || now-quote.getLowTime()>7200 || now-quote.getHighTime()>7200)
            return new MarketPriceProjection.Projection(chart?CHART_VERSION:VERSION,now,0,300,List.of(),"Waiting for timestamped market quotes");
        List<MarketPriceProjection.Point> points=new ArrayList<>();
        points.add(new MarketPriceProjection.Point(snapshotAt,new MarketPriceProjection.Side(quote.getLow(),quote.getLow(),quote.getLow()),
            new MarketPriceProjection.Side(quote.getHigh(),quote.getHigh(),quote.getHigh()),0));
        for(int seconds=300;seconds<=Math.min(chart?24:8,hours)*3600;seconds+=300) {
            Estimate low=at(false,seconds,quote.getLow()),high=at(true,seconds,quote.getHigh());
            if(!low.available() || !high.available()){if(chart)continue;else break;}
            // Public side averages can cross. Do not draw a negative forecast spread.
            double lowMid=Math.min(low.median,high.median),highMid=Math.max(low.median,high.median);
            points.add(new MarketPriceProjection.Point(snapshotAt+seconds,
                new MarketPriceProjection.Side(Math.min(low.lower,lowMid),lowMid,Math.max(low.upper,lowMid)),
                new MarketPriceProjection.Side(Math.min(high.lower,highMid),highMid,Math.max(high.upper,highMid)),Math.min(low.comparisons,high.comparisons)));
        }
        return new MarketPriceProjection.Projection(chart?CHART_VERSION:VERSION,now,snapshotAt,300,points,
            points.size()>1?"Public-market forecast; central 50% residual interval. Price and fill outcomes remain uncertain."
                :"Insufficient completed history for a qualified price forecast");
    }
    private static Fit fit(Series s,boolean high,int horizon) {
        if(!s.valid)return absent();
        return fit(s,high,horizon,s.origin-s.bucket);
    }
    private static Fit chartFit(Series s,boolean high,int horizon) {
        if(!s.valid || horizon<1)return absent();
        long end=s.origin-s.bucket;
        // A quiet side in the newest bucket does not invalidate the other completed evidence.
        // Use its actual earlier clock; no missing bucket is filled or compressed.
        while(end>=s.origin-2L*s.bucket && !Double.isFinite(s.price(end,high)))end-=s.bucket;
        return end<s.origin-2L*s.bucket?absent():fit(s,high,horizon,end);
    }
    private static Fit fit(Series s,boolean high,int horizon,long end) {
        double anchor=s.price(end,high);if(!Double.isFinite(anchor))return absent();
        List<Long> origins=new ArrayList<>();
        // Disjoint evaluation horizons retain timestamps and missing outcomes; no compressed time series.
        for(long at=end-(long)horizon*s.bucket;at>=s.rows.firstKey()+48L*s.bucket && origins.size()<TRAIN+VALIDATE;at-=(long)horizon*s.bucket)
            if(Double.isFinite(s.price(at,high)) && Double.isFinite(s.price(at+(long)horizon*s.bucket,high)))origins.add(at);
        Collections.reverse(origins);int selected=0;
        if(origins.size()==TRAIN+VALIDATE) {
            double[] error=new double[MODELS];
            for(int i=0;i<TRAIN;i++)for(int model=0;model<MODELS;model++)
                error[model]+=error(s,high,origins.get(i),horizon,model);
            for(int model=1;model<MODELS;model++)if(error[model]<error[selected])selected=model;
            double candidate=0,baseline=0;
            for(int i=TRAIN;i<origins.size();i++) {
                candidate+=error(s,high,origins.get(i),horizon,selected);
                baseline+=error(s,high,origins.get(i),horizon,0);
            }
            if(error[selected]>=.98*error[0] || candidate>=.98*baseline || !Double.isFinite(candidate))selected=0;
        }
        double shift=predict(s,high,end,horizon,selected)-anchor;
        if(!Double.isFinite(shift) || Math.abs(shift)>.25){selected=0;shift=0;}
        List<Double> residuals=new ArrayList<>();
        for(long at:origins) {
            double predicted=predict(s,high,at,horizon,selected),actual=s.price(at+(long)horizon*s.bucket,high);
            if(Double.isFinite(predicted))residuals.add(actual-predicted);
        }
        if(residuals.size()<12)return absent();
        Collections.sort(residuals);
        return new Fit(shift,quantile(residuals,.25),quantile(residuals,.75),selected,residuals.size());
    }
    private static double error(Series s,boolean high,long at,int horizon,int model) {
        double value=predict(s,high,at,horizon,model);
        return Double.isFinite(value)?Math.abs(value-s.price(at+(long)horizon*s.bucket,high)):Double.POSITIVE_INFINITY;
    }
    private static double predict(Series s,boolean high,long at,int horizon,int model) {
        double anchor=s.price(at,high);if(model==0 || !Double.isFinite(anchor))return anchor;
        double trend=0,reversion=0,seasonal=0;
        if(model==1 || model==4) {
            double[] prices=new double[12];double mean=0;
            for(int i=0;i<prices.length;i++) {prices[i]=s.price(at-(11L-i)*s.bucket,high);if(!Double.isFinite(prices[i]))return Double.NaN;mean+=prices[i]/12;}
            double numerator=0,denominator=0;List<Double> changes=new ArrayList<>();
            for(int i=0;i<12;i++){numerator+=(i-5.5)*(prices[i]-mean);denominator+=(i-5.5)*(i-5.5);if(i>0)changes.add(Math.abs(prices[i]-prices[i-1]));}
            Collections.sort(changes);double cap=Math.max(1e-8,3*quantile(changes,.5));
            double slope=Math.max(-cap,Math.min(cap,numerator/denominator));
            trend=slope*6*(1-Math.exp(-horizon/6.0));
        }
        if(model==2) {
            List<Double> prior=new ArrayList<>();
            for(int i=0;i<48;i++){double p=s.price(at-(long)i*s.bucket,high);if(Double.isFinite(p))prior.add(p);}
            if(prior.size()<36)return Double.NaN;Collections.sort(prior);
            reversion=(quantile(prior,.5)-anchor)*(1-Math.exp(-horizon/24.0));
        }
        if(model==3 || model==4) {
            long day=86400L/s.bucket;
            if(horizon>day)return Double.NaN;
            double past=s.price(at-day*s.bucket,high),future=s.price(at+(horizon-day)*s.bucket,high);
            if(!Double.isFinite(past) || !Double.isFinite(future))return Double.NaN;
            seasonal=.5*(future-past);
        }
        return anchor+(model==4?.5*(trend+seasonal):trend+reversion+seasonal);
    }
    private static String modelName(int model){return new String[]{"PERSISTENCE","DAMPED_TREND","MEAN_REVERSION","DAILY_RELATIVE","DAMPED_DAILY"}[model];}
    private static Fit absent(){return new Fit(0,0,0,0,0);}
    private static Estimate missing(){return new Estimate(Double.NaN,Double.NaN,Double.NaN,"UNAVAILABLE",0,false);}
    private static double quantile(List<Double> sorted,double q){double i=(sorted.size()-1)*q;int low=(int)i;return sorted.get(low)+(sorted.get((int)Math.ceil(i))-sorted.get(low))*(i-low);}
    private static boolean same(Candle a,Candle b){return Objects.equals(a.getAvgHighPrice(),b.getAvgHighPrice()) && Objects.equals(a.getAvgLowPrice(),b.getAvgLowPrice()) && a.getHighPriceVolume()==b.getHighPriceVolume() && a.getLowPriceVolume()==b.getLowPriceVolume();}
    private static final class Key {
        final long minute;final long[] values;final int hash;
        Key(List<Candle> shortRows,List<Candle> hourly,long now) {
            this(Arrays.asList(shortRows,hourly),now);
        }
        Key(List<Candle> shortRows,List<Candle> hourly,List<Candle> sixHourly,long now) {
            this(Arrays.asList(shortRows,hourly,sixHourly),now);
        }
        private Key(List<List<Candle>> histories,long now) {
            minute=now/300;
            int capacity=histories.size();
            for(List<Candle> rows:histories)if(rows!=null && rows.size()<=2048)capacity+=rows.size()*5;
            long[] data=new long[capacity];int index=0;
            for(List<Candle> rows:histories) {
                data[index++]=rows==null?-1L:rows.size();
                if(rows!=null && rows.size()<=2048)for(Candle c:rows) {
                    if(c==null){data[index++]=Long.MIN_VALUE;continue;}
                    data[index++]=c.getTimestamp();data[index++]=c.getAvgHighPrice()==null?-1L:c.getAvgHighPrice().longValue();
                    data[index++]=c.getAvgLowPrice()==null?-1L:c.getAvgLowPrice().longValue();
                    data[index++]=c.getHighPriceVolume();data[index++]=c.getLowPriceVolume();
                }
            }
            // Exact public-field keys still detect edits and conflicting duplicates. Avoid tens
            // of thousands of boxed Longs on every cache lookup; never reuse by mutable list identity.
            values=index==data.length?data:Arrays.copyOf(data,index);
            hash=31*Long.hashCode(minute)+Arrays.hashCode(values);
        }
        @Override public int hashCode(){return hash;}
        String fingerprint() {
            try {
                var bytes=java.nio.ByteBuffer.allocate((values.length+1)*8);bytes.putLong(minute);for(long value:values)bytes.putLong(value);
                byte[] digest=java.security.MessageDigest.getInstance("SHA-256").digest(bytes.array());
                var text=new StringBuilder();for(byte value:digest)text.append(String.format(Locale.ROOT,"%02x",value&255));return text.toString();
            }catch(java.security.NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}
        }
        @Override public boolean equals(Object other){return other instanceof Key && minute==((Key)other).minute && Arrays.equals(values,((Key)other).values);}
    }
}
