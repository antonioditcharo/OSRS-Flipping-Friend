package com.flippingfriend.ui;

import com.flippingfriend.companion.CompanionClient;
import com.flippingfriend.core.PositionConsistencyState;
import com.flippingfriend.core.PositionProjection;
import com.flippingfriend.core.StockDeclaration;
import java.awt.Component;
import java.util.Map;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JPanel;

/** Player declarations against fresh scoped ledger evidence; performs no game or accounting action. */
final class StockReviewPanel extends JPanel {
    private final CompanionClient companion;
    private final Runnable changed;
    private String lastSignature;
    StockReviewPanel(CompanionClient companion, Runnable changed) {
        this.companion = companion; this.changed = changed;
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBackground(UiUtils.BACKGROUND); setAlignmentX(Component.LEFT_ALIGNMENT);
    }
    void refresh() {
        var ledger = companion.lastLedger();
        Map<Integer, StockDeclaration> declarations = companion.stockReviews();
        StringBuilder signature = new StringBuilder(ledger == null ? "unavailable" : ledger.getAccountId());
        if (ledger != null) for (PositionProjection position : ledger.getPositions()) {
            signature.append('|').append(position.getItemId()).append(':').append(position.getQuantity())
                .append(':').append(position.getItemName()).append(':').append(position.getOpenedAt())
                .append(':').append(position.isCostKnown()).append(':').append(position.getSourceOfferIdentity())
                .append(':').append(position.getTotalCost()).append(':').append(position.getConsistencyState())
                .append(':').append(position.getConsistencyReason()).append(':').append(position.getSourceEventId());
            StockDeclaration declaration = declarations.get(position.getItemId());
            if (declaration != null) signature.append(':').append(declaration.getUse()).append(':').append(declaration.matches(position));
        }
        if (signature.toString().equals(lastSignature)) return;
        lastSignature = signature.toString(); removeAll();
        add(UiUtils.mutedText("Review unpriced entries that are personal items or that you no longer hold. "
            + "Your review changes trading intent; purchase history and reported profit stay unchanged. "
            + "Open offers and new trade evidence can still require review."));
        if (ledger == null) add(UiUtils.mutedText("Waiting for a fresh account ledger."));
        else {
            int reviews = 0;
            for (PositionProjection position : ledger.getPositions()) {
                StockDeclaration declaration = declarations.get(position.getItemId());
                if (position.getConsistencyState() != PositionConsistencyState.RECONCILE && declaration == null) continue;
                reviews++;
                JPanel row = UiUtils.card(); row.setLayout(new BoxLayout(row, BoxLayout.Y_AXIS));
                row.add(UiUtils.wrappedText(position.getItemName(), UiUtils.TEXT,
                    net.runelite.client.ui.FontManager.getRunescapeBoldFont(), UiUtils.CARD_TEXT_WIDTH));
                row.add(rowText("Recorded quantity: " + position.getQuantity() + ". "
                    + (position.isCostKnown() ? "Purchase cost recorded." : "Purchase cost unavailable.")));
                row.add(rowText(declaration != null && declaration.matches(position)
                    ? "Your review: " + (declaration.getUse() == StockDeclaration.Use.PERSONAL ? "personal item" : "no longer held")
                    : declaration == null ? "Needs your review." : "Old review no longer applies to this position."));
                if (!position.isCostKnown() && position.getTotalCost() == 0) {
                    if (position.getQuantity() > 0 && position.getSourceEventId() == null && position.getSourceOfferIdentity() == null)
                        addChoice(row, ledger.getAccountId(), position, "Personal item", StockDeclaration.Use.PERSONAL);
                    addChoice(row, ledger.getAccountId(), position, "I no longer hold this", StockDeclaration.Use.NO_LONGER_HELD);
                } else row.add(rowText("Cost evidence needs investigation; this entry cannot be excluded here."));
                if (declaration != null) addChoice(row, ledger.getAccountId(), position, "Clear review", null);
                UiUtils.constrainWidth(row); add(row); add(UiUtils.gap(UiUtils.SPACE_S));
            }
            if (reviews == 0) add(UiUtils.mutedText("No unpriced entries need review."));
            if (ledger.getUnresolvedSales() > 0)
                add(UiUtils.mutedText(ledger.getUnresolvedSales() + " sale updates still have unknown profit. Reviews do not price those sales."));
        }
        revalidate(); repaint();
    }
    private javax.swing.JTextArea rowText(String text) {
        return UiUtils.wrappedText(text, UiUtils.MUTED,
            net.runelite.client.ui.FontManager.getRunescapeSmallFont(), UiUtils.CARD_TEXT_WIDTH);
    }
    private void addChoice(JPanel row, String accountId, PositionProjection position, String text, StockDeclaration.Use use) {
        JButton button = new JButton(text);
        button.setAlignmentX(Component.LEFT_ALIGNMENT); button.setBackground(UiUtils.CARD);
        button.setForeground(UiUtils.TEXT); button.setFocusPainted(false);
        button.addActionListener(event -> {
            if (companion.reviewStock(accountId, position, use)) { lastSignature = null; changed.run(); refresh(); }
            else { row.add(UiUtils.mutedText("Review not saved. Refresh the account data and try again.")); revalidate(); }
        });
        row.add(button);
    }
}
