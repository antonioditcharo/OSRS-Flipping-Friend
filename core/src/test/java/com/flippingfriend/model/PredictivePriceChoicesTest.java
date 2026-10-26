package com.flippingfriend.model;

import com.flippingfriend.data.*;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class PredictivePriceChoicesTest {
    @Test public void observedForecastAlternativesExtendButNeverRemoveTheFallbackGrid() {
        long now=1_800_000_000L;List<Candle> shortRows=new ArrayList<>(),hourly=new ArrayList<>();
        for(int i=0;i<365;i++) {
            shortRows.add(new Candle(now-(365L-i)*300,101000-i*20,100000-i*20,100,100));
            hourly.add(new Candle(now-(365L-i)*3600,101000-i*20,100000-i*20,100,100));
        }
        var f=PublicPriceForecast.fit(shortRows,hourly,now);var quote=new LatestPrice(94000,now-20,93000,now-20);
        int[] prices=PredictivePriceChoices.prices(true,quote,shortRows,f,1800,new double[]{0,.002},now);
        assertTrue(Arrays.stream(prices).anyMatch(p->p==93000));assertTrue(Arrays.stream(prices).anyMatch(p->p==PriceOffset.apply(93000,.002)));
        assertTrue(Arrays.stream(prices).anyMatch(p->p<93000));
        assertTrue(Arrays.stream(prices).allMatch(p->p>=91000 && p<=95000));assertTrue(prices.length<=4);
        var old=new LatestPrice(94000,now-121,93000,now-20);
        assertArrayEquals(new int[]{93000},PredictivePriceChoices.prices(true,old,shortRows,f,1800,new double[]{0},now));
    }
    @Test public void unavailableModelPreservesEveryLocalPrice() {
        long now=1_800_000_000L;var quote=new LatestPrice(200,now-1,100,now-1);
        var f=PublicPriceForecast.fit(List.of(),List.of(),now);
        assertArrayEquals(new int[]{200,198},PredictivePriceChoices.prices(false,quote,List.of(),f,300,new double[]{0,-.01},now));
        assertArrayEquals(new int[]{200,198},PredictivePriceChoices.prices(false,quote,List.of(),null,300,new double[]{0,-.01},now));
    }
}
