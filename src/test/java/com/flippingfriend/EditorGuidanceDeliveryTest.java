package com.flippingfriend;

import com.flippingfriend.model.*;
import com.flippingfriend.overlay.*;
import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicLong;
import net.runelite.api.Client;
import net.runelite.api.events.*;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/** Editor close has no offer callback when the player abandons it. Do not lose its successor. */
public class EditorGuidanceDeliveryTest {
    private void boundary(FlippingFriendPlugin plugin)throws Exception {
        var method=FlippingFriendPlugin.class.getDeclaredMethod("refreshOfferEditorBoundary");method.setAccessible(true);method.invoke(plugin);
    }
    private Object field(FlippingFriendPlugin plugin,String name)throws Exception {
        Field f=FlippingFriendPlugin.class.getDeclaredField(name);f.setAccessible(true);return f.get(plugin);
    }
    @Test public void closingSetupPublishesItsQueuedMaintenanceAndImmediatelyRequestsFreshGuidance()throws Exception {
        var f=new AdviceDeliveryBoundaryTest.Fixture();var client=(Client)field(f.plugin,"client");when(client.isClientThread()).thenReturn(true);
        var resolver=(GeWidgetResolver)field(f.plugin,"widgetResolver");when(resolver.isSetupOpen()).thenReturn(true);
        var quantity=mock(net.runelite.api.widgets.Widget.class);when(resolver.getQuantityControl()).thenReturn(quantity);
        var guide=new StepGuide(client,resolver);f.set("stepGuide",guide);guide.setSuggestion(f.advice);boundary(f.plugin);
        var click=mock(MenuOptionClicked.class);when(click.getWidget()).thenReturn(quantity);guide.onMenuOptionClicked(click);
        var maintenance=Suggestion.builder(SuggestionType.MODIFY_SELL).item(561,"Waiting sale").quantity(3).price(123).slot(2).build();
        when(((SuggestionEngine)field(f.plugin,"engine")).recordPresentedMaintenance(any(),anyString(),anyLong())).thenReturn(true);
        guide.setSuggestion(maintenance);assertSame(f.advice,guide.getSuggestion());assertSame(maintenance,guide.getPending());
        when(resolver.isSetupOpen()).thenReturn(false);boundary(f.plugin);
        assertSame(maintenance,guide.getSuggestion());assertNull(guide.getPending());
        verify(f.companion).recordPresentedAdvice(eq(maintenance),anyString(),anyInt());assertEquals(1,f.clientQueue.size());
        boundary(f.plugin);assertEquals("An unchanged closed editor does not schedule another capture",1,f.clientQueue.size());
    }
    @Test public void editorCloseCannotRenewAnExpiredQueuedPrice()throws Exception {
        var f=new AdviceDeliveryBoundaryTest.Fixture();var client=(Client)field(f.plugin,"client");when(client.isClientThread()).thenReturn(true);
        var resolver=(GeWidgetResolver)field(f.plugin,"widgetResolver");when(resolver.isSetupOpen()).thenReturn(true);
        var quantity=mock(net.runelite.api.widgets.Widget.class);when(resolver.getQuantityControl()).thenReturn(quantity);
        var guide=new StepGuide(client,resolver);f.set("stepGuide",guide);guide.setSuggestion(f.advice);boundary(f.plugin);
        var click=mock(MenuOptionClicked.class);when(click.getWidget()).thenReturn(quantity);guide.onMenuOptionClicked(click);
        var pending=Suggestion.builder(SuggestionType.MODIFY_SELL).item(561,"Expired").quantity(3).price(123).slot(2).build();
        guide.setSuggestion(pending);var expiry=Suggestion.class.getDeclaredField("adviceExpiresAt");expiry.setAccessible(true);
        expiry.setLong(pending,java.time.Instant.now().getEpochSecond()-1);
        when(resolver.isSetupOpen()).thenReturn(false);boundary(f.plugin);
        assertNotSame(pending,guide.getSuggestion());assertEquals(1,f.clientQueue.size());
        verify(f.companion,never()).recordPresentedAdvice(eq(pending),anyString(),anyInt());
    }
    @Test public void aFrameChangingDuringPlanningAlwaysGetsASuccessorCapture()throws Exception {
        var f=new AdviceDeliveryBoundaryTest.Fixture();
        doAnswer(c->{((AtomicLong)field(f.plugin,"adviceObservationRevision")).incrementAndGet();return null;}).when(f.companion).refreshPlan();
        f.computeAdvice();verify(f.guide,never()).setSuggestion(any());
        assertEquals("Superseded work cannot silently leave the panel waiting for its heartbeat",1,f.clientQueue.size());
        assertFalse(((java.util.concurrent.atomic.AtomicBoolean)field(f.plugin,"engineRefreshRequested")).get());
    }
    @Test public void pendingExpiryCannotInterruptTheClientDeliveryDiagnostics()throws Exception {
        var f=new AdviceDeliveryBoundaryTest.Fixture();when(f.guide.getPending()).thenReturn(f.advice,null);
        f.queueAdvice();f.clientQueue.remove().run();verify(f.guide).setSuggestion(f.advice);
        verify(f.guide,times(1)).getPending();
    }
}
