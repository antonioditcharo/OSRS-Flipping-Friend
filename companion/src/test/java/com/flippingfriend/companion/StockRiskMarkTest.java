package com.flippingfriend.companion;

import com.google.gson.*;
import org.junit.*;
import static org.junit.Assert.*;

public class StockRiskMarkTest {
    final QualifiedStockRiskTest f=new QualifiedStockRiskTest();
    StockRiskMark.Result mark(MarketIngestionService.MarketState market) {
        var q=market.latest.getAsJsonObject("561");
        return StockRiskMark.quiet(market,561,2,q.get("low").getAsLong(),q.get("lowTime").getAsLong(),f.f.at);
    }
    MarketIngestionService.MarketState clocks(MarketIngestionService.MarketState m,long shortAt,long hourAt) {
        return new MarketIngestionService.MarketState(m.mapping,m.latest,m.fiveMinute,m.hourly,m.observedAt,shortAt,hourAt);
    }
    @Test public void completedClocksPriceHaircutVolumeAndExpiryAreAllBound() {
        var market=f.supported();var result=mark(market);
        assertEquals(2,result.quantity);assertEquals(33_300_000,result.price);
        assertTrue(result.validUntil<=f.f.at+600);assertTrue(result.validUntil>=f.f.at);
        assertTrue(result.detail.contains("10%"));assertTrue(result.detail.contains("no unrealized gain"));
    }
    @Test public void fiveMinuteAndHourlyVolumesAreNotAddedTogether() {
        var market=f.supported();market.hourly.getAsJsonObject("561").addProperty("lowPriceVolume",19);
        assertEquals(1,mark(market).quantity);
        market.fiveMinute.getAsJsonObject("561").addProperty("lowPriceVolume",4);
        assertEquals(2,mark(market).quantity);
    }
    @Test public void minimumOfLatestAndEachEligibleBarIsUsed() {
        var market=f.supported();market.fiveMinute.getAsJsonObject("561").addProperty("avgLowPrice",30_000_000);
        assertEquals(27_000_000,mark(market).price);
        market.hourly.getAsJsonObject("561").addProperty("avgLowPrice",20_000_000);
        assertEquals(18_000_000,mark(market).price);
    }
    @Test public void futureUnfinishedAndMisalignedBucketsCannotQualifyStock() {
        var market=f.supported();long next=(f.f.at/300)*300;
        for(long bad:new long[]{next,next+300,market.fiveMinuteStartedAt+1,Long.MAX_VALUE})
            assertEquals(0,mark(clocks(market,bad,0)).quantity);
    }
    @Test public void expiredBarsAndFeedCannotQualifyAStockMark() {
        var market=f.supported();
        assertEquals(0,mark(clocks(market,(f.f.at/300-5)*300,(f.f.at/3600-4)*3600)).quantity);
        var stale=new MarketIngestionService.MarketState(market.mapping,market.latest,market.fiveMinute,market.hourly,
            f.f.at-121,market.fiveMinuteStartedAt,market.hourlyStartedAt);
        assertEquals(0,mark(stale).quantity);
    }
    @Test public void malformedBarPricesAndVolumesAreNotRoundedOrCoerced() {
        for(String json:new String[]{"{\"avgLowPrice\":\"37000000\",\"lowPriceVolume\":4}",
                "{\"avgLowPrice\":37000000.5,\"lowPriceVolume\":4}",
                "{\"avgLowPrice\":37000000,\"lowPriceVolume\":\"4\"}",
                "{\"avgLowPrice\":37000000,\"lowPriceVolume\":4.5}",
                "{\"avgLowPrice\":37000000,\"lowPriceVolume\":-4}",
                "{\"avgLowPrice\":2147483648,\"lowPriceVolume\":4}","null"}) {
            var market=f.supported();market.fiveMinute.add("561",JsonParser.parseString(json));
            assertEquals(json,0,mark(clocks(market,market.fiveMinuteStartedAt,0)).quantity);
        }
    }
    @Test public void missingOldCrossedAndWideAskCannotCorroborateAnOldBid() {
        for(String field:new String[]{"high","highTime"}) {
            var market=f.supported();market.latest.getAsJsonObject("561").remove(field);assertEquals(0,mark(market).quantity);
        }
        for(long high:new long[]{36_000_000,47_000_000,Long.MAX_VALUE}) {
            var market=f.supported();market.latest.getAsJsonObject("561").addProperty("high",high);assertEquals(0,mark(market).quantity);
        }
        for(long time:new long[]{f.f.at-901,f.f.at+1}) {
            var market=f.supported();market.latest.getAsJsonObject("561").addProperty("highTime",time);assertEquals(0,mark(market).quantity);
        }
    }
}
