package com.flippingfriend.session;

/**
 * The plugin's own record of one Grand Exchange slot.
 * <p>
 * The game reports an offer's running totals, not the individual fills, and it replays every slot's
 * state on login. So the important field here is {@link #getRecordedQuantity()}: how much of this
 * offer we have already accounted for. Without it, logging in with two finished offers would book
 * them into the journal a second time, and every restart would corrupt both the profit figures and
 * the buy-limit ledger.
 */
public class TrackedOffer
{
	private int slot;
	private int itemId;
	private String itemName;
	private boolean buying;
	private int price;
	private int totalQuantity;
	private int quantityFilled;
	private int recordedQuantity;
	/** Coins already booked for this offer, so a later fill only books the difference. */
	private long recordedSpent;
	private long spent;
	private String state;
	private long firstSeen;
	private long lastChanged;

	/**
	 * The journal entry this offer is accruing, held here until the offer finishes.
	 * <p>
	 * The game reports running totals and fills an offer in as many pieces as the market feels like,
	 * so booking a journal entry per fill turned one sale of 7,000 rubies into three trades and one
	 * sale of 4,337 mithril bars into six. A flip is the offer, not the instalments, so the pieces
	 * are added up here and written once when the offer reaches a terminal state.
	 */
	private int pendingQuantity;
	private long pendingGross;
	private long pendingTax;
	private long pendingCostBasis;
	private int pendingBackedQuantity;
	private int pendingUnbackedQuantity;
	private long pendingOpenedAt;
	private double pendingPredictedMinutes;
	private long pendingPredictedProfit;
	/** Set once the entry has been written, so a replayed terminal event cannot write it twice. */
	private boolean journalled;
	/**
	 * Set when the offer has left its slot entirely.
	 * <p>
	 * Explicit rather than inferred from the state, because a SOLD offer sitting in a slot waiting to
	 * be collected looks identical to one already collected -- and filing that as finished would take
	 * it off the list of things the player still has to collect.
	 */
	private boolean collected;

	public TrackedOffer()
	{
	}

	public TrackedOffer(int slot, int itemId, boolean buying, int price, int totalQuantity, long firstSeen)
	{
		this.slot = slot;
		this.itemId = itemId;
		this.buying = buying;
		this.price = price;
		this.totalQuantity = totalQuantity;
		this.firstSeen = firstSeen;
		this.lastChanged = firstSeen;
	}

	public int getSlot()
	{
		return slot;
	}

	public int getItemId()
	{
		return itemId;
	}

	public String getItemName()
	{
		return itemName == null ? "Item " + itemId : itemName;
	}

	public void setItemName(String itemName)
	{
		this.itemName = itemName;
	}

	public boolean isBuying()
	{
		return buying;
	}

	public int getPrice()
	{
		return price;
	}

	public int getTotalQuantity()
	{
		return totalQuantity;
	}

	public int getQuantityFilled()
	{
		return quantityFilled;
	}

	public void setQuantityFilled(int quantityFilled)
	{
		this.quantityFilled = quantityFilled;
	}

	/** How much of this offer has already been written into the journal and ledgers. */
	public int getRecordedQuantity()
	{
		return recordedQuantity;
	}

	public void setRecordedQuantity(int recordedQuantity)
	{
		this.recordedQuantity = recordedQuantity;
	}

	public long getRecordedSpent()
	{
		return recordedSpent;
	}

	public void setRecordedSpent(long recordedSpent)
	{
		this.recordedSpent = recordedSpent;
	}

	public long getSpent()
	{
		return spent;
	}

	public void setSpent(long spent)
	{
		this.spent = spent;
	}

	public String getState()
	{
		return state;
	}

	public void setState(String state)
	{
		this.state = state;
	}

	public long getFirstSeen()
	{
		return firstSeen;
	}

	public long getLastChanged()
	{
		return lastChanged;
	}

	public void setLastChanged(long lastChanged)
	{
		this.lastChanged = lastChanged;
	}

	public int getRemaining()
	{
		return Math.max(0, totalQuantity - quantityFilled);
	}

	public boolean isComplete()
	{
		return quantityFilled >= totalQuantity && totalQuantity > 0;
	}

	public double getProgress()
	{
		return totalQuantity <= 0 ? 0 : Math.min(1.0, (double) quantityFilled / totalQuantity);
	}

	public long minutesOpen(long nowEpochSeconds)
	{
		return Math.max(0, (nowEpochSeconds - firstSeen) / 60);
	}

	/** True when this slot describes the same offer, rather than a new one placed after a collect. */
	public boolean matches(int itemId, boolean buying, int price, int totalQuantity)
	{
		return this.itemId == itemId
			&& this.buying == buying
			&& this.price == price
			&& this.totalQuantity == totalQuantity;
	}

	/** Adds one sell fill to the entry this offer will write when it finishes. */
	public void accrueSell(int quantity, long gross, long tax, Position.Removal removal,
		long openedAt, double predictedMinutes, long predictedProfit)
	{
		pendingQuantity += quantity;
		pendingGross += gross;
		pendingTax += tax;
		pendingCostBasis += removal.getCostBasis();
		pendingBackedQuantity += removal.getBackedQuantity();
		pendingUnbackedQuantity += removal.getUnbackedQuantity();
		if (pendingOpenedAt == 0 || (openedAt > 0 && openedAt < pendingOpenedAt))
		{
			pendingOpenedAt = openedAt;
		}
		// The prediction belongs to the trade, so the first fill's is the one that was actually made.
		if (pendingPredictedMinutes <= 0)
		{
			pendingPredictedMinutes = predictedMinutes;
		}
		pendingPredictedProfit += predictedProfit;
	}

	public int getPendingQuantity()
	{
		return pendingQuantity;
	}

	public long getPendingGross()
	{
		return pendingGross;
	}

	public long getPendingTax()
	{
		return pendingTax;
	}

	public long getPendingCostBasis()
	{
		return pendingCostBasis;
	}

	public int getPendingBackedQuantity()
	{
		return pendingBackedQuantity;
	}

	public int getPendingUnbackedQuantity()
	{
		return pendingUnbackedQuantity;
	}

	public long getPendingOpenedAt()
	{
		return pendingOpenedAt;
	}

	public double getPendingPredictedMinutes()
	{
		return pendingPredictedMinutes;
	}

	public long getPendingPredictedProfit()
	{
		return pendingPredictedProfit;
	}

	public boolean isCollected()
	{
		return collected;
	}

	public void setCollected(boolean collected)
	{
		this.collected = collected;
	}

	public boolean isJournalled()
	{
		return journalled;
	}

	public void setJournalled(boolean journalled)
	{
		this.journalled = journalled;
	}
}
