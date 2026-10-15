package com.flippingfriend.companion;

import com.flippingfriend.core.*;
import com.google.gson.Gson;
import java.nio.file.*;
import java.util.*;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

/** Recorded-source behavior with disposable real stores, including SELECT-only reopen. */
public class ExecutionObservationLedgerTest {
    @Rule public TemporaryFolder folder=new TemporaryFolder();
    final Gson gson=new Gson();final String account=AccountIdentity.fromProfileKey("observation-ledger");
    static final long AT=1_700_000_000;
    OfferEvent e(String id,long seq,long at,String state,int filled,boolean authenticated) {
        var b=OfferEvent.builder("fixture",AT+at,state).accountId(account)
            .eventIdentity(id,"session",state.equals("EMPTY")?null:"offer").sequence(seq).slot(0)
            .authenticatedObservation(authenticated);
        if(!state.equals("EMPTY"))b.item(561,"Nature rune").buying(true).price(100).quantities(10,filled)
            .spent(100L*filled).firstSeenAt(AT+100).recommendation("original",100,10,0,12,.75)
            .predictionEvidence("birth",AT+98,true);
        return b.build();
    }
    void accept(SqliteStore s,OfferEvent e)throws Exception {s.recordAndProjectOffer(e,gson.toJson(e),true);}
    void start(SqliteStore s,boolean empty)throws Exception {
        if(empty)accept(s,e("empty",1,90,"EMPTY",0,true));
        accept(s,e("birth",2,100,"BUYING",0,true));
    }
    ExecutionTimingObservation only(SqliteStore s,long at)throws Exception {
        var report=s.executionLearningReport(account,AT+at);
        assertEquals("OBSERVING",report.getStatus());assertEquals("HOLD_FOR_VALIDATION",report.getPromotionStatus());
        assertEquals(1,report.getObservations().size());return report.getObservations().get(0);
    }
    @Test public void reconnectCompletionIsAnIntervalWithPlacementAndSecondResolutionUncertainty()throws Exception {
        Path db=folder.newFolder().toPath().resolve("interval.db");var terminal=e("done",4,400,"BOUGHT",10,true);
        try(var s=new SqliteStore(db)) {
            start(s,true);accept(s,e("partial",3,130,"BUYING",3,true));accept(s,terminal);
            var o=only(s,500);
            assertEquals("COMPLETED_INTERVAL",o.getOutcome());assertTrue(o.isPredictionComparable());
            assertEquals(Long.valueOf(AT+90),o.getPlacementLowerAt());assertEquals(Long.valueOf(AT+101),o.getPlacementUpperAt());
            assertEquals(Long.valueOf(AT+130),o.getCompletionAfterAt());assertEquals(Long.valueOf(AT+401),o.getCompletionByAt());
            assertEquals(Long.valueOf(29),o.getDurationLowerSeconds());assertEquals(Long.valueOf(311),o.getDurationUpperSeconds());
            assertEquals(Long.valueOf(AT+100),o.getFirstFillAfterAt());assertEquals(Long.valueOf(AT+131),o.getFirstFillByAt());
            assertEquals(Long.valueOf(0),o.getFirstFillLowerSeconds());assertEquals(Long.valueOf(41),o.getFirstFillUpperSeconds());
            assertEquals(12,o.getPredictedMinutes(),0);assertEquals(2,s.positionEventCount());
        }
        byte[] before=Files.readAllBytes(db);
        try(var s=SqliteStore.openReadOnly(db)) {
            assertEquals(Long.valueOf(311),only(s,500).getDurationUpperSeconds());
            assertEquals(gson.toJson(terminal),s.recentOfferEvents(AT).get(3));
        }
        assertArrayEquals("Readout must not alter the database",before,Files.readAllBytes(db));
    }
    @Test public void cancellationUsesLastConfirmedActiveReadoutInsteadOfOfflineCancellationArrival()throws Exception {
        try(var s=new SqliteStore(folder.newFolder().toPath().resolve("cancel.db"))) {
            start(s,true);accept(s,e("partial",3,130,"BUYING",3,true));accept(s,e("cancel",4,400,"CANCELLED_BUY",3,true));
            var o=only(s,500);assertEquals("CANCELLED_CENSORED",o.getOutcome());
            assertEquals(Long.valueOf(29),o.getDurationLowerSeconds());assertNull(o.getDurationUpperSeconds());
            assertNull(o.getCompletionByAt());assertEquals("partial",o.getLastActiveEventId());
            assertEquals(Long.valueOf(41),o.getFirstFillUpperSeconds());
        }
    }
    @Test public void cancellationWithoutAnActiveHeartbeatCannotClaimTheWholeElapsedWait()throws Exception {
        try(var s=new SqliteStore(folder.newFolder().toPath().resolve("early-cancel.db"))) {
            start(s,true);accept(s,e("cancel",3,400,"CANCELLED_BUY",0,true));
            var o=only(s,500);assertEquals(Long.valueOf(0),o.getDurationLowerSeconds());
            assertNull(o.getDurationUpperSeconds());assertNull(o.getFirstFillByAt());
        }
    }
    @Test public void missingEmptyObservationLeavesPlacementAndDurationUpperBoundUnknown()throws Exception {
        try(var s=new SqliteStore(folder.newFolder().toPath().resolve("unknown-placement.db"))) {
            start(s,false);accept(s,e("active",3,130,"BUYING",0,true));accept(s,e("done",4,400,"BOUGHT",10,true));
            var o=only(s,500);assertNull(o.getPlacementLowerAt());assertNull(o.getDurationUpperSeconds());
            assertEquals(Long.valueOf(29),o.getDurationLowerSeconds());assertNull(o.getFirstFillUpperSeconds());
        }
    }
    @Test public void cachedTerminalAndLateHeartbeatsDoNotMoveTheFirstAuthenticatedCompletion()throws Exception {
        try(var s=new SqliteStore(folder.newFolder().toPath().resolve("cache.db"))) {
            start(s,true);accept(s,e("active",3,130,"BUYING",0,true));accept(s,e("cached",4,400,"BOUGHT",10,false));
            assertEquals("OPEN_CENSORED",only(s,500).getOutcome());
            accept(s,e("done",5,800,"BOUGHT",10,true));
            var first=only(s,900);assertEquals("done",first.getTerminalEventId());assertEquals(Long.valueOf(711),first.getDurationUpperSeconds());
            accept(s,e("heartbeat",6,1000,"BOUGHT",10,true));
            var later=only(s,1100);assertEquals("done",later.getTerminalEventId());
            assertEquals(first.getDurationUpperSeconds(),later.getDurationUpperSeconds());
            assertEquals(1,s.executionLearningReport(account,AT+1100).getCompletedIntervals());
        }
    }
    @Test public void legacyTotalsAreNotReplayedIntoProspectiveIntervals()throws Exception {
        try(var s=new SqliteStore(folder.newFolder().toPath().resolve("legacy.db"))) {
            s.recordExecution(561,true,10,40,10,40);
            var json=gson.toJsonTree(e("birth",1,100,"BUYING",0,true)).getAsJsonObject();
            for(String key:new String[]{"predictionEvidenceVersion","predictionSourceEventId","adviceObservedAt","predictionComparable"})json.remove(key);
            accept(s,gson.fromJson(json,OfferEvent.class));
            var report=s.executionLearningReport(account,AT+500);assertTrue(report.getObservations().isEmpty());
            assertEquals(1,s.executionStats().get(561).observed);
            accept(s,e("new-readout",2,130,"BUYING",0,true));
            assertFalse(only(s,500).isPredictionComparable());assertEquals(0,only(s,500).getPredictedMinutes(),0);
            assertEquals(1,s.executionStats().get(561).observed);
        }
    }
    @Test public void clearedWithoutSettlementIsCensoredAndDoesNotInventCancellationOrCompletion() {
        var history=List.of(e("empty",1,90,"EMPTY",0,true),e("birth",2,100,"BUYING",0,true),
            e("active",3,130,"BUYING",0,true),e("clear",4,400,"EMPTY",0,true));
        var report=ExecutionObservationLedger.read(account,AT+500,4,history,Set.of("offer"));
        assertEquals(1,report.getEndedCensored());assertEquals(0,report.getCompletedIntervals());
        var o=report.getObservations().get(0);assertEquals("OBSERVATION_ENDED_CENSORED",o.getOutcome());
        assertNull(o.getTerminalEventId());assertNull(o.getDurationUpperSeconds());assertFalse(o.isPredictionComparable());
    }
    @Test public void futureOrForeignSourcesFailClosedWithoutChangingRows()throws Exception {
        try(var s=new SqliteStore(folder.newFolder().toPath().resolve("scope.db"))) {
            start(s,true);int before=s.recentOfferEvents(AT).size();
            assertEquals("UNAVAILABLE",s.executionLearningReport(AccountIdentity.fromProfileKey("foreign"),AT+500).getStatus());
            assertEquals("UNAVAILABLE",s.executionLearningReport(account,AT+99).getStatus());
            assertEquals(before,s.recentOfferEvents(AT).size());
        }
    }
    @Test public void opaqueSessionAndOfferIdentitiesCannotCollideThroughStringConcatenation() {
        var a=OfferEvent.builder("fixture",AT+100,"BUYING").accountId(account).eventIdentity("one","a","1:b")
            .sequence(1).slot(0).authenticatedObservation(true).item(561,"Nature rune").buying(true).price(100)
            .quantities(10,0).firstSeenAt(AT+100).predictionEvidence(null,0,false).build();
        var b=OfferEvent.builder("fixture",AT+100,"BUYING").accountId(account).eventIdentity("two","a:0","b")
            .sequence(1).slot(1).authenticatedObservation(true).item(561,"Nature rune").buying(true).price(100)
            .quantities(10,0).firstSeenAt(AT+100).predictionEvidence(null,0,false).build();
        var report=ExecutionObservationLedger.read(account,AT+500,2,List.of(a,b),Set.of());
        assertEquals(2,report.getObservations().size());
        assertEquals("one",report.getObservations().get(0).getSourceEventId());
        assertEquals("two",report.getObservations().get(1).getSourceEventId());
    }
    @Test public void contradictoryReadoutClocksDoNotProduceAFiniteTimingRange() {
        var history=List.of(e("empty",1,90,"EMPTY",0,true),e("birth",2,100,"BUYING",0,true),
            e("active",3,130,"BUYING",0,true),e("done",4,120,"BOUGHT",10,true));
        var report=ExecutionObservationLedger.read(account,AT+500,4,history,Set.of());
        var o=report.getObservations().get(0);assertFalse(o.isPredictionComparable());
        assertNull("A regressed clock cannot bound elapsed execution time",o.getDurationUpperSeconds());
        assertNull(o.getDurationLowerSeconds());
    }
    @Test public void boundedDiagnosticRowsDoNotSilentlyTruncateWholePrefixCounts() {
        List<OfferEvent> history=new ArrayList<>();long sequence=1;
        for(int i=0;i<205;i++) {
            long start=100+i*10;String birth="birth-"+i;
            history.add(e("empty-"+i,sequence++,start-2,"EMPTY",0,true));
            for(boolean done:new boolean[]{false,true}) {
                var json=gson.toJsonTree(e(done?"done-"+i:birth,sequence++,start+(done?5:0),done?"BOUGHT":"BUYING",done?10:0,true)).getAsJsonObject();
                json.addProperty("offerIdentity","offer-"+i);json.addProperty("firstSeenAt",AT+start);
                json.addProperty("predictionSourceEventId",birth);json.addProperty("adviceObservedAt",AT+start-2);
                history.add(gson.fromJson(json,OfferEvent.class));
            }
        }
        var report=ExecutionObservationLedger.read(account,AT+10000,history.size(),history,Set.of());
        assertEquals(205,report.getTotalObservations());assertEquals(205,report.getCompletedIntervals());
        assertEquals(205,report.getComparableForecasts());assertTrue(report.isObservationsTruncated());
        assertEquals(200,report.getObservations().size());assertEquals("birth-5",report.getObservations().get(0).getSourceEventId());
        assertEquals("birth-204",report.getObservations().get(199).getSourceEventId());
    }
    @Test public void sourceCursorTracksOfferEvidenceInsteadOfUnrelatedPrunableSnapshots()throws Exception {
        try(var s=new SqliteStore(folder.newFolder().toPath().resolve("cursor.db"))) {
            start(s,true);long before=s.executionLearningReport(account,AT+500).getSourceCursor();
            s.recordEvent(AT+300,"snapshot",null,"ACCOUNT_STATE","{}");
            assertEquals(before,s.executionLearningReport(account,AT+500).getSourceCursor());
        }
    }
    @Test public void fullObservedQuantityIsCompletionEvenIfTerminalLabelSaysCancelled()throws Exception {
        try(var s=new SqliteStore(folder.newFolder().toPath().resolve("cancelled-full.db"))) {
            start(s,true);accept(s,e("active",3,130,"BUYING",3,true));accept(s,e("full-cancel",4,400,"CANCELLED_BUY",10,true));
            var o=only(s,500);assertEquals("COMPLETED_INTERVAL",o.getOutcome());
            assertEquals(Long.valueOf(311),o.getDurationUpperSeconds());
        }
    }
    @Test public void incompleteBoughtStateIsInconsistentRatherThanACensoredCancellation() {
        var history=List.of(e("empty",1,90,"EMPTY",0,true),e("birth",2,100,"BUYING",0,true),
            e("broken",3,130,"BOUGHT",3,true));
        var report=ExecutionObservationLedger.read(account,AT+500,3,history,Set.of());
        assertEquals(1,report.getInconsistentSources());assertEquals(0,report.getCancelledCensored());
        var o=report.getObservations().get(0);assertFalse(o.isPredictionComparable());assertNull(o.getDurationLowerSeconds());
    }
}
