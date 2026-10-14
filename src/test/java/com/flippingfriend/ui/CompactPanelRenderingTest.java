package com.flippingfriend.ui;

import com.flippingfriend.CheckInterval;
import com.flippingfriend.core.*;
import com.flippingfriend.model.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Collections;
import javax.imageio.ImageIO;
import javax.swing.*;
import net.runelite.client.util.AsyncBufferedImage;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/** Paints production Swing components with RuneLite's own look and feel; no game client or input. */
public class CompactPanelRenderingTest {
    @Test public void renderCompactStatesAndOpenedDetailsUsingNativeComponents() throws Exception {
        LookAndFeel previous = UIManager.getLookAndFeel();
        try {
            SwingUtilities.invokeAndWait(() -> assertTrue(net.runelite.client.ui.laf.RuneLiteLAF.setup()));
            CompactPanelContractTest f = new CompactPanelContractTest(); f.setup();
            AsyncBufferedImage sprite = mock(AsyncBufferedImage.class);
            File fixture = new File(".gradle/reference/bracelet-icon.png");
            if (fixture.exists()) {
                BufferedImage image = ImageIO.read(fixture);
                doAnswer(call -> { ((JLabel)call.getArgument(0)).setIcon(new ImageIcon(image)); return null; }).when(sprite).addTo(any(JLabel.class));
            }
            when(f.items.getImage(19544)).thenReturn(sprite);
            when(f.config.checkInterval()).thenReturn(CheckInterval.THIRTY_MINUTES);
            when(f.companion.lastLedger()).thenReturn(new AccountLedgerSnapshot(AccountIdentity.fromProfileKey("synthetic"),
                java.time.Instant.now().getEpochSecond(),3,3,0,3,2127000,2127000,100,100,Collections.emptyList()));
            Suggestion buy = Suggestion.builder(SuggestionType.BUY).item(19544,"Tormented bracelet").quantity(5).price(24276525)
                .targetSellPrice(25000000).expectedProfit(1110000).expectedMinutes(60).confidence(.72)
                .headline("Buy Tormented bracelet").detail("A fresh recommendation for the selected checking interval.").build();
            when(f.guide.getSuggestion()).thenReturn(buy); f.refresh();
            capture(f.panel,239,499,"compact-buy.png");
            verify(sprite,atLeastOnce()).addTo(any(JLabel.class));
            final JComponent[] opened = new JComponent[1];
            f.panel.setWindowPresenter((name,view)->opened[0]=view);
            for(String view:new String[]{"Recommendation","Holdings","Performance","Settings"}) {
                SwingUtilities.invokeAndWait(()->f.panel.detailsComponent(view));
                SwingUtilities.invokeAndWait(()->{
                    for(Component entry:f.panel.detailsMenu().getComponents()) if(entry instanceof JMenuItem && view.equals(((JMenuItem)entry).getText())) ((JMenuItem)entry).doClick();
                });
                capture(opened[0],730,540,"details-"+view.toLowerCase()+".png");
            }
            when(f.guide.getSuggestion()).thenReturn(Suggestion.builder(SuggestionType.SELL).item(561,"Nature rune").quantity(10).price(111).profitKnown(false).headline("Sell selected item").build());
            f.refresh(); capture(f.panel,239,499,"compact-manual-sell.png");
            when(f.guide.getSuggestion()).thenReturn(Suggestion.waiting("Recommendations paused","Resume using the play button."));
            when(f.guide.isPaused()).thenReturn(true); f.refresh(); capture(f.panel,239,499,"compact-paused.png");
            when(f.guide.isPaused()).thenReturn(false);
            when(f.guide.getSuggestion()).thenReturn(Suggestion.waiting("Waiting for fresh market prices","No current recommendation."));
            when(f.companion.lastLedger()).thenReturn(null); f.refresh(); capture(f.panel,239,499,"compact-waiting.png");
            when(f.guide.getSuggestion()).thenReturn(Suggestion.builder(SuggestionType.MODIFY_SELL).item(19544,"Very long synthetic item name that must wrap without hiding the price").quantity(999999).price(2147483647).build());
            f.refresh(); capture(f.panel,225,499,"compact-long.png");
            SwingUtilities.invokeAndWait(f.panel::closeWindows);
        } finally { SwingUtilities.invokeAndWait(()->{try { UIManager.setLookAndFeel(previous); } catch (UnsupportedLookAndFeelException e) { throw new AssertionError(e); }}); }
    }
    private boolean visibleTo(Component c, Component root) {
        while(c!=null) { if(!c.isVisible()) return false; if(c==root) return true; c=c.getParent(); } return false;
    }
    private void capture(JComponent component,int width,int height,String name) throws Exception {
        assertNotNull(component);
        SwingUtilities.invokeAndWait(()->{component.setSize(width,height); for(int i=0;i<4;i++) CompactPanelContractTest.layout(component);});
        SwingUtilities.invokeAndWait(()->{
            for(Component c:CompactPanelContractTest.tree(component)) if(c instanceof JTextArea && visibleTo(c,component) && c.getWidth()>0) {
                JTextArea actual=(JTextArea)c; if(actual.getText().isEmpty()) continue;
                JTextArea measure=new JTextArea(actual.getText()); measure.setLineWrap(true); measure.setWrapStyleWord(true);
                measure.setFont(actual.getFont()); measure.setMargin(actual.getMargin()); measure.setSize(actual.getWidth(),Short.MAX_VALUE);
                assertTrue(name+": clipped text: "+actual.getText(),actual.getHeight()>=measure.getPreferredSize().height);
            }
        });
        BufferedImage image=new BufferedImage(width,height,BufferedImage.TYPE_INT_RGB);
        SwingUtilities.invokeAndWait(()->{Graphics2D g=image.createGraphics(); component.printAll(g); g.dispose();});
        File dir=new File("build/ui"); assertTrue(dir.isDirectory()||dir.mkdirs()); ImageIO.write(image,"png",new File(dir,name));
    }
}
