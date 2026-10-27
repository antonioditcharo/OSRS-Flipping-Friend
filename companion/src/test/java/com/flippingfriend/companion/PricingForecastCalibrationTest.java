package com.flippingfriend.companion;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class PricingForecastCalibrationTest {
    private PricingForecastCalibration.Forecast constant() {
        return new PricingForecastCalibration.Forecast(new PricingForecastCalibration.Prediction(99,100,101),
            new PricingForecastCalibration.Prediction(119,120,121));
    }
    private PricingForecastCalibration.Report score(AccountReplayInput input) {
        return PricingForecastCalibration.evaluate(input,input.startAt,input.endAt,(f,item,quote,seconds)->constant());
    }
    @Test public void handCalculatedTargetsReportErrorPersistenceCoverageAndEveryCensoredOutcome() {
        var input=new AccountReplayTest().input(1,5);
        input.frames.get(5).latest.getAsJsonObject("1").addProperty("low",110);
        input.frames.get(5).latest.getAsJsonObject("1").addProperty("high",130);
        var report=score(input);var low=report.cadences.get(5).low;var high=report.cadences.get(5).high;
        assertEquals(2,low.attempted);assertEquals(2,low.forecasts);assertEquals(1,low.resolved);assertEquals(1,low.rightCensored);
        assertEquals(10,low.meanAbsoluteErrorGp,0);assertEquals(10,low.persistenceMeanAbsoluteErrorGp,0);
        assertEquals(-10,low.meanSignedErrorGp,0);assertEquals(0,low.centralIntervalCoverage,0);
        assertEquals(10,high.meanAbsoluteErrorGp,0);assertEquals(-10,high.meanSignedErrorGp,0);
        assertEquals(10/110.0,low.meanAbsolutePercentageError,1e-12);
        assertEquals("PUBLIC_QUOTE_TARGETS_NOT_FILLS_OR_ACCOUNT_EXECUTIONS",report.scope);
    }
    @Test public void olderTradeAtLaterSnapshotCannotBecomeAHorizonTargetAndSidesResolveIndependently() {
        var input=new AccountReplayTest().input(1,6);long start=input.startAt;
        var atFive=input.frames.get(5).latest.getAsJsonObject("1");atFive.addProperty("low",900);atFive.addProperty("lowTime",start+299);
        atFive.addProperty("high",150);input.frames.get(6).latest.getAsJsonObject("1").addProperty("low",110);
        var report=score(input);assertEquals(10,report.cadences.get(5).low.meanAbsoluteErrorGp,0);
        assertEquals(30,report.cadences.get(5).high.meanAbsoluteErrorGp,0);
        assertEquals(1,report.cadences.get(5).low.resolved);assertEquals(1,report.cadences.get(5).high.resolved);
    }
    @Test public void quietSidesMissingForecastsAndFoldEdgesStayUnknownInsteadOfBecomingFailuresOrZeroError() {
        var input=new AccountReplayTest().input(1,16);
        for(var f:input.frames)f.latest.getAsJsonObject("1").addProperty("lowTime",input.startAt);
        var report=score(input);var low=report.cadences.get(5).low;
        assertEquals(4,low.attempted);assertEquals(3,low.unsupportedAtOrigin);assertEquals(1,low.forecasts);
        assertEquals(0,low.resolved);assertEquals(1,low.missingTarget);assertEquals(0,low.rightCensored);
        assertNull(low.meanAbsoluteErrorGp);assertNull(low.centralIntervalCoverage);
        assertEquals(3,report.cadences.get(5).high.resolved);assertEquals(1,report.cadences.get(5).high.rightCensored);
        var missing=PricingForecastCalibration.evaluate(input,input.startAt,input.endAt,(f,item,q,h)->null).cadences.get(5).high;
        assertEquals(missing.attempted,missing.unsupportedAtOrigin);assertEquals(0,missing.forecasts);assertNull(missing.meanAbsoluteErrorGp);
    }
    @Test public void modelUsesOnlyTheRecordedOriginAndNeverSeesALaterQuoteOrFutureFold() {
        var input=new AccountReplayTest().input(1,16);List<Long> origins=new ArrayList<>();
        var report=PricingForecastCalibration.evaluate(input,input.startAt,input.startAt+600,(frame,item,q,h)-> {
            assertTrue(frame.availableAt<=input.startAt+600);assertEquals(frame.availableAt,q.getLowTime().longValue());
            origins.add(frame.availableAt);return constant();
        });
        assertFalse(origins.isEmpty());assertEquals(input.startAt+600,report.lastAvailableAt);
        assertEquals(11,report.frames);assertEquals(2,report.cadences.get(5).low.resolved);
    }
    @Test public void futureTradeTimesAreRejectedBeforeFittingAndAnalysisFailureCannotQualify() {
        var input=new AccountReplayTest().input(1,16);boolean[] fitted={false};
        input.frames.get(1).latest.getAsJsonObject("1").addProperty("highTime",input.endAt+1);
        try{PricingForecastCalibration.evaluate(input,input.startAt,input.endAt,(f,item,q,h)->{fitted[0]=true;return constant();});fail();}
        catch(IllegalArgumentException expected){}assertFalse(fitted[0]);
        var valid=new AccountReplayTest().input(1,16);
        var failure=PricingForecastCalibration.evaluate(valid,valid.startAt,valid.endAt,(f,item,q,h)->{throw new IllegalStateException("fixture");});
        assertEquals("FORECAST_ANALYSIS_FAILURE",failure.support(5));assertEquals(4,failure.cadences.get(5).modelFailures);
    }
    @Test public void longCadencesSparseCapturesAndUnqualifiedIntervalsKeepIndependentSupportOutcomes() {
        var full=new AccountReplayTest().input(1,1440);var report=score(full);
        assertEquals("SUPPORTED_PUBLIC_FORECASTS_ONLY",report.support(5));
        for(int cadence:new int[]{30,120,480})assertEquals("INSUFFICIENT_FORECAST_TARGETS",report.support(cadence));
        var sparse=new AccountReplayTest().input(1,60);sparse.frames.removeIf(f->f.availableAt>sparse.startAt+600 && f.availableAt<sparse.startAt+2100);
        assertEquals("INSUFFICIENT_PUBLIC_OBSERVATION_COVERAGE",score(sparse).support(5));
        assertEquals(1500,score(sparse).maximumObservationGapSeconds);
    }
    @Test public void bandUndercoverageCannotQualifyButPointErrorIsNotReintroducedAsPerMetricDominance() {
        var full=new AccountReplayTest().input(1,1440);
        var unsupported=PricingForecastCalibration.evaluate(full,full.startAt,full.endAt,(f,item,q,h)->new PricingForecastCalibration.Forecast(
            new PricingForecastCalibration.Prediction(108,109,110),new PricingForecastCalibration.Prediction(128,129,130)));
        assertEquals("FORECAST_INTERVAL_UNDERCOVERAGE",unsupported.support(5));
        var biased=PricingForecastCalibration.evaluate(full,full.startAt,full.endAt,(f,item,q,h)->new PricingForecastCalibration.Forecast(
            new PricingForecastCalibration.Prediction(99,101,102),new PricingForecastCalibration.Prediction(119,121,122)));
        assertTrue(biased.cadences.get(5).low.meanAbsoluteErrorGp>biased.cadences.get(5).low.persistenceMeanAbsoluteErrorGp);
        assertEquals("SUPPORTED_PUBLIC_FORECASTS_ONLY",biased.support(5));
    }
    @Test public void decimalOrOverflowingPublicPricesCannotBeTruncatedIntoObservedTargets() {
        for(Number price:List.of(100.5,2147483648L)) {
            var input=new AccountReplayTest().input(1,16);
            input.frames.get(5).latest.getAsJsonObject("1").addProperty("low",price);
            try{score(input);fail();}catch(ArithmeticException expected){}
        }
    }
}
