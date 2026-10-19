package com.flippingfriend.ui;

import com.flippingfriend.FlippingFriendConfig;
import com.flippingfriend.CheckInterval;
import com.flippingfriend.companion.CompanionClient;
import com.flippingfriend.core.AccountLedgerSnapshot;
import com.flippingfriend.data.MarketDataService;
import com.flippingfriend.model.Explainer;
import com.flippingfriend.model.Suggestion;
import com.flippingfriend.model.SuggestionEngine;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import javax.swing.*;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.ui.FontManager;

/** The former sidebar sections are opened deliberately in a single modeless details window. */
final class PanelDetails {
    private final JTabbedPane tabs = new DetailsTabs();
    private final Map<String, JComponent> views = new LinkedHashMap<>();
    private final PositionsPanel holdings;
    private final HistoryPanel history;
    private final PortfolioPanel plan;
    private final StatsPanel performance;
    private final BankTrajectoryPanel bank;
    private final MarketDataService market;
    private final Explainer explainer;
    private final JComboBox<CheckInterval> interval = new JComboBox<>(CheckInterval.values());
    private final JCheckBox sellOnly = new JCheckBox("Finish session (sell only)");
    private final SuggestionEngine engine;
    private final FlippingFriendConfig config;
    private final JTextArea recommendation = CompactSuggestionPanel.text(UiUtils.TEXT);
    private final MarketGraphView graph;
    private final MarketDropsPanel marketDrops;
    private final RecordedSalesPanel recordedSales;
    private final JButton copyQuantity = new JButton("Copy quantity"), copyPrice = new JButton("Copy price");
    private Suggestion shown = Suggestion.idle();
    private boolean syncing;
    private JFrame dialog;
    private BiConsumer<String, JComponent> presenter;

