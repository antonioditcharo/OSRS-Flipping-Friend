package com.flippingfriend.ui;

import com.flippingfriend.model.Explainer;
import com.flippingfriend.model.Suggestion;
import com.flippingfriend.model.SuggestionType;
import java.awt.*;
import javax.swing.*;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.FontManager;
import net.runelite.client.util.AsyncBufferedImage;

/** One current instruction. Explanations, forecasts and copy controls live in the details window. */
final class CompactSuggestionPanel extends JPanel {
    private final ItemManager items;
    private final Explainer explainer;
    private final JLabel icon = new JLabel();
    private final JTextArea action = text(UiUtils.TEXT);
    private final JTextArea item = text(UiUtils.TEXT);
    private final JTextArea price = text(new Color(255, 223, 0));
    private final JTextArea queued = text(UiUtils.WARNING);
    private final JTextArea forecast = text(UiUtils.profitColor(1));
    private Suggestion shown = Suggestion.idle();
    private int imageItem=Integer.MIN_VALUE;
    private AsyncBufferedImage requestedImage;

    CompactSuggestionPanel(ItemManager items, Explainer explainer) {
        this.items = items; this.explainer = explainer;
        setName("suggested-action"); setLayout(new BorderLayout(10, 0));
        setBackground(UiUtils.CARD); setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));
        icon.setPreferredSize(new Dimension(36, 36)); icon.setVerticalAlignment(SwingConstants.CENTER);
        add(icon, BorderLayout.WEST);
        JPanel words = new JPanel(new java.awt.GridLayout(0, 1)) {
            // No cached BoxLayout text heights: measure each line at the current available width.
            private int textWidth() {
                int parentWidth = CompactSuggestionPanel.this.getWidth();
                return Math.max(50, parentWidth > 0 ? parentWidth - 24 - (icon.isVisible() ? 46 : 0) : 150);
            }
            @Override public Dimension getPreferredSize() {
                int height = 0;
                for (Component line : getComponents()) if (line.isVisible()) height += heightAt((JTextArea)line, textWidth());
                return new Dimension(150, height);
            }
            @Override public void doLayout() {
                int y = 0;
                for (Component line : getComponents()) if (line.isVisible()) {
                    int height = heightAt((JTextArea)line, getWidth());
                    line.setBounds(0, y, getWidth(), height); y += height;
                }
            }
        }; words.setOpaque(false);
        for (JTextArea line : new JTextArea[]{action, item, price, forecast, queued}) { line.setAlignmentX(LEFT_ALIGNMENT); words.add(line); }
        add(words, BorderLayout.CENTER);
    }

    void update(Suggestion next, Suggestion pending) {
        shown = next == null ? Suggestion.idle() : next;
        String instruction;
        switch (shown.getType()) {
            case BUY: instruction = "Buy " + explainer.formatNumber(shown.getQuantity()); break;
            case SELL: instruction = "Sell " + explainer.formatNumber(shown.getQuantity()) + (shown.isLossCut() ? " (loss exit)" : ""); break;
            case MODIFY_BUY: case MODIFY_SELL: instruction = "Cancel, collect, relist"; break;
            case CANCEL: instruction = "Cancel offer"; break;
            case COLLECT: instruction = "Collect"; break;
            default: instruction = shown.getHeadline() == null || shown.getHeadline().isEmpty()
                ? "Waiting for a recommendation" : shown.getHeadline(); break;
        }
        boolean changed=setText(action,instruction);
        changed|=setText(item,shown.getItemName() == null ? "" : shown.getItemName());
        boolean priced = shown.getType() == SuggestionType.BUY || shown.getType() == SuggestionType.SELL
            || shown.getType() == SuggestionType.MODIFY_BUY || shown.getType() == SuggestionType.MODIFY_SELL;
        changed|=setText(price,priced && shown.getPrice() > 0 ? "at " + explainer.formatNumber(shown.getPrice()) + " gp each" : "");
        boolean projected=shown.getType()==SuggestionType.BUY || shown.getType()==SuggestionType.SELL;
        changed|=setText(forecast,projected && shown.isProfitKnown() && shown.getExpectedMinutes()>0
            ? "Est. "+explainer.formatGp(shown.getExpectedProfit())+" in "+Math.max(1,Math.round(shown.getExpectedMinutes()))+"min":"");
        forecast.setVisible(!forecast.getText().isEmpty());
        forecast.setToolTipText("Model estimate for this suggested trade, net of tax; not recorded profit or a guaranteed fill time.");
        forecast.setForeground(UiUtils.profitColor(shown.getExpectedProfit()));
        changed|=setText(queued,pending != null && pending.isActionable() ? "Another action is waiting." : "");
        queued.setVisible(!queued.getText().isEmpty());
        icon.setVisible(shown.getItemId() > 0);
        if (shown.getItemId() != imageItem || requestedImage==null && shown.getItemId()>0) {
            changed=true;imageItem=shown.getItemId();
            icon.setIcon(null);
            AsyncBufferedImage image = shown.getItemId() > 0 ? items.getImage(shown.getItemId()) : null;
            requestedImage=image;int requestedItem=imageItem;
            if (image != null) {
                icon.setIcon(new ImageIcon(image));
                image.onLoaded(()->SwingUtilities.invokeLater(()->{
                    if(imageItem==requestedItem && requestedImage==image)icon.setIcon(new ImageIcon(image));
                }));
            }
        }
        if(changed){revalidate(); repaint();}
    }
    private static boolean setText(JTextArea area,String value) {
        if(java.util.Objects.equals(area.getText(),value))return false;
        area.setText(value);return true;
    }
    private static int heightAt(JTextArea text, int width) {
        Dimension old = text.getSize(); text.setSize(Math.max(1, width), Short.MAX_VALUE);
        int height = text.getPreferredSize().height; text.setSize(old); return height;
    }
    @Override public void setBounds(int x, int y, int width, int height) {
        boolean changed = width != getWidth(); super.setBounds(x, y, width, height);
        if (changed && getParent() != null) { getParent().invalidate(); revalidate(); }
    }
    Suggestion displayed() { return shown; }
    @Override public Dimension getPreferredSize() {
        Dimension preferred = super.getPreferredSize(); return new Dimension(preferred.width, Math.max(82, preferred.height));
    }
    @Override public Dimension getMaximumSize() { return new Dimension(Integer.MAX_VALUE, getPreferredSize().height); }

    static JTextArea text(Color color) {
        JTextArea area = new JTextArea() {
            @Override public Dimension getPreferredSize() {
                int width = getWidth() > 0 ? getWidth() : 150;
                Dimension old = getSize();
                setSize(width, Short.MAX_VALUE);
                Dimension size = super.getPreferredSize(); setSize(old); return new Dimension(150, size.height);
            }
            @Override public Dimension getMaximumSize() { return new Dimension(Integer.MAX_VALUE, getPreferredSize().height); }
        };
        area.setEditable(false); area.setFocusable(false); area.setOpaque(false);
        ((javax.swing.text.DefaultCaret) area.getCaret()).setUpdatePolicy(javax.swing.text.DefaultCaret.NEVER_UPDATE);
        area.setLineWrap(true); area.setWrapStyleWord(true); area.setForeground(color);
        area.setFont(FontManager.getRunescapeFont()); area.setMargin(new Insets(0, 0, 0, 0));
        return area;
    }
}
