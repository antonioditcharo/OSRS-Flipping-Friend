package com.flippingfriend.ui;

import java.awt.Color;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import net.runelite.client.util.ImageUtil;

/** Google Material icons (Apache 2.0); source images and license are bundled with the plugin. */
final class PanelIcons {
    private PanelIcons() { }
    static ImageIcon icon(String name, Color color) {
        return new ImageIcon(ImageUtil.fillImage(ImageUtil.loadImageResource(PanelIcons.class,
            "/com/flippingfriend/ui/icons/" + name + ".png"), color));
    }
    static JButton button(String name, String asset, String description, Runnable action) {
        JButton button = new JButton(icon(asset, UiUtils.TEXT));
        button.setName(name); button.setToolTipText(description);
        button.getAccessibleContext().setAccessibleName(description);
        button.setBorder(javax.swing.BorderFactory.createEmptyBorder(4, 8, 4, 8));
        button.setUI(new javax.swing.plaf.basic.BasicButtonUI());
        button.setBackground(UiUtils.CARD); button.setOpaque(true); button.setContentAreaFilled(true);
        button.setFocusPainted(true); button.addActionListener(event -> action.run());
        return button;
    }
}
