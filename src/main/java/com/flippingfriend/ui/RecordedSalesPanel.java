package com.flippingfriend.ui;

import com.flippingfriend.core.*;
import java.awt.*;
import java.time.Instant;
import java.util.*;
import java.util.List;
import java.util.function.IntConsumer;
import java.util.regex.Pattern;
import javax.swing.*;
import javax.swing.table.*;

/** Investigates the same durable sale rows as Performance. Unknown bounds stay visible. */
final class RecordedSalesPanel extends JPanel {
    private final JLabel coverage=new JLabel("Waiting for account ledger");
    private final DefaultTableModel model=new DefaultTableModel(new String[]{"Observed (UTC)","Item","Qty","Gross GP","Matched cost GP","Tax GP","Profit GP"},0) {
        @Override public boolean isCellEditable(int row,int column){return false;}
    };
    private final JTable table=new JTable(model);
    private final JTextField filter=new JTextField();
    private final JTextArea evidence=CompactSuggestionPanel.text(UiUtils.TEXT);
    private List<RecordedSale> rows=List.of();
    private String lastSignature;
    RecordedSalesPanel(IntConsumer graph) {
        super(new BorderLayout(8,8));setBackground(UiUtils.CARD);setPreferredSize(new Dimension(680,420));
        JPanel header=new JPanel(new BorderLayout(8,8));header.setOpaque(false);
        coverage.setForeground(UiUtils.TEXT);
        header.add(coverage,BorderLayout.NORTH);filter.setToolTipText("Filter recorded sales by item, date or amount");
        JPanel search=new JPanel(new BorderLayout(6,0));search.setOpaque(false);
        JLabel searchLabel=new JLabel("Filter sales:");searchLabel.setForeground(UiUtils.TEXT);searchLabel.setLabelFor(filter);
        search.add(searchLabel,BorderLayout.WEST);search.add(filter,BorderLayout.CENTER);header.add(search,BorderLayout.SOUTH);
        add(header,BorderLayout.NORTH);table.setAutoCreateRowSorter(true);table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        add(new JScrollPane(table),BorderLayout.CENTER);
        JPanel detail=new JPanel(new BorderLayout(8,8));detail.setOpaque(false);detail.add(evidence,BorderLayout.CENTER);
        JButton priceGraph=new JButton("Open selected item's graph");detail.add(priceGraph,BorderLayout.SOUTH);add(detail,BorderLayout.SOUTH);
        priceGraph.addActionListener(e->{int selected=table.getSelectedRow();if(selected>=0)graph.accept(rows.get(table.convertRowIndexToModel(selected)).getItemId());});
        filter.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            void changed(){((TableRowSorter<?>)table.getRowSorter()).setRowFilter(RowFilter.regexFilter("(?i)"+Pattern.quote(filter.getText())));}
            public void insertUpdate(javax.swing.event.DocumentEvent e){changed();}public void removeUpdate(javax.swing.event.DocumentEvent e){changed();}public void changedUpdate(javax.swing.event.DocumentEvent e){changed();}
        });
        table.getSelectionModel().addListSelectionListener(e->{int selected=table.getSelectedRow();
            if(selected<0){evidence.setText("");return;}var sale=rows.get(table.convertRowIndexToModel(selected));
            String reason=Objects.toString(sale.getReason(),"");
            evidence.setText(SaleEvidenceText.explain(reason)+"\n\nSource: "+Objects.toString(sale.getSourceEventId(),"Legacy identity unavailable")
                +"\nOffer: "+Objects.toString(sale.getOfferIdentity(),"Unknown")+"\nTax rule: "+Objects.toString(sale.getTaxPolicy(),"Unknown")
                +"\n"+Objects.toString(sale.getReason(),"No recorded accounting explanation"));
        });
    }
    void updateLedger(AccountLedgerSnapshot ledger) {
        updateLedger(ledger,false);
    }
    void updateLedger(AccountLedgerSnapshot ledger,boolean updating) {
        List<RecordedSale> incoming=ledger==null?null:ledger.getRecentSales();
        coverage.setText(incoming==null?"Recorded sale detail unavailable on this connection":incoming.size()+" of "+ledger.getSales()
            +" sale updates. "+(updating?"Updating; showing last confirmed history.":"Unknown cost/profit remains unknown."));
        coverage.setToolTipText(ledger==null?null:"Recorded through "+Instant.ofEpochSecond(ledger.getObservedAt())+". Times are observations; tax/profit ranges preserve uncertainty.");
        StringBuilder signature=new StringBuilder(ledger==null?"unavailable":ledger.getAccountId()+":"+ledger.getSales()+":"+(incoming==null));
        if(incoming!=null)for(var sale:incoming)signature.append('|').append(sale.getRowId()).append(':').append(sale.getGross())
            .append(':').append(sale.getTaxLower()).append(':').append(sale.getTaxUpper()).append(':').append(sale.getProfitLower()).append(':').append(sale.getProfitUpper());
        if(signature.toString().equals(lastSignature))return;lastSignature=signature.toString();
        rows=incoming==null?List.of():incoming;model.setRowCount(0);
        for(var sale:rows)model.addRow(new Object[]{Instant.ofEpochSecond(sale.getObservedAt()).toString(),sale.getItemName()+" (#"+sale.getItemId()+")",
            sale.getQuantity(),sale.getGross()==null?"Unknown":sale.getGross(),sale.getAppliedCost(),range(sale.getTaxLower(),sale.getTaxUpper()),sale.getProfitLower()==null?SaleEvidenceText.status(Objects.toString(sale.getReason(),"")):range(sale.getProfitLower(),sale.getProfitUpper())});
        evidence.setText("");
    }
    void selectOffer(String offer) {
        if(offer==null)return;
        filter.setText("");
        for(int i=0;i<rows.size();i++)if(offer.equals(rows.get(i).getOfferIdentity())) {
            int view=table.convertRowIndexToView(i);table.setRowSelectionInterval(view,view);
            table.scrollRectToVisible(table.getCellRect(view,0,true));return;
        }
        evidence.setText("This offer is outside the most recent recorded sale details available here. Its measurement remains in Performance.");
    }
    private static String range(Long lower,Long upper){return lower==null?"Unknown":lower.equals(upper)?lower.toString():lower+" to "+upper;}
}
