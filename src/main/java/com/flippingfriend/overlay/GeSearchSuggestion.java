package com.flippingfriend.overlay;

import com.flippingfriend.FlippingFriendConfig;
import com.flippingfriend.model.Suggestion;
import com.flippingfriend.model.SuggestionType;
import java.util.Arrays;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.VarClientInt;
import net.runelite.api.VarClientStr;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetType;

/** Appends a suggested buy to an empty native GE search. Only the player's Select runs its listener. */
final class GeSearchSuggestion {
    private static final int SELECT_ITEM_SCRIPT = 754;
    private static final int ROW_HEIGHT = 32;
    private final Client client;
    private final StepGuide guide;
    private final GeWidgetResolver resolver;
    private final FlippingFriendConfig config;
    private Widget parent, button, icon, label;
    private int item = -1, width, y;
    private String name;

    GeSearchSuggestion(Client client, StepGuide guide, GeWidgetResolver resolver, FlippingFriendConfig config) {
        this.client = client; this.guide = guide; this.resolver = resolver; this.config = config;
    }

    void refresh(boolean accountReady) {
        if (!client.isClientThread()) return;
        // BeforeRender also runs before login, when the client's varbit backing arrays are absent.
        if (!accountReady || client.getGameState() != GameState.LOGGED_IN || !config.showSuggestedSearchItem()
                || !resolver.isSetupOpen()) {
            hide(); return;
        }
        Suggestion advice = guide.getSuggestion();
        int slot = client.getVarbitValue(VarbitID.GE_SELECTEDSLOT);
        String query = client.getVarcStrValue(VarClientStr.INPUT_TEXT);
        Widget root = client.getWidget(InterfaceID.Chatbox.MES_LAYER_SCROLLCONTENTS);
        if (client.getVarcIntValue(VarClientInt.INPUT_TYPE) != 14
            || client.getVarbitValue(VarbitID.GE_NEWOFFER_TYPE) != 0 || slot < 1 || slot > 8
            || (query != null && !query.isEmpty()) || !GeEditorValues.isOffer(advice)
            || (advice.getType() != SuggestionType.BUY && advice.getType() != SuggestionType.MODIFY_BUY)
            || root == null || root.isHidden()) {
            hide(); return;
        }
        Widget[] children = root.getDynamicChildren();
        if (root != parent || children == null || !Arrays.asList(children).contains(button)) {
            hide(); parent = root; button = null; icon = null; label = null; item = -1;
        }
        int top = 0;
        if (children != null) for (Widget child : children) {
            if (child != null && child != button && child != icon && child != label && !child.isHidden())
                top = Math.max(top, child.getOriginalY() + child.getOriginalHeight());
        }
        int rowWidth = root.getWidth() - 8;
        if (rowWidth < 150 || top + ROW_HEIGHT > root.getHeight()) { hide(); return; }
        if (button == null) {
            button = root.createChild(-1, WidgetType.RECTANGLE);
            icon = root.createChild(-1, WidgetType.GRAPHIC);
            label = root.createChild(-1, WidgetType.TEXT);
            button.setFilled(true); button.setOpacity(210); button.setTextColor(0x30503F);
            button.setHasListener(true); button.setAction(0, "Select");
            icon.setItemQuantity(1); icon.setItemQuantityMode(0); icon.setRotationX(550);
            icon.setModelZoom(1031); icon.setBorderType(1);
            label.setFontId(495); label.setTextColor(0xFFFFFF); label.setYTextAlignment(1);
        }
        if (item != advice.getItemId() || !java.util.Objects.equals(name, advice.getItemName())) {
            item = advice.getItemId(); name = advice.getItemName();
            button.setName("<col=ff9040>" + name + "</col>");
            // Native local item-selection listener, as used by the game's previous-search row.
            // Register it for the player's click; never invoke it from the plugin.
            button.setOnOpListener(SELECT_ITEM_SCRIPT, item, 84);
            icon.setItemId(item); label.setText("Suggested: " + name);
        }
        if (width != rowWidth || y != top || button.getOriginalHeight() != ROW_HEIGHT) {
            width = rowWidth; y = top;
            layout(button, 4, top, rowWidth); layout(icon, 8, top, 36); layout(label, 48, top, rowWidth - 48);
        }
        button.setHidden(false); icon.setHidden(false); label.setHidden(false);
    }

    private void layout(Widget widget, int left, int top, int rowWidth) {
        widget.setOriginalX(left); widget.setOriginalY(top); widget.setOriginalWidth(rowWidth);
        widget.setOriginalHeight(ROW_HEIGHT); widget.revalidate();
    }
    private void hide() {
        if (button != null) { button.setHidden(true); icon.setHidden(true); label.setHidden(true); }
    }
    void reset() { hide(); item = -1; name = null; }
}
