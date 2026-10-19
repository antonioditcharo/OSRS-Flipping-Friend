package com.flippingfriend.companion;

import com.google.gson.*;

/** Discounted public-market risk estimates. Never an order price, fill, or liquidation guarantee. */
final class StockRiskMark {
    static final long QUIET_TRADE_SECONDS=900;
    static final int HAIRCUT_PERCENT=10;
    static final class Result {
        final int quantity,price;
        final long validUntil;
        final String detail;
        Result(int quantity,int price,long until,String detail) {
            this.quantity=quantity;this.price=price;validUntil=until;this.detail=detail;
        }
    }
    private static final class Bar {
        final long price,quantity,expires;
        Bar(long price,long quantity,long expires){this.price=price;this.quantity=quantity;this.expires=expires;}
    }

    static Result quiet(MarketIngestionService.MarketState market,int item,int quantity,long bid,long bidAt,long now) {
        String missing="last instant-sell trade "+(now-bidAt)+"s ago; no qualifying completed public-price/volume evidence";
        Result unavailable=new Result(0,0,now,missing);
        if(market==null || market.observedAt<=0 || market.observedAt>now || now-market.observedAt>120
            || quantity<=0 || bid<=0 || bid>Integer.MAX_VALUE || bidAt<=0 || bidAt>now)return unavailable;
        if(now-bidAt>QUIET_TRADE_SECONDS)return new Result(0,0,now,"last instant-sell trade "+(now-bidAt)
            +"s ago; the supported quiet-market limit is 900s");
        try {
            JsonObject quote=market.latest.getAsJsonObject(Integer.toString(item));
            long ask=integer(quote.get("high")),askAt=integer(quote.get("highTime"));
            if(ask<bid || ask>Integer.MAX_VALUE || askAt<=0 || askAt>now || now-askAt>QUIET_TRADE_SECONDS
                || ask-bid>bid/4)return unavailable;
            Bar shortBar=bar(market.fiveMinute,market.fiveMinuteStartedAt,300,600,2,item,market.observedAt,now);
            Bar hourBar=bar(market.hourly,market.hourlyStartedAt,3600,7200,10,item,market.observedAt,now);
            if(shortBar==null && hourBar==null)return unavailable;
            long price=bid,capacity=0,until=Math.min(Math.addExact(bidAt,QUIET_TRADE_SECONDS),Math.addExact(askAt,QUIET_TRADE_SECONDS));
            if(shortBar!=null){price=Math.min(price,shortBar.price);capacity=shortBar.quantity;until=Math.min(until,shortBar.expires);}
            if(hourBar!=null){price=Math.min(price,hourBar.price);capacity=Math.max(capacity,hourBar.quantity);until=Math.min(until,hourBar.expires);}
            int supported=(int)Math.min(quantity,capacity);
            // Unit prices fit int, so this multiplication cannot overflow long. Round down.
            int discounted=(int)(price*(100-HAIRCUT_PERCENT)/100);
            if(supported<=0 || discounted<=0)return unavailable;
            String evidence=shortBar!=null && hourBar!=null?"5m/hourly":shortBar!=null?"5m":"hourly";
            return new Result(supported,discounted,until,"last instant-sell trade "+(now-bidAt)
                +"s ago; completed "+evidence+" low-price/volume support, 10% price haircut, no unrealized gain credited");
        }catch(RuntimeException unsupported){return unavailable;}
    }

    private static Bar bar(JsonObject data,long start,long seconds,long maxAge,int volumeDivisor,int item,long capturedAt,long now) {
        if(data==null || start<=0 || start%seconds!=0)return null;
        long end=Math.addExact(start,seconds),expires=Math.addExact(end,maxAge);
        if(end>capturedAt || end>now || now>expires)return null;
        var row=data.get(Integer.toString(item));if(row==null || !row.isJsonObject())return null;
        try {
            long price=integer(row.getAsJsonObject().get("avgLowPrice"));
            long volume=integer(row.getAsJsonObject().get("lowPriceVolume"));
            if(price<=0 || price>Integer.MAX_VALUE || volume<volumeDivisor)return null;
            return new Bar(price,volume/volumeDivisor,expires);
        }catch(RuntimeException unsupported){return null;}
    }

    private static long integer(JsonElement value) {
        if(value==null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber())
            throw new IllegalArgumentException("Exact numeric market value required");
        return value.getAsBigDecimal().longValueExact();
    }
}
