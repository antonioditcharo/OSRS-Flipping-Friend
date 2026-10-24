package com.flippingfriend.session;

import com.flippingfriend.data.Candle;
import com.flippingfriend.data.LatestPrice;
import java.time.Instant;
import java.util.List;
import java.util.TreeMap;

/** Public last trades are observations, not a live order book or standalone loss authority. */
final class StopLossEvidence {
    private StopLossEvidence() {}
    static boolean confirmed(Position position,LatestPrice latest,List<Candle> history,Instant now) {
        if(position==null || !position.isCostKnown() || position.getStopPrice()<=0 || latest==null
            || !latest.isComplete() || latest.getHighTime()==null || latest.getLowTime()==null
            || latest.getHighTime()<=0 || latest.getLowTime()<=0 || now==null
            || latest.secondsSinceHigh(now)<0 || latest.secondsSinceLow(now)<0
            || latest.stalestSideSeconds(now)>120 || latest.getLow()>latest.getHigh()
            || latest.getHigh()>position.getStopPrice() || history==null)return false;
        long at=now.getEpochSecond();
        var completed=new TreeMap<Long,Candle>();
        for(var bar:history)if(bar!=null && bar.getTimestamp()>0 && bar.getTimestamp()%300==0
            && bar.getTimestamp()<=at-300 && bar.getTimestamp()>=at-900) {
            var old=completed.put(bar.getTimestamp(),bar);
            if(old!=null && (!java.util.Objects.equals(old.getAvgHighPrice(),bar.getAvgHighPrice())
                || !java.util.Objects.equals(old.getAvgLowPrice(),bar.getAvgLowPrice())
                || old.getHighPriceVolume()!=bar.getHighPriceVolume() || old.getLowPriceVolume()!=bar.getLowPriceVolume()))return false;
        }
        if(completed.isEmpty())return false;
        var bar=completed.lastEntry().getValue();
        return bar.hasBothSides() && bar.getHighPriceVolume()>0 && bar.getLowPriceVolume()>0
            && bar.getAvgLowPrice()<=bar.getAvgHighPrice() && bar.getAvgHighPrice()<=position.getStopPrice();
    }
}
