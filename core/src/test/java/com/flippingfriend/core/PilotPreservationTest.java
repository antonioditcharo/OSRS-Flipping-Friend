package com.flippingfriend.core;

import java.nio.file.*;
import java.util.*;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class PilotPreservationTest {
    @Rule public TemporaryFolder folder=new TemporaryFolder();
    @Test public void onlyOneWriterOwnsAnAccountAndReleaseDoesNotEraseItsOutbox()throws Exception {
        Path root=folder.newFolder().toPath();Files.writeString(root.resolve("offer-event-outbox.json"),"original ids");
        try(var first=AccountWriterLease.tryAcquire(root)) {
            assertNotNull(first);assertNull(AccountWriterLease.tryAcquire(root));
            try(var other=AccountWriterLease.tryAcquire(folder.newFolder().toPath())){assertNotNull(other);}
        }
        try(var next=AccountWriterLease.tryAcquire(root)){assertNotNull(next);assertEquals("original ids",Files.readString(root.resolve("offer-event-outbox.json")));}
    }
    @Test public void repeatedIdenticalHistoryTradesKeepTheirOccurrencesAndAverageMoneyStaysBounded() {
        var snapshot=new GeHistorySnapshot();snapshot.accountId=AccountIdentity.fromProfileKey("history");snapshot.observedAt=2000000000;
        String row="{\"b\":true,\"i\":561,\"q\":3,\"p\":101,\"t\":\"2026-10-20T12:00:00Z\"}";
        snapshot.historyJson="["+row+","+row+"]";
        var rows=snapshot.rows();assertEquals(2,rows.size());assertEquals(0,rows.get(0).occurrence);assertEquals(1,rows.get(1).occurrence);
        assertEquals(303,rows.get(0).grossLower());assertEquals(305,rows.get(0).grossUpper());
        assertEquals(64,snapshot.evidenceId().length());
    }
    @Test public void impossibleProfitPreferencesFindASafeAlternativeWithoutRelaxingCashOrLossLimits() {
        var candidate=new PortfolioCandidate(561,"Nature rune","runes",100,100,100,120,120,120,10,100,
            180,50,1,.95,.95,.1,.1,.25,20000);
        var optimizer=new PortfolioOptimizer();var limits=new PortfolioConstraints(1,10000,100,0,0)
            .withCommitted(Map.of(),Map.of(),Long.MAX_VALUE);
        assertEquals("READY",optimizer.optimizePreferred(List.of(candidate),limits,"preference",10000).getStatus());
        assertTrue(optimizer.optimizePreferred(List.of(candidate),limits.withCoins(999),"cash",10000).getAllocations().isEmpty());
        assertTrue(optimizer.optimizePreferred(List.of(candidate),new PortfolioConstraints(1,10000,49,0,0)
            .withCommitted(Map.of(),Map.of(),Long.MAX_VALUE),"loss",10000).getAllocations().isEmpty());
        assertTrue(optimizer.optimizePreferred(List.of(candidate),limits.withSlots(0),"slots",10000).getAllocations().isEmpty());
    }
    @Test public void actualRuneLiteEpochMillisecondHistoryPreservesItsObservationClock() {
        var snapshot=new GeHistorySnapshot();snapshot.accountId=AccountIdentity.fromProfileKey("history");snapshot.observedAt=1792860000;
        snapshot.historyJson="[{\"b\":false,\"i\":561,\"q\":7,\"p\":101,\"t\":1792859999123}]";
        var row=snapshot.rows().get(0);assertEquals(1792859999,row.recordedAt);assertFalse(row.buying);
        assertEquals(707,row.grossLower());assertEquals(713,row.grossUpper());
    }
}
