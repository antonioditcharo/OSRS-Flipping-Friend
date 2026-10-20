package com.flippingfriend.ui;

import com.flippingfriend.core.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.*;
import javax.imageio.ImageIO;
import javax.swing.*;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/** Production native panel and controls, with explicit synthetic display measurements. */
public class SidebarPerformanceTest {
    String text(Component c){return CompactPanelContractTest.tree(c).stream().map(v->v instanceof JLabel?((JLabel)v).getText():v instanceof JTextArea?((JTextArea)v).getText():"").collect(java.util.stream.Collectors.joining("\n"));}
    PerformanceSnapshot fixture(int count) {
        long now=java.time.Instant.now().getEpochSecond();java.util.List<PerformanceSnapshot.Sale> sales=new ArrayList<>();
        for(int i=0;i<count;i++)sales.add(new PerformanceSnapshot.Sale("offer-"+i,561,"Nature rune",1926,now-i,2,77000L,77000L,true));
        var p=new PerformanceSnapshot(AccountIdentity.fromProfileKey("synthetic"),"SESSION","measurement",now,now-13302,0,
            count*2,0,count,54327323,391700,391700,sales);
        p.unrealizedProfit=0L;p.portfolioValue=144812000L;p.unpricedCost=0L;return p;
    }
    @Test public void scopesResetPaginationAndAccountExitUseRealControls()throws Exception {
        var f=new CompactPanelContractTest();f.setup();when(f.companion.performance()).thenReturn(fixture(25));f.refresh();
        assertTrue(text(f.panel).contains("Flips made:"));assertTrue(text(f.panel).contains("0.721%"));assertTrue(text(f.panel).contains("Page 1 of 3"));
        SwingUtilities.invokeAndWait(()->f.button("sales-next").doClick());assertTrue(text(f.panel).contains("Page 2 of 3"));
        SwingUtilities.invokeAndWait(()->f.button("reset-session").doClick());verify(f.companion).resetPerformanceSession();
        JComboBox<?> scope=CompactPanelContractTest.tree(f.panel).stream().filter(c->"performance-scope".equals(c.getName())).map(c->(JComboBox<?>)c).findFirst().orElseThrow();
        SwingUtilities.invokeAndWait(()->scope.setSelectedIndex(3));verify(f.companion).setPerformanceScope("ALL");
        when(f.monitor.getState()).thenReturn(com.flippingfriend.session.AccountState.loggedOut());f.refresh();
        assertFalse(text(f.panel).contains("391,700"));assertFalse(f.button("reset-session").isEnabled());assertTrue(text(f.panel).contains("Unavailable"));
    }
    @Test public void unknownSalesAndUnpricedStockStayVisible()throws Exception {
        var f=new CompactPanelContractTest();f.setup();long now=java.time.Instant.now().getEpochSecond();
        var p=new PerformanceSnapshot(AccountIdentity.fromProfileKey("synthetic"),"SESSION","measurement",now,now-100,0,
            1,1,0,0,0,0,java.util.List.of(new PerformanceSnapshot.Sale("unpriced",561,"Unknown-cost item",1,now,1,null,null,true)));
        p.unrealizedProfit=-100L;p.portfolioValue=1000L;p.unpricedCost=500L;
        when(f.companion.performance()).thenReturn(p);f.refresh();
        assertTrue(text(f.panel).contains("Unresolved"));assertTrue(text(f.panel).contains("-100 gp*"));assertTrue(text(f.panel).contains("Unknown"));
        var value=CompactPanelContractTest.tree(f.panel).stream().filter(c->"metric-unrealized-profit".equals(c.getName())).map(c->(JLabel)c).findFirst().orElseThrow();
        assertTrue(value.getToolTipText().contains("Priced portion only"));assertTrue(value.getToolTipText().contains("not lost"));
    }
    @Test public void unmatchedSaleOpensItsAccountingEvidenceAndRetainsKnownProceedsAndTax()throws Exception {
        var f=new CompactPanelContractTest();f.setup();long now=java.time.Instant.now().getEpochSecond();
        String reason="COST_BASIS_UNRESOLVED; EXACT_UNDER_RECORDED_RULE; disposal has no durable position";
        var sale=new PerformanceSnapshot.Sale("target",561,"Nature rune",2,now,1,null,null,true,240L,0L,4L,4L,java.util.List.of(reason));
        var p=new PerformanceSnapshot(AccountIdentity.fromProfileKey("synthetic"),"ALL",null,now,0,0,1,1,0,0,0,0,java.util.List.of(sale));
        var row=new RecordedSale(1,now,561,"Nature rune",2,0,"exact-source","target",240L,4L,4L,null,null,SaleAccounting.CURRENT_POLICY,reason);
        when(f.companion.performance()).thenReturn(p);
        when(f.companion.lastLedger()).thenReturn(new AccountLedgerSnapshot(p.accountId,now,1,0,0,1,0,0,4,4,java.util.List.of()).withRecentSales(java.util.List.of(row)));
        java.util.List<String> opened=new ArrayList<>();java.util.List<Component> windows=new ArrayList<>();
        f.panel.setWindowPresenter((name,content)->{opened.add(name);windows.add(content);});f.refresh();
        JLabel result=CompactPanelContractTest.tree(f.panel).stream().filter(c->c instanceof JLabel && "Cost unmatched".equals(((JLabel)c).getText())).map(c->(JLabel)c).findFirst().orElseThrow();
        assertTrue(result.getToolTipText().contains("Gross: 240 GP"));assertTrue(result.getToolTipText().contains("Tax: 4 gp"));
        assertTrue(result.getToolTipText().contains("does not mean the purchase record was deleted"));
        SwingUtilities.invokeAndWait(()->{for(var listener:result.getMouseListeners())listener.mouseClicked(new java.awt.event.MouseEvent(result,java.awt.event.MouseEvent.MOUSE_CLICKED,0,0,1,1,1,false));});
        assertEquals(java.util.List.of("Recorded sales"),opened);
        JTable table=CompactPanelContractTest.tree(windows.get(0)).stream().filter(c->c instanceof JTable).map(c->(JTable)c).filter(t->t.getColumnCount()==7).findFirst().orElseThrow();
        assertEquals(0,table.getSelectedRow());assertEquals("Cost unmatched",table.getValueAt(0,6));
        assertTrue(CompactPanelContractTest.tree(windows.get(0)).stream().filter(c->c instanceof JTextArea).anyMatch(c->((JTextArea)c).getText().contains("exact-source")));
        verify(f.market,never()).getGraphSeries(eq(561),anyString());
    }
    @Test public void archivedSalesLeaveTheActionListButNeverDisappearFromCoverageOrBecomeProfit()throws Exception {
        var f=new CompactPanelContractTest();f.setup();long now=java.time.Instant.now().getEpochSecond();
        var archived=new PerformanceSnapshot.Sale("archived",562,"Old archived sale",1,now,1,null,null,true);archived.archived=true;
        var fresh=new PerformanceSnapshot.Sale("new",561,"New unresolved sale",1,now,1,null,null,true);
        var p=new PerformanceSnapshot(AccountIdentity.fromProfileKey("synthetic"),"ALL",null,now,0,0,2,2,0,0,0,0,java.util.List.of(archived,fresh));p.archivedUnresolvedSales=1;
        when(f.companion.performance()).thenReturn(p);f.refresh();String text=text(f.panel);
        assertFalse(text.contains("Old archived sale"));assertTrue(text.contains("New unresolved sale"));
        assertTrue(text.contains("2 unresolved"));assertTrue(text.contains("1 archived (kept in coverage)"));assertTrue(text.contains("Unresolved"));assertTrue(p.isValid());assertEquals(0,p.profitLower);
    }
    @Test public void renderNativeExpandedPanelAndShortViewport()throws Exception {
        var old=UIManager.getLookAndFeel();
        try {
            SwingUtilities.invokeAndWait(()->assertTrue(net.runelite.client.ui.laf.RuneLiteLAF.setup()));
            var f=new CompactPanelContractTest();f.setup();when(f.companion.performance()).thenReturn(fixture(5));
            when(f.config.riskProfile()).thenReturn(com.flippingfriend.RiskProfile.HIGH);
            File sprite=new File(".gradle/reference/nature-rune.png");
            if(sprite.isFile()) {
                var image=ImageIO.read(sprite);var icon=mock(net.runelite.client.util.AsyncBufferedImage.class);
                doAnswer(c->{((JLabel)c.getArgument(0)).setIcon(new ImageIcon(image));return null;}).when(icon).addTo(any(JLabel.class));
                when(f.items.getImage(561)).thenReturn(icon);
            }
            when(f.guide.getSuggestion()).thenReturn(com.flippingfriend.model.Suggestion.builder(com.flippingfriend.model.SuggestionType.BUY)
                .item(561,"Nature rune").quantity(1000).price(100).expectedProfit(48800).expectedMinutes(10).build());f.refresh();
            for(int height:new int[]{1003,1026,499}) {
                SwingUtilities.invokeAndWait(()->{f.panel.setSize(239,height);for(int i=0;i<5;i++)CompactPanelContractTest.layout(f.panel);});
                var image=new BufferedImage(239,height,BufferedImage.TYPE_INT_RGB);
                SwingUtilities.invokeAndWait(()->{var g=image.createGraphics();f.panel.printAll(g);g.dispose();});
                var dir=new File("build/ui");assertTrue(dir.isDirectory()||dir.mkdirs());ImageIO.write(image,"png",new File(dir,"sidebar-parity-"+height+".png"));
                for(Component c:CompactPanelContractTest.tree(f.panel))if(c instanceof JLabel && c.isVisible() && ((JLabel)c).getText()!=null && !((JLabel)c).getText().isEmpty())
                    assertTrue(((JLabel)c).getText()+" collapsed",c.getHeight()>0);
            }
        }finally{SwingUtilities.invokeAndWait(()->{try{UIManager.setLookAndFeel(old);}catch(Exception e){throw new AssertionError(e);}});}
    }
}
