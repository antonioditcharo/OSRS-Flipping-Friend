package com.flippingfriend.model;

import java.util.Objects;
import net.runelite.api.GrandExchangeOffer;
import net.runelite.api.GrandExchangeOfferState;

public class SavedOffer
{
	private final int itemId;
	private final int quantitySold;
	private final long spent;
	private final int totalQuantity;
	private final GrandExchangeOfferState state;
	private final int price;

	public SavedOffer(int itemId, int quantitySold, long spent, int totalQuantity, GrandExchangeOfferState state, int price)
	{
		this.itemId = itemId;
		this.quantitySold = quantitySold;
		this.spent = spent;
		this.totalQuantity = totalQuantity;
		this.state = state;
		this.price = price;
	}

	public static SavedOffer fromGrandExchangeOffer(GrandExchangeOffer offer)
	{
		if (offer == null)
		{
			return null;
		}
		return new SavedOffer(
			offer.getItemId(),
			offer.getQuantitySold(),
			offer.getSpent(),
			offer.getTotalQuantity(),
			offer.getState(),
			offer.getPrice()
		);
	}

	public int getItemId() { return itemId; }
	public int getQuantitySold() { return quantitySold; }
	public long getSpent() { return spent; }
	public int getTotalQuantity() { return totalQuantity; }
	public GrandExchangeOfferState getState() { return state; }
	public int getPrice() { return price; }

	@Override
	public boolean equals(Object o)
	{
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		SavedOffer that = (SavedOffer) o;
		return itemId == that.itemId &&
			quantitySold == that.quantitySold &&
			spent == that.spent &&
			totalQuantity == that.totalQuantity &&
			price == that.price &&
			state == that.state;
	}

	@Override
	public int hashCode()
	{
		return Objects.hash(itemId, quantitySold, spent, totalQuantity, state, price);
	}

	@Override
	public String toString()
	{
		return "SavedOffer{" +
			"itemId=" + itemId +
			", quantitySold=" + quantitySold +
			", spent=" + spent +
			", totalQuantity=" + totalQuantity +
			", state=" + state +
			", price=" + price +
			'}';
	}
}
