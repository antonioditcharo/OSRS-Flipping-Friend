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
