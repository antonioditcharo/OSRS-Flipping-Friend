package com.flippingfriend.ui;

import com.flippingfriend.model.*;
import java.awt.image.BufferedImage;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.*;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/** Behavioral refusal/continuity checks. Headless painting never qualifies native latency. */
public class GuidanceNativeTimingTest {
    @Test public void timingEmitsExactSaleProvenanceOnlyInItsMatchingAccount() {
        String account=com.flippingfriend.core.AccountIdentity.fromProfileKey("one");
        var action=Suggestion.builder(SuggestionType.SELL).item(561,"Nature rune").quantity(3).price(120).build();
        action.recordSaleTimingSource(account,"original-acquisition");
        var timing=new GuidanceTiming("test:sale","SELECTED_SALE");timing.context(account,1,1);timing.delivery("VISIBLE",action);
        assertEquals(3,timing.diagnosticSnapshot().get("schemaVersion"));
        assertSame(action.getSaleTimingSource(),timing.diagnosticSnapshot().get("decisionProvenance"));
        var foreign=new GuidanceTiming("test:foreign","MAIN");
        foreign.context(com.flippingfriend.core.AccountIdentity.fromProfileKey("two"),1,1);foreign.delivery("VISIBLE",action);
        assertFalse(foreign.diagnosticSnapshot().containsKey("decisionProvenance"));
        var collect=Suggestion.builder(SuggestionType.COLLECT).item(561,"Nature rune").quantity(3).slot(1).build();
        collect.recordSaleTimingSource(account,"not-sale-authority");assertNull(collect.getSaleTimingSource());
    }
    @Test public void bufferedHiddenPanelIsNotANativePaintSampleAndHasOneTerminalOutcome() throws Exception {
        var timing=new GuidanceTiming("test:hidden","MAIN");
        var action=Suggestion.builder(SuggestionType.SELL).item(1,"Selected").quantity(2).price(55).build();
        timing.delivery("VISIBLE",action);
        SwingUtilities.invokeAndWait(()->{
            var card=new CompactSuggestionPanel(mock(net.runelite.client.game.ItemManager.class),new Explainer());
            card.update(action,null);card.measurePaint(timing,action);card.setSize(240,100);
            var graphics=new BufferedImage(240,100,BufferedImage.TYPE_INT_ARGB).createGraphics();
            try {graphics.setClip(0,0,240,100);card.paint(graphics);}finally{graphics.dispose();}
        });
        assertEquals("HIDDEN",timing.diagnosticSnapshot().get("paintOutcome"));
        timing.paint("PAINTED");timing.delivery("SUPERSEDED",action);
        assertEquals("HIDDEN",timing.diagnosticSnapshot().get("paintOutcome"));
        assertEquals("VISIBLE",timing.diagnosticSnapshot().get("deliveryOutcome"));
    }
    @Test public void queuedPresentationIsSupersededBeforeDrawingAnotherAction() {
        var old=new GuidanceTiming("test:old","MAIN");var next=new GuidanceTiming("test:new","SELECTED_SALE");
        var paint=new GuidancePaint();var action=Suggestion.idle();paint.request(old,action);paint.request(next,action);
        assertEquals("SUPERSEDED_BEFORE_PAINT",old.diagnosticSnapshot().get("paintOutcome"));
        paint.validate(new JPanel(),Suggestion.waiting("Changed","State changed"));
        assertEquals("SUPERSEDED_BEFORE_PAINT",next.diagnosticSnapshot().get("paintOutcome"));
    }
    @Test public void unchangedMeasuredRefreshDoesNotResetPopulatedMetricsOrProfitText() throws Exception {
        var f=new CompactPanelContractTest();f.setup();var fixture=new SidebarPerformanceTest().fixture(25);
        when(f.companion.performance()).thenReturn(fixture);f.refresh();var changes=new AtomicInteger();
        SwingUtilities.invokeAndWait(()->{
            for(var view:CompactPanelContractTest.tree(f.panel))
                if(view instanceof JLabel && view.getName()!=null && view.getName().startsWith("metric-")
                    && !view.getName().equals("metric-session-time") && !view.getName().equals("metric-hourly-profit"))
                    view.addPropertyChangeListener("text",event->changes.incrementAndGet());
            var profit=(JTextArea)CompactPanelContractTest.tree(f.panel).stream().filter(c->"measured-profit".equals(c.getName())).findFirst().orElseThrow();
            profit.addPropertyChangeListener("text",event->changes.incrementAndGet());
        });
        for(int i=0;i<5;i++)f.refresh();assertEquals(0,changes.get());
    }
    @Test public void sidebarSalesUseOneRoundedEstimateAndRetainUnknownCosts() throws Exception {
        var f=new CompactPanelContractTest();f.setup();long now=java.time.Instant.now().getEpochSecond();
        var p=new com.flippingfriend.core.PerformanceSnapshot(com.flippingfriend.core.AccountIdentity.fromProfileKey("test"),"ALL",null,now,0,0,2,1,1,10,-11,-10,
            java.util.List.of(new com.flippingfriend.core.PerformanceSnapshot.Sale("a",1,"Loss",1,now,1,-11L,-10L,true),
                new com.flippingfriend.core.PerformanceSnapshot.Sale("b",2,"Unknown",1,now,1,null,null,true)));
        when(f.companion.performance()).thenReturn(p);f.refresh();String text=new SidebarPerformanceTest().text(f.panel);
        assertTrue(text.contains("Est. -11 gp"));assertFalse(text.contains("…"));assertTrue(text.contains("Unknown"));
        assertEquals(-11,p.profitLower);assertEquals(-10,p.profitUpper);
    }
}
