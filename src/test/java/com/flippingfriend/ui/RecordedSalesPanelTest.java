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
            panel.updateLedger(ledger);var table=find(panel,JTable.class);assertEquals("Unknown",table.getValueAt(0,6));
            table.setRowSelectionInterval(0,0);panel.updateLedger(ledger);assertEquals(0,table.getSelectedRow());
            assertTrue(find(panel,JTextArea.class).getText().contains("source"));find(panel,JButton.class).doClick();assertEquals(561,graph.get());
            panel.updateLedger(null);assertEquals(0,table.getRowCount());assertEquals("",find(panel,JTextArea.class).getText());
        });
    }
}
