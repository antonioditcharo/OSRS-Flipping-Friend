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
import java.util.Arrays;
import java.util.List;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.table.AbstractTableModel;

/** Opened separately. Evidence is read-only; no buttons mutate accounting or classify belongings. */
final class MissedFlipsWindow {
    private final CompanionClient companion;
    private JFrame dialog;
    private JPanel content;
    private JTable table;
    private String displayedAccount;
    private java.util.function.BiConsumer<String, javax.swing.JComponent> presenter;
    void setPresenter(java.util.function.BiConsumer<String, javax.swing.JComponent> presenter) { this.presenter = presenter; }
    private final EvidenceTable model = new EvidenceTable();
    private final JLabel status = new JLabel();
    MissedFlipsWindow(CompanionClient companion) { this.companion = companion; }
    void open(Window owner) {
        if (content == null) {
            content = new JPanel(new BorderLayout(8, 8));
            content.setBorder(javax.swing.BorderFactory.createEmptyBorder(10, 10, 10, 10));
            JPanel header=new JPanel(new BorderLayout(6,6));
            status.setForeground(UiUtils.TEXT);
            header.add(status,BorderLayout.NORTH);
            javax.swing.JTextField filter=new javax.swing.JTextField();
            filter.setToolTipText("Filter history by item, reason, time, source or offer identity");
            JPanel search=new JPanel(new BorderLayout(6,0));
            JLabel searchLabel=new JLabel("Filter history:");searchLabel.setForeground(UiUtils.TEXT);searchLabel.setLabelFor(filter);
            search.add(searchLabel,BorderLayout.WEST);search.add(filter,BorderLayout.CENTER);
            header.add(search,BorderLayout.SOUTH);content.add(header,BorderLayout.NORTH);
            table = new JTable(model);
            table.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
            table.setAutoCreateRowSorter(true);
            table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
            int[] widths = {160,170,110,80,100,110,110,110,460,330,330};
            for (int i=0;i<widths.length;i++) table.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
            content.add(new JScrollPane(table), BorderLayout.CENTER);
            filter.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
                void changed() {((javax.swing.table.TableRowSorter<?>)table.getRowSorter()).setRowFilter(
                    javax.swing.RowFilter.regexFilter("(?i)"+java.util.regex.Pattern.quote(filter.getText())));}
                public void insertUpdate(javax.swing.event.DocumentEvent e){changed();}
                public void removeUpdate(javax.swing.event.DocumentEvent e){changed();}
                public void changedUpdate(javax.swing.event.DocumentEvent e){changed();}
            });
            content.add(new JLabel("Recorded trade updates; quantities are not current inventory. Unknown money stays unavailable."), BorderLayout.SOUTH);
        }
        refresh();
        if (presenter != null) { presenter.accept("Missed flips", content); return; }
        if (dialog == null) {
            dialog = InvestigationWindow.create(owner,"Missed flips",content);
        }
        InvestigationWindow.show(dialog);
    }
    void refresh() {
        refresh(true);
    }
    void refresh(boolean loggedIn) {
        if (content == null) return;
        AccountLedgerSnapshot ledger = loggedIn?companion.historyLedger():null;
        boolean sameAccount=ledger!=null && ledger.getAccountId().equals(displayedAccount);
        Object selectedSource=!sameAccount || table.getSelectedRow()<0?null:model.getValueAt(table.convertRowIndexToModel(table.getSelectedRow()),9);
        boolean changed=model.update(ledger);
        displayedAccount=ledger==null?null:ledger.getAccountId();
        if(changed && selectedSource!=null && !"Unavailable".equals(selectedSource))
            for(int row=0;row<model.getRowCount();row++) if(selectedSource.equals(model.getValueAt(row,9))) {
                int visible=table.convertRowIndexToView(row);
                if(visible>=0)table.setRowSelectionInterval(visible,visible);
                break;
            }
        status.setText(ledger == null || ledger.getMissedFlips() == null
            ? "Waiting for account trade history."
            : model.getRowCount() + " historical updates. " + (companion.lastLedger()!=ledger
                ? "Updating; showing last confirmed history." : "Unknown values stay unknown; current account checks govern new buys."));
        status.setToolTipText(ledger==null?null:"Recorded through "+Instant.ofEpochSecond(ledger.getObservedAt())+". Historical quantities are not current inventory.");
    }
    void dispose() { if (dialog != null) { dialog.dispose(); dialog = null; } content = null; table=null; displayedAccount=null; model.update(null); status.setText(""); }

    static final class EvidenceTable extends AbstractTableModel {
        private static final DateTimeFormatter DATE=DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneOffset.UTC);
        private final String[] columns = {"When (UTC)","Item","Update","Quantity","Buy cost","Gross sale","Tax","Profit","Missing evidence","Source event","Offer identity"};
        private final List<Object[]> rows = new ArrayList<>();
        private String accountId;
        boolean update(AccountLedgerSnapshot ledger) {
            List<Object[]> updated=new ArrayList<>();
            if (ledger != null && ledger.isValid() && ledger.getMissedFlips() != null) {
                for (MissedFlipEvidence e : ledger.getMissedFlips()) updated.add(new Object[] {
                    date(e.getObservedAt()),e.getItemName(),e.getType(),e.getQuantity(),money(e.getAcquisitionCost()),
                    money(e.getGrossProceeds()),range(e.getTaxLower(),e.getTaxUpper()),
                    range(e.getProfitLower(),e.getProfitUpper()),e.getReason(),e.getEventId(),
                    e.getOfferIdentity()==null?"Unavailable":e.getOfferIdentity()});
                // Preserve legacy cost evidence as legacy evidence, never as an observed acquisition.
                for (var p : ledger.getPositions()) if (p.isCostKnown() && p.getSourceEventId() == null)
                    updated.add(new Object[] {date(p.getUpdatedAt()),p.getItemName(),"Legacy record",p.getQuantity(),
                        money(p.getTotalCost()),"Unavailable","Unavailable","Unavailable",
                        "Observed acquisition provenance missing; recorded quantity is not current inventory.","Unavailable","Unavailable"});
            }
            String nextAccount=ledger==null?null:ledger.getAccountId();
            boolean unchanged=java.util.Objects.equals(accountId,nextAccount) && rows.size()==updated.size();
            if(unchanged)for(int i=0;i<rows.size();i++)if(!Arrays.equals(rows.get(i),updated.get(i))){unchanged=false;break;}
            if(unchanged)return false;
            accountId=nextAccount;
            rows.clear();rows.addAll(updated);
            fireTableDataChanged();
            return true;
        }
        private static String date(long seconds) { return DATE.format(Instant.ofEpochSecond(seconds)); }
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
