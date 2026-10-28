package com.flippingfriend.data;

import com.google.gson.*;
import java.util.*;

/** Exact decoding at public-market boundaries only; never rewrites financial or historical sources. */
public final class PublicMarketNumbers {
    public static final String VERSION="PUBLIC_NUMERIC_INPUT_V1";
    private PublicMarketNumbers(){}

    public static long integer(JsonElement value) {
        if(value==null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber())
            throw new IllegalArgumentException("Exact numeric public input required");
        return value.getAsBigDecimal().longValueExact();
    }
    public static int nonnegativeInt(JsonElement value) {
        long number=integer(value);
        if(number<0 || number>Integer.MAX_VALUE)throw new IllegalArgumentException("Unsupported public integer");
        return (int)number;
    }
    private static Integer price(JsonObject row,String field) {
        JsonElement value=row.get(field);if(value==null || value.isJsonNull())return null;
        int price=nonnegativeInt(value);if(price==0)throw new IllegalArgumentException("Nonpositive public price");return price;
    }
    private static Long time(JsonObject row,String field) {
        JsonElement value=row.get(field);if(value==null || value.isJsonNull())return null;
        long at=integer(value);if(at<=0)throw new IllegalArgumentException("Invalid public clock");return at;
    }
    /** Missing sides stay missing; any present unsupported numeric field refuses this item only. */
    public static LatestPrice quote(JsonElement value) {
        try {
            if(value==null || !value.isJsonObject())return null;var row=value.getAsJsonObject();
            return new LatestPrice(price(row,"high"),time(row,"highTime"),price(row,"low"),time(row,"lowTime"));
        }catch(RuntimeException unsupported){return null;}
    }
    /** Zero publication time retains the existing unknown-clock meaning; never substitute now. */
    public static Candle candle(JsonElement value,long publishedAt) {
        try {
            if(value==null || !value.isJsonObject() || publishedAt<0)return null;var row=value.getAsJsonObject();
            long at=row.has("timestamp") && !row.get("timestamp").isJsonNull()?integer(row.get("timestamp")):publishedAt;
            if(at<0)return null;
            Integer high=price(row,"avgHighPrice"),low=price(row,"avgLowPrice");
            int highVolume=nonnegativeInt(row.get("highPriceVolume")),lowVolume=nonnegativeInt(row.get("lowPriceVolume"));
            Math.addExact(highVolume,lowVolume); // The retained model represents total volume as an int.
            return new Candle(at,high,low,highVolume,lowVolume);
        }catch(RuntimeException unsupported){return null;}
    }
    /** Refuse one affected series as a unit, rather than hide malformed buckets inside a good history. */
    public static List<Candle> history(JsonElement value) {
        if(value==null || !value.isJsonArray())throw new IllegalArgumentException("Public history array required");
        var result=new ArrayList<Candle>();long previous=0;
        for(JsonElement raw:value.getAsJsonArray()) {
            Candle bar=candle(raw,0);
            if(bar==null || bar.getTimestamp()<=previous)throw new IllegalArgumentException("Unsupported public history");
            result.add(bar);previous=bar.getTimestamp();
        }
        return Collections.unmodifiableList(result);
    }
    public static int itemKey(String value) {
        int item=Integer.parseInt(value);
        if(item<=0 || !Integer.toString(item).equals(value))throw new IllegalArgumentException("Unsupported public item key");
        return item;
    }
    public static Map<Integer,LatestPrice> quotes(JsonObject source) {
        var result=new HashMap<Integer,LatestPrice>();if(source==null)return result;
        for(var entry:source.entrySet())try {
            int item=itemKey(entry.getKey());LatestPrice quote=quote(entry.getValue());if(quote!=null)result.put(item,quote);
        }catch(RuntimeException unsupported){/* An unrelated valid item remains usable. */}
        return result;
    }
    public static Map<Integer,Candle> candles(JsonObject source,long publishedAt) {
        var result=new HashMap<Integer,Candle>();if(source==null)return result;
        for(var entry:source.entrySet())try {
            int item=itemKey(entry.getKey());Candle bar=candle(entry.getValue(),publishedAt);if(bar!=null)result.put(item,bar);
        }catch(RuntimeException unsupported){/* Item-local refusal, not whole-feed failure. */}
        return result;
    }
    public static List<ItemMetadata> mapping(JsonElement value,Gson gson) {
        if(value==null || !value.isJsonArray())throw new IllegalArgumentException("Public mapping array required");
        var result=new LinkedHashMap<Integer,ItemMetadata>();var seen=new HashSet<Integer>();
        for(JsonElement raw:value.getAsJsonArray())try {
            if(!raw.isJsonObject())continue;var row=raw.getAsJsonObject();int id=nonnegativeInt(row.get("id"));
            if(id==0)continue;
            if(!seen.add(id)){result.remove(id);continue;}
            for(String field:List.of("limit","value","lowalch","highalch"))
                if(row.has(field) && !row.get(field).isJsonNull())nonnegativeInt(row.get(field));
            if(row.has("members") && !row.get("members").isJsonNull()
                && (!row.get("members").isJsonPrimitive() || !row.get("members").getAsJsonPrimitive().isBoolean()))continue;
            result.put(id,gson.fromJson(row,ItemMetadata.class));
        }catch(RuntimeException unsupported){/* Unsupported metadata cannot change another item's limit. */}
        return new ArrayList<>(result.values());
    }
}
