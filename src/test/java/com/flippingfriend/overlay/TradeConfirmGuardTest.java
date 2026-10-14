package com.flippingfriend.overlay;

import com.flippingfriend.FlippingFriendConfig;
import com.flippingfriend.model.*;
import net.runelite.api.*;
import net.runelite.api.events.*;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.widgets.Widget;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class TradeConfirmGuardTest {
    private final Client client=mock(Client.class);
    private final GeWidgetResolver resolver=mock(GeWidgetResolver.class);
    private final FlippingFriendConfig config=mock(FlippingFriendConfig.class);
    private final Widget button=mock(Widget.class);
    private final MenuEntry confirm=mock(MenuEntry.class,RETURNS_SELF);
    private final Menu menu=mock(Menu.class);
    private final MenuEntry locked=mock(MenuEntry.class,RETURNS_SELF);
    private StepGuide guide;
    private TradeConfirmGuard guard;
    @Before public void setup() {
        when(client.isClientThread()).thenReturn(true); when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
        when(resolver.isSetupOpen()).thenReturn(true); when(resolver.getConfirmButton()).thenReturn(button);
        when(client.getVarpValue(VarPlayerID.TRADINGPOST_SEARCH)).thenReturn(561);
        when(client.getVarbitValue(VarbitID.GE_NEWOFFER_TYPE)).thenReturn(0);
        when(client.getVarbitValue(VarbitID.GE_NEWOFFER_QUANTITY)).thenReturn(10);
        when(client.getVarbitValue(VarbitID.GE_SELECTEDSLOT)).thenReturn(1);
        when(client.getVarpLongValue(GeEditorValues.PRICE_VARP)).thenReturn(99L);
        when(config.protectTradeConfirmation()).thenReturn(true);
        when(confirm.getOption()).thenReturn("Confirm"); when(confirm.getType()).thenReturn(MenuAction.CC_OP);
        when(confirm.getWidget()).thenReturn(button); when(button.getId()).thenReturn(465<<16|26);
        when(button.getIndex()).thenReturn(58);
        when(confirm.getParam0()).thenReturn(58); when(confirm.getParam1()).thenReturn(465<<16|26);
        when(confirm.getIdentifier()).thenReturn(1);
        when(client.getMenu()).thenReturn(menu); when(menu.getMenuEntries()).thenReturn(new MenuEntry[]{confirm});
        when(menu.createMenuEntry(-1)).thenReturn(locked);
        guide=new StepGuide(client,resolver); guide.setSuggestion(buy());
        guard=new TradeConfirmGuard(client,guide,resolver,config);
    }
    private Suggestion buy() { return Suggestion.builder(SuggestionType.BUY).item(561,"Nature rune").price(100).quantity(10).build(); }
    private MenuEntryAdded added() { MenuEntryAdded e=mock(MenuEntryAdded.class); when(e.getMenuEntry()).thenReturn(confirm); return e; }
    private MenuOptionClicked click() { return new MenuOptionClicked(confirm); }
    private void open() { MenuOpened event = new MenuOpened(); event.setMenuEntries(new MenuEntry[]{confirm}); guard.onMenuOpened(event); }
    @Test public void mismatchLocksNormalClickWithoutRewritingNativeConfirm() {
        guard.onMenuEntryAdded(added());
        verify(confirm).setDeprioritized(true); verify(locked).setType(MenuAction.RUNELITE);
        verify(confirm,never()).setOption(anyString()); verify(confirm,never()).setType(any());
        verify(confirm,never()).setIdentifier(anyInt()); verify(confirm,never()).setParam0(anyInt()); verify(confirm,never()).setParam1(anyInt());
        MenuOptionClicked click=click(); guard.onMenuOptionClicked(click); assertTrue(click.isConsumed());
    }
    @Test public void actualMatchingValuesReleaseNormalConfirmImmediately() {
        when(client.getVarpLongValue(GeEditorValues.PRICE_VARP)).thenReturn(100L);
        guard.onMenuEntryAdded(added()); verify(menu,never()).createMenuEntry(anyInt());
        MenuOptionClicked click=click(); guard.onMenuOptionClicked(click); assertFalse(click.isConsumed());
    }
    @Test public void rightClickNativeConfirmOverridesMismatchOnceWithoutAutomatingAnything() {
        open(); MenuOptionClicked click=click(); guard.onMenuOptionClicked(click); assertFalse(click.isConsumed());
        verifyNoInteractions(menu); // No scripted submission, callbacks or rewritten native operation.
        MenuOptionClicked second=click(); guard.onMenuOptionClicked(second); assertTrue(second.isConsumed());
    }
    @Test public void rightClickAllowsPersonalItemAndSideWithoutClaimingRecommendationCompletion() {
        when(client.getVarpValue(VarPlayerID.TRADINGPOST_SEARCH)).thenReturn(4151);
        when(client.getVarbitValue(VarbitID.GE_NEWOFFER_TYPE)).thenReturn(1);
        when(client.getVarbitValue(VarbitID.GE_NEWOFFER_QUANTITY)).thenReturn(1);
        open(); MenuOptionClicked click=click(); guard.onMenuOptionClicked(click); assertFalse(click.isConsumed());
        assertFalse(guide.isQuantityDone()); assertFalse(guide.isPriceDone());
    }
    @Test public void changingItemSideOrSlotInvalidatesOpenedMenuOverride() {
        open(); when(client.getVarpValue(VarPlayerID.TRADINGPOST_SEARCH)).thenReturn(4151);
        MenuOptionClicked click=click(); guard.onMenuOptionClicked(click); assertTrue(click.isConsumed());
        when(client.getVarpValue(VarPlayerID.TRADINGPOST_SEARCH)).thenReturn(561); open();
        when(client.getVarbitValue(VarbitID.GE_NEWOFFER_TYPE)).thenReturn(1);
        click=click(); guard.onMenuOptionClicked(click); assertTrue(click.isConsumed());
        open(); when(client.getVarbitValue(VarbitID.GE_SELECTEDSLOT)).thenReturn(2);
        click=click(); guard.onMenuOptionClicked(click); assertTrue(click.isConsumed());
    }
    @Test public void closingMenuOrResettingSessionInvalidatesOverride() {
        open(); when(client.isMenuOpen()).thenReturn(false); guard.beforeRender();
        MenuOptionClicked click=click(); guard.onMenuOptionClicked(click); assertTrue(click.isConsumed());
        open(); guard.reset(); click=click(); guard.onMenuOptionClicked(click); assertTrue(click.isConsumed());
    }
    @Test public void unrelatedConfirmWithSharedParentCannotBeIntercepted() {
        when(confirm.getWidget()).thenReturn(null); when(confirm.getParam0()).thenReturn(51);
        guard.onMenuEntryAdded(added()); verifyNoInteractions(menu);
        MenuOptionClicked click=click(); guard.onMenuOptionClicked(click); assertFalse(click.isConsumed());
    }
    @Test public void unavailableEditorNoRecommendationAndDisabledProtectionDoNotLockPersonalTrading() {
        when(resolver.isSetupOpen()).thenReturn(false); guard.onMenuEntryAdded(added()); verifyNoInteractions(menu);
        when(resolver.isSetupOpen()).thenReturn(true); guide.reset(); guard.onMenuEntryAdded(added()); verifyNoInteractions(menu);
        guide.setSuggestion(buy()); when(config.protectTradeConfirmation()).thenReturn(false);
        MenuOptionClicked click=click(); guard.onMenuOptionClicked(click); assertFalse(click.isConsumed());
        guard.onMenuEntryAdded(added()); verifyNoInteractions(menu);
    }
    @Test public void openChatboxAndLongPriceCannotUnlockNormalConfirm() {
        when(client.getVarpLongValue(GeEditorValues.PRICE_VARP)).thenReturn((1L<<32)+100);
        MenuOptionClicked click=click(); guard.onMenuOptionClicked(click); assertTrue(click.isConsumed());
        when(client.getVarpLongValue(GeEditorValues.PRICE_VARP)).thenReturn(100L);
        when(client.getVarcIntValue(VarClientInt.INPUT_TYPE)).thenReturn(7);
        click=click(); guard.onMenuOptionClicked(click); assertTrue(click.isConsumed());
    }
    @Test public void ownLockedMenuActionIsConsumedAndCannotBecomeVanillaInput() {
        when(locked.getOption()).thenReturn(TradeConfirmGuard.LOCKED); when(locked.getType()).thenReturn(MenuAction.RUNELITE);
        MenuOptionClicked click=new MenuOptionClicked(locked); guard.onMenuOptionClicked(click); assertTrue(click.isConsumed());
    }
    @Test public void closedOrUnauthenticatedClientNeverChangesMenu() {
        when(client.getGameState()).thenReturn(GameState.LOGIN_SCREEN);
        guard.onMenuEntryAdded(added()); verifyNoInteractions(menu);
        when(client.getGameState()).thenReturn(GameState.LOGGED_IN); when(client.isClientThread()).thenReturn(false);
        guard.onMenuEntryAdded(added()); verifyNoInteractions(menu);
    }
}
