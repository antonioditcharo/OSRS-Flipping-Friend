package com.flippingfriend.ui;

import com.flippingfriend.model.MarketFluxIndex;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Singleton;

@Singleton
public class AlertManager
{
	public enum AlertLevel {
		INFO(Color.WHITE),
		WARNING(Color.YELLOW),
		CRITICAL(Color.RED);

		private final Color color;

		AlertLevel(Color color) {
			this.color = color;
		}

		public Color getColor() {
			return color;
		}
	}

	public static class Alert {
		private final String message;
		private final AlertLevel level;
		private final long timestamp;

		public Alert(String message, AlertLevel level) {
			this.message = message;
			this.level = level;
			this.timestamp = System.currentTimeMillis();
		}

		public String getMessage() {
			return message;
		}

		public AlertLevel getLevel() {
			return level;
		}

		public long getTimestamp() {
			return timestamp;
		}
	}

	private final List<Alert> activeAlerts = new ArrayList<>();
	
	// Threshold for triggering Safe Mode due to Market Flux
	private static final double MARKET_FLUX_SAFE_MODE_THRESHOLD = -0.5;

	public void checkMarketFlux(double currentFlux)
	{
		if (currentFlux <= MARKET_FLUX_SAFE_MODE_THRESHOLD)
		{
			addAlert(new Alert("SAFE MODE ACTIVE: Market Flux is highly negative (" + String.format("%.2f", currentFlux) + ").", AlertLevel.CRITICAL));
		}
	}

	public void addAlert(Alert alert)
	{
		activeAlerts.add(alert);
		// Keep only the last 10 alerts
		if (activeAlerts.size() > 10)
		{
			activeAlerts.remove(0);
		}
	}

	public List<Alert> getActiveAlerts()
	{
		return new ArrayList<>(activeAlerts);
	}
	
	public void clearAlerts()
	{
		activeAlerts.clear();
	}
}
