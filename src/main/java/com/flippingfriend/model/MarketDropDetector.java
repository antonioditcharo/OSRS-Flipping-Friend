package com.flippingfriend.model;

import com.flippingfriend.data.*;
import java.time.Instant;
import java.util.*;

/** Public-market watch list. A price drop is observational evidence, never entry authority. */
public final class MarketDropDetector {
    public static final String VERSION = "public-market-drop-1";
    public static final class Drop {
        public final int itemId, currentLow, referenceLow;
        public final String name;
        public final long observedAt, referenceAt;
        public final double fraction;
        Drop(int id, String name, int low, int reference, long at, long before) {
            itemId=id; this.name=name; currentLow=low; referenceLow=reference;
            observedAt=at; referenceAt=before; fraction=1.0-(double)low/reference;
        }
    }
    public List<Drop> detect(MarketSnapshot market, Instant now) {
        if (market==null || market.getCapturedAt()==null || market.getCapturedAt().isAfter(now)
            || now.getEpochSecond()-market.getCapturedAt().getEpochSecond()>120) return Collections.emptyList();
        List<Drop> drops=new ArrayList<>();
        for (var entry:market.getLatest().entrySet()) {
            int item=entry.getKey(); LatestPrice p=entry.getValue(); Candle prior=market.fiveMinute(item);
            if (p==null || !p.isComplete() || p.secondsSinceHigh(now)<0 || p.secondsSinceLow(now)<0
                || p.stalestSideSeconds(now)>120 || prior==null || !prior.hasBothSides()
                || prior.getLowPriceVolume()<10 || prior.getHighPriceVolume()<10
                || prior.getTimestamp()<=0 || prior.getTimestamp()>now.getEpochSecond()
                || prior.getTimestamp()>p.getLowTime()-300 || now.getEpochSecond()-prior.getTimestamp()>900) continue;
            double drop=1.0-(double)p.getLow()/prior.getAvgLowPrice();
            if ((long)p.getLow()*100<=(long)prior.getAvgLowPrice()*90) drops.add(new Drop(item,market.getItemName(item),p.getLow(),prior.getAvgLowPrice(),
                p.getLowTime(),prior.getTimestamp()));
        }
        drops.sort(Comparator.comparingDouble((Drop d)->d.fraction).reversed().thenComparingInt(d->d.itemId));
        return Collections.unmodifiableList(drops);
    }
}
