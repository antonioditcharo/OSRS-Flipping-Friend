package com.flippingfriend.overlay;

import com.flippingfriend.FlippingFriendConfig;
import com.flippingfriend.model.Suggestion;
import com.flippingfriend.model.SuggestionType;
import java.util.ArrayList;
import java.util.List;
import net.runelite.api.*;
import net.runelite.api.gameval.*;
import net.runelite.api.widgets.Widget;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/** Only mocked widget construction; the native Select listener is never executed by these tests. */
public class GeSearchSuggestionTest {
    private final Client client=mock(Client.class);
    private final GeWidgetResolver resolver=mock(GeWidgetResolver.class);
    private final FlippingFriendConfig config=mock(FlippingFriendConfig.class);
    private final Widget parent=mock(Widget.class);
    private final List<Widget> children=new ArrayList<>();
    private StepGuide guide;
    private GeSearchSuggestion search;
    @Before public void setup() {
        when(client.isClientThread()).thenReturn(true); when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
        when(resolver.isSetupOpen()).thenReturn(true);
        when(config.showSuggestedSearchItem()).thenReturn(true);
        when(client.getVarcIntValue(VarClientInt.INPUT_TYPE)).thenReturn(14);
        when(client.getVarcStrValue(VarClientStr.INPUT_TEXT)).thenReturn("");
        when(client.getVarbitValue(VarbitID.GE_SELECTEDSLOT)).thenReturn(1);
        when(client.getWidget(InterfaceID.Chatbox.MES_LAYER_SCROLLCONTENTS)).thenReturn(parent);
        when(parent.getWidth()).thenReturn(480); when(parent.getHeight()).thenReturn(100);
        when(parent.getDynamicChildren()).thenAnswer(call->children.toArray(new Widget[0]));
        when(parent.createChild(eq(-1),anyInt())).thenAnswer(call->{Widget w=mock(Widget.class); children.add(w); return w;});
        guide=new StepGuide(client,resolver); guide.setSuggestion(buy(561,"Nature rune"));
        search=new GeSearchSuggestion(client,guide,resolver,config);
    }
    private Suggestion buy(int item,String name) { return Suggestion.builder(SuggestionType.BUY).item(item,name).quantity(10).price(100).build(); }
    @Test public void currentBuyAppearsAsAnActualSelectableChatboxRowWithoutSelectingIt() {
        search.refresh(true); assertEquals(3,children.size());
        verify(children.get(0)).setAction(0,"Select"); verify(children.get(0)).setOnOpListener(754,561,84);
        verify(children.get(1)).setItemId(561); verify(children.get(2)).setText("Suggested: Nature rune");
        verify(client,never()).runScript(anyInt(),any()); verify(client,never()).setVarpValue(anyInt(),anyInt());
        search.refresh(true); assertEquals(3,children.size());
    }
    @Test public void existingPreviousSearchAndNativeResultsArePreserved() {
        Widget nativePrevious=mock(Widget.class); when(nativePrevious.getOriginalHeight()).thenReturn(32); children.add(nativePrevious);
        search.refresh(true); assertEquals(4,children.size());
        verify(nativePrevious,never()).setHidden(anyBoolean()); verify(nativePrevious,never()).setText(anyString());
        verify(nativePrevious,never()).setOnOpListener(any());
        verify(children.get(1)).setOriginalY(32);
    }
    @Test public void typedPersonalSearchHidesSuggestionWithoutChangingTheQueryOrResults() {
        search.refresh(true); when(client.getVarcStrValue(VarClientStr.INPUT_TEXT)).thenReturn("dragon"); search.refresh(true);
        for (Widget row:children) verify(row).setHidden(true);
        verify(client,never()).setVarcStrValue(anyInt(),anyString());
        when(client.getVarcStrValue(VarClientStr.INPUT_TEXT)).thenReturn(""); search.refresh(true); assertEquals(3,children.size());
    }
    @Test public void withdrawalSellCloseAndUnreadyStateClearTheSuggestedRow() {
        search.refresh(true); guide.setSuggestion(Suggestion.idle()); search.refresh(true);
        for (Widget row:children) verify(row).setHidden(true);
        guide.setSuggestion(buy(561,"Nature rune")); when(client.getVarbitValue(VarbitID.GE_NEWOFFER_TYPE)).thenReturn(1);
        search.refresh(true); search.refresh(false); search.reset();
        verify(client,never()).runScript(anyInt(),any());
    }
    @Test public void nativeRebuildCreatesOneFreshRowAndNeverReusesDetachedWidgets() {
        search.refresh(true); List<Widget> detached=new ArrayList<>(children); children.clear(); search.refresh(true);
        assertEquals(3,children.size()); for(Widget row:children) assertFalse(detached.contains(row));
        guide.setSuggestion(buy(4151,"Abyssal whip")); search.refresh(true);
        verify(children.get(1)).setItemId(4151); verify(children.get(2)).setText("Suggested: Abyssal whip");
    }
    @Test public void disabledHiddenAndAbsentPanelsCannotCreateClickableSuggestions() {
        when(config.showSuggestedSearchItem()).thenReturn(false); search.refresh(true);
        when(config.showSuggestedSearchItem()).thenReturn(true); when(parent.isHidden()).thenReturn(true); search.refresh(true);
        when(client.getWidget(InterfaceID.Chatbox.MES_LAYER_SCROLLCONTENTS)).thenReturn(null); search.refresh(true);
        assertTrue(children.isEmpty());
    }
}
