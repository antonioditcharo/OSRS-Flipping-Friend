package com.flippingfriend.overlay;

import com.flippingfriend.FlippingFriendConfig;
import com.flippingfriend.model.Suggestion;
import com.flippingfriend.model.SuggestionType;
import net.runelite.api.*;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.widgets.Widget;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/** Merge-preview regression: the player-selected quote, rather than background buy advice, is checked. */
public class ManualSaleConfirmationIntegrationTest {
    @Test public void playerKeyFillsTheSelectedPersonalSaleButConfirmWaitsForManualAcceptance() {
        Client client=mock(Client.class); GeWidgetResolver resolver=mock(GeWidgetResolver.class);
        FlippingFriendConfig config=mock(FlippingFriendConfig.class);
        net.runelite.client.callback.ClientThread thread=mock(net.runelite.client.callback.ClientThread.class);
        net.runelite.client.config.ConfigManager profiles=mock(net.runelite.client.config.ConfigManager.class);
        net.runelite.client.input.KeyManager keys=mock(net.runelite.client.input.KeyManager.class);
        java.awt.Canvas canvas=mock(java.awt.Canvas.class);
        when(client.isClientThread()).thenReturn(true); when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
        when(client.getCanvas()).thenReturn(canvas); when(canvas.hasFocus()).thenReturn(true);
        when(profiles.getRSProfileKey()).thenReturn("synthetic-profile");
        when(config.fillRecommendedInputKey()).thenReturn(new net.runelite.client.config.Keybind(java.awt.event.KeyEvent.VK_E,0));
        when(config.protectTradeConfirmation()).thenReturn(true); when(resolver.isSetupOpen()).thenReturn(true);
        when(client.getVarpValue(VarPlayerID.TRADINGPOST_SEARCH)).thenReturn(561);
        when(client.getVarbitValue(VarbitID.GE_NEWOFFER_TYPE)).thenReturn(1);
        when(client.getVarbitValue(VarbitID.GE_SELECTEDSLOT)).thenReturn(1);
        when(client.getVarbitValue(VarbitID.GE_NEWOFFER_QUANTITY)).thenReturn(100);
        when(client.getVarpLongValue(GeEditorValues.PRICE_VARP)).thenReturn(118L);
        when(client.getVarcIntValue(VarClientInt.INPUT_TYPE)).thenReturn(30);
        Widget title=mock(Widget.class), input=mock(Widget.class), button=mock(Widget.class);
        when(title.getText()).thenReturn("Set a price for each item:");
        when(client.getWidget(net.runelite.api.gameval.InterfaceID.Chatbox.MES_TEXT)).thenReturn(title);
        when(client.getWidget(net.runelite.api.gameval.InterfaceID.Chatbox.MES_TEXT2)).thenReturn(input);
        when(resolver.getConfirmButton()).thenReturn(button);
        StepGuide guide=new StepGuide(client,resolver);
        guide.setSuggestion(Suggestion.builder(SuggestionType.BUY).item(2,"Other buy").quantity(10).price(100).build());
        Suggestion quote=Suggestion.builder(SuggestionType.SELL).item(561,"Nature rune").quantity(100).price(119).profitKnown(false).build();
        guide.setEditorQuote(quote);
        doAnswer(call->{((Runnable)call.getArgument(0)).run(); return null;}).when(thread).invokeLater(any(Runnable.class));
        GeInputAssist assist=new GeInputAssist(client,thread,keys,profiles,config,guide,resolver);
        assist.start(); assist.refresh(true);
        java.awt.event.KeyEvent key=mock(java.awt.event.KeyEvent.class);
        when(key.getID()).thenReturn(java.awt.event.KeyEvent.KEY_PRESSED);
        when(key.getKeyCode()).thenReturn(java.awt.event.KeyEvent.VK_E);
        when(key.getExtendedKeyCode()).thenReturn(java.awt.event.KeyEvent.VK_E);
        assist.keyPressed(key);
        verify(client).setVarcStrValue(VarClientStr.INPUT_TEXT,"119"); verify(input).setText("119*");
        assertSame(quote,guide.getSuggestion()); assertFalse(quote.isProfitKnown()); assertFalse(guide.isPriceDone());
        MenuEntry entry=mock(MenuEntry.class); when(entry.getOption()).thenReturn("Confirm");
        when(entry.getType()).thenReturn(MenuAction.CC_OP); when(entry.getWidget()).thenReturn(button);
        TradeConfirmGuard guard=new TradeConfirmGuard(client,guide,resolver,config);
        MenuOptionClicked beforeEnter=new MenuOptionClicked(entry); guard.onMenuOptionClicked(beforeEnter); assertTrue(beforeEnter.isConsumed());
        // Copied game observations after the player accepts the filled prompt; no script/key submission.
        when(client.getVarpLongValue(GeEditorValues.PRICE_VARP)).thenReturn(119L);
        when(client.getVarcIntValue(VarClientInt.INPUT_TYPE)).thenReturn(0);
        MenuOptionClicked afterEnter=new MenuOptionClicked(entry); guard.onMenuOptionClicked(afterEnter); assertFalse(afterEnter.isConsumed());
        verify(client,never()).runScript(anyInt(),any());
    }
    @Test public void selectedSaleUsesActualValuesAndNewQuoteRechecksConfirmation() {
        Client client=mock(Client.class); GeWidgetResolver resolver=mock(GeWidgetResolver.class);
        FlippingFriendConfig config=mock(FlippingFriendConfig.class);
        when(config.protectTradeConfirmation()).thenReturn(true);
        when(client.isClientThread()).thenReturn(true); when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
        when(resolver.isSetupOpen()).thenReturn(true);
        when(client.getVarpValue(VarPlayerID.TRADINGPOST_SEARCH)).thenReturn(561);
        when(client.getVarbitValue(VarbitID.GE_NEWOFFER_TYPE)).thenReturn(1);
        when(client.getVarbitValue(VarbitID.GE_NEWOFFER_QUANTITY)).thenReturn(100);
        when(client.getVarbitValue(VarbitID.GE_SELECTEDSLOT)).thenReturn(1);
        when(client.getVarpLongValue(GeEditorValues.PRICE_VARP)).thenReturn(119L);
        Widget button=mock(Widget.class); when(resolver.getConfirmButton()).thenReturn(button);
        MenuEntry entry=mock(MenuEntry.class);
        when(entry.getOption()).thenReturn("Confirm"); when(entry.getType()).thenReturn(MenuAction.CC_OP);
        when(entry.getWidget()).thenReturn(button);
        StepGuide guide=new StepGuide(client,resolver);
        guide.setSuggestion(Suggestion.builder(SuggestionType.BUY).item(2,"Background buy").quantity(10).price(100).build());
        Suggestion quote=Suggestion.builder(SuggestionType.SELL).item(561,"Nature rune").quantity(100)
            .price(119).profitKnown(false).build();
        guide.setEditorQuote(quote);
        TradeConfirmGuard guard=new TradeConfirmGuard(client,guide,resolver,config);
        MenuOptionClicked matching=new MenuOptionClicked(entry); guard.onMenuOptionClicked(matching);
        assertFalse(matching.isConsumed()); assertTrue(guide.isQuantityDone()); assertTrue(guide.isPriceDone());
        assertFalse(guide.getSuggestion().isProfitKnown());
        guide.setEditorQuote(Suggestion.builder(SuggestionType.SELL).item(561,"Nature rune").quantity(100)
            .price(120).profitKnown(false).build());
        MenuOptionClicked changed=new MenuOptionClicked(entry); guard.onMenuOptionClicked(changed);
        assertTrue(changed.isConsumed()); assertFalse(guide.isPriceDone());
        guide.reset(); assertFalse(GeEditorValues.isOffer(guide.getSuggestion()));
        MenuOptionClicked afterLogout=new MenuOptionClicked(entry); guard.onMenuOptionClicked(afterLogout);
        assertFalse(afterLogout.isConsumed());
    }
}
