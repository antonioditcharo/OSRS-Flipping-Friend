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
        assertFalse(tree(panel).stream().anyMatch(c->c instanceof CollapsibleSection || c instanceof OnboardingCard));
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
}
