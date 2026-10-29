package com.flippingfriend.ui;

import com.flippingfriend.model.*;
import com.flippingfriend.session.AccountState;
import java.util.*;
import java.util.concurrent.*;
import javax.swing.*;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class ForegroundGuidanceTest {
    @Test public void actionAndInvalidationDoNotWaitForOptionalMeasurements()throws Exception {
        var f=new CompactPanelContractTest();f.setup();clearInvocations(f.companion);
        var sale=Suggestion.builder(SuggestionType.SELL).item(562,"Current sale").quantity(2).price(123).build();
        SwingUtilities.invokeAndWait(()->{
            when(f.guide.getSuggestion()).thenReturn(sale);f.panel.refresh();
            verify(f.companion,never()).requestPerformanceRefresh();
            assertTrue(CompactPanelContractTest.tree(f.panel).stream().filter(c->c instanceof JTextArea)
                .anyMatch(c->((JTextArea)c).getText().equals("Current sale")));
            when(f.monitor.getState()).thenReturn(AccountState.loggedOut());f.panel.refresh();
            assertFalse(f.button("skip-action").isEnabled());
            assertTrue(CompactPanelContractTest.tree(f.panel).stream().filter(c->c instanceof JTextArea)
                .anyMatch(c->((JTextArea)c).getText().equals("Log in to start")));
        });
        SwingUtilities.invokeAndWait(()->{});
        verify(f.companion,never()).requestPerformanceRefresh(); // deferred callback reads current logout
    }
    @Test public void measuredRefreshHasAnActualEdtEntryAndDoesNotRunOptionalWorkInline()throws Exception {
        var f=new CompactPanelContractTest();f.setup();clearInvocations(f.companion);
        var timing=new GuidanceTiming("foreground-test:MAIN:1","MAIN");timing.delivery("VISIBLE",f.displayed);
        SwingUtilities.invokeAndWait(()->{
            f.panel.refresh(timing,f.displayed);
            verify(f.companion,never()).requestPerformanceRefresh();
            assertEquals("HIDDEN",timing.diagnosticSnapshot().get("paintOutcome"));
            f.panel.closeWindows();
        });
        SwingUtilities.invokeAndWait(()->{});
        verify(f.companion,never()).requestPerformanceRefresh(); // close cancels optional work
    }
    @Test public void taskAttributionPreservesSerialOfferReplayAndFrameOrder()throws Exception {
        var worker=new GuidanceWorker();var started=new CountDownLatch(1);var release=new CountDownLatch(1);
        var effects=Collections.synchronizedList(new ArrayList<String>());
        try {
            GuidanceWorker.named(worker,GuidanceWorker.Task.OFFER_REPLAY).execute(()->{
                started.countDown();try{assertTrue(release.await(5,TimeUnit.SECONDS));}
                catch(InterruptedException interrupted){throw new AssertionError(interrupted);}
                effects.add("original-offer");
            });
            assertTrue(started.await(5,TimeUnit.SECONDS));
            var timing=new GuidanceTiming("worker-test:MAIN:1","MAIN");GuidanceWorker.describe(worker,timing);
            assertEquals("OFFER_REPLAY",timing.diagnosticSnapshot().get("workerActiveTask"));
            GuidanceWorker.named(worker,GuidanceWorker.Task.MAIN).execute(()->effects.add("account-frame"));
            release.countDown();worker.shutdown();assertTrue(worker.awaitTermination(5,TimeUnit.SECONDS));
            assertEquals(List.of("original-offer","account-frame"),effects);
        }finally{release.countDown();worker.shutdownNow();}
    }
}
