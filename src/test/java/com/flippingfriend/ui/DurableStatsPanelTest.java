package com.flippingfriend.ui;

import com.flippingfriend.core.*;
import com.flippingfriend.model.Explainer;
import java.awt.Component;
import java.awt.Container;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.swing.JLabel;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import org.junit.Test;
import static org.junit.Assert.*;

public class DurableStatsPanelTest
{
    private List<String> text(Container container)
    {
        List<String> result = new ArrayList<>();
        for (Component child : container.getComponents())
        {
            if (child instanceof JLabel) result.add(((JLabel) child).getText());
            if (child instanceof JTextArea) result.add(((JTextArea) child).getText());
            if (child instanceof Container) result.addAll(text((Container) child));
        }
        return result;
    }
    private StatsPanel render(AccountLedgerSnapshot ledger) throws Exception
    {
        StatsPanel[] panel = new StatsPanel[1];
        SwingUtilities.invokeAndWait(() -> {
            panel[0] = new StatsPanel(new Explainer()); panel[0].updateLedger(ledger);
            panel[0].setSize(225, panel[0].getPreferredSize().height); panel[0].doLayout();
        });
        return panel[0];
    }

    @Test public void unavailableLedgerDoesNotFallBackToAnInventedZero() throws Exception
    {
        List<String> labels = text(render(null));
        assertTrue(labels.contains("Unavailable")); assertFalse(labels.contains("0 gp"));
    }

    @Test public void smallTaxRangesAndLossesRemainVisibleAtFullPrecision() throws Exception
    {
        AccountLedgerSnapshot ledger = new AccountLedgerSnapshot(AccountIdentity.fromProfileKey("p"), 1,
            1, 1, 1, 1, -2, -1, 1, 2, Collections.emptyList());
        List<String> labels = text(render(ledger));
        assertTrue(labels.contains("-2 to -1 gp")); assertTrue(labels.contains("Recorded tax: 1 to 2 gp"));
        assertTrue(labels.stream().anyMatch(label -> label.contains("GP/hour awaits session tracking")));
    }

    @Test public void oldUnpricedSalesAreExcludedAndIdentifiedAsUnresolved() throws Exception
    {
        AccountLedgerSnapshot ledger = new AccountLedgerSnapshot(AccountIdentity.fromProfileKey("p"), 1,
            1, 0, 0, 0, 0, 0, 0, 0, Collections.emptyList());
        List<String> labels = text(render(ledger));
        assertTrue(labels.contains("Unresolved")); assertTrue(labels.contains("Known recorded tax: Unresolved"));
        assertTrue(labels.contains("Unresolved sales: 1")); assertFalse(labels.contains("0 gp"));
    }

    @Test public void largeRangesWrapInsideTheSidebar() throws Exception
    {
        StatsPanel panel = render(new AccountLedgerSnapshot(AccountIdentity.fromProfileKey("p"), 1,
            1, 1, 1, 1, 100_000_001, 110_000_002, 1_000_001, 2_000_002, Collections.emptyList()));
        for (Component child : panel.getComponents()) if (child instanceof JTextArea)
        {
            JTextArea area = (JTextArea) child;
            assertTrue(area.getLineWrap()); assertTrue(area.getWidth() <= panel.getWidth());
            assertTrue("range and coverage must fit vertically", area.getHeight() >= area.getPreferredSize().height);
        }
    }
}
