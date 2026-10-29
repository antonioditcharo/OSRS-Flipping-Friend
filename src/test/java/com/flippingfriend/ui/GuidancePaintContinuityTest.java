package com.flippingfriend.ui;

import com.flippingfriend.*;
import com.flippingfriend.model.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.*;
import javax.swing.event.*;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.game.ItemManager;
import net.runelite.client.util.AsyncBufferedImage;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class GuidancePaintContinuityTest {
    @Test public void hiddenInvestigationViewsNeverUseTheForegroundRefreshBudget()throws Exception {
        var f=new CompactPanelContractTest();f.setup();clearInvocations(f.engine);
        for(int i=0;i<12;i++)f.refresh();verify(f.engine,never()).tradeRecovery();verify(f.engine,never()).saleMaintenanceDiagnostics();
        SwingUtilities.invokeAndWait(()->f.panel.detailsComponent("Recommendation"));verify(f.engine,atLeastOnce()).saleMaintenanceDiagnostics();
        verify(f.engine,never()).tradeRecovery();
        SwingUtilities.invokeAndWait(()->f.panel.detailsComponent("Trade recovery"));verify(f.engine,atLeastOnce()).tradeRecovery();
        clearInvocations(f.engine);for(int i=0;i<4;i++)f.refresh();
        verify(f.engine,never()).tradeRecovery();verify(f.engine,never()).saleMaintenanceDiagnostics();
    }
    @Test public void unchangedOrderDoesNotRewriteTextButAChangedPriceAndForecastRender()throws Exception {
        var f=new CompactPanelContractTest();f.setup();var changes=new AtomicInteger();
        SwingUtilities.invokeAndWait(()->{
            var card=CompactPanelContractTest.tree(f.panel).stream().filter(c->"suggested-action".equals(c.getName())).findFirst().orElseThrow();
            for(var c:CompactPanelContractTest.tree(card))if(c instanceof JTextArea)((JTextArea)c).getDocument().addDocumentListener(new DocumentListener(){
                public void insertUpdate(DocumentEvent e){changes.incrementAndGet();}public void removeUpdate(DocumentEvent e){changes.incrementAndGet();}
                public void changedUpdate(DocumentEvent e){changes.incrementAndGet();}
            });
        });
        for(int i=0;i<10;i++)f.refresh();assertEquals("Polling unchanged guidance must not relayout its text",0,changes.get());
        when(f.guide.getSuggestion()).thenReturn(Suggestion.builder(SuggestionType.BUY).item(561,"Nature rune").quantity(10).price(101)
            .expectedProfit(500).expectedMinutes(8).build());f.refresh();assertTrue(changes.get()>0);
        assertTrue(CompactPanelContractTest.tree(f.panel).stream().filter(c->c instanceof JTextArea)
            .map(c->((JTextArea)c).getText()).anyMatch(s->s.contains("101 gp each")));
        int after=changes.get();f.refresh();assertEquals(after,changes.get());
    }
    @Test public void delayedIconForAnOlderTradeCannotReplaceTheCurrentIcon()throws Exception {
        var items=mock(ItemManager.class);var callbacks=new ArrayList<Runnable>();
        var first=mock(AsyncBufferedImage.class,org.mockito.AdditionalAnswers.delegatesTo(new AsyncBufferedImage(mock(ClientThread.class),36,32,java.awt.image.BufferedImage.TYPE_INT_ARGB)));
        var second=mock(AsyncBufferedImage.class,org.mockito.AdditionalAnswers.delegatesTo(new AsyncBufferedImage(mock(ClientThread.class),36,32,java.awt.image.BufferedImage.TYPE_INT_ARGB)));
        doAnswer(c->{callbacks.add(c.getArgument(0));return null;}).when(first).onLoaded(any());
        doAnswer(c->{callbacks.add(c.getArgument(0));return null;}).when(second).onLoaded(any());
        when(items.getImage(561)).thenReturn(first);when(items.getImage(562)).thenReturn(second);
        CompactSuggestionPanel[] view=new CompactSuggestionPanel[1];
        SwingUtilities.invokeAndWait(()->{
            view[0]=new CompactSuggestionPanel(items,new Explainer());
            for(int i=0;i<10;i++)view[0].update(Suggestion.builder(SuggestionType.SELL).item(561,"First").quantity(1).price(100).build(),null);
            view[0].update(Suggestion.builder(SuggestionType.SELL).item(562,"Second").quantity(1).price(200).build(),null);
        });
        verify(items,times(1)).getImage(561);verify(first,times(1)).onLoaded(any());assertEquals(2,callbacks.size());
        callbacks.get(0).run();SwingUtilities.invokeAndWait(()->{});
        JLabel icon=(JLabel)CompactPanelContractTest.tree(view[0]).stream().filter(c->c instanceof JLabel).findFirst().orElseThrow();
        assertSame(second,((ImageIcon)icon.getIcon()).getImage());
        callbacks.get(1).run();SwingUtilities.invokeAndWait(()->{});assertSame(second,((ImageIcon)icon.getIcon()).getImage());
    }
    @Test public void refreshBurstsRenderLatestGuidanceInOneQueuedPaintAndKeepControlSelection()throws Exception {
        var f=new CompactPanelContractTest();f.setup();var changes=new AtomicInteger();var icons=new AtomicInteger();
        SwingUtilities.invokeAndWait(()->{
            f.button("cadence-5m").addChangeListener(e->changes.incrementAndGet());
            f.button("pause-action").addPropertyChangeListener("icon",e->icons.incrementAndGet());
        });
        for(int i=0;i<5;i++)f.refresh();assertEquals("Selection must not be cleared and reapplied on each poll",0,changes.get());
        assertEquals("Do not decode PNG resources and replace the same icon on every poll",0,icons.get());
        clearInvocations(f.monitor);
        SwingUtilities.invokeAndWait(()->{
            Thread publisher=new Thread(()->{for(int i=0;i<1000;i++)f.panel.refresh();});publisher.start();
            try{publisher.join();}catch(InterruptedException failure){throw new AssertionError(failure);}
            when(f.guide.getSuggestion()).thenReturn(Suggestion.builder(SuggestionType.MODIFY_SELL).item(562,"Latest maintenance").quantity(2).price(222).build());
        });
        SwingUtilities.invokeAndWait(()->{});SwingUtilities.invokeAndWait(()->{});
        verify(f.monitor,times(2)).getState(); // one guidance read and one deferred performance read
        assertTrue(CompactPanelContractTest.tree(f.panel).stream().filter(c->c instanceof JTextArea)
            .map(c->((JTextArea)c).getText()).anyMatch(s->s.equals("Latest maintenance")));
        when(f.config.checkInterval()).thenReturn(CheckInterval.EIGHT_HOURS);f.refresh();
        assertTrue(f.button("cadence-8h").isSelected());assertFalse(f.button("cadence-5m").isSelected());
    }
}
