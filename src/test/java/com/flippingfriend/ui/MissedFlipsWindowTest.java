package com.flippingfriend.ui;
import com.flippingfriend.core.*;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;
public class MissedFlipsWindowTest {
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
}
