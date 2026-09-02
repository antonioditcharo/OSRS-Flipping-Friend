package com.flippingfriend.ui;

import java.awt.Dimension;
import java.awt.Graphics2D;
import javax.inject.Inject;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.PanelComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;
import net.runelite.client.ui.overlay.components.LineComponent;

public class AlertOverlay extends Overlay {
    private final AlertManager alertManager;
    private final PanelComponent panelComponent = new PanelComponent();

    @Inject
    public AlertOverlay(AlertManager alertManager) {
        this.alertManager = alertManager;
        setPosition(OverlayPosition.TOP_CENTER);
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        if (alertManager.getActiveAlerts().isEmpty()) {
            return null;
        }
        panelComponent.getChildren().clear();
        panelComponent.getChildren().add(TitleComponent.builder()
            .text("Flipping Friend Alerts")
            .build());
        
        for (AlertManager.Alert alert : alertManager.getActiveAlerts()) {
            panelComponent.getChildren().add(LineComponent.builder()
                .left(alert.getMessage())
                .leftColor(alert.getLevel().getColor())
                .build());
        }
        return panelComponent.render(graphics);
    }
}