    PanelDetails(PositionsPanel holdings, HistoryPanel history, PortfolioPanel plan, StatsPanel performance,
        BankTrajectoryPanel bank, MarketDataService market, Explainer explainer, SuggestionEngine engine,
        FlippingFriendConfig config, ConfigManager settings, CompanionClient companion, Runnable changed) {
        this.holdings = holdings; this.history = history; this.plan = plan; this.performance = performance;
        this.bank = bank; this.market = market; this.explainer = explainer; this.engine = engine;
        this.config = config;
        graph=new MarketGraphView(market);
        marketDrops=new MarketDropsPanel(market);
        recordedSales=new RecordedSalesPanel(item->{graph.showItem(item);select("Price graph");});
        JPanel guide = new JPanel(new BorderLayout(8, 8)); guide.setBackground(UiUtils.CARD);
        guide.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12)); guide.add(recommendation, BorderLayout.CENTER);
        JPanel copies = new JPanel(new FlowLayout(FlowLayout.LEFT)); copies.setOpaque(false);
        copies.add(copyQuantity); copies.add(copyPrice); guide.add(copies, BorderLayout.SOUTH);
        copyQuantity.addActionListener(e -> copy(Integer.toString(shown.getQuantity())));
        copyPrice.addActionListener(e -> copy(Integer.toString(shown.getPrice())));
        add("Recommendation", guide);
        add("Price graph",graph);
        add("Plan", plan); add("Holdings", holdings); add("Performance", performance);
        add("Local history", history); add("Bank estimate", bank);
        add("Market drops",marketDrops);
        add("Recorded sales",recordedSales);
        JPanel settingsView = new JPanel(); settingsView.setBackground(UiUtils.CARD);
        settingsView.setLayout(new BoxLayout(settingsView, BoxLayout.Y_AXIS));
        settingsView.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        settingsView.add(UiUtils.bodyText("Other checking intervals")); settingsView.add(interval);
        interval.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        interval.addActionListener(e -> {
            if (!syncing && interval.getSelectedItem() != null) {
                settings.setConfiguration(FlippingFriendConfig.GROUP, "checkInterval", interval.getSelectedItem());
                companion.clearIncumbent(); changed.run();
            }
        });
        sellOnly.setOpaque(false); sellOnly.setForeground(UiUtils.TEXT);
        sellOnly.setToolTipText("Stop new buys and continue offer/sell guidance. Cleared on next login.");
        sellOnly.addActionListener(e -> { engine.setSellOnly(sellOnly.isSelected()); companion.clearIncumbent(); changed.run(); });
        settingsView.add(UiUtils.gap(12)); settingsView.add(sellOnly); settingsView.add(UiUtils.gap(12));
        settingsView.add(UiUtils.bodyText("Set the input key and other options in RuneLite's Flipping Friend configuration. "
            + "The key fills one open quantity or price prompt; press Enter and Confirm yourself."));
        for (Component child : settingsView.getComponents()) if (child instanceof JComponent)
            ((JComponent)child).setAlignmentX(Component.LEFT_ALIGNMENT);
        add("Settings", settingsView);
        tabs.setFont(FontManager.getRunescapeSmallFont());
        tabs.setTabLayoutPolicy(JTabbedPane.SCROLL_TAB_LAYOUT);
        tabs.addChangeListener(e -> refreshSelected());
    }

    private void add(String name, JComponent content) {
        views.put(name, content);
        if (name.equals("Price graph")) tabs.addTab(name, content);
        else {
            UiUtils.ScrollableColumn column = new UiUtils.ScrollableColumn();
            column.setLayout(new BorderLayout()); column.add(content, BorderLayout.NORTH);
            JScrollPane scroll = new JScrollPane(column); scroll.setBorder(BorderFactory.createEmptyBorder());
            scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
            tabs.addTab(name, scroll);
        }
    }

    void update(Suggestion advice, AccountLedgerSnapshot ledger) {
        update(advice,ledger,ledger,false);
    }
    void update(Suggestion advice,AccountLedgerSnapshot ledger,AccountLedgerSnapshot history,boolean historyUpdating) {
        shown = advice == null ? Suggestion.idle() : advice;
        graph.update(shown);
        performance.updateLedger(ledger);
        recordedSales.updateLedger(history,historyUpdating);
        holdings.updateRecordedHistory(history,historyUpdating);
        StringBuilder text = new StringBuilder(shown.getHeadline() == null ? "" : shown.getHeadline());
        if (shown.getDetail() != null && !shown.getDetail().isEmpty()) text.append("\n\n").append(shown.getDetail());
        if (shown.getItemId() > 0) text.append("\n\n").append(shown.getItemName());
        if (shown.getPrice() > 0) text.append("\nQuantity: ").append(explainer.formatNumber(shown.getQuantity()))
            .append("\nPrice: ").append(explainer.formatNumber(shown.getPrice())).append(" gp each");
        if (shown.getTargetSellPrice() > 0) text.append("\nTarget sell: ").append(explainer.formatNumber(shown.getTargetSellPrice())).append(" gp");
        if (shown.isActionable() && shown.getPrice() > 0) text.append("\nEstimated after-tax profit: ")
            .append(shown.isProfitKnown() ? explainer.formatGp(shown.getExpectedProfit()) : "Unavailable (unknown acquisition cost)");
        if (shown.getExpectedMinutes() > 0) text.append("\nEstimated duration: ").append(Math.round(shown.getExpectedMinutes())).append(" minutes");
        if (shown.getBreakEvenPrice() > 0) text.append("\nBreak-even sell: ").append(explainer.formatNumber(shown.getBreakEvenPrice())).append(" gp");
        if (shown.getType() == com.flippingfriend.model.SuggestionType.BUY) text.append("\nModel confidence: ").append(Math.round(shown.getConfidence() * 100)).append("%");
        recommendation.setText(text.toString());
        copyQuantity.setEnabled(shown.getQuantity() > 0); copyPrice.setEnabled(shown.getPrice() > 0);
        syncing = true; interval.setSelectedItem(config.checkInterval()); sellOnly.setSelected(engine.isSellOnly()); syncing = false;
        if (dialog != null && dialog.isVisible()) refreshSelected();
    }
    JComponent select(String name) {
        int index = new java.util.ArrayList<>(views.keySet()).indexOf(name);
        if (index < 0) throw new IllegalArgumentException("Unknown details view: " + name);
        tabs.setSelectedIndex(index); refreshSelected(); return tabs;
    }
    JComponent view(String name) { return views.get(name); }
    void open(Component owner, String name) {
        if("Price graph".equals(name) && graph.item()<=0)graph.showItem(shown.getItemId());
        select(name);
        if (presenter != null) { presenter.accept(name, tabs); return; }
        if (dialog == null) {
            dialog = InvestigationWindow.create(SwingUtilities.getWindowAncestor(owner), "Flipping Friend - "+name,tabs);
        }
        InvestigationWindow.show(dialog);
    }
    private void refreshSelected() {
        String name = tabs.getTitleAt(tabs.getSelectedIndex());
        if(dialog!=null)dialog.setTitle("Flipping Friend - "+name);
        switch (name) {
            case "Holdings": holdings.refresh(); break;
            case "Local history": history.refresh(); break;
            case "Plan": plan.refresh(); break;
            case "Market drops": marketDrops.refresh(); break;
            case "Bank estimate": bank.repaint(); break;
            case "Price graph":
                graph.update(shown);if(graph.item()<=0)graph.showItem(shown.getItemId());else graph.refresh();
                break;
            default: break;
        }
    }
    void setPresenter(BiConsumer<String, JComponent> presenter) { this.presenter = presenter; }
    void setGraphItemManager(net.runelite.client.game.ItemManager items){graph.setItemManager(items);}
    void dispose() { graph.dispose();if (dialog != null) { dialog.dispose(); dialog = null; } }
    private void copy(String text) { Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text), null); }
}
