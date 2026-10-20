package com.flippingfriend.ui;

import com.flippingfriend.companion.CompanionClient;
import com.flippingfriend.core.AccountLedgerSnapshot;
import com.flippingfriend.core.MissedFlipEvidence;
import com.flippingfriend.core.TradeReviewSnapshot;
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

/** Opened separately. Reversible review annotations never mutate accounting or classify belongings. */
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
    private final javax.swing.JComboBox<String> view=new javax.swing.JComboBox<>(new String[]{"Needs attention","Archived","All"});
    private final javax.swing.JButton archive=new javax.swing.JButton("Archive eligible"),restore=new javax.swing.JButton("Restore selected");
    private AccountLedgerSnapshot ledgerShown;
    MissedFlipsWindow(CompanionClient companion) { this.companion = companion; }
    void open(Window owner) {
        if (content == null) {
            content = new JPanel(new BorderLayout(8, 8));
            content.setBorder(javax.swing.BorderFactory.createEmptyBorder(10, 10, 10, 10));
            JPanel header=new JPanel(new BorderLayout(6,6));
            status.setForeground(UiUtils.TEXT);
            header.add(status,BorderLayout.NORTH);
            JPanel actions=new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT,6,0));
            view.setName("trade-review-view");archive.setName("archive-eligible-trades");restore.setName("restore-reviewed-trade");
            archive.setToolTipText("Archive only closed unmatched sales with no earlier recorded acquisition. Keeps all money, source identities and risk guards.");
            actions.add(view);actions.add(archive);actions.add(restore);header.add(actions,BorderLayout.CENTER);
            view.addActionListener(e->refresh());
            archive.addActionListener(e->{var review=review();if(review!=null)companion.reviewTrades(displayedAccount,review.positionCursor,eligible(review),true);});
            restore.addActionListener(e->{var review=review();String source=selectedSource();
                if(review!=null && source!=null)companion.reviewTrades(displayedAccount,review.positionCursor,List.of(source),false);});
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
            int[] widths = {160,170,110,80,100,110,110,110,460,330,330,210};
            for (int i=0;i<widths.length;i++) table.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
            content.add(new JScrollPane(table), BorderLayout.CENTER);
            filter.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
                void changed() {((javax.swing.table.TableRowSorter<?>)table.getRowSorter()).setRowFilter(
                    javax.swing.RowFilter.regexFilter("(?i)"+java.util.regex.Pattern.quote(filter.getText())));}
                public void insertUpdate(javax.swing.event.DocumentEvent e){changed();}
                public void removeUpdate(javax.swing.event.DocumentEvent e){changed();}
                public void changedUpdate(javax.swing.event.DocumentEvent e){changed();}
            });
            table.getSelectionModel().addListSelectionListener(e->buttons());
            content.add(new JLabel("Archiving hides review clutter only. Original trades, unknown money, loss history and live risk checks are preserved."), BorderLayout.SOUTH);
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
        ledgerShown=ledger;
        var review=companion.tradeReview();if(review!=null && (ledger==null || !ledger.getAccountId().equals(review.accountId)))review=null;
        boolean changed=model.update(ledger,review,(String)view.getSelectedItem());
        displayedAccount=ledger==null?null:ledger.getAccountId();
        if(changed && selectedSource!=null && !"Unavailable".equals(selectedSource))
            for(int row=0;row<model.getRowCount();row++) if(selectedSource.equals(model.getValueAt(row,9))) {
                int visible=table.convertRowIndexToView(row);
                if(visible>=0)table.setRowSelectionInterval(visible,visible);
                break;
            }
        status.setText(ledger == null || ledger.getMissedFlips() == null
            ? "Waiting for account trade history."
            : model.getRowCount() + " shown · "+(review==null?0:review.effectiveArchived().size())+" archived. " + (companion.lastLedger()!=ledger
                ? "Updating; showing last confirmed history." : "Unknown values stay unknown; current account checks govern new buys."));
        String reviewError=java.util.Objects.toString(companion.tradeReviewError(),"");
        if(ledger!=null && !reviewError.isEmpty())status.setText(status.getText()+" "+reviewError);
        status.setToolTipText(ledger==null?null:"Recorded through "+Instant.ofEpochSecond(ledger.getObservedAt())+". Historical quantities are not current inventory.");
        buttons();
    }
    private TradeReviewSnapshot review(){var r=companion.tradeReview();return r!=null && r.accountId.equals(displayedAccount)?r:null;}
    private String selectedSource(){return table==null || table.getSelectedRow()<0?null:(String)model.getValueAt(table.convertRowIndexToModel(table.getSelectedRow()),9);}
    private List<String> eligible(TradeReviewSnapshot review) {
        List<String> sources=new ArrayList<>();if(ledgerShown!=null && ledgerShown.getMissedFlips()!=null)
            for(var row:ledgerShown.getMissedFlips())if(TradeReviewSnapshot.ELIGIBLE.equals(review.states.get(row.getEventId())))sources.add(row.getEventId());
        return sources;
    }
    private void buttons(){var review=review();boolean ready=review!=null && !companion.tradeReviewUpdating();
        archive.setEnabled(ready && !eligible(review).isEmpty());restore.setEnabled(ready && review.archivedSources.contains(selectedSource()));}
    void dispose() { if (dialog != null) { dialog.dispose(); dialog = null; } content = null; table=null; displayedAccount=null; model.update(null); status.setText(""); }

    static final class EvidenceTable extends AbstractTableModel {
        private static final DateTimeFormatter DATE=DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneOffset.UTC);
        private final String[] columns = {"When (UTC)","Item","Update","Quantity","Buy cost","Gross sale","Tax","Profit","Missing evidence","Source event","Offer identity","Review"};
        private final List<Object[]> rows = new ArrayList<>();
        private String accountId;
        boolean update(AccountLedgerSnapshot ledger) {
            return update(ledger,null,"Needs attention");
        }
        boolean update(AccountLedgerSnapshot ledger,TradeReviewSnapshot review,String view) {
            List<Object[]> updated=new ArrayList<>();
            if (ledger != null && ledger.isValid() && ledger.getMissedFlips() != null) {
                if(review!=null && (!review.isValid() || !ledger.getAccountId().equals(review.accountId)))review=null;
                for (MissedFlipEvidence e : ledger.getMissedFlips()) {
                    String state=review==null?TradeReviewSnapshot.OPEN:review.states.getOrDefault(e.getEventId(),TradeReviewSnapshot.OPEN);
                    if("Archived".equals(view)!=TradeReviewSnapshot.ARCHIVED.equals(state) && !"All".equals(view))continue;
                    updated.add(new Object[] {
                    date(e.getObservedAt()),e.getItemName(),e.getType(),e.getQuantity(),money(e.getAcquisitionCost()),
                    money(e.getGrossProceeds()),range(e.getTaxLower(),e.getTaxUpper()),
                    range(e.getProfitLower(),e.getProfitUpper()),e.getReason(),e.getEventId(),
                    e.getOfferIdentity()==null?"Unavailable":e.getOfferIdentity(),label(state)});
                }
                // Preserve legacy cost evidence as legacy evidence, never as an observed acquisition.
                for (var p : ledger.getPositions()) if (!"Archived".equals(view) && p.isCostKnown() && p.getSourceEventId() == null)
                    updated.add(new Object[] {date(p.getUpdatedAt()),p.getItemName(),"Legacy record",p.getQuantity(),
                        money(p.getTotalCost()),"Unavailable","Unavailable","Unavailable",
                        "Observed acquisition provenance missing; recorded quantity is not current inventory.","Unavailable","Unavailable","Retained legacy evidence"});
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
        private static String label(String state) {
            switch(state) {
                case TradeReviewSnapshot.ELIGIBLE:return "Closed; no recorded purchase";
                case TradeReviewSnapshot.RETAINED:return "Purchase records retained";
                case TradeReviewSnapshot.ARCHIVED:return "Archived (restorable)";
                default:return "Live or closure unproven";
            }
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
