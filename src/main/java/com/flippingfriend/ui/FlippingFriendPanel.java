package com.flippingfriend.ui;

import com.flippingfriend.CheckInterval;
import com.flippingfriend.FlippingFriendConfig;
import com.flippingfriend.RiskProfile;
import com.flippingfriend.companion.CompanionClient;
import com.flippingfriend.core.AccountLedgerSnapshot;
import com.flippingfriend.data.MarketDataService;
import com.flippingfriend.model.*;
import com.flippingfriend.overlay.StepGuide;
import com.flippingfriend.session.*;
import java.awt.*;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import javax.inject.Inject;
import javax.swing.*;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;

/** Current action and scoped account performance; detailed investigation opens separately. */
public class FlippingFriendPanel extends PluginPanel {
    private final SuggestionEngine engine;
    private final AccountMonitor accountMonitor;
    private final FlippingFriendConfig config;
    private final ConfigManager configManager;
    private final CompanionClient companion;
    private final StepGuide stepGuide;
    private final Explainer explainer;
    private final CompactSuggestionPanel suggestion;
    private final PanelDetails details;
    private final MissedFlipsWindow missedFlips;
    private final SidebarPerformance performance;
    // A logout or expired guide may produce no later engine callback. Refresh the visible action
    // as well as measurements; rendering never clears durable workflow state.
    private final Timer performanceTimer=new Timer(1000,event->refreshOnEdt());
    private final ButtonGroup cadenceGroup = new ButtonGroup(), riskGroup = new ButtonGroup();
    private final Map<CheckInterval, JToggleButton> cadences = new LinkedHashMap<>();
    private final Map<RiskProfile, JToggleButton> risks = new LinkedHashMap<>();
    private final JLabel cadenceCaption = label("How often do you check offers?", false);
    private final JLabel bankroll = label("", false), mode = label("", false);
    private final JButton skip, block, pause, graph;
    private final Icon pauseImage=PanelIcons.icon("pause",UiUtils.TEXT),playImage=PanelIcons.icon("play_arrow",UiUtils.TEXT);
    private final JPopupMenu menu = new JPopupMenu();
    private Runnable onRejection = () -> { };
    private final java.util.concurrent.atomic.AtomicBoolean refreshQueued=new java.util.concurrent.atomic.AtomicBoolean();

