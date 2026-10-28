package com.flippingfriend.data;

import com.google.gson.*;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class PublicMarketNumbersTest {
    private final Gson json=new Gson();
    private JsonElement parse(String value){return json.fromJson(value,JsonElement.class);}
    @Test public void badQuotesCannotWrapOrTruncateAndDoNotRemoveTheGoodNeighbour() {
        for(String bad:List.of("8770000000","4294967396","2147483648","100.5","-1","0","\"100\"","true")) {
            JsonObject source=parse("{\"1\":{\"low\":"+bad+",\"high\":120},\"2\":{\"low\":100,\"high\":120,\"lowTime\":1800000000}}").getAsJsonObject();
            String original=json.toJson(source);var quotes=PublicMarketNumbers.quotes(source);
            assertFalse(bad,quotes.containsKey(1));assertEquals(Integer.valueOf(100),quotes.get(2).getLow());assertEquals(original,json.toJson(source));
        }
    }
    @Test public void unknownSidesAndDistinctLongClocksStayUnknownAndExact() {
        LatestPrice q=PublicMarketNumbers.quote(parse("{\"low\":null,\"high\":2147483647,\"lowTime\":null,\"highTime\":4294967297}"));
        assertNull(q.getLow());assertNull(q.getLowTime());assertEquals(Integer.valueOf(Integer.MAX_VALUE),q.getHigh());assertEquals(Long.valueOf(4294967297L),q.getHighTime());
        assertNull(PublicMarketNumbers.quote(parse("{\"low\":100,\"lowTime\":1800000000.5}")));
    }
    @Test public void invalidVolumeAndMissingVolumeAreNotConvertedToEmptyOrPlausibleBuckets() {
        for(String volume:List.of("4294967296","2147483648","1.5","-1","null","\"1\"")) {
            assertNull(PublicMarketNumbers.candle(parse("{\"avgLowPrice\":100,\"avgHighPrice\":120,\"lowPriceVolume\":"+volume+",\"highPriceVolume\":2}"),1800));
        }
        assertNull(PublicMarketNumbers.candle(parse("{\"avgLowPrice\":100,\"highPriceVolume\":2}"),1800));
        assertNull(PublicMarketNumbers.candle(parse("{\"lowPriceVolume\":2147483647,\"highPriceVolume\":1}"),1800));
        Candle known=PublicMarketNumbers.candle(parse("{\"avgLowPrice\":null,\"avgHighPrice\":120,\"lowPriceVolume\":0,\"highPriceVolume\":2}"),1800);
        assertNull(known.getAvgLowPrice());assertEquals(1800,known.getTimestamp());assertEquals(2,known.getTotalVolume());
    }
    @Test public void aBadHistoryCannotLoseJustTheBadBucketAndBecomeAnApparentlyCompleteSeries() {
        String first="{\"timestamp\":1800,\"avgLowPrice\":100,\"avgHighPrice\":120,\"lowPriceVolume\":1,\"highPriceVolume\":1}";
        for(String second:List.of(first,first.replace("1800","2100").replace("100","4294967396"),first.replace("1800","2100.5"))) {
            try{PublicMarketNumbers.history(parse("["+first+","+second+"]"));fail();}catch(IllegalArgumentException|ArithmeticException expected){}
        }
    }
    @Test public void metadataRefusesOnlyUnsupportedOrAmbiguousIdsWithoutChangingMissingLimitFallback() {
        var list=PublicMarketNumbers.mapping(parse("[{\"id\":1,\"name\":\"Good\",\"members\":true,\"limit\":100},{\"id\":2,\"limit\":4294967396},{\"id\":3,\"value\":100000},{\"id\":4,\"limit\":8},{\"id\":4,\"limit\":100}]"),json);
        assertEquals(2,list.size());assertEquals(1,list.get(0).getId());assertEquals(100,list.get(0).getBuyLimit());
        assertEquals(3,list.get(1).getId());assertEquals(8,list.get(1).getBuyLimit());assertFalse(list.get(1).hasPublishedBuyLimit());
    }
    @Test public void positiveLargeLegalPricesHaveAnOverflowSafeMidpoint() {
        Candle bar=new Candle(1800,2_000_000_001,2_000_000_000,1,1);
        assertEquals(2_000_000_000.5,bar.midPrice(),0);
    }
}
