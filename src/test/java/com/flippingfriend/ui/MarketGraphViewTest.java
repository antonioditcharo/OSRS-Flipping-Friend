package com.flippingfriend.ui;

import com.flippingfriend.data.*;
import com.flippingfriend.model.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.time.Instant;
import java.util.*;
import java.util.List;
import javax.swing.*;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class MarketGraphViewTest {
    final long now=1800000000;
    List<Candle> bars(int step,int count) {
        var rows=new ArrayList<Candle>();for(int i=0;i<count;i++)rows.add(new Candle(now-(long)(count-i)*step,4000000+i*20,3900000+i*20,10,12));return rows;
    }
    MarketDataService market() {
        var market=mock(MarketDataService.class);
        when(market.getSnapshot()).thenReturn(new MarketSnapshot(Map.of(30088,new ItemMetadata(30088,"Huasca seed",true,200,0),
            4151,new ItemMetadata(4151,"Abyssal whip",true,70,0)),Map.of(30088,new LatestPrice(4010000,now-30,3910000,now-60),
            4151,new LatestPrice(2000000,now-30,1900000,now-60)),Map.of(),Map.of(),Instant.ofEpochSecond(now)));
        var five=bars(300,365);var hour=bars(3600,365);var six=bars(21600,365);
        when(market.getGraphSeries(anyInt(),anyString())).thenAnswer(call->{switch((String)call.getArgument(1)){case "5m":return five;case "6h":return six;default:return hour;}});
        return market;
    }
    AbstractButton button(Container parent,String name) {
        for(Component child:parent.getComponents()) {if(child instanceof AbstractButton && name.equals(child.getName()))return (AbstractButton)child;
            if(child instanceof Container){var found=button((Container)child,name);if(found!=null)return found;}}
        return null;
    }
    static String text(Component component) {
        String value=component instanceof JLabel?((JLabel)component).getText():component instanceof JTextArea?((JTextArea)component).getText():"";
        if(component instanceof Container)for(Component child:((Container)component).getComponents())value+="\n"+text(child);return value;
    }
    @Test public void selectedItemDoesNotDriftToAnotherSuggestionAndSwitchButtonIsExplicit()throws Exception {
        SwingUtilities.invokeAndWait(()->{
            var view=new MarketGraphView(market(),()->now);view.showItem(30088);
            view.update(Suggestion.builder(SuggestionType.BUY).item(4151,"Abyssal whip").price(1900001).quantity(1).build());
            view.refresh();assertEquals(30088,view.item());assertTrue(text(view).contains("Huasca seed"));
            view.suggested.doClick();assertEquals(4151,view.item());assertTrue(text(view).contains("Abyssal whip"));view.dispose();
        });
    }
    @Test public void searchRangesZoomResetAndProjectionToggleAreWorkingControls()throws Exception {
        SwingUtilities.invokeAndWait(()->{
            var market=market();var view=new MarketGraphView(market,()->now);view.search.setText("Huasca");view.search.postActionEvent();assertEquals(30088,view.item());
            view.search.setText("not a known item");view.search.postActionEvent();view.refresh();assertEquals(30088,view.item());assertTrue(text(view).contains("No item matches"));
            for(String label:new String[]{"Month","Week","Day","8h"})assertNotNull(button(view,"graph-range-"+label));
            button(view,"graph-range-Month").doClick();verify(market,atLeastOnce()).getGraphSeries(30088,"6h");
            double[] before=view.chart.window();button(view,"graph-zoom-in").doClick();double[] zoomed=view.chart.window();
            assertTrue(zoomed[1]-zoomed[0]<before[1]-before[0]);button(view,"graph-reset").doClick();assertArrayEquals(before,view.chart.window(),.1);
            view.projections.doClick();assertFalse(view.projections.isSelected());view.dispose();
        });
    }
    @Test public void missingHistoryAndQuotesStayUnavailableWithoutBorrowingAnotherItemsStats()throws Exception {
        SwingUtilities.invokeAndWait(()->{
            var market=mock(MarketDataService.class);when(market.getSnapshot()).thenReturn(MarketSnapshot.empty());
            when(market.getGraphSeries(anyInt(),anyString())).thenReturn(List.of());
            var view=new MarketGraphView(market,()->now);view.showItem(561);assertTrue(text(view).contains("Unavailable"));
            assertTrue(text(view).contains("Loading completed market history"));view.dispose();
        });
    }
    @Test public void chartTooltipUsesRealTimestampsAndIndependentSides()throws Exception {
        SwingUtilities.invokeAndWait(()->{
            var canvas=new MarketGraphCanvas();canvas.setSize(1000,600);canvas.setData(bars(300,12),300,null,0,0,now-3600,now,false);
            String tip=canvas.getToolTipText(new MouseEvent(canvas,MouseEvent.MOUSE_MOVED,0,0,980,100,0,false));
            assertTrue(tip,tip.contains("completed bucket averages"));assertTrue(tip.contains("Low:"));assertTrue(tip.contains("High:"));assertTrue(tip.contains("UTC"));
            double span=canvas.window()[1]-canvas.window()[0];canvas.getActionMap().get("EQUALS").actionPerformed(null);
            assertTrue(canvas.window()[1]-canvas.window()[0]<span);canvas.getActionMap().get("HOME").actionPerformed(null);
            assertEquals(span,canvas.window()[1]-canvas.window()[0],.1);
        });
    }
}
