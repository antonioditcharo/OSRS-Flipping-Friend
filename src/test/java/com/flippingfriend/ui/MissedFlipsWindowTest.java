package com.flippingfriend.ui;
import com.flippingfriend.core.*;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
public class MissedFlipsWindowTest {
    @Test public void archiveViewsKeepCurrentGapsAndRetainedPurchasesVisibleAndRestoreTheExactSelectedSource()throws Exception {
        var companion=mock(com.flippingfriend.companion.CompanionClient.class);String account=AccountIdentity.fromProfileKey("review-ui");
        var old=new MissedFlipEvidence("old","old-offer",561,"Old unmatched sale","DISPOSE",1,2,null,100L,2L,2L,null,null,"Unknown cost");
        var keep=new MissedFlipEvidence("keep","keep-offer",562,"Retained purchase evidence","DISPOSE",1,3,null,100L,2L,2L,null,null,"Unknown cost");
        var eligible=new MissedFlipEvidence("eligible","closed",563,"Closed unmatched sale","DISPOSE",1,4,null,100L,2L,2L,null,null,"Unknown cost");
        var gap=new MissedFlipEvidence("gap",null,0,null,"RECONCILE",0,5,null,null,null,null,null,null,"Unassigned gap");
        var ledger=new AccountLedgerSnapshot(account,100,3,0,0,3,0,0,6,6,List.of()).withTradeEvidence(List.of(),List.of(gap,eligible,keep,old));
        var review=new TradeReviewSnapshot(account,100,10,java.util.Map.of("old",TradeReviewSnapshot.ARCHIVED,"keep",TradeReviewSnapshot.RETAINED,"eligible",TradeReviewSnapshot.ELIGIBLE),java.util.Set.of("old"));
        when(companion.historyLedger()).thenReturn(ledger);when(companion.tradeReview()).thenReturn(review);
        var window=new MissedFlipsWindow(companion);javax.swing.JComponent[] view=new javax.swing.JComponent[1];window.setPresenter((n,c)->view[0]=c);
        javax.swing.SwingUtilities.invokeAndWait(()->{
            window.open(null);var tree=CompactPanelContractTest.tree(view[0]);
            var table=(javax.swing.JTable)tree.stream().filter(c->c instanceof javax.swing.JTable).findFirst().orElseThrow();
            var select=(javax.swing.JComboBox<?>)tree.stream().filter(c->"trade-review-view".equals(c.getName())).findFirst().orElseThrow();
            var archive=(javax.swing.JButton)tree.stream().filter(c->"archive-eligible-trades".equals(c.getName())).findFirst().orElseThrow();
            var restore=(javax.swing.JButton)tree.stream().filter(c->"restore-reviewed-trade".equals(c.getName())).findFirst().orElseThrow();
            assertEquals(3,table.getRowCount());assertEquals("Unidentified offer",table.getValueAt(0,1));assertTrue(ledger.hasUnboundedReconciliation());
            archive.doClick();verify(companion).reviewTrades(account,10,List.of("eligible"),true);
            select.setSelectedItem("Archived");assertEquals(1,table.getRowCount());assertEquals("old",table.getValueAt(0,9));
            table.setRowSelectionInterval(0,0);assertTrue(restore.isEnabled());restore.doClick();verify(companion).reviewTrades(account,10,List.of("old"),false);
            select.setSelectedItem("All");assertEquals(4,table.getRowCount());
            when(companion.historyLedger()).thenReturn(new AccountLedgerSnapshot(AccountIdentity.fromProfileKey("other"),100,0,0,0,0,0,0,0,0,List.of()).withTradeEvidence(List.of(),List.of()));
            window.refresh();assertEquals(0,table.getRowCount());assertFalse(archive.isEnabled());assertFalse(restore.isEnabled());window.dispose();
        });
    }
    @Test public void unidentifiedOfferShowsItsReasonWithUnavailableMoneyAndNoClassificationControls() {
        var evidence=new MissedFlipEvidence("unidentified-clear",null,0,null,"RECONCILE",0,2,
            null,null,null,null,null,null,"offer cleared before a terminal observation");
        var ledger=new AccountLedgerSnapshot(AccountIdentity.fromProfileKey("one"),100,0,0,0,0,0,0,0,0,List.of())
            .withTradeEvidence(List.of(),List.of(evidence));
        var table=new MissedFlipsWindow.EvidenceTable(); table.update(ledger);
        assertEquals(1,table.getRowCount()); assertEquals("Unidentified offer",table.getValueAt(0,1));
        assertEquals("RECONCILE",table.getValueAt(0,2)); assertEquals(0,table.getValueAt(0,3));
        for(int column=4;column<=7;column++) assertEquals("Unavailable",table.getValueAt(0,column));
        assertEquals(evidence.getReason(),table.getValueAt(0,8));
        for(int column=0;column<table.getColumnCount();column++) assertFalse(table.isCellEditable(0,column));
    }
    @Test public void unknownSaleShowsRecordedEvidenceReadOnlyAndPersonalInventoryIsAbsent() {
        var personal=PositionProjection.reconcile(561,"Nature rune",6026,0,false,1,2,null,null,"personal");
        var ledger=new AccountLedgerSnapshot(AccountIdentity.fromProfileKey("one"),100,1,0,0,1,0,0,0,2,List.of(personal))
            .withTradeEvidence(List.of(),List.of(new MissedFlipEvidence("sale","offer",2,"Item 2","DISPOSE",100,2,
                null,12000L,0L,2L,null,null,"Acquisition unavailable")));
        var table=new MissedFlipsWindow.EvidenceTable(); table.update(ledger);
        assertEquals(1,table.getRowCount()); assertEquals("Item 2",table.getValueAt(0,1));
        assertEquals("Unavailable",table.getValueAt(0,7)); assertFalse(table.isCellEditable(0,7));
        table.update(null); assertEquals(0,table.getRowCount());
    }
    @Test public void anUnchangedHeartbeatKeepsTheFilterSortAndSelectedEvidence() {
        var evidence=new MissedFlipEvidence("sale","offer",561,"Nature rune","DISPOSE",1,2,
            null,100L,2L,2L,null,null,"Acquisition unavailable");
        var ledger=new AccountLedgerSnapshot(AccountIdentity.fromProfileKey("one"),100,1,0,0,1,0,0,0,0,List.of())
            .withTradeEvidence(List.of(),List.of(evidence));
        var model=new MissedFlipsWindow.EvidenceTable();model.update(ledger);
        var table=new javax.swing.JTable(model);table.setAutoCreateRowSorter(true);
        ((javax.swing.table.TableRowSorter<?>)table.getRowSorter()).setRowFilter(javax.swing.RowFilter.regexFilter("Nature rune"));
        table.setRowSelectionInterval(0,0);var events=new java.util.concurrent.atomic.AtomicInteger();
        model.addTableModelListener(e->events.incrementAndGet());
        model.update(ledger);
        assertEquals("A heartbeat must not tear down a populated table",0,events.get());
        assertEquals(1,table.getRowCount());assertEquals(0,table.getSelectedRow());
        model.update(null);assertEquals(0,table.getRowCount());assertEquals(1,events.get());
    }
    @Test public void theOpenWindowRetainsFilteredSelectionThroughNewRecordsAndClearsOnLogout()throws Exception {
        var companion=mock(com.flippingfriend.companion.CompanionClient.class);
        String account=AccountIdentity.fromProfileKey("one");
        var sale=new MissedFlipEvidence("sale","offer",561,"Nature rune","DISPOSE",1,2,
            null,100L,2L,2L,null,null,"Acquisition unavailable");
        var ledger=new AccountLedgerSnapshot(account,100,1,0,0,1,0,0,0,0,List.of()).withTradeEvidence(List.of(),List.of(sale));
        when(companion.historyLedger()).thenReturn(ledger);
        var window=new MissedFlipsWindow(companion);javax.swing.JComponent[] view=new javax.swing.JComponent[1];
        window.setPresenter((name,component)->view[0]=component);
        javax.swing.SwingUtilities.invokeAndWait(()->{
            window.open(null);
            var tree=CompactPanelContractTest.tree(view[0]);
            var table=(javax.swing.JTable)tree.stream().filter(c->c instanceof javax.swing.JTable).findFirst().orElseThrow();
            var filter=(javax.swing.JTextField)tree.stream().filter(c->c instanceof javax.swing.JTextField).findFirst().orElseThrow();
            filter.setText("Nature rune");table.getRowSorter().toggleSortOrder(0);table.setRowSelectionInterval(0,0);
            window.refresh();assertEquals(1,table.getRowCount());assertEquals(0,table.getSelectedRow());
            assertTrue(tree.stream().filter(c->c instanceof javax.swing.JLabel).map(c->((javax.swing.JLabel)c).getText())
                .anyMatch(t->t!=null && t.contains("showing last confirmed history")));
            var later=new MissedFlipEvidence("later","other",561,"Nature rune","RECONCILE",0,3,
                null,null,null,null,null,null,"Missing terminal capture");
            when(companion.historyLedger()).thenReturn(new AccountLedgerSnapshot(account,101,1,0,0,1,0,0,0,0,List.of())
                .withTradeEvidence(List.of(),List.of(later,sale)));
            window.refresh();assertEquals(2,table.getRowCount());
            assertEquals("sale",table.getValueAt(table.getSelectedRow(),9));
            assertEquals("Nature rune",filter.getText());
            window.refresh(false);assertEquals(0,table.getRowCount());assertEquals(-1,table.getSelectedRow());
            window.dispose();
        });
    }
}
