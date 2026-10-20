package com.flippingfriend.overlay;

import com.flippingfriend.model.*;
import java.util.concurrent.atomic.AtomicLong;
import net.runelite.api.Client;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.widgets.Widget;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/** Deterministic receipt clocks; no sleeps, wall-clock changes or real game input. */
public class GuidanceLifetimeTest {
    final AtomicLong clock=new AtomicLong(100);
    final Client client=mock(Client.class);
    final GeWidgetResolver resolver=mock(GeWidgetResolver.class);
    final StepGuide guide=new StepGuide(client,resolver,clock::get);
    Suggestion advice(SuggestionType type,int item) {
        return Suggestion.builder(type).item(item,"Fixture").quantity(10).price(100).slot(0).build();
    }
    void beginEntry() {
        when(client.isClientThread()).thenReturn(true);when(resolver.isSetupOpen()).thenReturn(true);
        var quantity=mock(Widget.class);when(resolver.getQuantityControl()).thenReturn(quantity);
        var click=mock(MenuOptionClicked.class);when(click.getWidget()).thenReturn(quantity);guide.onMenuOptionClicked(click);
    }
    @Test public void everyActionExpiresWithoutAnUpstreamDeadlineAndWithoutLosingItsOriginalObject() {
        for(var type:SuggestionType.values())if(type.isActionable()) {
            guide.reset();var original=advice(type,561);guide.setSuggestion(original);
            clock.addAndGet(StepGuide.MAX_ADVICE_AGE_NANOS-1);assertSame(original,guide.getSuggestion());
            clock.incrementAndGet();assertEquals(SuggestionType.WAIT,guide.getSuggestion().getType());
            assertEquals(0,original.getAdviceExpiresAt());
        }
    }
    @Test public void renewingIdenticalGuidanceCannotRenewTheReceiptCapturedByAnEarlierKeyPress() {
        var original=advice(SuggestionType.SELL,561);guide.setSuggestion(original);var old=guide.getAdviceReceipt();
        clock.addAndGet(StepGuide.MAX_ADVICE_AGE_NANOS/2);guide.setSuggestion(advice(SuggestionType.SELL,561));
        var fresh=guide.getAdviceReceipt();clock.addAndGet(StepGuide.MAX_ADVICE_AGE_NANOS/2);
        assertFalse(guide.isReceiptValid(old));assertTrue(guide.isReceiptValid(fresh));assertTrue(guide.getSuggestion().isActionable());
    }
    @Test public void deferredAdviceKeepsItsOriginalReceiptAgeWhenTheEditorCloses() {
        guide.setSuggestion(advice(SuggestionType.SELL,561));beginEntry();
        var pending=advice(SuggestionType.BUY,562);guide.setSuggestion(pending);assertSame(pending,guide.getPending());
        clock.addAndGet(StepGuide.MAX_ADVICE_AGE_NANOS);assertNull(guide.getPending());
        guide.onSetupClosed();assertEquals(SuggestionType.WAIT,guide.getSuggestion().getType());
    }
    @Test public void unrelatedPendingUpdatesCannotKeepTheOldMidEntryTradeAlive() {
        guide.setSuggestion(advice(SuggestionType.SELL,561));beginEntry();
        clock.addAndGet(StepGuide.MAX_ADVICE_AGE_NANOS-1);guide.setSuggestion(advice(SuggestionType.BUY,562));
        clock.incrementAndGet();assertEquals(SuggestionType.WAIT,guide.getSuggestion().getType());
        var fresh=advice(SuggestionType.SELL,563);guide.setSuggestion(fresh);
        assertSame("An expired editor must release deferral for fresh guidance",fresh,guide.getSuggestion());assertNull(guide.getPending());
    }
    @Test public void personalQuotesAndTrackedGuidanceHaveIndependentReceiptAges() {
        guide.setSuggestion(advice(SuggestionType.BUY,561));clock.addAndGet(StepGuide.MAX_ADVICE_AGE_NANOS/2);
        var personal=advice(SuggestionType.SELL,562);guide.setEditorQuote(personal);
        clock.addAndGet(StepGuide.MAX_ADVICE_AGE_NANOS/2);assertSame(personal,guide.getSuggestion());
        guide.setEditorQuote(null);assertEquals(SuggestionType.WAIT,guide.getSuggestion().getType());
        guide.setEditorQuote(personal);clock.addAndGet(StepGuide.MAX_ADVICE_AGE_NANOS);
        assertEquals(SuggestionType.WAIT,guide.getSuggestion().getType());
    }
    @Test public void backwardsClockAndExpiredUpstreamAuthorityCannotExtendAReceipt() {
        guide.setSuggestion(advice(SuggestionType.MODIFY_SELL,561));clock.decrementAndGet();assertFalse(guide.getSuggestion().isActionable());
        var expired=Suggestion.builder(SuggestionType.BUY).item(561,"Fixture").quantity(10).price(100)
            .adviceContext("account","old",0,java.time.Instant.now().getEpochSecond()-1).build();
        guide.setSuggestion(expired);assertFalse(guide.getSuggestion().isActionable());
    }
    @Test public void aFreshStopOnTheSameItemImmediatelyWithdrawsItsMidEntryBuyAndQueuedProgress() {
        for(var type:new SuggestionType[]{SuggestionType.BUY,SuggestionType.MODIFY_BUY}) {
            guide.reset();guide.setSuggestion(advice(type,561));beginEntry();
            long generation=guide.getAdviceGeneration();
            guide.setSuggestion(advice(SuggestionType.BUY,562));assertNotNull(guide.getPending());
            var stop=Suggestion.builder(type==SuggestionType.BUY?SuggestionType.SELL:SuggestionType.CANCEL)
                .item(561,"Fixture").quantity(4).price(80).slot(0).lossCut(true).build();
            guide.setSuggestion(stop);
            assertSame("A stop must not queue behind the purchase it supersedes",stop,guide.getSuggestion());assertNull(guide.getPending());
            assertTrue(guide.getAdviceGeneration()>generation);
            guide.onSetupClosed();assertSame(stop,guide.getSuggestion());
            // The input helper also checks this generation before delivering a queued numeric key.
        }
    }
    @Test public void stoppingAnUnderlyingBuyDoesNotReplaceAnUnrelatedPersonalSaleQuote() {
        guide.setSuggestion(advice(SuggestionType.BUY,561));beginEntry();
        var personal=advice(SuggestionType.SELL,563);guide.setEditorQuote(personal);
        var stop=Suggestion.builder(SuggestionType.CANCEL).item(561,"Fixture").quantity(4).price(80).lossCut(true).build();
        guide.setSuggestion(stop);assertSame(personal,guide.getSuggestion());guide.setEditorQuote(null);
        assertSame(stop,guide.getSuggestion());
    }
    @Test public void anUnrelatedStopStillRespectsTheBoundedManualEditorDeferral() {
        var original=advice(SuggestionType.MODIFY_BUY,561);guide.setSuggestion(original);beginEntry();
        var stop=Suggestion.builder(SuggestionType.SELL).item(562,"Other").quantity(4).price(80).lossCut(true).build();
        guide.setSuggestion(stop);assertSame(original,guide.getSuggestion());assertSame(stop,guide.getPending());
        clock.addAndGet(StepGuide.MAX_ADVICE_AGE_NANOS);assertEquals(SuggestionType.WAIT,guide.getSuggestion().getType());
        guide.setSuggestion(stop);assertSame(stop,guide.getSuggestion());assertNull(guide.getPending());
    }
}
