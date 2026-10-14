package com.flippingfriend.overlay;

import com.flippingfriend.FlippingFriendConfig;
import com.flippingfriend.model.Suggestion;
import java.awt.Canvas;
import java.awt.event.KeyEvent;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.VarClientInt;
import net.runelite.api.VarClientStr;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.config.Keybind;
import net.runelite.client.input.KeyListener;
import net.runelite.client.input.KeyManager;

/** A player key press may fill one already open GE input. It never accepts or submits the value. */
@Singleton
public final class GeInputAssist implements KeyListener {
    private final Client client;
    private final ClientThread thread;
    private final KeyManager keys;
    private final ConfigManager profiles;
    private final FlippingFriendConfig config;
    private final StepGuide guide;
    private final GeWidgetResolver resolver;
    private final GeSearchSuggestion search;
    private final AtomicLong generation = new AtomicLong();
    private volatile boolean running;
    private volatile boolean accountReady;
    private volatile Target target;
    private volatile int heldKey;
    private volatile char heldCharacter;

    @Inject public GeInputAssist(Client client, ClientThread thread, KeyManager keys, ConfigManager profiles,
        FlippingFriendConfig config, StepGuide guide, GeWidgetResolver resolver) {
        this.client = client; this.thread = thread; this.keys = keys; this.profiles = profiles;
        this.config = config; this.guide = guide; this.resolver = resolver;
        this.search = new GeSearchSuggestion(client, guide, resolver, config);
    }

    public void start() {
        if (running) return;
        running = true;
        keys.registerKeyListener(this);
    }

    public void stop() {
        running = false;
        keys.unregisterKeyListener(this);
        reset();
    }

    public void reset() {
        long resetGeneration = generation.incrementAndGet();
        target = null; accountReady = false; heldKey = 0;
        if (client.isClientThread()) search.reset();
        else thread.invokeLater(() -> { if (generation.get() == resetGeneration) search.reset(); });
    }

    /** Called on the client thread after rendering/rebuilds; AWT receives a copied eligibility target. */
    public void refresh(boolean ready) {
        if (!client.isClientThread()) return;
        Target previous = target;
        accountReady = running && ready && hasProfile();
        Target observed = readTarget();
        if (previous != null && (observed == null || !observed.samePrompt(previous))) {
            // Even a prompt closed and reopened with identical widgets needs a new key press.
            generation.incrementAndGet();
            observed = readTarget();
        }
        target = observed;
        Canvas canvas = client.getCanvas();
        if (canvas == null || !canvas.hasFocus()) heldKey = 0;
        search.refresh(accountReady);
    }

    private boolean hasProfile() {
        String profile = profiles.getRSProfileKey();
        return profile != null && !profile.isEmpty();
    }

    private Target readTarget() {
        if (!running || !accountReady || !client.isClientThread() || !hasProfile()) return null;
        Canvas canvas = client.getCanvas();
        if (canvas == null || !canvas.hasFocus()) return null;
        GeEditorValues values = GeEditorValues.read(client, resolver);
        Suggestion advice = guide.getSuggestion();
        if (values == null || !values.contextMatches(advice)) return null;
        int mode = client.getVarcIntValue(VarClientInt.INPUT_TYPE);
        if (mode != 7 && mode != 30) return null;
        Widget title = client.getWidget(InterfaceID.Chatbox.MES_TEXT);
        Widget input = client.getWidget(InterfaceID.Chatbox.MES_TEXT2);
        if (title == null || input == null || title.isHidden() || input.isHidden()) return null;
        String prompt = title.getText();
        int value;
        if ((values.type == 0 && "How many do you wish to buy?".equals(prompt))
            || (values.type == 1 && "How many do you wish to sell?".equals(prompt))) {
            if (mode != 7) return null;
            value = advice.getQuantity();
        }
        else if ("Set a price for each item:".equals(prompt)) value = advice.getPrice();
        else return null;
        Keybind key = config.fillRecommendedInputKey();
        if (value <= 0 || key == null || Keybind.NOT_SET.equals(key)) return null;
        return new Target(generation.get(), profiles.getRSProfileKey(), key, values, advice, mode, prompt, title, input, value);
    }

    @Override public void keyPressed(KeyEvent event) {
        if (!running || event.isConsumed() || event.getKeyCode() == KeyEvent.VK_ENTER) return;
        if (heldKey != 0 && heldKey == event.getKeyCode()) { event.consume(); return; }
        Target requested = target;
        if (requested == null || !requested.key.equals(config.fillRecommendedInputKey()) || !requested.key.matches(event)) return;
        event.consume();
        heldKey = event.getKeyCode(); heldCharacter = event.getKeyChar();
        thread.invokeLater(() -> fill(requested));
    }

    private void fill(Target requested) {
        Target current = readTarget();
        if (current == null || !current.samePrompt(requested)) return;
        // Sole permitted input write: a player-requested value in the already open numeric prompt.
        // No Enter, script invocation, item selection, varp/varbit change or offer submission follows.
        String text = Integer.toString(current.value);
        client.setVarcStrValue(VarClientStr.INPUT_TEXT, text);
        current.input.setText(text + "*");
    }

    @Override public void keyTyped(KeyEvent event) {
        if (heldKey != 0 && heldCharacter != KeyEvent.CHAR_UNDEFINED && event.getKeyChar() == heldCharacter) event.consume();
    }
    @Override public void keyReleased(KeyEvent event) {
        if (heldKey == event.getKeyCode()) heldKey = 0;
    }

    private static final class Target {
        final long generation;
        final String profile;
        final Keybind key;
        final GeEditorValues editor;
        final Suggestion advice;
        final int mode, value;
        final String prompt;
        final Widget title, input;
        Target(long generation, String profile, Keybind key, GeEditorValues editor, Suggestion advice,
            int mode, String prompt, Widget title, Widget input, int value) {
            this.generation = generation; this.profile = profile; this.key = key; this.editor = editor;
            this.advice = advice; this.mode = mode; this.prompt = prompt; this.title = title; this.input = input; this.value = value;
        }
        boolean samePrompt(Target other) {
            return generation == other.generation && Objects.equals(profile, other.profile) && key.equals(other.key)
                && editor.sameEditor(other.editor) && advice.sameAs(other.advice) && mode == other.mode && value == other.value
                && prompt.equals(other.prompt) && title == other.title && input == other.input;
        }
    }
}
