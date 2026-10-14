package com.flippingfriend.ui;
import com.flippingfriend.core.*;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;
public class MissedFlipsWindowTest {
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
