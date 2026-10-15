package com.flippingfriend.ui;

import com.flippingfriend.*;
import com.flippingfriend.companion.CompanionClient;
import com.flippingfriend.core.*;
import com.flippingfriend.data.MarketDataService;
import com.flippingfriend.model.*;
import com.flippingfriend.overlay.StepGuide;
import com.flippingfriend.session.*;
import java.awt.Component;
import java.awt.Container;
import java.util.*;
import javax.swing.*;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.game.ItemManager;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/** Exercises the installed panel class and real controls on the EDT, with synthetic model data. */
public class CompactPanelContractTest {
    final SuggestionEngine engine=mock(SuggestionEngine.class);
    final PositionBook positions=mock(PositionBook.class);
    final TradeJournal journal=mock(TradeJournal.class);
    final AccountMonitor monitor=mock(AccountMonitor.class);
    final MarketDataService market=mock(MarketDataService.class);
    final ItemManager items=mock(ItemManager.class);
    final FlippingFriendConfig config=mock(FlippingFriendConfig.class);
    final ConfigManager settings=mock(ConfigManager.class);
    final CompanionClient companion=mock(CompanionClient.class);
    final StepGuide guide=mock(StepGuide.class);
    FlippingFriendPanel panel;
    Suggestion displayed=Suggestion.builder(SuggestionType.BUY).item(561,"Nature rune").quantity(10).price(100).build();
    @Before public void setup() throws Exception {
        when(config.riskProfile()).thenReturn(RiskProfile.MODERATE); when(config.checkInterval()).thenReturn(CheckInterval.FIVE_MINUTES);
        when(monitor.getState()).thenReturn(new AccountState(true,true,false,100_000_000L,0,0,8,8,0,true,
            Collections.emptyMap(),Collections.emptyMap(),Collections.emptyMap()));
        when(guide.getSuggestion()).thenReturn(displayed);
        when(engine.getCurrent()).thenReturn(Suggestion.builder(SuggestionType.BUY).item(4151,"Other item").quantity(1).price(1000).build());
        when(market.getSnapshot()).thenReturn(com.flippingfriend.data.MarketSnapshot.empty());
        SwingUtilities.invokeAndWait(()->panel=new FlippingFriendPanel(engine,positions,journal,monitor,market,items,
            new Explainer(),new TaxCalculator(),config,settings,companion,guide));
        refresh();
    }
    void refresh() throws Exception { SwingUtilities.invokeAndWait(panel::refresh); SwingUtilities.invokeAndWait(()->{}); }
    static java.util.List<Component> tree(Component c) {
        java.util.List<Component> all=new ArrayList<>(); all.add(c);
        if(c instanceof Container) for(Component child:((Container)c).getComponents()) all.addAll(tree(child)); return all;
    }
    AbstractButton button(String name) {
        return tree(panel).stream().filter(c->c instanceof AbstractButton && name.equals(c.getName()))
            .map(c->(AbstractButton)c).findFirst().orElseThrow(()->new AssertionError("Missing connected control: "+name));
    }
    @Test public void sidebarIsAnActionSurfaceRatherThanAStackOfOldSections() {
        assertFalse(tree(panel).stream().anyMatch(c->c.getClass().getSimpleName().equals("CollapsibleSection") || c.getClass().getSimpleName().equals("OnboardingCard")));
        assertTrue("Compact action must be present",tree(panel).stream().anyMatch(c->"suggested-action".equals(c.getName())));
    }
    @Test public void skipRejectsTheDisplayedGuideItemAndRefreshesThroughThePlugin() throws Exception {
        Runnable refresh=mock(Runnable.class); panel.setOnRejection(refresh);
        SwingUtilities.invokeAndWait(()->button("skip-action").doClick());
        verify(engine).skip(561); verify(engine,never()).skip(4151); verify(refresh).run();
        verify(engine,never()).refresh(anyBoolean());
    }
    @Test public void blockPersistsTheDisplayedItemWithoutBlockingAnotherEngineItem() throws Exception {
        when(config.blockedItems()).thenReturn("Steel bar");
        SwingUtilities.invokeAndWait(()->button("block-action").doClick());
        verify(settings).setConfiguration(FlippingFriendConfig.GROUP,"blockedItems","Steel bar, Nature rune");
        verify(engine).skip(561); verify(engine,never()).skip(4151);
    }
    @Test public void allFourCadenceControlsPublishTheirActualIntervals() throws Exception {
        String[] labels={"5m","30m","2h","8h"}; int[] minutes={5,30,120,480};
        for(int i=0;i<labels.length;i++) {
            final String name="cadence-"+labels[i]; SwingUtilities.invokeAndWait(()->button(name).doClick());
            CheckInterval selected=Arrays.stream(CheckInterval.values()).filter(v->v.getMinutes()==minutes[Arrays.asList(labels).indexOf(name.substring(8))]).findFirst().orElseThrow();
            verify(settings).setConfiguration(FlippingFriendConfig.GROUP,"checkInterval",selected);
        }
    }
    @Test public void riskControlsKeepTheExistingConfigurationContract() throws Exception {
        SwingUtilities.invokeAndWait(()->button("risk-high").doClick());
        verify(settings).setConfiguration(FlippingFriendConfig.GROUP,"riskProfile",RiskProfile.HIGH);
    }
    @Test public void compactProfitRemainsDurableAndNeverFallsBackToJournalNumbers() throws Exception {
        when(companion.lastLedger()).thenReturn(new AccountLedgerSnapshot(AccountIdentity.fromProfileKey("synthetic"),
            java.time.Instant.now().getEpochSecond(),1,1,0,1,-11,-10,2,3,Collections.emptyList()));
        refresh();
        assertTrue(tree(panel).stream().filter(c->c instanceof JTextArea).map(c->((JTextArea)c).getText()).anyMatch(t->t.contains("-11")&&t.contains("-10")));
        verify(journal,never()).sessionStats(); verify(journal,never()).lifetimeStats();
    }
    private String text(Component view) {
        return tree(view).stream().map(c -> c instanceof JTextArea ? ((JTextArea)c).getText()
            : c instanceof JLabel ? ((JLabel)c).getText() : "").collect(java.util.stream.Collectors.joining("\n"));
    }
    @Test public void graphPreferencesAndEveryRecordsMenuRouteToRealViews() throws Exception {
        java.util.List<String> opened=new ArrayList<>();
        panel.setWindowPresenter((name,content)->{opened.add(name); assertNotNull(content);});
        SwingUtilities.invokeAndWait(()->{
            button("graph-action").doClick(); button("preferences-action").doClick(); button("performance-action").doClick();
            for(Component entry:panel.detailsMenu().getComponents()) ((JMenuItem)entry).doClick();
        });
        assertEquals(Arrays.asList("Price graph","Settings","Performance","Recommendation","Price graph","Plan","Holdings",
            "Performance","Local history","Bank estimate","Settings","Missed flips"),opened);
        verify(market,atLeastOnce()).getSeries(561,"1h"); verify(market,never()).getSeries(4151,"1h");
    }
    @Test public void controlsDisableForNonBuyAdviceAndFollowGuideCorrections() throws Exception {
        when(guide.getSuggestion()).thenReturn(Suggestion.builder(SuggestionType.SELL).item(2361,"Adamant bar").quantity(5).price(222).profitKnown(false).build());
        refresh(); assertFalse(button("skip-action").isEnabled()); assertFalse(button("block-action").isEnabled());
        assertTrue(text(panel).contains("222 gp each"));
        assertFalse(text(panel).contains("Nature rune"));
        SwingUtilities.invokeAndWait(()->{ button("skip-action").doClick(); button("block-action").doClick(); });
        verify(engine,never()).skip(anyInt());
        Component[] detail=new Component[1]; SwingUtilities.invokeAndWait(()->detail[0]=panel.detailsComponent("Recommendation"));
        assertTrue(text(detail[0]).contains("Unavailable (unknown acquisition cost)"));
        when(guide.getSuggestion()).thenReturn(Suggestion.idle()); refresh(); assertFalse(button("graph-action").isEnabled());
    }
    @Test public void oldCustomIntervalsAndExternalConfigUpdatesStayVisible() throws Exception {
        when(config.checkInterval()).thenReturn(CheckInterval.OCCASIONAL); when(config.riskProfile()).thenReturn(RiskProfile.LOW);
        refresh(); assertTrue(text(panel).contains("180m (custom)")); assertTrue(button("risk-low").isSelected());
        for(String cadence:Arrays.asList("5m","30m","2h","8h")) assertFalse(button("cadence-"+cadence).isSelected());
        when(config.checkInterval()).thenReturn(CheckInterval.EIGHT_HOURS); refresh(); assertTrue(button("cadence-8h").isSelected());
        Component[] detail=new Component[1]; SwingUtilities.invokeAndWait(()->detail[0]=panel.detailsComponent("Settings"));
        JComboBox<?> intervals=tree(detail[0]).stream().filter(c->c instanceof JComboBox).map(c->(JComboBox<?>)c).findFirst().orElseThrow();
        assertEquals(CheckInterval.EIGHT_HOURS,intervals.getSelectedItem());
        SwingUtilities.invokeAndWait(()->intervals.setSelectedItem(CheckInterval.HOURLY));
        verify(settings).setConfiguration(FlippingFriendConfig.GROUP,"checkInterval",CheckInterval.HOURLY);
    }
    @Test public void sellOnlyAndPauseControlsCallTheExistingGuidancePaths() throws Exception {
        Runnable callback=mock(Runnable.class); panel.setOnRejection(callback);
        Component[] detail=new Component[1]; SwingUtilities.invokeAndWait(()->detail[0]=panel.detailsComponent("Settings"));
        JCheckBox finish=tree(detail[0]).stream().filter(c->c instanceof JCheckBox).map(c->(JCheckBox)c).findFirst().orElseThrow();
        SwingUtilities.invokeAndWait(finish::doClick); verify(engine).setSellOnly(true); verify(callback).run();
        SwingUtilities.invokeAndWait(()->button("pause-action").doClick()); verify(guide).setPaused(true); verify(callback,times(2)).run();
        when(guide.isPaused()).thenReturn(true); when(guide.getSuggestion()).thenReturn(Suggestion.waiting("Recommendations paused",""));
        refresh(); assertFalse(button("skip-action").isEnabled());
        assertEquals("Resume recommendations",button("pause-action").getToolTipText());
        SwingUtilities.invokeAndWait(()->button("pause-action").doClick()); verify(guide).setPaused(false);
    }
    @Test public void ledgerLossAndAccountExitClearProfitWithoutJournalFallback() throws Exception {
        when(companion.lastLedger()).thenReturn(new AccountLedgerSnapshot(AccountIdentity.fromProfileKey("synthetic"),
            java.time.Instant.now().getEpochSecond(),1,0,0,0,0,0,0,0,Collections.emptyList())); refresh();
        assertTrue(text(panel).contains("Unresolved")); assertTrue(text(panel).contains("Known recorded profit"));
        when(companion.lastLedger()).thenReturn(null); refresh(); assertTrue(text(panel).contains("Unavailable"));
        when(monitor.getState()).thenReturn(AccountState.loggedOut()); refresh();
        assertTrue(text(panel).contains("Log in to see account guidance"));
        verify(journal,never()).sessionStats(); verify(journal,never()).lifetimeStats();
    }
    @Test public void narrowSidebarKeepsAccountAndCoverageVisibleRatherThanAtZeroHeight() throws Exception {
        SwingUtilities.invokeAndWait(()->{
            panel.setSize(225,499); layout(panel); layout(panel);
            for(Component c:tree(panel)) if(c instanceof JLabel && ((JLabel)c).getText()!=null && !((JLabel)c).getText().isEmpty()) {
                assertTrue(((JLabel)c).getText()+" is collapsed",c.getHeight()>0);
                assertTrue(((JLabel)c).getText()+" is cut off",c.getFontMetrics(c.getFont()).stringWidth(((JLabel)c).getText())<=c.getWidth());
            }
        });
    }
    static void layout(Component c) { c.doLayout(); if(c instanceof Container) for(Component child:((Container)c).getComponents()) layout(child); }
    @Test public void openedSettingsControlsShareTheSameLeftEdge() throws Exception {
        SwingUtilities.invokeAndWait(()->{
            JComponent view=panel.detailsComponent("Settings"); view.setSize(730,250); layout(view);
            for(Component c:view.getComponents()) if(c instanceof JTextArea || c instanceof JComboBox || c instanceof JCheckBox)
                assertEquals("Settings content should align with its 12px inset",12,c.getX());
        });
    }
    @Test public void budgetKeepsBankCollectionAndCapConnectionsFromTheOldHeader() throws Exception {
        when(monitor.getState()).thenReturn(new AccountState(true,true,false,10_000_000,50_000_000,5_000_000,8,8,0,true,
            Collections.emptyMap(),Collections.emptyMap(),Collections.emptyMap()));
        when(config.includeBankValue()).thenReturn(true); when(config.bankrollCap()).thenReturn(40_000_000L);
        refresh(); assertTrue("Show the same capped budget sent to the planner",text(panel).contains("Budget: 40m gp"));
        when(config.includeBankValue()).thenReturn(false); refresh(); assertTrue(text(panel).contains("Budget: 15m gp"));
        when(monitor.getState()).thenReturn(new AccountState(true,false,false,10_000_000,50_000_000,5_000_000,3,3,0,true,
            Collections.emptyMap(),Collections.emptyMap(),Collections.emptyMap()));
        refresh(); assertTrue("Preserve the old account-mode indication",text(panel).contains("Free-to-play"));
    }
}
