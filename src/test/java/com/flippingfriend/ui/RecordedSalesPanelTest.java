package com.flippingfriend.ui;

import com.flippingfriend.core.*;
import java.awt.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class RecordedSalesPanelTest {
    <T> T find(Container root,Class<T> type) {
        for(Component c:root.getComponents()){if(type.isInstance(c) && !(c instanceof javax.swing.plaf.basic.BasicArrowButton))return type.cast(c);if(c instanceof Container){T child=find((Container)c,type);if(child!=null)return child;}}return null;
    }
    @Test public void showsUnknownSaleProfitAndKeepsSelectionAcrossUnchangedPolls()throws Exception {
        SwingUtilities.invokeAndWait(()->{
            AtomicInteger graph=new AtomicInteger();var panel=new RecordedSalesPanel(graph::set);
            var sale=new RecordedSale(1,100,561,"Nature rune",1,0,"source","offer",120L,2L,2L,null,null,SaleAccounting.CURRENT_POLICY,"COST_BASIS_UNRESOLVED");
            var ledger=new AccountLedgerSnapshot(AccountIdentity.fromProfileKey("a"),100,1,0,0,1,0,0,2,2,List.of()).withRecentSales(List.of(sale));
            panel.updateLedger(ledger);var table=find(panel,JTable.class);assertEquals("Cost unmatched",table.getValueAt(0,6));
            assertEquals(0L,table.getValueAt(0,4));
            table.setRowSelectionInterval(0,0);panel.updateLedger(ledger);assertEquals(0,table.getSelectedRow());
            assertTrue(find(panel,JTextArea.class).getText().contains("source"));find(panel,JButton.class).doClick();assertEquals(561,graph.get());
            panel.updateLedger(null);assertEquals(0,table.getRowCount());assertEquals("",find(panel,JTextArea.class).getText());
        });
    }
    @Test public void selectingAnOfferClearsFiltersAndExplainsTheRecordedMismatchWithoutInventingProfit()throws Exception {
        SwingUtilities.invokeAndWait(()->{
            var panel=new RecordedSalesPanel(item->{});
            var sale=new RecordedSale(1,100,561,"Nature rune",2,0,"source","target",240L,4L,4L,null,null,
                SaleAccounting.CURRENT_POLICY,"COST_BASIS_UNRESOLVED; EXACT_UNDER_RECORDED_RULE; disposal has no durable position");
            var ledger=new AccountLedgerSnapshot(AccountIdentity.fromProfileKey("a"),100,1,0,0,1,0,0,4,4,List.of()).withRecentSales(List.of(sale));
            panel.updateLedger(ledger);find(panel,JTextField.class).setText("no match");assertEquals(0,find(panel,JTable.class).getRowCount());
            panel.selectOffer("target");var table=find(panel,JTable.class);assertEquals(0,table.getSelectedRow());
            String explanation=find(panel,JTextArea.class).getText();assertTrue(explanation.contains("does not mean the purchase record was deleted"));
            assertTrue(explanation.contains("source"));assertEquals(240L,table.getValueAt(0,3));assertEquals("4",table.getValueAt(0,5));
            assertEquals("Cost unmatched",table.getValueAt(0,6));assertEquals(0,ledger.getProfitLower());
            panel.selectOffer("outside-the-cap");assertTrue(find(panel,JTextArea.class).getText().contains("outside the most recent"));
        });
    }
}
