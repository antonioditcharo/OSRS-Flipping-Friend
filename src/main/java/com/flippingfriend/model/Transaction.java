package com.flippingfriend.model;

import java.util.UUID;

public class Transaction
{
	private final UUID id;
	private final int itemId;
	private final int quantity;
	private final int price; // Average price paid/received per item in this fill
	private final long amountSpent;
	private final boolean isBuy;
	private final long timestamp;
	private final int slot;
	private final UUID offerId;

	public Transaction(UUID id, int itemId, int quantity, int price, long amountSpent, boolean isBuy, long timestamp, int slot, UUID offerId)
	{
		this.id = id;
		this.itemId = itemId;
		this.quantity = quantity;
		this.price = price;
		this.amountSpent = amountSpent;
		this.isBuy = isBuy;
		this.timestamp = timestamp;
		this.slot = slot;
		this.offerId = offerId;
	}

	public UUID getId() { return id; }
	public int getItemId() { return itemId; }
	public int getQuantity() { return quantity; }
	public int getPrice() { return price; }
	public long getAmountSpent() { return amountSpent; }
	public boolean isBuy() { return isBuy; }
	public long getTimestamp() { return timestamp; }
	public int getSlot() { return slot; }
	public UUID getOfferId() { return offerId; }

	@Override
	public String toString()
	{
		return "Transaction{" +
			"id=" + id +
			", itemId=" + itemId +
			", quantity=" + quantity +
			", price=" + price +
			", amountSpent=" + amountSpent +
			", isBuy=" + isBuy +
			", timestamp=" + timestamp +
			", slot=" + slot +
			", offerId=" + offerId +
			'}';
	}
}
