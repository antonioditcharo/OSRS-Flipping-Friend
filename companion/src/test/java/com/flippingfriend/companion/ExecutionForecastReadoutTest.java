package com.flippingfriend.companion;

import com.flippingfriend.core.*;
import com.flippingfriend.data.Candle;
import com.flippingfriend.model.*;
import com.google.gson.Gson;
import java.util.*;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

/** Canonical source readout, including a real disposable SQLite reopen. */
public class ExecutionForecastReadoutTest {
    @Rule public TemporaryFolder folder=new TemporaryFolder();
    final Gson gson=new Gson();final String account=AccountIdentity.fromProfileKey("forecast-readout");
    static final long AT=1_700_000_000;
    ExecutionForecast forecast(double horizon) {
        var model=new FillModel();var curve=FillCurve.overRecentHistory(List.of(
            new Candle(1000,110,99,500,400),new Candle(1300,110,99,500,400)));
        var estimate=model.estimateBuy(curve,100,10,horizon);
        return model.forecast(561,true,curve,100,10,horizon,1,estimate,1,estimate.getExpectedHours());
    }
    OfferEvent e(String id,long seq,long at,boolean done,ExecutionForecast forecast) {
        return OfferEvent.builder("fixture",AT+at,done?"BOUGHT":"BUYING").accountId(account)
            .eventIdentity(id,"session","offer").sequence(seq).slot(0).authenticatedObservation(true)
            .item(561,"Nature rune").buying(true).price(100).quantities(10,done?10:0).spent(done?1000:0)
            .firstSeenAt(AT+100).recommendation("original",100,10,0,
                forecast.getExpectedTotalSeconds()/60,forecast.getCompletionProbability())
            .predictionEvidence("birth",AT+98,true).executionForecast(forecast).build();
    }
    @Test public void fullReportKeepsOriginalSnapshotAcrossAChangedTerminalAndReadOnlyReopen()throws Exception {
        var path=folder.newFolder().toPath().resolve("source.db");var original=forecast(1);
        try(var store=new SqliteStore(path)) {
            var first=e("birth",1,100,false,original);var last=e("done",2,400,true,forecast(2));
            store.recordAndProjectOffer(first,gson.toJson(first),true);store.recordAndProjectOffer(last,gson.toJson(last),true);
        }
        try(var store=new SqliteStore(path)) {
            int before=store.recentOfferEvents(AT).size();var report=store.executionLearningReport(account,AT+500);
            assertEquals("OBSERVING",report.getStatus());assertEquals(1,report.getFullForecasts());
            assertEquals(1,report.getComparableForecasts());assertEquals("HOLD_FOR_VALIDATION",report.getPromotionStatus());
            var observation=report.getObservations().get(0);
            assertEquals(original.getFingerprint(),observation.getExecutionForecast().getFingerprint());
            assertEquals(before,store.recentOfferEvents(AT).size());assertEquals(1,report.getCompletedIntervals());
            assertTrue(gson.toJson(report).contains("executionForecast"));
        }
    }
    @Test public void invalidOriginalContextCannotBorrowTheTerminalForecastOrChangeFinancialEvidence()throws Exception {
        var f=forecast(1);var raw=gson.toJsonTree(e("birth",1,100,false,f)).getAsJsonObject();
        raw.getAsJsonObject("executionForecast").addProperty("schemaVersion","future");
        var original=gson.fromJson(raw,OfferEvent.class);
        try(var store=new SqliteStore(folder.newFolder().toPath().resolve("invalid.db"))) {
            store.recordAndProjectOffer(original,gson.toJson(original),true);
            var last=e("done",2,400,true,f);store.recordAndProjectOffer(last,gson.toJson(last),true);
            var report=store.executionLearningReport(account,AT+500);
            assertEquals(0,report.getFullForecasts());assertEquals(0,report.getComparableForecasts());
            assertNull(report.getObservations().get(0).getExecutionForecast());
            assertEquals(2,store.recentOfferEvents(AT).size());assertEquals(1000,last.getSpent());
        }
    }
    @Test public void legacyScalarEvidenceAndAccountingGapsDoNotManufactureFullForecasts() {
        var f=forecast(1);var raw=gson.toJsonTree(e("birth",1,100,false,f)).getAsJsonObject();raw.remove("executionForecast");
        var legacy=gson.fromJson(raw,OfferEvent.class);
        var scalar=ExecutionObservationLedger.read(account,AT+500,1,List.of(legacy),Set.of());
        assertEquals(1,scalar.getComparableForecasts());assertEquals(0,scalar.getFullForecasts());
        assertNull(scalar.getObservations().get(0).getExecutionForecast());
        var gap=ExecutionObservationLedger.read(account,AT+500,1,List.of(e("birth",1,100,false,f)),Set.of("offer"));
        assertEquals(0,gap.getFullForecasts());assertEquals(0,gap.getComparableForecasts());
    }
}
