package com.flippingfriend.overlay;

import com.flippingfriend.model.*;
import java.util.concurrent.atomic.AtomicLong;
import net.runelite.api.Client;
import net.runelite.api.events.MenuOptionClicked;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class GuidanceDeliveryReceiptTest {
    private Suggestion sale(int item){return Suggestion.builder(SuggestionType.SELL).item(item,"Sale").price(100).quantity(3).build();}
    @Test public void anEditorVerificationWaitIsDistinguishedFromALostForegroundSale() {
        var guide=new StepGuide(mock(Client.class),mock(GeWidgetResolver.class));var sale=sale(561);guide.setSuggestion(sale);
        assertEquals(StepGuide.DeliveryOutcome.VISIBLE,guide.deliveryReceipt(sale).outcome);
        var wait=Suggestion.waiting("Verifying selected quantity","Checking exact cost");guide.setEditorQuote(wait);
        var receipt=guide.deliveryReceipt(sale);assertEquals(1,receipt.schemaVersion);
        assertEquals(StepGuide.DeliveryOutcome.SELECTED_EDITOR_QUOTE,receipt.outcome);assertSame(wait,receipt.visible);
        guide.onSetupClosed();assertEquals(StepGuide.DeliveryOutcome.VISIBLE,guide.deliveryReceipt(sale).outcome);
    }
    @Test public void expiredEditorQuotesAndPendingActionsAreNotReportedAsCurrent() {
        var clock=new AtomicLong(100);var client=mock(Client.class);when(client.isClientThread()).thenReturn(true);
        var resolver=mock(GeWidgetResolver.class);when(resolver.isSetupOpen()).thenReturn(true);
        var guide=new StepGuide(client,resolver,clock::get);var current=sale(561);guide.setSuggestion(current);
        var control=mock(net.runelite.api.widgets.Widget.class);when(resolver.getQuantityControl()).thenReturn(control);
        var click=mock(MenuOptionClicked.class);when(click.getWidget()).thenReturn(control);guide.onMenuOptionClicked(click);
        var next=sale(562);guide.setSuggestion(next);var receipt=guide.deliveryReceipt(next);
        assertEquals(StepGuide.DeliveryOutcome.DEFERRED_EDITOR,receipt.outcome);assertSame(next,receipt.pending);
        clock.addAndGet(StepGuide.MAX_ADVICE_AGE_NANOS);receipt=guide.deliveryReceipt(next);
        assertEquals(StepGuide.DeliveryOutcome.EXPIRED,receipt.outcome);assertNull(receipt.pending);
        guide.setSuggestion(current);var selected=sale(563);guide.setEditorQuote(selected);clock.addAndGet(StepGuide.MAX_ADVICE_AGE_NANOS);
        assertEquals(StepGuide.DeliveryOutcome.EXPIRED,guide.deliveryReceipt(current).outcome);
    }
    @Test public void pauseAndARealSuccessorHaveTheirOwnDeliveryOutcomes() {
        var guide=new StepGuide(mock(Client.class),mock(GeWidgetResolver.class));var old=sale(561);var next=sale(562);
        guide.setSuggestion(old);guide.setSuggestion(next);var receipt=guide.deliveryReceipt(old);
        assertEquals(StepGuide.DeliveryOutcome.SUPERSEDED,receipt.outcome);assertSame(next,receipt.visible);
        guide.setPaused(true);assertEquals(StepGuide.DeliveryOutcome.PAUSED,guide.deliveryReceipt(next).outcome);
    }
}
