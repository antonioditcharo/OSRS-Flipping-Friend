package com.flippingfriend.core;

/**
 * Real-time alert produced by the companion daemon, to be surfaced to the user.
 */
public final class PortfolioAlert
{
	private final String alertType;
	private final int itemId;
	private final int slot;
	private final String message;

	public PortfolioAlert(String alertType, int itemId, int slot, String message)
	{
		this.alertType = alertType;
		this.itemId = itemId;
		this.slot = slot;
		this.message = message;
	}

	public String getAlertType() { return alertType; }
	public int getItemId() { return itemId; }
	public int getSlot() { return slot; }
	public String getMessage() { return message; }
}
