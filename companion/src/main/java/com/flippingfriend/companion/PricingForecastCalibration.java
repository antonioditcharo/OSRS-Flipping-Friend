package com.flippingfriend.companion;

import com.flippingfriend.data.LatestPrice;
import com.flippingfriend.model.PublicPriceForecast;
import com.google.gson.Gson;
import java.util.*;

/** Prospective public quote targets, never account executions, simulated fills or learning labels. */
final class PricingForecastCalibration {
    static final String VERSION="PUBLIC_QUOTE_CALIBRATION_V1";
    private static final int TARGET_WINDOW_SECONDS=300;
    private static final Gson JSON=new Gson();
    static final class Prediction {
        final double lower,median,upper;
        Prediction(double low,double middle,double high) {
            if(!Double.isFinite(low) || !Double.isFinite(middle) || !Double.isFinite(high)
                || low<1 || low>middle || middle>high || high>Integer.MAX_VALUE)
                throw new IllegalArgumentException("Invalid frozen public forecast");
            lower=low;median=middle;upper=high;
        }
        static Prediction from(PublicPriceForecast.Estimate e){return e.available()?new Prediction(e.lower,e.median,e.upper):null;}
    }
    static final class Forecast {
        final Prediction low,high;
        Forecast(Prediction low,Prediction high){this.low=low;this.high=high;}
    }
    interface Forecaster {Forecast at(AccountReplayInput.Frame frame,int item,LatestPrice quote,int seconds);}
    static final class Side {
        final long attempted,unsupportedAtOrigin,forecasts,resolved,missingTarget,rightCensored,covered;
        final Double meanAbsoluteErrorGp,persistenceMeanAbsoluteErrorGp,meanSignedErrorGp,meanAbsolutePercentageError,centralIntervalCoverage;
        Side(Tally t) {
            attempted=t.attempted;unsupportedAtOrigin=t.unsupported;forecasts=t.forecasts;resolved=t.resolved;
            missingTarget=t.missing;rightCensored=t.censored;covered=t.covered;
            if(attempted!=unsupportedAtOrigin+forecasts || forecasts!=resolved+missingTarget+rightCensored)
                throw new IllegalStateException("Every public forecast requires an explained outcome");
            meanAbsoluteErrorGp=resolved==0?null:t.error/resolved;
            persistenceMeanAbsoluteErrorGp=resolved==0?null:t.persistenceError/resolved;
            meanSignedErrorGp=resolved==0?null:t.signedError/resolved;
            meanAbsolutePercentageError=resolved==0?null:t.percentageError/resolved;
            centralIntervalCoverage=resolved==0?null:covered/(double)resolved;
        }
    }
    static final class Cadence {
        final int cadenceMinutes;
        final Side low,high;
        final long modelFailures;
        Cadence(int cadence,Accumulator a){cadenceMinutes=cadence;low=new Side(a.low);high=new Side(a.high);modelFailures=a.modelFailures;}
    }
    static final class Report {
        final String version=VERSION,model=PublicPriceForecast.VERSION;
        final String scope="PUBLIC_QUOTE_TARGETS_NOT_FILLS_OR_ACCOUNT_EXECUTIONS";
        final String originRule="FIRST_RECORDED_SNAPSHOT_AT_OR_AFTER_NONOVERLAPPING_HORIZON_CUTOFF";
        final String percentageErrorUnit="FRACTION_OF_TARGET_PRICE";
        final int targetWindowSeconds=TARGET_WINDOW_SECONDS;
        final double nominalCentralInterval=0.5;
        final long firstAvailableAt,lastAvailableAt,frames,maximumObservationGapSeconds;
        final Map<Integer,Cadence> cadences;
        Report(long first,long last,long count,long gap,Map<Integer,Accumulator> source) {
            firstAvailableAt=first;lastAvailableAt=last;frames=count;maximumObservationGapSeconds=gap;
            var result=new TreeMap<Integer,Cadence>();source.forEach((cadence,a)->result.put(cadence,new Cadence(cadence,a)));
            cadences=Collections.unmodifiableMap(result);
        }
        String support(int cadence) {
            if(frames<2 || maximumObservationGapSeconds>900)return "INSUFFICIENT_PUBLIC_OBSERVATION_COVERAGE";
            Cadence c=cadences.get(cadence);
            if(c==null || c.modelFailures>0)return "FORECAST_ANALYSIS_FAILURE";
            for(Side s:List.of(c.low,c.high)) {
                if(s.resolved<100 || s.forecasts<s.attempted*.5 || s.resolved<s.forecasts*.8)
                    return "INSUFFICIENT_FORECAST_TARGETS";
                if(s.centralIntervalCoverage==null || s.centralIntervalCoverage<.4)return "FORECAST_INTERVAL_UNDERCOVERAGE";
            }
            return "SUPPORTED_PUBLIC_FORECASTS_ONLY";
        }
    }
    private static final class Tally {
        long attempted,unsupported,forecasts,resolved,missing,censored,covered;
        double error,persistenceError,signedError,percentageError;
        void resolved(Pending p,int actual) {
            resolved++;double delta=p.prediction.median-actual;
            error+=Math.abs(delta);signedError+=delta;percentageError+=Math.abs(delta)/actual;
            persistenceError+=Math.abs((double)p.anchor-actual);
            if(actual>=p.prediction.lower && actual<=p.prediction.upper)covered++;
        }
    }
    private static final class Accumulator {
        final Tally low=new Tally(),high=new Tally();long nextOrigin,modelFailures;
        Accumulator(long start){nextOrigin=start;}
    }
    private static final class Pending {
        final int item,anchor;final boolean high;final long deadline,expiresAt;
        final Prediction prediction;final Tally tally;
        Pending(int item,int anchor,boolean high,long deadline,Prediction prediction,Tally tally) {
            this.item=item;this.anchor=anchor;this.high=high;this.deadline=deadline;expiresAt=Math.addExact(deadline,TARGET_WINDOW_SECONDS);this.prediction=prediction;this.tally=tally;
        }
    }
    private PricingForecastCalibration(){}
    static Report evaluate(AccountReplayInput input,long start,long end) {
        return evaluate(input,start,end,(frame,item,quote,seconds)-> {
            var model=PublicPriceForecast.fit(frame.shortHistory.getOrDefault(item,List.of()),
                frame.longHistory.getOrDefault(item,List.of()),frame.availableAt);
            return new Forecast(quote.getLow()==null?null:Prediction.from(model.at(false,seconds,quote.getLow())),
                quote.getHigh()==null?null:Prediction.from(model.at(true,seconds,quote.getHigh())));
        });
    }
    static Report evaluate(AccountReplayInput input,long start,long end,Forecaster forecaster) {
        input.validate();
        if(start<input.startAt || end>input.endAt || end<=start || input.mapping.size()>128 || forecaster==null)
            throw new IllegalArgumentException("Explicit bounded chronological public fold required");
        var accumulators=new TreeMap<Integer,Accumulator>();
        for(int c:new int[]{5,30,120,480})accumulators.put(c,new Accumulator(start));
        List<Pending> pending=new ArrayList<>();long first=0,last=start,frames=0,gap=0;
        for(var frame:input.frames) {
            long now=frame.availableAt;if(now<start)continue;if(now>end)break;
            if(first==0)first=now;frames++;gap=Math.max(gap,now-last);last=now;
            Map<Integer,LatestPrice> quotes=new TreeMap<>();
            for(int item:input.mapping.keySet())if(frame.latest.has(Integer.toString(item))) {
                var source=frame.latest.getAsJsonObject(Integer.toString(item));
                for(String side:List.of("low","high"))if(source.has(side) && !source.get(side).isJsonNull())
                    source.get(side).getAsBigDecimal().intValueExact(); // Retain the evaluator's exact-price refusal contract.
                LatestPrice quote=com.flippingfriend.data.PublicMarketNumbers.quote(source);
                if(quote==null)throw new IllegalArgumentException("Unsupported public forecast target");
                quotes.put(item,quote);
            }
            for(var iterator=pending.iterator();iterator.hasNext();) {
                Pending p=iterator.next();
                if(now>p.expiresAt){p.tally.missing++;iterator.remove();continue;}
                if(now<p.deadline)continue;
                LatestPrice quote=quotes.get(p.item);Long at=quote==null?null:p.high?quote.getHighTime():quote.getLowTime();
                Integer price=quote==null?null:p.high?quote.getHigh():quote.getLow();
                if(at!=null && price!=null && price>0 && at>=p.deadline && at<=p.expiresAt
                    && at<=frame.observedAt && at<=now) {p.tally.resolved(p,price);iterator.remove();}
            }
            for(var entry:accumulators.entrySet()) {
                int seconds=entry.getKey()*60;var a=entry.getValue();if(now<a.nextOrigin)continue;
                a.nextOrigin=Math.addExact(now,seconds);
                for(int item:input.mapping.keySet()) {
                    a.low.attempted++;a.high.attempted++;LatestPrice quote=quotes.get(item);Forecast forecast=null;
                    boolean low=fresh(frame,quote,false),high=fresh(frame,quote,true);
                    if(low || high)try{forecast=forecaster.at(frame,item,quote,seconds);}
                    catch(RuntimeException failure){a.modelFailures++;}
                    add(pending,item,quote,false,low,now+seconds,forecast==null?null:forecast.low,a.low);
                    add(pending,item,quote,true,high,now+seconds,forecast==null?null:forecast.high,a.high);
                }
            }
            if(pending.size()>2560)throw new IllegalStateException("Public forecast preparation exceeds bounded pending work");
        }
        for(Pending p:pending)if(p.expiresAt>end)p.tally.censored++;else p.tally.missing++;
        return new Report(first,last,frames,Math.max(gap,end-last),accumulators);
    }
    private static boolean fresh(AccountReplayInput.Frame frame,LatestPrice q,boolean high) {
        if(q==null || frame.availableAt-frame.observedAt>150)return false;
        Integer price=high?q.getHigh():q.getLow();Long time=high?q.getHighTime():q.getLowTime();
        return price!=null && price>0 && time!=null && time>0 && time<=frame.observedAt && frame.availableAt-time<=120;
    }
    private static void add(List<Pending> pending,int item,LatestPrice quote,boolean high,boolean fresh,long deadline,Prediction prediction,Tally tally) {
        if(!fresh || prediction==null){tally.unsupported++;return;}
        tally.forecasts++;pending.add(new Pending(item,high?quote.getHigh():quote.getLow(),high,deadline,prediction,tally));
    }
}
