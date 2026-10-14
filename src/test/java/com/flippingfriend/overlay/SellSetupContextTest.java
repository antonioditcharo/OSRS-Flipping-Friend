package com.flippingfriend.overlay;
import com.flippingfriend.model.*;
import net.runelite.api.Client;
import net.runelite.api.gameval.*;
import net.runelite.api.widgets.Widget;
import net.runelite.client.game.ItemManager;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
public class SellSetupContextTest {
    @Test public void quoteContextRequiresActualSellEditorAndSelectedQuantity() {
        Client client=mock(Client.class); ItemManager items=mock(ItemManager.class);
        Widget setup=mock(Widget.class); when(client.getWidget(InterfaceID.GeOffers.SETUP)).thenReturn(setup);
        when(items.canonicalize(561)).thenReturn(561);
        when(client.getVarpValue(VarPlayerID.TRADINGPOST_SEARCH)).thenReturn(561);
        when(client.getVarbitValue(VarbitID.GE_NEWOFFER_QUANTITY)).thenReturn(100);
        var resolver=new GeWidgetResolver(client,items);
        assertNull(resolver.sellSetup());
        when(client.getVarbitValue(VarbitID.GE_NEWOFFER_TYPE)).thenReturn(1);
        assertArrayEquals(new int[]{561,100},resolver.sellSetup());
        when(setup.isHidden()).thenReturn(true); assertNull(resolver.sellSetup());
    }
    @Test public void playerChosenSaleTemporarilyOverridesBuyAdviceAndClosingRestoresIt() {
        Client client=mock(Client.class); when(client.isClientThread()).thenReturn(true);
        var resolver=mock(GeWidgetResolver.class);
        when(resolver.isSetupOpen()).thenReturn(true);
        when(client.getGameState()).thenReturn(net.runelite.api.GameState.LOGGED_IN);
        when(client.getVarpValue(VarPlayerID.TRADINGPOST_SEARCH)).thenReturn(561);
        when(client.getVarbitValue(VarbitID.GE_NEWOFFER_TYPE)).thenReturn(1);
        when(client.getVarbitValue(VarbitID.GE_NEWOFFER_QUANTITY)).thenReturn(100);
        when(client.getVarbitValue(VarbitID.GE_SELECTEDSLOT)).thenReturn(1);
        when(client.getVarpLongValue(GeEditorValues.PRICE_VARP)).thenReturn(118L);
        var guide=new StepGuide(client,resolver);
        var buy=Suggestion.builder(SuggestionType.BUY).item(2,"Other item").quantity(10).price(100).build();
        guide.setSuggestion(buy);
        var quote=Suggestion.builder(SuggestionType.SELL).item(561,"Nature rune").quantity(100).price(119).profitKnown(false).build();
        guide.setEditorQuote(quote); assertSame(quote,guide.getSuggestion());
        assertTrue(guide.isQuantityDone()); assertFalse(guide.isPriceDone());
        when(client.getVarbitValue(VarbitID.GE_NEWOFFER_QUANTITY)).thenReturn(99);
        assertFalse(guide.isQuantityDone());
        guide.onSetupClosed(); assertSame(buy,guide.getSuggestion());
    }
}
