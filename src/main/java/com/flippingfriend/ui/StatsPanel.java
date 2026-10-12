package com.flippingfriend.ui;

import com.flippingfriend.core.AccountLedgerSnapshot;
import com.flippingfriend.model.Explainer;
import java.awt.Component;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import net.runelite.client.ui.FontManager;

/** Recorded realized profit, with incomplete coverage and tax ranges visible. */
class StatsPanel extends JPanel
{
    private final Explainer explainer;
    private final JLabel profitCaption = UiUtils.small("Recorded profit");
    private final JTextArea profitValue = UiUtils.wrappedText("—", UiUtils.TEXT,
        FontManager.getRunescapeBoldFont(), UiUtils.CARD_TEXT_WIDTH);
    private final JTextArea taxValue = UiUtils.mutedText("—");
    private final JLabel salesValue = UiUtils.small("—");
    private final JLabel reviewValue = UiUtils.small("—");
    private final JLabel positionReviewValue = UiUtils.small("—");
    private final JTextArea coverage = UiUtils.mutedText("");

    StatsPanel(Explainer explainer)
    {
        this.explainer = explainer;
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBackground(UiUtils.CARD); setBorder(UiUtils.cardBorder());
        setAlignmentX(Component.LEFT_ALIGNMENT);
        for (Component component : new Component[] {profitCaption, profitValue, taxValue, salesValue, reviewValue, positionReviewValue, coverage})
        {
            if (component instanceof javax.swing.JComponent)
                ((javax.swing.JComponent) component).setAlignmentX(Component.LEFT_ALIGNMENT);
            add(component); add(UiUtils.gap(UiUtils.SPACE_XS));
        }
        updateLedger(null);
    }

    void updateLedger(AccountLedgerSnapshot ledger)
    {
        if (ledger == null)
        {
            profitCaption.setText("Recorded profit"); UiUtils.setWrappedText(profitValue, "Unavailable");
            profitValue.setForeground(UiUtils.TEXT);
            UiUtils.setWrappedText(taxValue, "Tax: unavailable"); salesValue.setText("Sales: unavailable"); reviewValue.setText("");
            positionReviewValue.setText("");
            UiUtils.setWrappedText(coverage, "Waiting for this account's ledger. Earlier trades remain in trade history.");
        }
        else
        {
            profitCaption.setText(ledger.getUnresolvedSales() > 0 ? "Known recorded profit" : "Recorded profit");
            UiUtils.setWrappedText(profitValue, ledger.getBoundedSales() == 0 && ledger.getSales() > 0
                ? "Unresolved" : range(ledger.getProfitLower(), ledger.getProfitUpper()));
            profitValue.setForeground(ledger.getProfitLower() > 0 ? UiUtils.profitColor(1)
                : ledger.getProfitUpper() < 0 ? UiUtils.profitColor(-1) : UiUtils.TEXT);
            UiUtils.setWrappedText(taxValue, (ledger.getUnresolvedTaxSales() > 0 ? "Known recorded tax: " : "Recorded tax: ")
                + (ledger.getTaxBoundedSales() == 0 && ledger.getSales() > 0 ? "Unresolved" : range(ledger.getTaxLower(), ledger.getTaxUpper())));
            salesValue.setText("Sale updates: " + ledger.getSales());
            reviewValue.setText("Unresolved sales: " + ledger.getUnresolvedSales());
            positionReviewValue.setText("Positions to review: " + ledger.getPositionsNeedingReview());
            UiUtils.setWrappedText(coverage, "Realized sale profit after tax. Ranges preserve uncertain fills or tax rules. "
                + "Earlier journals and unresolved sales are excluded. GP/hour awaits session tracking.");
        }
        revalidate(); repaint();
    }

    @Override public java.awt.Dimension getMaximumSize()
    { return new java.awt.Dimension(Integer.MAX_VALUE, getPreferredSize().height); }

    private String range(long lower, long upper)
    {
        // Full GP precision avoids hiding a small loss or rounding uncertainty at a large value.
        return lower == upper ? explainer.formatNumber(lower) + " gp"
            : explainer.formatNumber(lower) + " to " + explainer.formatNumber(upper) + " gp";
    }
}
