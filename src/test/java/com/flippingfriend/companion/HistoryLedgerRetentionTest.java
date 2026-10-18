package com.flippingfriend.companion;

import com.flippingfriend.core.*;
import com.flippingfriend.data.*;
import com.google.gson.Gson;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/** Display history can survive delivery gaps; stale history can never authorize trading. */
public class HistoryLedgerRetentionTest {
    @Rule public TemporaryFolder folder=new TemporaryFolder();
    final long now=Instant.now().getEpochSecond();
    AccountLedgerSnapshot ledger(String account,long at) {
        return new AccountLedgerSnapshot(account,at,1,0,0,1,0,0,0,0,List.of()).withTradeEvidence(List.of(),
            List.of(new MissedFlipEvidence("sale","offer",561,"Nature rune","DISPOSE",1,at,
                null,100L,2L,2L,null,null,"Acquisition unavailable")));
    }
    @Test public void anActualPendingCaptureStopsTradingWithoutEmptyingRecordedHistory()throws Exception {
        var storage=TestStorage.rootedAt(folder.newFolder().toPath(),"one");
        var outbox=new OfferEventOutbox(storage);
        var client=new CompanionClient(storage,new Gson(),new SuggestionLedger(),outbox);
        client.resumeOfferReplay();var recorded=ledger(storage.accountId(),now);
        assertTrue(client.acceptLedger(recorded,storage.accountId()));
        outbox.enqueueObservedOffer((id,session,sequence)->OfferEvent.builder("capture",now,"EMPTY")
            .accountId(storage.accountId()).slot(0).authenticatedObservation(true)
            .eventIdentity(id,session,null).sequence(sequence).build());
        assertNull("Pending evidence returns before any HTTP call",client.refreshPlan());
        assertNull(client.lastLedger());assertNull(client.tradingLedger());
        assertSame("The investigation view must not flicker empty",recorded,client.historyLedger());
        assertEquals(1,outbox.pending().size());
    }
    @Test public void aFailedRefreshRetainsHistoryAndExpiredHistoryIsNeverTradingEvidence()throws Exception {
        var storage=TestStorage.rootedAt(folder.newFolder().toPath(),"one");
        var client=new CompanionClient(storage,new Gson(),new SuggestionLedger());client.resumeOfferReplay();
        var recorded=ledger(storage.accountId(),now-151);assertTrue(client.acceptLedger(recorded,storage.accountId()));
        assertNull(client.lastLedger());assertNull(client.tradingLedger());assertSame(recorded,client.historyLedger());
        assertFalse(client.acceptLedger(null,storage.accountId()));assertSame(recorded,client.historyLedger());
    }
    @Test public void accountSwitchAndLogoutClearDisplayedHistoryWithoutResurrectingIt()throws Exception {
        Path root=folder.newFolder().toPath();var one=TestStorage.rootedAt(root,"one");var two=TestStorage.rootedAt(root,"two");
        String first=one.accountId(),second=two.accountId();Path firstDir=one.accountDir(),secondDir=two.accountDir();
        var storage=mock(PluginStorage.class);when(storage.accountId()).thenReturn(first);
        when(storage.accountDir(first)).thenReturn(firstDir);when(storage.accountDir(second)).thenReturn(secondDir);
        var client=new CompanionClient(storage,new Gson(),new SuggestionLedger());client.resumeOfferReplay();
        var recorded=ledger(one.accountId(),now);assertTrue(client.acceptLedger(recorded,one.accountId()));
        long firstRequest=generation(client);
        when(storage.accountId()).thenReturn(second);assertNull(client.historyLedger());
        assertFalse(client.acceptLedger(recorded,one.accountId()));
        when(storage.accountId()).thenReturn(first);assertNull(client.historyLedger());
        assertFalse("Switching back cannot resurrect an earlier response",client.acceptLedger(recorded,first,firstRequest));
        client.acceptLedger(recorded,one.accountId());client.pauseOfferReplay();assertNull(client.historyLedger());
        client.resumeOfferReplay();assertNull(client.historyLedger());
    }
    long generation(CompanionClient client)throws Exception {
        var field=CompanionClient.class.getDeclaredField("ledgerGeneration");field.setAccessible(true);return field.getLong(client);
    }
    @Test public void aLateResponseCannotRestoreHistoryAcrossLogoutAndLogin()throws Exception {
        var storage=TestStorage.rootedAt(folder.newFolder().toPath(),"one");
        var client=new CompanionClient(storage,new Gson(),new SuggestionLedger());client.resumeOfferReplay();
        long request=generation(client);var recorded=ledger(storage.accountId(),now);
        client.pauseOfferReplay();client.resumeOfferReplay();
        assertFalse(client.acceptLedger(recorded,storage.accountId(),request));
        assertNull(client.historyLedger());assertNull(client.lastLedger());
        assertTrue(client.acceptLedger(recorded,storage.accountId()));
        assertFalse(client.acceptLedger(null,storage.accountId(),request));
        assertSame(recorded,client.lastLedger());assertSame(recorded,client.historyLedger());
    }
    @Test public void historyDoesNotMoveBackwardsOrAcceptFutureRecords()throws Exception {
        var storage=TestStorage.rootedAt(folder.newFolder().toPath(),"one");
        var client=new CompanionClient(storage,new Gson(),new SuggestionLedger());client.resumeOfferReplay();
        var recorded=ledger(storage.accountId(),now);assertTrue(client.acceptLedger(recorded,storage.accountId()));
        assertTrue(client.acceptLedger(ledger(storage.accountId(),now-1),storage.accountId()));
        assertSame(recorded,client.historyLedger());
        assertFalse(client.acceptLedger(ledger(storage.accountId(),now+600),storage.accountId()));
        assertSame(recorded,client.historyLedger());assertNull(client.lastLedger());
    }
}
