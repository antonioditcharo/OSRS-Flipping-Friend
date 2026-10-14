package com.flippingfriend.ui;

import com.flippingfriend.companion.CompanionClient;
import com.flippingfriend.core.AccountLedgerSnapshot;
import com.flippingfriend.core.MissedFlipEvidence;
import java.awt.BorderLayout;
import java.awt.Window;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.table.AbstractTableModel;

/** Opened separately. Evidence is read-only; no buttons mutate accounting or classify belongings. */
final class MissedFlipsWindow {
    private final CompanionClient companion;
    private JDialog dialog;
    private JPanel content;
    private java.util.function.BiConsumer<String, javax.swing.JComponent> presenter;
    void setPresenter(java.util.function.BiConsumer<String, javax.swing.JComponent> presenter) { this.presenter = presenter; }
    private final EvidenceTable model = new EvidenceTable();
    private final JLabel status = new JLabel();
    MissedFlipsWindow(CompanionClient companion) { this.companion = companion; }
    void open(Window owner) {
        if (content == null) {
            content = new JPanel(new BorderLayout(8, 8));
            content.setBorder(javax.swing.BorderFactory.createEmptyBorder(10, 10, 10, 10));
            content.add(status, BorderLayout.NORTH);
            JTable table = new JTable(model);
            table.setAutoCreateRowSorter(true);
            table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
            int[] widths = {160,170,110,80,100,110,110,110,460};
            for (int i=0;i<widths.length;i++) table.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
            content.add(new JScrollPane(table), BorderLayout.CENTER);
            content.add(new JLabel("Recorded trade updates; quantities are not current inventory. Unknown money stays unavailable."), BorderLayout.SOUTH);
        }
        refresh();
        if (presenter != null) { presenter.accept("Missed flips", content); return; }
        if (dialog == null) {
            dialog = new JDialog(owner, "Missed flips", java.awt.Dialog.ModalityType.MODELESS);
            dialog.setDefaultCloseOperation(JDialog.HIDE_ON_CLOSE);
            dialog.setContentPane(content); dialog.setSize(1100, 460); dialog.setLocationRelativeTo(owner);
        }
        dialog.setVisible(true);
    }
    void refresh() {
        if (content == null) return;
        AccountLedgerSnapshot ledger = companion.lastLedger();
        model.update(ledger);
        status.setText(ledger == null || ledger.getMissedFlips() == null
            ? "Waiting for fresh account trade evidence."
            : model.getRowCount() + " incomplete or uncertain trade updates. Historical gaps can require investigation.");
    }
    void dispose() { if (dialog != null) { dialog.dispose(); dialog = null; } content = null; model.update(null); status.setText(""); }

    static final class EvidenceTable extends AbstractTableModel {
        private final String[] columns = {"When (UTC)","Item","Update","Quantity","Buy cost","Gross sale","Tax","Profit","Missing evidence"};
        private final List<Object[]> rows = new ArrayList<>();
        void update(AccountLedgerSnapshot ledger) {
            rows.clear();
            if (ledger != null && ledger.isValid() && ledger.getMissedFlips() != null) {
                for (MissedFlipEvidence e : ledger.getMissedFlips()) rows.add(new Object[] {
                    date(e.getObservedAt()),e.getItemName(),e.getType(),e.getQuantity(),money(e.getAcquisitionCost()),
                    money(e.getGrossProceeds()),range(e.getTaxLower(),e.getTaxUpper()),
                    range(e.getProfitLower(),e.getProfitUpper()),e.getReason()});
                // Preserve legacy cost evidence as legacy evidence, never as an observed acquisition.
                for (var p : ledger.getPositions()) if (p.isCostKnown() && p.getSourceEventId() == null)
                    rows.add(new Object[] {date(p.getUpdatedAt()),p.getItemName(),"Legacy record",p.getQuantity(),
                        money(p.getTotalCost()),"Unavailable","Unavailable","Unavailable",
                        "Observed acquisition provenance missing; recorded quantity is not current inventory."});
            }
            fireTableDataChanged();
        }
        private static String date(long seconds) { return DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneOffset.UTC).format(Instant.ofEpochSecond(seconds)); }
        private static String money(Long value) { return value == null ? "Unavailable" : value + " gp"; }
        private static String range(Long low, Long high) {
            return low == null ? "Unavailable" : low.equals(high) ? money(low) : low + "–" + high + " gp";
        }
        public int getRowCount() { return rows.size(); }
        public int getColumnCount() { return columns.length; }
        public String getColumnName(int column) { return columns[column]; }
        public Object getValueAt(int row, int column) { return rows.get(row)[column]; }
        public boolean isCellEditable(int row, int column) { return false; }
    }
}
