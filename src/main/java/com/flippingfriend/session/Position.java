package com.flippingfriend.session;

/**
 * An item we are currently holding, and what we intended to do with it.
 * <p>
 * Positions survive restarts, world hops and logouts, because a flip routinely outlives a play
 * session and a plugin that forgets what you bought is worse than no plugin at all.
 */
public class Position
{
	private int itemId;
	private String itemName;
	private int quantity;
	/** Total coins paid, so the average cost stays exact across partial fills at different prices. */
	private long totalCost;
	private long openedAt;
	private int targetSellPrice;
	private int stopPrice;

	private double predictedSellMinutes;
	/**
	 * The chance the plan gave this position's sell leg of finishing, kept so the outcome can be
	 * scored against it.
	 * <p>
	 * The sell leg is where the uncertainty actually lives -- a buy at the bid nearly always fills --
	 * and it is the leg the completion calibrator has never seen, because nothing carried the claim
	 * from the moment the trade was chosen to the moment the sale settled.
	 */
	/** False for items that were already in the bank when the plugin first saw them. */
	private boolean costKnown;

	public Position()
	{
	}

	public Position(int itemId, String itemName, int quantity, long totalCost, long openedAt, boolean costKnown)
	{
		this.itemId = itemId;
		this.itemName = itemName;
		this.quantity = quantity;
		this.totalCost = totalCost;
		this.openedAt = openedAt;
		this.costKnown = costKnown;
	}

	/** A holding we found in the bank or inventory, whose purchase price we never saw. */
	public static Position preExisting(int itemId, String itemName, int quantity, long now)
	{
		return new Position(itemId, itemName, quantity, 0, now, false);
	}

	public int getItemId()
	{
		return itemId;
	}

	public String getItemName()
	{
		return itemName == null ? "Item " + itemId : itemName;
	}

	public void setTotalCost(long totalCost)
	{
		this.totalCost = totalCost;
	}

	public void setItemName(String itemName)
	{
		this.itemName = itemName;
	}

	public int getQuantity()
	{
		return quantity;
	}

	public void setQuantity(int quantity)
	{
		this.quantity = quantity;
	}

	public long getTotalCost()
	{
		return totalCost;
	}

	public long getOpenedAt()
	{
		return openedAt;
	}

	public boolean isCostKnown()
	{
		return costKnown && quantity > 0 && totalCost > 0;
	}

	public int getAverageCost()
	{
		if (quantity <= 0 || !costKnown)
		{
			return 0;
		}
		return (int) (totalCost / quantity);
	}

	public int getTargetSellPrice()
	{
		return targetSellPrice;
	}

	public void setTargetSellPrice(int targetSellPrice)
	{
		this.targetSellPrice = targetSellPrice;
	}

	public int getStopPrice()
	{
		return stopPrice;
	}

	public void setStopPrice(int stopPrice)
	{
		this.stopPrice = stopPrice;
	}

	public double getPredictedSellMinutes()
	{
		return predictedSellMinutes;
	}


	public void setPredictedSellMinutes(double predictedSellMinutes)
	{
		this.predictedSellMinutes = predictedSellMinutes;
	}

	/** Folds an additional purchase in, keeping the average cost exact. */
	public void addFill(int extraQuantity, long extraCost)
	{
		if (extraQuantity <= 0)
		{
			return;
		}
		int costBefore = getAverageCost();

		this.quantity += extraQuantity;
		this.totalCost += extraCost;
		this.costKnown = true;

		// The stop moves with the cost it was set against.
		//
		// It is written once, by the plan, as a percentage below what the item cost at the time --
		// twelve per cent on the High profile. Averaging more units into the position moves the
		// average cost and left the stop where it was, so the distance between them was whatever
		// arithmetic happened to produce.
		//
		// Averaging DOWN is the dangerous direction. Buy one at 1,000 and the stop is 880; buy another
		// at 800 and the average is 900 with the stop still at 880 -- two per cent below cost instead
		// of twelve. A dip that the profile intended to sit through now cuts the position instead.
		//
		// Rescaled by the same ratio rather than recomputed, because the percentage belongs to the
		// risk profile the trade was opened under and this class has no business knowing it.
		int costAfter = getAverageCost();
		if (stopPrice > 0 && costBefore > 0 && costAfter > 0)
		{
			stopPrice = (int) Math.max(1, Math.round((double) stopPrice / costBefore * costAfter));
		}
	}

