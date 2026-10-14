package com.flippingfriend.overlay;

import com.flippingfriend.FlippingFriendConfig;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.api.events.MenuOpened;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.widgets.Widget;

/** Prevents a normal mismatched confirmation; native right-click Confirm remains player-operated. */
@Singleton
public final class TradeConfirmGuard {
    static final String LOCKED = "Set recommended values first";
    private final Client client;
    private final StepGuide guide;
    private final GeWidgetResolver resolver;
    private final FlippingFriendConfig config;
    private final List<Override> overrides = new ArrayList<>();
    @Inject public TradeConfirmGuard(Client client, StepGuide guide, GeWidgetResolver resolver, FlippingFriendConfig config) {
        this.client=client; this.guide=guide; this.resolver=resolver; this.config=config;
    }
    private boolean protects(GeEditorValues values) {
        return config.protectTradeConfirmation() && values!=null && values.usable()
            && GeEditorValues.isOffer(guide.getSuggestion());
    }
    private boolean confirm(MenuEntry entry) {
        if (entry==null || !"Confirm".equalsIgnoreCase(entry.getOption())
            || (entry.getType()!=MenuAction.CC_OP && entry.getType()!=MenuAction.CC_OP_LOW_PRIORITY)) return false;
        Widget button=resolver.getConfirmButton();
        if (button==null) return false;
        Widget widget=entry.getWidget();
        return widget!=null ? widget==button : button.getId()>0 && entry.getParam1()==button.getId()
            && entry.getParam0()==button.getIndex();
    }
    public void onMenuEntryAdded(MenuEntryAdded event) {
        GeEditorValues values=guide.editorValues();
        MenuEntry nativeConfirm=event.getMenuEntry();
        if (!protects(values) || values.matches(guide.getSuggestion()) || !confirm(nativeConfirm)) return;
        // Preserve native opcode, identifier, widget parameters and option for the manual override.
        nativeConfirm.setDeprioritized(true);
        for (MenuEntry entry:client.getMenu().getMenuEntries())
            if (entry.getType()==MenuAction.RUNELITE && LOCKED.equals(entry.getOption())) return;
        client.getMenu().createMenuEntry(-1).setOption(LOCKED).setTarget("")
            .setType(MenuAction.RUNELITE).setParam0(nativeConfirm.getParam0()).setParam1(nativeConfirm.getParam1());
    }
    /** Only a real menu-open event arms the native Confirm override for this editor. */
    public void onMenuOpened(MenuOpened event) {
        overrides.clear();
        GeEditorValues values=guide.editorValues();
        if (!protects(values)) return;
        for (MenuEntry entry:event.getMenuEntries()) if (confirm(entry)) overrides.add(new Override(entry,values));
    }
    public void onMenuOptionClicked(MenuOptionClicked event) {
        if (LOCKED.equals(event.getMenuOption()) && event.getMenuAction()==MenuAction.RUNELITE) {
            event.consume(); overrides.clear(); return;
        }
        MenuEntry entry=event.getMenuEntry();
        GeEditorValues values=guide.editorValues();
        if (confirm(entry) && protects(values) && !values.matches(guide.getSuggestion())) {
            boolean manual=false;
            for (Override override:overrides) if (override.matches(entry,values)) manual=true;
            if (!manual) event.consume();
        }
        overrides.clear();
    }
    public void beforeRender() {
        if (!client.isMenuOpen() || guide.editorValues()==null) overrides.clear();
    }
    public void reset() { overrides.clear(); }
    private static final class Override {
        final int widget, child, identifier;
        final MenuAction type;
        final GeEditorValues editor;
        Override(MenuEntry entry,GeEditorValues editor) {
            widget=entry.getParam1(); child=entry.getParam0(); identifier=entry.getIdentifier();
            type=entry.getType(); this.editor=editor;
        }
        boolean matches(MenuEntry entry,GeEditorValues values) {
            return editor.sameEditor(values) && entry.getParam1()==widget && entry.getParam0()==child
                && entry.getIdentifier()==identifier && entry.getType()==type;
        }
    }
}
