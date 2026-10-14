package com.flippingfriend.overlay;

import com.flippingfriend.model.Suggestion;
import com.flippingfriend.model.SuggestionType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.widgets.Widget;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/** Progress is established by the committed editor values, never by opening an input control. */
public class TradeWalkthroughValuesTest {
    private final Client client = mock(Client.class);
    private final GeWidgetResolver resolver = mock(GeWidgetResolver.class);
    private final Widget quantity = mock(Widget.class), price = mock(Widget.class), confirm = mock(Widget.class);
    private StepGuide guide;
    @Before public void setup() {
        when(client.isClientThread()).thenReturn(true);
        when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
        when(resolver.isGeOpen()).thenReturn(true); when(resolver.isSetupOpen()).thenReturn(true);
        when(resolver.getQuantityControl()).thenReturn(quantity); when(resolver.getPriceControl()).thenReturn(price);
        when(resolver.getConfirmButton()).thenReturn(confirm);
        when(client.getVarpValue(VarPlayerID.TRADINGPOST_SEARCH)).thenReturn(561);
        when(client.getVarbitValue(VarbitID.GE_NEWOFFER_TYPE)).thenReturn(0);
        when(client.getVarbitValue(VarbitID.GE_SELECTEDSLOT)).thenReturn(1);
        when(client.getVarbitValue(VarbitID.GE_NEWOFFER_QUANTITY)).thenReturn(10);
        when(client.getVarpLongValue(5753)).thenReturn(100L);
        guide = new StepGuide(client,resolver);
        guide.setSuggestion(Suggestion.builder(SuggestionType.BUY).item(561,"Nature rune").quantity(10).price(100).build());
    }
    private StepGuide.GuideState resolve() { return guide.resolve(8,new boolean[8]); }
    private void click(Widget widget, String option) {
        MenuOptionClicked event=mock(MenuOptionClicked.class);
        when(event.getWidget()).thenReturn(widget); when(event.getMenuOption()).thenReturn(option);
        guide.onMenuOptionClicked(event);
    }
    @Test public void matchingLiveValuesHighlightConfirmWithoutAnyRememberedClicks() {
        assertEquals(GuideStep.CONFIRM,resolve().getStep());
        assertEquals(java.util.List.of(confirm),resolve().getTargets());
    }
    @Test public void openingQuantityDoesNotCompleteAnIncorrectQuantity() {
        when(client.getVarbitValue(VarbitID.GE_NEWOFFER_QUANTITY)).thenReturn(9);
        click(quantity,"Enter quantity");
        assertEquals(GuideStep.SET_QUANTITY,resolve().getStep());
        assertTrue(resolve().getTargets().contains(quantity));
    }
    @Test public void bothIncorrectFieldsRemainHighlightedTogether() {
        when(client.getVarbitValue(VarbitID.GE_NEWOFFER_QUANTITY)).thenReturn(9);
        when(client.getVarpLongValue(5753)).thenReturn(99L);
        assertTrue(resolve().getTargets().contains(quantity));
        assertTrue(resolve().getTargets().contains(price));
        assertFalse(resolve().getTargets().contains(confirm));
    }
    @Test public void editingBackToAWrongValueRestoresThatHighlight() {
        click(quantity,"Enter quantity"); click(price,"Enter price");
        assertEquals(GuideStep.CONFIRM,resolve().getStep());
        when(client.getVarbitValue(VarbitID.GE_NEWOFFER_QUANTITY)).thenReturn(9);
        assertTrue(resolve().getTargets().contains(quantity));
        assertFalse(resolve().getTargets().contains(confirm));
    }
    @Test public void longPriceCannotMatchAfterAnIntegerWrap() {
        click(quantity,"Enter quantity"); click(price,"Enter price");
        when(client.getVarpLongValue(5753)).thenReturn((1L<<32)+100);
        assertEquals(GuideStep.SET_PRICE,resolve().getStep());
        assertTrue(resolve().getTargets().contains(price));
    }
}