	/**
	 * Removes sold units.
	 * <p>
	 * The clamp matters, and it is why this returns a {@link Removal} rather than a bare number. If
	 * the book thinks it holds 462 rubies and the game reports 3,558 sold, only 462 of them have a
	 * cost basis here. Returning just the cost silently invited the caller to subtract the cost of
	 * 462 units from the proceeds of 3,558 and call the difference profit -- which is exactly what
	 * happened, turning a 28k flip into a recorded 2.57m. The shortfall has to travel with the
	 * answer so the caller can refuse to price what this book cannot account for.
	 *
	 * @return how many units actually had a cost basis, and what that cost was
	 */
	/**
	 * Sets the quantity and cost a player says this position really has.
	 *
	 * <p>Separate from {@link #setQuantity} and {@link #addFill} because it is a different claim.
	 * addFill blends a fill the game reported; this records a correction the player made by hand, so
	 * the cost becomes known by assertion rather than by observation -- which is the only way to price
	 * a holding that was adopted pre-existing and never had a cost at all.
	 *
	 * <p>The stop is rescaled with the cost for the reason addFill spells out above: it was written
	 * once as a percentage below what the item cost at the time, and leaving it behind when the cost
	 * moves turns a twelve per cent stop into whatever arithmetic happens to produce.
	 */
	public void statedCost(int quantity, long totalCost)
	{
		if (quantity <= 0 || totalCost < 0)
		{
			return;
		}
		int costBefore = getAverageCost();

		this.quantity = quantity;
		this.totalCost = totalCost;
		this.costKnown = true;

		int costAfter = getAverageCost();
		if (stopPrice > 0 && costBefore > 0 && costAfter > 0)
		{
			stopPrice = (int) Math.max(1, Math.round((double) stopPrice / costBefore * costAfter));
		}
	}

	public Removal removeQuantity(int soldQuantity)
	{
		if (soldQuantity <= 0 || quantity <= 0)
		{
			return new Removal(0, 0, Math.max(0, soldQuantity));
		}

		int removed = Math.min(soldQuantity, quantity);
		long costBasis = costKnown ? (totalCost * removed) / quantity : 0;

		quantity -= removed;
		totalCost -= costBasis;
		return new Removal(removed, costBasis, soldQuantity - removed);
	}

	/**
	 * Reduces the holding to what is really there, keeping the average cost intact.
	 * <p>
	 * Shrinking {@code quantity} on its own leaves {@code totalCost} whole, and since the average is
	 * {@code totalCost / quantity} that silently re-prices whatever survives upwards. Losing sight of
	 * a sale should not make the rest of the stack look more expensive than it was.
	 */
	public void reduceTo(int reallyHeld)
	{
		if (reallyHeld < 0 || reallyHeld >= quantity)
		{
			return;
		}
		if (quantity > 0)
		{
			totalCost = (totalCost * reallyHeld) / quantity;
		}
		quantity = reallyHeld;
	}

	/** What one call to {@link #removeQuantity(int)} could and could not account for. */
	public static final class Removal
	{
		private final int backedQuantity;
		private final long costBasis;
		private final int unbackedQuantity;

		/** Nothing was accounted for, because there was no position to account against. */
		static Removal none(int askedFor)
		{
			return new Removal(0, 0, Math.max(0, askedFor));
		}

		Removal(int backedQuantity, long costBasis, int unbackedQuantity)
		{
			this.backedQuantity = backedQuantity;
			this.costBasis = costBasis;
			this.unbackedQuantity = unbackedQuantity;
		}

		/** Units removed that had a known cost. */
		public int getBackedQuantity()
		{
			return backedQuantity;
		}

		/** What those units cost.  */
		public long getCostBasis()
		{
			return costBasis;
		}

		/** Units the caller asked about that this book could not account for. */
		public int getUnbackedQuantity()
		{
			return unbackedQuantity;
		}
	}

	public long minutesHeld(long nowEpochSeconds)
	{
		return Math.max(0, (nowEpochSeconds - openedAt) / 60);
	}
}
