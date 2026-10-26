package com.flippingfriend.model;

import com.flippingfriend.data.Candle;
import com.flippingfriend.data.LatestPrice;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class PublicPriceChartForecastTest {
    private static final long NOW=21600L*85000;
    private List<Candle> rows(int bucket,int direction) {
        List<Candle> result=new ArrayList<>();
        for(int i=0;i<365;i++)result.add(new Candle(NOW-(365L-i)*bucket,101000+direction*i*20,100000+direction*i*20,100,100));
        return result;
    }
    private LatestPrice quote(){return new LatestPrice(108280,NOW-30,107280,NOW-60);}

    @Test public void missingFiveMinuteEvidenceDoesNotHideQualifiedHourlyAndDayAheadForecasts() {
        var chart=PublicPriceForecast.fitChart(List.of(),rows(3600,1),rows(21600,1),NOW);
        var projection=chart.projection(quote(),NOW,NOW,24);
        assertTrue(projection.reason,projection.isAvailable());
        assertEquals(PublicPriceForecast.CHART_VERSION,projection.modelVersion);
        assertEquals(NOW+3600,projection.points.get(1).at);
        assertEquals(NOW+86400,projection.points.get(projection.points.size()-1).at);
        assertFalse(chart.at(false,300,107280).available());
        assertTrue(chart.at(false,86400,107280).directional);
    }
    @Test public void quietNewestSideUsesActualEarlierCompletedEvidenceWithoutFillingTheGap() {
        var hours=rows(3600,1);Candle last=hours.get(hours.size()-1);
        hours.set(hours.size()-1,new Candle(last.getTimestamp(),last.getAvgHighPrice(),null,100,0));
        var chart=PublicPriceForecast.fitChart(List.of(),hours,rows(21600,1),NOW);
        assertTrue(chart.at(false,3600,107280).available());
        assertTrue(chart.at(true,3600,108280).available());
        assertNull(hours.get(hours.size()-1).getAvgLowPrice());
        assertFalse(PublicPriceForecast.fit(List.of(),hours,NOW).at(false,7200,107280).available());
    }
    @Test public void chartFitsNeverExpandOrReplaceLiveStrategyFitsOrTheirIdentities() {
        var shortRows=rows(300,-1);var hours=rows(3600,-1);var six=rows(21600,1);
        var strategy=PublicPriceForecast.fit(shortRows,hours,NOW);
        var before=strategy.at(false,28800,92720);String fingerprint=strategy.historyFingerprint;
        var chart=PublicPriceForecast.fitChart(shortRows,hours,six,NOW);
        assertTrue(chart.at(false,86400,92720).available());
        assertFalse(strategy.at(false,86400,92720).available());
        assertSame(strategy,PublicPriceForecast.cached(shortRows,hours,NOW));
        assertEquals(fingerprint,strategy.historyFingerprint);
        assertEquals(before.median,strategy.at(false,28800,92720).median,0);
        var projection=strategy.projection(quote(),NOW,NOW,24);
        assertEquals(PublicPriceForecast.VERSION,projection.modelVersion);
        assertEquals(NOW+28800,projection.points.get(projection.points.size()-1).at);
    }
    @Test public void absentConflictingFutureAndStaleEvidenceNeverManufactureForecasts() {
        assertFalse(PublicPriceForecast.fitChart(List.of(),List.of(),List.of(),NOW).projection(quote(),NOW,NOW,24).isAvailable());
        var hours=rows(3600,1);var six=rows(21600,1);
        var before=PublicPriceForecast.fitChart(List.of(),hours,six,NOW).at(true,86400,108280);
        six.add(new Candle(NOW+21600,1,1,1,1));
        assertEquals(before.median,PublicPriceForecast.fitChart(List.of(),hours,six,NOW).at(true,86400,108280).median,0);
        hours.add(new Candle(hours.get(20).getTimestamp(),9,8,1,1));
        six.add(new Candle(six.get(20).getTimestamp(),9,8,1,1));
        assertFalse(PublicPriceForecast.fitChart(List.of(),hours,six,NOW).projection(quote(),NOW,NOW,24).isAvailable());
        assertFalse(PublicPriceForecast.fitChart(List.of(),rows(3600,1),rows(21600,1),NOW+43201).at(true,86400,108280).available());
        assertFalse(PublicPriceForecast.fitChart(List.of(),rows(3600,1),rows(21600,1),NOW).projection(quote(),NOW-151,NOW,24).isAvailable());
    }
    @Test public void flatObservedMarketCanHonestlyForecastFlatWithoutDecorativeMovement() {
        var chart=PublicPriceForecast.fitChart(rows(300,0),rows(3600,0),rows(21600,0),NOW);
        var estimate=chart.at(false,86400,100000);
        assertTrue(estimate.available());assertFalse(estimate.directional);
        assertEquals("PERSISTENCE",estimate.model);assertEquals(100000,estimate.median,1e-8);
        assertEquals(estimate.median,estimate.lower,1e-8);assertEquals(estimate.median,estimate.upper,1e-8);
    }
}