    @Inject public FlippingFriendPanel(SuggestionEngine engine, PositionBook positions, TradeJournal journal,
        AccountMonitor accountMonitor, MarketDataService marketData, ItemManager itemManager,
        Explainer explainer, TaxCalculator taxCalculator, FlippingFriendConfig config,
        ConfigManager configManager, CompanionClient companion, StepGuide stepGuide) {
        super(false);
        this.engine = engine; this.accountMonitor = accountMonitor; this.config = config;
        this.configManager = configManager; this.companion = companion; this.stepGuide = stepGuide; this.explainer = explainer;
        suggestion = new CompactSuggestionPanel(itemManager, explainer);
        PositionsPanel holdings = new PositionsPanel(positions, marketData, itemManager, explainer, taxCalculator, engine::getPositionStatuses);
        holdings.setTradingPositions(engine::tradingPositions); holdings.setTradeEvidenceAvailable(engine::hasTradeEvidence);
        details = new PanelDetails(holdings, new HistoryPanel(journal, itemManager, explainer),
            new PortfolioPanel(companion, explainer), new StatsPanel(explainer), new BankTrajectoryPanel(journal, explainer),
            marketData, explainer, engine, config, configManager, companion, this::changed);
        details.setGraphItemManager(itemManager);
        missedFlips = new MissedFlipsWindow(companion);
        setLayout(new BorderLayout()); setBackground(UiUtils.CARD);
        setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
        UiUtils.ScrollableColumn content = new UiUtils.ScrollableColumn();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS)); content.setBackground(UiUtils.CARD);
        JPanel header = row(new BorderLayout());
        header.add(label("Flipping Friend", true), BorderLayout.CENTER);
        JPanel tools = row(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        tools.add(PanelIcons.button("preferences-action", "settings", "Preferences", () -> open("Settings")));
        JButton more = PanelIcons.button("more-action", "more_horiz", "Records and details", () -> { });
        more.setIcon(null); more.setText("Records"); more.setFont(FontManager.getRunescapeSmallFont());
        more.addActionListener(e -> menu.show(more, 0, more.getHeight())); tools.add(more);
        header.add(tools, BorderLayout.EAST); add(content, header, 8);
        add(content, suggestion, 0);
        JPanel actions = row(new FlowLayout(FlowLayout.CENTER, 0, 0));
        graph = PanelIcons.button("graph-action", "show_chart", "Open current item's price graph", () -> open("Price graph"));
        pause = PanelIcons.button("pause-action", "pause", "Pause recommendations", this::togglePause);
        block = PanelIcons.button("block-action", "block", "Block this buy item", () -> reject(true));
        skip = PanelIcons.button("skip-action", "skip_next", "Skip this buy item", () -> reject(false));
        block.setIcon(PanelIcons.icon("block", new Color(225, 100, 100)));
        for (JButton button : new JButton[]{graph,PanelIcons.button("portfolio-action","pie_chart","Open managed holdings",()->open("Holdings")), pause, block, skip}) actions.add(button);
        add(content, actions, 12);
        cadenceCaption.setHorizontalAlignment(SwingConstants.CENTER);
        JPanel cadenceTitle = row(new BorderLayout()); cadenceTitle.add(cadenceCaption); add(content, cadenceTitle, 3);
        JPanel cadenceRow = row(new GridLayout(1, 4, 2, 0));
        CheckInterval[] intervals = {CheckInterval.FIVE_MINUTES, CheckInterval.THIRTY_MINUTES, CheckInterval.TWO_HOURS, CheckInterval.EIGHT_HOURS};
        String[] labels = {"5m", "30m", "2h", "8h"};
        for (int i = 0; i < intervals.length; i++) {
            CheckInterval value = intervals[i]; JToggleButton button = toggle(labels[i], "cadence-" + labels[i]);
            button.setToolTipText(value.getDescription());
            button.addActionListener(e -> { configManager.setConfiguration(FlippingFriendConfig.GROUP, "checkInterval", value); changed(); });
            cadences.put(value, button); cadenceGroup.add(button); cadenceRow.add(button);
        }
        add(content, cadenceRow, 0);
        JPanel riskRow = row(new GridLayout(1, 4, 2, 0)); riskRow.add(label("Risk", false));
        String[] riskLabels = {"Low", "Med", "High"}; int i = 0;
        for (RiskProfile value : RiskProfile.values()) {
            JToggleButton button = toggle(riskLabels[i++], "risk-" + value.name().toLowerCase(java.util.Locale.ROOT));
            button.setToolTipText(value.getDescription());
            button.addActionListener(e -> { configManager.setConfiguration(FlippingFriendConfig.GROUP, "riskProfile", value); changed(); });
            risks.put(value, button); riskGroup.add(button); riskRow.add(button);
        }
        add(content, riskRow, 7); add(content, divider(), 12);
        performance=new SidebarPerformance(companion,explainer,()->open("Performance"),()->open("Holdings"),offer->details.openSale(this,offer),item->details.openItemGraph(this,item));
        add(content,performance,4);
        JPanel footer=row(new BorderLayout());footer.add(performance.footer(),BorderLayout.NORTH);
        JPanel funding=row(new GridLayout(0,1));funding.setBorder(BorderFactory.createMatteBorder(1,0,0,0,UiUtils.DIVIDER));
        funding.add(bankroll);funding.add(mode);footer.add(funding,BorderLayout.SOUTH);add(footer,BorderLayout.SOUTH);
        JScrollPane scroll = new JScrollPane(content, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setBorder(BorderFactory.createEmptyBorder()); scroll.getViewport().setBackground(UiUtils.CARD);
        scroll.getVerticalScrollBar().setUnitIncrement(16); scroll.getVerticalScrollBar().setPreferredSize(new Dimension(8, 0));
        add(scroll);
        for (String name : new String[]{"Recommendation", "Trade recovery", "Price graph", "Plan", "Holdings", "Performance", "Recorded sales", "Local history", "Bank estimate", "Market drops", "Settings"}) {
            JMenuItem entry = new JMenuItem(name); entry.addActionListener(e -> open(name)); menu.add(entry);
        }
        JMenuItem missed = new JMenuItem("Missed flips"); missed.addActionListener(e -> missedFlips.open(SwingUtilities.getWindowAncestor(this))); menu.add(missed);
    }

    private static JPanel divider() {
        JPanel rule = new JPanel(); rule.setBackground(UiUtils.DIVIDER); rule.setPreferredSize(new Dimension(0, 1)); return rule;
    }
    private static JPanel row(LayoutManager layout) {
        JPanel panel = new JPanel(layout); panel.setOpaque(false); return panel;
    }
    private static JLabel label(String text, boolean bold) {
        JLabel label = new JLabel(text) {
            @Override public Dimension getMaximumSize() { return new Dimension(Integer.MAX_VALUE, getPreferredSize().height); }
        }; label.setForeground(bold ? UiUtils.TEXT : UiUtils.MUTED);
        label.setFont(bold ? FontManager.getRunescapeBoldFont() : FontManager.getRunescapeSmallFont()); return label;
    }
    private static JToggleButton toggle(String text, String name) {
        JToggleButton button = new JToggleButton(text); button.setName(name); button.setFont(FontManager.getRunescapeFont());
        button.setUI(new javax.swing.plaf.basic.BasicToggleButtonUI());
        button.setBorder(BorderFactory.createLineBorder(UiUtils.DIVIDER));
        button.setMargin(new Insets(4, 2, 4, 2)); button.setPreferredSize(new Dimension(40, 24)); button.setFocusPainted(true); button.setBackground(UiUtils.CARD);
        button.setForeground(UiUtils.TEXT); button.setOpaque(true); return button;
    }
    private static void add(JPanel column, JComponent child, int gap) {
        if (gap > 0) column.add(Box.createVerticalStrut(gap));
        child.setAlignmentX(LEFT_ALIGNMENT);
        // Text owns its wrapped height; rows remain compact rather than stretching with the viewport.
        if (!(child instanceof JTextArea) && !(child instanceof CompactSuggestionPanel) && !(child instanceof JLabel))
            child.setMaximumSize(new Dimension(Integer.MAX_VALUE, child.getPreferredSize().height));
        column.add(child);
    }
    private void open(String name) { details.open(this, name); }
    @Override public void addNotify(){super.addNotify();performanceTimer.start();}
    @Override public void removeNotify(){performanceTimer.stop();super.removeNotify();}
    private void refreshPerformance() {
        AccountState account=accountMonitor.getState();boolean loggedIn=account!=null && account.isLoggedIn();
        if(loggedIn)companion.requestPerformanceRefresh();
        performance.update(loggedIn?companion.performance():null,loggedIn?companion.lastLedger():null,
            loggedIn,item->details.openItemGraph(this,item));
    }
    private void togglePause() { stepGuide.setPaused(!stepGuide.isPaused()); changed(); }
    private void reject(boolean permanently) {
        Suggestion shown = suggestion.displayed();
        if (shown.getType() != SuggestionType.BUY || stepGuide.isPaused()) return;
        if (permanently) {
            String existing = config.blockedItems() == null ? "" : config.blockedItems().trim();
            String name = shown.getItemName();
            if (!SuggestionEngine.parseBlocked(existing).contains(name.toLowerCase(java.util.Locale.ROOT)))
                configManager.setConfiguration(FlippingFriendConfig.GROUP, "blockedItems", existing.isEmpty() ? name : existing + ", " + name);
        }
        engine.skip(shown.getItemId()); changed();
    }
    private void changed() { companion.clearIncumbent(); refresh(); onRejection.run(); }
    public void setOnRejection(Runnable callback) { onRejection = callback == null ? () -> { } : callback; }
    public void refresh() {
        if(SwingUtilities.isEventDispatchThread()){refreshOnEdt();return;}
        if(refreshQueued.compareAndSet(false,true))SwingUtilities.invokeLater(()->{
            refreshQueued.set(false);refreshOnEdt();
        });
    }
    private void refreshOnEdt() {
        AccountState account = accountMonitor.getState();
        boolean loggedIn=account!=null && account.isLoggedIn();
        Suggestion active = loggedIn?stepGuide.getSuggestion():Suggestion.waiting("Log in to start",
            "Log in and open the Grand Exchange for current account guidance.");
        suggestion.update(active, loggedIn?stepGuide.getPending():null);
        AccountLedgerSnapshot ledger = account != null && account.isLoggedIn() ? companion.lastLedger() : null;
        refreshPerformance();
        AccountLedgerSnapshot history = account != null && account.isLoggedIn() ? companion.historyLedger() : null;
        if(history==null)history=ledger;
        details.update(active,ledger,history,history!=null && ledger!=history);
        details.updateSaleMaintenance(loggedIn);
        missedFlips.refresh(account!=null && account.isLoggedIn());
        boolean buying = active != null && active.getType() == SuggestionType.BUY && !stepGuide.isPaused();
        skip.setEnabled(buying); block.setEnabled(buying); graph.setEnabled(active != null && active.getItemId() > 0);
        pause.setIcon(stepGuide.isPaused()?playImage:pauseImage);
        pause.setToolTipText(stepGuide.isPaused() ? "Resume recommendations" : "Pause recommendations");
        pause.getAccessibleContext().setAccessibleName(pause.getToolTipText());
        if(!cadences.containsKey(config.checkInterval()))cadenceGroup.clearSelection();
        if(!risks.containsKey(config.riskProfile()))riskGroup.clearSelection();
        for (Map.Entry<CheckInterval, JToggleButton> entry : cadences.entrySet()) style(entry.getValue(), entry.getKey() == config.checkInterval());
        for (Map.Entry<RiskProfile, JToggleButton> entry : risks.entrySet()) style(entry.getValue(), entry.getKey() == config.riskProfile());
        cadenceCaption.setText(cadences.containsKey(config.checkInterval()) || config.checkInterval() == null
            ? "How often do you check offers?" : "Checking every " + config.checkInterval().getMinutes() + "m (custom)");
        bankroll.setText(account != null && account.isLoggedIn()
            ? "Budget: " + explainer.formatGp(account.spendableCoins(config.includeBankValue(), config.bankrollCap())) + " · " + account.getFreeSlots() + "/" + account.getTotalSlots() + " free"
            : "Log in to see account guidance");
        String accountMode = account != null && account.isLoggedIn() && account.isFreeToPlay() ? "Free-to-play" : "";
        mode.setText(accountMode + (engine.isSellOnly() ? (accountMode.isEmpty() ? "" : " · ") + "Sell only" : ""));
        bankroll.setToolTipText("Planning budget using bank-value preference, collection coins, live commitments and bankroll cap."); mode.setForeground(UiUtils.WARNING);
        revalidate(); repaint();
    }
    private static void style(JToggleButton button, boolean selected) {
        boolean high=selected && "risk-high".equals(button.getName());
        button.setSelected(selected); button.setForeground(selected && !high ? Color.BLACK : UiUtils.TEXT);
        button.setBackground(high?new Color(255,64,64):selected ? new Color(255, 190, 0) : UiUtils.CARD);
    }
    // Test access uses the same window contents and routing as the live controls, without creating a native window.
    JComponent detailsComponent(String name) { details.select(name); return details.view(name); }
    void setWindowPresenter(BiConsumer<String, JComponent> presenter) { details.setPresenter(presenter); missedFlips.setPresenter(presenter); }
    JPopupMenu detailsMenu() { return menu; }
    public void closeWindows() {
        Runnable close = () -> { performanceTimer.stop();details.dispose(); missedFlips.dispose(); };
        if (SwingUtilities.isEventDispatchThread()) close.run(); else SwingUtilities.invokeLater(close);
    }
}
