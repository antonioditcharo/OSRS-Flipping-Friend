package com.flippingfriend.overlay;

import com.flippingfriend.model.Suggestion;
import com.flippingfriend.model.SuggestionType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.VarClientInt;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.gameval.VarPlayerID;

/** One client-thread observation of the actual editor; no input writes or price narrowing. */
final class GeEditorValues {
    // RuneLite 1.13.2 names the 64-bit GE setup price varp in the generated API.
    static final int PRICE_VARP = VarPlayerID.GE_NEWOFFER_PRICE;
    final int item, quantity, type, slot;
    final long price;
    final boolean typing;
    GeEditorValues(int item, int quantity, int type, int slot, long price, boolean typing) {
        this.item=item; this.quantity=quantity; this.type=type; this.slot=slot;
        this.price=price; this.typing=typing;
    }
    static GeEditorValues read(Client client, GeWidgetResolver resolver) {
        if (!client.isClientThread() || client.getGameState()!=GameState.LOGGED_IN || !resolver.isSetupOpen()) return null;
        return new GeEditorValues(client.getVarpValue(VarPlayerID.TRADINGPOST_SEARCH),
            client.getVarbitValue(VarbitID.GE_NEWOFFER_QUANTITY),client.getVarbitValue(VarbitID.GE_NEWOFFER_TYPE),
            client.getVarbitValue(VarbitID.GE_SELECTEDSLOT)-1,client.getVarpLongValue(PRICE_VARP),
            client.getVarcIntValue(VarClientInt.INPUT_TYPE)!=0);
    }
    boolean usable() { return item>0 && quantity>=0 && price>=0 && (type==0 || type==1) && slot>=0 && slot<8; }
    static boolean isOffer(Suggestion suggestion) {
        if (suggestion==null || suggestion.getItemId()<=0 || suggestion.getPrice()<=0 || suggestion.getQuantity()<=0) return false;
        switch (suggestion.getType()) {
            case BUY: case SELL: case MODIFY_BUY: case MODIFY_SELL: return true;
            default: return false;
        }
    }
    boolean contextMatches(Suggestion suggestion) {
        if (!usable() || !isOffer(suggestion)) return false;
        boolean sell=suggestion.getType()==SuggestionType.SELL || suggestion.getType()==SuggestionType.MODIFY_SELL;
        return item==suggestion.getItemId() && type==(sell?1:0);
    }
    boolean matches(Suggestion suggestion) {
        return contextMatches(suggestion) && !typing && quantity==suggestion.getQuantity() && price==suggestion.getPrice();
    }
    boolean sameEditor(GeEditorValues other) {
        return other!=null && item==other.item && type==other.type && slot==other.slot;
    }
}
