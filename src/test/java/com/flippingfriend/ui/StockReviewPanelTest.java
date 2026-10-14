package com.flippingfriend.ui;

import com.flippingfriend.companion.CompanionClient;
import com.flippingfriend.core.*;
import java.awt.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.*;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class StockReviewPanelTest {
    @Test public void playerClickUsesDisplayedAccountAndRowAndRequestsRefreshOnlyAfterSave() throws Exception {
        CompanionClient client = mock(CompanionClient.class);
        String account = AccountIdentity.fromProfileKey("synthetic-ui");
        PositionProjection p = PositionProjection.reconcile(100, "Example supplies", 3, 0, false, 10, 20, null, null, "unknown cost");
        when(client.lastLedger()).thenReturn(new AccountLedgerSnapshot(account, Instant.now().getEpochSecond(),
            1, 0, 0, 0, 0, 0, 0, 0, Collections.singletonList(p)));
        when(client.stockReviews()).thenReturn(Collections.emptyMap());
        when(client.reviewStock(account, p, StockDeclaration.Use.PERSONAL)).thenReturn(true);
        AtomicInteger changed = new AtomicInteger();
        SwingUtilities.invokeAndWait(() -> {
            StockReviewPanel panel = new StockReviewPanel(client, changed::incrementAndGet);
            panel.refresh();
            JButton personal = button(panel, "Personal item"); assertNotNull(personal); personal.doClick();
            assertEquals(1, changed.get());
            JButton retired = button(panel, "I no longer hold this"); assertNotNull(retired); retired.doClick();
            assertEquals("a failed save cannot recompute under an unrecorded declaration", 1, changed.get());
        });
        verify(client).reviewStock(account, p, StockDeclaration.Use.PERSONAL);
        verify(client).reviewStock(account, p, StockDeclaration.Use.NO_LONGER_HELD);
    }
    private static JButton button(Container root, String text) {
        for (Component c : root.getComponents()) {
            if (c instanceof JButton && text.equals(((JButton)c).getText())) return (JButton)c;
            if (c instanceof Container) { JButton found = button((Container)c, text); if (found != null) return found; }
        }
        return null;
    }
}
