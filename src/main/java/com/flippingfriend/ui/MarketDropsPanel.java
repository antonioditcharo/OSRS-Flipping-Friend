package com.flippingfriend.ui;

import com.flippingfriend.data.MarketDataService;
import com.flippingfriend.model.MarketDropDetector;
import java.awt.BorderLayout;
import java.time.Instant;
import java.util.Locale;
import javax.swing.*;

/** Read-only market-wide investigation; it cannot publish or override trading advice. */
final class MarketDropsPanel extends JPanel {
    private final MarketDataService market;
    private final JTextArea rows=CompactSuggestionPanel.text(UiUtils.TEXT);
    MarketDropsPanel(MarketDataService market) {
        super(new BorderLayout(8,8)); this.market=market; setBackground(UiUtils.CARD);
        setBorder(BorderFactory.createEmptyBorder(12,12,12,12));
        add(UiUtils.bodyText("Public market drops — watch list. Price recovery and fills are unproven. Use current guided advice for any entry."),BorderLayout.NORTH);
        add(rows,BorderLayout.CENTER);
    }
    void refresh() {
        var drops=new MarketDropDetector().detect(market.getSnapshot(),Instant.now());
        StringBuilder text=new StringBuilder();
        for(var d:drops) text.append(String.format(Locale.ROOT,"%s (#%d)\nLow %,d gp · %.1f%% below prior average %,d gp\nObserved %s\n\n",
            d.name,d.itemId,d.currentLow,d.fraction*100,d.referenceLow,Instant.ofEpochSecond(d.observedAt)));
        rows.setText(drops.isEmpty()?"No fresh, liquid drops meet the 10% watch threshold. Missing/stale prices are excluded.":text.toString());
    }
}
