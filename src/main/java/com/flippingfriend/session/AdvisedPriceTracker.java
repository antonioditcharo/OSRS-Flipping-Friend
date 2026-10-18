package com.flippingfriend.session;

import com.flippingfriend.TradingHorizon;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Display-price stability shared by live guidance and chronological replay. No financial authority. */
public final class AdvisedPriceTracker {
    public static final String VERSION="advised-price-2";
    private static final long MAX_AGE_SECONDS=600;
    private final Map<Long,Quote> quotes=new ConcurrentHashMap<>();
    private static final class Quote {
        final int price;final long at;
        Quote(int price,long at){this.price=price;this.at=at;}
    }
    public int price(int item,boolean buying,int fresh,long at) {
        if(item<=0 || fresh<=0 || at<0)throw new IllegalArgumentException("Invalid advised price observation");
        int drift=Math.max(1,(int)Math.round(fresh*.02));
        // Final sale authority uses this stricter budget. Stability cannot retain a refused order.
        if(!buying)drift=Math.min(drift,TradingHorizon.outbidDeadbandForPrice(fresh));
        long key=((long)item<<1)|(buying?1:0);Quote old=quotes.get(key);
        if(old!=null && at>=old.at && at-old.at<MAX_AGE_SECONDS && Math.abs((long)fresh-old.price)<drift)return old.price;
        quotes.put(key,new Quote(fresh,at));return fresh;
    }
    public void clear(){quotes.clear();}
}
