package com.flippingfriend.overlay;

import com.flippingfriend.FlippingFriendConfig;
import com.flippingfriend.model.Suggestion;
import com.flippingfriend.model.SuggestionType;
import java.awt.Canvas;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import net.runelite.api.*;
import net.runelite.api.gameval.*;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.config.Keybind;
import net.runelite.client.input.KeyManager;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/** Paused client-thread queue and synthetic key events; no real game input. */
public class GeInputAssistTest {
    private final Client client=mock(Client.class);
    private final ClientThread thread=mock(ClientThread.class);
    private final KeyManager keys=mock(KeyManager.class);
    private final ConfigManager profiles=mock(ConfigManager.class);
    private final FlippingFriendConfig config=mock(FlippingFriendConfig.class);
    private final GeWidgetResolver resolver=mock(GeWidgetResolver.class);
    private final Widget title=mock(Widget.class), input=mock(Widget.class);
    private final Canvas canvas=mock(Canvas.class);
    private final List<Runnable> queued=new ArrayList<>();
    private StepGuide guide;
    private GeInputAssist assist;
    @Before public void setup() {
        when(client.isClientThread()).thenReturn(true); when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
        when(client.getCanvas()).thenReturn(canvas); when(canvas.hasFocus()).thenReturn(true);
        when(profiles.getRSProfileKey()).thenReturn("synthetic-profile");
        when(config.fillRecommendedInputKey()).thenReturn(new Keybind(KeyEvent.VK_E,0));
        when(resolver.isSetupOpen()).thenReturn(true);
        when(client.getVarpValue(VarPlayerID.TRADINGPOST_SEARCH)).thenReturn(561);
        when(client.getVarbitValue(VarbitID.GE_NEWOFFER_TYPE)).thenReturn(0);
        when(client.getVarbitValue(VarbitID.GE_SELECTEDSLOT)).thenReturn(1);
        when(client.getVarcIntValue(VarClientInt.INPUT_TYPE)).thenReturn(7);
        when(client.getWidget(InterfaceID.Chatbox.MES_TEXT)).thenReturn(title);
        when(client.getWidget(InterfaceID.Chatbox.MES_TEXT2)).thenReturn(input);
        when(title.getText()).thenReturn("How many do you wish to buy?");
        doAnswer(call->{queued.add(call.getArgument(0)); return null;}).when(thread).invokeLater(any(Runnable.class));
        guide=new StepGuide(client,resolver);
        guide.setSuggestion(buy(100));
        assist=new GeInputAssist(client,thread,keys,profiles,config,guide,resolver);
        assist.start(); assist.refresh(true);
    }
    private Suggestion buy(int price) { return Suggestion.builder(SuggestionType.BUY).item(561,"Nature rune").quantity(10).price(price).build(); }
    private KeyEvent press(int code) {
        KeyEvent event=mock(KeyEvent.class); when(event.getKeyCode()).thenReturn(code);
        when(event.getExtendedKeyCode()).thenReturn(code); when(event.getID()).thenReturn(KeyEvent.KEY_PRESSED);
        when(event.getKeyChar()).thenReturn((char)Character.toLowerCase(code)); return event;
    }
    private void drain() { List<Runnable> pending=new ArrayList<>(queued); queued.clear(); pending.forEach(Runnable::run); }
    @Test public void entryRefusalCancelsAQueuedKeyFillEvenIfTheSameBuyReturns() {
        var quantity=mock(Widget.class);when(resolver.getQuantityControl()).thenReturn(quantity);
        var click=mock(net.runelite.api.events.MenuOptionClicked.class);when(click.getWidget()).thenReturn(quantity);
        guide.onMenuOptionClicked(click);
        assist.keyPressed(press(KeyEvent.VK_E));
        guide.setSuggestion(Suggestion.waiting("Entry unavailable","Waiting for current authority."));
        guide.setSuggestion(buy(100));
        drain();verify(client,never()).setVarcStrValue(anyInt(),anyString());
    }
    @Test public void onlyAPlayerKeyPressFillsOneOpenQuantityPromptWithoutSubmitting() {
        verify(client,never()).setVarcStrValue(anyInt(),anyString());
        KeyEvent e=press(KeyEvent.VK_E); assist.keyPressed(e);
        verify(e).consume(); verify(client,never()).setVarcStrValue(anyInt(),anyString());
        drain(); verify(client).setVarcStrValue(VarClientStr.INPUT_TEXT,"10"); verify(input).setText("10*");
        verify(client,never()).setVarbitValue(any(int[].class),anyInt(),anyInt()); verify(client,never()).runScript(anyInt(),any());
    }
    @Test public void supportsBothPricePromptModesAndLeavesEnterToThePlayer() {
        for (int mode:new int[]{7,30}) {
            when(client.getVarcIntValue(VarClientInt.INPUT_TYPE)).thenReturn(mode);
            when(title.getText()).thenReturn("Set a price for each item:"); assist.refresh(true);
            assist.keyPressed(press(KeyEvent.VK_E)); drain(); assist.keyReleased(press(KeyEvent.VK_E));
        }
        verify(client,times(2)).setVarcStrValue(VarClientStr.INPUT_TEXT,"100");
        when(config.fillRecommendedInputKey()).thenReturn(new Keybind(KeyEvent.VK_ENTER,0));
        assist.refresh(true); KeyEvent enter=press(KeyEvent.VK_ENTER); assist.keyPressed(enter); drain();
        verify(enter,never()).consume(); verify(client,times(2)).setVarcStrValue(anyInt(),anyString());
    }
    @Test public void typingRepeatedOrUnassignedKeysCannotFillAnotherField() {
        assist.keyPressed(press(KeyEvent.VK_E)); assist.keyPressed(press(KeyEvent.VK_E)); drain();
        verify(client,times(1)).setVarcStrValue(anyInt(),anyString());
        KeyEvent typed=mock(KeyEvent.class); when(typed.getKeyChar()).thenReturn('e'); assist.keyTyped(typed); verify(typed).consume();
        assist.keyReleased(press(KeyEvent.VK_E)); when(config.fillRecommendedInputKey()).thenReturn(Keybind.NOT_SET);
        assist.refresh(true); KeyEvent unused=press(KeyEvent.VK_E); assist.keyPressed(unused); drain(); verify(unused,never()).consume();
        verify(client,times(1)).setVarcStrValue(anyInt(),anyString());
    }
    @Test public void queuedFillCannotCrossProfileItemSideSlotOrPromptChanges() {
        for (int changed=0;changed<5;changed++) {
            setup(); assist.keyPressed(press(KeyEvent.VK_E));
            switch(changed) {
                case 0: when(profiles.getRSProfileKey()).thenReturn("other-profile"); break;
                case 1: when(client.getVarpValue(VarPlayerID.TRADINGPOST_SEARCH)).thenReturn(4151); break;
                case 2: when(client.getVarbitValue(VarbitID.GE_NEWOFFER_TYPE)).thenReturn(1); break;
                case 3: when(client.getVarbitValue(VarbitID.GE_SELECTEDSLOT)).thenReturn(2); break;
                case 4: when(title.getText()).thenReturn("Set a price for each item:"); break;
            }
            drain(); verify(client,never()).setVarcStrValue(anyInt(),anyString());
        }
    }
    @Test public void recommendationResetStopAndExposureLossFenceQueuedInput() {
        assist.keyPressed(press(KeyEvent.VK_E)); guide.setSuggestion(buy(101)); drain();
        verify(client,never()).setVarcStrValue(anyInt(),anyString());
        assist.reset(); guide.setSuggestion(buy(100)); assist.refresh(true); assist.keyPressed(press(KeyEvent.VK_E));
        assist.reset(); drain(); verify(client,never()).setVarcStrValue(anyInt(),anyString());
        assist.refresh(true); assist.keyPressed(press(KeyEvent.VK_E)); assist.refresh(false); drain();
        verify(client,never()).setVarcStrValue(anyInt(),anyString());
        assist.refresh(true); assist.keyReleased(press(KeyEvent.VK_E)); assist.keyPressed(press(KeyEvent.VK_E));
        assist.stop(); drain(); verify(client,never()).setVarcStrValue(anyInt(),anyString()); verify(keys).unregisterKeyListener(assist);
    }
    @Test public void unrelatedChatBankSearchWrongContextAndLostFocusAreUntouched() {
        when(title.getText()).thenReturn("Enter amount:"); assist.refresh(true); assist.keyPressed(press(KeyEvent.VK_E)); drain();
        when(title.getText()).thenReturn("How many do you wish to buy?");
        when(client.getVarcIntValue(VarClientInt.INPUT_TYPE)).thenReturn(14); assist.refresh(true); assist.keyPressed(press(KeyEvent.VK_E)); drain();
        when(client.getVarcIntValue(VarClientInt.INPUT_TYPE)).thenReturn(7);
        when(canvas.hasFocus()).thenReturn(false); assist.refresh(true); assist.keyPressed(press(KeyEvent.VK_E)); drain();
        verify(client,never()).setVarcStrValue(anyInt(),anyString());
    }
    @Test public void profilelessLoggedOutHiddenAndUnreadyClientNeverConsumesKeys() {
        assist.refresh(false); KeyEvent e=press(KeyEvent.VK_E); assist.keyPressed(e); verify(e,never()).consume();
        when(profiles.getRSProfileKey()).thenReturn(""); assist.refresh(true); assist.keyPressed(e);
        when(profiles.getRSProfileKey()).thenReturn("synthetic-profile"); when(client.getGameState()).thenReturn(GameState.LOGIN_SCREEN);
        assist.refresh(true); assist.keyPressed(e);
        when(client.getGameState()).thenReturn(GameState.LOGGED_IN); when(input.isHidden()).thenReturn(true);
        assist.refresh(true); assist.keyPressed(e); drain(); verify(e,never()).consume(); verify(client,never()).setVarcStrValue(anyInt(),anyString());
    }
    @Test public void changingTheConfiguredKeyBeforeQueuedFillCancelsIt() {
        assist.keyPressed(press(KeyEvent.VK_E)); when(config.fillRecommendedInputKey()).thenReturn(Keybind.NOT_SET); drain();
        verify(client,never()).setVarcStrValue(anyInt(),anyString());
    }
    @Test public void closingAndReopeningTheSamePromptCannotReuseAnEarlierKeyPress() {
        assist.keyPressed(press(KeyEvent.VK_E));
        when(client.getVarcIntValue(VarClientInt.INPUT_TYPE)).thenReturn(0); assist.refresh(true);
        when(client.getVarcIntValue(VarClientInt.INPUT_TYPE)).thenReturn(7); assist.refresh(true);
        drain(); verify(client,never()).setVarcStrValue(anyInt(),anyString());
    }
    @Test public void temporaryReadinessLossCannotReviveAnEarlierKeyPress() {
        assist.keyPressed(press(KeyEvent.VK_E)); assist.refresh(false); assist.refresh(true); drain();
        verify(client,never()).setVarcStrValue(anyInt(),anyString());
    }
    @Test public void heldKeyCannotFillTheNextPromptUntilReleasedAndPressedAgain() {
        assist.keyPressed(press(KeyEvent.VK_E)); drain();
        when(title.getText()).thenReturn("Set a price for each item:"); assist.refresh(true);
        assist.keyPressed(press(KeyEvent.VK_E)); drain(); verify(client,never()).setVarcStrValue(VarClientStr.INPUT_TEXT,"100");
        assist.keyReleased(press(KeyEvent.VK_E)); assist.keyPressed(press(KeyEvent.VK_E)); drain();
        verify(client).setVarcStrValue(VarClientStr.INPUT_TEXT,"10"); verify(client).setVarcStrValue(VarClientStr.INPUT_TEXT,"100");
    }
    @Test public void sellQuantityRequiresTheMatchingSellPromptAndDoesNotChangeAdvice() {
        when(client.getVarbitValue(VarbitID.GE_NEWOFFER_TYPE)).thenReturn(1);
        Suggestion sale=Suggestion.builder(SuggestionType.SELL).item(561,"Nature rune").quantity(5).price(119).build();
        guide.setSuggestion(sale);
        assist.refresh(true); assist.keyPressed(press(KeyEvent.VK_E)); drain();
        verify(client,never()).setVarcStrValue(anyInt(),anyString());
        when(title.getText()).thenReturn("How many do you wish to sell?"); assist.refresh(true);
        assist.keyPressed(press(KeyEvent.VK_E)); drain(); verify(client).setVarcStrValue(VarClientStr.INPUT_TEXT,"5");
        assertSame(sale,guide.getSuggestion());
    }
    @Test public void queuedKeyCannotCrossPauseAndResumeEvenWithIdenticalFreshAdvice() {
        assist.keyPressed(press(KeyEvent.VK_E));
        guide.setPaused(true);
        guide.setPaused(false); guide.setSuggestion(buy(100));
        drain(); verify(client,never()).setVarcStrValue(anyInt(),anyString());
        assist.refresh(true); assist.keyReleased(press(KeyEvent.VK_E)); assist.keyPressed(press(KeyEvent.VK_E));
        drain(); verify(client).setVarcStrValue(VarClientStr.INPUT_TEXT,"10");
    }
    @Test public void pauseWithdrawsBuyAndManualSaleGuidanceUntilFreshAdviceAfterResume() {
        guide.setEditorQuote(Suggestion.builder(SuggestionType.SELL).item(561,"Nature rune").quantity(3).price(99).profitKnown(false).build());
        guide.setPaused(true); assertFalse(guide.getSuggestion().isActionable()); assertNull(guide.getPending());
        guide.setSuggestion(buy(110)); guide.setEditorQuote(buy(120));
        assertFalse(guide.getSuggestion().isActionable());
        guide.setPaused(false); assertFalse(guide.getSuggestion().isActionable());
        guide.setSuggestion(buy(100)); assertTrue(guide.getSuggestion().isActionable());
        guide.setPaused(true); guide.reset(); assertFalse(guide.isPaused()); assertFalse(guide.getSuggestion().isActionable());
    }
}
